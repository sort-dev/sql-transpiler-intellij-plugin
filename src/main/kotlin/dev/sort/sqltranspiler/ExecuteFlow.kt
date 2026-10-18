package dev.sort.sqltranspiler

import com.intellij.database.console.JdbcConsole
import com.intellij.database.console.JdbcConsoleProvider
import com.intellij.database.settings.DatabaseSettings
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import dev.brikk.house.sql.shape.PipeLowering
import dev.brikk.house.sql.shape.lowerForExecution

/**
 * Phase 2: execute-through-transpilation. The pipeline is
 *
 *   transpile -> review (diagnostics shown; skipped when this exact transpilation was
 *   already approved and nothing changed) -> append generated SQL to the attached
 *   console -> run it through DataGrip's own script-model execution.
 *
 * Native-parser verification slots in between transpile and review once
 * brikk-sql-verify lands in this branch (Phase 3).
 */
object ExecuteFlow {

    /** The JDBC console attached to the editor's file, or null (drives action visibility). */
    fun consoleFor(project: Project, editor: Editor): JdbcConsole? {
        val virtualFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
        return try {
            JdbcConsoleProvider.getValidConsole(project, virtualFile)
        } catch (_: Exception) {
            null
        }
    }

    /** The brikk dialect of the console's data source engine, or null when unsupported. */
    fun targetDialectOf(console: JdbcConsole): String? =
        BrikkDialects.fromDbms(console.dataSource.dbms)

    fun run(
        project: Project,
        editor: Editor,
        psiFile: PsiFile?,
        sourceOverride: String? = null,
    ) {
        if (!SqlActionEligibility.ensureAllowed(project, editor, psiFile)) return
        val console = consoleFor(project, editor)
        if (console == null) {
            TranspileFlow.notify(
                project,
                "No database console/session is attached to this editor \u2014 attach a data source to execute through transpilation.",
                NotificationType.WARNING,
            )
            return
        }
        val target = targetDialectOf(console)
        if (target == null) {
            TranspileFlow.notify(
                project,
                "The attached data source engine '${console.dataSource.dbms.name}' has no brikk-sql dialect yet (known: ${BrikkDialects.names.joinToString()}).",
                NotificationType.WARNING,
            )
            return
        }

        val scope = TranspileFlow.resolveScope(editor)
        if (scope.text.isBlank()) {
            TranspileFlow.notify(project, "Nothing to execute: the scope is empty.", NotificationType.WARNING)
            return
        }

        val detected = sourceOverride ?: TranspileFlow.detectSourceDialect(scope, psiFile)
        if (detected == null) {
            JBPopupFactory.getInstance()
                .createPopupChooserBuilder(BrikkDialects.names)
                .setTitle("Source Dialect")
                .setItemChosenCallback { transpileReviewExecute(project, editor, scope, it, target, console) }
                .createPopup()
                .showInBestPositionFor(editor)
            return
        }
        transpileReviewExecute(project, editor, scope, detected, target, console)
    }

    private fun transpileReviewExecute(
        project: Project,
        editor: Editor,
        scope: TranspileFlow.Scope,
        source: String,
        target: String,
        console: JdbcConsole,
    ) {
        if (!TranspileFlow.sourceIsCurrent(project, editor, scope)) return
        val destination = console.dataSource
        fun destinationIsCurrent(): Boolean {
            if (consoleFor(project, editor) === console && console.dataSource === destination && targetDialectOf(console) == target) return true
            TranspileFlow.notify(project, "The destination console changed. Run the current statement again.", NotificationType.WARNING)
            return false
        }
        if (!destinationIsCurrent()) return
        if (source == target && canRunUnchanged(scope.text, source)) {
            // Same dialect, no pipe syntax: nothing to transpile; run the text as-is
            // (still through the console pipeline so behavior is uniform). The engine —
            // not brikk-sql — is the authority on its own dialect, so no parse gate here.
            executeInConsole(project, console, scope.text.trim())
            return
        }
        val (outcome, verification) = TranspileFlow.transpileAndVerify(project, scope.text, source, target)
        if (!TranspileFlow.sourceIsCurrent(project, editor, scope) || !destinationIsCurrent()) return
        when (outcome) {
            is BrikkTranspiler.TranspileOutcome.Failure -> {
                val position = outcome.line?.let { " (line ${outcome.line}, col ${outcome.col ?: "?"})" } ?: ""
                TranspileFlow.notify(
                    project,
                    "Cannot parse as ${BrikkDialects.displayName(source)}$position: ${outcome.message}",
                    NotificationType.ERROR,
                )
            }
            is BrikkTranspiler.TranspileOutcome.Success -> {
                if (!canRunUnchanged(outcome.sql, target)) {
                    TranspileFlow.notify(project, "Generated SQL still contains PIPE syntax or has incomplete lexical boundaries. Execution blocked.", NotificationType.ERROR)
                    return
                }
                val cache = project.service<TranspileApprovalCache>()
                ProgressManager.checkCanceled()
                val key = cache.key(scope.text, source, target)
                if (cache.isApproved(key, outcome.sql) &&
                    FindingPolicy.canExecute(outcome, verification)
                ) {
                    // Unchanged since the last review, not newly rejected by an authoritative
                    // parser (advisory hints don't invalidate approval), and no blocking
                    // refusals -> re-execute without review.
                    executeInConsole(project, console, outcome.sql)
                    return
                }
                TranspilePreviewDialog(project, editor, scope, outcome, verification, onExecute = {
                    if (FindingPolicy.canExecute(outcome, verification)) {
                        if (!TranspileFlow.sourceIsCurrent(project, editor, scope) || !destinationIsCurrent()) return@TranspilePreviewDialog
                        ProgressManager.checkCanceled()
                        cache.approve(key, outcome.sql)
                        executeInConsole(project, console, outcome.sql)
                    }
                }).show()
            }
        }
    }

    /** Quoted/commented markers are native text; claimed or uncertain PIPE text must lower. */
    internal fun canRunUnchanged(sql: String, dialect: String): Boolean {
        ProgressManager.checkCanceled()
        if ("|>" !in sql) return true
        val lowering = lowerForExecution(sql, dialect, allowFirstStage = false)
        ProgressManager.checkCanceled()
        return when (lowering) {
            is PipeLowering.Ok, is PipeLowering.Refused -> false
            PipeLowering.NotPipe -> true
        }
    }

    /**
     * Appends [sql] to the console document and runs it through DataGrip's script-model
     * execution — the generated SQL stays visible in the console, exactly like a typed
     * query, and results/errors land in the standard result tabs.
     */
    fun executeInConsole(project: Project, console: JdbcConsole, sql: String) {
        ProgressManager.checkCanceled()
        val document = console.document
        var start = 0
        // Each run lands as its own terminated statement after a blank line, so the console
        // stays a valid script to re-run from.
        val statement = sql.trimEnd().let { if (it.endsWith(";")) it else "$it;" }
        WriteCommandAction.runWriteCommandAction(project, "Brikk Execute via Transpilation", null, {
            ProgressManager.checkCanceled()
            val length = document.textLength
            val existing = document.charsSequence
            val prefix = when {
                length == 0 || existing.isBlank() -> ""
                existing.endsWith("\n\n") -> ""
                existing.endsWith("\n") -> "\n"
                else -> "\n\n"
            }
            document.insertString(length, prefix + statement)
            start = length + prefix.length
        })
        PsiDocumentManager.getInstance(project).commitDocument(document)

        val range = TextRange(start, document.textLength)
        val consoleEditor = console.consoleView.currentEditor
        val info = JdbcConsoleProvider.findScriptModelNoInject(
            project,
            console.file,
            consoleEditor,
            range,
            DatabaseSettings.ExecOption(),
        )
        if (info == null) {
            TranspileFlow.notify(
                project,
                "Could not build an executable script model over the generated SQL \u2014 it was appended to the console; run it manually.",
                NotificationType.WARNING,
            )
            return
        }
        ProgressManager.checkCanceled()
        JdbcConsoleProvider.doRunQueryInConsole(console, info)
    }
}

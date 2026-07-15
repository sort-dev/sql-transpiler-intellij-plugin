package dev.sort.sqltranspiler

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.datatransfer.StringSelection
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * The transpile review surface: a before/after diff of the scope against the generated
 * SQL, a diagnostics strip (generator `unsupported(...)` messages + functions unknown
 * to the target engine's catalog), and the apply modes.
 *
 * Two shapes:
 *  - preview mode ([onExecute] == null): Replace In Place / Insert After / Copy;
 *  - execute-review mode ([onExecute] != null, the Phase 2 gate): Execute / Copy —
 *    the callback runs after the user approves the generated SQL.
 */
class TranspilePreviewDialog(
    private val project: Project,
    private val editor: Editor,
    private val scope: TranspileFlow.Scope,
    private val outcome: BrikkTranspiler.TranspileOutcome.Success,
    private val verification: VerifierService.Report? = null,
    private val onExecute: (() -> Unit)? = null,
) : DialogWrapper(project) {

    private val replaceAction = object : DialogWrapperAction("Replace In Place") {
        override fun doAction(e: java.awt.event.ActionEvent) {
            applyEdit(replace = true)
            close(OK_EXIT_CODE)
        }
    }

    private val insertAfterAction = object : DialogWrapperAction("Insert After") {
        override fun doAction(e: java.awt.event.ActionEvent) {
            applyEdit(replace = false)
            close(OK_EXIT_CODE)
        }
    }

    private val copyAction = object : DialogWrapperAction("Copy") {
        override fun doAction(e: java.awt.event.ActionEvent) {
            CopyPasteManager.getInstance().setContents(StringSelection(outcome.sql))
            TranspileFlow.notify(project, "Generated ${BrikkDialects.displayName(outcome.target)} SQL copied to clipboard.", NotificationType.INFORMATION)
            close(OK_EXIT_CODE)
        }
    }

    private val executeAction = object : DialogWrapperAction("Execute") {
        override fun doAction(e: java.awt.event.ActionEvent) {
            close(OK_EXIT_CODE)
            onExecute?.invoke()
        }
    }

    private val sendToConsoleAction = object : DialogWrapperAction("Send to Console...") {
        override fun doAction(e: java.awt.event.ActionEvent) {
            SendToConsole.choose(project, outcome, sourceFileName(), e.source as? JComponent) {
                close(OK_EXIT_CODE)
            }
        }
    }

    private fun sourceFileName(): String =
        com.intellij.openapi.fileEditor.FileDocumentManager.getInstance()
            .getFile(editor.document)?.name ?: "editor"

    init {
        val arrow = "${BrikkDialects.displayName(outcome.source)} \u2192 ${BrikkDialects.displayName(outcome.target)}"
        title = if (onExecute != null) "Execute via Transpilation: $arrow" else "Transpile: $arrow"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val diagnostics = createDiagnosticsComponent()
        // A findings *list* gets a draggable splitter (resizable, never squeezed to a
        // partial row); the single clean-transpile label just docks at the bottom.
        val panel: JComponent = if (diagnostics is JPanel) {
            com.intellij.ui.JBSplitter(true, 0.72f).apply {
                firstComponent = wrap(createDiffComponent())
                secondComponent = wrap(diagnostics)
                setHonorComponentsMinimumSize(true)
            }
        } else {
            JPanel(BorderLayout(0, JBUI.scale(8))).apply {
                add(createDiffComponent(), BorderLayout.CENTER)
                diagnostics?.let { add(it, BorderLayout.SOUTH) }
            }
        }
        panel.preferredSize = JBUI.size(900, 560)
        return panel
    }

    private fun wrap(component: JComponent): javax.swing.JPanel =
        JPanel(BorderLayout()).apply { add(component, BorderLayout.CENTER) }

    private fun createDiffComponent(): JComponent {
        val fileType = FileTypeManager.getInstance().getFileTypeByExtension("sql")
        val factory = DiffContentFactory.getInstance()
        val request = SimpleDiffRequest(
            null,
            factory.create(project, scope.text, fileType),
            factory.create(project, outcome.sql, fileType),
            "Source: ${BrikkDialects.displayName(outcome.source)}",
            "Generated: ${BrikkDialects.displayName(outcome.target)}",
        )
        val diffPanel = DiffManager.getInstance().createRequestPanel(project, disposable, null)
        diffPanel.setRequest(request)
        return diffPanel.component
    }

    /** One row in the diagnostics list: rendered text, icon, optional provenance tooltip. */
    private data class DiagnosticRow(val text: String, val severe: Boolean, val tooltip: String?)

    private fun createDiagnosticsComponent(): JComponent? {
        val target = BrikkDialects.displayName(outcome.target)
        val rows = buildDiagnosticRows(target)

        val notes = buildList {
            if (outcome.pipesDesugared) add("Pipe (|>) syntax was desugared to standard $target SQL.")
            if (verification?.allAccepted == true) {
                add(
                    if (verification.advisory) "Checked: accepted by the $target advisory grammar oracle (advisory, not authoritative)."
                    else "Verified: accepted by the native $target parser.",
                )
            }
            FindingPolicy.informational(outcome).forEach {
                add("Capability checks unavailable: ${it.detail}.")
            }
        }.joinToString(" ") { it }.let { if (it.isEmpty()) "" else " $it" }

        if (rows.isEmpty()) {
            return JBLabel("Certified clean \u2014 no findings.$notes", AllIcons.General.InspectionsOK, JBLabel.LEADING)
        }

        val blockedCount = FindingPolicy.blockingRefusals(outcome, verification).size
        val header = buildString {
            append(rows.size).append(" finding").append(if (rows.size > 1) "s" else "")
            if (onExecute != null && blockedCount > 0) {
                append(" \u2014 execution blocked by ").append(blockedCount).append(" refusal").append(if (blockedCount > 1) "s" else "")
            } else {
                append(" \u2014 output is best-effort, review before use")
            }
            append(':').append(notes)
        }

        val panel = JPanel(BorderLayout(0, JBUI.scale(4)))
        panel.add(
            JBLabel(header, if (blockedCount > 0) AllIcons.General.Error else AllIcons.General.Warning, JBLabel.LEADING),
            BorderLayout.NORTH,
        )
        val list = object : com.intellij.ui.components.JBList<DiagnosticRow>(rows) {
            override fun getToolTipText(event: java.awt.event.MouseEvent): String? {
                val index = locationToIndex(event.point)
                return if (index >= 0) model.getElementAt(index).tooltip else null
            }
        }
        list.setCellRenderer { _, row, _, _, _ ->
            JBLabel(row.text, if (row.severe) AllIcons.General.Error else AllIcons.General.Warning, JBLabel.LEADING)
        }
        // At least 4 rows tall even for a single finding, so a horizontal scrollbar
        // never squeezes the list to a partial line; grows up to 8 before scrolling.
        list.visibleRowCount = rows.size.coerceIn(4, 8)
        val scroll = JBScrollPane(list)
        scroll.minimumSize = JBUI.size(100, 96)
        panel.add(scroll, BorderLayout.CENTER)
        return panel
    }

    private fun buildDiagnosticRows(target: String): List<DiagnosticRow> = buildList {
        // Native-parser rejections first, walked back to source positions when the
        // emit-span source map covers the error location. Authoritative rejections are
        // hard errors; advisory ones (ShardingSphere grammar) are soft, possibly-false hints.
        verification?.rejected?.forEach { verdict ->
            val statement = outcome.statements.getOrNull(verdict.index)
            val statementNote = if (outcome.statementCount > 1) " (statement ${verdict.index + 1})" else ""
            val position = verdict.line?.let { line ->
                // Exact emit-span hit first; otherwise (brikk-sql 0.6.0) fall back to the
                // nearest covering span \u2014 approximate, so labeled \u2248 instead of \u2192.
                val col = verdict.col ?: 1
                val sourceNote = statement?.let { st ->
                    val exact = st.result.mapErrorToSource(line, col)
                    val mapped = exact ?: st.result.mapErrorToSource(line, col, exact = false)
                    mapped?.let {
                        val arrow = if (exact != null) "\u2192" else "\u2248"
                        " $arrow source line ${it.line + st.sourceLineOffset}"
                    }
                }.orEmpty()
                " at line $line, col ${verdict.col ?: "?"}$sourceNote"
            } ?: ""
            val text = if (verdict.advisory) {
                "Advisory: the $target grammar oracle flagged$statementNote$position " +
                    "(re-implemented grammar \u2014 may be a false positive): ${verdict.error}"
            } else {
                "Rejected by the native $target parser$statementNote$position: ${verdict.error}"
            }
            add(DiagnosticRow(text, severe = !verdict.advisory, tooltip = null))
        }
        // Certification findings: blocking refusals as errors, downgraded refusals and
        // warnings as warnings; provenance (research-report pointer) as tooltip.
        FindingPolicy.reviewable(outcome, verification).forEach { entry ->
            val kindLabel = entry.finding.kind.name.lowercase().replace('_', ' ')
            val suffix = if (entry.downgraded) " (accepted by the native $target parser \u2014 downgraded)" else ""
            add(
                DiagnosticRow(
                    "[$kindLabel] ${entry.finding.subject}: ${entry.finding.detail}$suffix",
                    severe = entry.blocksExecution,
                    tooltip = entry.finding.provenance?.let { "Provenance: $it" },
                )
            )
        }
    }

    override fun createActions(): Array<Action> =
        if (onExecute != null) {
            // REFUSAL semantics: certified-wrong or unverifiable output must not run
            // (NO_TARGET_CATALOG and verifier-accepted passthrough softened by policy).
            executeAction.isEnabled = FindingPolicy.blockingRefusals(outcome, verification).isEmpty()
            executeAction.putValue(DEFAULT_ACTION, true)
            arrayOf(executeAction, copyAction, cancelAction)
        } else {
            replaceAction.putValue(DEFAULT_ACTION, true)
            arrayOf(replaceAction, insertAfterAction, sendToConsoleAction, copyAction, cancelAction)
        }

    private fun applyEdit(replace: Boolean) {
        val document = editor.document
        WriteCommandAction.runWriteCommandAction(project, "Brikk Transpile", null, {
            if (replace) {
                document.replaceString(scope.range.startOffset, scope.range.endOffset, outcome.sql)
            } else {
                val insertion = buildString {
                    append("\n\n-- dialect: ").append(outcome.target).append('\n')
                    append(outcome.sql)
                    append('\n')
                }
                document.insertString(scope.range.endOffset, insertion)
            }
        })
        // Insert After may have just introduced a foreign-dialect block into a
        // vendor-dialect file — refresh so the "go Generic SQL" banner shows right away.
        com.intellij.ui.EditorNotifications.getInstance(project).updateAllNotifications()
    }
}

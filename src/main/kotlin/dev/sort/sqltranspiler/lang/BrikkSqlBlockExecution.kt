package dev.sort.sqltranspiler.lang

import com.intellij.database.console.JdbcConsole
import com.intellij.database.console.JdbcConsoleProvider
import com.intellij.database.console.session.DatabaseSessionManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.settings.DatabaseSettings
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.psi.SqlStatement
import dev.brikk.house.sql.intellij.CooperativeExecuteConfiguration
import dev.brikk.house.sql.intellij.CooperativeExecutePromoter
import dev.brikk.house.sql.intellij.ExecuteInterceptor
import dev.brikk.house.sql.intellij.ExecutionClaim
import dev.brikk.house.sql.parser.SqlScriptSnapshot
import dev.brikk.house.sql.shape.PipeLowering
import dev.brikk.house.sql.shape.lowerForExecution
import dev.sort.sqltranspiler.BrikkDialects
import dev.sort.sqltranspiler.ExecuteFlow
import dev.sort.sqltranspiler.TranspileFlow

/**
 * Execute (Ctrl+Enter and its variants) in a `.bsql` block runs the statement at the caret,
 * or the selection, as written, on the block's `using=` data source. The stock action
 * would attach the whole `.bsql` file to one console instead, which also replaces every
 * block's own data source for resolution.
 */
object BrikkSqlBlockExecuteInterceptor : ExecuteInterceptor {
    override fun execute(event: AnActionEvent, option: DatabaseSettings.ExecOption): ExecutionClaim {
        val project = event.project ?: return ExecutionClaim.DECLINED
        val editor = event.getData(CommonDataKeys.EDITOR)?.let(InjectedLanguageEditorUtil::getTopLevelEditor)
            ?: return ExecutionClaim.DECLINED
        val documents = PsiDocumentManager.getInstance(project)
        val file = documents.getPsiFile(editor.document) ?: return ExecutionClaim.DECLINED
        if (file.language !== BrikkSqlDialect.INSTANCE) return ExecutionClaim.DECLINED
        documents.commitDocument(editor.document)

        val selection = editor.selectionModel.takeIf { it.hasSelection() }?.let { TextRange(it.selectionStart, it.selectionEnd) }
        val block = BrikkSqlBlocks.blockAt(file, selection?.startOffset ?: editor.caretModel.offset)
        if (block == null) {
            TranspileFlow.notify(project, "Nothing to execute: the caret is not in a block.", NotificationType.WARNING)
            return ExecutionClaim.HANDLED
        }
        val using = BrikkSqlBlocks.directiveOf(block)?.using
        if (using == null) {
            TranspileFlow.notify(project, "This block has no connection. Add using=\"<data source>\" to its -- language= comment.", NotificationType.WARNING)
            return ExecutionClaim.HANDLED
        }
        val dataSource = BrikkSqlBlocks.localDataSource(project, using)
        if (dataSource == null) {
            TranspileFlow.notify(project, "No database connection named \"$using\".", NotificationType.WARNING)
            return ExecutionClaim.HANDLED
        }
        val sql = when {
            selection != null -> selection.intersection(block.textRange)?.substring(file.text)
            else -> statementAt(block, editor.caretModel.offset)
        }
        if (sql.isNullOrBlank()) {
            TranspileFlow.notify(project, "Nothing to execute.", NotificationType.WARNING)
            return ExecutionClaim.HANDLED
        }
        val runnable = executable(project, sql.trim(), BrikkSqlBlocks.directiveOf(block)?.dialect) ?: return ExecutionClaim.HANDLED
        BrikkSqlBlockRunner.run(project, dataSource, BrikkSqlBlocks.languageOf(block), runnable)
        return ExecutionClaim.HANDLED
    }

    /**
     * PIPE text (`|>` stages, or a bare `FROM ...` first stage) lowered to standard SQL in the
     * block's dialect, the way the dialect plugins lower it in their own consoles; other SQL as
     * written. Null, with a notification, when the PIPE text cannot run.
     */
    internal fun executable(project: Project, sql: String, dialect: String?): String? {
        if (dialect == null || !BrikkDialects.isSupported(dialect)) return sql
        val firstStage = FIRST_STAGE.containsMatchIn(sql)
        return when (val lowering = lowerForExecution(sql, dialect, allowFirstStage = firstStage)) {
            PipeLowering.NotPipe -> sql
            is PipeLowering.Ok -> lowering.sql.takeIf { lowering.blockers.isEmpty() } ?: run {
                TranspileFlow.notify(project, "PIPE query cannot run as ${BrikkDialects.displayName(dialect)}: ${lowering.blockers.joinToString("; ")}", NotificationType.ERROR)
                null
            }
            is PipeLowering.Refused -> {
                // A plain query that only looked like a first stage runs as written.
                if (firstStage && "|>" !in sql) return sql
                TranspileFlow.notify(project, "PIPE query cannot run as ${BrikkDialects.displayName(dialect)}: ${lowering.message}", NotificationType.ERROR)
                null
            }
        }
    }

    private val FIRST_STAGE = Regex("""^\s*FROM\b""", RegexOption.IGNORE_CASE)

    /** The text of the statement at [offset] (see [statementRangeAt]); empty when the block has none. */
    internal fun statementAt(block: BrikkSqlBlock, offset: Int): String =
        statementRangeAt(block, offset)?.shiftLeft(block.textRange.startOffset)?.substring(block.text).orEmpty()

    /**
     * The statement at [offset], in host offsets and without its surrounding whitespace: by
     * brikk-sql's statement ranges for the block's dialect, which know PIPE; else as the block's
     * own language parses it; else the whole block. What Execute runs and what is highlighted.
     */
    internal fun statementRangeAt(block: BrikkSqlBlock, offset: Int): TextRange? {
        val text = block.text
        val relative = (offset - block.textRange.startOffset).coerceIn(0, text.length)
        val dialect = BrikkSqlBlocks.directiveOf(block)?.dialect?.takeIf(BrikkDialects::isSupported)
        val range = dialect?.let {
            val ranges = SqlScriptSnapshot.parse(text, it).statements.filter { range -> range.hasSql }
            (ranges.firstOrNull { range -> relative in range.start..range.end } ?: ranges.lastOrNull { range -> range.end <= relative } ?: ranges.firstOrNull())
                ?.let { range -> TextRange(range.start, range.end) }
        } ?: statementFromPsi(block, relative) ?: TextRange(0, text.length)
        var start = range.startOffset
        var end = range.endOffset
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        return if (start == end) null else TextRange(start, end).shiftRight(block.textRange.startOffset)
    }

    private fun statementFromPsi(block: BrikkSqlBlock, relative: Int): TextRange? {
        val injected = InjectedLanguageManager.getInstance(block.project).getInjectedPsiFiles(block)
            ?.firstOrNull()?.first?.containingFile ?: return null
        val statements = PsiTreeUtil.findChildrenOfType(injected, SqlStatement::class.java)
            .filter { PsiTreeUtil.getParentOfType(it, SqlStatement::class.java) == null }
        if (statements.isEmpty()) return null
        val statement = statements.firstOrNull { relative in it.textRange.startOffset..it.textRange.endOffset }
            ?: statements.lastOrNull { it.textRange.endOffset <= relative }
            ?: statements.first()
        return statement.textRange
    }
}

/** Runs SQL in a console of a data source, reusing one console file per data source. */
object BrikkSqlBlockRunner {
    private val CONSOLE_FILES = Key.create<MutableMap<String, VirtualFile>>("brikk.sql.block.console.files")

    fun run(project: Project, dataSource: LocalDataSource, language: Language, sql: String) {
        val console = consoleFor(project, dataSource, language)
        if (console == null) {
            TranspileFlow.notify(project, "Could not open a console for \"${dataSource.name}\".", NotificationType.ERROR)
            return
        }
        ExecuteFlow.executeInConsole(project, console, sql)
    }

    fun consoleFor(project: Project, dataSource: LocalDataSource, language: Language): JdbcConsole? {
        val files = project.getUserData(CONSOLE_FILES) ?: HashMap<String, VirtualFile>().also { project.putUserData(CONSOLE_FILES, it) }
        val file = files[dataSource.uniqueId]?.takeIf { it.isValid } ?: ScratchRootType.getInstance().createScratchFile(
            project, "brikk-${dataSource.name.replace(Regex("[^A-Za-z0-9._-]+"), "_")}.sql", language, "",
            ScratchFileService.Option.create_if_missing,
        )?.also { files[dataSource.uniqueId] = it } ?: return null
        JdbcConsoleProvider.getValidConsole(project, file)?.takeIf { it.dataSource == dataSource }?.let { return it }
        // The console file is never opened: every run, first or not, stays in the .bsql editor
        // and shows its results in the bottom pane.
        val session = DatabaseSessionManager.getSession(project, dataSource)
        return JdbcConsoleProvider.attachConsole(project, session, file)
    }

    /** Test seam: the console file used for [dataSource]. */
    internal fun useConsoleFile(project: Project, dataSource: LocalDataSource, file: VirtualFile) {
        val files = project.getUserData(CONSOLE_FILES) ?: HashMap<String, VirtualFile>().also { project.putUserData(CONSOLE_FILES, it) }
        files[dataSource.uniqueId] = file
    }
}

class BrikkSqlBlockExecuteConfiguration : CooperativeExecuteConfiguration() {
    override val interceptor: ExecuteInterceptor get() = BrikkSqlBlockExecuteInterceptor
}

class BrikkSqlBlockExecutePromoter : CooperativeExecutePromoter()

package dev.sort.sqltranspiler.lang

import com.intellij.database.editor.DatabaseColors
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.concurrency.AppExecutorUtil

/**
 * The statement Execute would run, boxed like DataGrip boxes it in a console. DataGrip draws that
 * only in files attached to a console, and a `.bsql` file never is (attaching would replace every
 * block's own connection), so `.bsql` editors draw it here: same color, same statement as
 * [BrikkSqlBlockExecuteInterceptor.statementRangeAt], and only in blocks that can run (a
 * `using=` naming a connection).
 */
class BrikkSqlCurrentStatementEditorListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return
        // File editors; diff and preview editors of the same document stay plain.
        if (editor.editorKind != EditorKind.MAIN_EDITOR && editor.editorKind != EditorKind.UNTYPED) return
        if (FileDocumentManager.getInstance().getFile(editor.document)?.fileType !== BrikkSqlFileType) return
        BrikkSqlCurrentStatement(project, editor).install()
    }
}

internal class BrikkSqlCurrentStatement(private val project: Project, private val editor: Editor) {
    private val disposable = Disposer.newDisposable("Brikk SQL current statement")
    private var highlighter: RangeHighlighter? = null

    fun install() {
        EditorUtil.disposeWithEditor(editor, disposable)
        editor.putUserData(KEY, this)
        editor.caretModel.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) = update()
        }, disposable)
        editor.selectionModel.addSelectionListener(object : SelectionListener {
            override fun selectionChanged(e: SelectionEvent) = update()
        }, disposable)
        editor.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = update()
        }, disposable)
        update()
    }

    fun update() {
        if (editor.isDisposed) return
        // A selection runs as selected; DataGrip shows no statement box then either. It goes through
        // the same coalesced task, so an earlier caret update cannot land after it.
        val selected = editor.selectionModel.hasSelection()
        val offset = editor.caretModel.offset
        ReadAction.nonBlocking<TextRange?> { if (selected) null else statementAt(offset) }
            .withDocumentsCommitted(project)
            .expireWith(disposable)
            .coalesceBy(this)
            .finishOnUiThread(ModalityState.any(), ::show)
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun statementAt(offset: Int): TextRange? {
        if (editor.isDisposed) return null
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
        val block = BrikkSqlBlocks.blockAt(file, offset) ?: return null
        val using = BrikkSqlBlocks.directiveOf(block)?.using ?: return null
        BrikkSqlBlocks.localDataSource(project, using) ?: return null
        return BrikkSqlBlockExecuteInterceptor.statementRangeAt(block, offset)
    }

    private fun show(range: TextRange?) {
        if (editor.isDisposed) return
        val current = highlighter
        if (current != null && current.isValid && range != null && current.textRange == range) return
        current?.let(editor.markupModel::removeHighlighter)
        highlighter = range?.takeIf { it.endOffset <= editor.document.textLength }?.let {
            editor.markupModel.addRangeHighlighter(
                DatabaseColors.CONSOLE_RANGE_TO_EXECUTE, it.startOffset, it.endOffset,
                HighlighterLayer.CARET_ROW + 1, HighlighterTargetArea.LINES_IN_RANGE,
            )
        }
    }

    /** The statement range currently boxed; for tests. */
    internal val shown: TextRange? get() = highlighter?.takeIf { it.isValid }?.textRange

    companion object {
        internal val KEY = Key.create<BrikkSqlCurrentStatement>("brikk.sql.current.statement")
    }
}

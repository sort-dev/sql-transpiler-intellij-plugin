package dev.sort.sqltranspiler.lang

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hint.HintManager
import com.intellij.database.connection.throwable.info.ErrorInfo
import com.intellij.database.console.JdbcConsole
import com.intellij.database.datagrid.DataAuditor
import com.intellij.database.datagrid.DataProducer
import com.intellij.database.datagrid.DataRequest
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import java.util.Collections
import java.util.WeakHashMap

/**
 * Errors of runs started from `.bsql` blocks, shown where they were started. The run itself
 * goes to the connection's console, which is never in front, so its Output tab alone would leave
 * the `.bsql` editor looking like the statement succeeded. A failed statement gets an error on
 * its text (until it is edited or run again) and an error hint in its editor.
 */
object BrikkSqlRunErrors {
    /** A statement's last run failed with [message]; it holds while [range] still has [text]. */
    class Mark(val range: RangeMarker, val text: String, val message: String)

    private class Run(
        val project: Project, val document: Document, val range: RangeMarker, val text: String, val label: String,
        /** What was submitted, normalized: it tells the run's request from the console's own (search path, ...). */
        val submitted: String,
    )

    private fun normalized(sql: String) = sql.trim().removeSuffix(";").trim().replace(Regex("\\s+"), " ")

    private val MARKS = Key.create<List<Mark>>("brikk.sql.run.errors")
    private val runs = Collections.synchronizedMap(WeakHashMap<DataRequest, Run>())
    private val audited = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    /** The run each console's next request belongs to, with when it was started. */
    private val pending = Collections.synchronizedMap(WeakHashMap<JdbcConsole, Pair<Run, Long>>())
    private const val PENDING_MILLIS = 30_000L

    /**
     * Runs [submit], which submits [sql], the statement at [range] of [document], to [console]; the
     * request that runs it reports its errors back to that statement. Earlier errors there are cleared.
     */
    fun track(project: Project, console: JdbcConsole, document: Document, range: TextRange, label: String, sql: String, submit: () -> Unit) {
        clear(project, document, range)
        val run = Run(project, document, document.createRangeMarker(range), document.getText(range), label, normalized(sql))
        ensureAuditor(console)
        pending[console] = run to System.currentTimeMillis()
        submit()
    }

    /** The errors still standing in [document]: their statement is unchanged since the run. */
    fun marks(document: Document): List<Mark> =
        document.getUserData(MARKS).orEmpty().filter { it.range.isValid && document.getText(it.range.textRange) == it.text }

    private fun ensureAuditor(console: JdbcConsole) {
        val bus = console.session.messageBus
        synchronized(audited) {
            if (audited.containsKey(bus)) return
            bus.addAuditor(object : DataAuditor {
                override fun jobSubmitted(request: DataRequest, producer: DataProducer) {
                    // The run's request is the console's next query with its text, submitted inside the run or just after.
                    val owner = request.owner as? JdbcConsole ?: return
                    val query = (request as? DataRequest.QueryRequest)?.query?.let(::normalized) ?: return
                    val (run, started) = pending[owner] ?: return
                    if (System.currentTimeMillis() - started > PENDING_MILLIS) {
                        pending.remove(owner)
                        return
                    }
                    if (query != run.submitted && run.submitted !in query) return
                    pending.remove(owner)
                    runs[request] = run
                }

                override fun error(context: DataRequest.Context, info: ErrorInfo) {
                    failed(context.request, info.message ?: "Query failed")
                }
            })
            audited[bus] = true
        }
    }

    /** Reports [message] for the statement that submitted [request], if a tracked run did. */
    internal fun failed(request: DataRequest, message: String) {
        val run = runs[request] ?: return
        ApplicationManager.getApplication().invokeLater({
            if (!run.range.isValid || run.document.getText(run.range.textRange) != run.text) return@invokeLater
            val text = "${run.label}: ${message.trim()}"
            val marks = marks(run.document)
            if (marks.none { it.range.textRange == run.range.textRange && it.message == text }) {
                run.document.putUserData(MARKS, marks + Mark(run.range, run.text, text))
            }
            PsiDocumentManager.getInstance(run.project).getPsiFile(run.document)
                ?.let { DaemonCodeAnalyzer.getInstance(run.project).restart(it, "Brikk SQL run failed") }
            val selected = FileEditorManager.getInstance(run.project).selectedTextEditor
            EditorFactory.getInstance().getEditors(run.document, run.project).firstOrNull { it === selected }
                ?.let { HintManager.getInstance().showErrorHint(it, text) }
        }, run.project.disposed)
    }

    private fun clear(project: Project, document: Document, range: TextRange) {
        val marks = marks(document)
        val kept = marks.filterNot { it.range.textRange.intersects(range) }
        if (kept.size == marks.size) return
        document.putUserData(MARKS, kept)
        PsiDocumentManager.getInstance(project).getPsiFile(document)
            ?.let { DaemonCodeAnalyzer.getInstance(project).restart(it, "Brikk SQL run errors cleared") }
    }
}

/** Paints [BrikkSqlRunErrors] on the blocks of a `.bsql` file. */
class BrikkSqlRunErrorAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is BrikkSqlBlock) return
        val document = PsiDocumentManager.getInstance(element.project).getDocument(element.containingFile) ?: return
        for (mark in BrikkSqlRunErrors.marks(document)) {
            val range = mark.range.textRange
            if (!element.textRange.contains(range)) continue
            holder.newAnnotation(HighlightSeverity.ERROR, mark.message).range(range).create()
        }
    }
}

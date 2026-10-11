package dev.sort.sqltranspiler

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement

/**
 * Gutter affordance for `-- dialect: xyz` marker comments: click to transpile the
 * marker's segment (marker line to next marker / end of file) from the declared
 * dialect into a picked target. Phase 2 extends this into the execute-through-
 * transpilation pipeline (transpile -> verify -> review -> run on current engine).
 */
class DialectMarkerLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Line markers must sit on leaf elements; SQL line comments are comment leaves.
        if (element !is PsiComment || element.firstChild != null) return null
        // Scratch-file affordance only: in strict vendor-dialect hosts (consoles, mapped
        // files) the host parser mis-parses foreign segments, so the gutter stays out of
        // the way there — select the SQL and use the Brikk SQL actions instead.
        if (!SqlHosts.isLenient(element.containingFile)) return null
        val dialect = DialectMarker.parseLine(element.text) ?: return null
        val file = element.containingFile
        val markers = com.intellij.psi.util.CachedValuesManager.getCachedValue(file) {
            com.intellij.psi.util.CachedValueProvider.Result.create(
                DialectMarker.findAll(file.text, BrikkDialects.fromLanguageId(file.language.id)), file,
            )
        }
        if (markers.none { it.lineStartOffset <= element.textRange.startOffset && it.lineEndOffset >= element.textRange.endOffset }) return null
        val supported = BrikkDialects.isSupported(dialect)
        val tooltip = if (supported) {
            "Brikk SQL: this block is ${BrikkDialects.displayName(dialect)} \u2014 click to transpile"
        } else {
            "Brikk SQL: unknown dialect '$dialect' (known: ${BrikkDialects.names.joinToString()})"
        }
        val icon = if (supported) AllIcons.Actions.SwapPanels else AllIcons.General.Warning
        return LineMarkerInfo(
            element,
            element.textRange,
            icon,
            { tooltip },
            { _, elt -> if (supported) transpileMarkerSegment(elt, dialect) },
            GutterIconRenderer.Alignment.LEFT,
            { "Brikk SQL dialect marker: $dialect" },
        )
    }

    private fun transpileMarkerSegment(element: PsiElement, dialect: String) {
        val project = element.project
        val psiFile = element.containingFile ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        if (PsiDocumentManager.getInstance(project).getDocument(psiFile) !== editor.document) return
        // Resolve the clicked marker itself. Its following line can be another marker
        // when this block is empty; moving there would claim the neighboring SQL.
        editor.selectionModel.removeSelection()
        editor.caretModel.moveToOffset(
            element.textRange.startOffset
        )
        // With a console attached this is the execute affordance (transpile -> review ->
        // run on the current engine); otherwise it falls back to transpile-and-preview.
        if (ExecuteFlow.consoleFor(project, editor) != null) {
            ExecuteFlow.run(project, editor, psiFile, sourceOverride = dialect)
        } else {
            TranspileFlow.run(project, editor, psiFile, source = dialect)
        }
    }
}

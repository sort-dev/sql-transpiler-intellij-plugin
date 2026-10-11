package dev.sort.sqltranspiler

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.test.assertFailsWith

class PreviewEditPlatformTest : BasePlatformTestCase() {
    fun testWholeBlockReplacementUpdatesMarkerAndPreservesNextBlockWithUndo() {
        val original = "-- dialect: mysql\nSELECT 1;\n-- dialect: duckdb\nSELECT 2;\n"
        myFixture.configureByText("work.bsql", original)
        myFixture.editor.caretModel.moveToOffset(original.indexOf("SELECT"))
        val scope = TranspileFlow.resolveScope(myFixture.editor)
        val result = BrikkTranspiler.transpile(scope.text, "mysql", "postgres", false) as BrikkTranspiler.TranspileOutcome.Success
        val dialog = TranspilePreviewDialog(project, myFixture.editor, scope, result)
        try {
            assertTrue(dialog.applyEdit(true))
            assertEquals("-- dialect: postgres\nSELECT 1;\n-- dialect: duckdb\nSELECT 2;\n", myFixture.editor.document.text)
            val fileEditor = FileEditorManager.getInstance(project).selectedEditor!!
            assertTrue(UndoManager.getInstance(project).isUndoAvailable(fileEditor))
            UndoManager.getInstance(project).undo(fileEditor)
            assertEquals(original, myFixture.editor.document.text)
        } finally { dialog.close(DialogWrapper.CANCEL_EXIT_CODE) }
    }

    fun testPartialBlockReplacementRestoresUntouchedSuffixDialect() {
        val output = editSelection("SELECT 1;", replace = true)
        assertEquals("-- dialect: mysql\n-- dialect: postgres\nSELECT 1;\n-- dialect: mysql\n\nSELECT 2;\n", output)
        assertEquals("mysql", DialectMarker.dialectAt(output, output.indexOf("SELECT 2")))
    }

    fun testInsertAfterRestoresUntouchedSuffixDialect() {
        val output = editSelection("SELECT 1;", replace = false)
        assertEquals("-- dialect: mysql\nSELECT 1;\n\n-- dialect: postgres\nSELECT 1;\n-- dialect: mysql\n\nSELECT 2;\n", output)
    }

    fun testSelectionWithoutTerminatorStillPreservesFollowingStatement() {
        val output = editSelection("SELECT 1", replace = true)
        assertEquals("mysql", DialectMarker.dialectAt(output, output.indexOf("SELECT 2")))
        assertTrue(output.contains("SELECT 1;\n-- dialect: mysql\n;"))
    }

    fun testExpressionSelectionCannotIntroduceMarkersInsideStatement() {
        assertFailsWith<IllegalArgumentException> { editSelection("1", replace = true) }
    }

    fun testStaleDocumentCannotOverwriteNewContent() {
        myFixture.configureByText("work.bsql", "-- dialect: mysql\nSELECT 1;\n")
        myFixture.editor.caretModel.moveToOffset(20)
        val scope = TranspileFlow.resolveScope(myFixture.editor)
        val result = BrikkTranspiler.transpile(scope.text, "mysql", "postgres") as BrikkTranspiler.TranspileOutcome.Success
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.insertString(0, "-- new\n") }
        assertFailsWith<IllegalArgumentException> { PreviewEdit.plan(myFixture.editor.document.text, scope, result, true) }
        assertTrue(myFixture.editor.document.text.startsWith("-- new\n"))
    }

    fun testReadOnlyDocumentIsNotModified() {
        val original = "SELECT 1;"
        myFixture.configureByText("work.bsql", original)
        val scope = TranspileFlow.resolveScope(myFixture.editor)
        val result = BrikkTranspiler.transpile(original, "mysql", "postgres") as BrikkTranspiler.TranspileOutcome.Success
        val dialog = TranspilePreviewDialog(project, myFixture.editor, scope, result)
        myFixture.editor.document.setReadOnly(true)
        try {
            assertFalse(dialog.applyEdit(true))
            assertEquals(original, myFixture.editor.document.text)
        } finally {
            myFixture.editor.document.setReadOnly(false)
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }

    fun testRoutineInternalSemicolonDoesNotPermitAPartialBlockEdit() {
        val text = "CREATE FUNCTION f() RETURNS BIGINT BEGIN DECLARE x BIGINT DEFAULT 1; RETURN x; END;"
        val start = text.indexOf("RETURN")
        val range = com.intellij.openapi.util.TextRange(start, start + "RETURN x;".length)
        val scope = TranspileFlow.Scope(range, range.substring(text), "trino", text)
        val result = BrikkTranspiler.transpile("SELECT 1", "trino", "duckdb") as BrikkTranspiler.TranspileOutcome.Success
        assertFailsWith<IllegalArgumentException> { PreviewEdit.plan(text, scope, result, true) }
    }

    private fun editSelection(selection: String, replace: Boolean): String {
        val text = "-- dialect: mysql\nSELECT 1;\nSELECT 2;\n"
        myFixture.configureByText("work.bsql", text)
        val start = text.indexOf(selection)
        myFixture.editor.selectionModel.setSelection(start, start + selection.length)
        val scope = TranspileFlow.resolveScope(myFixture.editor)
        val result = BrikkTranspiler.transpile(scope.text, "mysql", "postgres", false) as BrikkTranspiler.TranspileOutcome.Success
        val edit = PreviewEdit.plan(text, scope, result, replace)
        return text.replaceRange(edit.range.startOffset, edit.range.endOffset, edit.text)
    }
}

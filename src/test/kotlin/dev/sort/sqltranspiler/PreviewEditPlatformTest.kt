package dev.sort.sqltranspiler

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.SqlExecutionRefusal
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
            assertEquals("-- language=postgresql\nSELECT 1;\n\n-- dialect: duckdb\nSELECT 2;\n", myFixture.editor.document.text)
            val fileEditor = FileEditorManager.getInstance(project).selectedEditor!!
            assertTrue(UndoManager.getInstance(project).isUndoAvailable(fileEditor))
            UndoManager.getInstance(project).undo(fileEditor)
            assertEquals(original, myFixture.editor.document.text)
        } finally { dialog.close(DialogWrapper.CANCEL_EXIT_CODE) }
    }

    fun testPartialBlockReplacementRestoresUntouchedSuffixDialect() {
        val output = editSelection("SELECT 1;", replace = true)
        assertEquals("-- language=postgresql\nSELECT 1;\n\n-- dialect: mysql\nSELECT 2;\n", output)
        assertEquals("mysql", DialectMarker.dialectAt(output, output.indexOf("SELECT 2")))
    }

    fun testInsertAfterRestoresUntouchedSuffixDialect() {
        val output = editSelection("SELECT 1;", replace = false)
        assertEquals("-- dialect: mysql\nSELECT 1;\n\n-- language=postgresql\nSELECT 1;\n\n-- dialect: mysql\nSELECT 2;\n", output)
    }

    fun testSelectionWithoutTerminatorStillPreservesFollowingStatement() {
        val output = editSelection("SELECT 1", replace = true)
        assertEquals("mysql", DialectMarker.dialectAt(output, output.indexOf("SELECT 2")))
        assertTrue(output, output.contains("SELECT 1;\n\n-- dialect: mysql\n;"))
    }

    /** One blank line after the source statement and one after the inserted block, however many were there. */
    fun testInsertAfterABlockKeepsOneBlankLineEachSide() {
        val output = editBlock("-- language=mysql using=\"Local\"\nSELECT 1;\n\n\n\n-- language=duckdb\nSELECT 2;\n", replace = false)
        assertEquals("-- language=mysql using=\"Local\"\nSELECT 1;\n\n-- language=postgresql\nSELECT 1;\n\n-- language=duckdb\nSELECT 2;\n", output)
        assertEquals("-- language=mysql\nSELECT 1;\n\n-- language=postgresql\nSELECT 1;\n",
            editBlock("-- language=mysql\nSELECT 1;\n\n\n", replace = false))
    }

    fun testInsertAfterAStatementInsideABlockRestoresItsMarkerVerbatim() {
        val text = "-- language=mysql using=\"Local\"\nSELECT 1;\nSELECT 2;\n"
        myFixture.configureByText("work.bsql", text)
        myFixture.editor.selectionModel.setSelection(text.indexOf("SELECT 1"), text.indexOf("SELECT 1") + "SELECT 1;".length)
        val scope = TranspileFlow.resolveScope(myFixture.editor)
        val result = BrikkTranspiler.transpile(scope.text, "mysql", "postgres", false) as BrikkTranspiler.TranspileOutcome.Success
        val edit = PreviewEdit.plan(text, scope, result, replace = false)
        assertEquals("-- language=mysql using=\"Local\"\nSELECT 1;\n\n-- language=postgresql\nSELECT 1;\n\n-- language=mysql using=\"Local\"\nSELECT 2;\n",
            text.replaceRange(edit.range.startOffset, edit.range.endOffset, edit.text))
    }

    fun testReplaceInPlaceKeepsOneBlankLineBeforeTheNextBlock() {
        assertEquals("-- language=postgresql\nSELECT 1;\n\n-- language=duckdb\nSELECT 2;\n",
            editBlock("-- language=mysql\nSELECT 1;\n-- language=duckdb\nSELECT 2;\n", replace = true))
    }

    fun testTargetsWithoutAnIdeLanguageNameTheirDialect() {
        assertEquals("-- language=genericsql dialect=datafusion\nSELECT 1;\n",
            editBlock("-- language=mysql\nSELECT 1;\n", replace = true, target = "datafusion"))
    }

    /** Outside .bsql files the IDE reads `-- language=` as an injection comment, so the plugin's own form is written. */
    fun testOrdinarySqlFilesGetDialectMarkers() {
        val text = "SELECT 1;\nSELECT 'x';\n"
        myFixture.configureByText("work.sql", text)
        myFixture.editor.selectionModel.setSelection(0, "SELECT 1;".length)
        val scope = TranspileFlow.resolveScope(myFixture.editor)
        val result = BrikkTranspiler.transpile(scope.text, "mysql", "postgres", false) as BrikkTranspiler.TranspileOutcome.Success
        val dialog = TranspilePreviewDialog(project, myFixture.editor, scope, result)
        try {
            assertTrue(dialog.applyEdit(false))
            val output = myFixture.editor.document.text
            assertTrue(output, output.contains("\n-- dialect: postgres\nSELECT 1;\n"))
            assertFalse(output, output.contains("language="))
        } finally { dialog.close(DialogWrapper.CANCEL_EXIT_CODE) }
    }

    private fun editBlock(text: String, replace: Boolean, target: String = "postgres"): String {
        myFixture.configureByText("block.bsql", text)
        val editor = com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil.getTopLevelEditor(myFixture.editor)
        editor.caretModel.moveToOffset(text.indexOf("SELECT 1") + 2)
        val scope = TranspileFlow.resolveScope(editor)
        val result = BrikkTranspiler.transpile(scope.text, "mysql", target, false) as BrikkTranspiler.TranspileOutcome.Success
        val edit = PreviewEdit.plan(text, scope, result, replace)
        return text.replaceRange(edit.range.startOffset, edit.range.endOffset, edit.text)
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
        assertFailsWith<SqlExecutionRefusal> { PreviewEdit.plan(myFixture.editor.document.text, scope, result, true) }
        assertTrue(myFixture.editor.document.text.startsWith("-- new\n"))
    }

    fun testEditAndRevertStillInvalidatesThePreview() {
        myFixture.configureByText("work.bsql", "SELECT 1;")
        val scope = TranspileFlow.resolveScope(myFixture.editor)
        val result = BrikkTranspiler.transpile(scope.text, "doris", "duckdb") as BrikkTranspiler.TranspileOutcome.Success
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText("SELECT 2;") }
        WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText(scope.text) }
        assertEquals(scope.text, myFixture.editor.document.text)
        assertFailsWith<SqlExecutionRefusal> { PreviewEdit.plan(myFixture.editor.document.text, scope, result, true) }
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

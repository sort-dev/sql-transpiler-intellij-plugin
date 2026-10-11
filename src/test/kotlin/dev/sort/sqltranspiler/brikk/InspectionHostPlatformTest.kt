package dev.sort.sqltranspiler.brikk

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.sort.sqltranspiler.*
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import java.awt.event.ActionEvent
import javax.swing.Action

class InspectionHostPlatformTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        val stdlib = java.io.File(com.intellij.util.PathUtil.getJarPathForClass(kotlin.Unit::class.java))
        com.intellij.testFramework.PsiTestUtil.addLibrary(module, "kotlin-stdlib", stdlib.parent, stdlib.name)
        val jdk = com.intellij.openapi.projectRoots.JavaSdk.getInstance().createJdk("Host guard fixture", System.getProperty("java.home"), false)
        WriteCommandAction.runWriteCommandAction(project) { com.intellij.openapi.projectRoots.ProjectJdkTable.getInstance().addJdk(jdk, testRootDisposable) }
        com.intellij.openapi.roots.ModuleRootModificationUtil.setModuleSdk(module, jdk)
        myFixture.addFileToProject("org/intellij/lang/annotations/Language.java", """
            package org.intellij.lang.annotations;
            public @interface Language { String value(); String prefix() default ""; String suffix() default ""; }
        """.trimIndent())
        myFixture.addFileToProject("runtime/Types.kt", """
            package dev.brikk.house.sql.runtime
            import org.intellij.lang.annotations.Language
            annotation class BrikkSql
            object Sql {
                fun doris(@Language("SQL") sql: String): String = sql
                fun duckdb(@Language("SQL") sql: String): String = sql
                fun trino(@Language("SQL") sql: String): String = sql
            }
        """.trimIndent())
    }

    private fun configure(body: String) {
        myFixture.configureByText("Query.kt", "import dev.brikk.house.sql.runtime.*\n$body")
        val start = myFixture.editor.document.text.indexOf("SELECT")
        myFixture.editor.selectionModel.setSelection(start, start + "SELECT 1".length)
    }

    fun testAllThreeResolvedBrikkDialectsDisableConversion() {
        for (dialect in listOf("doris", "duckdb", "trino")) {
            configure("@BrikkSql fun query() = Sql.$dialect(\"SELECT 1\")")
            assertFalse(dialect, SqlActionEligibility.isAllowed(project, myFixture.editor, myFixture.file))
            val event = AnActionEvent.createFromDataContext("guard", Presentation(), SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project).add(CommonDataKeys.EDITOR, myFixture.editor)
                .add(CommonDataKeys.PSI_FILE, myFixture.file).build())
            TranspileToAction().update(event)
            assertFalse(event.presentation.isEnabledAndVisible)
        }
    }

    fun testAliasesAreResolvedRatherThanMatchedBySpelling() {
        configure("import dev.brikk.house.sql.runtime.Sql as Query\nimport dev.brikk.house.sql.runtime.BrikkSql as Template\n@Template fun query() = Query.doris(\"SELECT 1\")")
        assertFalse(SqlActionEligibility.isAllowed(project, myFixture.editor, myFixture.file))
        configure("object Local { fun doris(sql: String) = sql }; @BrikkSql fun query() = Local.doris(\"SELECT 1\")")
        assertTrue(SqlActionEligibility.isAllowed(project, myFixture.editor, myFixture.file))
    }

    fun testAnnotationRemovalReevaluatesEligibilityWithoutChangingSql() {
        configure("@BrikkSql fun query() = Sql.doris(\"SELECT 1\")")
        assertFalse(SqlActionEligibility.isAllowed(project, myFixture.editor, myFixture.file))
        WriteCommandAction.runWriteCommandAction(project) {
            val start = myFixture.editor.document.text.indexOf("@BrikkSql ")
            myFixture.editor.document.deleteString(start, start + "@BrikkSql ".length)
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(SqlActionEligibility.isAllowed(project, myFixture.editor, myFixture.file))
    }

    fun testUnresolvedContextDoesNotAuthorizeConversion() {
        configure("@BrikkSql fun query() = Missing.doris(\"SELECT 1\")")
        assertFalse(SqlActionEligibility.isAllowed(project, myFixture.editor, myFixture.file))
    }

    fun testInjectedInspectionSqlResolvesBackToItsKotlinHost() {
        configure("@BrikkSql fun query() = Sql.doris(\"SELECT 1\")")
        val host = PsiTreeUtil.findChildOfType(myFixture.file, KtStringTemplateExpression::class.java)!!
        val injected = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(host).orEmpty()
        assertFalse("The fixture must contain a real SQL injection", injected.isEmpty())
        assertFalse(SqlActionEligibility.isAllowed(project, myFixture.editor, injected.first().first.containingFile))
    }

    fun testForcedPreviewApplyAndExecuteCannotBypassTheGuard() {
        configure("@BrikkSql fun query() = Sql.doris(\"SELECT 1\")")
        val original = myFixture.editor.document.text
        val scope = TranspileFlow.resolveScope(myFixture.editor)
        val result = BrikkTranspiler.transpile(scope.text, "doris", "duckdb") as BrikkTranspiler.TranspileOutcome.Success
        var executed = false
        val dialog = TranspilePreviewDialog(project, myFixture.editor, scope, result, onExecute = { executed = true })
        try {
            assertFalse(dialog.applyEdit(true))
            assertFalse(dialog.applyEdit(false))
            val action = TranspilePreviewDialog::class.java.getDeclaredField("executeAction").let {
                it.isAccessible = true
                it.get(dialog) as Action
            }
            action.isEnabled = true
            action.actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, "Execute"))
            assertFalse(executed)
            assertEquals(original, myFixture.editor.document.text)
        } finally { dialog.close(DialogWrapper.CANCEL_EXIT_CODE) }
    }

    fun testStandaloneSqlRemainsAvailableWithKotlinInstalled() {
        myFixture.configureByText("query.bsql", "SELECT 1")
        assertTrue(SqlActionEligibility.isAllowed(project, myFixture.editor, myFixture.file))
    }
}

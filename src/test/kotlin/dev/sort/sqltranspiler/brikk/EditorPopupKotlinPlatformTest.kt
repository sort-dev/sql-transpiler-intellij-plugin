package dev.sort.sqltranspiler.brikk

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import dev.sort.sqltranspiler.EditorPopupTestCase
import dev.sort.sqltranspiler.SqlActionEligibility
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

class EditorPopupKotlinPlatformTest : EditorPopupTestCase() {
    fun testRealSqlInjectionRequiresItsMatchingEditor() {
        myFixture.configureByText("InjectedMenu.kt", """
            import org.intellij.lang.annotations.Language
            fun accept(@Language("SQL") text: String) = text
            fun example() = accept("SELECT 17")
        """.trimIndent())
        val (file, editor) = injectedContext()
        contract.assertAbsent()
        contract.assertAbsent(contract.context(file = file))
        contract.assertShown(contract.context(editor = editor, file = file))
    }

    fun testBrikkInspectionHostsAndTheirInjectionsRemainIneligible() {
        myFixture.configureByText("InspectionMenu.kt", """
            import dev.brikk.house.sql.runtime.*
            @BrikkSql fun example() = Sql.doris("SELECT 17")
        """.trimIndent())
        val (file, editor) = injectedContext()
        assertFalse(SqlActionEligibility.isAllowed(project, myFixture.editor, myFixture.file))
        contract.assertAbsent()
        contract.assertAbsent(contract.context(editor = editor, file = file))
    }

    private fun injectedContext(): Pair<PsiFile, Editor> {
        val host = PsiTreeUtil.findChildrenOfType(myFixture.file, KtStringTemplateExpression::class.java)
            .single { "SELECT 17" in it.text }
        val injected = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(host).orEmpty()
        assertEquals("The fixture must produce a real SQL injection", 1, injected.size)
        val offset = myFixture.editor.document.text.indexOf("SELECT")
        myFixture.editor.caretModel.moveToOffset(offset)
        val file = checkNotNull(injected.single().first.containingFile)
        return file to com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myFixture.editor, myFixture.file, offset)
    }
}

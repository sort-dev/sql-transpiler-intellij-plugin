package dev.sort.sqltranspiler

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.dialects.generic.GenericDialect
import com.intellij.sql.dialects.mysql.MysqlDialect
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PathUtil
import dev.brikk.house.sql.intellij.testing.setSqlDialectMapping
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import java.io.File

class EditorPopupPlatformTest : BasePlatformTestCase() {
    private val manager get() = ActionManager.getInstance()
    private val popup get() = manager.getAction("BrikkSql.EditorMenu") as ActionGroup
    private val conversions get() = listOf("BrikkSql.TranspileTo", "BrikkSql.TranspileFrom").map { checkNotNull(manager.getAction(it)) }

    override fun setUp() {
        super.setUp()
        val stdlib = File(PathUtil.getJarPathForClass(Unit::class.java))
        PsiTestUtil.addLibrary(module, "kotlin-stdlib", stdlib.parent, stdlib.name)
        val jdk = JavaSdk.getInstance().createJdk("Menu fixture", System.getProperty("java.home"), false)
        WriteCommandAction.runWriteCommandAction(project) {
            ProjectJdkTable.getInstance().addJdk(jdk, testRootDisposable)
        }
        ModuleRootModificationUtil.setModuleSdk(module, jdk)
        myFixture.addFileToProject("org/intellij/lang/annotations/Language.java", """
            package org.intellij.lang.annotations;
            public @interface Language { String value(); String prefix() default ""; String suffix() default ""; }
        """.trimIndent())
        myFixture.addFileToProject("runtime/MenuSql.kt", """
            package dev.brikk.house.sql.runtime
            import org.intellij.lang.annotations.Language
            annotation class BrikkSql
            object Sql { fun doris(@Language("SQL") sql: String): String = sql }
        """.trimIndent())
    }

    override fun tearDown() {
        try {
            setSqlDialectMapping(project, null, null)
        } finally {
            super.tearDown()
        }
    }

    fun testUnrelatedEditorsOmitThePopupWithOrWithoutSelections() {
        for ((name, text) in listOf(
            "MenuSample.kt" to "fun sample() = \"SELECT 31\"",
            "MenuSample.java" to "class MenuSample { String value = \"SELECT 31\"; }",
            "menu-notes.md" to "# Notes\nSELECT 31",
            "menu-notes.txt" to "SELECT 31",
        )) {
            myFixture.configureByText(name, text)
            assertPopup(context(), false)
            myFixture.editor.selectionModel.setSelection(0, text.length)
            assertPopup(context(), false)
        }
    }

    fun testUnrelatedEditorPopupChildrenAreInvisibleNotDisabled() {
        myFixture.configureByText("OtherEntry.kt", "fun value() = \"SELECT 42\"")
        myFixture.editor.selectionModel.setSelection(0, myFixture.editor.document.textLength)
        val factory = PresentationFactory()
        val children = expand(popup, context(), factory)
        for (action in conversions + checkNotNull(manager.getAction("BrikkSql.ExecuteViaTranspile"))) {
            assertFalse(children.contains(action))
            assertFalse(factory.getPresentation(action).isVisible)
        }
    }

    fun testMissingProjectEditorOrPsiOmitsThePopup() {
        myFixture.configureByText("missing-context.bsql", "SELECT 17")
        assertPopup(context(editor = null), false)
        assertPopup(context(file = null), false)
        assertPopup(DataContext.EMPTY_CONTEXT, false)
    }

    fun testGenericAndBrikkSqlKeepConversionActions() {
        for (name in listOf("query.bsql", "query.sql")) {
            myFixture.configureByText(name, "SELECT 17")
            if (name.endsWith(".sql")) setSqlDialectMapping(project, myFixture.file.virtualFile, GenericDialect.INSTANCE)
            assertPopup(context(), true)
            myFixture.editor.selectionModel.setSelection(0, myFixture.editor.document.textLength)
            assertPopup(context(), true)
        }
    }

    fun testVendorSqlStillRequiresASelection() {
        myFixture.configureByText("vendor-query.sql", "SELECT 17")
        setSqlDialectMapping(project, myFixture.file.virtualFile, MysqlDialect.INSTANCE)
        assertPopup(context(), false)
        myFixture.editor.selectionModel.setSelection(0, myFixture.editor.document.textLength)
        assertPopup(context(), true)
    }

    fun testMismatchedSqlFileAndEditorCannotAuthorizeThePopup() {
        val sql = myFixture.addFileToProject("unrelated-query.bsql", "SELECT 17")
        myFixture.configureByText("NonSql.kt", "fun value() = \"SELECT 17\"")
        myFixture.editor.selectionModel.setSelection(0, myFixture.editor.document.textLength)
        assertPopup(context(file = sql), false)
        myFixture.configureByText("another-query.bsql", "SELECT 29")
        assertPopup(context(file = sql), false)
    }

    fun testRealSqlInjectionRequiresItsMatchingEditor() {
        myFixture.configureByText("InjectedMenu.kt", """
            import org.intellij.lang.annotations.Language
            fun accept(@Language("SQL") text: String) = text
            fun example() = accept("SELECT 17")
        """.trimIndent())
        val (file, editor) = injectedContext()
        assertPopup(context(), false)
        assertPopup(context(file = file), false)
        assertPopup(context(editor = editor, file = file), true)
    }

    fun testBrikkInspectionHostsAndTheirInjectionsRemainIneligible() {
        myFixture.configureByText("InspectionMenu.kt", """
            import dev.brikk.house.sql.runtime.*
            @BrikkSql fun example() = Sql.doris("SELECT 17")
        """.trimIndent())
        val (file, editor) = injectedContext()
        assertFalse(SqlActionEligibility.isAllowed(project, myFixture.editor, myFixture.file))
        assertPopup(context(), false)
        assertPopup(context(editor = editor, file = file), false)
    }

    fun testManualConversionAndScratchEntryPointsAreUnchanged() {
        myFixture.configureByText("ManualMenu.kt", "fun value() = \"SELECT 17\"")
        myFixture.editor.selectionModel.setSelection(0, myFixture.editor.document.textLength)
        for (place in listOf(ActionPlaces.ACTION_SEARCH, ActionPlaces.MAIN_MENU)) {
            val actions = expand(DefaultActionGroup(conversions), context(), PresentationFactory(), place)
            assertTrue(actions.containsAll(conversions))
        }
        val scratch = checkNotNull(manager.getAction("BrikkSql.NewScratch"))
        for (id in listOf("ToolsMenu", "NewGroup")) {
            assertTrue(id, (manager.getAction(id) as ActionGroup).getChildren(null).contains(scratch))
        }
        assertTrue(expand(DefaultActionGroup(scratch), context(), PresentationFactory(), ActionPlaces.MAIN_MENU).contains(scratch))
    }

    fun testOnlyThePopupDisplayLabelChanges() {
        myFixture.configureByText("label-query.bsql", "SELECT 17")
        val factory = PresentationFactory()
        val actions = expand(manager.getAction("EditorPopupMenu") as ActionGroup, context(), factory)
        assertTrue(actions.contains(popup))
        assertEquals("SQL Transpiler", factory.getPresentation(popup).text)
        assertEquals("Brikk SQL Scratch", manager.getAction("BrikkSql.NewScratch").templatePresentation.text)
    }

    private fun context(editor: Editor? = myFixture.editor, file: PsiFile? = myFixture.file): DataContext =
        SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .apply {
                if (editor == null) addNull(CommonDataKeys.EDITOR) else add(CommonDataKeys.EDITOR, editor)
                if (file == null) addNull(CommonDataKeys.PSI_FILE) else add(CommonDataKeys.PSI_FILE, file)
            }
            .build()

    private fun assertPopup(context: DataContext, expected: Boolean) {
        val factory = PresentationFactory()
        val actions = expand(manager.getAction("EditorPopupMenu") as ActionGroup, context, factory)
        assertEquals("EditorPopupMenu contains the Transpiler submenu", expected, actions.contains(popup))
        val children = expand(popup, context, factory)
        for (action in conversions) {
            assertEquals("conversion is rendered", expected, children.contains(action))
            assertEquals("conversion is enabled and visible", expected, factory.getPresentation(action).isEnabledAndVisible)
        }
        assertFalse(children.contains(manager.getAction("BrikkSql.ExecuteViaTranspile")))
    }

    private fun expand(group: ActionGroup, context: DataContext, factory: PresentationFactory, place: String = ActionPlaces.EDITOR_POPUP): List<AnAction> =
        Utils.expandActionGroup(group, factory, context, place, ActionUiKind.POPUP)

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

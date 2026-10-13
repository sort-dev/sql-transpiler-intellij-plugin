package dev.sort.sqltranspiler

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.LanguageCommenters
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

/**
 * BrikkSQL (.bsql) — the plugin-owned polyglot file type: language registration,
 * lenient host classification, and the marker gutter being active by default.
 */
class BrikkSqlFileTypePlatformTest : BasePlatformTestCase() {

    fun testPlatformLineCommenterRoundTrip() {
        myFixture.configureByText("scratch.bsql", "<caret>select 1;")
        val commenter = checkNotNull(LanguageCommenters.INSTANCE.forLanguage(myFixture.file.language))
        assertEquals("com.intellij.database.sql.common.core.psi.impl.support.SqlNestedCommenter", commenter.javaClass.name)

        myFixture.performEditorAction("CommentByLineComment")
        assertEquals("-- select 1;", myFixture.editor.document.text)
        myFixture.performEditorAction("CommentByLineComment")
        assertEquals("select 1;", myFixture.editor.document.text)
    }

    fun testPlatformNestedBlockCommenterRoundTrip() {
        val sql = "select /* existing */ 1;"
        myFixture.configureByText("scratch.bsql", "<selection>$sql</selection>")

        myFixture.performEditorAction("CommentByBlockComment")
        assertEquals("/*$sql*/", myFixture.editor.document.text)
        myFixture.performEditorAction("CommentByBlockComment")
        assertEquals(sql, myFixture.editor.document.text)
    }

    fun testBsqlFileGetsBrikkSqlLanguage() {
        myFixture.configureByText("scratch.bsql", "select 1;")
        assertEquals("BrikkSQL", myFixture.file.language.id)
        for (id in listOf("BrikkSql.NewScratch", "BrikkSql.TranspileTo", "BrikkSql.TranspileFrom", "BrikkSql.ExecuteViaTranspile")) {
            assertNotNull(id, ActionManager.getInstance().getAction(id))
        }
        assertTrue(SqlActionEligibility.isAllowed(project, myFixture.editor, myFixture.file))
        if (System.getProperty("test.kotlin") == "absent") {
            assertFalse(PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.kotlin"))?.isEnabled == true)
            val guards = ExtensionPointName.create<InspectionHostGuard>("dev.sort.sql-transpiler-intellij-plugin.inspectionHostGuard")
            assertTrue(guards.extensionList.isEmpty())
            val log = Files.readString(Path.of(PathManager.getLogPath(), "idea.log"))
            assertFalse("Kotlin-absent startup must not log errors", Regex("\\b(?:ERROR|SEVERE)\\s+-").containsMatchIn(log))
        }
    }

    fun testBrikkSqlHostIsLenient() {
        myFixture.configureByText("scratch.bsql", "select 1;")
        assertTrue(SqlHosts.isLenient(myFixture.file))
    }

    fun testMarkerGutterActiveInBsql() {
        myFixture.configureByText(
            "scratch.bsql",
            "-- dialect: clickhouse\nselect toString(now());\n\n-- dialect: doris\nFROM t |> WHERE a > 1 |> SELECT a;\n",
        )
        myFixture.doHighlighting()
        val markers = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
            .filter { it.lineMarkerTooltip?.contains("Brikk SQL") == true }
        assertEquals(2, markers.size)
    }

    fun testMixedDialectContentParsesWithoutExceptions() {
        // The point of the lenient host: none of this should explode the parser.
        myFixture.configureByText(
            "scratch.bsql",
            """
            -- dialect: mysql
            select date_format(created_at, '%Y-%m-%d') from t;

            -- dialect: clickhouse
            select toString(number) from numbers(10);

            -- dialect: duckdb
            select * from read_parquet('f.parquet');
            """.trimIndent(),
        )
        myFixture.doHighlighting() // would throw on parser/lexer crashes
    }
}

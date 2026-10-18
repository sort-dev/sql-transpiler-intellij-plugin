package dev.sort.sqltranspiler.lang

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.database.Dbms
import com.intellij.database.psi.DbPsiFacade
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.sql.database.SqlDataSourceManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.sort.sqltranspiler.lang.BrikkSqlMarkerContext.Context

class BrikkSqlMarkerCompletionPlatformTest : BasePlatformTestCase() {

    fun testContextFromTheLineBeforeTheCaret() {
        assertEquals(Context.Key("", emptySet(), first = true), BrikkSqlMarkerContext.at("-- "))
        assertEquals(Context.Key("lang", emptySet(), first = true), BrikkSqlMarkerContext.at("  -- lang"))
        assertNull(BrikkSqlMarkerContext.at("-- some comment"))
        assertNull(BrikkSqlMarkerContext.at("select 1 -- lang"))
        assertEquals(Context.Value("language", "my"), BrikkSqlMarkerContext.at("-- language=my"))
        assertEquals(Context.Key("", setOf("language"), first = false), BrikkSqlMarkerContext.at("-- language=mysql "))
        assertEquals(Context.Key("us", setOf("language", "dialect"), first = false), BrikkSqlMarkerContext.at("-- language=genericsql dialect=duckdb us"))
        assertEquals(Context.Value("using", "\"My Lo"), BrikkSqlMarkerContext.at("-- language=mysql using=\"My Lo"))
        assertEquals(Context.Key("", setOf("language", "using"), first = false), BrikkSqlMarkerContext.at("-- language=mysql using=\"My \\\"Local\\\" db\" "))
        assertEquals(Context.Value("dialect", ""), BrikkSqlMarkerContext.at("-- language=genericsql dialect="))
    }

    fun testQuotingOnlyWhenNeeded() {
        assertEquals("local", BrikkSqlMarkerContext.quote("local"))
        assertEquals("\"My Local Connection\"", BrikkSqlMarkerContext.quote("My Local Connection"))
        assertEquals("\"say \\\"hi\\\"\"", BrikkSqlMarkerContext.quote("say \"hi\""))
    }

    fun testAttributeNamesComplete() {
        myFixture.configureByText("keys.bsql", "-- language=mysql <caret>\nselect 1;")
        assertSameElements(lookups(), "dialect=", "using=")
        myFixture.configureByText("keys2.bsql", "-- language=mysql using=x <caret>\nselect 1;")
        assertSameElements(lookups(), "dialect=")
    }

    fun testLanguageKeywordCompletesWhileStillTypingInsideABlock() {
        myFixture.configureByText("first.bsql", "-- language=mysql\nselect 1;\n\n-- langu<caret>\nselect 2;")
        assertNull("single variant is inserted", myFixture.completeBasic())
        assertTrue(myFixture.editor.document.text, hostText().contains("\n-- language=\nselect 2;"))
    }

    fun testDialectValuesCompleteFromNothing() {
        myFixture.configureByText("dialect0.bsql", "-- language=genericsql dialect=<caret>\nselect 1;")
        assertTrue(lookups().toString(), "datafusion" in lookups())
    }

    fun testDialectValuesComplete() {
        myFixture.configureByText("dialect.bsql", "-- language=genericsql dialect=data<caret>\nselect 1;")
        assertNull(myFixture.completeBasic())
        assertTrue(hostText(), hostText().startsWith("-- language=genericsql dialect=datafusion\n"))
    }

    fun testConnectionNamesCompleteQuotedWhenNeeded() = withDataSources("My Local Connection", "warehouse") {
        myFixture.configureByText("using.bsql", "-- language=mysql using=<caret>\nselect 1;")
        assertSameElements(lookups(), "\"My Local Connection\"", "warehouse")

        myFixture.configureByText("using2.bsql", "-- language=mysql using=My<caret>\nselect 1;")
        assertNull(myFixture.completeBasic())
        assertTrue(hostText(), hostText().startsWith("-- language=mysql using=\"My Local Connection\"\n"))

        myFixture.configureByText("using3.bsql", "-- language=mysql using=\"My L<caret>\nselect 1;")
        assertNull(myFixture.completeBasic())
        assertTrue(hostText(), hostText().startsWith("-- language=mysql using=\"My Local Connection\"\n"))
    }

    fun testOrdinaryCommentsAreLeftAlone() {
        myFixture.configureByText("plain.bsql", "-- language=mysql\nselect 1; -- us<caret>\n")
        val names = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        LookupManager.getInstance(project).hideActiveLookup()
        assertFalse(names.toString(), names.any { it.endsWith("=") })
    }

    private fun hostText(): String = InjectedLanguageManager.getInstance(project).getTopLevelFile(myFixture.file).text

    private fun lookups(): List<String> {
        val names = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        LookupManager.getInstance(project).hideActiveLookup()
        return names
    }

    private fun withDataSources(vararg names: String, test: () -> Unit) {
        val manager = SqlDataSourceManager.getInstance(project)
        val sources = names.map { name -> manager.createEmpty().apply { this.name = name; definedDbms = Dbms.MYSQL }.also(manager::addDataSource) }
        DbPsiFacade.getInstance(project).clearCaches()
        try { test() } finally { sources.forEach(manager::removeDataSource) }
    }
}

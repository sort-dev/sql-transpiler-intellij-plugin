package dev.sort.sqltranspiler.lang

import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.database.Dbms
import com.intellij.database.psi.DbPsiFacade
import com.intellij.sql.database.SqlDataSourceManager
import com.intellij.testFramework.EdtTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Completion opens by itself while typing a marker comment. */
class BrikkSqlMarkerAutoPopupPlatformTest : BasePlatformTestCase() {
    private lateinit var tester: CompletionAutoPopupTester

    override fun setUp() {
        super.setUp()
        tester = CompletionAutoPopupTester(myFixture)
    }

    override fun runInDispatchThread(): Boolean = false

    private fun shown(): List<String> =
        LookupManager.getActiveLookup(myFixture.editor)?.items?.map { it.lookupString }.orEmpty()

    fun testTypingAnAttributeNameOpensCompletion() {
        tester.runWithAutoPopupEnabled {
            myFixture.configureByText("popup.bsql", "-- language=mysql <caret>\nselect 1;")
            tester.typeWithPauses("u")
            assertEquals(listOf("using="), shown())
        }
    }

    fun testTypingEqualsAfterUsingOffersConnections() {
        val manager = SqlDataSourceManager.getInstance(project)
        val source = manager.createEmpty().apply { name = "My Local Connection"; definedDbms = Dbms.MYSQL }
        edt {
            manager.addDataSource(source)
            DbPsiFacade.getInstance(project).clearCaches()
        }
        try {
            tester.runWithAutoPopupEnabled {
                myFixture.configureByText("popup2.bsql", "-- language=mysql using<caret>\nselect 1;")
                tester.typeWithPauses("=")
                assertEquals(listOf("\"My Local Connection\""), shown())
            }
        } finally {
            edt { manager.removeDataSource(source) }
        }
    }

    fun testTypingEqualsAfterLanguageOffersLanguages() {
        tester.runWithAutoPopupEnabled {
            myFixture.configureByText("popup4.bsql", "-- language<caret>\nselect 1;")
            tester.typeWithPauses("=")
            assertTrue(shown().toString(), shown().any { it.equals("mysql", ignoreCase = true) })
        }
    }

    fun testTypingEqualsAfterDialectOffersDialects() {
        tester.runWithAutoPopupEnabled {
            myFixture.configureByText("popup5.bsql", "-- language=genericsql dialect<caret>\nselect 1;")
            tester.typeWithPauses("=")
            assertTrue(shown().toString(), "datafusion" in shown())
        }
    }

    fun testOrdinaryCommentsDoNotPopUp() {
        tester.runWithAutoPopupEnabled {
            myFixture.configureByText("popup3.bsql", "-- language=mysql\nselect 1;\n-- <caret>\n")
            tester.typeWithPauses("note")
            assertNull(LookupManager.getActiveLookup(myFixture.editor))
        }
    }

    private fun edt(block: () -> Unit) = EdtTestUtil.runInEdtAndWait<Throwable> { block() }
}

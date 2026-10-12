package dev.sort.sqltranspiler

import dev.brikk.house.sql.verify.DorisVerifier
import dev.brikk.house.sql.verify.VerifyResult
import org.junit.AfterClass
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifier wiring against the real brikk-sql-verify oracles. DuckDB runs embedded on
 * any JDK; Trino needs a Java 25 runtime (JBR 25 in-IDE) and must degrade to
 * "unavailable" — never throw — on smaller JVMs like the CI test JVM.
 */
class VerifierServiceTest {

    @Test
    fun `duckdb verifier accepts valid SQL per statement`() {
        val report = service.verify("duckdb", listOf("SELECT 1", "SELECT * FROM range(3)"))
        assertNotNull(report)
        assertTrue(report.allAccepted, "expected clean verdicts, got: ${report.verdicts}")
        assertEquals(2, report.verdicts.size)
    }

    @Test
    fun `duckdb verifier rejects garbage with an engine error`() {
        val report = service.verify("duckdb", listOf("SELECT 1", "SELEC banana FROM FROM"))
        assertNotNull(report)
        assertTrue(!report.allAccepted)
        val rejected = report.rejected.single()
        assertEquals(1, rejected.index)
        assertNotNull(rejected.error)
    }

    @Test
    fun `advisory engines verify with the advisory flag set`() {
        // brikk-sql-verify 0.4.0 gives postgres/mysql/hive/clickhouse a lightweight
        // ShardingSphere grammar oracle: available, cross-platform, but advisory.
        for (engine in listOf("postgres", "mysql", "hive", "clickhouse")) {
            val report = service.verify(engine, listOf("SELECT 1"))
            assertNotNull(report, "$engine should have an advisory verifier in 0.4.0")
            assertTrue(report.advisory, "$engine verdicts must be marked advisory")
            assertTrue(report.verdicts.single().verified, "$engine SELECT 1 should have run")
        }
    }

    @Test
    fun `an advisory rejection is a soft hint, never a hard rejection`() {
        // Garbage is rejected, but on an advisory engine it must land in advisoryRejected
        // (non-blocking) and never in hardRejected (which gates Execute).
        val report = service.verify("postgres", listOf("SELEC banana FROM FROM"))
        assertNotNull(report)
        if (!report.allAccepted) {
            assertTrue(report.hardRejected.isEmpty(), "advisory rejects must not be hard")
            assertTrue(report.advisoryRejected.isNotEmpty(), "the reject should be advisory")
        }
    }

    @Test
    fun `unknown engines report unavailable`() {
        assertNull(service.verify("sybase", listOf("SELECT 1")))
        // Offered dialects with no verifier degrade the same way (null, never a throw).
        assertNull(service.verify("bigquery", listOf("SELECT 1")))
        assertNull(service.verify("datafusion", listOf("SELECT 1")))
    }

    @Test
    fun `trino verifier never throws - available or gracefully absent`() {
        // On a Java 25+ runtime (the IDE's JBR 25) this returns a working verifier; on
        // smaller JVMs (CI) trino-parser's class files can't load and this must be null.
        val verifier = service.verifierFor("trino")
        assertNotNull(verifier)
        val result = verifier.verify("SELECT 1")
        assertTrue(result == VerifyResult.Accepted || result is VerifyResult.Unavailable)
    }

    @Test
    fun `duckdb distinguishes unsupported statements from an unavailable verifier`() {
        val owned = VerifierService()
        try {
            val report = owned.verify("duckdb", listOf("SELECT 1", "CREATE TABLE probe AS SELECT 1"))!!
            assertTrue(!report.allAccepted)
            assertTrue(report.unverified.single().result is VerifyResult.Unverified)
            assertTrue(report.unavailable.isEmpty())
            (owned.verifierFor("duckdb") as AutoCloseable).close()
            val closed = owned.verify("duckdb", listOf("SELECT 1"))!!
            assertTrue(!closed.allAccepted)
            assertTrue(closed.unavailable.single().result is VerifyResult.Unavailable)
            assertTrue(closed.rejected.isEmpty())
        } finally {
            owned.dispose()
        }
    }

    @Test
    fun `shared advisory parsers work with a foreign context loader and restore it`() {
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        val foreign = object : ClassLoader(null) {}
        val owned = VerifierService()
        try {
            thread.contextClassLoader = foreign
            for (engine in listOf("postgres", "mysql", "hive", "clickhouse")) {
                val report = owned.verify(engine, listOf("SELECT 1", "SELEC banana FROM FROM"))!!
                assertEquals(VerifyResult.Accepted, report.verdicts.first().result)
                assertTrue(report.advisoryRejected.isNotEmpty())
                assertTrue(thread.contextClassLoader === foreign)
            }
        } finally {
            thread.contextClassLoader = previous
            owned.dispose()
        }
    }

    @Test
    fun `bundled doris verifier is required and checks grammar`() {
        val verifier = service.verifierFor("doris")
        assertNotNull(verifier)
        assertTrue(verifier is DorisVerifier)
        assertTrue(org.apache.doris.sqlparser.DorisSqlParser::class.java
            .getResource("DorisSqlParser.class")!!.toString().contains("4.1.4-gad35a140c7fd"))
        assertTrue(verifier.verify("SELECT 1") == VerifyResult.Accepted)
        assertTrue(verifier.verify("SELEC banana FROM FROM") is VerifyResult.Rejected)
    }

    @Test
    fun `doris parser ignores external discovery properties and service stays disposed`() {
        val property = "brikk.doris.parser.jar"
        val original = System.getProperty(property)
        System.setProperty(property, "/missing/untrusted/doris-fe-sql-parser.jar")
        val owned = VerifierService()
        try {
            assertTrue(owned.verify("doris", listOf("SELECT 1"))!!.allAccepted)
            assertEquals("/missing/untrusted/doris-fe-sql-parser.jar", System.getProperty(property))
            owned.dispose()
            assertNull(owned.verifierFor("duckdb"))
            assertNull(owned.verify("doris", listOf("SELECT 1")))
        } finally {
            owned.dispose()
            if (original == null) System.clearProperty(property) else System.setProperty(property, original)
        }
    }

    companion object {
        private val service = VerifierService()

        @JvmStatic
        @AfterClass
        fun closeVerifiers() {
            service.dispose()
        }
    }
}

package dev.sort.sqltranspiler

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
    fun `postgres has no native verifier by design`() {
        assertNull(service.verify("postgres", listOf("SELECT 1")))
    }

    @Test
    fun `unknown engines report unavailable`() {
        assertNull(service.verify("sybase", listOf("SELECT 1")))
    }

    @Test
    fun `trino verifier never throws - available or gracefully absent`() {
        // On a Java 25+ runtime (the IDE's JBR 25) this returns a working verifier; on
        // smaller JVMs (CI) trino-parser's class files can't load and this must be null.
        val verifier = service.verifierFor("trino")
        if (verifier != null) {
            assertTrue(verifier.verify("SELECT 1").accepted)
        }
    }

    @Test
    fun `doris verifier resolves and verifies when the FE parser is reachable`() {
        // In this test env the doris plugin dependency puts its vendored FE parser jar
        // on the classpath, so the verifier resolves via Class.forName. In the IDE,
        // plugin classloaders are isolated and VerifierService discovers the jar from
        // the installed doris plugin instead (brikk.doris.parser.jar property). Either
        // way: available -> engine-exact verdicts; unavailable -> null, never a throw.
        val verifier = service.verifierFor("doris")
        if (verifier != null) {
            assertTrue(verifier.verify("SELECT 1").accepted)
            assertTrue(!verifier.verify("SELEC banana FROM FROM").accepted)
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

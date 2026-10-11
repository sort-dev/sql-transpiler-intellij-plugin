package dev.sort.sqltranspiler

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerificationReportTest {
    private val accepted = VerifierService.StatementVerdict(0, accepted = true)
    private val unavailable = VerifierService.StatementVerdict(1, accepted = false, verified = false, error = "parse-only API unsupported")

    @Test fun `a partly checked script is never fully accepted`() {
        val report = VerifierService.Report("duckdb", listOf(accepted, unavailable))
        assertFalse(report.allAccepted)
        assertEquals(listOf(unavailable), report.unverified)
        assertTrue(report.hardRejected.isEmpty())
    }

    @Test fun `empty and unavailable checks are not acceptance`() {
        assertFalse(VerifierService.Report("duckdb", emptyList()).allAccepted)
        assertFalse(VerifierService.Report("duckdb", listOf(unavailable)).allAccepted)
        assertFalse(VerifierService.Report("duckdb", listOf(unavailable.copy(accepted = true))).allAccepted)
    }

    @Test fun `full acceptance and rejection retain their authority`() {
        assertTrue(VerifierService.Report("duckdb", listOf(accepted, accepted.copy(index = 1))).allAccepted)
        val rejected = accepted.copy(accepted = false)
        assertEquals(listOf(rejected), VerifierService.Report("duckdb", listOf(rejected)).hardRejected)
        assertTrue(VerifierService.Report("postgres", listOf(rejected.copy(advisory = true))).hardRejected.isEmpty())
    }
}

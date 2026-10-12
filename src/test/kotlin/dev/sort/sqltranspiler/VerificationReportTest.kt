package dev.sort.sqltranspiler

import dev.brikk.house.sql.verify.VerifyError
import dev.brikk.house.sql.verify.VerifyResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerificationReportTest {
    private val accepted = VerifierService.StatementVerdict(0, VerifyResult.Accepted)
    private val unverified = VerifierService.StatementVerdict(1, VerifyResult.Unverified("statement unsupported"))
    private val unavailable = VerifierService.StatementVerdict(2, VerifyResult.Unavailable("parser unavailable"))

    @Test fun `a partly checked script is never fully accepted`() {
        val report = VerifierService.Report("duckdb", listOf(accepted, unverified, unavailable))
        assertFalse(report.allAccepted)
        assertEquals(listOf(unverified), report.unverified)
        assertEquals(listOf(unavailable), report.unavailable)
        assertTrue(report.hardRejected.isEmpty())
    }

    @Test fun `empty unverified and unavailable checks are not acceptance`() {
        assertFalse(VerifierService.Report("duckdb", emptyList()).allAccepted)
        for (verdict in listOf(unverified, unavailable)) {
            assertFalse(verdict.accepted)
            assertFalse(verdict.verified)
            assertFalse(VerifierService.Report("duckdb", listOf(verdict)).allAccepted)
        }
    }

    @Test fun `full acceptance and rejection retain their authority and all UTF16 errors`() {
        assertTrue(VerifierService.Report("duckdb", listOf(accepted, accepted.copy(index = 1))).allAccepted)
        val sql = "SELECT '😀', FROM"
        val errors = listOf(VerifyError.atOffset(sql, "first", 13, 17), VerifyError("second"))
        val rejected = accepted.copy(result = VerifyResult.Rejected(errors))
        assertEquals(errors, rejected.errors)
        assertEquals(13, rejected.errors.first().offset)
        assertEquals(14, rejected.errors.first().column)
        assertEquals(listOf(rejected), VerifierService.Report("duckdb", listOf(rejected)).hardRejected)
        assertTrue(VerifierService.Report("postgres", listOf(rejected.copy(advisory = true))).hardRejected.isEmpty())
    }
}

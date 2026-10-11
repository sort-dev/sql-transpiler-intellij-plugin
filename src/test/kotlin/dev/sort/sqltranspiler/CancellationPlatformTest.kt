package dev.sort.sqltranspiler

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.verify.SqlVerifier
import dev.brikk.house.sql.verify.VerifyResult
import java.util.Optional
import java.util.concurrent.CancellationException
import kotlin.test.assertFailsWith

class CancellationPlatformTest : BasePlatformTestCase() {
    fun testAlreadyCanceledInputNeverReachesVerification() {
        val indicator = ProgressIndicatorBase().apply { cancel() }
        var verified = false
        assertFailsWith<ProcessCanceledException> {
            ProgressManager.getInstance().runProcess(Runnable {
                TranspileFlow.computeTranspilation("SELECT 1", "mysql", "duckdb") {
                    verified = true
                    null
                }
            }, indicator)
        }
        assertFalse(verified)
    }

    fun testCancellationDuringLastVerificationCannotReturnAnApprovedResult() {
        val cache = TranspileApprovalCache()
        val sql = "SELECT 1"
        val result = BrikkTranspiler.transpile(sql, "mysql", "duckdb") as BrikkTranspiler.TranspileOutcome.Success
        val key = cache.key(sql, "mysql", "duckdb")
        cache.approve(key, result.sql)
        val indicator = ProgressIndicatorBase()
        var reachedExecution = false
        assertFailsWith<ProcessCanceledException> {
            ProgressManager.getInstance().runProcess(Runnable {
                val (outcome, report) = TranspileFlow.computeTranspilation(sql, "mysql", "duckdb") {
                    indicator.cancel()
                    VerifierService.Report("duckdb", listOf(VerifierService.StatementVerdict(0, true)))
                }
                val success = outcome as BrikkTranspiler.TranspileOutcome.Success
                if (cache.isApproved(key, success.sql) && FindingPolicy.canExecute(success, report)) reachedExecution = true
            }, indicator)
        }
        assertFalse(reachedExecution)
    }

    fun testVerifierNeverSwallowsCancellationOrFatalErrors() {
        for (failure in listOf(ProcessCanceledException(), CancellationException("cancel"), AssertionError("fatal"))) {
            val service = VerifierService()
            var calls = 0
            val fake = object : SqlVerifier {
                override val engine = "fake"
                override fun verify(sql: String): VerifyResult { calls++; throw failure }
                override fun verifyExpression(sql: String): VerifyResult = verify(sql)
            }
            @Suppress("UNCHECKED_CAST")
            val map = VerifierService::class.java.getDeclaredField("verifiers").let {
                it.isAccessible = true
                it.get(service) as MutableMap<String, Optional<SqlVerifier>>
            }
            map["fake"] = Optional.of(fake)
            try {
                val actual = assertFailsWith<Throwable> { service.verify("fake", listOf("SELECT 1", "SELECT 2")) }
                assertSame(failure, actual)
                assertEquals(1, calls)
            } finally { service.dispose() }
        }
    }
}

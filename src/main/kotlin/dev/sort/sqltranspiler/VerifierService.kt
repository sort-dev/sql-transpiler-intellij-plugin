package dev.sort.sqltranspiler

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import dev.brikk.house.sql.verify.DorisVerifier
import dev.brikk.house.sql.verify.ParseOnlyDuckdbVerifier
import dev.brikk.house.sql.verify.ShardingSphereVerifier
import dev.brikk.house.sql.verify.SqlVerifier
import dev.brikk.house.sql.verify.TrinoVerifier
import dev.brikk.house.sql.verify.UnavailableVerifier
import dev.brikk.house.sql.verify.VerifyError
import dev.brikk.house.sql.verify.VerifyResult
import java.util.Optional
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.APP)
class VerifierService : Disposable {
    private val log = logger<VerifierService>()
    private val verifiers = ConcurrentHashMap<String, Optional<SqlVerifier>>()
    private var disposed = false

    data class StatementVerdict(
        val index: Int,
        val result: VerifyResult,
        val advisory: Boolean = false,
    ) {
        val accepted: Boolean get() = result == VerifyResult.Accepted
        val verified: Boolean get() = accepted || result is VerifyResult.Rejected
        val errors: List<VerifyError> get() = (result as? VerifyResult.Rejected)?.errors.orEmpty()
        val error: String? get() = when (val verdict = result) {
            VerifyResult.Accepted -> null
            is VerifyResult.Rejected -> verdict.error.message
            is VerifyResult.Unverified -> verdict.reason
            is VerifyResult.Unavailable -> verdict.reason
        }
    }

    data class Report(val engine: String, val verdicts: List<StatementVerdict>) {
        val allAccepted: Boolean get() = verdicts.isNotEmpty() && verdicts.all { it.accepted }
        val unverified: List<StatementVerdict> get() = verdicts.filter { it.result is VerifyResult.Unverified }
        val unavailable: List<StatementVerdict> get() = verdicts.filter { it.result is VerifyResult.Unavailable }
        val rejected: List<StatementVerdict> get() = verdicts.filter { it.result is VerifyResult.Rejected }
        val hardRejected: List<StatementVerdict> get() = rejected.filter { !it.advisory }
        val advisoryRejected: List<StatementVerdict> get() = rejected.filter { it.advisory }
        val advisory: Boolean get() = verdicts.any { it.advisory }
    }

    @Synchronized
    fun verify(target: String, statements: List<String>): Report? {
        ProgressManager.checkCanceled()
        val verifier = verifierFor(target) ?: return null
        return Report(target, statements.mapIndexed { index, sql ->
            ProgressManager.checkCanceled()
            val result = try {
                verifier.verify(sql)
            } catch (t: ProcessCanceledException) {
                throw t
            } catch (t: CancellationException) {
                throw t
            } catch (t: Exception) {
                log.warn("verifier '${verifier.engine}' failed on statement $index", t)
                VerifyResult.Unavailable("verifier failure: ${t.message}")
            }
            ProgressManager.checkCanceled()
            StatementVerdict(index, result, verifier.advisory)
        })
    }

    @Synchronized
    fun verifierFor(dialect: String): SqlVerifier? {
        if (disposed) return null
        return verifiers.computeIfAbsent(dialect.lowercase()) { name ->
            ProgressManager.checkCanceled()
            Optional.ofNullable(createVerifier(name))
        }.orElse(null)
    }

    private fun createVerifier(name: String): SqlVerifier? = try {
        when (name) {
            "duckdb" -> ParseOnlyDuckdbVerifier()
            "doris" -> DorisVerifier.create()
            "trino" -> TrinoVerifier()
            else -> ShardingSphereVerifier.forEngine(name)
        }
    } catch (t: ProcessCanceledException) {
        throw t
    } catch (t: CancellationException) {
        throw t
    } catch (t: LinkageError) {
        unavailable(name, "${t::class.simpleName}: ${t.message}")
    } catch (t: Exception) {
        ProgressManager.checkCanceled()
        unavailable(name, t.message ?: t::class.java.simpleName)
    }

    private fun unavailable(engine: String, reason: String): SqlVerifier {
        log.warn("native verifier for '$engine' unavailable: $reason")
        return UnavailableVerifier(engine, reason)
    }

    @Synchronized
    override fun dispose() {
        disposed = true
        for (holder in verifiers.values) {
            val verifier = holder.orElse(null) ?: continue
            (verifier as? AutoCloseable)?.let {
                try {
                    it.close()
                } catch (t: Exception) {
                    log.warn("closing verifier '${verifier.engine}' failed", t)
                }
            }
        }
        verifiers.clear()
    }
}

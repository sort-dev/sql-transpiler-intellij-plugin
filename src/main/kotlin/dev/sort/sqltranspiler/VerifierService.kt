package dev.sort.sqltranspiler

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import java.util.concurrent.CancellationException
import dev.brikk.house.sql.verify.SqlVerifier
import dev.brikk.house.sql.verify.SqlVerifiers
import dev.brikk.house.sql.verify.VerifyResult
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Native-grammar verification: runs generated SQL through the target engine's *own*
 * parser (brikk-sql-verify oracles), so the review dialog can say exactly what the
 * engine would say — before anything executes.
 *
 * Two tiers of verdict (brikk-sql-verify 0.4.0), resolved lazily and cached per engine:
 *
 *  AUTHORITATIVE (the engine's real parser; a rejection is a hard error):
 *  - trino: embedded io.trino parser — requires a Java 25 runtime (fine in-IDE: the
 *    261/262 lines run on JBR 25; guarded so a smaller JVM just reports "unavailable");
 *  - duckdb: embedded in-memory DuckDB over JDBC;
 *  - doris: the bundled, source-pinned Doris 4.1.4 grammar;
 *
 *  ADVISORY ([VerifyResult.advisory] == true; a re-implemented ShardingSphere grammar that
 *  can false-reject/false-accept, so a rejection is a non-blocking hint, never a hard error):
 *  - postgres, mysql, hive, clickhouse.
 *
 *  The real-engine ClickHouse/Postgres oracles (embedded PG boot, chdb) live in the heavy
 *  brikk-sql-oracle module, which the plugin deliberately does not depend on.
 *
 *  Every other engine has no verifier — verify() returns null (engine-exact or nothing).
 */
@Service(Service.Level.APP)
class VerifierService : Disposable {

    private val log = logger<VerifierService>()
    private val verifiers = ConcurrentHashMap<String, Optional<SqlVerifier>>()
    private var disposed = false

    /** One statement's verdict from the engine's own parser. */
    data class StatementVerdict(
        val index: Int,
        val accepted: Boolean,
        val error: String? = null,
        val line: Int? = null,
        val col: Int? = null,
        /** False when the check couldn't run (engine unavailable): treat as neither pass nor fail. */
        val verified: Boolean = true,
        /** True for the ShardingSphere advisory tier: a rejection is a hint, not a hard error. */
        val advisory: Boolean = false,
    )

    /** Verification of a full (possibly multi-statement) transpile output. */
    data class Report(val engine: String, val verdicts: List<StatementVerdict>) {
        /** Verdicts whose check actually ran. */
        private val ran: List<StatementVerdict> get() = verdicts.filter { it.verified }

        /** Full-script acceptance requires an actual check for every statement. */
        val allAccepted: Boolean get() = verdicts.isNotEmpty() && verdicts.all { it.verified && it.accepted }

        val unverified: List<StatementVerdict> get() = verdicts.filterNot { it.verified }

        /** Rejections that actually ran — shown in the review dialog (severity depends on [advisory]). */
        val rejected: List<StatementVerdict> get() = ran.filter { !it.accepted }

        /** Authoritative rejections (real engine parser) — these are hard errors that gate Execute. */
        val hardRejected: List<StatementVerdict> get() = rejected.filter { !it.advisory }

        /** Advisory rejections (re-implemented grammar) — non-blocking hints; may be false positives. */
        val advisoryRejected: List<StatementVerdict> get() = rejected.filter { it.advisory }

        /** True when this engine's verifier is the advisory tier (affects wording, not blocking). */
        val advisory: Boolean get() = verdicts.any { it.advisory }
    }

    /**
     * Verifies each rendered statement under the [target] engine's native parser.
     * Returns null when no verifier is available for the engine.
     */
    @Synchronized
    fun verify(target: String, statements: List<String>): Report? {
        ProgressManager.checkCanceled()
        val verifier = verifierFor(target) ?: return null
        return Report(
            engine = target,
            verdicts = statements.mapIndexed { index, sql ->
                ProgressManager.checkCanceled()
                val result = try {
                    verifier.verify(sql)
                } catch (t: ProcessCanceledException) {
                    throw t
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Exception) {
                    log.warn("verifier '${verifier.engine}' failed on statement $index", t)
                    // A crash is "couldn't verify" (verified = false), not a rejection: never
                    // block or scare the user on our own verifier failure.
                    VerifyResult(false, "verifier failure: ${t.message}", null, null, false, null, false)
                }
                ProgressManager.checkCanceled()
                StatementVerdict(
                    index = index,
                    accepted = result.accepted,
                    error = result.error ?: result.warning,
                    line = result.line,
                    col = result.col,
                    verified = result.verified,
                    advisory = result.advisory,
                )
            },
        )
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
        // Note: the advisory (ShardingSphere) grammars are generated by ANTLR 4.10.1 but
        // run against antlr4-runtime 4.13.x, so the first parse prints a one-time, harmless
        // "ANTLR Tool version ... does not match the current runtime version" line straight
        // to System.err from the parser's static initializer. There's no logger category to
        // mute and swapping System.err in a shared IDE process isn't acceptable — left as is
        // (upstream may suppress it eventually).
        when (name) {
            "duckdb" -> ParseOnlyDuckdbVerifier()
            "doris" -> BundledDorisVerifier()
            else -> SqlVerifiers.forEngine(name)
        }
    } catch (t: ProcessCanceledException) {
        throw t
    } catch (t: CancellationException) {
        throw t
    } catch (t: LinkageError) {
        // UnsupportedClassVersionError (trino-parser needs class-file 69 / Java 25),
        // NoClassDefFoundError, native-lib failures: degrade to "unavailable".
        log.warn("native verifier for '$name' unavailable: ${t::class.simpleName}: ${t.message}")
        null
    } catch (t: Exception) {
        ProgressManager.checkCanceled()
        log.warn("native verifier for '$name' unavailable: ${t.message}")
        null
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

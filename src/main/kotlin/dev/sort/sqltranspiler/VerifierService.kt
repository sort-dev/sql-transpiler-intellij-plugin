package dev.sort.sqltranspiler

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
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
 * Availability is per engine and resolved lazily (cold starts are paid once and cached):
 *
 *  - trino: embedded io.trino parser — requires a Java 25 runtime (fine in-IDE: the
 *    261/262 lines run on JBR 25; guarded so a smaller JVM just reports "unavailable");
 *  - duckdb: embedded in-memory DuckDB over JDBC;
 *  - doris: the Doris FE parser jar, discovered from the installed
 *    dev.sort.doris-intellij-plugin (which vendors the exact jar) via the
 *    `brikk.doris.parser.jar` system property;
 *  - postgres and others: no native verifier (by design upstream — engine-exact or nothing).
 */
@Service(Service.Level.APP)
class VerifierService : Disposable {

    private val log = logger<VerifierService>()
    private val verifiers = ConcurrentHashMap<String, Optional<SqlVerifier>>()

    /** One statement's verdict from the engine's own parser. */
    data class StatementVerdict(
        val index: Int,
        val accepted: Boolean,
        val error: String? = null,
        val line: Int? = null,
        val col: Int? = null,
    )

    /** Verification of a full (possibly multi-statement) transpile output. */
    data class Report(val engine: String, val verdicts: List<StatementVerdict>) {
        val allAccepted: Boolean get() = verdicts.all { it.accepted }
        val rejected: List<StatementVerdict> get() = verdicts.filter { !it.accepted }
    }

    /**
     * Verifies each rendered statement under the [target] engine's native parser.
     * Returns null when no verifier is available for the engine.
     */
    fun verify(target: String, statements: List<String>): Report? {
        val verifier = verifierFor(target) ?: return null
        return Report(
            engine = target,
            verdicts = statements.mapIndexed { index, sql ->
                val result = try {
                    verifier.verify(sql)
                } catch (t: Throwable) {
                    log.warn("verifier '${verifier.engine}' failed on statement $index", t)
                    VerifyResult(accepted = false, error = "verifier failure: ${t.message}")
                }
                StatementVerdict(index, result.accepted, result.error, result.line, result.col)
            },
        )
    }

    fun verifierFor(dialect: String): SqlVerifier? =
        verifiers.computeIfAbsent(dialect.lowercase()) { name ->
            Optional.ofNullable(createVerifier(name))
        }.orElse(null)

    private fun createVerifier(name: String): SqlVerifier? = try {
        if (name == "doris") ensureDorisParserJarProperty()
        SqlVerifiers.forEngine(name)
    } catch (t: Throwable) {
        // UnsupportedClassVersionError (trino-parser needs class-file 69 / Java 25),
        // NoClassDefFoundError, native-lib failures: degrade to "unavailable".
        log.warn("native verifier for '$name' unavailable: ${t::class.simpleName}: ${t.message}")
        null
    }

    /**
     * The installed doris plugin vendors the exact Doris FE parser jar brikk-sql-verify
     * expects; point the discovery property at it when present.
     */
    private fun ensureDorisParserJarProperty() {
        try {
            if (System.getProperty(DORIS_JAR_PROPERTY) != null) return
            val dorisPlugin = PluginManagerCore.getPlugin(PluginId.getId(DORIS_PLUGIN_ID)) ?: return
            val jar = dorisPlugin.pluginPath?.resolve("lib")?.toFile()
                ?.listFiles { f -> f.name.startsWith("doris-fe-sql-parser") && f.name.endsWith(".jar") }
                ?.firstOrNull() ?: return
            System.setProperty(DORIS_JAR_PROPERTY, jar.absolutePath)
            log.info("doris parser jar discovered from $DORIS_PLUGIN_ID: ${jar.name}")
        } catch (t: Throwable) {
            // Plugin manager unavailable (plain unit tests) or IO trouble: stay unavailable.
            log.debug("doris parser jar discovery skipped: ${t.message}")
        }
    }

    override fun dispose() {
        for (holder in verifiers.values) {
            val verifier = holder.orElse(null) ?: continue
            (verifier as? AutoCloseable)?.let {
                try {
                    it.close()
                } catch (t: Throwable) {
                    log.warn("closing verifier '${verifier.engine}' failed", t)
                }
            }
        }
        verifiers.clear()
    }

    private companion object {
        private const val DORIS_PLUGIN_ID = "dev.sort.doris-intellij-plugin"
        private const val DORIS_JAR_PROPERTY = "brikk.doris.parser.jar"
    }
}

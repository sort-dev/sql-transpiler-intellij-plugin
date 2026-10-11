package dev.sort.sqltranspiler

import com.intellij.openapi.progress.ProgressManager
import dev.brikk.house.sql.verify.SqlVerifier
import dev.brikk.house.sql.verify.VerifyResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.duckdb.DuckDBDriver
import java.sql.Connection
import java.sql.SQLException
import java.util.Properties

/** Only the parser runs. Unsupported serialization never falls back to SQL binding. */
internal class ParseOnlyDuckdbVerifier : SqlVerifier, AutoCloseable {
    override val engine = "duckdb"
    private var connection: Connection? = null
    private var closed = false

    @Synchronized
    override fun verify(sql: String): VerifyResult {
        ProgressManager.checkCanceled()
        if (closed) return unavailable("Verifier has been disposed.")
        val document = try {
            val conn = connection ?: DuckDBDriver().connect("jdbc:duckdb:", Properties().apply {
                setProperty("enable_external_access", "false")
                setProperty("autoinstall_known_extensions", "false")
                setProperty("autoload_known_extensions", "false")
                setProperty("threads", "1")
                setProperty("memory_limit", "256MB")
            }).also { connection = it }
            // This DuckDB function requires a constant VARCHAR, not a JDBC parameter.
            val literal = "'" + sql.replace("'", "''") + "'"
            conn.createStatement().use { statement ->
                statement.executeQuery("SELECT json_serialize_sql($literal)").use { result ->
                    check(result.next())
                    result.getString(1)
                }
            }
        } catch (e: SQLException) {
            return unavailable(e.message ?: "Parser connection failed.")
        }
        ProgressManager.checkCanceled()
        val json = Json.parseToJsonElement(document).jsonObject
        if (json["error"]?.jsonPrimitive?.booleanOrNull == false) return VerifyResult(accepted = true)
        val type = json["error_type"]?.jsonPrimitive?.contentOrNull
        val message = json["error_message"]?.jsonPrimitive?.contentOrNull ?: "No parser detail."
        if (type != "parser") return unavailable("Parse-only verification cannot check this statement: $message")
        // The adapter does not claim a column until DuckDB's offset units are normalized.
        return VerifyResult(accepted = false, error = message)
    }

    override fun verifyExpression(sql: String): VerifyResult = verify("SELECT $sql")

    private fun unavailable(message: String) = VerifyResult(
        accepted = false, verified = false, warning = "DuckDB verification unavailable: $message",
    )

    @Synchronized
    override fun close() {
        closed = true
        val owned = connection
        connection = null
        owned?.close()
    }
}

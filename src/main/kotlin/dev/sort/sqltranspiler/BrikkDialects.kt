package dev.sort.sqltranspiler

import dev.brikk.house.sql.dialects.Dialects

/**
 * The plugin-side registry surface over brikk-sql's [Dialects]: the user-facing dialect
 * list plus mapping from IDE SQL language ids (DataGrip dialect languages) to brikk
 * dialect names.
 */
object BrikkDialects {

    /**
     * Canonical brikk dialect names offered in pickers, alphabetical by [displayName]
     * (what the user reads in the popup — so "spark2" sorts as "Spark").
     */
    val names: List<String> = listOf(
        "bigquery",
        "clickhouse",
        "datafusion",
        "doris",
        "duckdb",
        "hive",
        "mysql",
        "postgres",
        "presto",
        // Offered as "Spark"; backed by brikk-sql's spark2 dialect.
        "spark2",
        "trino",
    )

    fun isSupported(name: String): Boolean = Dialects.forNameOrNull(name) != null

    /** Human label for a canonical dialect name. */
    fun displayName(name: String): String = when (name.lowercase()) {
        "mysql" -> "MySQL"
        "doris" -> "Doris"
        "trino" -> "Trino"
        "presto" -> "Presto"
        "duckdb" -> "DuckDB"
        "postgres" -> "PostgreSQL"
        "clickhouse" -> "ClickHouse"
        "hive" -> "Hive"
        "spark2" -> "Spark"
        "datafusion" -> "DataFusion"
        "bigquery" -> "BigQuery"
        else -> name
    }

    /**
     * Maps an IntelliJ SQL dialect language id (e.g. "MySQL", "PostgreSQL", "DorisSQL",
     * the file's `psiFile.language.id`) to a brikk dialect name, or null when the IDE
     * dialect has no brikk counterpart (generic SQL, Oracle, ...). Matching is
     * defensive/substring-based: DataGrip language ids are stable-ish but vendor
     * plugins (like doris-intellij-plugin's "DorisSQL") add their own.
     */
    fun fromLanguageId(languageId: String): String? {
        val id = languageId.lowercase()
        return when {
            "doris" in id -> "doris"
            "maria" in id || "mysql" in id -> "mysql"
            "postgres" in id -> "postgres"
            "clickhouse" in id -> "clickhouse"
            "duck" in id -> "duckdb"
            "trino" in id -> "trino"
            "presto" in id -> "presto"
            "spark" in id -> "spark2"
            "hive" in id -> "hive"
            "bigquery" in id -> "bigquery"
            "datafusion" in id -> "datafusion"
            else -> null
        }
    }
}

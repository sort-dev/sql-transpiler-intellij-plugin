package dev.sort.sqltranspiler

import com.intellij.database.Dbms
import dev.brikk.house.sql.intellij.dialect.SqlDialectRegistry
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrikkDialectsTest {

    @Test
    fun `shared registry labels and language identities are preserved`() {
        assertEquals(2, SqlDialectRegistry.VERSION)
        for (entry in SqlDialectRegistry.entries) {
            assertEquals(entry.label, BrikkDialects.displayName(entry.coreDialect.uppercase()))
            for (id in entry.languageIds) {
                assertEquals(entry.coreDialect, BrikkDialects.fromLanguageId(id), id)
            }
        }
        assertEquals("unknown", BrikkDialects.displayName("unknown"))
    }

    @Test
    fun `shared registry resolves stock and plugin dbms identities`() {
        for (entry in SqlDialectRegistry.entries) {
            for (dbms in entry.dbmsIds.mapNotNull(Dbms::byName)) {
                assertEquals(entry.coreDialect, BrikkDialects.fromDbms(dbms), dbms.name)
            }
        }
        val duckDb = listOf("DUCKDB", "DUCK").firstNotNullOf(Dbms::byName)
        assertEquals("duckdb", BrikkDialects.fromDbms(duckDb))
        assertNull(BrikkDialects.fromDbms(Dbms.UNKNOWN))
    }

    @Test
    fun `unregistered identities retain substring compatibility`() {
        for ((id, dialect) in listOf(
            "vendor Doris dialect" to "doris",
            "vendor MariaDB dialect" to "mysql",
            "vendor PostgreSQL dialect" to "postgres",
            "vendor ClickHouse dialect" to "clickhouse",
            "vendor DuckDB dialect" to "duckdb",
            "vendor Trino dialect" to "trino",
            "vendor Presto dialect" to "presto",
            "vendor Spark dialect" to "spark2",
            "vendor Hive dialect" to "hive",
            "vendor BigQuery dialect" to "bigquery",
            "vendor DataFusion dialect" to "datafusion",
        )) {
            assertNull(SqlDialectRegistry.forLanguageId(id))
            assertEquals(dialect, BrikkDialects.fromLanguageId(id), id)
        }
    }

    @Test
    fun `every offered dialect is supported by brikk-sql`() {
        for (name in BrikkDialects.names) {
            assertTrue(BrikkDialects.isSupported(name), "brikk-sql does not know '$name'")
        }
    }

    @Test
    fun `picker list is alphabetical by display name`() {
        val displayed = BrikkDialects.names.map { BrikkDialects.displayName(it) }
        assertEquals(
            displayed.sortedBy { it.lowercase() },
            displayed,
            "Transpile To/From pickers show dialects alphabetically",
        )
    }

    @Test
    fun `maps IDE language ids to brikk dialects`() {
        assertEquals("mysql", BrikkDialects.fromLanguageId("MySQL"))
        assertEquals("mysql", BrikkDialects.fromLanguageId("MariaDB"))
        assertEquals("postgres", BrikkDialects.fromLanguageId("PostgreSQL"))
        assertEquals("clickhouse", BrikkDialects.fromLanguageId("ClickHouse"))
        assertEquals("doris", BrikkDialects.fromLanguageId("DorisSQL"))
        assertEquals("duckdb", BrikkDialects.fromLanguageId("DuckDB"))
    }

    @Test
    fun `generic and unrelated ids map to null`() {
        assertNull(BrikkDialects.fromLanguageId("SQL"))
        assertNull(BrikkDialects.fromLanguageId("SQL92"))
        assertNull(BrikkDialects.fromLanguageId("GenericSQL"))
        assertNull(BrikkDialects.fromLanguageId("Oracle"))
        assertNull(BrikkDialects.fromLanguageId("TSQL"))
    }
}

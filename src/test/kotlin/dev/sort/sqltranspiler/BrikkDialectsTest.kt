package dev.sort.sqltranspiler

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrikkDialectsTest {

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
        assertNull(BrikkDialects.fromLanguageId("Oracle"))
        assertNull(BrikkDialects.fromLanguageId("TSQL"))
    }
}

package dev.sort.sqltranspiler

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DialectMarkerTest {

    @Test
    fun `marker text inside literals identifiers and block comments is not a boundary`() {
        for ((source, body) in listOf(
            "postgres" to "SELECT \$tag\$\n-- dialect: mysql\n\$tag\$;",
            "duckdb" to "SELECT \$\$\n-- dialect: mysql\n\$\$;",
            "doris" to "SELECT '\n-- dialect: mysql\n';",
            "mysql" to "SELECT `\n-- dialect: postgres\n`;",
            "bigquery" to "SELECT \"\"\"\n-- dialect: mysql\n\"\"\";",
            "postgres" to "/* outer\n/* inner */\n-- dialect: mysql\n*/\nSELECT 1;",
        )) {
            val text = "-- dialect: $source\n$body\n-- dialect: trino\nSELECT 2;"
            assertEquals(listOf(source, "trino"), DialectMarker.findAll(text).map { it.dialect }, text)
        }
    }

    @Test
    fun `unmarked prelude and unfinished constructs cannot invent markers`() {
        assertTrue(DialectMarker.findAll("SELECT \$\$\n-- dialect: mysql\n\$\$;").isEmpty())
        assertTrue(DialectMarker.findAll("/* unclosed\n-- dialect: mysql").isEmpty())
        assertEquals(listOf("doris"), DialectMarker.findAll("-- dialect: doris\nSELECT '\n-- dialect: mysql").map { it.dialect })
    }

    @Test
    fun `real markers survive CRLF comments and preceding grammar errors`() {
        val text = "-- dialect: postgres\r\nSELECT FROM;\r\n/* closed */\r\n-- dialect: mysql\r\nSELECT 2;"
        assertEquals(listOf("postgres", "mysql"), DialectMarker.findAll(text).map { it.dialect })
    }

    @Test
    fun `parses plain marker line`() {
        assertEquals("clickhouse", DialectMarker.parseLine("-- dialect: clickhouse"))
    }

    @Test
    fun `parses marker with flexible spacing and case`() {
        assertEquals("duckdb", DialectMarker.parseLine("  --   dialect :  DuckDB  "))
        assertEquals("mysql", DialectMarker.parseLine("--dialect:mysql"))
    }

    @Test
    fun `rejects non-marker comments`() {
        assertNull(DialectMarker.parseLine("-- dialect clickhouse"))
        assertNull(DialectMarker.parseLine("-- some dialect: clickhouse comment"))
        assertNull(DialectMarker.parseLine("select 1 -- dialect: clickhouse"))
        assertNull(DialectMarker.parseLine("# dialect: clickhouse"))
    }

    @Test
    fun `findAll returns markers in order with offsets`() {
        val text = """
            -- dialect: mysql
            select 1;

            -- dialect: clickhouse
            select 2;
        """.trimIndent()
        val markers = DialectMarker.findAll(text)
        assertEquals(listOf("mysql", "clickhouse"), markers.map { it.dialect })
        assertEquals(0, markers[0].lineStartOffset)
        assertTrue(markers[1].lineStartOffset > markers[0].lineEndOffset)
    }

    @Test
    fun `dialectAt picks closest marker above`() {
        val text = "-- dialect: mysql\nselect 1;\n-- dialect: doris\nselect 2;\n"
        assertEquals("mysql", DialectMarker.dialectAt(text, text.indexOf("select 1")))
        assertEquals("doris", DialectMarker.dialectAt(text, text.indexOf("select 2")))
        assertEquals("mysql", DialectMarker.dialectAt(text, 0))
    }

    @Test
    fun `dialectAt returns null before any marker`() {
        val text = "select 0;\n-- dialect: mysql\nselect 1;\n"
        assertNull(DialectMarker.dialectAt(text, 0))
    }

    @Test
    fun `segmentAfter spans to next marker`() {
        val text = "-- dialect: mysql\nselect 1;\n-- dialect: doris\nselect 2;\n"
        val markers = DialectMarker.findAll(text)
        val (start1, end1) = DialectMarker.segmentAfter(text, markers[0])
        assertEquals("select 1;\n", text.substring(start1, end1))
        val (start2, end2) = DialectMarker.segmentAfter(text, markers[1])
        assertEquals("select 2;\n", text.substring(start2, end2))
    }

    @Test
    fun `segmentAfter of trailing marker is empty`() {
        val text = "select 1;\n-- dialect: mysql"
        val marker = DialectMarker.findAll(text).single()
        val (start, end) = DialectMarker.segmentAfter(text, marker)
        assertEquals(start, end)
    }

    @Test
    fun `unknown dialect marker is found but unsupported`() {
        val marker = DialectMarker.findAll("-- dialect: exotic\nselect 1").single()
        assertEquals("exotic", marker.dialect)
        assertTrue(!marker.isSupported)
    }
}

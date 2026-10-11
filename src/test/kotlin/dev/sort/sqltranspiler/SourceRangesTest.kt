package dev.sort.sqltranspiler

import dev.sort.sqltranspiler.BrikkTranspiler.TranspileOutcome
import dev.sort.sqltranspiler.lang.BrikkSqlErrorAnnotator
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SourceRangesTest {
    @Test fun `trailing comments remain trivia rather than empty statements`() {
        for (comment in listOf(" -- done", "\n/* done */", "\n-- line one\n-- line two")) {
            val result = assertIs<TranspileOutcome.Success>(BrikkTranspiler.transpile("SELECT 1;$comment", "doris", "duckdb", false))
            assertEquals(1, result.statementCount)
            assertTrue(result.sql.endsWith(comment), result.sql)
        }
        val result = assertIs<TranspileOutcome.Success>(BrikkTranspiler.transpile("SELECT ';'; /* ; */ SELECT 2; -- ;", "doris", "duckdb", false))
        assertEquals(2, result.statementCount)
    }

    @Test fun `parse columns retain whitespace and same-line statement displacement`() {
        for (sql in listOf("SELECT )", "    SELECT )", "SELECT 1; SELECT )", "SELECT '😀'; SELECT )")) {
            val failure = assertIs<TranspileOutcome.Failure>(BrikkTranspiler.transpile(sql, "doris", "duckdb"))
            assertEquals(1, failure.line, sql)
            assertEquals(sql.indexOf(')') + 1, failure.col, sql)
        }
        val failure = assertIs<TranspileOutcome.Failure>(BrikkTranspiler.transpile("SELECT 1;\r\n    SELECT )", "doris", "duckdb"))
        assertEquals(2, failure.line)
        assertEquals(12, failure.col)
    }

    @Test fun `annotator marks actual token and leaves comment-only blocks quiet`() {
        val annotator = BrikkSqlErrorAnnotator()
        val sql = "-- dialect: doris\nSELECT 1;    SELECT )"
        val error = annotator.doAnnotate(BrikkSqlErrorAnnotator.Info(sql))!!.errors.single()
        assertEquals(sql.indexOf(')'), error.range.startOffset)
        assertEquals(sql.indexOf(')') + 1, error.range.endOffset)
        assertTrue(annotator.doAnnotate(BrikkSqlErrorAnnotator.Info("-- dialect: doris\n/* just a comment */"))!!.errors.isEmpty())
    }

    @Test fun `large scripts retain offsets without quadratic prefix work`() {
        val sql = (1..2000).joinToString("\n") { "SELECT $it;" }
        val result = assertIs<TranspileOutcome.Success>(BrikkTranspiler.transpile(sql, "doris", "duckdb", false))
        assertEquals(2000, result.statementCount)
        assertEquals(1998, result.statements.last().sourceLineOffset)
        assertEquals("SELECT 2000", result.statements.last().sql)
    }

    @Test fun `incomplete final construct prevents partial success`() {
        assertIs<TranspileOutcome.Failure>(BrikkTranspiler.transpile("SELECT 1; /* never closed ; SELECT 2", "doris", "duckdb"))
        assertIs<TranspileOutcome.Failure>(BrikkTranspiler.transpile("DELIMITER //\nSELECT 1;", "mysql", "duckdb"))
    }

    @Test fun `upstream snapshot itself rejects unclosed comments without plugin recovery`() {
        for (dialect in listOf("doris", "duckdb", "trino", "postgres", "mysql")) {
            val sql = "SELECT 1; /* unfinished ; SELECT 2"
            val tail = dev.brikk.house.sql.parser.SqlScriptSnapshot.parse(sql, dialect).statements.last()
            assertTrue(tail.boundaryError != null, "$dialect: $tail")
            assertTrue(tail.hasSql)
            assertEquals(sql.length, tail.end)
        }
    }
}

package dev.sort.sqltranspiler

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExecuteInputTest {
    @Test fun `literal and comment markers leave ordinary native SQL untouched`() {
        for (sql in listOf(
            "SELECT '|>' AS arrow",
            "SELECT 1 -- |> not a pipeline",
            "SELECT 1 /* |> */",
            "SELECT '|>'; SELECT 2",
            "SELECT 'it''s |> native'",
        )) {
            assertTrue(ExecuteFlow.canRunUnchanged(sql, "doris"), sql)
        }
    }

    @Test fun `claimed pipe and uncertain lexical input cannot run unchanged`() {
        for (sql in listOf(
            "FROM t |> SELECT id",
            "SELECT 1; FROM t |> WHERE",
            "SELECT '|>",
            "SELECT 'unterminated |> WHERE a",
            "SELECT 1 /* unterminated |> WHERE a",
            "FROM t |> SELECT id; FROM u |> SELECT id",
        )) {
            assertFalse(ExecuteFlow.canRunUnchanged(sql, "doris"), sql)
        }
        assertIs<BrikkTranspiler.TranspileOutcome.Failure>(
            BrikkTranspiler.transpile("SELECT 1; FROM t |> WHERE", "doris", "doris"),
        )
    }

    @Test fun `nested pipes are lowered by the current engine`() {
        val result = assertIs<BrikkTranspiler.TranspileOutcome.Success>(
            BrikkTranspiler.transpile("SELECT * FROM (FROM t |> SELECT id)", "doris", "duckdb"),
        )
        assertTrue(ExecuteFlow.canRunUnchanged(result.sql, "duckdb"))
        assertFalse(result.sql.contains("|>"))
        assertTrue(result.pipesDesugared)
    }

    @Test fun `native input without pipe markers still belongs to the engine`() {
        for (sql in listOf("SELECT engine_specific_syntax", "SELECT 'unterminated", "FROM t", "SELECT 1; SELECT 2")) {
            assertTrue(ExecuteFlow.canRunUnchanged(sql, "doris"), sql)
        }
    }

    @Test fun `star rename refuses execution without a complete layout in trino and presto`() {
        val sql = "FROM t |> RENAME a AS b"
        for (dialect in listOf("trino", "presto")) {
            assertFalse(ExecuteFlow.canRunUnchanged(sql, dialect), dialect)
            val failure = assertIs<BrikkTranspiler.TranspileOutcome.Failure>(
                BrikkTranspiler.transpile(sql, "duckdb", dialect),
            )
            assertTrue("star RENAME requires a complete column layout" in failure.message, failure.message)
        }
    }
}

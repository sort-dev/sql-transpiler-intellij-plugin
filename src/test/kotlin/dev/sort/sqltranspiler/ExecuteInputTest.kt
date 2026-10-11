package dev.sort.sqltranspiler

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExecuteInputTest {
    @Test fun `literal and comment markers leave ordinary native SQL untouched`() {
        for (sql in listOf("SELECT '|>' AS arrow", "SELECT 1 -- |> not a pipeline", "SELECT 1 /* |> */")) {
            assertTrue(ExecuteFlow.canRunUnchanged(sql, "doris"), sql)
        }
    }

    @Test fun `claimed pipe and uncertain lexical input cannot run unchanged`() {
        for (sql in listOf("FROM t |> SELECT id", "SELECT 1; FROM t |> WHERE", "SELECT '|>")) {
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
}

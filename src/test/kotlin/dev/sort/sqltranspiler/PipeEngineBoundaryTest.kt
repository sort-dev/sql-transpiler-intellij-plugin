package dev.sort.sqltranspiler

import dev.brikk.house.sql.generator.UnsupportedError
import dev.brikk.house.sql.shape.FindingKind
import dev.brikk.house.sql.shape.PipeLowering
import dev.brikk.house.sql.shape.Shape
import dev.brikk.house.sql.shape.ShapeCatalog
import dev.brikk.house.sql.shape.SqlFragment
import dev.brikk.house.sql.shape.lowerForExecution
import dev.brikk.house.sql.verify.VerifyResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PipeEngineBoundaryTest {
    private data class Refusal(val dialect: String, val sql: String, val construct: String)

    private val engineInvalid = listOf(
        Refusal("trino", "FROM t |> CALL exclude_columns(DESCRIPTOR(a))", "pipe CALL"),
        Refusal("trino", "FROM t |> STRAIGHT_JOIN u ON t.a = u.a", "STRAIGHT_JOIN"),
        Refusal("trino", "FROM t |> STRAIGHT_JOIN u USING (a)", "STRAIGHT_JOIN"),
        Refusal("trino", "FROM t |> ASOF JOIN u ON t.a = u.a AND t.b >= u.c", "ASOF JOIN"),
        Refusal("trino", "FROM t |> NATURAL JOIN u", "NATURAL JOIN"),
        Refusal("duckdb", "FROM t |> STRAIGHT_JOIN u ON t.a = u.a", "STRAIGHT_JOIN"),
        Refusal("duckdb", "FROM t |> STRAIGHT_JOIN u USING (a)", "STRAIGHT_JOIN"),
        Refusal("doris", "FROM t |> CALL f(1)", "pipe CALL"),
        Refusal("doris", "FROM t |> STRAIGHT_JOIN u ON t.a = u.a", "STRAIGHT_JOIN"),
        Refusal("doris", "FROM t |> NATURAL JOIN u", "NATURAL JOIN"),
        Refusal("clickhouse", "FROM t |> CALL view()", "pipe CALL"),
        Refusal("clickhouse", "FROM t |> STRAIGHT_JOIN u ON t.a = u.a", "STRAIGHT_JOIN"),
        Refusal("clickhouse", "FROM t |> RENAME a AS z", "RENAME"),
        Refusal("clickhouse", "FROM t |> SELECT a, b |> RENAME a AS z", "RENAME"),
        Refusal("presto", "FROM t |> STRAIGHT_JOIN u ON t.a = u.a", "STRAIGHT_JOIN"),
    )

    @Test fun `engine invalid stages refuse execution but keep reviewable preview findings`() {
        for (case in engineInvalid) {
            assertBoundary(case)
            assertBoundary(case.copy(sql = "WITH p AS (${case.sql}) SELECT * FROM p"))
        }
    }

    @Test fun `unsupported pivot and unpivot refuse execution but keep reviewable preview findings`() {
        for (dialect in listOf("presto", "trino", "mysql", "doris", "postgres", "clickhouse")) {
            for (stage in listOf("PIVOT (SUM(id) FOR payload IN (1, 2))", "UNPIVOT (v FOR k IN (id, payload))")) {
                assertBoundary(Refusal(dialect, "FROM t |> $stage", "PIVOT"))
            }
        }
    }

    @Test fun `trino and presto drop and set require a complete layout`() {
        for (dialect in listOf("trino", "presto")) {
            for (stage in listOf("DROP id", "SET id = 1", "SET id = id + 1")) {
                val sql = "FROM t |> $stage"
                val refused = assertIs<PipeLowering.Refused>(lowerForExecution(sql, dialect))
                assertTrue(if (stage.startsWith("DROP")) "EXCEPT" in refused.message else "REPLACE" in refused.message)
                assertFailsWith<UnsupportedError> { SqlFragment(sql, dialect).toExecutable(dialect) }
                assertIs<BrikkTranspiler.TranspileOutcome.Failure>(BrikkTranspiler.transpile(sql, dialect, dialect))
                assertFalse(ExecuteFlow.canRunUnchanged(sql, dialect))
            }
        }
    }

    @Test fun `supported native stages remain executable and accepted by native parsers`() {
        val service = VerifierService()
        try {
            for ((dialect, stages) in listOf(
                "duckdb" to listOf("CALL summary()", "NATURAL JOIN u", "ASOF JOIN u ON t.a = u.a AND t.b >= u.c", "RENAME a AS z", "DROP a", "SET a = 1", "PIVOT (SUM(a) FOR b IN (1, 2))", "UNPIVOT (v FOR k IN (a, b))"),
                "doris" to listOf("ASOF JOIN u ON t.a = u.a AND t.b >= u.c", "DROP a", "SET a = 1"),
            )) {
                for (stage in stages) {
                    val sql = "FROM t |> $stage"
                    assertIs<PipeLowering.Ok>(lowerForExecution(sql, dialect), "$dialect: $sql")
                    val preview = assertIs<BrikkTranspiler.TranspileOutcome.Success>(BrikkTranspiler.transpile(sql, dialect, dialect))
                    assertTrue(preview.findings.none { it.kind == FindingKind.UNSUPPORTED_TRANSLATION }, preview.findings.toString())
                    assertEquals(VerifyResult.Accepted, service.verify(dialect, preview.renderedStatements)!!.verdicts.single().result, "$dialect: ${preview.sql}")
                }
            }
        } finally {
            service.dispose()
        }
    }

    @Test fun `best effort preview preserves engine invalid output instead of hiding it`() {
        for ((dialect, sql, expected) in listOf(
            Triple("trino", "FROM t |> STRAIGHT_JOIN u ON t.a = u.a", "SELECT * FROM t STRAIGHT_JOIN u ON t.a = u.a"),
            Triple("clickhouse", "FROM t |> RENAME a AS z", "WITH __tmp1 AS (SELECT * FROM t), __tmp2 AS (SELECT * RENAME (a AS z) FROM __tmp1) SELECT * FROM __tmp2"),
            Triple("doris", "FROM t |> CALL f()", "SELECT * FROM F((SELECT * FROM t))"),
        )) {
            val preview = assertIs<BrikkTranspiler.TranspileOutcome.Success>(BrikkTranspiler.transpile(sql, dialect, dialect, pretty = false))
            assertEquals(expected, preview.sql)
            assertTrue(preview.findings.any { it.kind == FindingKind.UNSUPPORTED_TRANSLATION })
            assertEquals(preview.sql, preview.statements.single().result.sourceMap!!.output)
        }
    }

    @Test fun `complete layouts produce native trino drop and set projections`() {
        val complete = ShapeCatalog(tables = mapOf("t" to Shape.of("label" to "VARCHAR", "id" to "INT", "payload" to "BIGINT")))
        val service = VerifierService()
        try {
            for (dialect in listOf("trino", "presto")) {
                for ((stage, columns) in listOf(
                    "DROP id" to listOf("label", "payload"),
                    "SET id = 1" to listOf("label", "id", "payload"),
                    "SET payload = id, id = payload" to listOf("label", "id", "payload"),
                )) {
                    val sql = SqlFragment("FROM t |> $stage", dialect).toStandardSql(dialect, complete, expandStars = true)
                    assertFalse("* EXCEPT" in sql || "* REPLACE" in sql, sql)
                    assertEquals(columns, SqlFragment(sql, dialect).outputShape(complete).names())
                    assertEquals(VerifyResult.Accepted, service.verify("trino", listOf(sql))!!.verdicts.single().result, sql)
                }
            }
        } finally {
            service.dispose()
        }
    }

    private fun assertBoundary(case: Refusal) {
        val (dialect, sql, construct) = case
        assertFalse(ExecuteFlow.canRunUnchanged(sql, dialect), "$dialect: $sql")
        val refusal = assertIs<PipeLowering.Refused>(lowerForExecution(sql, dialect), "$dialect: $sql")
        assertTrue(construct in refusal.message, refusal.message)
        assertFailsWith<UnsupportedError>("$dialect: $sql") { SqlFragment(sql, dialect).toExecutable(dialect) }
        val preview = assertIs<BrikkTranspiler.TranspileOutcome.Success>(BrikkTranspiler.transpile(sql, dialect, dialect), "$dialect: $sql")
        assertTrue(preview.sql.isNotBlank())
        assertFalse("|>" in preview.sql, preview.sql)
        assertTrue(preview.findings.any { it.kind == FindingKind.UNSUPPORTED_TRANSLATION && construct in it.detail }, preview.findings.toString())
        val accepted = VerifierService.Report(dialect, preview.statements.map { VerifierService.StatementVerdict(it.index, VerifyResult.Accepted) })
        assertFalse(FindingPolicy.canExecute(preview, accepted), "$dialect: $sql")
    }
}

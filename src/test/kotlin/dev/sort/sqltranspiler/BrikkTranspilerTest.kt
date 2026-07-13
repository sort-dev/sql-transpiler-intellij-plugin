package dev.sort.sqltranspiler

import dev.brikk.house.sql.shape.FindingKind
import dev.brikk.house.sql.shape.Severity
import dev.sort.sqltranspiler.BrikkTranspiler.TranspileOutcome
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BrikkTranspilerTest {

    @Test
    fun `transpiles mysql to duckdb`() {
        val outcome = BrikkTranspiler.transpile(
            "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') FROM t",
            source = "mysql",
            target = "duckdb",
            pretty = false,
        )
        val success = assertIs<TranspileOutcome.Success>(outcome)
        assertEquals(1, success.statementCount)
        assertTrue("STRFTIME" in success.sql.uppercase(), "expected strftime in: ${success.sql}")
    }

    @Test
    fun `multi-statement input renders all statements`() {
        val outcome = BrikkTranspiler.transpile(
            "SELECT 1; SELECT 2",
            source = "mysql",
            target = "postgres",
            pretty = false,
        )
        val success = assertIs<TranspileOutcome.Success>(outcome)
        assertEquals(2, success.statementCount)
        assertTrue(success.sql.contains(";"), "statements should be ;-separated: ${success.sql}")
    }

    @Test
    fun `parse failure reports position`() {
        val outcome = BrikkTranspiler.transpile(
            "SELECT FROM WHERE GROUP)) nonsense((",
            source = "mysql",
            target = "duckdb",
        )
        assertIs<TranspileOutcome.Failure>(outcome)
    }

    @Test
    fun `unknown dialect is a failure not an exception`() {
        val outcome = BrikkTranspiler.transpile("SELECT 1", source = "sybase", target = "duckdb")
        val failure = assertIs<TranspileOutcome.Failure>(outcome)
        assertTrue("sybase" in failure.message)
    }

    @Test
    fun `blank input is a failure`() {
        assertIs<TranspileOutcome.Failure>(
            BrikkTranspiler.transpile("   \n  ", source = "mysql", target = "duckdb")
        )
    }

    @Test
    fun `unmappable functions surface as REFUSAL findings`() {
        // duckdb's read_parquet has no Doris counterpart: it lands verbatim in the
        // output and Doris's catalog does not register it -> UNMAPPABLE_FUNCTION.
        val outcome = BrikkTranspiler.transpile(
            "SELECT * FROM read_parquet('f.parquet')",
            source = "duckdb",
            target = "doris",
            pretty = false,
        )
        val success = assertIs<TranspileOutcome.Success>(outcome)
        val finding = success.findings.firstOrNull {
            it.kind == FindingKind.UNMAPPABLE_FUNCTION && it.subject.equals("read_parquet", ignoreCase = true)
        }
        assertTrue(finding != null, "expected read_parquet UNMAPPABLE_FUNCTION, got: ${success.findings}")
        assertTrue(finding.severity == Severity.REFUSAL)
        assertTrue(!success.isClean)
    }

    @Test
    fun `catalog-less targets get an informational NO_TARGET_CATALOG finding`() {
        val outcome = BrikkTranspiler.transpile(
            "SELECT 1",
            source = "mysql",
            target = "postgres",
            pretty = false,
        )
        val success = assertIs<TranspileOutcome.Success>(outcome)
        assertTrue(success.findings.any { it.kind == FindingKind.NO_TARGET_CATALOG })
        // Plugin policy: capability gaps inform, they don't gate execution.
        assertTrue(FindingPolicy.blockingRefusals(success, verification = null).isEmpty())
        assertTrue(FindingPolicy.informational(success).isNotEmpty())
    }

    @Test
    fun `findings attach to the offending statement in multi-statement input`() {
        val outcome = BrikkTranspiler.transpile(
            "SELECT 1; SELECT * FROM read_parquet('f.parquet')",
            source = "duckdb",
            target = "doris",
            pretty = false,
        )
        val success = assertIs<TranspileOutcome.Success>(outcome)
        assertEquals(2, success.statementCount)
        assertTrue(success.statements[0].findings.none { it.kind == FindingKind.UNMAPPABLE_FUNCTION })
        assertTrue(success.statements[1].findings.any { it.kind == FindingKind.UNMAPPABLE_FUNCTION })
    }

    @Test
    fun `blocking refusals gate execution and downgrade on verifier acceptance`() {
        val outcome = BrikkTranspiler.transpile(
            "SELECT * FROM read_parquet('f.parquet')",
            source = "duckdb",
            target = "doris",
            pretty = false,
        )
        val success = assertIs<TranspileOutcome.Success>(outcome)
        assertTrue(FindingPolicy.blockingRefusals(success, verification = null).isNotEmpty())

        // RAW_PASSTHROUGH downgrades when the native parser accepts that statement;
        // UNMAPPABLE_FUNCTION does not.
        val accepted = VerifierService.Report(
            engine = "doris",
            verdicts = listOf(VerifierService.StatementVerdict(0, accepted = true)),
        )
        assertTrue(
            FindingPolicy.blockingRefusals(success, accepted).isNotEmpty(),
            "verifier acceptance must not soften UNMAPPABLE_FUNCTION",
        )
    }

    @Test
    fun `pipe syntax is desugared for the target engine`() {
        val outcome = BrikkTranspiler.transpile(
            "FROM produce |> WHERE item <> 'bananas' |> SELECT item, sales",
            source = "doris",
            target = "doris",
            pretty = false,
        )
        val success = assertIs<TranspileOutcome.Success>(outcome)
        assertTrue(success.pipesDesugared, "expected pipesDesugared")
        assertTrue("|>" !in success.sql, "pipes must not reach the engine: ${success.sql}")
        assertTrue("SELECT" in success.sql.uppercase() && "WHERE" in success.sql.uppercase(), success.sql)
    }

    @Test
    fun `pipe inside a string literal does not trigger desugaring`() {
        val outcome = BrikkTranspiler.transpile(
            "SELECT '|>' AS arrow FROM t",
            source = "mysql",
            target = "mysql",
            pretty = false,
        )
        val success = assertIs<TranspileOutcome.Success>(outcome)
        assertTrue(!success.pipesDesugared)
    }

    @Test
    fun `non-pipe statements report pipesDesugared false`() {
        val outcome = BrikkTranspiler.transpile(
            "SELECT 1",
            source = "mysql",
            target = "duckdb",
            pretty = false,
        )
        assertTrue(!assertIs<TranspileOutcome.Success>(outcome).pipesDesugared)
    }

    @Test
    fun `semantic hazards surface with provenance`() {
        // The headline certify example: duckdb lower() is simple case folding, trino's
        // is full Unicode folding — probe-verified divergence in the hazard registry.
        val outcome = BrikkTranspiler.transpile(
            "SELECT lower(x) FROM t",
            source = "duckdb",
            target = "trino",
            pretty = false,
        )
        val success = assertIs<TranspileOutcome.Success>(outcome)
        val hazard = success.findings.firstOrNull { it.kind == FindingKind.SEMANTIC_HAZARD }
        assertTrue(hazard != null, "expected a SEMANTIC_HAZARD for lower(), got: ${success.findings}")
        assertTrue(hazard.provenance != null, "hazard findings carry provenance")
    }

    @Test
    fun `clean transpile has no diagnostics`() {
        val outcome = BrikkTranspiler.transpile(
            "SELECT a, b FROM t WHERE a > 1",
            source = "mysql",
            target = "duckdb",
            pretty = false,
        )
        val success = assertIs<TranspileOutcome.Success>(outcome)
        assertTrue(success.isClean, "expected clean, got findings=${success.findings}")
    }
}

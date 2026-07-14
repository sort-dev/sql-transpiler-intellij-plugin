package dev.sort.sqltranspiler

import dev.brikk.house.sql.shape.Finding
import dev.brikk.house.sql.shape.FindingKind
import dev.brikk.house.sql.shape.Severity

/**
 * The plugin's reading of certification findings for the execute gate. brikk-sql's
 * strict semantics say "any REFUSAL must not run"; the plugin softens exactly two
 * kinds, both deliberately:
 *
 *  - [FindingKind.NO_TARGET_CATALOG]: only doris/trino/duckdb ship catalogs today —
 *    blocking would permanently disable Execute for every other engine. Rendered as
 *    an informational note instead ("capability checks unavailable").
 *  - [FindingKind.RAW_PASSTHROUGH_STATEMENT]: uncertifiable by brikk-sql, but the
 *    native verifier can still get an engine-exact verdict on the passthrough text —
 *    when the target's own parser accepts the statement, the refusal downgrades to a
 *    reviewable warning (a layering brikk-sql itself cannot do: it can't see the
 *    verifier module). Only an *authoritative* accept downgrades: the advisory
 *    ShardingSphere tier (postgres/mysql/hive/clickhouse) can false-accept invalid SQL,
 *    so an advisory pass is not enough to wave through a hard refusal.
 *
 * Everything else with [Severity.REFUSAL] blocks Execute. WARNINGs never block.
 */
object FindingPolicy {

    /** Findings that gate execution, given the native verifier's verdicts (if any). */
    fun blockingRefusals(
        success: BrikkTranspiler.TranspileOutcome.Success,
        verification: VerifierService.Report?,
    ): List<Finding> = success.statements.flatMap { statement ->
        statement.findings.filter { finding ->
            finding.severity == Severity.REFUSAL &&
                finding.kind != FindingKind.NO_TARGET_CATALOG &&
                !(finding.kind == FindingKind.RAW_PASSTHROUGH_STATEMENT &&
                    verification?.verdicts?.getOrNull(statement.index)
                        ?.let { it.accepted && it.verified && !it.advisory } == true)
        }
    }.distinct()

    /** Informational, never-blocking notes (rendered outside the diagnostics list). */
    fun informational(success: BrikkTranspiler.TranspileOutcome.Success): List<Finding> =
        success.findings.filter { it.kind == FindingKind.NO_TARGET_CATALOG }

    /** Findings shown in the diagnostics list (everything except the informational kinds). */
    fun reviewable(
        success: BrikkTranspiler.TranspileOutcome.Success,
        verification: VerifierService.Report?,
    ): List<ReviewEntry> {
        val blocking = blockingRefusals(success, verification).toSet()
        return success.findings
            .filter { it.kind != FindingKind.NO_TARGET_CATALOG }
            .map { finding ->
                ReviewEntry(
                    finding = finding,
                    blocksExecution = finding in blocking,
                    downgraded = finding.severity == Severity.REFUSAL && finding !in blocking,
                )
            }
    }

    data class ReviewEntry(
        val finding: Finding,
        /** REFUSAL that gates the Execute action. */
        val blocksExecution: Boolean,
        /** REFUSAL softened by policy (e.g. passthrough accepted by the native parser). */
        val downgraded: Boolean,
    )
}

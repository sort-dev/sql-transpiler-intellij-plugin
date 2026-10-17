package dev.sort.sqltranspiler

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import java.util.concurrent.CancellationException
import dev.brikk.house.sql.dialects.Dialects
import dev.brikk.house.sql.parser.ParseError
import dev.brikk.house.sql.parser.SqlScriptSnapshot
import dev.brikk.house.sql.shape.Finding
import dev.brikk.house.sql.shape.SqlFragment
import dev.brikk.house.sql.shape.TranspileResult
import dev.brikk.house.sql.shape.certify

/**
 * The plugin's transpilation entry point: multi-statement aware (SqlFragment is
 * single-statement by design; core script ranges are certified one by one).
 * Cancellation and fatal errors propagate. Expected input failures and certification
 * findings are returned as structured results:
 * every [Finding] (unmappable functions, unsupported translations, raw passthrough,
 * semantic hazards, capability gaps) plus an emit-span source map per statement for
 * walking verifier errors back to the original source.
 */
object BrikkTranspiler {

    /** One statement's certified transpilation. */
    data class StatementCertification(
        val index: Int,
        /** Rendered target SQL for this statement. */
        val sql: String,
        val findings: List<Finding>,
        /** Carries the emit-span source map ([TranspileResult.mapErrorToSource]). */
        val result: TranspileResult,
        /** True when this statement used pipe (`|>`) syntax (desugared for the target). */
        val pipesDesugared: Boolean,
        /** 0-based line of this statement's first char in the transpiled scope text. */
        val sourceLineOffset: Int,
        /** First-line UTF-16 column displacement of the untrimmed source slice. */
        val sourceColumnOffset: Int = 0,
    )

    sealed interface TranspileOutcome {
        data class Success(
            val sql: String,
            val source: String,
            val target: String,
            val statements: List<StatementCertification>,
        ) : TranspileOutcome {
            val statementCount: Int get() = statements.size
            val pipesDesugared: Boolean get() = statements.any { it.pipesDesugared }
            val renderedStatements: List<String> get() = statements.map { it.sql }

            /** Aggregated findings across statements, order-preserving, deduped. */
            val findings: List<Finding> get() = statements.flatMap { it.findings }.distinct()
            val isClean: Boolean get() = findings.isEmpty()
        }

        data class Failure(
            val message: String,
            /** 1-based, anchored at the END of the offending token (sqlglot semantics). */
            val line: Int? = null,
            val col: Int? = null,
            /** The offending token's text, when the parser captured it. */
            val highlight: String? = null,
        ) : TranspileOutcome
    }

    fun transpile(
        sql: String,
        source: String,
        target: String,
        pretty: Boolean = true,
    ): TranspileOutcome {
        ProgressManager.checkCanceled()
        if (Dialects.forNameOrNull(source) == null) return TranspileOutcome.Failure("Unknown source dialect: '$source'")
        Dialects.forNameOrNull(target)
            ?: return TranspileOutcome.Failure("Unknown target dialect: '$target'")

        val snapshot = try {
            SqlSourceSnapshot.parse(sql, source)
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ProgressManager.checkCanceled()
            return TranspileOutcome.Failure(e.message ?: "Tokenizer error (${e::class.simpleName})")
        }
        val slices = statementSlices(snapshot)
        if (slices.isEmpty()) {
            return TranspileOutcome.Failure("No SQL statements found in the ${BrikkDialects.displayName(source)} input.")
        }

        val certifications = ArrayList<StatementCertification>(slices.size)
        for ((index, slice) in slices.withIndex()) {
            ProgressManager.checkCanceled()
            val fragment = SqlFragment(slice.text, source)
            val (report, hasPipes) = try {
                val pipes = fragment.containsPipe
                if (slice.boundaryError != null) return TranspileOutcome.Failure(slice.boundaryError)
                fragment.certify(
                    target,
                    pretty = pretty,
                    trackSourceMap = true,
                    // Real engines don't speak |>: desugar pipe statements to standard SQL.
                    desugarPipes = pipes,
                ) to pipes
            } catch (e: ParseError) {
                ProgressManager.checkCanceled()
                val info = e.errors.firstOrNull()
                return TranspileOutcome.Failure(
                    message = info?.description ?: e.message ?: "Parse error",
                    line = info?.line?.plus(slice.lineOffset),
                    col = info?.col?.let { it + if (info.line == 1) slice.columnOffset else 0 },
                    highlight = info?.highlight,
                )
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProgressManager.checkCanceled()
                return TranspileOutcome.Failure(
                    "Cannot transpile statement ${index + 1} to ${BrikkDialects.displayName(target)}: ${e.message}"
                )
            }
            certifications.add(
                StatementCertification(
                    index = index,
                    sql = report.result.sql,
                    findings = report.findings,
                    result = report.result,
                    pipesDesugared = hasPipes,
                    sourceLineOffset = slice.lineOffset,
                    sourceColumnOffset = slice.columnOffset,
                )
            )
        }

        val trailingTrivia = snapshot.statements.lastOrNull()?.takeIf { !it.hasSql && it.boundaryError == null }
            ?.let { sql.substring(it.start, it.end) }.orEmpty()
        val outSql = certifications.joinToString(separator = ";\n\n", postfix = if (certifications.size > 1 || trailingTrivia.isNotEmpty()) ";" else "") { it.sql } + trailingTrivia
        ProgressManager.checkCanceled()
        return TranspileOutcome.Success(
            sql = outSql,
            source = source,
            target = target,
            statements = certifications,
        )
    }

    private data class Slice(val text: String, val lineOffset: Int, val columnOffset: Int, val boundaryError: String?)

    /** Retain authored whitespace; count prefixes once rather than rescanning for each statement. */
    private fun statementSlices(snapshot: SqlScriptSnapshot): List<Slice> {
        val slices = ArrayList<Slice>()
        var scanned = 0
        var line = 0
        var lineStart = 0
        for (range in snapshot.statements) {
            ProgressManager.checkCanceled()
            if (!range.hasSql && range.boundaryError == null) continue
            while (scanned < range.start) {
                if (snapshot.text[scanned] == '\n') { line++; lineStart = scanned + 1 }
                scanned++
            }
            slices.add(Slice(snapshot.text.substring(range.start, range.end), line, range.start - lineStart, range.boundaryError))
        }
        return slices
    }
}

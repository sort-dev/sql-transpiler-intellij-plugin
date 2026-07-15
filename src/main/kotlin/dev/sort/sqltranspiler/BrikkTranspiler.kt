package dev.sort.sqltranspiler

import dev.brikk.house.sql.ast.PipeQuery
import dev.brikk.house.sql.dialects.Dialects
import dev.brikk.house.sql.parser.ParseError
import dev.brikk.house.sql.parser.TokenType
import dev.brikk.house.sql.shape.Finding
import dev.brikk.house.sql.shape.SqlFragment
import dev.brikk.house.sql.shape.TranspileResult
import dev.brikk.house.sql.shape.certify

/**
 * The plugin's transpilation entry point: multi-statement aware (SqlFragment is
 * single-statement by design — statements are split via parser positions and certified
 * one by one), never throws, and surfaces brikk-sql's *certified* transpilation:
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
        val readDialect = Dialects.forNameOrNull(source)
            ?: return TranspileOutcome.Failure("Unknown source dialect: '$source'")
        Dialects.forNameOrNull(target)
            ?: return TranspileOutcome.Failure("Unknown target dialect: '$target'")

        // Statement source slices via semicolon tokens — the same boundaries the parser
        // splits on (the tokenizer already skips ';' inside strings and comments).
        val slices = try {
            statementSlices(sql, readDialect.tokenize(sql))
        } catch (e: Exception) {
            return TranspileOutcome.Failure(e.message ?: "Tokenizer error (${e::class.simpleName})")
        }
        if (slices.isEmpty()) {
            return TranspileOutcome.Failure("No SQL statements found in the ${BrikkDialects.displayName(source)} input.")
        }

        val certifications = ArrayList<StatementCertification>(slices.size)
        for ((index, slice) in slices.withIndex()) {
            val fragment = SqlFragment(slice.text, source)
            val (report, hasPipes) = try {
                val statement = fragment.ast
                val pipes = statement is PipeQuery || statement.findAll<PipeQuery>().any()
                fragment.certify(
                    target,
                    pretty = pretty,
                    trackSourceMap = true,
                    // Real engines don't speak |>: desugar pipe statements to standard SQL.
                    desugarPipes = pipes,
                ) to pipes
            } catch (e: ParseError) {
                val info = e.errors.firstOrNull()
                return TranspileOutcome.Failure(
                    message = info?.description ?: e.message ?: "Parse error",
                    line = info?.line?.plus(slice.lineOffset),
                    col = info?.col,
                    highlight = info?.highlight,
                )
            } catch (e: Exception) {
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
                )
            )
        }

        val outSql = certifications.joinToString(separator = ";\n\n", postfix = if (certifications.size > 1) ";" else "") { it.sql }
        return TranspileOutcome.Success(
            sql = outSql,
            source = source,
            target = target,
            statements = certifications,
        )
    }

    private data class Slice(val text: String, val lineOffset: Int)

    /** Splits [sql] into per-statement source slices at top-level semicolon tokens. */
    private fun statementSlices(
        sql: String,
        tokens: List<dev.brikk.house.sql.parser.Token>,
    ): List<Slice> {
        val slices = ArrayList<Slice>()
        var sliceStart = 0
        fun addSlice(endExclusive: Int) {
            val text = sql.substring(sliceStart, endExclusive)
            if (text.isNotBlank()) {
                val lineOffset = sql.take(sliceStart).count { it == '\n' } +
                    text.takeWhile { it == '\n' || it == '\r' || it == ' ' || it == '\t' }.count { it == '\n' }
                slices.add(Slice(text.trim(), lineOffset))
            }
        }
        for (token in tokens) {
            if (token.tokenType == TokenType.SEMICOLON) {
                addSlice(token.start)
                sliceStart = token.end + 1
            }
        }
        addSlice(sql.length)
        return slices
    }
}

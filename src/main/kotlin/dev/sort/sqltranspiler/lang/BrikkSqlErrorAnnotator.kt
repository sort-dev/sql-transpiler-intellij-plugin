package dev.sort.sqltranspiler.lang

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import dev.sort.sqltranspiler.BrikkDialects
import dev.sort.sqltranspiler.BrikkTranspiler
import dev.sort.sqltranspiler.DialectMarker
import dev.sort.sqltranspiler.SqlSourceSnapshot

/**
 * brikk-sql syntax errors for `.bsql` blocks that have no real IDE dialect: legacy
 * `-- dialect: xyz` blocks and `language=genericsql dialect=xyz` blocks are injected as
 * Generic SQL, so brikk-sql's parser for the declared dialect is the better authority.
 * Blocks in a real IDE language get that language's own errors instead.
 *
 * Runs as an [ExternalAnnotator] (off-EDT, after typing settles); parse failures are
 * painted at their reported line/col. Unknown-dialect and unmarked segments are left
 * alone — no authority, no opinion.
 */
class BrikkSqlErrorAnnotator : ExternalAnnotator<BrikkSqlErrorAnnotator.Info, BrikkSqlErrorAnnotator.Result>() {

    data class Info(val text: String)
    data class SyntaxError(val range: TextRange, val message: String)
    data class Result(val errors: List<SyntaxError>)

    override fun collectInformation(file: PsiFile): Info? =
        if (file.language === BrikkSqlDialect.INSTANCE) Info(file.text) else null

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): Info? =
        collectInformation(file)

    override fun doAnnotate(collectedInfo: Info?): Result? {
        val text = collectedInfo?.text ?: return null
        val errors = ArrayList<SyntaxError>()
        val markers = DialectMarker.findAll(text)
        for (marker in markers) {
            if (!marker.isSupported) continue
            // A block in a real IDE dialect gets that dialect's own syntax errors.
            if (BrikkSqlBlocks.languageFor(marker.languageId).id != BrikkSqlBlocks.GENERIC_LANGUAGE_ID) continue
            val (start, end) = DialectMarker.segmentAfter(text, marker, markers)
            val segment = text.substring(start, end)
            if (segment.isBlank()) continue
            if (SqlSourceSnapshot.parse(segment, marker.dialect).statements.none { it.hasSql || it.boundaryError != null }) continue
            val outcome = BrikkTranspiler.transpile(segment, marker.dialect, marker.dialect, pretty = false)
            if (outcome is BrikkTranspiler.TranspileOutcome.Failure) {
                errors.add(
                    SyntaxError(
                        range = errorRange(text, start, segment, outcome.line, outcome.col, outcome.highlight),
                        message = "${BrikkDialects.displayName(marker.dialect)}: ${outcome.message}",
                    )
                )
            }
        }
        return Result(errors)
    }

    override fun apply(file: PsiFile, annotationResult: Result?, holder: AnnotationHolder) {
        for (error in annotationResult?.errors.orEmpty()) {
            holder.newAnnotation(HighlightSeverity.ERROR, error.message)
                .range(error.range)
                .create()
        }
    }

    /**
     * Maps a brikk parse failure ([line]/[col] 1-based within [segment], both anchored at
     * the END of the offending token — sqlglot semantics) to a range in the full text.
     * With [highlight] (the token's text) the range covers exactly that token; without it,
     * from the error column to the line's end. Falls back to the segment's first non-blank
     * line when the parser gave no position.
     */
    private fun errorRange(
        text: String,
        segmentStart: Int,
        segment: String,
        line: Int?,
        col: Int?,
        highlight: String? = null,
    ): TextRange {
        val lines = segment.split('\n')
        val lineIndex = when {
            line != null -> (line - 1).coerceIn(0, lines.lastIndex)
            else -> lines.indexOfFirst { it.isNotBlank() }.coerceAtLeast(0)
        }
        val lineStartInSegment = lines.take(lineIndex).sumOf { it.length + 1 }
        val lineText = lines[lineIndex]
        // col points at the token's LAST char (1-based); its 0-based end-exclusive offset
        // is simply col. The token starts highlight.length chars earlier when known.
        val endOffset = (col ?: lineText.length).coerceIn(1, lineText.length.coerceAtLeast(1))
        val startOffset = when {
            highlight != null -> (endOffset - highlight.length).coerceAtLeast(0)
            else -> (endOffset - 1).coerceAtLeast(0)
        }
        val from = segmentStart + lineStartInSegment + startOffset
        val to = segmentStart + lineStartInSegment + (if (highlight != null) endOffset else lineText.length)
        return TextRange(from.coerceAtMost(text.length), to.coerceAtMost(text.length).coerceAtLeast(from))
    }
}

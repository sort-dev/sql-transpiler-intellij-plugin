package dev.sort.sqltranspiler.lang

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import dev.sort.sqltranspiler.BrikkDialects
import dev.sort.sqltranspiler.BrikkTranspiler
import dev.sort.sqltranspiler.DialectMarker

/**
 * The authoritative syntax errors for `.bsql` files, from brikk-sql's own dialect
 * parsers. [BrikkSqlHighlightErrorFilter] blanket-suppresses the Generic substrate's
 * PsiErrorElements (its opinion of foreign dialects is worthless), so this annotator is
 * the *only* source of red syntax squiggles — the doris-plugin recipe: never ship the
 * blanket filter without the real parser layered back on, or broken SQL looks fine.
 *
 * Runs as an [ExternalAnnotator] (off-EDT, after typing settles): each supported
 * `-- dialect: xyz` segment is parsed by its own brikk-sql dialect; parse failures are
 * painted at their reported line/col. Unknown-dialect and unmarked segments are left
 * alone — no authority, no opinion.
 */
class BrikkSqlErrorAnnotator : ExternalAnnotator<BrikkSqlErrorAnnotator.Info, BrikkSqlErrorAnnotator.Result>() {

    data class Info(val text: String)
    data class SyntaxError(val range: TextRange, val message: String)
    data class Result(val errors: List<SyntaxError>)

    override fun collectInformation(file: PsiFile): Info? =
        if (file.language === BrikkSqlDialect.INSTANCE) Info(file.text) else null

    override fun doAnnotate(collectedInfo: Info?): Result? {
        val text = collectedInfo?.text ?: return null
        val errors = ArrayList<SyntaxError>()
        for (marker in DialectMarker.findAll(text)) {
            if (!marker.isSupported) continue
            val (start, end) = DialectMarker.segmentAfter(text, marker)
            val segment = text.substring(start, end)
            if (segment.isBlank()) continue
            val outcome = BrikkTranspiler.transpile(segment, marker.dialect, marker.dialect, pretty = false)
            if (outcome is BrikkTranspiler.TranspileOutcome.Failure) {
                errors.add(
                    SyntaxError(
                        range = errorRange(text, start, segment, outcome.line, outcome.col),
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
     * Maps a brikk parse failure ([line] 1-based within [segment], [col] 1-based) to a
     * range in the full text: from the error column to that line's end. Falls back to
     * the segment's first non-blank line when the parser gave no position.
     */
    private fun errorRange(text: String, segmentStart: Int, segment: String, line: Int?, col: Int?): TextRange {
        val lines = segment.split('\n')
        val lineIndex = when {
            line != null -> (line - 1).coerceIn(0, lines.lastIndex)
            else -> lines.indexOfFirst { it.isNotBlank() }.coerceAtLeast(0)
        }
        val lineStartInSegment = lines.take(lineIndex).sumOf { it.length + 1 }
        val lineText = lines[lineIndex]
        val colOffset = ((col ?: 1) - 1).coerceIn(0, (lineText.length - 1).coerceAtLeast(0))
        val from = segmentStart + lineStartInSegment + colOffset
        val to = segmentStart + lineStartInSegment + lineText.length
        return TextRange(from.coerceAtMost(text.length), to.coerceAtMost(text.length).coerceAtLeast(from))
    }
}

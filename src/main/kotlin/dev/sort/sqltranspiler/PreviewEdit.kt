package dev.sort.sqltranspiler

import com.intellij.openapi.util.TextRange
import dev.brikk.house.sql.dialects.Dialects
import dev.brikk.house.sql.parser.TokenType

/** Product-specific edits for the dialect-marker workbench, computed before the write command. */
internal data class PreviewEdit(val range: TextRange, val text: String) {
    companion object {
        @JvmOverloads
        fun plan(document: String, scope: TranspileFlow.Scope,
                 outcome: BrikkTranspiler.TranspileOutcome.Success, replace: Boolean, initialDialect: String? = null): PreviewEdit {
            scope.snapshot?.requireCurrent()
            require(scope.documentText == null || scope.documentText == document) { "The document changed. Transpile it again before editing." }
            val start = scope.range.startOffset
            val end = scope.range.endOffset
            require(start >= 0 && end <= document.length && document.substring(start, end) == scope.text) {
                "The source changed. Transpile it again before editing."
            }
            val markers = DialectMarker.findAll(document, initialDialect)
            val governing = markers.lastOrNull { it.lineEndOffset < start }
            val blockStart = governing?.let { it.lineEndOffset + 1 } ?: 0
            val blockEnd = markers.firstOrNull { it.lineStartOffset >= start }?.lineStartOffset ?: document.length
            require(end <= blockEnd) { "Select SQL within one dialect block before applying an edit." }
            val source = scope.markerDialect ?: outcome.source
            val block = document.substring(blockStart, blockEnd)
            val tokens = Dialects.forName(source).tokenize(block)
            val from = start - blockStart
            val to = end - blockStart
            val script = SqlSourceSnapshot.parse(block, source)
            require(script.statements.none { it.boundaryError != null }) { "The block has incomplete or unsupported statement boundaries." }
            // A semicolon inside a routine is not a statement boundary. Require complete
            // core-owned statements, while allowing surrounding trivia/terminators to remain.
            var tokenIndex = 0
            for (statement in script.statements) {
                val firstToken = tokenIndex
                while (tokenIndex < tokens.size && tokens[tokenIndex].end < statement.end) tokenIndex++
                val content = tokens.subList(firstToken, tokenIndex).filter { it.tokenType != TokenType.SEMICOLON }
                if (content.any { it.start < to && it.end >= from }) {
                    require(content.first().start >= from && content.last().end < to) {
                        "Select complete SQL statements before applying an edit."
                    }
                }
            }
            require(tokens.none { it.start < from && it.end >= from || it.start < to && it.end >= to }) {
                "Select complete SQL statements before applying an edit."
            }
            val before = tokens.lastOrNull { it.end < from }
            val inside = tokens.filter { it.start >= from && it.end < to }
            val after = tokens.firstOrNull { it.start >= to }
            require((before == null || before.tokenType == TokenType.SEMICOLON) &&
                (after == null || after.tokenType == TokenType.SEMICOLON || inside.lastOrNull()?.tokenType == TokenType.SEMICOLON)) {
                "Select complete SQL statements before applying an edit."
            }
            val generated = outcome.sql.trimEnd()
            val last = Dialects.forName(outcome.target).tokenize(generated).lastOrNull()
            val terminated = when {
                last?.tokenType == TokenType.SEMICOLON -> "$generated\n"
                last?.end == generated.lastIndex -> "$generated;\n"
                else -> "$generated\n;\n"
            }
            val targetBlock = "-- dialect: ${outcome.target}\n$terminated"
            val fullBlock = document.substring(blockStart, start).isBlank() && document.substring(end, blockEnd).isBlank()
            if (replace && fullBlock) {
                return PreviewEdit(TextRange(governing?.lineStartOffset ?: blockStart, blockEnd), targetBlock)
            }
            val remainingSql = tokens.any { it.start >= to && it.tokenType != TokenType.SEMICOLON }
            val restore = if (remainingSql) "-- dialect: $source\n" else ""
            if (replace) {
                val newline = if (start > 0 && document[start - 1] != '\n') "\n" else ""
                return PreviewEdit(scope.range, newline + targetBlock + restore)
            }
            // The original selection may omit its semicolon. Put the terminator on its own
            // line so an authored trailing line comment cannot swallow it or the next marker.
            val separator = if (inside.lastOrNull()?.tokenType == TokenType.SEMICOLON) "\n\n" else "\n;\n\n"
            return PreviewEdit(TextRange(end, end), separator + targetBlock + restore)
        }
    }
}

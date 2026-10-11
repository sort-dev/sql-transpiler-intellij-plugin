package dev.sort.sqltranspiler

import com.intellij.openapi.progress.ProgressManager
import dev.brikk.house.sql.parser.SqlScriptSnapshot
import dev.brikk.house.sql.parser.SqlStatementRange
import dev.brikk.house.sql.parser.Tokenizer
import dev.brikk.house.sql.parser.TokenizerConfigs

/** Core owns ranges. This adapter contains the 0.19.0 unterminated-comment recovery gap. */
internal object SqlSourceSnapshot {
    fun parse(text: String, dialect: String): SqlScriptSnapshot {
        ProgressManager.checkCanceled()
        val snapshot = SqlScriptSnapshot.parse(text, dialect)
        if (snapshot.statements.any { it.boundaryError != null } || isLexicallyComplete(text, dialect)) return snapshot
        val message = "Unterminated SQL lexical construct; the complete statement is unknown."
        val ranges = snapshot.statements.toMutableList()
        if (ranges.isEmpty()) ranges.add(SqlStatementRange(0, text.length, true, false, message))
        else ranges[ranges.lastIndex] = ranges.last().copy(hasSql = true, boundaryError = message)
        return snapshot.copy(statements = ranges)
    }

    fun isLexicallyComplete(text: String, dialect: String): Boolean {
        ProgressManager.checkCanceled()
        // A newline ends a legitimate line comment. An open block comment/quote must
        // not swallow this token. No parser, generated SQL or user document is modified.
        val probe = "__brikk_lexical_end__"
        val tokens = Tokenizer(TokenizerConfigs.forName(dialect)).tokenizeForEditor("$text\n$probe")
        ProgressManager.checkCanceled()
        return tokens.error == null && tokens.tokens.lastOrNull()?.let {
            it.start == text.length + 1 && it.text == probe
        } == true
    }
}

package dev.sort.sqltranspiler

import com.intellij.openapi.progress.ProgressManager
import dev.brikk.house.sql.parser.SqlScriptSnapshot
import dev.brikk.house.sql.parser.Tokenizer
import dev.brikk.house.sql.parser.TokenizerConfigs

/** Core owns lexical semantics and ranges; the IDE adapter only checks cancellation. */
internal object SqlSourceSnapshot {
    fun parse(text: String, dialect: String): SqlScriptSnapshot {
        ProgressManager.checkCanceled()
        val snapshot = SqlScriptSnapshot.parse(text, dialect)
        ProgressManager.checkCanceled()
        return snapshot
    }

    fun isLexicallyComplete(text: String, dialect: String): Boolean {
        ProgressManager.checkCanceled()
        val tokens = Tokenizer(TokenizerConfigs.forName(dialect)).tokenizeForEditor(text)
        ProgressManager.checkCanceled()
        return tokens.error == null
    }
}

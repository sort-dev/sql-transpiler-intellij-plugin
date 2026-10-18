package dev.sort.sqltranspiler.lang

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.lexer.LexerBase
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.TokenType
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.impl.source.tree.injected.InjectionBackgroundSuppressor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.ILeafElementType
import com.intellij.sql.psi.SqlTokens
import dev.sort.sqltranspiler.DialectMarker

/**
 * The `.bsql` host grammar: marker lines are SQL line comments, and the text of each
 * block between them is one [BrikkSqlBlock] leaf that its declared language is injected
 * into. Boundaries come from [DialectMarker.findAll], so marker-shaped lines inside a
 * block's strings or comments stay part of the block.
 */
object BrikkSqlBlockType : IElementType("BRIKK_SQL_BLOCK", BrikkSqlDialect.INSTANCE), ILeafElementType {
    override fun createLeafNode(leafText: CharSequence): ASTNode = BrikkSqlBlock(this, leafText)
}

/** A block's SQL is the file's own SQL, not an embedded fragment: no injected-fragment background. */
class BrikkSqlBlock(type: IElementType, text: CharSequence) : LeafPsiElement(type, text), PsiLanguageInjectionHost,
    InjectionBackgroundSuppressor {
    override fun isValidHost(): Boolean = true
    override fun updateText(text: String): PsiLanguageInjectionHost = replaceWithText(text) as BrikkSqlBlock
    override fun createLiteralTextEscaper(): LiteralTextEscaper<out PsiLanguageInjectionHost> = LiteralTextEscaper.createSimple(this)
    override fun toString(): String = "BrikkSqlBlock"
}

class BrikkSqlBlockManipulator : AbstractElementManipulator<BrikkSqlBlock>() {
    override fun handleContentChange(element: BrikkSqlBlock, range: TextRange, newContent: String): BrikkSqlBlock {
        val text = range.replace(element.text, newContent)
        return element.updateText(text) as BrikkSqlBlock
    }
}

/**
 * Lexes `.bsql` into marker comments, blocks and the whitespace around them. Only the
 * file start and marker lines are restart points (state 0): a marker's meaning depends
 * on the text above it, and a marker line resets that context.
 */
class BrikkSqlHostLexer : LexerBase() {
    private data class Token(val start: Int, val end: Int, val type: IElementType, val state: Int)

    private var buffer: CharSequence = ""
    private var bufferEnd = 0
    private var tokens: List<Token> = emptyList()
    private var index = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        bufferEnd = endOffset
        tokens = tokenize(buffer.subSequence(startOffset, endOffset).toString(), startOffset)
        index = 0
    }

    private fun tokenize(text: String, base: Int): List<Token> {
        val out = ArrayList<Token>()
        // A block owns everything from the line after its marker up to the next marker line,
        // blank lines and trailing whitespace included: wherever the caret is under a marker,
        // it is inside the block's language (typing "FROM " or on a fresh empty line).
        fun segment(from: Int, to: Int) {
            if (from >= to) return
            var start = from
            if (out.isNotEmpty()) {
                // The rest of the marker line, through its line break, stays host whitespace.
                while (start < to && text[start] != '\n' && text[start].isWhitespace()) start++
                if (start < to && text[start] == '\n') start++
                if (start > from) out += Token(base + from, base + start, TokenType.WHITE_SPACE, 1)
            }
            if (to > start) out += Token(base + start, base + to, BrikkSqlBlockType, 1)
        }
        var position = 0
        for (marker in DialectMarker.findAll(text)) {
            segment(position, marker.lineStartOffset)
            var commentStart = marker.lineStartOffset
            while (commentStart < marker.lineEndOffset && text[commentStart].isWhitespace()) commentStart++
            var commentEnd = marker.lineEndOffset
            while (commentEnd > commentStart && text[commentEnd - 1].isWhitespace()) commentEnd--
            if (commentStart > marker.lineStartOffset) out += Token(base + marker.lineStartOffset, base + commentStart, TokenType.WHITE_SPACE, 1)
            out += Token(base + commentStart, base + commentEnd, SqlTokens.SQL_LINE_COMMENT, 0)
            position = commentEnd
        }
        segment(position, text.length)
        if (out.isNotEmpty() && out[0].state != 0) out[0] = out[0].copy(state = 0)
        return out
    }

    override fun getState(): Int = tokens.getOrNull(index)?.state ?: 0
    override fun getTokenType(): IElementType? = tokens.getOrNull(index)?.type
    override fun getTokenStart(): Int = tokens.getOrNull(index)?.start ?: bufferEnd
    override fun getTokenEnd(): Int = tokens.getOrNull(index)?.end ?: bufferEnd
    override fun advance() { index++ }
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = bufferEnd
}

/** A flat file: the injected languages own everything inside the blocks. */
class BrikkSqlHostParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val file = builder.mark()
        while (!builder.eof()) builder.advanceLexer()
        file.done(root)
        return builder.treeBuilt
    }
}

class BrikkSqlHostSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = BrikkSqlHostLexer()
    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        if (tokenType == SqlTokens.SQL_LINE_COMMENT) COMMENT else TextAttributesKey.EMPTY_ARRAY

    private companion object {
        val COMMENT = arrayOf(DefaultLanguageHighlighterColors.LINE_COMMENT)
    }
}

class BrikkSqlSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter = BrikkSqlHostSyntaxHighlighter()
}

package dev.sort.sqltranspiler.lang

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionConfidence
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.database.psi.DbPsiFacade
import com.intellij.icons.AllIcons
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import dev.sort.sqltranspiler.BrikkDialects

/**
 * Where the caret is in a `-- language=... dialect=... using=...` marker line, from the
 * line's text before the caret. Pure text, so it reads the same whether the line is
 * already a marker (a host comment) or still being typed inside a block's SQL.
 */
object BrikkSqlMarkerContext {
    sealed interface Context {
        /** Typing an attribute name: [prefix] so far; [present] names already on the line. */
        data class Key(val prefix: String, val present: Set<String>, val first: Boolean) : Context

        /** Typing the value of [key]: [prefix] so far, including an opening quote. */
        data class Value(val key: String, val prefix: String) : Context
    }

    private val LINE = Regex("""^\s*--\s?(.*)$""")
    const val LANGUAGE = "language"
    val OPTIONAL_KEYS = listOf("dialect", "using")

    fun at(linePrefix: String): Context? {
        val body = LINE.matchEntire(linePrefix)?.groupValues?.get(1) ?: return null
        if (!body.startsWith("$LANGUAGE=")) {
            val word = body.trimStart()
            return if (LANGUAGE.startsWith(word)) Context.Key(word, emptySet(), first = true) else null
        }
        val tokens = tokens(body)
        val current = if (body.isNotEmpty() && body.last().isWhitespace() && !inQuotes(body)) "" else tokens.lastOrNull().orEmpty()
        val previous = if (current.isEmpty()) tokens else tokens.dropLast(1)
        val present = previous.mapNotNull { it.substringBefore('=', "").takeIf(String::isNotEmpty) }.toSet()
        val equals = current.indexOf('=')
        return if (equals > 0) Context.Value(current.substring(0, equals), current.substring(equals + 1))
        else Context.Key(current, present, first = false)
    }

    /** Whitespace-separated tokens, keeping quoted values (with `\"`) whole. */
    private fun tokens(body: String): List<String> {
        val out = ArrayList<String>()
        val token = StringBuilder()
        var quoted = false
        var index = 0
        while (index < body.length) {
            val c = body[index]
            when {
                quoted && c == '\\' && index + 1 < body.length -> { token.append(c).append(body[index + 1]); index++ }
                c == '"' -> { quoted = !quoted; token.append(c) }
                c.isWhitespace() && !quoted -> if (token.isNotEmpty()) { out += token.toString(); token.clear() }
                else -> token.append(c)
            }
            index++
        }
        if (token.isNotEmpty()) out += token.toString()
        return out
    }

    private fun inQuotes(body: String): Boolean {
        var quoted = false
        var index = 0
        while (index < body.length) {
            if (quoted && body[index] == '\\') index++ else if (body[index] == '"') quoted = !quoted
            index++
        }
        return quoted
    }

    /** A value as written in a marker: quoted when it has whitespace or quotes. */
    fun quote(value: String): String =
        if (value.isEmpty() || value.any { it.isWhitespace() || it == '"' }) "\"" + value.replace("\"", "\\\"") + "\"" else value

    /** The text before [offset] on its line in the top-level `.bsql` document, or null outside `.bsql` comments. */
    fun linePrefix(file: PsiFile, offset: Int): String? {
        val manager = InjectedLanguageManager.getInstance(file.project)
        val host = manager.getTopLevelFile(file)
        if (host.language !== BrikkSqlDialect.INSTANCE) return null
        val document = PsiDocumentManager.getInstance(file.project).getDocument(host) ?: return null
        val hostOffset = manager.injectedToHost(file, offset).coerceIn(0, document.textLength)
        val lineStart = document.getLineStartOffset(document.getLineNumber(hostOffset))
        return document.charsSequence.subSequence(lineStart, hostOffset).toString()
    }

    fun inComment(element: PsiElement?): Boolean =
        element is PsiComment || PsiTreeUtil.getParentOfType(element, PsiComment::class.java, false) != null
}

/**
 * Completes marker attributes in `.bsql`: `language=`, `dialect=` and `using=`, then the
 * brikk-sql dialects after `dialect=` and the data source names (quoted when needed)
 * after `using=`. Language ids after `language=` come from the IDE's own comment
 * completion.
 */
class BrikkSqlMarkerCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return
        if (!BrikkSqlMarkerContext.inComment(parameters.position)) return
        val line = BrikkSqlMarkerContext.linePrefix(parameters.originalFile, parameters.offset) ?: return
        when (val context = BrikkSqlMarkerContext.at(line)) {
            is BrikkSqlMarkerContext.Context.Key -> {
                val keys = if (context.first) listOf(BrikkSqlMarkerContext.LANGUAGE)
                else BrikkSqlMarkerContext.OPTIONAL_KEYS - context.present
                val set = result.withPrefixMatcher(context.prefix)
                keys.forEach { set.addElement(key(it)) }
                // Other contributors would offer words from the comment around the names.
                result.stopHere()
            }
            is BrikkSqlMarkerContext.Context.Value -> {
                val set = result.withPrefixMatcher(context.prefix)
                when (context.key) {
                    "dialect" -> BrikkDialects.names.forEach { name ->
                        set.addElement(LookupElementBuilder.create(name).withTypeText(BrikkDialects.displayName(name)).withIcon(AllIcons.Nodes.DataTables))
                    }
                    "using" -> dataSources(parameters.originalFile.project).forEach(set::addElement)
                }
                if (context.key != BrikkSqlMarkerContext.LANGUAGE) result.stopHere()
            }
            null -> Unit
        }
    }

    private fun key(name: String): LookupElement = LookupElementBuilder.create("$name=")
        .withPresentableText(name)
        .withTypeText(
            when (name) {
                "language" -> "IDE language of the block"
                "dialect" -> "brikk-sql dialect for transpiling"
                else -> "connection to resolve and run against"
            },
        )
        .withInsertHandler { context, _ -> AutoPopupController.getInstance(context.project).scheduleAutoPopup(context.editor) }

    private fun dataSources(project: Project): List<LookupElement> =
        DbPsiFacade.getInstance(project).dataSources.map { source ->
            val quoted = BrikkSqlMarkerContext.quote(source.name)
            // The bare name too, so typing it unquoted still matches; the quoted form is inserted.
            LookupElementBuilder.create(source, quoted)
                .withLookupStrings(setOf(quoted, source.name))
                .withPresentableText(source.name)
                .withTypeText(source.dbms.displayName)
                .withIcon(source.getIcon(0))
        }
}

/** Lets completion pop up automatically inside `.bsql` marker comments. */
class BrikkSqlMarkerCompletionConfidence : CompletionConfidence() {
    override fun shouldSkipAutopopup(editor: Editor, contextElement: PsiElement, psiFile: PsiFile, offset: Int): ThreeState {
        val line = BrikkSqlMarkerContext.linePrefix(psiFile, offset) ?: return ThreeState.UNSURE
        return if (BrikkSqlMarkerContext.at(line) != null && BrikkSqlMarkerContext.inComment(contextElement)) ThreeState.NO else ThreeState.UNSURE
    }
}

/** Opens completion while typing a marker: after `=`, after a finished value, and on attribute names. */
class BrikkSqlMarkerTypedHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
        val hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
        val documents = PsiDocumentManager.getInstance(project)
        val host = documents.getPsiFile(hostEditor.document) ?: return Result.CONTINUE
        if (host.language !== BrikkSqlDialect.INSTANCE) return Result.CONTINUE
        val document = hostEditor.document
        val caret = hostEditor.caretModel.offset
        val lineStart = document.getLineStartOffset(document.getLineNumber(caret))
        val line = document.charsSequence.subSequence(lineStart, caret).toString() + charTyped
        val context = BrikkSqlMarkerContext.at(line) ?: return Result.CONTINUE
        // A plain "-- x" comment is not worth a popup; "-- la" on the way to language= is.
        if (context is BrikkSqlMarkerContext.Context.Key && context.first && context.prefix.length < 2) return Result.CONTINUE
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return Result.STOP
    }
}

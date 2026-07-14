package dev.sort.sqltranspiler.lang

import com.intellij.codeInsight.highlighting.HighlightErrorFilter
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.database.Dbms
import com.intellij.lang.Commenter
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.tree.IFileElementType
import com.intellij.sql.dialects.base.SqlElementFactoryBase
import com.intellij.sql.dialects.base.SqlLanguageDialectBase
import com.intellij.sql.dialects.base.SqlParserDefinitionBase
import com.intellij.sql.dialects.base.SqlSyntaxHighlighterFactory
import com.intellij.sql.dialects.base.TokensHelper
import com.intellij.sql.dialects.generic.GenericLexer
import com.intellij.sql.dialects.generic.GenericParser
import com.intellij.sql.dialects.sql92.Sql92Dialect
import com.intellij.sql.dialects.sql92.Sql92ParserDefinition
import com.intellij.sql.psi.stubs.elementTypes.SqlFileElementType
import javax.swing.Icon

/**
 * BrikkSQL: the plugin-owned SQL dialect for polyglot files (`.bsql`) — the first-class
 * home of the marker workflow. Blocks declare their dialect with `-- dialect: xyz`,
 * brikk-sql does the real parsing/transpilation, and the host grammar just needs to be
 * lenient with correct `;` statement boundaries.
 *
 * v1 is Generic SQL under our own language id (same recipe: SQL92 tokens helper +
 * Generic lexer/parser — see GenericDialect/GenericParserDefinition), which means no
 * vendor grumbling, our gutter/actions always active ([SqlHosts] treats it lenient),
 * and full control of the id so we can layer per-segment behavior over time (segment
 * re-lexing, dialect-aware completion, pipe syntax support) without churning users.
 */
class BrikkSqlDialect private constructor() : SqlLanguageDialectBase("BrikkSQL") {

    override fun getDbms(): Dbms = Dbms.UNKNOWN

    // Friendly label in language pickers (New -> Scratch File, language popups).
    override fun getDisplayName(): String = "Brikk SQL"

    // Same tokens as Generic SQL: reuse SQL92's helper (GenericDialect does exactly this).
    override fun createTokensHelper(): TokensHelper = Sql92Dialect.INSTANCE.tokensHelper

    // Mirror GenericDialect's implementations (delegate to SQL92).
    override fun isOperatorSupported(token: com.intellij.psi.tree.IElementType): Boolean =
        Sql92Dialect.INSTANCE.isOperatorSupported(token)

    override fun getSystemVariables(): Set<String> = Sql92Dialect.INSTANCE.keywords

    companion object {
        @JvmField
        val INSTANCE = BrikkSqlDialect()
    }
}

/** Generic SQL parsing under the BrikkSQL language (Generic's own recipe, our file type). */
class BrikkSqlParserDefinition : SqlParserDefinitionBase() {
    override fun createElementFactory(): SqlElementFactoryBase = Sql92ParserDefinition().elementFactory
    override fun createLexer(project: Project?): Lexer = GenericLexer()
    override fun createParser(project: Project?): PsiParser = GenericParser()
    override fun getFileNodeType(): IFileElementType = FILE

    private companion object {
        private val FILE = SqlFileElementType("BRIKK_SQL_FILE", BrikkSqlDialect.INSTANCE)
    }
}

/** Platform SQL highlighter resolved for the BrikkSQL dialect. */
class BrikkSqlSyntaxHighlighterFactory : SqlSyntaxHighlighterFactory.Base(BrikkSqlDialect.INSTANCE)

/**
 * Silences the schema-bound SQL inspections inside `.bsql` files. A Brikk SQL scratch is
 * a polyglot playground: its blocks target other engines' schemas, so the IDE can never
 * resolve their tables/columns/functions — every reference would wave a false "unable to
 * resolve" / "no data source" squiggle. brikk-sql's own certification (findings, function
 * catalogs, native verifiers) does the real checking at transpile time instead. Syntax
 * errors and the non-schema inspections stay active.
 */
class BrikkSqlInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean =
        toolId in SUPPRESSED_TOOLS

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> =
        SuppressQuickFix.EMPTY_ARRAY

    private companion object {
        /**
         * Schema-dependent inspections (fields/functions/data sources) — meaningless in
         * .bsql. Keyed by the *suppress id* each tool declares (`suppressId=` in DataGrip's
         * registrations — SqlResolveInspection→SqlResolve etc.), which is what
         * [InspectionSuppressor.isSuppressedFor] receives; shortNames included for safety.
         */
        private val SUPPRESSED_TOOLS = setOf(
            "SqlResolve", "SqlResolveInspection",         // unresolved tables/columns/routines
            "SqlType", "SqlTypeInspection",               // argument/return types need resolved schema
            "SqlSignature", "SqlSignatureInspection",     // function arity/signature needs resolved routines
            "SqlNoDataSourceInspection",                  // "no data sources configured" nag (no suppressId)
        )
    }
}

/**
 * Swallows the substrate parser's syntax errors in `.bsql` files (the doris-intellij-plugin
 * recipe). BrikkSQL parses with the lenient Generic grammar, but foreign-dialect blocks
 * (ClickHouse `toStartOfMonth`, pipe syntax, ...) still leave PsiErrorElements the substrate
 * has no authority over — brikk-sql is the real parser, at transpile/verify time. Total,
 * not selective: the base grammar's opinion of another engine's syntax is worthless.
 */
class BrikkSqlHighlightErrorFilter : HighlightErrorFilter() {
    override fun shouldHighlightErrorElement(element: PsiErrorElement): Boolean =
        element.containingFile?.language !== BrikkSqlDialect.INSTANCE
}

class BrikkSqlCommenter : Commenter {
    override fun getLineCommentPrefix(): String = "--"
    override fun getBlockCommentPrefix(): String = "/*"
    override fun getBlockCommentSuffix(): String = "*/"
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}

object BrikkSqlFileType : LanguageFileType(BrikkSqlDialect.INSTANCE) {
    override fun getName(): String = "Brikk SQL"
    override fun getDescription(): String =
        "Brikk SQL polyglot file: dialect-marker blocks, cross-dialect transpilation, pipe syntax"
    override fun getDefaultExtension(): String = "bsql"
    override fun getIcon(): Icon = com.intellij.icons.AllIcons.Nodes.DataTables
}

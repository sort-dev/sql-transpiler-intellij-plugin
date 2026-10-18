package dev.sort.sqltranspiler.lang

import com.intellij.database.Dbms
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IFileElementType
import com.intellij.sql.dialects.base.SqlElementFactoryBase
import com.intellij.sql.dialects.base.SqlLanguageDialectBase
import com.intellij.sql.dialects.base.SqlParserDefinitionBase
import com.intellij.sql.dialects.base.TokensHelper
import com.intellij.sql.dialects.sql92.Sql92Dialect
import com.intellij.sql.dialects.sql92.Sql92ParserDefinition
import com.intellij.sql.psi.stubs.elementTypes.SqlFileElementType
import javax.swing.Icon

/**
 * BrikkSQL: the plugin-owned SQL dialect for polyglot files (`.bsql`) — the first-class
 * home of the marker workflow. Blocks declare their language with `-- language=xyz`
 * (optionally `dialect=` and `using=`); each block is injected with that language and
 * resolves against its own data source. It stays a SQL dialect so console, gutter and
 * scratch plumbing keep treating `.bsql` as SQL ([SqlHosts] treats it lenient).
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

/**
 * The `.bsql` host: marker comments and one injection host per block (see
 * [BrikkSqlHostLexer]). Each block's own language parses its SQL.
 */
class BrikkSqlParserDefinition : SqlParserDefinitionBase() {
    override fun createElementFactory(): SqlElementFactoryBase = Sql92ParserDefinition().elementFactory
    override fun createLexer(project: Project?): Lexer = BrikkSqlHostLexer()
    override fun createParser(project: Project?): PsiParser = BrikkSqlHostParser()
    override fun getFileNodeType(): IFileElementType = FILE

    private companion object {
        private val FILE = SqlFileElementType("BRIKK_SQL_HOST_FILE", BrikkSqlDialect.INSTANCE)
    }
}

object BrikkSqlFileType : LanguageFileType(BrikkSqlDialect.INSTANCE) {
    override fun getName(): String = "Brikk SQL"
    override fun getDescription(): String =
        "Brikk SQL polyglot file: dialect-marker blocks, cross-dialect transpilation, pipe syntax"
    override fun getDefaultExtension(): String = "bsql"
    override fun getIcon(): Icon = com.intellij.icons.AllIcons.Nodes.DataTables
}

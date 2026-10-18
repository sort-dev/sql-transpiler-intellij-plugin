package dev.sort.sqltranspiler.lang

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.sql.dialects.SqlLanguageDialect
import com.intellij.sql.psi.impl.DataSourceProvider
import dev.sort.sqltranspiler.BrikkDialects
import dev.sort.sqltranspiler.DialectMarker

/** The blocks of a `.bsql` file and what their marker comments declare. */
object BrikkSqlBlocks {
    const val GENERIC_LANGUAGE_ID = "GenericSQL"

    /** The directive of the marker right above [block], or null for an unmarked block. */
    fun directiveOf(block: BrikkSqlBlock): DialectMarker.Directive? {
        var previous = block.prevSibling
        while (previous is PsiWhiteSpace) previous = previous.prevSibling
        return (previous as? PsiComment)?.let { DialectMarker.parseDirective(it.text) }
    }

    /** The language named by `language=`, matched case-insensitively; Generic SQL otherwise. */
    fun languageOf(block: BrikkSqlBlock): Language = languageFor(directiveOf(block)?.languageId)

    fun languageFor(languageId: String?): Language {
        val named = languageId?.let { id -> Language.getRegisteredLanguages().firstOrNull { it.id.equals(id, ignoreCase = true) } }
        return named ?: requireNotNull(Language.findLanguageByID(GENERIC_LANGUAGE_ID)) { "Generic SQL is not registered" }
    }

    /** IDE languages that speak a brikk dialect, best first: the dialect plugins, then built-ins. */
    private val PREFERRED_LANGUAGES = mapOf(
        "doris" to listOf("DorisSQL"),
        "trino" to listOf("TrinoSQL"),
        "duckdb" to listOf("DuckDBSQL"),
        "clickhouse" to listOf("ClickHouseSQL", "ClickHouse"),
        "mysql" to listOf("MySQL"),
        "postgres" to listOf("PostgreSQL"),
        "bigquery" to listOf("BigQuery"),
    )

    /**
     * The marker line for a block of brikk [dialect]: `-- language=<id>` for the best installed
     * IDE language that speaks it, with `dialect=` only when the language does not imply it
     * (`-- language=genericsql dialect=datafusion`).
     */
    fun markerFor(dialect: String): String {
        val registered = Language.getRegisteredLanguages().filterIsInstance<SqlLanguageDialect>()
        val language = PREFERRED_LANGUAGES[dialect].orEmpty().firstNotNullOfOrNull { id -> registered.firstOrNull { it.id == id } }
            ?: registered.firstOrNull { it.id != GENERIC_LANGUAGE_ID && BrikkDialects.fromLanguageId(it.id) == dialect }
        val id = language?.id ?: GENERIC_LANGUAGE_ID
        val implied = language != null && BrikkDialects.fromLanguageId(id) == dialect
        return "-- language=${id.lowercase()}" + if (implied) "" else " dialect=$dialect"
    }

    /** The data source named by `using=`: exact name first, then case-insensitive. */
    fun dataSource(project: Project, name: String): DbDataSource? {
        val all = DbPsiFacade.getInstance(project).dataSources
        return all.firstOrNull { it.name == name } ?: all.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    /** The connection named by `using=`, for execution (DDL data sources cannot run SQL). */
    fun localDataSource(project: Project, name: String): LocalDataSource? {
        val all = LocalDataSourceManager.getInstance(project).dataSources
        return all.firstOrNull { it.name == name } ?: all.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    /** The block hosting the SQL of [file] when it is injected into a `.bsql` block. */
    fun hostOf(file: PsiFile): BrikkSqlBlock? {
        val manager = InjectedLanguageManager.getInstance(file.project)
        return manager.getInjectionHost(file) as? BrikkSqlBlock
            ?: file.originalFile.takeIf { it !== file }?.let { manager.getInjectionHost(it) as? BrikkSqlBlock }
    }

    /** The block at [offset] of a `.bsql` file, also from its marker line or the whitespace after it. */
    fun blockAt(file: PsiFile, offset: Int): BrikkSqlBlock? {
        val element = file.findElementAt(offset) ?: file.findElementAt(offset - 1) ?: return null
        if (element is BrikkSqlBlock) return element
        (file.findElementAt(offset - 1) as? BrikkSqlBlock)?.let { return it }
        var next: PsiElement? = element.nextSibling
        while (next is PsiWhiteSpace) next = next.nextSibling
        return next as? BrikkSqlBlock
    }
}

/** Injects each block's declared language (Generic SQL when unknown or unmarked). */
class BrikkSqlBlockInjector : MultiHostInjector {
    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        val block = context as? BrikkSqlBlock ?: return
        if (block.textLength == 0) return
        registrar.startInjecting(BrikkSqlBlocks.languageOf(block))
            .addPlace(null, null, block, TextRange(0, block.textLength))
            .doneInjecting()
    }

    override fun elementsToInjectIn(): List<Class<out PsiElement>> = listOf(BrikkSqlBlock::class.java)
}

/**
 * Resolves the SQL of a block against its `using=` data source. DataGrip consults this
 * for the injected file itself, but only while the `.bsql` file is not attached to a
 * console: an attached console's data source replaces provided ones.
 */
class BrikkSqlBlockDataSourceProvider : DataSourceProvider {
    override fun getDataSources(file: PsiFile): List<DbDataSource> {
        val block = BrikkSqlBlocks.hostOf(file) ?: return emptyList()
        val using = BrikkSqlBlocks.directiveOf(block)?.using ?: return emptyList()
        return listOfNotNull(BrikkSqlBlocks.dataSource(file.project, using))
    }
}

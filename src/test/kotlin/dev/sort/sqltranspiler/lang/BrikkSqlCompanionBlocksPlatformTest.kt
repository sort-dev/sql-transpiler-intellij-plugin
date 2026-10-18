package dev.sort.sqltranspiler.lang

import com.intellij.database.Dbms
import com.intellij.database.dataSource.DatabaseDriverManager
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.dataSource.LocalDataSourceManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.database.psi.DbPsiFacade
import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.lang.ExternalLanguageAnnotators
import com.intellij.lang.Language
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.sql.database.SqlDataSourceManager
import com.intellij.sql.dialects.SqlDataSourceMappings
import com.intellij.sql.psi.SqlReferenceExpression
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * With the Doris, Trino, DuckDB and ClickHouse plugins in the sandbox
 * (`-Ptest.<dialect>PluginZip=<zip>` for doris, trino, duckdb, clickhouse), their languages own their blocks. Skips without them.
 */
class BrikkSqlCompanionBlocksPlatformTest : BasePlatformTestCase() {
    private val companions = listOf("DorisSQL", "TrinoSQL", "DuckDBSQL", "ClickHouseSQL")

    private fun companionsPresent() = companions.all { Language.findLanguageByID(it) != null }

    /** Turns the Doris plugin's project PIPE setting on for the test, as in a PIPE user's project. */
    private fun withDorisPipes(test: () -> Unit) {
        val loader = checkNotNull(PluginManagerCore.getPlugin(PluginId.getId("dev.sort.doris-intellij-plugin"))?.pluginClassLoader)
        val settingsClass = loader.loadClass("dev.sort.doris.pipes.DorisPipesSettings")
        val setEnabled = settingsClass.getMethod("setEnabled", Boolean::class.javaPrimitiveType)
        setEnabled.invoke(project.getService(settingsClass), true)
        try { test() } finally { setEnabled.invoke(project.getService(settingsClass), false) }
    }

    fun testCompanionDialectsOwnTheirBlocks() {
        if (!companionsPresent()) return
        withDorisPipes { dialectsOwnTheirBlocks() }
    }

    private fun dialectsOwnTheirBlocks() {
        val text = """
            -- language=dorissql
            SELECT a FROM t |> WHERE a > 1;

            -- language=trinosql
            SELECT * FROM t FOR TIMESTAMP AS OF TIMESTAMP '2024-01-01 00:00:00 UTC';

            -- language=duckdbsql
            SELECT * EXCLUDE (region) FROM orders QUALIFY row_number() OVER (PARTITION BY region) = 1;

            -- language=clickhousesql
            SELECT toStartOfMonth(event_time) AS m FROM events FINAL SETTINGS max_threads = 1;
        """.trimIndent()
        myFixture.configureByText("companions.bsql", "<caret>$text")
        val host = InjectedLanguageManager.getInstance(project).getTopLevelFile(myFixture.file)
        val blocks = PsiTreeUtil.findChildrenOfType(host, BrikkSqlBlock::class.java).toList()
        val injected = blocks.map { InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(it).orEmpty().single().first.containingFile }
        assertEquals(companions, injected.map { it.language.id })
        for (file in injected) {
            val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(file.language, project, file.virtualFile)
            assertNotNull("${file.language.id} needs its own highlighter", highlighter)
        }

        val infos = myFixture.doHighlighting()
        // The dialect plugins' own annotators run inside the blocks: Trino and DuckDB repaint
        // the spans their lexers mask, which only happens under their language.
        fun keywordAt(word: String) = infos.any {
            it.forcedTextAttributesKey?.externalName == "SQL_KEYWORD" && text.substring(it.startOffset, it.endOffset) == word
        }
        assertTrue("Trino's masked-span recolor did not run", keywordAt("FOR"))
        assertTrue("DuckDB's masked-span recolor did not run", keywordAt("EXCLUDE"))
        val errors = infos.filter { it.severity == HighlightSeverity.ERROR }
        assertTrue("Companion blocks must not show syntax errors: ${errors.map { it.description }}", errors.isEmpty())
    }

    /**
     * The dialect plugins' own catalog features (PIPE `FROM` paths, live functions, relation facts)
     * find their connection through the shared `DialectDataSourceResolver`, in each plugin's own
     * copy of the support library. It must see a block's `using=` connection, not the host file's.
     * Needs support library builds whose resolver consults `sql.dataSourceProvider`.
     */
    fun testCompanionResolversSeeEachBlocksConnection() {
        if (!companionsPresent()) return
        val plugins = listOf(
            Triple("doris", "DORIS", "doris"),
            Triple("trino", "TRINO_BRIKK", "trino-brikk"),
            Triple("duckdb", "DUCKDB_BRIKK", "duckdb-brikk-native"),
            Triple("clickhouse", "CLICKHOUSE_BRIKK", "clickhouse-sortdev"),
        )
        val manager = LocalDataSourceManager.getInstance(project)
        val sources = plugins.map { (name, _, driver) ->
            LocalDataSource().apply {
                this.name = "$name live"
                databaseDriver = checkNotNull(DatabaseDriverManager.getInstance().getDriver(driver)) { driver }
                url = "jdbc:offline:$name"
                isAutoSynchronize = false
                isKeepAlive = false
            }.also(manager::addDataSource)
        }
        DbPsiFacade.getInstance(project).clearCaches()
        try {
            val text = plugins.joinToString("\n\n") { (name, _, _) -> "-- language=${name}sql using=\"$name live\"\nselect 1;" }
            myFixture.configureByText("resolvers.bsql", "<caret>$text")
            val host = InjectedLanguageManager.getInstance(project).getTopLevelFile(myFixture.file)
            val blocks = PsiTreeUtil.findChildrenOfType(host, BrikkSqlBlock::class.java).toList()
            val resolved = blocks.mapIndexed { index, block ->
                val file = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(block).orEmpty().single().first.containingFile
                val plugin = checkNotNull(PluginManagerCore.getPlugin(PluginId.getId("dev.sort.${plugins[index].first}-intellij-plugin")))
                val loader = checkNotNull(plugin.pluginClassLoader)
                val resolverClass = loader.loadClass("dev.brikk.house.sql.intellij.catalog.DialectDataSourceResolver")
                val resolver = resolverClass.getConstructor(Dbms::class.java, Boolean::class.javaPrimitiveType)
                    .newInstance(Dbms.byName(plugins[index].second), false)
                val resolution = resolverClass.getMethod("resolve", com.intellij.psi.PsiFile::class.java).invoke(resolver, file)
                (resolution.javaClass.getMethod("getDataSource").invoke(resolution) as LocalDataSource?)?.name
            }
            assertEquals(sources.map { it.name }, resolved)
        } finally {
            sources.forEach(manager::removeDataSource)
        }
    }

    /**
     * While editing, a dialect plugin's own engine-parser errors (its external annotator) mark
     * its blocks, as in its consoles. External annotators never run on injected fragments, so each
     * plugin registers its annotator for BrikkSQL, the host, as it does for Kotlin.
     */
    fun testCompanionNativeErrorsMarkTheirBlocksWhileEditing() {
        if (!companionsPresent()) return
        val text = """
            -- language=dorissql
            SELECT a FROM t WHERE;

            -- language=trinosql
            SELECT a FROM t WHERE;

            -- language=clickhousesql
            SELECT a FROM t WHERE;
        """.trimIndent()
        myFixture.configureByText("native-errors.bsql", "<caret>$text")
        val host = InjectedLanguageManager.getInstance(project).getTopLevelFile(myFixture.file)
        val errors = HashMap<String, List<String>>()
        @Suppress("UNCHECKED_CAST")
        for (annotator in ExternalLanguageAnnotators.allForFile(host.language, host) as List<ExternalAnnotator<Any?, Any?>>) {
            val result = annotator.doAnnotate(annotator.collectInformation(host) ?: continue) ?: continue
            val holder = AnnotationHolderImpl(AnnotationSession(host), false)
            holder.runAnnotatorWithContext(host) { _, h -> annotator.apply(host, result, h) }
            holder.assertAllAnnotationsCreated()
            errors[annotator.javaClass.simpleName] = holder.filter { it.severity == HighlightSeverity.ERROR }
                .map { "${text.substring(0, it.startOffset).count { c -> c == '\n' } + 1}: ${it.message}" }
        }
        val lines = errors.values.flatten().map { it.substringBefore(':').toInt() }.toSet()
        assertTrue("Doris, Trino and ClickHouse blocks need their engine errors: $errors", setOf(2, 5, 8).all { it in lines })
    }

    fun testCompanionBlocksResolveAgainstTheirOwnDataSources() {
        if (!companionsPresent()) return
        val schemas = mapOf(
            "Doris DDL" to ("DORIS" to "CREATE TABLE doris_t (doris_col INT);"),
            "Trino DDL" to ("TRINO_BRIKK" to "CREATE TABLE trino_t (trino_col INT);"),
            "DuckDB DDL" to ("DUCKDB_BRIKK" to "CREATE TABLE duck_t (duck_col INT);"),
            "ClickHouse DDL" to ("CLICKHOUSE_BRIKK" to "CREATE TABLE click_t (click_col Int32) ENGINE = Memory;"),
        )
        val manager = SqlDataSourceManager.getInstance(project)
        val sources = schemas.entries.mapIndexed { index, (name, value) ->
            val schema = myFixture.addFileToProject("companion-schema-$index.sql", value.second)
            manager.createEmpty().apply {
                this.name = name; definedDbms = Dbms.byName(value.first); setFiles(arrayOf(schema.virtualFile))
            }.also(manager::addDataSource)
        }
        try {
            sources.forEach { source ->
                PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread {
                    source.javaClass.methods.firstOrNull { it.name == "waitComputed" && it.parameterCount == 0 }?.invoke(source)
                })
            }
            DbPsiFacade.getInstance(project).clearCaches()
            val cases = listOf(
                Triple("dorissql", "Doris DDL", "doris_t" to "doris_col"),
                Triple("trinosql", "Trino DDL", "trino_t" to "trino_col"),
                Triple("duckdbsql", "DuckDB DDL", "duck_t" to "duck_col"),
                Triple("clickhousesql", "ClickHouse DDL", "click_t" to "click_col"),
            )
            val text = cases.joinToString("\n\n") { (language, source, names) ->
                "-- language=$language using=\"$source\"\nselect ${names.second} from ${names.first};"
            }
            myFixture.configureByText("companion-resolve.bsql", "<caret>$text")
            val host = InjectedLanguageManager.getInstance(project).getTopLevelFile(myFixture.file)
            val blocks = PsiTreeUtil.findChildrenOfType(host, BrikkSqlBlock::class.java).toList()
            val unresolved = mutableListOf<String>()
            for ((index, block) in blocks.withIndex()) {
                val file = InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(block).orEmpty().single().first.containingFile
                val (table, column) = cases[index].third
                for (name in listOf(table, column)) {
                    val reference = PsiTreeUtil.findChildrenOfType(file, SqlReferenceExpression::class.java).first { it.text == name }
                    if (reference.references.none { it.resolve() != null }) unresolved += "${file.language.id}:$name"
                }
                val sources = SqlDataSourceMappings.getInstance(project).getDataSources(file).map { it.name }
                assertEquals(file.language.id, listOf(cases[index].second), sources)
            }
            assertTrue("Unresolved in companion blocks: $unresolved", unresolved.isEmpty())
        } finally {
            sources.forEach(manager::removeDataSource)
        }
    }
}

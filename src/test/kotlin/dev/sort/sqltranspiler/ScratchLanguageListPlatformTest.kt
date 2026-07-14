package dev.sort.sqltranspiler

import com.intellij.codeInspection.LanguageInspectionSuppressors
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.LanguageUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.sort.sqltranspiler.lang.BrikkSqlDialect
import dev.sort.sqltranspiler.lang.BrikkSqlErrorAnnotator

/**
 * BrikkSQL must be offered in New -> Scratch File. The popup lists
 * [LanguageUtil.getFileLanguages], which requires a registered parser definition and a
 * primary [com.intellij.openapi.fileTypes.LanguageFileType] with a non-empty extension.
 * Each sub-condition is asserted separately so a regression names the broken link.
 */
class ScratchLanguageListPlatformTest : BasePlatformTestCase() {

    fun testBrikkSqlQualifiesAsFileLanguage() {
        val language = BrikkSqlDialect.INSTANCE
        assertNotNull(
            "parser definition must be registered for BrikkSQL",
            LanguageParserDefinitions.INSTANCE.forLanguage(language),
        )
        val fileType = language.associatedFileType
        assertNotNull("BrikkSQL must have an associated LanguageFileType", fileType)
        assertTrue("associated file type needs a default extension", !fileType!!.defaultExtension.isNullOrEmpty())
    }

    fun testBrikkSqlAppearsInScratchLanguageList() {
        val languages = LanguageUtil.getFileLanguages()
        assertTrue(
            "BrikkSQL missing from LanguageUtil.getFileLanguages(); present: " +
                languages.filter { it.id.contains("sql", ignoreCase = true) }.map { it.id },
            languages.any { it.id == "BrikkSQL" },
        )
        assertEquals("Brikk SQL", BrikkSqlDialect.INSTANCE.displayName)
    }

    fun testSchemaBoundInspectionsAreSuppressedInBsqlFiles() {
        val file = myFixture.configureByText("suppress.bsql", "-- dialect: duckdb\nselect unknown_col from t;\n")
        val suppressors = LanguageInspectionSuppressors.INSTANCE.allForLanguage(file.language)
        assertTrue("BrikkSQL needs an inspection suppressor registered", suppressors.isNotEmpty())
        // The toolId isSuppressedFor receives is each tool's suppressId (SqlResolve, not
        // SqlResolveInspection) — both spellings must be covered.
        for (tool in listOf(
            "SqlResolve", "SqlResolveInspection",
            "SqlType", "SqlTypeInspection",
            "SqlSignature", "SqlSignatureInspection",
            "SqlNoDataSourceInspection",
        )) {
            assertTrue(
                "$tool should be suppressed in .bsql",
                suppressors.any { it.isSuppressedFor(file.firstChild, tool) },
            )
        }
        // Non-schema inspections stay live — the suppressor is a scalpel, not a blanket.
        assertTrue(suppressors.none { it.isSuppressedFor(file.firstChild, "SqlDialectInspection") })
    }

    fun testSubstrateSyntaxErrorsAreNotPaintedInBsqlFiles() {
        // ClickHouse-flavoured block: toStartOfMonth/if/has and the CTE shape from the
        // report leave PsiErrorElements in the Generic substrate parse. The
        // highlightErrorFilter must keep every one of them off the editor.
        myFixture.configureByText(
            "errors.bsql",
            """
            -- dialect: clickhouse
            WITH monthly AS (
                SELECT user_id, toStartOfMonth(event_time) AS month,
                       count(*) AS events,
                       if(sum(amount) > 1000, 'high', 'low') AS tier
                FROM events
                WHERE has(tags, 'active')
                GROUP BY user_id, toStartOfMonth(event_time)
            )
            SELECT user_id, month FROM monthly ORDER BY month DESC LIMIT 100;
            """.trimIndent(),
        )
        val errors = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.ERROR)
        assertTrue(
            "no substrate syntax errors may surface in .bsql, got: " +
                errors.map { "'${it.description}' @${it.startOffset}" },
            errors.isEmpty(),
        )
    }

    fun testBrikkParserSuppliesTheAuthoritativeSyntaxErrors() {
        // Layered design: the substrate's errors are blanket-filtered, brikk-sql's own
        // dialect parse paints the real ones. External annotators don't run inside
        // doHighlighting() in tests (async daemon pass; same reason the doris plugin has
        // no such test), so the annotator pipeline is driven directly.
        val text = """
            -- dialect: mysql
            select 1;

            -- dialect: duckdb
            SELEC banana FROM FROM;
        """.trimIndent()
        val file = myFixture.configureByText("authoritative.bsql", text)
        val annotator = BrikkSqlErrorAnnotator()
        val info = annotator.collectInformation(file)
        assertNotNull("annotator must claim .bsql files", info)
        val result = annotator.doAnnotate(info)
        assertNotNull(result)
        val errors = result!!.errors
        assertEquals(
            "valid mysql block clean, broken duckdb block flagged once: ${errors.map { it.message }}",
            1, errors.size,
        )
        val error = errors.single()
        assertTrue("message names the dialect: ${error.message}", "DuckDB" in error.message)
        val brokenLineStart = text.indexOf("SELEC banana")
        assertTrue(
            "error must sit in the broken segment (range ${error.range}, segment @$brokenLineStart)",
            error.range.startOffset >= brokenLineStart && error.range.endOffset <= text.length,
        )
    }

    fun testErrorAnnotatorIgnoresForeignFiles() {
        val file = myFixture.configureByText("plain.sql", "-- dialect: duckdb\nSELEC banana FROM FROM;\n")
        assertNull(
            "the annotator has no authority outside BrikkSQL files",
            BrikkSqlErrorAnnotator().collectInformation(file),
        )
    }
}

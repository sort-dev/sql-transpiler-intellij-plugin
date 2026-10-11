package dev.sort.sqltranspiler.lang

import com.intellij.lang.LanguageAnnotators
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BrikkSqlErrorAnnotatorPlatformTest : BasePlatformTestCase() {
    private val prefix = "-- dialect: mysql\nSELECT 1;\n"
    private val sql = prefix + "SELECT * FROM t WHERE;"

    fun testValidationRunsWhenTheDaemonAlreadyFoundErrors() {
        val file = myFixture.configureByText("validation.bsql", sql)
        val annotator = BrikkSqlErrorAnnotator()

        val input = checkNotNull(annotator.collectInformation(file, myFixture.editor, true))
        val result = checkNotNull(annotator.doAnnotate(input))

        assertTrue(result.errors.isNotEmpty())
        assertTrue(result.errors.all { it.range.startOffset >= prefix.length })
    }

    fun testNativeErrorIsHighlightedAlongsideAnotherAnnotatorsError() {
        myFixture.configureByText("validation.bsql", sql)
        val otherRange = TextRange(sql.indexOf("SELECT 1"), prefix.length - 1)
        val otherAnnotator = IndependentErrorAnnotator()
        LanguageAnnotators.INSTANCE.addExplicitExtension(BrikkSqlDialect.INSTANCE, otherAnnotator)
        try {
            val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
            assertTrue("The other annotator must produce an error", errors.any {
                it.startOffset == otherRange.startOffset && it.endOffset == otherRange.endOffset
            })
            assertTrue("The native parser must still highlight the invalid statement", errors.any {
                it.startOffset >= prefix.length && it.endOffset > it.startOffset
            })
        } finally {
            LanguageAnnotators.INSTANCE.removeExplicitExtension(BrikkSqlDialect.INSTANCE, otherAnnotator)
        }
    }

    class IndependentErrorAnnotator : Annotator {
        override fun annotate(element: PsiElement, holder: AnnotationHolder) {
            if (element !is PsiFile) return
            val start = element.text.indexOf("SELECT 1;")
            if (start < 0) return
            holder.newAnnotation(HighlightSeverity.ERROR, "Independent validation error")
                .range(TextRange.from(start, "SELECT 1;".length))
                .create()
        }
    }
}

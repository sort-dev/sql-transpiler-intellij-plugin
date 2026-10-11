package dev.sort.sqltranspiler

import com.intellij.openapi.progress.ProgressManager
import dev.brikk.house.sql.verify.SqlVerifier
import dev.brikk.house.sql.verify.VerifyResult
import org.apache.doris.nereids.exceptions.ParseException
import org.apache.doris.sqlparser.DorisSqlParser

/** Directly linked, source-pinned grammar. No filesystem discovery or system property. */
internal class BundledDorisVerifier : SqlVerifier {
    override val engine = "doris"
    private val parser = DorisSqlParser()

    override fun verify(sql: String): VerifyResult = parse(sql) { parser.parseStatement(sql) }
    override fun verifyExpression(sql: String): VerifyResult = parse(sql) { parser.parseExpression(sql) }

    private fun parse(sql: String, action: () -> Unit): VerifyResult {
        ProgressManager.checkCanceled()
        val result = try {
            action()
            VerifyResult(accepted = true)
        } catch (e: ParseException) {
            val match = POSITION.find(e.message.orEmpty())
            val line = match?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
            val codePoints = match?.groupValues?.get(2)?.toIntOrNull()
            val lineText = line?.let { sql.lineSequence().elementAtOrNull(it - 1) }
            val col = if (lineText != null && codePoints != null && codePoints in 0..lineText.codePointCount(0, lineText.length)) {
                lineText.offsetByCodePoints(0, codePoints) + 1
            } else null
            VerifyResult(accepted = false, error = e.message, line = line, col = col)
        }
        ProgressManager.checkCanceled()
        return result
    }

    private companion object {
        val POSITION = Regex("""line\s+(\d+),\s*pos\s+(\d+)""")
    }
}

package dev.sort.sqltranspiler

/**
 * Block markers: a line comment that declares what the SQL after it is, up to the next
 * marker or end of text. The IDE's own injection comment form is the primary one, with
 * optional `dialect=` (brikk-sql dialect override) and `using=` (data source name):
 *
 * ```
 * -- language=mysql using="Local MySQL"
 * select * from ...
 *
 * -- language=genericsql dialect=DataFusion using="SpiceDB localhost"
 * select * from ...
 * ```
 *
 * The legacy `-- dialect: xyz` form still marks a block (no language, no connection).
 *
 * Pure text-level parsing (no PSI) so it works identically in unit tests, actions, and
 * annotators.
 */
object DialectMarker {

    private val LEGACY_MARKER = Regex("""^\s*--\s*dialect\s*:\s*([A-Za-z0-9_]+)\s*$""")
    private val LANGUAGE_MARKER = Regex("""^\s*--\s*(language=.*?)\s*$""")
    // IntelliLang's own comment-injection attribute syntax: key=value, value optionally quoted.
    private val ATTRIBUTE = Regex("""([\S&&[^=]]+)=("(?:[^"]|\\")*"|\S*)""")

    /**
     * What a marker line declares. [languageId] is the IDE language named by `language=`
     * (null for the legacy form); [dialect] is the brikk-sql dialect name.
     */
    data class Directive(val dialect: String, val languageId: String? = null, val using: String? = null)

    /** A marker occurrence: its directive plus the line's offsets in the full text. */
    data class Marker(
        val dialect: String,
        val lineStartOffset: Int,
        val lineEndOffset: Int,
        val languageId: String? = null,
        val using: String? = null,
    ) {
        /** Whether brikk-sql knows this dialect (a marker can name an unknown one). */
        val isSupported: Boolean get() = BrikkDialects.isSupported(dialect)
    }

    /** Parses a single line; returns the dialect name or null when not a marker line. */
    fun parseLine(line: String): String? = parseDirective(line)?.dialect

    fun parseDirective(line: String): Directive? {
        LEGACY_MARKER.matchEntire(line)?.let { return Directive(it.groupValues[1].lowercase()) }
        val body = LANGUAGE_MARKER.matchEntire(line)?.groupValues?.get(1) ?: return null
        val values = ATTRIBUTE.findAll(body).associate { it.groupValues[1] to unquote(it.groupValues[2]) }
        val languageId = values["language"]?.takeIf { it.isNotBlank() } ?: return null
        val dialect = values["dialect"]?.takeIf { it.isNotBlank() }?.let { BrikkDialects.fromLanguageId(it) ?: it.lowercase() }
            ?: BrikkDialects.fromLanguageId(languageId)
            ?: languageId.lowercase()
        return Directive(dialect, languageId, values["using"]?.takeIf { it.isNotBlank() })
    }

    private fun unquote(value: String): String =
        if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) value.substring(1, value.length - 1).replace("\\\"", "\"")
        else value

    /** Marker-shaped lines count only when the preceding dialect's lexer is outside a token. */
    @JvmOverloads
    fun findAll(text: String, initialDialect: String? = null): List<Marker> {
        val out = ArrayList<Marker>()
        var lineStart = 0
        var blockStart = 0
        var activeDialect = initialDialect?.takeIf { it.isNotBlank() && BrikkDialects.isSupported(it) }
        while (lineStart <= text.length) {
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            val directive = parseDirective(text.substring(lineStart, lineEnd))
            if (directive != null) {
                val dialect = directive.dialect
                val prefix = text.substring(blockStart, lineEnd)
                // Before the first declaration, an untyped workbench must be conservative
                // about supported literal forms, including dollar strings and backticks.
                val dialects = activeDialect?.let(::listOf) ?: BrikkDialects.names
                if (dialects.all { SqlSourceSnapshot.isLexicallyComplete(prefix, it) }) {
                    out.add(Marker(dialect, lineStart, lineEnd, directive.languageId, directive.using))
                    activeDialect = dialect.takeIf(BrikkDialects::isSupported)
                    blockStart = (lineEnd + 1).coerceAtMost(text.length)
                }
            }
            if (lineEnd == text.length) break
            lineStart = lineEnd + 1
        }
        return out
    }

    /**
     * The dialect governing [offset]: the closest marker at or above it, or null when no
     * marker precedes the offset.
     */
    @JvmOverloads
    fun dialectAt(text: String, offset: Int, initialDialect: String? = null): String? =
        findAll(text, initialDialect).lastOrNull { it.lineStartOffset <= offset }?.dialect

    /**
     * The text range governed by the marker starting at [marker]: from the line after
     * the marker to the start of the next marker line (or end of text). Returns
     * (startOffset, endOffset).
     */
    @JvmOverloads
    fun segmentAfter(text: String, marker: Marker, markers: List<Marker> = findAll(text)): Pair<Int, Int> {
        val start = (marker.lineEndOffset + 1).coerceAtMost(text.length)
        val next = markers.firstOrNull { it.lineStartOffset > marker.lineStartOffset }
        val end = next?.lineStartOffset ?: text.length
        return start to end.coerceAtLeast(start)
    }
}

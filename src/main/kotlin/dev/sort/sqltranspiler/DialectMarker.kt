package dev.sort.sqltranspiler

/**
 * The `-- dialect: xyz` marker convention: a line comment that declares the dialect of
 * the SQL that follows it, up to the next marker or end of text.
 *
 * ```
 * -- dialect: clickhouse
 * select * from ...
 * ```
 *
 * Pure text-level parsing (no PSI) so it works identically in unit tests, actions, and
 * annotators.
 */
object DialectMarker {

    private val MARKER = Regex("""^\s*--\s*dialect\s*:\s*([A-Za-z0-9_]+)\s*$""")

    /** A marker occurrence: the dialect name plus the line's offsets in the full text. */
    data class Marker(
        val dialect: String,
        val lineStartOffset: Int,
        val lineEndOffset: Int,
    ) {
        /** Whether brikk-sql knows this dialect (a marker can name an unknown one). */
        val isSupported: Boolean get() = BrikkDialects.isSupported(dialect)
    }

    /** Parses a single line; returns the dialect name or null when not a marker line. */
    fun parseLine(line: String): String? =
        MARKER.matchEntire(line)?.groupValues?.get(1)?.lowercase()

    /** Marker-shaped lines count only when the preceding dialect's lexer is outside a token. */
    @JvmOverloads
    fun findAll(text: String, initialDialect: String? = null): List<Marker> {
        val out = ArrayList<Marker>()
        var lineStart = 0
        var blockStart = 0
        var activeDialect = initialDialect?.takeIf { it.isNotBlank() && BrikkDialects.isSupported(it) }
        while (lineStart <= text.length) {
            val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
            val dialect = parseLine(text.substring(lineStart, lineEnd))
            if (dialect != null) {
                val prefix = text.substring(blockStart, lineEnd)
                // Before the first declaration, an untyped workbench must be conservative
                // about supported literal forms, including dollar strings and backticks.
                val dialects = activeDialect?.let(::listOf) ?: BrikkDialects.names
                if (dialects.all { SqlSourceSnapshot.isLexicallyComplete(prefix, it) }) {
                    out.add(Marker(dialect, lineStart, lineEnd))
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

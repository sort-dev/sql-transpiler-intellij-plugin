package dev.sort.sqltranspiler

import com.sun.net.httpserver.HttpServer
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParseOnlyDuckdbVerifierTest {
    @Test fun `serialization checks SELECT without binding external tables`() {
        ParseOnlyDuckdbVerifier().use { verifier ->
            assertTrue(verifier.verify("SELECT * FROM missing_table").accepted)
            assertTrue(verifier.verify("SELECT 'it''s safe; CREATE TABLE x AS SELECT 1'").accepted)
            val invalid = verifier.verify("SELEC banana FROM FROM")
            assertTrue(invalid.verified)
            assertFalse(invalid.accepted)
        }
    }

    @Test fun `non SELECT verification never reads local files or contacts HTTP`() {
        val path = Files.createTempFile("transpiler-verification-", ".csv")
        val requests = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/probe.csv") { exchange ->
            requests.incrementAndGet()
            val content = "id\n1\n".toByteArray()
            exchange.sendResponseHeaders(200, content.size.toLong())
            exchange.responseBody.use { it.write(content) }
        }
        server.start()
        try {
            Files.writeString(path, "id\n1\n")
            val oldAccess = FileTime.from(Instant.parse("2000-01-01T00:00:00Z"))
            Files.setAttribute(path, "basic:lastAccessTime", oldAccess)
            ParseOnlyDuckdbVerifier().use { verifier ->
                val inputs = listOf(path.toString(), "$path.missing", "http://127.0.0.1:${server.address.port}/probe.csv")
                for (input in inputs) {
                    val quoted = input.replace("'", "''")
                    assertTrue(verifier.verify("SELECT * FROM read_csv('$quoted')").accepted)
                    val verdict = verifier.verify("CREATE TABLE probe AS SELECT * FROM read_csv('$quoted')")
                    assertFalse(verdict.verified, verdict.toString())
                    assertFalse(verdict.accepted)
                }
            }
            assertTrue(Files.getAttribute(path, "basic:lastAccessTime") == oldAccess, "verification read the CSV")
            assertTrue(requests.get() == 0, "verification contacted the HTTP endpoint")
        } finally {
            server.stop(0)
            Files.deleteIfExists(path)
        }
    }

    @Test fun `disposed verifier cannot reopen its engine`() {
        val verifier = ParseOnlyDuckdbVerifier()
        assertTrue(verifier.verify("SELECT 1").accepted)
        verifier.close()
        assertFalse(verifier.verify("SELECT 1").verified)
    }
}

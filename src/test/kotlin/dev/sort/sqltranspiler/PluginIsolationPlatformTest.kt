package dev.sort.sqltranspiler

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** No product-type linkage: the actual distribution must own the runtime classes. */
class PluginIsolationPlatformTest : BasePlatformTestCase() {
    fun testPrivateLibrariesAndNativeVerificationWithoutKotlinOrCompanions() {
        val descriptor = PluginManagerCore.getPlugin(PluginId.getId("dev.sort.sql-transpiler-intellij-plugin"))!!
        val loader = descriptor.pluginClassLoader!!
        assertEquals("com.intellij.ide.plugins.cl.PluginClassLoader", loader.javaClass.name)
        assertFalse(PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.kotlin"))?.isEnabled == true)
        for (name in listOf("doris", "duckdb", "trino")) {
            assertFalse(PluginManagerCore.getPlugin(PluginId.getId("dev.sort.$name-intellij-plugin"))?.isEnabled == true)
        }
        for ((name, version) in listOf(
            "dev.brikk.house.sql.shape.SqlFragment" to "0.19.0",
            "dev.brikk.house.sql.metadata.FunctionDef" to "0.19.0",
            "dev.brikk.house.sql.verify.SqlVerifier" to "0.19.0",
            "dev.brikk.house.sql.intellij.EditorSnapshot" to "0.1.0",
            "dev.brikk.house.intellij.BrikkDialect" to "0.2.0-SNAPSHOT",
            "org.apache.doris.sqlparser.DorisSqlParser" to "4.1.4-gad35a140c7fd",
            "org.duckdb.DuckDBDriver" to "1.5.6.0",
        )) {
            val type = loader.loadClass(name)
            assertSame(name, loader, type.classLoader)
            assertTrue(name, type.getResource(type.simpleName + ".class")!!.toString().contains(version))
        }
        val type = loader.loadClass("dev.brikk.house.sql.shape.SqlFragment")
        val fragment = type.getConstructor(String::class.java, String::class.java)
            .newInstance("SELECT * FROM (FROM t |> SELECT id)", "doris")
        val rendered = type.getMethod("toExecutable", String::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            .invoke(fragment, "duckdb", false, true)
        val sql = rendered.javaClass.getMethod("getSql").invoke(rendered) as String
        assertFalse(sql.contains("|>"))
        val map = rendered.javaClass.getMethod("getSourceMap").invoke(rendered)
        assertSame(sql, map.javaClass.getMethod("getOutput").invoke(map))

        val serviceType = loader.loadClass("dev.sort.sqltranspiler.VerifierService")
        val service = serviceType.getConstructor().newInstance()
        try {
            for (engine in listOf("doris", "duckdb", "trino", "postgres")) {
                val report = serviceType.getMethod("verify", String::class.java, List::class.java)
                    .invoke(service, engine, listOf("SELECT 1"))
                assertNotNull("$engine verifier must load in the real plugin", report)
                assertEquals(engine, true, report.javaClass.getMethod("getAllAccepted").invoke(report))
            }
        } finally { serviceType.getMethod("dispose").invoke(service) }
    }
}

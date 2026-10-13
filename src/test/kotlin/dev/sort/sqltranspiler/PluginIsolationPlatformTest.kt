package dev.sort.sqltranspiler

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** No product-type linkage: the actual distribution must own the runtime classes. */
class PluginIsolationPlatformTest : BasePlatformTestCase() {
    fun testRuntimeLibraryOrigins() {
        val descriptor = checkNotNull(PluginManagerCore.getPlugin(PluginId.getId("dev.sort.sql-transpiler-intellij-plugin")))
        val loader = checkNotNull(descriptor.pluginClassLoader)
        val stdlib = loader.loadClass("kotlin.Unit")
        assertSame(Unit::class.java, stdlib)
        val stdlibOrigin = checkNotNull(stdlib.getResource("Unit.class")).toString()
        println("Plugin Kotlin stdlib: $stdlibOrigin")
        assertTrue("Kotlin stdlib must come from the IDE", stdlibOrigin.contains(PathManager.getHomePath()))

        for (name in listOf("kotlinx.serialization.KSerializer", "kotlinx.serialization.json.Json")) {
            val platformType = Unit::class.java.classLoader.loadClass(name)
            val platformOrigin = checkNotNull(platformType.getResource(platformType.simpleName + ".class")).toString()
            println("Platform $name: $platformOrigin")
            assertFalse("The worker must not use plugin serialization", platformOrigin.contains(descriptor.pluginPath.toString()))

            val pluginType = loader.loadClass(name)
            val pluginOrigin = checkNotNull(pluginType.getResource(pluginType.simpleName + ".class")).toString()
            println("Plugin $name: $pluginOrigin")
            assertNotSame(platformType, pluginType)
            assertSame(name, loader, pluginType.classLoader)
            assertTrue("The plugin must use its packaged serialization", pluginOrigin.contains(descriptor.pluginPath.toString()))
        }
        val jsonType = loader.loadClass("kotlinx.serialization.json.Json")
        val json = checkNotNull(jsonType.getField("Default").get(null))
        val element = jsonType.getMethod("parseToJsonElement", String::class.java).invoke(json, "{\"value\":1}")
        assertSame(loader, element.javaClass.classLoader)
    }

    fun testPrivateLibrariesAndNativeVerificationWithoutKotlinWithConfiguredCompanions() {
        val descriptor = PluginManagerCore.getPlugin(PluginId.getId("dev.sort.sql-transpiler-intellij-plugin"))!!
        val loader = descriptor.pluginClassLoader!!
        assertEquals("com.intellij.ide.plugins.cl.PluginClassLoader", loader.javaClass.name)
        assertFalse(PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.kotlin"))?.isEnabled == true)
        val installedSiblings = System.getProperty("test.siblingPlugins", "").split(',').filter { it.isNotEmpty() }.toSet()
        for (name in listOf("clickhouse", "doris", "duckdb", "trino")) {
            val companion = PluginManagerCore.getPlugin(PluginId.getId("dev.sort.$name-intellij-plugin"))
            assertEquals(name, name in installedSiblings, companion?.isEnabled == true)
            if (name in installedSiblings) {
                val companionLoader = checkNotNull(companion?.pluginClassLoader)
                assertNotSame(name, loader, companionLoader)
                val shared = "dev.brikk.house.sql.shape.SqlFragment"
                val owned = loader.loadClass(shared)
                val sibling = companionLoader.loadClass(shared)
                assertNotSame("$name $shared", owned, sibling)
                assertSame("$name $shared", companionLoader, sibling.classLoader)
            }
        }
        for ((name, version) in listOf(
            "dev.brikk.house.sql.shape.SqlFragment" to "0.20.0-SNAPSHOT",
            "dev.brikk.house.sql.metadata.FunctionDef" to "0.20.0-SNAPSHOT",
            "dev.brikk.house.sql.verify.SqlVerifier" to "0.20.0-SNAPSHOT",
            "dev.brikk.house.sql.intellij.EditorSnapshot" to "0.2.0-SNAPSHOT",
            "dev.brikk.house.sql.intellij.dialect.SqlDialectMappingKt" to "0.2.0-SNAPSHOT",
            "dev.brikk.house.sql.intellij.dialect.SqlDialectRegistry" to "0.2.0-SNAPSHOT",
            "dev.brikk.house.intellij.BrikkDialect" to "0.3.0-SNAPSHOT",
            "org.apache.doris.sqlparser.DorisSqlParser" to "4.1.4-gad35a140c7fd",
            "org.duckdb.DuckDBDriver" to "1.5.6.0",
        )) {
            val type = loader.loadClass(name)
            assertSame(name, loader, type.classLoader)
            val origin = checkNotNull(type.getResource(type.simpleName + ".class")).toString()
            assertTrue(name, origin.contains("-$version.jar"))
            assertTrue("$name must come from the Transpiler distribution", origin.contains(descriptor.pluginPath.toString()))
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

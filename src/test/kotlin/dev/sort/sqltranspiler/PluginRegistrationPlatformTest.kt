package dev.sort.sqltranspiler

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.testing.PluginRegistrationContract

class PluginRegistrationPlatformTest : BasePlatformTestCase() {
    fun testRegisteredExtensionsAndServicesInstantiate() {
        PluginRegistrationContract("dev.sort.sql-transpiler-intellij-plugin", "dev.sort.sqltranspiler").check(project)
    }
}

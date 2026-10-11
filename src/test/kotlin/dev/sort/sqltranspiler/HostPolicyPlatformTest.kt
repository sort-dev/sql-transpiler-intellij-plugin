package dev.sort.sqltranspiler

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.sql.dialects.SqlDialectMappings
import com.intellij.sql.dialects.SqlLanguageDialect
import com.intellij.sql.dialects.generic.GenericDialect
import com.intellij.sql.dialects.mysql.MysqlDialect
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.FileContentUtil
import java.lang.reflect.InvocationTargetException

/**
 * The scratch-vs-console host policy: marker gutter + "go Generic SQL" banner behavior
 * across lenient (Generic SQL) and strict (vendor dialect) hosts.
 */
class HostPolicyPlatformTest : BasePlatformTestCase() {

    private val mixed = "-- dialect: clickhouse\nselect toString(1);\n"

    override fun tearDown() {
        try {
            setDialectMapping(null, null)
        } finally {
            super.tearDown()
        }
    }

    private fun brikkMarkers() =
        DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
            .filter { it.lineMarkerTooltip?.contains("Brikk SQL") == true }

    private fun setDialectMapping(file: VirtualFile?, dialect: SqlLanguageDialect?) {
        val workspaceSetter = try {
            Class.forName("com.intellij.sql.dialects.SqlDialectMappingUtilsKt").methods.singleOrNull {
                it.name == "setDialectMappingSync" && it.parameterCount == 3
            }
        } catch (_: ClassNotFoundException) {
            null
        }
        if (workspaceSetter == null) {
            SqlDialectMappings.getInstance(project).setMapping(file, dialect)
        } else {
            // 263's legacy setter changes storage, not the active workspace mapping or cached PSI.
            WriteAction.run<RuntimeException> {
                try {
                    workspaceSetter.invoke(null, project, file, dialect)
                    FileContentUtil.reparseFiles(project, listOfNotNull(file), true)
                } catch (failure: InvocationTargetException) {
                    throw failure.targetException
                }
            }
        }
    }

    fun testGutterShownInLenientHost() {
        myFixture.configureByText("lenient.sql", mixed)
        setDialectMapping(myFixture.file.virtualFile, GenericDialect.INSTANCE)
        myFixture.doHighlighting()
        assertTrue(brikkMarkers().isNotEmpty())
    }

    fun testGutterHiddenInStrictVendorHost() {
        myFixture.configureByText("strict.sql", mixed)
        setDialectMapping(myFixture.file.virtualFile, MysqlDialect.INSTANCE)
        myFixture.doHighlighting()
        assertTrue("no gutter in vendor-dialect hosts", brikkMarkers().isEmpty())
    }

    fun testBannerOfferedForForeignMarkersInVendorHost() {
        myFixture.configureByText("banner.sql", mixed)
        setDialectMapping(myFixture.file.virtualFile, MysqlDialect.INSTANCE)
        val data = MixedDialectNotificationProvider()
            .collectNotificationData(project, myFixture.file.virtualFile)
        assertNotNull("banner expected for clickhouse block in MySQL file", data)
    }

    fun testNoBannerWhenMarkerMatchesHostDialect() {
        myFixture.configureByText("native.sql", "-- dialect: mysql\nselect 1;\n")
        setDialectMapping(myFixture.file.virtualFile, MysqlDialect.INSTANCE)
        val data = MixedDialectNotificationProvider()
            .collectNotificationData(project, myFixture.file.virtualFile)
        assertNull("marker matching the host dialect needs no banner", data)
    }

    fun testNoBannerInGenericHost() {
        myFixture.configureByText("generic.sql", mixed)
        setDialectMapping(myFixture.file.virtualFile, GenericDialect.INSTANCE)
        val data = MixedDialectNotificationProvider()
            .collectNotificationData(project, myFixture.file.virtualFile)
        assertNull("Generic SQL host is already fine", data)
    }
}

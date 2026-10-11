package dev.sort.sqltranspiler

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.sql.dialects.generic.GenericDialect
import com.intellij.sql.dialects.mysql.MysqlDialect
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.ui.UIUtil

/**
 * The scratch-vs-console host policy: marker gutter + "go Generic SQL" banner behavior
 * across lenient (Generic SQL) and strict (vendor dialect) hosts.
 */
class HostPolicyPlatformTest : BasePlatformTestCase() {

    private val mixed = "-- dialect: clickhouse\nselect toString(1);\n"

    override fun tearDown() {
        try {
            setDialectMapping(project, null, null)
        } finally {
            super.tearDown()
        }
    }

    private fun brikkMarkers() =
        DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
            .filter { it.lineMarkerTooltip?.contains("Brikk SQL") == true }

    fun testGutterShownInLenientHost() {
        myFixture.configureByText("lenient.sql", mixed)
        setDialectMapping(project, myFixture.file.virtualFile, GenericDialect.INSTANCE)
        myFixture.doHighlighting()
        assertTrue(brikkMarkers().isNotEmpty())
    }

    fun testGutterHiddenInStrictVendorHost() {
        myFixture.configureByText("strict.sql", mixed)
        setDialectMapping(project, myFixture.file.virtualFile, MysqlDialect.INSTANCE)
        myFixture.doHighlighting()
        assertTrue("no gutter in vendor-dialect hosts", brikkMarkers().isEmpty())
    }

    fun testBannerOfferedForForeignMarkersInVendorHost() {
        myFixture.configureByText("banner.sql", mixed)
        setDialectMapping(project, myFixture.file.virtualFile, MysqlDialect.INSTANCE)
        val data = MixedDialectNotificationProvider()
            .collectNotificationData(project, myFixture.file.virtualFile)
        assertNotNull("banner expected for clickhouse block in MySQL file", data)
    }

    fun testNoBannerWhenMarkerMatchesHostDialect() {
        myFixture.configureByText("native.sql", "-- dialect: mysql\nselect 1;\n")
        setDialectMapping(project, myFixture.file.virtualFile, MysqlDialect.INSTANCE)
        val data = MixedDialectNotificationProvider()
            .collectNotificationData(project, myFixture.file.virtualFile)
        assertNull("marker matching the host dialect needs no banner", data)
    }

    fun testNoBannerInGenericHost() {
        myFixture.configureByText("generic.sql", mixed)
        setDialectMapping(project, myFixture.file.virtualFile, GenericDialect.INSTANCE)
        val data = MixedDialectNotificationProvider()
            .collectNotificationData(project, myFixture.file.virtualFile)
        assertNull("Generic SQL host is already fine", data)
    }

    fun testSwitchActionChangesContentFileToGenericWithoutWarning() {
        val file = myFixture.addFileToProject("queries/mixed.sql", mixed).virtualFile
        myFixture.configureFromExistingVirtualFile(file)
        setDialectMapping(project, file, MysqlDialect.INSTANCE)
        assertSame(MysqlDialect.INSTANCE, myFixture.file.language)
        val warnings = mutableListOf<Notification>()
        project.messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
            override fun notify(notification: Notification) {
                if (notification.type == NotificationType.WARNING) warnings.add(notification)
            }
        })
        val data = checkNotNull(MixedDialectNotificationProvider().collectNotificationData(project, file))
        val panel = checkNotNull(data.apply(TextEditorProvider.getInstance().getTextEditor(myFixture.editor)))

        UIUtil.findComponentsOfType(panel, HyperlinkLabel::class.java).first().doClick()

        assertSame(GenericDialect.INSTANCE, myFixture.file.language)
        assertTrue("Switching an ordinary content file must not warn", warnings.isEmpty())
    }
}

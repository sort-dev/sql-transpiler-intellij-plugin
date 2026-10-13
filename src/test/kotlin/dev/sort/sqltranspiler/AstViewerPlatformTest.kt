package dev.sort.sqltranspiler

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.dialects.Dialects
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AstViewerPlatformTest : BasePlatformTestCase() {
    private var previousAsync: String? = null
    override fun setUp() {
        super.setUp()
        previousAsync = System.getProperty("intellij.progress.task.ignoreHeadless")
        // Exercise the shipping asynchronous Task path, not the fixture's synchronous shortcut.
        System.setProperty("intellij.progress.task.ignoreHeadless", "true")
    }

    override fun tearDown() {
        try {
            if (previousAsync == null) System.clearProperty("intellij.progress.task.ignoreHeadless")
            else System.setProperty("intellij.progress.task.ignoreHeadless", previousAsync!!)
        } finally { super.tearDown() }
    }

    fun testParsingAndTreeBuildingRunOffTheUiThread() {
        myFixture.configureByText("tree.bsql", "SELECT 1")
        val onUi = AtomicBoolean(true)
        val panel = AstViewerPanel(project) { text, _ ->
            onUi.set(ApplicationManager.getApplication().isDispatchThread)
            Dialects.BASE.parse(text).filterNotNull()
        }
        Disposer.register(testRootDisposable, panel)
        panel.refresh()
        await { !panel.isBusy }
        assertFalse(onUi.get())
        assertEquals("statements (base)", panel.displayedRoot.toString())
        assertTrue(panel.displayedRoot.childCount > 0)
    }

    fun testShippingParserUsesTheDialectMarkerAndSelectedSource() {
        val sql = "-- dialect: duckdb\nSELECT list_value(1, 2) AS values;\nSELECT 99;"
        myFixture.configureByText("tree.bsql", sql)
        val start = sql.indexOf("SELECT")
        myFixture.editor.selectionModel.setSelection(start, sql.indexOf(';') + 1)
        val panel = AstViewerPanel(project)
        Disposer.register(testRootDisposable, panel)
        panel.refresh()
        await { !panel.isBusy }
        assertEquals("statements (duckdb)", panel.displayedRoot.toString())
        assertEquals(1, panel.displayedRoot.childCount)
        val nodes = panel.displayedRoot.depthFirstEnumeration().asSequence().map { it.toString() }.toList()
        assertTrue(nodes.toString(), nodes.any { "Array" in it })
        assertFalse(nodes.toString(), nodes.any { "Parse error" in it || "this = 99" in it })
    }

    fun testEditAndRevertDiscardsPendingAst() {
        myFixture.configureByText("tree.bsql", "SELECT 1")
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val panel = AstViewerPanel(project) { text, _ ->
            started.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            Dialects.BASE.parse(text).filterNotNull()
        }
        Disposer.register(testRootDisposable, panel)
        val original = panel.displayedRoot
        try {
            panel.refresh()
            await { started.count == 0L }
            WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText("SELECT 2") }
            WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText("SELECT 1") }
        } finally { release.countDown() }
        await { !panel.isBusy }
        assertSame(original, panel.displayedRoot)
    }

    fun testNewerRefreshWinsOverCanceledOlderParse() {
        myFixture.configureByText("tree.bsql", "SELECT 1")
        val first = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val panel = AstViewerPanel(project) { text, _ ->
            if (calls.incrementAndGet() == 1) {
                first.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
            Dialects.BASE.parse(text).filterNotNull()
        }
        Disposer.register(testRootDisposable, panel)
        try {
            panel.refresh()
            await { first.count == 0L }
            WriteCommandAction.runWriteCommandAction(project) { myFixture.editor.document.setText("SELECT 2") }
            panel.refresh()
        } finally { release.countDown() }
        await { !panel.isBusy }
        val nodes = panel.displayedRoot.depthFirstEnumeration().asSequence().map { it.toString() }.toList()
        assertTrue(nodes.toString(), "this = 2" in nodes)
        assertFalse("this = 1" in nodes)
    }

    fun testDisposedContentDoesNotReceivePendingResult() {
        myFixture.configureByText("tree.bsql", "SELECT 1")
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val panel = AstViewerPanel(project) { text, _ ->
            started.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            Dialects.BASE.parse(text).filterNotNull()
        }
        val original = panel.displayedRoot
        try {
            panel.refresh()
            await { started.count == 0L }
            Disposer.dispose(panel)
        } finally { release.countDown() }
        await { !panel.isBusy }
        assertSame(original, panel.displayedRoot)
    }

    private fun await(condition: () -> Boolean) {
        PlatformTestUtil.waitWithEventsDispatching("AST background work must complete", condition, 15)
    }
}

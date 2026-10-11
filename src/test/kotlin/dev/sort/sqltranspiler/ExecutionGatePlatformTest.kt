package dev.sort.sqltranspiler

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.event.ActionEvent
import javax.swing.Action

class ExecutionGatePlatformTest : BasePlatformTestCase() {
    fun testAuthoritativeRejectionBlocksEvenForcedDialogInvocation() {
        checkDialog(accepted = false, advisory = false, expectedExecution = false)
    }

    fun testAdvisoryRejectionCanBeReviewedAndExecuted() {
        checkDialog(accepted = false, advisory = true, expectedExecution = true)
    }

    fun testAuthoritativeAcceptanceCanExecute() {
        checkDialog(accepted = true, advisory = false, expectedExecution = true)
    }

    fun testUnmappableFunctionStillBlocksDespiteParserAcceptance() {
        checkDialog(accepted = true, advisory = false, expectedExecution = false,
            sql = "SELECT * FROM read_parquet('f.parquet')", source = "duckdb", target = "doris")
    }

    private fun checkDialog(accepted: Boolean, advisory: Boolean, expectedExecution: Boolean,
                            sql: String = "SELECT 1", source: String = "mysql", target: String = "duckdb") {
        myFixture.configureByText("query.sql", sql)
        val scope = TranspileFlow.resolveScope(myFixture.editor)
        val result = BrikkTranspiler.transpile(sql, source, target) as BrikkTranspiler.TranspileOutcome.Success
        val report = VerifierService.Report(target, listOf(
            VerifierService.StatementVerdict(0, accepted, error = if (accepted) null else "Rejected", advisory = advisory),
        ))
        val cache = TranspileApprovalCache()
        val key = cache.key(sql, source, target)
        cache.approve(key, result.sql)
        assertEquals(expectedExecution, cache.isApproved(key, result.sql) && FindingPolicy.canExecute(result, report))
        var executed = false
        val dialog = TranspilePreviewDialog(project, myFixture.editor, scope, result, report) { executed = true }
        try {
            val action = TranspilePreviewDialog::class.java.getDeclaredField("executeAction").let {
                it.isAccessible = true
                it.get(dialog) as Action
            }
            assertEquals(expectedExecution, action.isEnabled)
            // An invocation must enforce the rule even if enablement has been bypassed.
            action.isEnabled = true
            action.actionPerformed(ActionEvent(this, ActionEvent.ACTION_PERFORMED, "Execute"))
            assertEquals(expectedExecution, executed)
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
    }
}

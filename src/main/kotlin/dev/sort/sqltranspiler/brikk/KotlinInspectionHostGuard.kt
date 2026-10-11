package dev.sort.sqltranspiler.brikk

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import dev.brikk.house.intellij.BrikkKotlinContext
import dev.brikk.house.intellij.ContextResult
import dev.sort.sqltranspiler.InspectionHostGuard
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/** Loaded only with Kotlin. No peer keys, injection registrations or shape enumeration. */
class KotlinInspectionHostGuard : InspectionHostGuard {
    override fun isInspectionOnly(file: PsiFile, editor: Editor): Boolean {
        val injectedHost = InjectedLanguageManager.getInstance(file.project).getInjectionHost(file)
        val host = injectedHost as? KtStringTemplateExpression ?: run {
            if (file !is KtFile || file.textLength == 0) return false
            val offset = if (editor.selectionModel.hasSelection()) editor.selectionModel.selectionStart else editor.caretModel.offset
            PsiTreeUtil.getParentOfType(file.findElementAt(offset.coerceIn(0, file.textLength - 1)), KtStringTemplateExpression::class.java)
        } ?: return false
        return when (val result = BrikkKotlinContext.inspect(host, includeShapes = false)) {
            ContextResult.NotBrikk -> false
            is ContextResult.Available -> !result.context.executable
            is ContextResult.Unavailable -> true
        }
    }
}

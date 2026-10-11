package dev.sort.sqltranspiler

import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import dev.brikk.house.sql.intellij.recoverEditorOperation

/** Optional adapters identify inspection-only hosts; the base plugin has no Kotlin linkage. */
interface InspectionHostGuard {
    fun isInspectionOnly(file: PsiFile, editor: Editor): Boolean
}

internal object SqlActionEligibility {
    private val guards = ExtensionPointName.create<InspectionHostGuard>(
        "dev.sort.sql-transpiler-intellij-plugin.inspectionHostGuard",
    )

    fun isAllowed(project: Project, editor: Editor, file: PsiFile? = null): Boolean {
        if (project.isDisposed || editor.isDisposed) return false
        val source = file ?: PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return true
        return recoverEditorOperation {
            source.isValid && guards.extensionList.none { it.isInspectionOnly(source, editor) }
        }.getOrDefault(false)
    }

    fun ensureAllowed(project: Project, editor: Editor, file: PsiFile? = null): Boolean {
        if (isAllowed(project, editor, file)) return true
        if (!project.isDisposed) TranspileFlow.notify(project,
            "Brikk SQL is an inspection view. Edit the authored Kotlin and run the compiled/composed relation with its bindings; inspection placeholders cannot be converted, replaced or sent to a console.",
            NotificationType.WARNING)
        return false
    }
}

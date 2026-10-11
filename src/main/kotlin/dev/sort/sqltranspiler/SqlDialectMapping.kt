package dev.sort.sqltranspiler

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.sql.dialects.SqlDialectMappings
import com.intellij.sql.dialects.SqlLanguageDialect
import com.intellij.util.FileContentUtil
import java.lang.reflect.InvocationTargetException

internal fun setDialectMapping(project: Project, file: VirtualFile?, dialect: SqlLanguageDialect?) {
    val workspaceSetter = try {
        Class.forName("com.intellij.sql.dialects.SqlDialectMappingUtilsKt").methods.singleOrNull {
            it.name == "setDialectMappingSync" && it.parameterCount == 3
        }
    } catch (_: ClassNotFoundException) {
        null
    }
    // 263's legacy setter changes storage, not the active workspace mapping or cached PSI.
    WriteAction.run<RuntimeException> {
        if (workspaceSetter == null) {
            SqlDialectMappings.getInstance(project).setMapping(file, dialect)
        } else {
            try {
                workspaceSetter.invoke(null, project, file, dialect)
            } catch (failure: InvocationTargetException) {
                throw failure.targetException
            }
        }
        FileContentUtil.reparseFiles(project, listOfNotNull(file), true)
    }
}

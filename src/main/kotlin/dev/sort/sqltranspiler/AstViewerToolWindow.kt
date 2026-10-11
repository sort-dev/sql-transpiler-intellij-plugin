package dev.sort.sqltranspiler

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import dev.brikk.house.sql.ast.Expression
import dev.brikk.house.sql.dialects.Dialects
import dev.brikk.house.sql.parser.ParseError
import dev.brikk.house.sql.intellij.recoverEditorOperation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * "Brikk SQL AST" tool window: parses the active editor's selection (or whole file)
 * under a chosen dialect and renders the brikk-sql expression tree. Node navigation
 * (click-to-source) lands once parser position tracking does.
 */
class AstViewerToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = AstViewerPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}

private const val AUTO_DIALECT = "auto (marker / file dialect)"

class AstViewerPanel(
    private val project: Project,
    private val parseSql: (String, String) -> List<Expression> = { text, dialect ->
        (Dialects.forNameOrNull(dialect) ?: Dialects.BASE).parse(text).filterNotNull()
    },
) : JPanel(BorderLayout()), Disposable {

    private val dialectCombo = ComboBox(arrayOf(AUTO_DIALECT) + BrikkDialects.names.toTypedArray())
    private val tree = Tree(DefaultTreeModel(DefaultMutableTreeNode("Parse the active editor to view its AST")))
    private val generation = AtomicInteger()
    private val pending = AtomicInteger()
    private val running = ConcurrentHashMap<Int, ProgressIndicator>()
    @Volatile private var disposed = false
    internal val isBusy: Boolean get() = pending.get() != 0
    internal val displayedRoot: DefaultMutableTreeNode get() = tree.model.root as DefaultMutableTreeNode

    init {
        val refreshAction = object : AnAction("Parse Active Editor", "Parses the selection or whole file of the active SQL editor", AllIcons.Actions.Refresh), DumbAware {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            override fun actionPerformed(e: AnActionEvent) = refresh()
        }
        val toolbar = ActionManager.getInstance()
            .createActionToolbar("BrikkSqlAstViewer", DefaultActionGroup(refreshAction), true)
        toolbar.targetComponent = this

        val header = JPanel(BorderLayout())
        header.add(toolbar.component, BorderLayout.WEST)
        header.add(dialectCombo, BorderLayout.CENTER)

        tree.isRootVisible = true
        add(header, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)
    }

    internal fun refresh() {
        if (disposed || project.isDisposed) return
        val request = generation.incrementAndGet()
        running.values.forEach { it.cancel() }
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor == null) {
            setRoot(DefaultMutableTreeNode("No active editor"))
            return
        }
        val scope = TranspileFlow.resolveScope(editor)
        val dialect = when (val picked = dialectCombo.selectedItem as? String) {
            null, AUTO_DIALECT -> {
                val psiFile = com.intellij.psi.PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
                TranspileFlow.detectSourceDialect(scope, psiFile) ?: ""
            }
            else -> picked
        }
        pending.incrementAndGet()
        object : Task.Backgroundable(project, "Parsing SQL AST", true) {
            private var result: DefaultMutableTreeNode? = null
            override fun run(indicator: ProgressIndicator) {
                running[request] = indicator
                try {
                    if (disposed || request != generation.get()) throw ProcessCanceledException()
                    result = buildRoot(scope.text, dialect)
                    ProgressManager.checkCanceled()
                } finally { running.remove(request) }
            }

            override fun onSuccess() {
                if (disposed || project.isDisposed || editor.isDisposed || request != generation.get()) return
                if (recoverEditorOperation { scope.snapshot?.requireCurrent() }.isFailure) return
                result?.let(::setRoot)
            }

            override fun onFinished() { pending.decrementAndGet() }
        }.queue()
    }

    private fun buildRoot(text: String, dialect: String): DefaultMutableTreeNode {
        val dialectLabel = if (dialect.isEmpty()) "base" else dialect
        val root = DefaultMutableTreeNode("statements ($dialectLabel)")
        recoverEditorOperation {
            val statements = parseSql(text, dialect)
            ProgressManager.checkCanceled()
            if (statements.isEmpty()) {
                root.add(DefaultMutableTreeNode("<no statements>"))
            }
            for (statement in statements) {
                root.add(expressionNode(null, statement))
            }
        }.onFailure { e ->
            val info = (e as? ParseError)?.errors?.firstOrNull()
            val at = info?.line?.let { " at line ${info.line}, col ${info.col}" } ?: ""
            root.removeAllChildren()
            root.add(DefaultMutableTreeNode("Parse error$at: ${info?.description ?: e.message}"))
        }
        return root
    }

    override fun dispose() {
        disposed = true
        generation.incrementAndGet()
        running.values.forEach { it.cancel() }
    }

    private fun setRoot(root: DefaultMutableTreeNode) {
        tree.model = DefaultTreeModel(root)
        for (i in 0 until tree.rowCount.coerceAtMost(50)) tree.expandRow(i)
    }

    /** Builds a tree node for an AST node: class name, with args as children. */
    private fun expressionNode(key: String?, expression: Expression): DefaultMutableTreeNode {
        ProgressManager.checkCanceled()
        val prefix = key?.let { "$it: " } ?: ""
        val node = DefaultMutableTreeNode("$prefix${expression::class.simpleName}")
        for ((argKey, value) in expression.args) {
            appendArg(node, argKey, value)
        }
        return node
    }

    private fun appendArg(parent: DefaultMutableTreeNode, key: String, value: Any?) {
        ProgressManager.checkCanceled()
        when (value) {
            null -> {}
            is Expression -> parent.add(expressionNode(key, value))
            is List<*> -> {
                if (value.isEmpty()) return
                val listNode = DefaultMutableTreeNode("$key [${value.size}]")
                value.forEachIndexed { i, item ->
                    when (item) {
                        is Expression -> listNode.add(expressionNode(i.toString(), item))
                        else -> listNode.add(DefaultMutableTreeNode("$i = $item"))
                    }
                }
                parent.add(listNode)
            }
            else -> parent.add(DefaultMutableTreeNode("$key = $value"))
        }
    }
}

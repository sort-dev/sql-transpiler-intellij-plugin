package dev.sort.sqltranspiler.lang

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.database.Dbms
import com.intellij.database.dataSource.LocalDataSource
import com.intellij.database.datagrid.DataRequest
import com.intellij.database.psi.DbPsiFacade
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.sql.database.SqlDataSource
import com.intellij.sql.database.SqlDataSourceManager
import com.intellij.sql.dialects.SqlDataSourceMappings
import com.intellij.sql.psi.SqlReferenceExpression
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.brikk.house.sql.intellij.CooperativeExecuteAction
import dev.brikk.house.sql.intellij.testing.RecordingConsole

/** Spike: per-block language injection, per-block data sources and per-block execution in `.bsql`. */
class BrikkSqlBlocksPlatformTest : BasePlatformTestCase() {

    private val hostFile: PsiFile get() = InjectedLanguageManager.getInstance(project).getTopLevelFile(myFixture.file)

    private fun blocks(file: PsiFile = hostFile): List<BrikkSqlBlock> =
        PsiTreeUtil.findChildrenOfType(file, BrikkSqlBlock::class.java).toList()

    private fun injected(block: BrikkSqlBlock): List<PsiFile> =
        InjectedLanguageManager.getInstance(project).getInjectedPsiFiles(block).orEmpty().map { it.first.containingFile }

    fun testEachBlockGetsItsDeclaredLanguage() {
        myFixture.configureByText(
            "blocks.bsql",
            """
            select 0;

            -- language=mysql
            select `a` from t;

            -- language=PostgreSQL using="Somewhere"
            select a::text from t;

            -- language=nosuch
            select 3;

            -- language=clickhouse
            select toStartOfMonth(now());

            -- dialect: trino
            select 4;
            """.trimIndent(),
        )
        val blocks = blocks()
        assertEquals(6, blocks.size)
        val languages = blocks.map { block -> injected(block).single().language.id }
        assertEquals(listOf("GenericSQL", "MySQL", "PostgreSQL", "GenericSQL", languages[4], "GenericSQL"), languages)
        assertTrue("ClickHouse should be the platform dialect: ${languages[4]}", languages[4].contains("ClickHouse", ignoreCase = true))
        assertEquals("select `a` from t;\n\n", blocks[1].text)
        val markers = PsiTreeUtil.findChildrenOfType(hostFile, PsiComment::class.java).map { it.text }
        assertEquals(5, markers.size)
        assertEquals("Somewhere", BrikkSqlBlocks.directiveOf(blocks[2])?.using)
    }

    fun testLanguageCommentCompletesLanguageIds() {
        myFixture.configureByText("complete-language.bsql", "-- language=postgre<caret>\nselect 1;")
        val names = myFixture.completeBasic()?.map { it.lookupString }
        LookupManager.getInstance(project).hideActiveLookup()
        assertTrue("IDE language completion missing in .bsql markers: $names", names == null || names.any { it.equals("postgresql", ignoreCase = true) })
        if (names == null) assertTrue(myFixture.editor.document.text.startsWith("-- language=PostgreSQL", ignoreCase = true))
    }

    fun testMarkerShapedLineInsideAStringStaysInTheBlock() {
        myFixture.configureByText(
            "strings.bsql",
            "-- language=mysql\nselect '\n-- language=postgresql\n' from t;\n-- language=postgresql\nselect 1;",
        )
        val blocks = blocks()
        assertEquals(2, blocks.size)
        assertEquals("select '\n-- language=postgresql\n' from t;\n", blocks[0].text)
        assertEquals("MySQL", injected(blocks[0]).single().language.id)
        assertEquals("PostgreSQL", injected(blocks[1]).single().language.id)
    }

    fun testEachBlockReportsItsOwnDialectsSyntaxErrors() {
        val text = "-- language=mysql\nselect `ok` from t;\n\n-- language=postgresql\nselect (1 from t;\n"
        myFixture.configureByText("errors.bsql", "<caret>$text")
        val parseErrors = blocks().map { block -> injected(block).single().let { PsiTreeUtil.findChildrenOfType(it, com.intellij.psi.PsiErrorElement::class.java).size } }
        assertEquals(listOf(0, 1), parseErrors.map { it.coerceAtMost(1) })
        val infos = myFixture.doHighlighting()
        assertTrue("Blocks are the file's own SQL: no injected-fragment background",
            infos.none { it.forcedTextAttributesKey?.externalName?.endsWith("INJECTED_LANGUAGE_FRAGMENT") == true })
        val errors = infos.filter { it.severity == HighlightSeverity.ERROR }
        val mysqlBlock = TextRange(text.indexOf("select `ok`"), text.indexOf("\n\n"))
        assertTrue("MySQL block must be clean: ${errors.map { it.description }}", errors.none { it.startOffset in mysqlBlock.startOffset until mysqlBlock.endOffset })
        assertTrue("PostgreSQL block must have a syntax error: ${errors.map { it.startOffset to it.description }}", errors.any { it.startOffset >= text.indexOf("select (1") })
    }

    fun testEditingInsideABlockKeepsTheInjection() {
        myFixture.configureByText("edit.bsql", "-- language=mysql\nselect 1<caret>;\n\n-- language=postgresql\nselect 2;")
        myFixture.type(" + 2")
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        val blocks = blocks()
        assertEquals("select 1 + 2;\n\n", blocks[0].text)
        assertEquals("MySQL", injected(blocks[0]).single().language.id)
        assertEquals("PostgreSQL", injected(blocks[1]).single().language.id)
    }

    fun testEachBlockResolvesAgainstItsOwnDataSource() = withDdlDataSources { alpha, beta ->
        val text = """
            -- language=mysql using="Alpha"
            select alpha_col from alpha_t;

            -- language=postgresql using="Beta"
            select beta_col from beta_t;

            -- language=postgresql using="Beta"
            select alpha_col from alpha_t;
        """.trimIndent()
        myFixture.configureByText("resolve.bsql", text)
        val blocks = blocks()
        val files = blocks.map { injected(it).single() }
        val mappings = SqlDataSourceMappings.getInstance(project)
        assertEquals(listOf(alpha.name), mappings.getDataSources(files[0]).map { it.name })
        assertEquals(listOf(beta.name), mappings.getDataSources(files[1]).map { it.name })

        fun resolves(file: PsiFile, name: String): Boolean {
            val reference = PsiTreeUtil.findChildrenOfType(file, SqlReferenceExpression::class.java).first { it.text == name }
            return reference.references.any { it.resolve() != null }
        }
        assertTrue(resolves(files[0], "alpha_t"))
        assertTrue(resolves(files[0], "alpha_col"))
        assertTrue(resolves(files[1], "beta_t"))
        assertTrue(resolves(files[1], "beta_col"))
        assertFalse("Beta must not see Alpha's table", resolves(files[2], "alpha_t"))
    }

    /** The constraint behind per-block execution: a console attached to the .bsql file overrides every block's data source. */
    fun testAnAttachedConsoleReplacesTheBlocksDataSources() = withDdlDataSources { _, beta ->
        myFixture.configureByText("attached.bsql", "-- language=mysql using=\"Alpha\"\nselect alpha_col from alpha_t;")
        val injectedFile = injected(blocks().single()).single()
        assertEquals(listOf("Alpha"), SqlDataSourceMappings.getInstance(project).getDataSources(injectedFile).map { it.name })
        RecordingConsole(project, hostFile.virtualFile, "postgresql").use { recording ->
            DbPsiFacade.getInstance(project).clearCaches()
            val names = SqlDataSourceMappings.getInstance(project).getDataSources(injectedFile).map { it.name }
            assertEquals(listOf(recording.console.dataSource.name), names)
        }
    }

    fun testCompletionOffersOnlyTheBlocksDataSourceTables() = withDdlDataSources { _, _ ->
        myFixture.configureByText(
            "complete.bsql",
            "-- language=mysql using=\"Alpha\"\nselect * from alpha_t;\n\n-- language=postgresql using=\"Beta\"\nselect * from <caret>",
        )
        val names = myFixture.completeBasic().orEmpty().map { it.lookupString }
        LookupManager.getInstance(project).hideActiveLookup()
        assertTrue("Beta's table missing: $names", "beta_t" in names)
        assertFalse("Alpha's table leaked into Beta's block: $names", "alpha_t" in names)
    }

    fun testExecuteRunsTheStatementAtTheCaretOnTheBlocksDataSource() = runOnRunner()

    fun testExecuteLowersPipeSyntaxForTheBlocksDialect() = runOnRunner(
        "-- language=postgresql using=\"Runner\"\nselect 2;\nFROM t |> WHERE a > 1<caret> |> SELECT a;\nselect 4;",
        check = { query ->
            assertFalse(query, "|>" in query)
            assertTrue(query, Regex("""(?is)SELECT\s+a\s+FROM\s+t\s+WHERE\s+a\s*>\s*1""").containsMatchIn(query))
        },
    )

    /**
     * The registered Ctrl+Enter action itself, not just the interceptor. (Its enablement
     * cannot be checked here: the stock update disables Execute in every light-test editor.)
     */
    fun testRegisteredExecuteActionIsRoutedToTheBlocksDataSource() = runOnRunner(run = {
        com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction("Console.Jdbc.Execute").actionPerformed(event())
    })

    /** The statement Execute would run is boxed as in a console, in blocks that have a connection. */
    fun testCurrentStatementIsBoxedLikeAConsoleStatement() = runOnRunner(
        "-- language=postgresql using=\"Runner\"\nselect 2;\nFROM t\n|> WHERE a > 1<caret>\n|> SELECT a;\nselect 4;\n\n-- language=mysql\nselect 5;\n",
        check = {},
        run = {
            val editor = com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil.getTopLevelEditor(myFixture.editor)
            val text = editor.document.text
            fun shown(): String? {
                com.intellij.openapi.application.impl.NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
                PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
                return editor.getUserData(BrikkSqlCurrentStatement.KEY)?.shown?.substring(text)
            }
            assertNotNull("The .bsql editor has no statement box installed (kind=${editor.editorKind})", editor.getUserData(BrikkSqlCurrentStatement.KEY))
            assertEquals("FROM t\n|> WHERE a > 1\n|> SELECT a;", shown())
            editor.caretModel.moveToOffset(text.indexOf("select 4") + 3)
            assertEquals("select 4;", shown())
            editor.caretModel.moveToOffset(text.indexOf("select 5") + 3)
            assertNull("A block without a connection cannot run, so nothing is boxed", shown())
            editor.caretModel.moveToOffset(text.indexOf("select 2") + 3)
            editor.selectionModel.setSelection(text.indexOf("select 2"), text.indexOf("select 2") + 6)
            assertNull("A selection runs as selected", shown())
            editor.selectionModel.removeSelection()
            execute()
        },
        expectedRequests = 1,
    )

    /**
     * The console's error comes back to the statement that ran it: an error on its text and in the
     * editor, until the statement is edited (or run again).
     */
    fun testAFailedRunMarksItsStatementInTheBsqlEditor() = runOnRunner(after = { recording ->
        val editor = com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil.getTopLevelEditor(myFixture.editor)
        val document = editor.document
        BrikkSqlRunErrors.failed(recording.requests.single(), "Query failed: Error opening Hive split")
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        val statement = document.text.indexOf("select 3 + 1;")
        fun runErrors() = myFixture.doHighlighting(HighlightSeverity.ERROR).filter { "Error opening Hive split" in it.description.orEmpty() }
        val error = runErrors().single()
        assertEquals("Runner: Query failed: Error opening Hive split", error.description)
        assertEquals(TextRange(statement, statement + "select 3 + 1;".length), TextRange(error.startOffset, error.endOffset))
        assertTrue("A failure elsewhere does not mark this file", BrikkSqlRunErrors.marks(document).size == 1)
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { document.insertString(statement + "select 3".length, "0") }
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(document)
        assertEmpty("Editing the statement retires its run error", runErrors())
    })

    fun testRunsAccumulateAsTerminatedStatementsInTheConsole() = runOnRunner(
        "-- language=postgresql using=\"Runner\"\nselect 1<caret>\n",
        check = {},
        run = { execute(); execute() },
        expectedRequests = 2,
        console = { assertEquals("select 1;\n\nselect 1;", it) },
    )

    private fun runOnRunner(
        text: String = "-- language=mysql using=\"Alpha\"\nselect 1;\n\n-- language=postgresql using=\"Runner\"\nselect 2;\nselect 3<caret> + 1;\nselect 4;",
        check: (String) -> Unit = { assertEquals("select 3 + 1", it.trim().removeSuffix(";")) },
        run: () -> Unit = { execute() },
        expectedRequests: Int = 1,
        console: (String) -> Unit = {},
        after: (RecordingConsole) -> Unit = {},
    ) = withDdlDataSources { _, _ ->
        val target = LocalDataSource().apply {
            name = "Runner"
            databaseDriver = checkNotNull(com.intellij.database.dataSource.DatabaseDriverManager.getInstance().getDriver("postgresql"))
            url = "jdbc:offline:runner"
            isAutoSynchronize = false
            isKeepAlive = false
        }
        com.intellij.database.dataSource.LocalDataSourceManager.getInstance(project).addDataSource(target)
        DbPsiFacade.getInstance(project).clearCaches()
        val consoleFile = myFixture.addFileToProject("runner-console.sql", "").virtualFile
        try {
            RecordingConsole(project, consoleFile, target).use { recording ->
                BrikkSqlBlockRunner.useConsoleFile(project, target, consoleFile)
                myFixture.configureByText("run.bsql", text)
                run()
                // The console file is never opened: results go to the bottom pane, the .bsql stays in front.
                assertFalse(FileEditorManager.getInstance(project).isFileOpen(consoleFile))
                assertEquals(expectedRequests, recording.requests.size)
                check((recording.requests.last() as DataRequest.QueryRequest).query)
                console(recording.console.document.text)
                after(recording)
            }
        } finally {
            com.intellij.database.dataSource.LocalDataSourceManager.getInstance(project).removeDataSource(target)
        }
    }

    fun testExecuteWithoutUsingIsRefusedRatherThanAttachingTheFile() {
        myFixture.configureByText("nousing.bsql", "-- language=mysql\nselect 1<caret>;")
        var delegated = false
        execute { delegated = true }
        assertFalse("The stock action would attach the whole .bsql to one console", delegated)
    }

    /** Execute's data context with the caret in a block: the injected editor and file, as for Ctrl+Enter. */
    private fun event(): AnActionEvent {
        val file = myFixture.file
        return AnActionEvent.createEvent(
            SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.EDITOR, myFixture.editor)
                .add(CommonDataKeys.PSI_FILE, file)
                .add(CommonDataKeys.VIRTUAL_FILE, file.virtualFile)
                .build(),
            Presentation(), "BrikkSqlBlocksPlatformTest", ActionUiKind.NONE, null,
        )
    }

    private fun execute(onStock: () -> Unit = { fail("A .bsql block must not reach the stock Execute") }) {
        val event = event()
        val stock = object : AnAction() {
            override fun actionPerformed(e: AnActionEvent) = onStock()
        }
        CooperativeExecuteAction(1, stock, BrikkSqlBlockExecuteInterceptor).actionPerformed(event)
    }

    private fun withDdlDataSources(test: (SqlDataSource, SqlDataSource) -> Unit) {
        val alphaSchema = myFixture.addFileToProject("alpha-schema.sql", "CREATE TABLE alpha_t (alpha_col INT);")
        val betaSchema = myFixture.addFileToProject("beta-schema.sql", "CREATE TABLE beta_t (beta_col INT);")
        val manager = SqlDataSourceManager.getInstance(project)
        val alpha = manager.createEmpty().apply { name = "Alpha"; definedDbms = Dbms.MYSQL; setFiles(arrayOf(alphaSchema.virtualFile)) }
        val beta = manager.createEmpty().apply { name = "Beta"; definedDbms = Dbms.POSTGRES; setFiles(arrayOf(betaSchema.virtualFile)) }
        manager.addDataSource(alpha)
        manager.addDataSource(beta)
        try {
            alpha.syncAndWait()
            beta.syncAndWait()
            DbPsiFacade.getInstance(project).clearCaches()
            test(alpha, beta)
        } finally {
            manager.removeDataSource(alpha)
            manager.removeDataSource(beta)
        }
    }

    private fun SqlDataSource.syncAndWait() {
        PlatformTestUtil.waitForFuture(ApplicationManager.getApplication().executeOnPooledThread {
            javaClass.methods.firstOrNull { it.name == "waitComputed" && it.parameterCount == 0 }?.invoke(this)
        })
    }
}

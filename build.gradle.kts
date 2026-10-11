import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "dev.sort.sqltranspiler"
version = "0.4.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Core and verification are independently versioned publications. Oracle stays test/offline-only.
val brikkSqlVersion = providers.gradleProperty("brikkSqlVersion").getOrElse("0.19.1")
val brikkVerifyVersion = providers.gradleProperty("brikkVerifyVersion").getOrElse("0.19.1")
val brikkKotlinSupportVersion = providers.gradleProperty("brikkKotlinSupportVersion").getOrElse("0.2.0")
val kotlinHostTests = providers.gradleProperty("test.kotlinHosts").orNull == "true"
val pluginIsolationTests = providers.gradleProperty("test.pluginIsolation").orNull == "true"
require(!pluginIsolationTests || !kotlinHostTests) { "Run native classloader isolation and Kotlin host fixtures in separate workers" }

dependencies {
    implementation("dev.brikk.house:brikk-sql-jvm:$brikkSqlVersion")
    implementation("dev.brikk.house:brikk-sql-metadata-jvm:$brikkSqlVersion")
    implementation("dev.brikk.house:brikk-sql-intellij-support:0.1.1") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    implementation("dev.brikk.house:brikk-engine-kotlin-intellij-support:$brikkKotlinSupportVersion") {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    // The plugin owns its parse-only adapter instead of the verifier library's binding fallback.
    implementation("org.duckdb:duckdb_jdbc:1.5.6.0")
    implementation(files("vendor/lib/doris-fe-sql-parser-4.1.4-gad35a140c7fd.jar"))
    implementation("org.antlr:antlr4-runtime:4.13.2")
    // Native-grammar verification. Authoritative JVM parsers for trino/doris/duckdb;
    // pure-JVM advisory grammar oracles for postgres/mysql/hive/clickhouse. DuckDB is
    // the only native dep and TrinoVerifier needs a Java 25 runtime (fine in-IDE on JBR
    // 25; guarded elsewhere). Lightweight since 0.4.0 — no embedded-postgres/chdb.
    implementation("dev.brikk.house:brikk-sql-verify:$brikkVerifyVersion") {
        // The advisory SQL parsers do not use Groovy's sharding-expression runtime.
        exclude(group = "org.apache.groovy")
    }
    // The brikk POMs carry kotlin-stdlib only in runtime scope, and this build sets
    // kotlin.stdlib.default.dependency=false — pin it explicitly for compilation,
    // in lockstep with what brikk-sql compiles against.
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.10")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        // Compile optional host recognition on IDEA 261; DataGrip needs no Kotlin plugin.
        intellijIdea("2026.1.3")
        bundledPlugin("com.intellij.database")
        bundledPlugin("org.jetbrains.kotlin")
        bundledPlugin("com.intellij.java")
        // The database plugin's intellij.json.backend module dependency lives in the
        // JSON plugin; without it com.intellij.database won't load in unit tests.
        bundledPlugin("com.intellij.modules.json")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Optional sandbox companion only. The product owns its native Doris parser.
        // An explicitly requested local artifact must never silently fall back to Marketplace.
        val dorisPluginZip = providers.gradleProperty("dorisPluginZip").map(::File).orNull
        val dorisPluginVersion = providers.gradleProperty("dorisPluginVersion").orNull
        require(dorisPluginZip == null || dorisPluginVersion == null) {
            "Choose dorisPluginZip or dorisPluginVersion, not both"
        }
        if (dorisPluginZip != null) {
            require(dorisPluginZip.isFile) { "Missing Doris plugin ZIP: $dorisPluginZip" }
            logger.lifecycle("Doris sandbox companion: ${dorisPluginZip.absolutePath}")
            localPlugin(dorisPluginZip)
        } else if (dorisPluginVersion != null) {
            logger.lifecycle("Doris sandbox companion: Marketplace $dorisPluginVersion")
            plugin("dev.sort.doris-intellij-plugin", dorisPluginVersion)
        }
    }
}

intellijPlatform {
    // No custom settings UI yet; skip the headless searchable-options IDE launch.
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
            untilBuild = "262.*"
        }
        changeNotes = """
            <b>0.4.1</b>
            <ul>
                <li>Update the shared SQL IntelliJ support library to released 0.1.1.</li>
            </ul>
            <b>0.4.0</b>
            <ul>
                <li>brikk-sql core, metadata and verification 0.19.1; SQL IntelliJ support
                    0.1.0 and Brikk Kotlin support 0.2.0 from Maven Central.</li>
                <li>Reject unsafe execution, propagate cancellation, and preserve dialect blocks
                    and source positions during conversion.</li>
                <li>Parse-only DuckDB verification and a bundled, source-pinned Doris parser.</li>
                <li>Nested PIPE lowering, lexical dialect markers, background AST parsing,
                    and guards for Brikk Kotlin inspection views.</li>
            </ul>
            <b>0.3.0</b>
            <ul>
                <li>Transpile To… / Transpile From… dialect pickers are alphabetical.</li>
            </ul>
            <b>0.2.0</b>
            <ul>
                <li>brikk-sql 0.6.0: expanded cross-dialect function verdicts (incl. new
                    Doris&nbsp;&#8596;&nbsp;ClickHouse coverage), hardened dialect guards, and the
                    ClickHouse <code>count()</code> fix.</li>
                <li>Verifier errors map back to the source more often: nearest-span fallback
                    (shown as "&#8776; source line N") when no exact emit-span covers the error.</li>
                <li>Syntax errors in .bsql scratches underline exactly the offending token
                    instead of the rest of the line.</li>
                <li>7 MB smaller: dropped a transitive Groovy runtime the SQL parsers never use.</li>
            </ul>
            <b>0.1.0</b>
            <ul>
                <li>Dialects: MySQL, Doris, Trino, Presto, DuckDB, PostgreSQL, ClickHouse,
                    Hive, Spark, DataFusion, BigQuery.</li>
                <li>Native verification tiers: authoritative parsers for Trino/Doris/DuckDB,
                    advisory grammar oracles for PostgreSQL/MySQL/Hive/ClickHouse.</li>
                <li>Brikk SQL scratches: quiet editor (schema-bound inspections and substrate
                    syntax errors suppressed), with brikk-sql's own dialect parsers supplying
                    the real syntax errors per block.</li>
                <li>File | New | Brikk SQL Scratch, and "Brikk SQL" in the Scratch File language list.</li>
            </ul>
        """.trimIndent()
    }
    pluginVerification {
        // Structural/binary findings fail the release. API-use notices remain visible
        // in uploaded reports and must be reviewed; no ignored-problems file is used.
        failureLevel.set(listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.COMPATIBILITY_WARNINGS,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
            VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
            VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
            VerifyPluginTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
        ))
        ides {
            create("DB", "2026.1.3") {}
            create("IU", "2026.1.3") {}
            create("IU", "262.8665.81") {}
            create("DB", "2026.2.5") {}
            // Exact forward-check targets from Marketplace. These do not widen untilBuild.
            create("IU", "263.4732.28") {}
            create("IU", "263.5701.42") {}
        }
    }
    publishing {
        token = providers.gradleProperty("intellijPlatformPublishingToken")
    }
}

tasks {
    processTestResources {
        from("src/main/resources/META-INF/transpiler-kotlin.xml") { into("META-INF") }
    }
    withType<VerifyPluginTask> {
        // Peer agents can verify other plugins concurrently. Never share extraction cleanup.
        systemProperty("plugin.verifier.home.dir", layout.buildDirectory.dir("plugin-verifier-cache").get().asFile.absolutePath)
    }
    processResources {
        from(files("LICENSE", "THIRD_PARTY_NOTICES.md")) { into("META-INF") }
    }
    // Stable artifact name (no version suffix) so install-from-disk always points at the same file.
    buildPlugin {
        archiveVersion = ""
    }

    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    named<Test>("test") {
        useJUnit()
        systemProperty("java.awt.headless", "true")
        // The light test fixture doesn't enable the database plugin by default.
        systemProperty("idea.load.plugins.id", buildList {
            add("com.intellij.database")
            add("dev.sort.sql-transpiler-intellij-plugin")
            if (kotlinHostTests) { add("com.intellij.java"); add("org.jetbrains.kotlin") }
        }.joinToString(","))
        if (!kotlinHostTests) exclude("**/brikk/**")
        if (!pluginIsolationTests) exclude("**/PluginIsolationPlatformTest.class")
    }
}

if (pluginIsolationTests) {
    val mainOutputs = sourceSets.main.get().output.files.map { it.absoluteFile }.toSet() +
        layout.buildDirectory.dir("instrumented/instrumentCode").get().asFile.absoluteFile
    val testResources = sourceSets.test.get().output.resourcesDir?.absoluteFile
    tasks.named<Test>("test") {
        include("**/PluginIsolationPlatformTest.class")
        classpath = classpath.filter {
            it.absoluteFile !in mainOutputs && it.absoluteFile != testResources &&
                !it.name.startsWith("sql-transpiler-intellij-plugin-") &&
                !it.name.startsWith("brikk-sql-") &&
                !it.name.startsWith("brikk-engine-kotlin-intellij-support-") &&
                !it.name.startsWith("doris-fe-sql-parser-") &&
                !it.name.startsWith("duckdb_jdbc-") &&
                !it.name.startsWith("shardingsphere-") &&
                !it.name.startsWith("trino-parser-") && !it.name.startsWith("trino-grammar-")
        }
        jvmArgumentProviders.add(CommandLineArgumentProvider {
            listOf("-Didea.force.use.core.classloader=true", "-Didea.use.core.classloader.for.plugin.path=false")
        })
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        // Don't synthesize bridge overrides for interface default methods (JVM default
        // methods are used directly). Without this, every ToolWindowFactory default —
        // including deprecated/experimental ones — appears as an "override" in our
        // classes and the Plugin Verifier flags them.
        jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY)
    }
}

// Dogfooding sandbox with the PSI structure viewer, for inspecting how DataGrip's SQL
// PSI lines up with brikk-sql's AST (same convention as doris-intellij-plugin).
val runIdeWithPsiViewer by intellijPlatformTesting.runIde.registering {
    plugins {
        plugin("PsiViewer", "252.23892.248")
    }
}

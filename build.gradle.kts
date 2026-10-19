plugins {
    id("dev.sort.sql-dialect-plugin")
}

group = "dev.sort.sqltranspiler"
version = "0.5.4"

require(providers.gradleProperty("test.pluginIsolation").orNull != "true" || providers.gradleProperty("test.kotlin").orNull != "present") {
    "Run native classloader isolation and Kotlin host fixtures in separate workers"
}

sqlDialectPlugin {
    kotlinTests = false
    excludePlatformKotlin = false
    legalFiles = listOf("LICENSE", "THIRD_PARTY_NOTICES.md")
    additionalVerifierIdes = listOf("IU-2026.1.3")
    bytecodeCeilings = mapOf("trino-parser" to 69, "trino-grammar" to 69)
    embeddedLibraries = with(libs.versions) {
        setOf(
            "antlr4-runtime-${antlr.get()}.jar",
            "brikk-engine-kotlin-intellij-support-${brikk.engine.kotlin.intellij.support.get()}.jar",
            "brikk-sql-intellij-support-${brikk.sql.intellij.support.get()}.jar",
            "brikk-sql-jvmMain-${brikk.sql.core.get()}.jar",
            "brikk-sql-metadata-jvmMain-${brikk.sql.core.get()}.jar",
            "brikk-sql-verify-api-${brikk.sql.verify.get()}.jar",
            "brikk-sql-verify-trino-${brikk.sql.verify.get()}.jar",
            "brikk-sql-verify-duckdb-${brikk.sql.verify.get()}.jar",
            "brikk-sql-verify-doris-${brikk.sql.verify.get()}.jar",
            "brikk-sql-verify-shardingsphere-${brikk.sql.verify.get()}.jar",
            "caffeine-2.9.3.jar",
            "checker-qual-3.19.0.jar",
            "commons-lang3-3.15.0.jar",
            "doris-fe-sql-parser-4.1.4-gad35a140c7fd.jar",
            "duckdb_jdbc-${duckdb.jdbc.get()}.jar",
            "error_prone_annotations-2.41.0.jar",
            "failureaccess-1.0.3.jar",
            "guava-33.5.0-jre.jar",
            "j2objc-annotations-3.1.jar",
            "jackson-annotations-2.16.1.jar",
            "jackson-core-2.16.1.jar",
            "jackson-databind-2.16.1.jar",
            "jackson-datatype-jdk8-2.16.1.jar",
            "jackson-datatype-jsr310-2.16.1.jar",
            "jakarta.annotation-api-3.0.0.jar",
            "java-util-2.4.0.jar",
            "jcl-over-slf4j-1.7.36.jar",
            "jspecify-1.0.0.jar",
            "kotlinx-serialization-core-jvm-1.11.0.jar",
            "kotlinx-serialization-json-jvm-1.11.0.jar",
            "listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar",
            "shardingsphere-infra-database-clickhouse-5.5.2.jar",
            "shardingsphere-infra-database-core-5.5.2.jar",
            "shardingsphere-infra-database-hive-5.5.2.jar",
            "shardingsphere-infra-database-mysql-5.5.2.jar",
            "shardingsphere-infra-database-postgresql-5.5.2.jar",
            "shardingsphere-infra-exception-core-5.5.2.jar",
            "shardingsphere-infra-spi-5.5.2.jar",
            "shardingsphere-infra-util-5.5.2.jar",
            "shardingsphere-parser-sql-clickhouse-5.5.2.jar",
            "shardingsphere-parser-sql-engine-5.5.2.jar",
            "shardingsphere-parser-sql-hive-5.5.2.jar",
            "shardingsphere-parser-sql-mysql-5.5.2.jar",
            "shardingsphere-parser-sql-postgresql-5.5.2.jar",
            "shardingsphere-parser-sql-spi-5.5.2.jar",
            "shardingsphere-parser-sql-statement-clickhouse-5.5.2.jar",
            "shardingsphere-parser-sql-statement-core-5.5.2.jar",
            "shardingsphere-parser-sql-statement-doris-5.5.2.jar",
            "shardingsphere-parser-sql-statement-firebird-5.5.2.jar",
            "shardingsphere-parser-sql-statement-hive-5.5.2.jar",
            "shardingsphere-parser-sql-statement-mysql-5.5.2.jar",
            "shardingsphere-parser-sql-statement-opengauss-5.5.2.jar",
            "shardingsphere-parser-sql-statement-oracle-5.5.2.jar",
            "shardingsphere-parser-sql-statement-postgresql-5.5.2.jar",
            "shardingsphere-parser-sql-statement-presto-5.5.2.jar",
            "shardingsphere-parser-sql-statement-sql92-5.5.2.jar",
            "shardingsphere-parser-sql-statement-sqlserver-5.5.2.jar",
            "slf4j-api-1.7.36.jar",
            "snakeyaml-2.2.jar",
            "trino-grammar-${trino.get()}.jar",
            "trino-parser-${trino.get()}.jar",
        )
    }
}

dependencies {
    implementation(libs.brikk.sql.jvm)
    implementation(libs.brikk.sql.metadata.jvm)
    implementation(libs.brikk.sql.intellij.support) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    testImplementation(libs.brikk.sql.intellij.test.support) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    implementation(libs.brikk.engine.kotlin.intellij.support) {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }
    implementation(files("vendor/lib/doris-fe-sql-parser-4.1.4-gad35a140c7fd.jar"))
    implementation(libs.antlr4.runtime)
    implementation("dev.brikk.house:brikk-sql-verify-api:${libs.versions.brikk.sql.verify.get()}")
    implementation("dev.brikk.house:brikk-sql-verify-trino:${libs.versions.brikk.sql.verify.get()}")
    implementation("dev.brikk.house:brikk-sql-verify-duckdb:${libs.versions.brikk.sql.verify.get()}")
    implementation("dev.brikk.house:brikk-sql-verify-doris:${libs.versions.brikk.sql.verify.get()}")
    implementation("dev.brikk.house:brikk-sql-verify-shardingsphere:${libs.versions.brikk.sql.verify.get()}") {
        exclude(group = "org.apache.groovy")
    }
    // The brikk POMs carry kotlin-stdlib only in runtime scope, and this build sets
    // kotlin.stdlib.default.dependency=false — pin it explicitly for compilation,
    // in lockstep with what brikk-sql compiles against.
    implementation(libs.kotlin.stdlib)

    intellijPlatform {
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
    pluginConfiguration {
        changeNotes = """
            <b>0.5.4</b>
            <ul>
                <li>SQL IntelliJ support 0.2.3: no deprecated, scheduled-for-removal or experimental platform calls that have stable equivalents; no behavior changes.</li>
            </ul>
            <b>0.5.3</b>
            <ul>
                <li>Brikk SQL scratches: each block starts with <code>-- language=...</code> (optional
                    <code>dialect=</code> and <code>using="connection"</code>) and is real SQL of that
                    language, with its own highlighting, completion and errors, including those of the
                    Doris, Trino, DuckDB and ClickHouse plugins.</li>
                <li>Blocks resolve against their own <code>using=</code> connection and run there with
                    Ctrl+Enter (PIPE lowered first); the current statement is boxed and failures are
                    marked on the statement.</li>
                <li>Completion for the marker comment's attributes, dialects and connection names.</li>
                <li>Transpile edits write <code>-- language=</code> markers in .bsql files and keep one blank
                    line around the new block.</li>
                <li>SQL IntelliJ support 0.2.2 and a README that matches the plugin.</li>
            </ul>
            <b>0.5.2</b>
            <ul>
                <li>Update to SQL IntelliJ support 0.2.1 with stable public API annotations.</li>
                <li>Keep the optional JDBC introspector adapter out of the plugin distribution.</li>
                <li>Retain the existing SQL Transpiler context and inspection-host safeguards.</li>
            </ul>
            <b>0.5.1</b>
            <ul>
                <li>Rename the editor submenu to SQL Transpiler and hide inapplicable entries,
                    preserving SQL context, selection and inspection-host guards.</li>
                <li>Use released Brikk SQL core, metadata and verifiers 0.20.0,
                    SQL IntelliJ support 0.2.0 and Brikk Kotlin IntelliJ support 0.3.0.</li>
            </ul>
            <b>0.4.2</b>
            <ul>
                <li>Fresh local dogfood build with the validated formal Brikk dependencies.</li>
            </ul>
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
}

tasks.withType<Test>().configureEach {
    val productJacksonLibraries = configurations.runtimeClasspath.get().files
        .filter { it.name.startsWith("jackson-") }.map { it.name }.toSet()
    classpath = classpath.filter { it.name !in productJacksonLibraries }
}

kotlin {
    compilerOptions {
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

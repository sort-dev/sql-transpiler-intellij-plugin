plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "dev.sort.sqltranspiler"
version = "0.1.0"

repositories {
    mavenCentral()
    // brikk-house snapshots (for -PbrikkSqlVersion=x.y.z-SNAPSHOT tracking of head).
    maven("https://central.sonatype.com/repository/maven-snapshots/") {
        mavenContent { snapshotsOnly() }
        content { includeGroup("dev.brikk.house") }
    }
    intellijPlatform {
        defaultRepositories()
    }
}

// brikk-sql version; overridable with -PbrikkSqlVersion. On the 0.6.0 release (adds
// pipe-processing and source-map trace-back APIs; 0.5.x's hardened dialect guards +
// expanded cross-dialect function verdicts). brikk-sql-verify is the lightweight tier
// (pure-JVM ShardingSphere advisory oracles + trino-parser + duckdb_jdbc; no
// embedded-postgres/chdb). The heavy real-engine oracles live in brikk-sql-oracle,
// which we deliberately do NOT depend on (CI/offline only).
val brikkSqlVersion = providers.gradleProperty("brikkSqlVersion").getOrElse("0.6.0")

dependencies {
    implementation("dev.brikk.house:brikk-sql-jvm:$brikkSqlVersion")
    implementation("dev.brikk.house:brikk-sql-metadata-jvm:$brikkSqlVersion")
    // Native-grammar verification. Authoritative JVM parsers for trino/doris/duckdb;
    // pure-JVM advisory grammar oracles for postgres/mysql/hive/clickhouse. DuckDB is
    // the only native dep and TrinoVerifier needs a Java 25 runtime (fine in-IDE on JBR
    // 25; guarded elsewhere). Lightweight since 0.4.0 — no embedded-postgres/chdb.
    implementation("dev.brikk.house:brikk-sql-verify:$brikkSqlVersion") {
        // ShardingSphere drags in Groovy (7 MB) for its inline *sharding expression*
        // language — never touched by the SQL-parse path the advisory verifiers use
        // (verified: all verifier tests pass without it).
        exclude(group = "org.apache.groovy")
    }
    // The brikk POMs carry kotlin-stdlib only in runtime scope, and this build sets
    // kotlin.stdlib.default.dependency=false — pin it explicitly for compilation,
    // in lockstep with what brikk-sql compiles against.
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        // DataGrip 2026.1 (platform build 261), remote SDK — same target as the
        // sibling doris-intellij-plugin; 261/262 both run on JBR 25.
        datagrip("2026.1.3")
        bundledPlugin("com.intellij.database")
        // The database plugin's intellij.json.backend module dependency lives in the
        // JSON plugin; without it com.intellij.database won't load in unit tests.
        bundledPlugin("com.intellij.modules.json")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Dogfooding companion: the doris-intellij-plugin in the runIde sandbox, so
        // Doris data sources get their real dialect instead of platform fallbacks.
        // Default: Marketplace release (machine-independent). To dogfood an unreleased
        // local build instead, pass -PdorisPluginZip=/path/to/doris-intellij-plugin.zip.
        // Tests are unaffected either way — the test framework only loads what
        // idea.load.plugins.id lists.
        val dorisPluginZip = providers.gradleProperty("dorisPluginZip").map(::File).orNull
        if (dorisPluginZip != null && dorisPluginZip.exists()) {
            localPlugin(dorisPluginZip)
        } else {
            plugin(
                "dev.sort.doris-intellij-plugin",
                providers.gradleProperty("dorisPluginVersion").getOrElse("0.5.0"),
            )
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
            <ul>
                <li>brikk-sql 0.4.0: adds Hive, Spark, DataFusion, and BigQuery dialects.</li>
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
        // The supported range is 261..262 (sinceBuild/untilBuild above): verify both ends
        // on both marketed IDEs — DataGrip and IntelliJ IDEA Ultimate.
        ides {
            ide("DB", "2026.1.3")
            ide("IU", "2026.1.3")
            // 2026.2 (branch 262) — the EAP build the plugin compiles against, until GA.
            ide("IU", "262.8665.81")
        }
    }
}

tasks {
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
        // The light test fixture doesn't enable the database plugin by default.
        systemProperty("idea.load.plugins.id", "com.intellij.database,dev.sort.sql-transpiler-intellij-plugin")
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

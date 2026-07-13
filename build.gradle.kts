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

// brikk-sql version: Maven Central release by default; pass
// -PbrikkSqlVersion=0.2.0-SNAPSHOT to track head from the snapshots repo.
val brikkSqlVersion = providers.gradleProperty("brikkSqlVersion").getOrElse("0.1.0")

dependencies {
    implementation("dev.brikk.house:brikk-sql-jvm:$brikkSqlVersion")
    implementation("dev.brikk.house:brikk-sql-metadata-jvm:$brikkSqlVersion")
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
    }
    pluginVerification {
        ides {
            ide("DB", "2026.1.3")
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
    }
}

// Dogfooding sandbox with the PSI structure viewer, for inspecting how DataGrip's SQL
// PSI lines up with brikk-sql's AST (same convention as doris-intellij-plugin).
val runIdeWithPsiViewer by intellijPlatformTesting.runIde.registering {
    plugins {
        plugin("PsiViewer", "252.23892.248")
    }
}

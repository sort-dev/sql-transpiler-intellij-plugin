pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("build-logic/gradle/libs.versions.toml"))
            providers.gradleProperty("brikkSqlVersion").orNull?.let { version("brikk-sql-core", it) }
            providers.gradleProperty("brikkVerifyVersion").orNull?.let { version("brikk-sql-verify", it) }
            providers.gradleProperty("brikkSqlSupportVersion").orNull?.let { version("brikk-sql-intellij-support", it) }
            providers.gradleProperty("brikkKotlinSupportVersion").orNull?.let { version("brikk-engine-kotlin-intellij-support", it) }
        }
    }
}

rootProject.name = "sql-transpiler-intellij-plugin"

pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("build-logic/gradle/libs.versions.toml"))
            version("brikk-sql-core", providers.gradleProperty("brikkSqlVersion").getOrElse("0.23.0"))
            version("brikk-sql-verify", providers.gradleProperty("brikkVerifyVersion").getOrElse("0.23.0"))
            version("brikk-sql-intellij-support", providers.gradleProperty("brikkSqlSupportVersion").getOrElse("0.4.1"))
            version("duckdb-jdbc", "2.0.0.0")
            version("trino", "484")
            providers.gradleProperty("brikkKotlinSupportVersion").orNull?.let { version("brikk-engine-kotlin-intellij-support", it) }
        }
    }
}

rootProject.name = "sql-transpiler-intellij-plugin"

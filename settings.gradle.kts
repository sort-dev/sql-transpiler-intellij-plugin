pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("build-logic/gradle/libs.versions.toml"))
            version("brikk-sql-core", providers.gradleProperty("brikkSqlVersion").getOrElse("0.20.0-SNAPSHOT"))
            version("brikk-sql-verify", providers.gradleProperty("brikkVerifyVersion").getOrElse("0.20.0-SNAPSHOT"))
            version("brikk-engine-kotlin-intellij-support", providers.gradleProperty("brikkKotlinSupportVersion").getOrElse("0.2.0"))
        }
    }
}

rootProject.name = "sql-transpiler-intellij-plugin"

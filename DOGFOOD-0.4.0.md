# SQL Transpiler 0.4.0 local build

Validated 2026-10-10. This is a local installation build, not a Marketplace or
GitHub release. The plugin descriptor identifies version 0.4.0 and supports
IntelliJ IDEA Ultimate/DataGrip 2026.1 and 2026.2, build branches 261/262.

## Install

In Settings > Plugins > gear menu > Install Plugin from Disk, select:

```text
/home/jayson/DEV/sortdev/sql-transpiler-intellij-plugin/build/dogfood/sql-transpiler-intellij-plugin-0.4.0.zip
```

At validation, the standard Gradle output at `build/distributions/sql-transpiler-intellij-plugin.zip`
contained identical bytes. Later builds replace that standard output; the versioned
dogfood path above identifies this 0.4.0 build. ZIP size: 104,521,077 bytes.

SHA-256:

```text
7e62c15d7a80436aad97e0c09fae4d5bc9a701a0d6616c172f66f367e5a8b668
```

## Formal dependencies

| Library | Version |
| --- | --- |
| brikk-sql core | 0.19.1 |
| brikk-sql metadata | 0.19.1 |
| brikk-sql verify | 0.19.1 |
| brikk-sql-intellij-support | 0.1.0 |
| brikk-engine-kotlin-intellij-support | 0.2.0 |

All five packaged JARs were compared byte-for-byte with their Maven Central
downloads. Maven Local and the snapshot repository have been removed from the
build. The resolved runtime dependency graph has no snapshot versions, the ZIP
has no snapshot-named JARs, and no obsolete Brikk versions or oracle library are
bundled.

One upstream label remains inside the fixed vendored Doris grammar JAR:
`Implementation-Version: 1.2-SNAPSHOT`, also present in its `pom.properties`.
This is the standalone facade's internal version, not a changing Maven dependency.
The grammar is Doris 4.1.4, with pinned source revisions and checksum documented in
[vendor/README.md](vendor/README.md). The ZIP's parser matches that pinned checksum.

## Validation

- 102 default tests passed, zero failures/errors/skips.
- Seven optional Kotlin-host tests passed, including all three Brikk dialects.
- The actual PluginClassLoader test passed without Kotlin or companion plugins,
  requiring correctly owned libraries and functional native/advisory verifiers.
- `buildPlugin` and Plugin Verifier 1.410 passed. All six targets report Compatible:
  DB-261.24374.56, IU-261.25134.95, IU-262.8665.81, DB-262.10315.132,
  IU-263.4732.28 and IU-263.5701.42. The two 263 checks are forward binary checks;
  they do not widen the advertised installation range.

Commands:

```bash
./gradlew test buildPlugin dependencies --configuration runtimeClasspath --refresh-dependencies --console=plain
./gradlew test -Ptest.kotlinHosts=true --tests '*InspectionHostPlatformTest' --console=plain
./gradlew test -Ptest.pluginIsolation=true --console=plain
./gradlew buildPlugin verifyPlugin --console=plain
```

Logs are under `/tmp/opencode/sql-transpiler-040-*.log`; compatibility reports are
in `build/reports/pluginVerifier`. Tests use platform fixtures, a controlled
loopback endpoint and local parsers, not a live database execution session.

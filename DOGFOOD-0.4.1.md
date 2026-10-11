# SQL Transpiler 0.4.1 local build

Validated 2026-10-10. This build updates `brikk-sql-intellij-support` to the formal
0.1.1 release from Maven Central. Core, metadata and verify remain 0.19.1; Kotlin
IntelliJ support remains 0.2.0. There are no Maven snapshot dependencies.
The fixed vendored Doris parser retains its internal 1.2-SNAPSHOT label, with the
same pinned source revisions and checksum documented in [vendor/README.md](vendor/README.md).

## Install

Use Settings > Plugins > gear menu > Install Plugin from Disk:

```text
/home/jayson/DEV/sortdev/sql-transpiler-intellij-plugin/build/dogfood/sql-transpiler-intellij-plugin-0.4.1.zip
```

The descriptor identifies plugin 0.4.1 and IDE branches 261/262. At validation,
the standard `build/distributions` ZIP contained these same bytes; later builds
replace that standard output. The versioned path above identifies this build.
This is a local build, not a Marketplace or GitHub release.

ZIP size: 104,524,810 bytes. SHA-256:

```text
108c08bd6f72a8269a14406e3768b9babda642504993dac027ea3656decb0c36
```

The packaged support 0.1.1 JAR is byte-identical to the Central download. SHA-256:

```text
7ffdbb93990507db3f7ad83bc8e2571a7ef011f6debe0dd6b03d72d1b1ebd64e
```

## Checks

- 102 default tests pass, with no failures/errors/skips.
- Seven optional Kotlin-host tests pass.
- The actual PluginClassLoader test passes and asserts support 0.1.1 ownership.
- `buildPlugin` and the six-target Plugin Verifier matrix pass: DB-261.24374.56,
  IU-261.25134.95, IU-262.8665.81, DB-262.10315.132, IU-263.4732.28 and
  IU-263.5701.42 all report Compatible. The 263 checks are forward binary checks,
  not an expanded advertised installation range.
- Runtime dependency and ZIP audits find no snapshot-resolved or snapshot-named
  libraries, duplicate Brikk versions or oracle dependency.

Commands match [the 0.4.0 report](DOGFOOD-0.4.0.md#validation). Logs are in
`/tmp/opencode/sql-transpiler-041-*.log`.

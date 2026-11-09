# SQL Transpiler for DataGrip and IntelliJ IDEA

Convert SQL between dialects in DataGrip and IntelliJ IDEA Ultimate. SQL Transpiler uses
[brikk-sql](https://github.com/brikk/brikk-sql), a SQLGlot-derived Kotlin transpiler, to parse each
statement, generate target-dialect SQL, and report constructs that cannot be translated safely.

Supported source and target dialects are **MySQL, Doris, Trino, Presto, DuckDB, PostgreSQL,
ClickHouse, Hive, Spark, DataFusion, and BigQuery**. Convert a selection or file with a before/after
preview, execute a supported conversion through an attached console, or keep multiple dialects and
connections together in a Brikk SQL (`.bsql`) scratch.

Before **Execute via Transpilation** runs converted SQL, the plugin runs any available target-parser
verification and shows the generated SQL and diagnostics for review. Native parser checks are
available for Trino, Doris, and DuckDB; PostgreSQL, MySQL, Hive, and ClickHouse use advisory
ShardingSphere grammar checks. Blocking translation findings and native parser rejections prevent
execution. This build includes Trino 484, Doris 4.1.4, and DuckDB 2.0.0.0 verification components.

## What it does

- **Execute via Transpilation** — convert a block from its source dialect to the supported dialect
  of the attached console. Conversion and any available verification happen before execution. Review
  the generated SQL and its diagnostics before the first run; re-running an unchanged, approved
  conversion skips the dialog but still reconverts and rechecks it. A same-dialect execution scope
  containing no PIPE syntax runs unchanged, without this conversion and verification pipeline.
- **Transpile To… / Transpile From…** — convert the selection, dialect-marker block, or whole file
  between supported dialects, previewed as a before/after diff. Replace in place, insert after, copy,
  or send to an open console.
- **Brikk SQL scratches (`.bsql`)** — keep blocks in different SQL dialects in one file. Each block
  resolves against and runs on its own connection. See
  [the workflow](#the-workflow-a-bsql-scratch-is-the-multi-engine-workbench).
- **PIPE syntax (`|>`)** — desugar pipelines to standard SQL during transpilation. Supported-dialect
  PIPE blocks in `.bsql` scratches are also lowered before direct execution; lowering blockers stop
  the run.
- **Translation diagnostics and verification** — findings cover unmappable functions, unsupported
  translations, raw passthrough, semantic hazards, and capability gaps. Blocking findings and
  authoritative parser rejections prevent Execute via Transpilation; warnings and advisory grammar
  rejections remain reviewable. See [Verification tiers](#verification-tiers).
- **AST viewer** — inspect the brikk-sql expression tree in a tool window.

## The workflow: a `.bsql` scratch is the multi-engine workbench

Create a Brikk SQL scratch (**File → New → Brikk SQL Scratch**) and start each block with
the IDE's own language comment. `using=` names the connection the block resolves against
and runs on; `dialect=` names the brikk-sql dialect when the language does not imply it.

```sql
-- language=clickhousesql using="ClickHouse prod"
SELECT user_id, toStartOfMonth(event_time) AS month, count(*) AS events
FROM events
WHERE has(tags, 'active')
GROUP BY user_id, month;

-- language=duckdbsql using="Local DuckDB"
SELECT user_id, date_trunc('month', event_time) AS month, count(*) AS events
FROM events
WHERE list_contains(tags, 'active')
GROUP BY user_id, month;

-- language=genericsql dialect=datafusion using="SpiceDB localhost"
SELECT 1;
```

Each block is real SQL of its language: highlighting, completion and inspections come from
that language — from the Doris, Trino, DuckDB and ClickHouse dialect plugins when they are
installed, from the IDE's built-in dialects otherwise, and from Generic SQL for a language
that is not installed. `language=`, `dialect=` and `using=` all complete in the comment,
and connection names are quoted when they need it.

- **Resolution:** tables, columns and completion come from the block's `using=` connection.
  Keep the `.bsql` file itself unattached from consoles: an attached console replaces every
  block's connection.
- **Execute (Ctrl+Enter):** runs the statement at the caret, or the selection, on the block's
  connection, in that connection's own console. The statement that would run is boxed as in
  a console. Ordinary SQL runs as written; the block is not transpiled into another dialect.
  Supported-dialect PIPE queries are lowered to standard SQL before execution, and lowering
  blockers stop the run. This direct execution path does not use the Execute via Transpilation
  review or native-verifier gate; the destination engine parses the SQL it receives. Results open
  in the bottom pane, and a failed statement is marked in the `.bsql` editor with the engine's error.
- **Errors while editing:** each dialect's own errors, including the engine-parser errors of
  the dialect plugins. Blocks without a real IDE language (Generic SQL with `dialect=`) get
  brikk-sql's parser errors.
- **Transpile To…** a picked dialect (gutter icon or right-click → SQL Transpiler): review the
  diff, then replace in place, insert the converted copy below, copy it, or send it to an
  open console. The new block gets its own `-- language=` marker.

The older `-- dialect: xyz` marker still works and marks a Generic SQL block.

The transpile actions also work in ordinary `.sql` files and consoles: mark a foreign block
with `-- dialect: xyz` (not `-- language=`, which the IDE reads as a language hint for the
next string there) and transpile it in place. Until the block is converted, the file's own
dialect will red-flag the foreign syntax it cannot parse; for more than a one-off, use a
`.bsql` scratch.

## Verification tiers

For **Execute via Transpilation**, the review dialog reports verification of each generated
statement. These are parser or grammar checks, not schema binding or test execution against the
target database. They do not apply to a same-dialect execution scope containing no PIPE syntax or
to direct `.bsql` execution.

| Tier | Engines | Meaning |
|---|---|---|
| Authoritative | Trino, Doris, DuckDB | The engine's own parser. A rejection is a hard error and blocks Execute. |
| Advisory | PostgreSQL, MySQL, Hive, ClickHouse | A re-implemented ShardingSphere 5.5.2 grammar. Rejections are shown as non-blocking hints — they can be false positives. |
| None | Presto, Spark, DataFusion, BigQuery | No target-parser verifier; brikk-sql certification findings still apply. |

This build includes the Trino 484 parser, the Doris 4.1.4 grammar, and DuckDB JDBC 2.0.0.0.
Trino verification requires a Java 25 or newer runtime; on an older runtime, the review reports
the verifier as unavailable rather than treating the SQL as accepted.

DuckDB verification uses its parse-only SQL serialization API with external access
and extension auto-loading disabled. Statement types that API cannot check are
reported as unverified; verification never prepares/binds the supplied SQL.

Doris verification uses the bundled Apache Doris 4.1.4 grammar. It works without
the Doris plugin and ignores external parser-path properties and repository files.
The grammar version is pinned; acceptance does not guarantee support on older servers.

The core also reports unresearched cross-dialect semantic coverage. That refusal
blocks Execute even when a native parser accepts the generated syntax. The conversion
preview and its findings remain available for manual review.

Current detailed dialect function transpilation verification support:

| pair | verdicts | divergent | identical | cond-eq | no-equiv | unclear | 
| --- | --- | --- | --- | --- | --- | --- | 
| trino ↔ duckdb | 246 | 38 | 106 | 52 | 44 | 6 | 
| duckdb ↔ doris | 258 | 58 | 112 | 74 | 10 | 4 | 
| trino ↔ doris | 216 | 36 | 114 | 58 | 6 | 2 | 
| duckdb ↔ clickhouse | 213 | 52 | 125 | 36 | 0 | 0 | 
| trino ↔ clickhouse | 134 | 33 | 71 | 30 | 0 | 0 | 
| doris ↔ clickhouse | 177 | 29 | 142 | 6 | 0 | 0 | 

> *We actually ran both engines in each pair on real inputs and compared results*

Coming soon:

* Deepening to less common functions for combinations previously determined
* Additional dialect function comparisons

## Requirements

- IntelliJ IDEA Ultimate or DataGrip **2026.1 through 2026.3** (IDE builds 261 through 263.*),
  with the bundled Database Tools plugin.

## Installation

**From the JetBrains Marketplace** (recommended): search for *"SQL Transpiler"* in
**Settings → Plugins → Marketplace**, install, and restart.

**From disk** (specific version / offline): download `sql-transpiler-intellij-plugin-<version>.zip`
from the [Releases](https://github.com/sort-dev/sql-transpiler-intellij-plugin/releases) page (or
build it below), then **Settings → Plugins → ⚙ → Install Plugin from Disk…**, pick the zip, and
restart.

## Building

```bash
./gradlew build          # compile + tests
./gradlew runIde         # sandbox IDE with the plugin installed
./gradlew buildPlugin    # distributable zip in build/distributions
```

The shared dependency catalog is in `build-logic/gradle/libs.versions.toml`.
`settings.gradle.kts` pins this plugin's SQL family and IntelliJ support versions,
while retaining explicit overrides such as `-PbrikkSqlVersion=x.y.z`.

The `0.7.0` base uses SQL core/metadata/all verifier modules `1.0.1`, SQL IntelliJ
support/JDBC/test-support `1.0.1`, DuckDB JDBC `2.0.0.0`, and Brikk Kotlin IntelliJ
support `0.3.0`.
Test-support is a test-only dependency. Formal dependencies resolve through the
configured public repositories; Maven Local is reserved for explicitly requested
Brikk snapshots. The fixed vendored Doris parser retains an upstream internal
`1.2-SNAPSHOT` label; its source pins and SHA-256 are recorded in `vendor/README.md`,
and it is not a changing Maven dependency.

SQL 1.0.1 and support/test-kit 1.0.1 are published on Maven Central. Normal builds
need no candidate repository override. SQL Transpiler does not bundle the optional
JDBC adapter; it is only a transitive test-kit dependency during tests.

Default builds use a commit-derived development version. For a formal ZIP, pass
`-Prelease=true` and validate it with the shared script. Start with a clean build
directory so the validator sees exactly one ZIP:

```bash
./gradlew clean buildPlugin -Prelease=true
python3 build-logic/tools/verify-release-zip.py build/distributions dev.sort.sql-transpiler-intellij-plugin 0.7.0
```

Release builds reject snapshot dependencies and development plugin versions.
To publish the same release version to JetBrains Marketplace, use
`./gradlew publishPlugin -Prelease=true` from the release commit with your
`intellijPlatformPublishingToken` configured. GitHub releases do not publish to Marketplace.
To upload a downloaded, validated GitHub release ZIP already in `build/distributions`
without rebuilding it, add `-x buildPlugin`. Distribution and release checks still run.

Commit release preparation to `main`, then create an authorized `release/<version>` branch from
that commit. The shared workflow rejects commits absent from main, builds on JDK 21, and runs API
verification on IntelliJ 262 and 263. Internal usages without exact owner-confirmed call-site approvals
block GitHub release; full reports remain available.
Compatible alone is not Marketplace approval. The owner handles Marketplace uploads. GitHub release
0.5.1 was withdrawn; this is separate from the owner's Marketplace publication.

Manual dispatch on `release/<version>` runs the same guarded workflow. Use
`gh workflow run release.yml --ref release/<version>` if `[skip ci]` skipped the push trigger.

In Kotlin K2, recognized Brikk SQL strings are inspection views. Transpiler actions
refuse to replace or export their placeholders as executable SQL. Ordinary SQL and
DataGrip without Kotlin retain their existing workflows.

Run optional host and real-loader checks separately:

```bash
./gradlew test -Ptest.kotlin=present --tests '*InspectionHostPlatformTest' --tests '*EditorPopupKotlinPlatformTest'
./gradlew test -Ptest.pluginIsolation=true
```

The default sandbox is standalone. For companion testing, explicitly pass either
`-PdorisPluginVersion=<version>` or `-PdorisPluginZip=/absolute/path/to/plugin.zip`.
A missing requested ZIP fails the build rather than selecting a different release.
The `.bsql` companion tests (`BrikkSqlCompanionBlocksPlatformTest`) run only with the dialect
plugins installed: pass `-Ptest.dorisPluginZip`, `-Ptest.trinoPluginZip`, `-Ptest.duckdbPluginZip`
and `-Ptest.clickhousePluginZip`; without them they skip.

Review findings and follow-up work are tracked in the [review register](REVIEW.md),
using stable IDs such as `B1`.

## Related plugins and tools

- [Doris SQL Dialect](https://plugins.jetbrains.com/plugin/32777-sql-dialect-for-apache-doris)
  for DataGrip and JetBrains IDEs.
- [ClickHouse SQL](https://github.com/sort-dev/clickhouse-intellij-plugin) dialect for JetBrains IDEs.
- [Trino SQL Dialect](https://plugins.jetbrains.com/plugin/33124-sql-dialect-for-trino)
  for DataGrip and JetBrains IDEs.
- [DuckDB SQL](https://plugins.jetbrains.com/plugin/33098-sql-dialect-for-duckdb-embedded--quack-)
  for embedded DuckDB and Quack in DataGrip and JetBrains IDEs.
- [DataFusion and Spice.ai SQL](https://github.com/sort-dev/datafusion-spice-intellij-plugin)
  dialect for JetBrains IDEs.
- [Trino Doris connector](https://github.com/brikk/trino-doris-connector) for querying Doris
  through Trino.
- [Trino native DuckLake catalog](https://github.com/brikk/ducklake-integrations/tree/main/jvm/trino-ducklake)
  with read and write support.
- [Trino Duckbridge](https://github.com/brikk/duckbridge) for remote access to DuckDB via Quack.
- [brikk-house](https://github.com/brikk/brikk-house), a data engineering platform (coming soon).

## License

Apache-2.0 — see [`LICENSE`](LICENSE) and [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
An independent plugin by Sortdev SRL, built on the open-source brikk-sql transpiler.

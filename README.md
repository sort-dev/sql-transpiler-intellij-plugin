# SQL Transpiler (IntelliJ / DataGrip plugin)

Cross-dialect SQL transpilation inside DataGrip and IntelliJ IDEA Ultimate, powered by
[brikk-sql](https://github.com/brikk/brikk-house) — a full Kotlin port of sqlglot.

Write SQL in the dialect you think in; run it on the engine you have.

Part of our SQL-tooling family alongside:

* [**Doris SQL Dialect** for DataGrip & Jetbrains IDE's](https://plugins.jetbrains.com/plugin/32777-sql-dialect-for-apache-doris)
* [**Trino SQL Dialect** for DataGrip & Jetbrains IDE's](https://plugins.jetbrains.com/plugin/33124-sql-dialect-for-trino)
* [**DuckDB SQL** (embedded+Quack) for DataGrip & Jetbrains IDE's](https://plugins.jetbrains.com/plugin/33098-sql-dialect-for-duckdb-embedded--quack-)
* [**Trino - Doris connector** allowing Trino to access your Doris data](https://github.com/brikk/trino-doris-connector)
* [**Trino native DuckLake Catalog** with full Read/Write support](https://github.com/brikk/ducklake-integrations/tree/main/jvm/trino-ducklake)
* [**Trino Duckbridge** - remote access to DuckDB via Quack](https://github.com/brikk/duckbridge)
* [**brikk-house** - Data engineering platform](https://github.com/brikk/brikk-house) (coming soon)

## What it does

- **Execute via transpilation** — write a block in one dialect, run it on whatever engine
  the console is attached to. The plugin transpiles, shows a review dialog with the
  generated SQL and its diagnostics, then executes. Re-running an unchanged, approved
  block skips the review.
- **Transpile To… / Transpile From…** — convert the selection, dialect-marker block, or
  whole file between dialects, previewed as a before/after diff. Replace in place, insert
  after, copy, or send to an open console.
- **Brikk SQL scratches (`.bsql`)** — a polyglot file where each block is real SQL of its
  own dialect, resolves against its own connection and runs there. The recommended
  workbench for multi-engine work — see [the workflow](#the-workflow-a-bsql-scratch-is-the-multi-engine-workbench).
- **Pipe syntax (`|>`)** — write pipe-syntax SQL; the plugin desugars it to standard SQL
  for engines that don't speak it.
- **Certified transpilation** — every statement carries brikk-sql findings: unmappable
  functions, unsupported translations, raw passthrough, semantic hazards, capability gaps.
  Refusals gate execution; warnings are reviewable.
- **Native verification** — generated SQL is checked by the target engine's own parser
  before anything runs (see the tiers below).
- **AST viewer** — a tool window over the brikk-sql expression tree.

## Dialects

MySQL, Doris, Trino, Presto, DuckDB, PostgreSQL, ClickHouse, Hive, Spark, DataFusion,
BigQuery.

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
  a console. PIPE (`|>`) statements are lowered to standard SQL first. Results open in the
  bottom pane; a failed statement is marked in the `.bsql` editor with the engine's error.
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

The review dialog reports what the *target engine itself* would say, per statement:

| Tier | Engines | Meaning |
|---|---|---|
| Authoritative | Trino, Doris, DuckDB | The engine's real parser. A rejection is a hard error and blocks Execute. |
| Advisory | PostgreSQL, MySQL, Hive, ClickHouse | A re-implemented grammar (ShardingSphere). Rejections are shown as non-blocking hints — they can be false positives. |
| None | Presto, Spark, DataFusion, BigQuery | No verifier; brikk-sql certification findings still apply. |

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

- IntelliJ IDEA Ultimate or DataGrip 2026.1+ with the Database Tools plugin (bundled).

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

The Brikk dependency defaults are in `build-logic/gradle/libs.versions.toml`.
`settings.gradle.kts` retains explicit overrides such as `-PbrikkSqlVersion=x.y.z`;
without an override, the formal catalog version applies.

The `0.5.4` base pins SQL core/metadata/all verifier modules to `0.20.0`, SQL IntelliJ
support/test-support to `0.2.3`, and Brikk Kotlin IntelliJ support to `0.3.0`.
Test-support is a test-only dependency. Formal dependencies resolve through the
configured public repositories; Maven Local is reserved for explicitly requested
Brikk snapshots. The fixed vendored Doris parser retains an upstream internal
`1.2-SNAPSHOT` label; its source pins and SHA-256 are recorded in `vendor/README.md`,
and it is not a changing Maven dependency.

Support/test-kit 0.2.3 are published on Maven Central. Normal builds need no candidate repository
override. SQL Transpiler does not bundle the optional JDBC adapter; it is only a transitive test-kit
dependency during tests.

Default builds use a commit-derived development version. For a formal ZIP, pass
`-Prelease=true` and validate it with the shared script. Start with a clean build
directory so the validator sees exactly one ZIP:

```bash
./gradlew clean buildPlugin -Prelease=true
python3 build-logic/tools/verify-release-zip.py build/distributions dev.sort.sql-transpiler-intellij-plugin 0.5.4
```

Release builds reject snapshot dependencies and development plugin versions.

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
./gradlew test -Ptest.kotlin=present --tests '*InspectionHostPlatformTest'
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

## License

Apache-2.0 — see [`LICENSE`](LICENSE). An independent plugin by Sortdev SRL, built on the
open-source brikk-sql transpiler.

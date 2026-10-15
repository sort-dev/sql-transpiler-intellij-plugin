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
- **Brikk SQL scratches (`.bsql`)** — a polyglot file type where mixed-dialect blocks live
  side by side, each marked `-- dialect: xyz`, with gutter actions per block. The
  recommended workbench for conversion work — see [the workflow](#the-workflow-a-bsql-scratch-is-the-conversion-workbench).
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

Blocks declare their dialect with a line comment:

```sql
-- dialect: clickhouse
SELECT toStartOfMonth(event_time) AS month FROM events;

-- dialect: duckdb
SELECT date_trunc('month', event_time) AS month FROM events;
```

## The workflow: a `.bsql` scratch is the conversion workbench

For ad-hoc dialect work — pasting queries in, comparing engines, converting back and
forth — create a Brikk SQL scratch (**File → New → Brikk SQL Scratch**). It's built for
exactly this: drop in blocks from anywhere, mark each with its dialect, and work on them
side by side without the IDE grumbling.

```sql
-- dialect: clickhouse
SELECT user_id, toStartOfMonth(event_time) AS month, count(*) AS events
FROM events
WHERE has(tags, 'active')
GROUP BY user_id, month;

-- dialect: duckdb
SELECT user_id, date_trunc('month', event_time) AS month, count(*) AS events
FROM events
WHERE list_contains(tags, 'active')
GROUP BY user_id, month;
```

From any block (gutter icon or right-click → Brikk SQL):

- **Transpile To…** a picked dialect — review the diff, then replace in place, insert the
  converted copy below, copy it, or send it straight to an open console of the target
  engine.
- **Execute via Transpilation** — run the block as-is against whatever engine the console
  is attached to; the plugin converts on the way.

In a `.bsql` scratch the editor stays quiet on purpose: the IDE's own SQL parser and
schema inspections have no authority over foreign dialects there, so the only errors you
see are real ones from brikk-sql's dialect parsers.

The same actions work in ordinary `.sql` files and consoles — mark a foreign block with
`-- dialect: xyz` and transpile it in place. Expect noise though: until the block is
converted, the file's own dialect (MySQL, PostgreSQL, …) will red-flag the foreign syntax
it can't parse. Fine for a quick one-off; for anything more than that, use a `.bsql`
scratch.

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

## Errors and warnings in `.bsql` files

A polyglot scratch targets other engines' schemas, so the IDE's own SQL machinery has no
authority there. The plugin layers it accordingly:

1. The base parser's syntax errors are fully suppressed (its opinion of ClickHouse or
   pipe syntax is worthless).
2. brikk-sql's dialect parsers supply the real syntax errors, per marker block, at their
   reported positions.
3. Schema-bound inspections (unresolved columns/functions, type/signature checks,
   "no data source" nags) are suppressed; brikk-sql certification and the native
   verifiers do that checking for real at transpile time.

Everything else — non-schema inspections, highlighting, completion — behaves normally.

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

The Brikk dependency versions are set in `settings.gradle.kts`, with overrides such
as `-PbrikkSqlVersion=x.y.z`.

The `0.5.0` development baseline uses SQL core/metadata/verification `0.20.0-SNAPSHOT`
and SQL IntelliJ support/test-support `0.2.0-SNAPSHOT` from frozen brikk-sql publish
`a62ad24447a0994a724712d7574aefafa6d48ede`, published on 2026-10-15 at 05:58 UTC.
Brikk Kotlin IntelliJ support remains `0.3.0-SNAPSHOT` from helper `507fe04`.
Brikk snapshots resolve exclusively from Maven Local; other dependencies use the
configured public repositories. The fixed vendored Doris parser retains an upstream
internal `1.2-SNAPSHOT` label; its source pins and SHA-256 are recorded in
`vendor/README.md`, and it is not a changing Maven dependency.

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

Review findings and follow-up work are tracked in the [review register](REVIEW.md),
using stable IDs such as `B1`.

## License

Apache-2.0 — see [`LICENSE`](LICENSE). An independent plugin by Sortdev SRL, built on the
open-source brikk-sql transpiler.

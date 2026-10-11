# SQL Transpiler modernization plan

Status: proposed, awaiting the user's go-ahead. Prepared 2026-10-10.
This is a plan and source/history investigation, not an implemented or verified fix.

Subsequent update: the user authorized local ST-B fixes while deferring work owned
by the shared extraction. See [REVIEW.md](REVIEW.md#implementation-log-2026-10-10)
for implementation commits, verification and remaining IDs. The text below records
the original broader proposal; H1-H6 remain reviewer-only and are not implemented here.

Use `P1` through `P5` for the work packages below, `H1` through `H6` for sharing
proposals, and `ST-B...` for the existing [review findings](REVIEW.md). IDs are
permanent. A request to do one work package includes its stated acceptance checks.
Shared-library publication and changes to other repositories require explicit scope.

## Scope and inspected state

SQL Transpiler remains the standalone cross-dialect conversion, review, execution,
AST and `.bsql` product. The dialect plugins own their embedded PIPE engines.
Provider migration, consumer capability handshakes and downstream provider release
gates are removed from the work here. Ordinary co-installation remains a test case.

| Repository | Inspected state | Meaning |
| --- | --- | --- |
| SQL Transpiler | `5aae5d6`, version 0.3.0; existing README/review documentation edits | Implementation remains the reviewed 0.6.0-engine baseline. |
| Doris | `178918b358d483aba750c90e23384a4baf2e9b6a`, version 1.5.6 | Working tree was clean when inspected, six commits ahead of origin. The final relation-column work had already been committed. |
| DuckDB | `60fb207cced74033becf9035f3c765e934bd2d60`, version 0.3.2 | Matches the requested endpoint. Uncommitted `REVIEW-shared-relations.md` adds final validation results; it was read and left intact. |
| Trino | Local `2ee135f`, version 0.2.0, clean | This checkout does not yet contain the in-progress migration described by the user. Treat migration as peer-owned, not a reason to preserve a provider architecture here. |
| Shared Kotlin helper | `brikk-house/brikk-engine-kotlin-intellij-support` | Source/API inspected; Central's 0.1.0 POM confirmed. Both current peer builds consume that release. |
| SQL libraries | Standalone `brikk/brikk-sql`, local `4db2589` | Development follows published 0.18.0. Central's core 0.18.0 POM confirmed. Do not resolve development snapshots for final delivery. |

No peer files, installed IDEs, distributions, source code or dependency versions were
changed. No builds, tests or live SQL were run for this planning pass. Peer test
counts and verifier results below are their recorded evidence, not new results here.

## History traced

The supplied hashes are stronger anchors than a relative date search. Embedding
preceded Doris 1.5.0, so both the earlier PIPE fixes and the later 1.5.x fixes matter.

| Ref | Change and lesson |
| --- | --- |
| Doris `28b56ea` | Original review baseline. |
| Doris `8f59f06`, `5c6c9f2` | Complete lexical statement boundaries and cooperative Execute delegation. |
| Doris `e1ed71e`, September 5 | Embeds core/metadata, removes Transpiler as a provider, adds project enablement, package inventory and real-classloader checks. |
| Doris `921cdc3`, `e0c6b02`, `00ec61d`, `95af652`, `8df92b6` | Fail closed on lossy/failed translation; isolate diagnostics; retain scope, parameters and initiating console; validate generated SQL; fix stage behavior. Generation success is not execution approval. |
| Doris `c510fba` | Read current editor text and reject stale prepared statements rather than execute cached SQL. |
| Doris `effe60f` | Older passing verifier targets missed real failures on newer 262 and 263. Compile on 261, exercise the same artifact on later runtimes. |
| Doris `d044f3e`, v1.5.0 | brikk-sql 0.16.0 plus source-pinned Doris 4.1.4 grammar. Core and native verifier upgrades have separate provenance and tests. |
| Doris `13838e3`, September 26 | Marketplace objected to internal action-customizer APIs. Use public `DynamicActionConfigurationCustomizer`; restore only an owned top wrapper, otherwise detach to pass-through. |
| Doris `eb9a3ef` | Adds Marketplace's actual `263.5701.42` target. A generic claim of 263 coverage is insufficient. |
| Doris `feb439f`, `af8e540`, `2d7992d` | Correct PSI determines execute versus executeQuery, token order, and parameter prompting. Test the actual platform preparation/submission path, not only SQL strings. These Doris parser fixes are not code to transplant into our generic plugin. |
| Doris `a04bca5` | Statement-boundary regressions continued after earlier fixes; preserve multi-statement execution tests. |
| Doris `fbfc50c` | Resolved Brikk Kotlin hosts, authored-reference preservation, scoped editing enablement, and explicit execution guards. |
| Doris `12040ef`, `178918b` | Replaces duplicated Kotlin semantics with the shared helper, then adds function-local relation columns and moves to Central 0.1.0. |
| DuckDB `d19eb12` to `b5c604f` | Built-in PIPE 0.3.0 with brikk-sql 0.18.0, cancellation-aware boundaries, checked lowering, cooperative stock execution, and the five-target verification matrix. |
| DuckDB `8a8f1f7`, `8da2c6a`, `60fb207` | Brikk Kotlin injection, shared semantic helper and relation scopes, then Central 0.1.0 and local plugin 0.3.2. |

Useful evidence and implementation references:

- [Doris execution contract](../doris-intellij-plugin/PIPE-EXECUTION-CONTRACT.md).
- [Doris newer-IDE failure/fix report](../doris-intellij-plugin/COMPAT-263.md).
- [Doris 1.5.0 engine/grammar verification](../doris-intellij-plugin/REVIEW-brikk-sql-0.16.0.md).
- [Doris Brikk integration and limits](../doris-intellij-plugin/BRIKK-HOUSE.md).
- [DuckDB PIPE verification](../duckdb-intellij-plugin/REVIEW-pipes-0.3.0.md).
- [DuckDB shared helper and final validation notes](../duckdb-intellij-plugin/REVIEW-shared-relations.md).

## Proposed work order

Complexity includes regression tests. Each package should be reviewed and verified
before moving on. Fix the local safety paths before a broad dependency upgrade.

| Order | ID | Work | Complexity | Findings |
| --- | --- | --- | --- | --- |
| 1 | P1 | Establish current verification and packaging baseline | Medium | B15, B16, B17, B20, B14 |
| 2 | P2 | Make execution eligibility, cancellation and snapshots consistent | Medium | B1, B2, B8, B23 |
| 3 | P3 | Make native verification explicit and safe | High | B3, B4, B16, B21 |
| 4 | P4 | Upgrade core and repair scopes, edits and diagnostics | High as a group | B5 through B7, B9 through B13 |
| 5 | P5 | Respect Brikk inspection hosts and extract proven shared mechanics | Medium to high | B22; H1 through H6 as separately scoped proposals |

### P1: Verification and delivery baseline

1. Reproduce the existing suite against the current source, keeping old 56-test
   results historical. Use isolated reports/build directories so verification
   cannot overwrite a user's selected installable ZIP.
2. Adopt the peers' IntelliJ Platform Gradle Plugin 2.19.0 workflow and Java 21
   product bytecode baseline. Check the actual wrapper/build-JDK compatibility.
   Kotlin 2.4.10 becomes the candidate when consuming the newer core/helper.
3. Keep 261 as the compilation baseline. Verify the same built artifact against:
   - DataGrip 2026.1.3, `DB-261.24374.56`;
   - IDEA `IU-262.8665.81`;
   - DataGrip 2026.2.5, `DB-262.10315.132`;
   - IDEA `IU-263.4732.28`;
   - IDEA `IU-263.5701.42`.
   Add newer Marketplace-reported builds if needed. Treat 263 as forward validation
   until runtime checks justify changing our current `untilBuild=262.*`.
4. Run focused later-runtime tests using 261-compiled/instrumented classes. Use
   headless fixtures and the peers' newer dialect-mapping setup only where needed.
   Product tests need real plugin classloaders in a separate lane; a flattened
   fixture can select another plugin's engine, Jackson or descriptor by accident.
5. Audit package contents, descriptor, bytecode, notices and exact dependencies.
   Do not copy the peers' core-only JAR whitelist: this plugin intentionally has
   verifier dependencies, including Trino parser classes requiring Java 25. Check
   product/core Java 21 and optional verifier runtime requirements separately;
   decide packaging against supported JBRs rather than declaring every JAR Java 21.
6. Keep compatibility failures and invalid packaging fatal, with internal,
   experimental, deprecated and removal notices visible and individually assessed.
   The public-customizer fix is guidance for future wrappers, not a reason to add
   wrappers here: our explicit actions are outside the peers' global Execute chain.
7. Gate our release on the chosen checks and record the artifact hash. Fix B14's
   mutable release asset behavior. The peers still have `needs: build` release
   jobs; copy their useful checks, not their non-gating release policy.

Acceptance: reproducible baseline/results and CI gates, no hidden compatibility
failures, real class ownership assertions, genuine companion-free operation and
optional co-installation checks. A missing 263-compatible Transpiler build is a
reported limitation, not permission to modify its descriptor inside a test.

### P2: Execution safety

1. Centralize our execution-eligibility decision. Use it at both button display and
   invocation, including the cached approval path. Preserve advisory versus
   authoritative semantics and report partially unverified output honestly.
2. Follow the peers' exception handling: propagate platform/coroutine cancellation
   and fatal JVM errors. Check cancellation between statements and expensive phases
   and before preview, approval updates, console writes or submission.
3. Distinguish ordinary same-dialect SQL from claimed PIPE input. Genuine failed or
   refused PIPE lowering must not take `ExecuteFlow`'s current raw-SQL fallback.
   Pipe-shaped text inside a literal/comment remains ordinary SQL.
4. Snapshot source, generated result, dialects and destination. Revalidate the
   document and destination before applying an edit or executing an approved result.
   A source map must correspond to the exact rendering reviewed/submitted.
5. Add recording-console tests for actual selection, mixed scripts, parameter
   prompting/substitution, canceled prompting, new-tab options and initiating
   console ownership. Preserve our explicit review workflow while using the
   platform's preparation/submission path; do not copy old direct request-bus code.

Acceptance: no request or document mutation after cancellation, rejection, stale
input or a claimed failure. A failure in a later selected statement must not permit
earlier statements to submit before preflight completes. Test fresh/cached approvals.

### P3: Safe native verification

The peer implementations are not a complete security template. The inspected
upstream `DuckdbVerifier` still prepares/binds unsupported non-SELECT statements,
and the peer DuckDB validator still uses EXPLAIN/binding. Neither establishes a
no-external-I/O parser boundary. Upstream Doris still searches the working-directory
ancestry. Do not close B3/B4 merely by upgrading the library.

1. DuckDB: prefer a parse-only API for the supported statements; return an explicit
   unavailable result for unsupported syntax. If a bind-based adapter is retained,
   external access and extension loading must be disabled before processing input,
   with controlled local file and local HTTP tests proving the promised boundary.
2. Doris: provide an explicit trusted parser construction path. Recommend owning a
   source-pinned parser artifact, or a published parser adapter when available, so
   verification need not borrow an installed plugin's private file layout. Until
   then, trusted companion discovery must terminate as unavailable when absent;
   never fall through to repository-oriented discovery.
3. Keep engine-specific availability, parser rejection, advisory rejection and
   infrastructure failure distinct. Manage driver/classloader/connection lifetimes
   and remove only property values owned by this service if globals remain.
4. Evaluate verifier artifacts separately from core. The current single
   `brikkSqlVersion` upgrades everything together; split the version decisions if
   needed to land a tested core upgrade without importing unreviewed verifier changes.
5. Propose verifier fixes in `brikk-sql-verify` where reusable. Do not move native
   verifier implementations into the Kotlin-host helper or implicitly bundle them
   in every dialect plugin.

Acceptance: controlled no-I/O regression tests, precise unavailable reporting,
trusted-path-only Doris loading, disposal/cancellation tests, and packaged-runtime
verification. Library publication would be a separately authorized step.

### P4: Core upgrade and document correctness

Candidate: published `brikk-sql-jvm:0.18.0` and its aligned metadata, confirmed on
Central. Doris currently uses 0.16.0; DuckDB uses 0.18.0. Neither fact alone verifies
our full cross-dialect conversion behavior.

1. Build regressions from the existing review before upgrading: trailing comments,
   nested pipes, statement offsets, marker-shaped literals, invalid boundaries,
   all supported dialect pairs' representative function conversions and refusals.
2. Use the newer core's whole-tree desugaring and typed `UnsupportedError` contract.
   Preserve certification findings and exact generated SQL/source-map identity.
   Avoid declaring a known-invalid or unlowered result executable.
3. Establish one source-range representation with UTF-16 half-open offsets,
   statement/trivia distinction and explicit incomplete-boundary status. Use
   source-dialect tokenization rather than transplanting a Doris- or DuckDB-only
   scanner. Track newlines incrementally. Keep dialect-specific comment/quote rules.
4. Resolve actual marker comments, preserve marker/newline/statement boundaries in
   Replace/Insert, and retain untouched text's source dialect. Check writable
   destination, unchanged source and undo behavior at the edit boundary.
5. Map diagnostic columns correctly through trimmed slices, multi-statement files,
   surrogate pairs and CRLF. Convert external code-point positions explicitly.
6. Move AST parsing/tree work off the UI thread with cancellation and stale-result
   checks, then publish through the owning project's lifecycle.

Important API distinction: `toSourcePreservingExecutable`, introduced in 0.17.0,
is a separate opt-in API. Its documented `sourceMap` is always null after patching.
It is not a drop-in replacement for our certified, mapped conversion. Retain the
mapped generator route first; evaluate source-preserving display later if useful.

Acceptance: exact-document and exact-span regressions, no remaining nested operators
in executable output, reviewable/refused lossy translations, and all P1 runtime and
package gates against the candidate. Verify Kotlin/serialization compatibility before
excluding their runtime JARs just because the peers do so for a narrower feature set.

### P5: Brikk-aware action boundaries

Brikk recognition already has a suitable shared API:
`dev.brikk.house:brikk-engine-kotlin-intellij-support:0.1.0`. Its
`BrikkContext.executable` is always false; interpolation replacements are explicitly
for inspection. It recognizes resolved calls, annotations, aliases, trims and
full/partial relation shapes without evaluating user code.

1. Add optional Kotlin-aware recognition for our actions, using
   `BrikkKotlinContext.inspect(..., includeShapes = false)` at the injection host.
   Keep DataGrip/Kotlin-absent loading supported. Do not read peer-private PSI keys
   or reflect into peer classes to determine Brikk ownership.
2. Guard every relevant entry point and apply boundary. Replace/Insert must not
   write rendered inspection text into authored Kotlin; Send/Execute must not treat
   slots or scalar placeholders as a composed runtime relation. Pending resolution
   must not authorize the operation. Initially explain the limitation; a separate
   explicit inspection-only view can be added with clear provenance.
3. Retain authored reference/rename ownership in Kotlin. Do not register another
   competing dialect injector or infer exact SQL types from Kotlin families.
4. Leave existing dialect injection and slot-column completion with their owners.
   A generic `.bsql` conversion plugin does not need to reproduce those features to
   coexist safely. Add optional-helper tests for resolved, unrelated, pending and
   withdrawn contexts, plus real-classloader/Kotlin-absent checks.

Acceptance: no placeholder execution/export or destructive host edits through this
plugin; ordinary SQL and `.bsql` behavior remains covered. Any generated-shape tests
must use the actual reviewed compiler plugin and matching IDEA compiler. Never count
handwritten output traits as proof of generated-row support.

## Shared-library proposals

Prefer `brikk-sql-intellij-support` for generic IDE helpers, alongside the existing
`brikk-engine-kotlin-intellij-support`. Both are embedded libraries, not Marketplace
plugins or global services. A name such as `brikk-sql-intellij-plugin-helpers` is
possible, but `-support` matches the current artifact and avoids implying a plugin.

The existing helper explicitly keeps general SQL helpers outside its scope.
Leave Kotlin analysis there. Do not force SQL-only consumers to link Kotlin IDE APIs.

| ID | Candidate and home | Recommendation | Complexity |
| --- | --- | --- | --- |
| H1 | Lexical statement/range and diagnostic-coordinate contract in core SQL | Highest-value domain sharing. Include exact trivia spans, incomplete boundaries, token-aware pipe detection and source-dialect rules. Reuse existing engine capabilities before adding a second parser. Parameter occurrences already have parser-proved upstream APIs; do not replace them with another colon regex. | Medium to high |
| H2 | Cancellation-aware background work, document snapshots and guarded apply in generic IntelliJ support | Useful to this plugin and the dialect actions. Keep policy explicit and dependency-inject project lifecycle/compute/apply functions; do not swallow cancellation or make a process-global scope. | Medium |
| H3 | Cooperative action registration, detach and promotion in generic IntelliJ support | Concrete duplication in Doris and DuckDB. Preserve captured predecessors, all four variants, public APIs and cycle guards. Our explicit actions need not use it. Two embedded copies still form a chain; this cannot promise clean hot unload of a wrapper retained by a peer. | Medium |
| H4 | Recording-console, runtime and real-classloader fixtures in a test-support artifact; ZIP/bytecode/notices checks in Gradle build logic | Strong early extraction candidate. Share verification mechanisms; keep each product's expected library inventory, supported runtimes and accepted API notices explicit. Never ship test infrastructure. | Medium |
| H5 | Query-local relation contracts/scopes in core SQL or a separate relation-support module | Evaluate later. DuckDB has a custom AST scope graph; Doris feeds native `SqlTableType` and the engine's stage shapes. Share copied contracts and semantic cases first. Avoid replacing both with an unproven universal resolver, conflating partial inventory with absence, or exact SQL types with Kotlin hints. | High |
| H6 | A checked-translation/verifier-result contract in core/verify, with separate engine adapters | Share distinctions among generated, refused, unavailable, advisory and authoritative results. Preserve each product's execution policy: cross-dialect best-effort preview is different from automatic same-dialect PIPE execution. Native engines remain opt-in dependencies. | Medium to high |

H1, H2 and H4 have the clearest benefit for this repository. H3 is useful peer work
but not a prerequisite for our explicit actions. H5 should wait for common semantic
tests and demonstrated need. The existing Kotlin helper already centralizes host,
interpolation and relation-shape discovery; do not duplicate that work.

No helper object should be exchanged between independently loaded plugin copies.
Share the Maven implementation and tests, not private class identities. Avoid
global services, project-wide slot registries or process-global dialect state.

## Verification checklist for the eventual implementation

- Unit/contract: all retained ST-B regressions, Unicode and source-map identity,
  partial/unavailable reports, failure/cancellation behavior.
- Platform: exact edit text, undo, readonly/stale documents, real action invocation,
  parameter prompts, selected-script atomic preflight and recording submissions.
- Runtime: 261-compiled artifact on current 262 and the exact 263 targets; no
  recompilation against the newer API to hide a shipped-artifact incompatibility.
- Isolation: actual distribution PluginClassLoaders, standalone and co-installed,
  Kotlin absent/present. Assert the engine/helper resource comes from our own JAR.
- Safety: local harmless file and loopback HTTP probes for verification only;
  native parser tests never submit database mutations.
- Packaging: exact intended dependencies, runtime/bytecode requirements, optional
  descriptors, notices, Central provenance and immutable candidate checksum.
- Reporting: distinguish pass, skip, unavailable compiler/IDE and unrun live-server
  tests. Binary compatibility and source tests do not establish semantic fidelity.

## Hold point

The next action is the user's selection or approval of a work package. This pass
ends with the plan. No implementation, library extraction, build, publication,
commit or push is authorized by recording it.

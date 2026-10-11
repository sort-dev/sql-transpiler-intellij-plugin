# Review register

## SQL IntelliJ support 0.1.1 update

The previously validated changes were pushed first at `ce73ede`, after merging
origin's documentation/tooling commits without rewriting the fix history.
The new Maven Central release `brikk-sql-intellij-support:0.1.1` is adopted in local
plugin 0.4.1. All 102 default tests, seven optional host tests, the real-classloader
test and six binary-verifier targets pass. The packaged helper matches Central.
See [DOGFOOD-0.4.1.md](DOGFOOD-0.4.1.md) for the versioned ZIP and checksums.

## Formal dependency transition and dogfood 0.4.0

The user requested formal SQL 0.19.1 and Kotlin helper 0.2.0. Core, metadata and
verification now resolve 0.19.1; SQL IntelliJ support stays 0.1.0. The local plugin
version is 0.4.0. Maven Local and snapshot repositories are removed. All 102 default
tests, seven optional host tests and the real-classloader lane pass; all six IDE
targets report Compatible. Five Brikk JARs match Central byte-for-byte.

No Maven snapshot dependencies remain. The fixed vendored Doris parser retains
its upstream internal 1.2-SNAPSHOT facade label, with unchanged pinned provenance.
See [DOGFOOD-0.4.0.md](DOGFOOD-0.4.0.md) for the audited ZIP, checksum and installation.
The snapshot history below is retained as evidence, not the current build setup.

## Shared API adoption follow-up

Snapshot validation now supersedes the release blockers recorded below. The user
requested SQL `0.19.0-SNAPSHOT` and confirmed use of the Kotlin helper's Maven Local
`0.2.0-SNAPSHOT`. SQL snapshot build `0.19.0-20261011.033612-6` passes 25 direct
core cases across five dialects, without plugin recovery. Its core JAR SHA-256 is
`2aa649146851023523c203753c938fa51f722b5683ea480ab1e8b84e83576bf2`.
The temporary lexical-sentinel workaround is removed; core now reports the error.

ST-B22 is implemented with the restored draft and the Trino-capable local helper.
All seven optional host tests pass, including real injection, resolved aliases,
pending resolution, annotation removal and forced apply/execute attempts. All 98
default Kotlin-absent tests and the real-classloader test pass. The helper hash is
`e6467167d33c11f37a291dc451d30f6f8d8df4d16e8ea2cde5b8c035bd5abb99`.
Maven Local resolves only that exact helper coordinate. The prior stash is retained
as a historical backup, not pending work. Source CI needs the helper publication in
its repository until the owner promotes a released artifact; no remote CI run is claimed.

ST-B9 is fixed using the governing dialect's core lexer to check marker candidates.
Literal/identifier contents and block comments cannot declare dialects. Untyped
preludes are conservative across supported lexical forms; mapped SQL supplies its
initial dialect. Scope, annotations, banners and cached gutter discovery share the
same recognized markers. All 102 default tests pass, including dollar/triple-quoted
strings, backtick identifiers, nested comments, CRLF and actual gutter/scope behavior.

Final snapshot candidate: 102 default tests, seven optional Kotlin host tests and
one actual PluginClassLoader test pass. Plugin Verifier 1.410 reports Compatible
on all six configured targets. The installable ZIP SHA-256 is
`20bff124e568ba72af2d7e36f6e85b68fedfd473d0f1e1cbe1695572d76bc924`.
ST-B22 is commit `9c5f421`; ST-B9 is `e1e4630`. No additional brikk-sql defects were
confirmed: ST-B24 was this plugin's SPI loader integration, and the Kotlin helper's
Trino support was a version-availability gap resolved by the approved local snapshot.

The user authorized continuing the deferred fixes once the shared libraries landed.
Each section is committed separately; earlier deferrals below record their original
reason, not a permanent exclusion.

Pre-snapshot validation: 97 default tests passed, plus the separate real-plugin-classloader
test. The final core/metadata/verify 0.19.0 candidate builds and reports Compatible
on all six configured IDE targets. SQL IntelliJ support is 0.1.0. Oracle 0.19.0 is
also confirmed on Central but is not a plugin dependency. Final ZIP SHA-256:
`580b2e327b9d3d2ddd3e4c391ea149ed8975b4d39d6ab773c3e1e3739ff3b42e`.

Historical release blockers, superseded by the approved snapshots above:

- The reported core unterminated-comment bug has upstream source fix `9f60987`,
  but Central still lists core 0.19.0. Keep the temporary local completeness guard
  until the fixed release lands; marker work ST-B9 is paused. No upstream source
  was changed by this session.
- Kotlin helper 0.1.0 has only DORIS/DUCKDB enum entries. The ST-B22 draft passed
  six host tests, with the three-dialect test failing specifically for Trino.
  Helper 0.2.0 with Trino recognition is not yet released on Central. The draft,
  optional descriptor and regression tests are preserved in stash
  `618606f09853a0ddaf7f009bf9ef0d3adaa2e460`, named
  `ST-B22 draft: awaiting released Kotlin helper with Trino recognition`.
  It is not part of the validated artifact. Apply that stash after the dependency
  is available, update the coordinate, and rerun all seven host tests plus native,
  real-classloader and binary verification lanes before committing completion.

- ST-B7 fixed by adopting Central core/metadata 0.19.0 with Kotlin 2.4.10. The
  shared IntelliJ support library 0.1.0 is available for subsequent sections.
  All 85 tests pass, including nested subquery/CTE lowering and rendering/map
  identity. The new core's semantic-coverage refusals remain blocking and have
  their own regression. Verification temporarily stays at 0.6.0 until its announced
  0.19.0 Central publication is reachable; no oracle dependency is added.
- ST-B6/B10/B13 fixed using core `SqlScriptSnapshot` ranges. Comment-only slices
  are not certified, trailing comments survive, slices retain original whitespace,
  first-line columns include same-line statement displacement, and line offsets
  are counted incrementally. Annotation tests assert exact token spans, CRLF and
  supplementary Unicode; a 2,000-statement case preserves positions. Preview edits
  also use core boundaries to reject partial routine edits. Thirteen focused range/
  edit tests pass. Core 0.19.0 misses unterminated trailing block comments, contained
  here by a lexer sentinel check in `SqlSourceSnapshot`; no shared source was changed.
- Shared `EditorSnapshot` and `recoverEditorOperation` now protect preview edits,
  execution and console handoff. An edit followed by a revert still invalidates the
  reviewed result. Source snapshots are checked after analysis and before apply;
  execution also rechecks its destination. Nineteen focused preview/gate/cancellation
  tests pass. The reported upstream unterminated-comment defect is awaiting a core
  release; range/marker work is paused rather than expanding the temporary guard.
- ST-B12 fixed: AST parsing and complete node construction run in a cancelable
  background Task. Refresh replaces older work, snapshot revisions prevent stale
  publication, and content disposal cancels pending work. Four platform tests exercise
  the actual asynchronous Task path, including edit/revert, competing refreshes and
  disposal. Cancellation uses shared recovery rather than becoming a parse error.
- Central now serves `brikk-sql-verify:0.19.0`; the verifier dependency is upgraded
  along with its DuckDB JDBC 1.5.6.0 and Trino 483 versions. All 97 default tests
  pass, including the plugin's parse-only no-file/no-network regressions. The owned
  Doris adapter and parse-only DuckDB adapter remain selected; no oracle dependency
  is added. Kotlin-host guard work is separate and awaiting helper 0.2.0 publication.
- ST-B24 found and fixed while checking actual PluginClassLoaders: advisory parser
  SPI discovery used the platform thread loader and returned unavailable. Creation
  and verification now temporarily use this plugin's loader and always restore the
  caller's loader, including cancellation/fatal paths. The real-classloader lane
  passes without Kotlin or companions, asserting owned core/helper/parser classes
  and working Doris, DuckDB, Trino and PostgreSQL verifiers. Twelve focused verifier/
  cancellation tests also pass. This was an IDE integration fix, not a shared-core edit.

## Implementation log, 2026-10-10

The user authorized in-place fixes and separate commits between sections. H1-H6
remain reviewer-only. Implementation results below supersede the proposed hold
and the original open-status table; historical evidence remains intact.

Work proceeds by ST-B number. Range/parser extraction work B6/B9/B10/B13, the
engine upgrade B7, shared background-work B12, and Kotlin helper integration B22
are deferred while the Trino agent establishes those contracts. B18/B19 are retired.
Product-specific B5/B11 edits, execution policy, verifier safety/lifecycle and local
release/build fixes proceed here. B15 is covered by each section's regression tests.

- Initial baseline: `./gradlew test --console=plain` succeeds with cached tasks.
  Fresh targeted checks will accompany each implementation section.
- ST-B1 fixed: one execution rule now gates the dialog, its invocation, cached
  approval and review callback. Native rejections count in the blocked header.
  Verified 18 tests with `./gradlew test --tests '*ExecutionGatePlatformTest'
  --tests '*BrikkTranspilerTest' --console=plain`, including forced invocation of
  a rejected dialog, advisory rejection and unmappable-function refusal.
- ST-B2 fixed locally: cancellation checks surround transpilation/verification and
  execution boundaries; cancellation and fatal errors are not converted to verifier
  unavailability. Verified 14 targeted tests covering pre-canceled work, cancellation
  during the final verifier of an approved query, and thrown platform/coroutine
  cancellation and fatal errors. This is cooperative cancellation, not interruption
  of an already-blocked native call. No shared helper was introduced.
- ST-B3 fixed: the plugin owns a parse-only DuckDB adapter with external access,
  extension auto-install and auto-load disabled. Unsupported statement types are
  unverified; there is no prepare/bind fallback. Ten targeted verifier tests pass,
  including existing/missing CSV paths, a controlled loopback HTTP endpoint, parser
  rejection and disposal. The CSV access time stayed unchanged and HTTP requests
  remained zero. The existing 1.5.4.0 JDBC version is now an explicit dependency.
- ST-B4 fixed with directly linked, bundled Doris 4.1.4 grammar. No companion lookup,
  global parser property or repository search remains. This also removes B16's
  internal API and B21's cross-plugin stale-path state. Service verification/creation
  and disposal are coordinated and disposed services cannot create resources.
  Fourteen targeted verifier/cancellation tests and buildPlugin pass; parser resource
  origin, pinned SHA-256 and bundled LICENSE/NOTICE were checked. Final Plugin
  Verifier checks will confirm B16 alongside the build/CI section.
- ST-B5 fixed with product-local planned marker edits: whole blocks change their
  governing declaration, partial statement-block edits bound the target text and
  restore the untouched suffix dialect, and next-marker/separator boundaries survive.
  Expression-only/cross-marker selections are refused for mutation. Original document
  snapshots and writable-document checks prevent stale/read-only writes. Eleven
  preview/execution platform tests pass, including exact output, undo and refusal.
  Marker lexical recognition itself remains deferred under ST-B9/H1.
- ST-B8 fixed: full acceptance requires every statement's check to run and pass.
  Unverified statements now have visible diagnostic rows; missing verifiers have
  an explicit unavailable note. Sixteen targeted report/dialog/verifier tests pass.
- ST-B11 fixed: a gutter click resolves its own marker even when its block is empty.
  All three gutter platform tests pass; the regression invokes the real navigation
  handler with adjacent markers and a pre-existing selection, then checks scope,
  caret, selection and unchanged SQL.
- ST-B14 fixed: release publication atomically creates a new tag at the built SHA
  and uses `--verify-tag`; existing version tags fail instead of replacing assets.
  Dry-run tests with a stub GitHub CLI verified new-tag publication and existing-tag
  rejection with no release/upload call. No remote release or tag was changed.
- ST-B17 fixed: CI now runs Plugin Verifier before uploading the release candidate;
  the release depends on that build job. Gradle platform plugin is 2.19.0 and the
  matrix adds current DataGrip 262 and both exact 263 EAP targets while retaining
  the 261 compilation baseline and advertised 261/262 range. All 79 tests pass.
  Plugin Verifier 1.410 reports Compatible on all six targets with no internal-API
  findings, confirming ST-B16. This is binary verification on later IDEs; the test
  suite itself ran on the 261 SDK. No shared build/test-support extraction was added.
- ST-B20's local scope fixed: standalone is the default, optional companion version
  or ZIP selection is explicit and logged, and missing/ambiguous inputs fail.
  All 79 tests pass without any Doris companion dependency. Two negative Gradle
  configuration checks passed for a missing ZIP and simultaneous ZIP/version inputs.
- ST-B23 fixed: only lexically ordinary same-dialect SQL uses the unchanged-input
  path. Claimed conversion failures report errors and cannot retry as raw SQL.
  Generated output with remaining real PIPE tokens is also blocked, containing B7
  until the engine upgrade. Eleven targeted execution/cancellation/dialog tests pass,
  including quoted/commented markers, incomplete literals, mixed-script parse failure
  and the old engine's nested-pipe result. No database requests were made by tests.
- ST-B2 follow-up: the modal runner now consumes the boolean completion result,
  covering cancellation after the computation's last cooperative check as well.
  All four cancellation/platform runner tests pass.
- Final local validation: 83 tests, zero failures/errors/skips; buildPlugin passes.
  The first final verifier attempt hit a shared-cache DirectoryNotEmptyException.
  A project-local verifier cache avoids concurrent peer cleanup; the rerun passed
  all six targets on the final candidate with no internal-API findings. The plugin
  remains an unpublished 0.3.0 development build, advertised for 261/262. Forward
  binary checks on 263 do not claim 263 runtime testing or expand that range.
  Final ZIP SHA-256:
  `6b97644749a5a3005149265353dceac48648d242b8362eec9e75a027e3b56560`.

Implementation commits:

| Finding | Commit |
| --- | --- |
| B1 | `f240747` |
| B2 | `4fc0cae`, `d242b1a` |
| B3 | `634c7e9` |
| B4, B16, B21 | `11fa0ba` |
| B5a-B5c | `75ed303` |
| B8 | `d9a2b6e` |
| B11 | `779a679` |
| B14 | `163e7ba` |
| B17 | `784e6c7`, plus final project-local verifier-cache adjustment |
| B20 | `30e9cba` |
| B23 | `59e0d09` |

## Current planning status, 2026-10-10

The reference proposal is [PLAN-modernization.md](PLAN-modernization.md). The user
subsequently authorized in-place fixes; results are above and in the status table.
Shared extraction remains reviewer-only. September descriptions below are historical
evidence, not a claim that fixed code still has the same behavior.

Doris now embeds its engine at `e1ed71e` and is at `178918b`, version 1.5.6.
DuckDB embeds its engine at `b5c604f` and is at `60fb207`, version 0.3.2.
Trino's migration is peer-owned work per the user. We will not build provider
compatibility gates or migration features here. Co-installation testing and our own
native-verifier correctness still matter.

The September peer integration section and its "Peer boundary/impact" paragraphs
are historical evidence. They describe `28b56ea`, not today's Doris implementation.
Current ownership, commit history, verification lessons and shared-library proposals
are recorded as I6 and in the new plan. Stable IDs have not been renumbered.

Review date: 2026-09-05. Baseline: commit `5aae5d6`, plugin version `0.3.0`.
This document records the review, not completed fixes.

Peer-dependency follow-up on the same date inspected `../doris-intellij-plugin` at
`28b56ea`, declaring plugin version `1.3.0`. Its existing review document was read
but not edited. No plugin implementation changed during this follow-up.

## Using the IDs

- Say `do B1` to request the fix and regression tests described by B1.
- Across repositories, use `ST-B1` for this register and `DORIS-B1` for the [Doris review](../doris-intellij-plugin/REVIEW-pipes-2026-09-05.md). Bare `B1` still means this register while working in this repository. The qualified names are aliases, not new IDs.
- IDs are permanent. Append new IDs; never renumber or reuse existing IDs.
- B1 through B10 retain the numbering of the original review's main findings.
- B5 is a group. `do B5` means B5a, B5b, and B5c; an individual sub-ID narrows the request.
- B IDs are actionable findings. I IDs are integration facts, Q IDs are assessments, V IDs are verification records, and R IDs are release facts.
- P1 through P5 identify the proposed work packages in [the current plan](PLAN-modernization.md#proposed-work-order). H1 through H6 there identify optional shared-library proposals. They do not renumber the B findings.
- Update a finding's status after implementation and verification. Keep resolved entries and their evidence.
- Source line references describe the reviewed baseline. Recheck current code before making a fix.

Evidence labels distinguish reproduced behavior from source review. A reproduced helper or dependency result does not imply an end-to-end IDE test.

Complexity includes implementation and regression tests, not publishing or waiting for an upstream release:

- Low: a local policy or workflow change with focused tests.
- Medium: changes across several paths, document-range handling, or platform integration tests.
- High: dependency changes, runtime isolation, or substantial background-work and lifecycle handling.

## Top five priorities

Current work packages, revised after the October peer-history investigation:

| Rank | Plan ID | Work | Complexity | Existing findings |
| --- | --- | --- | --- | --- |
| 1 | P1 | Exact-IDE, runtime, package and release verification baseline | Medium | B15, B16, B17, remaining B20, B14 |
| 2 | P2 | Consistent execution gates, cancellation and current-source checks | Medium | B1, B2, B8, B23 |
| 3 | P3 | Safe native verification and explicit parser ownership | High | B3, B4, B16, remaining B21 |
| 4 | P4 | Tested core upgrade, document boundaries, edits and diagnostics | High as a group | B5-B7, B9-B13 |
| 5 | P5 | Brikk inspection-host protection and scoped shared-helper work | Medium to high | B22; H proposals in the plan |

P1 is an implementation/verification sequence, not a claim that test infrastructure
is more severe than B1-B4. Small B1/B2 fixes can land after the baseline is captured.
B18 no longer blocks them or dependency upgrades. All packages remain proposed.

B2's estimate covers cooperative cancellation and preventing post-cancel actions. Immediate interruption of a blocked native parser is a separate, higher-complexity requirement if cooperative cancellation proves insufficient.

## Findings

| ID | Finding | Severity | Status | Complexity |
| --- | --- | --- | --- | --- |
| B1 | Authoritative rejection does not block Execute | P1 | Fixed and tested | Low |
| B2 | Cancellation can still lead to execution | P1 | Fixed; cooperative cancellation | Medium |
| B3 | DuckDB verification reads files before approval | P1 | Fixed; parse-only checks | High |
| B4 | Doris discovery can load an untrusted parser JAR | P1, conditional | Fixed; bundled pinned parser | Medium |
| B5 | Preview edits break dialect-block structure | P2 | Fixed within recognized marker blocks | Medium |
| B5a | Replacement destroys the following marker boundary | P2 | Fixed and tested | Low |
| B5b | Replacement retains the old source-dialect declaration | P2 | Fixed and tested | Medium |
| B5c | Insert After changes the dialect of untouched suffix SQL | P2 | Fixed and tested | Medium |
| B6 | Trailing comments make valid SQL fail transpilation | P2 | Fixed using core ranges and upstream snapshot; workaround removed | Low |
| B7 | Nested pipes remain in supposedly desugared output | P2 | Fixed with core 0.19.0; nested query/CTE map regressions pass | Medium |
| B8 | Partial verification is reported as complete acceptance | P2 | Fixed and tested | Low |
| B9 | Marker discovery ignores SQL lexical context | P2 | Fixed using core lexer; scope, gutter and lexical regressions pass | Medium |
| B10 | Error columns lose indentation and statement offsets | P2 | Fixed; exact ranges, indentation, CRLF and Unicode tested | Medium |
| B11 | Empty-block gutter actions operate on the next block | P2 | Fixed; actual handler tested | Low |
| B12 | AST parsing and tree construction block the UI thread | P2 | Fixed; async, cancellation, staleness and disposal tested | Medium |
| B13 | Statement splitting does quadratic prefix scanning | P2 | Fixed; incremental offset scans and core ranges | Low |
| B14 | Release assets can diverge from their tagged source | P2 | Fixed; mocked release paths tested | Low |
| B15 | Behavioral test coverage misses safety and edit workflows | P2 | Improved: 102 default tests, optional host and real-classloader lanes | Medium |
| B16 | Doris discovery uses an internal API on IDE branch 262 | P3 | Removed; six-target verifier clean | Medium |
| B17 | CI does not run Plugin Verifier | P2 | Fixed; release gated on verification | Low |
| B18 | Engine-provider releases lack paired-plugin compatibility coverage | Historical P2 | Superseded by peer embedding; no provider gate planned | N/A |
| B19 | Doris checks engine class presence, not the API it needs | Historical P2 | Retired for this task; peers own embedded engines | N/A |
| B20 | Development sandboxes do not reliably exercise the current peer | P2 | Fixed locally; standalone default and strict explicit overrides | Low |
| B21 | Companion availability and parser paths can become stale | P2, conditional | Removed companion state; disposed service cannot reopen | Medium |
| B22 | Explicit Transpiler actions do not distinguish Brikk inspection SQL | P2 | Fixed with released Kotlin helper 0.2.0; seven host tests pass | Medium |
| B23 | Failed same-dialect PIPE conversion falls back to raw execution | P1 | Fixed; failures and residual PIPE output blocked | Low to medium |
| B24 | Advisory verifier service discovery uses the wrong IDE classloader | P2 | Fixed; real PluginClassLoader regression passes | Medium |

### I6: Current peer ownership and reusable lessons

Inspected October 10, 2026:

- Doris `178918b` embeds core/metadata 0.16.0 and the shared Kotlin helper 0.1.0.
  DuckDB `60fb207` embeds core 0.18.0 and helper 0.1.0. Both helper coordinates
  resolve from Central. Neither requires SQL Transpiler for PIPE support.
- The supplied Doris embedding/fix/1.5.x history and DuckDB's built-in-PIPE through
  shared-helper commits were traced. See [the commit map](PLAN-modernization.md#history-traced).
- Doris's newer verification uses current 262 and two exact 263 builds after old
  passing targets missed Marketplace failures. Public action customization,
  actual stock preparation/parameters, UTF-16 ranges, complete preflight and
  real class ownership are reusable lessons.
- Kotlin helpers centralize resolved host recognition and copied relation/input
  contracts. Their replacement SQL is inspection-only. Dialect injection and
  execution guards remain consumer responsibilities.
- An engine version bump does not itself close B3/B4. The inspected current upstream
  verifier still has the DuckDB prepare fallback and Doris repository discovery.
  The peer DuckDB EXPLAIN validator is not a demonstrated no-I/O replacement.
- The reverse parser borrowing in our current `VerifierService` still exists even
  though Doris stopped borrowing our engine. Its safe replacement belongs to P3.

The earlier I1-I5 section below remains a September snapshot, not current guidance.

## Historical peer integration, September 2026

### I1

Doris uses SQL Transpiler as an optional provider of `dev.brikk.house.sql` engine
classes, not as a caller of `dev.sort.sqltranspiler` services. Normal Doris Execute
and run-to-stage call `SqlFragment.toExecutable("doris", pretty = true)` through
`DorisPipesEngine`, then submit the result through Doris's own request machinery.
There is no call to this plugin's `BrikkTranspiler`, `FindingPolicy`,
`VerifierService`, preview dialog, or approval cache on those paths.

Source: [Doris optional dependency](../doris-intellij-plugin/src/main/resources/META-INF/plugin.xml#L263-L269),
[DorisPipesEngine.kt:44-54](../doris-intellij-plugin/src/main/kotlin/dev/sort/doris/pipes/DorisPipesEngine.kt#L44-L54),
[Doris execution](../doris-intellij-plugin/src/main/kotlin/dev/sort/doris/pipes/DorisPipesRunQueryAction.kt#L115-L129),
[run-to-stage](../doris-intellij-plugin/src/main/kotlin/dev/sort/doris/pipes/DorisPipesIntentions.kt#L85-L100).

The consumed engine contract extends beyond generated SQL:

| API | Consumer reliance |
| --- | --- |
| `SqlFragment`, `PipeQuery`, `toExecutable` | Construction, root classification, desugaring, default arguments, and generation results. |
| `TranspileResult.mapErrorToSource` | The source map must belong to the exact generated SQL submitted; one-based input positions and inclusive source end offsets are converted by Doris. |
| `PipeStageSplitter.split` | Stage start/end offsets and FROM at index zero. |
| `ColumnShape`, `Shape`, `ShapeCatalog`, `stageShapes` | Constructors and stage-output ordering used for completion. |
| `ParseError` and its error records | Exception type, description, and coordinate conventions. |

Source: [DorisPipesEngine.kt:61-146](../doris-intellij-plugin/src/main/kotlin/dev/sort/doris/pipes/DorisPipesEngine.kt#L61-L146).
Preserve these existing binary and behavioral contracts, or coordinate an explicit
consumer migration. Do not introduce a broad new shared framework merely to change
an internal dialog or policy in this plugin.

### I2

The dependency runs in the opposite direction for native Doris verification:
SQL Transpiler finds the enabled Doris plugin and borrows its parser JAR from the
installed `lib` directory. It does not invoke Doris's own annotator. Doris vendors
`doris-fe-sql-parser-1.2-SNAPSHOT-g7027772afcb.jar`, with ANTLR 4.13.1 declared in its
build. The verifier loads the borrowed parser using its own dependency environment.
Renaming, relocating, shading, or changing that parser can therefore affect this
plugin even if Doris still works on its own.

Source: [VerifierService.kt:113-144](src/main/kotlin/dev/sort/sqltranspiler/VerifierService.kt#L113-L144),
[Doris build.gradle.kts:23-29](../doris-intellij-plugin/build.gradle.kts#L23-L29),
[parser provenance](../doris-intellij-plugin/vendor/README.md#L11-L21).
There is no reverse Doris `<depends>` in this plugin. Adding one as a quick B4 fix
would create a dependency cycle with Doris's existing dependency on this plugin.

### I3

Doris's build requests Marketplace SQL Transpiler `0.2.0`; this checkout is `0.3.0`.
Both Transpiler releases use brikk-sql `0.6.0`; the `0.3.0` user-facing change is alphabetical
dialect pickers. The version difference alone does not prove incompatibility or
establish a requirement to raise Doris's minimum to `0.3.0`.

Source: [Doris build.gradle.kts:54-58](../doris-intellij-plugin/build.gradle.kts#L54-L58),
[Transpiler dependency and change notes](build.gradle.kts#L22-L28),
[0.2.0 and 0.3.0 notes](build.gradle.kts#L89-L104).
The Gradle companion pin supplies build/sandbox dependencies; the optional plugin
descriptor identifies the runtime provider by ID, without that version constraint.
Neither plugin ZIP embeds the other plugin.

### I4

Doris separately bundles brikk metadata `0.9.0`, while this plugin bundles engine,
metadata, and verifier `0.6.0`. Separate plugin classloaders mean this is not proof
of a current library collision. It does mean an engine upgrade is not a single
shared Gradle version resolution across the two plugins. Avoid passing separately
loaded metadata objects across a new API without defining their ownership.

Source: [Doris build.gradle.kts:31-42](../doris-intellij-plugin/build.gradle.kts#L31-L42),
[Transpiler build.gradle.kts:28-47](build.gradle.kts#L28-L47).

### I5

Users can still choose SQL Transpiler's explicit actions on a selection in a Doris
editor. Those actions take this plugin's own flow and retain its existing findings.
That is distinct from pressing normal Doris Execute or using Doris Pipes preview.
Doris's preview is read-only; it does not use our Replace/Insert actions.

Source: [TranspileActions.kt:23-61](src/main/kotlin/dev/sort/sqltranspiler/TranspileActions.kt#L23-L61),
[Doris preview](../doris-intellij-plugin/src/main/kotlin/dev/sort/doris/pipes/DorisPipesIntentions.kt#L122-L146).

### Impact on existing work

| IDs | Impact on normal Doris Pipes execution |
| --- | --- |
| B1, B8 | Our execution/report-policy fixes do not reach it. Doris generates and submits directly; see the peer-owned findings below. |
| B2 | Our progress fix does not reach it. Doris's broad failure catch independently swallows cancellation; an upstream exception change must account for that consumer. |
| B3 | Normal Doris Pipes does not call DuckDB verification. Exposure still exists through an explicit Transpile To DuckDB action. |
| B4, B16 | Affect SQL Transpiler's use of the companion parser, not Doris's direct pipe generation. Preserve trusted parser provisioning. |
| B21 | Cached engine availability affects Doris feature enablement; cached parser paths affect our verification. Both need a clear restart/lifecycle policy. |
| B5 | Does not affect Doris's read-only preview or source-preserving submission. Still affects explicit SQL Transpiler edits in a Doris editor. |
| B6, B13 | Doris has a separate unsafe statement splitter; fixing ours does not fix it. See DORIS-B2 below. |
| B7 | Shared-engine desugaring matters to both. Doris additionally classifies only root pipelines, so nested-only pipelines become `NotPipe` and take stock execution. |
| B9 | Doris ignores our dialect markers. Its separate raw pipe-marker classification has its own peer-owned defects. |
| B10 | Doris has its own coordinate conversion. Preserve engine position conventions; correcting our wrapper alone does not correct the peer wrapper. |
| B15, B17, B18 | Separate suites and IDE compatibility checks are insufficient evidence that the installed plugins work together. |

### Peer-owned work

These are existing IDs in the [Doris review](../doris-intellij-plugin/REVIEW-pipes-2026-09-05.md),
not new SQL Transpiler tickets. They must not be marked resolved by a provider-only
fix. A request naming `DORIS-B...` explicitly identifies work in that repository.

| Peer ID | Relevant defect | Consequence for work here |
| --- | --- | --- |
| DORIS-B2 | Raw semicolon splitting can cut execution before a filter. | Shared engine generation cannot restore input Doris already truncated. Do not claim B6 or B13 fixes this. |
| DORIS-B3 | `unsupportedMessages` are discarded before execution and preview. | Fixing B1 does not protect this path. Doris must consume generation diagnostics rather than treating every generated result as safe. |
| DORIS-B10 | Claimed pipe failures, including uncaught engine/linkage/cancellation exceptions, fall back to raw execution. | An engine compatibility failure can become an execution-path change rather than a clean disabled feature. Coordinate with B19 and upstream cancellation changes. |
| DORIS-B14 | Generation can return warning-free SQL rejected by the Doris grammar. | Checking warnings alone is not an output-validity gate. Keep exact generated SQL/source-map identity if validation is added. |

Direct source evidence for these boundaries:
[chunking](../doris-intellij-plugin/src/main/kotlin/dev/sort/doris/pipes/DorisPipes.kt#L94-L128),
[generation](../doris-intellij-plugin/src/main/kotlin/dev/sort/doris/pipes/DorisPipesEngine.kt#L44-L54),
[failure fallback](../doris-intellij-plugin/src/main/kotlin/dev/sort/doris/pipes/DorisPipesRunQueryAction.kt#L88-L92),
[submission](../doris-intellij-plugin/src/main/kotlin/dev/sort/doris/pipes/DorisPipesRunQueryAction.kt#L187-L218).
The peer review records probes of the omitted-filter, lossy-function, and invalid-output
examples; this follow-up inspected their source paths but did not rerun those probes.

## Finding details

### B1

The dialog checks only certification refusals when enabling Execute. It ignores authoritative verifier rejections, unlike the cached-execution path.

Evidence: reproduced `hardRejections=1` with the dialog's enabling predicate returning `true`. The actual dialog was reviewed in source, not exercised interactively.

Source: [TranspilePreviewDialog.kt:232-238](src/main/kotlin/dev/sort/sqltranspiler/TranspilePreviewDialog.kt#L232-L238), [ExecuteFlow.kt:125-139](src/main/kotlin/dev/sort/sqltranspiler/ExecuteFlow.kt#L125-L139).

Completion: share one execution-eligibility rule across preview and cached execution; enforce it at invocation as well as button enablement. Tests must distinguish authoritative rejection, advisory rejection, acceptance, and certification refusal.

Peer boundary: this is one rule across SQL Transpiler's execution paths, not a claim
that Doris calls it. Validate explicit SQL Transpiler actions with Doris installed;
normal Doris Execute remains governed by DORIS-B3 and DORIS-B14.

### B2

The cancelable computation does not check cancellation before returning success. IntelliJ's value-returning progress overload can return a result after its indicator was canceled if the computation returns normally. The approval-cache path can then execute without another review. Broad exception handlers can also swallow cancellation.

Evidence: plugin control-flow review and the [IntelliJ 261 progress implementation](https://github.com/JetBrains/intellij-community/blob/idea/261.22158.277/platform/core-impl/src/com/intellij/openapi/progress/impl/CoreProgressManager.java#L372-L398). No interactive cancellation/execution reproduction was performed.

Source: [TranspileFlow.kt:119-129](src/main/kotlin/dev/sort/sqltranspiler/TranspileFlow.kt#L119-L129), [BrikkTranspiler.kt:75-109](src/main/kotlin/dev/sort/sqltranspiler/BrikkTranspiler.kt#L75-L109), [VerifierService.kt:82-126](src/main/kotlin/dev/sort/sqltranspiler/VerifierService.kt#L82-L126).

Completion: check cancellation between phases and statements, propagate platform cancellation rather than turning it into failure/unavailability, and prevent canceled work from opening preview, updating approvals, appending SQL, or executing. Test the already-approved path explicitly.

Peer boundary: a plugin-layer fix is independent of Doris's normal path. If the fix
changes cancellation behavior in the shared engine, coordinate DORIS-B10 so its
catch-all cannot convert cancellation into stock execution. Do not silently close
that peer finding as part of B2.

### B3

The published DuckDB adapter falls back from SQL serialization to JDBC preparation for some statements. Preparation binds the SQL and can read files before the review dialog appears.

Evidence: a probe against published `brikk-sql-verify:0.6.0` and `duckdb_jdbc:1.5.4.0` verified `CREATE TABLE probe AS SELECT * FROM read_csv(...)`. A harmless CSV's access time advanced, and changing its columns changed the binder's result. The table was not created. External access and extension auto-install/load were enabled by default. Network access and exfiltration were not tested.

Source: [VerifierService.kt:82-94](src/main/kotlin/dev/sort/sqltranspiler/VerifierService.kt#L82-L94), [published verifier sources](https://repo.maven.apache.org/maven2/dev/brikk/house/brikk-sql-verify/0.6.0/brikk-sql-verify-0.6.0-sources.jar), `DuckdbVerifier.kt:39-42, 79-81, 99-114` inside that archive.

Completion: use parser-only verification or mark unsupported statements unverified rather than binding them with unrestricted external access. Test that verification cannot read a controlled local file or contact a controlled local HTTP endpoint. Preserve useful diagnostics without claiming an unperformed check succeeded.

Peer boundary: preserve the exported brikk engine classes if reducing or upgrading
the verifier bundle. Run B18's candidate-provider checks for a dependency change.
Normal Doris Pipes does not itself invoke the DuckDB adapter.

### B4

When trusted companion-plugin discovery finds no parser, the dependency still searches the process working directory and its ancestors for `vendor/lib/doris-fe-sql-parser-*.jar`, then loads and instantiates the parser.

Evidence: confirmed in the published `0.6.0` sources and bytecode. No untrusted JAR was loaded. Exploitation requires a matching attacker-controlled JAR in that working-directory ancestry, fallback discovery, and invocation of Doris verification. Opening an arbitrary project alone is insufficient.

Source: [VerifierService.kt:113-144](src/main/kotlin/dev/sort/sqltranspiler/VerifierService.kt#L113-L144), [published verifier sources](https://repo.maven.apache.org/maven2/dev/brikk/house/brikk-sql-verify/0.6.0/brikk-sql-verify-0.6.0-sources.jar), `DorisVerifier.kt:89-133` inside that archive.

Completion: use an explicit trusted-parser factory or return unavailable when trusted discovery fails. Test missing/disabled companion behavior and that working-directory contents never affect parser selection. Do not work around this by changing the IDE's global working directory.

Peer acceptance: assert Doris verification is actually available with the trusted
companion installed, not merely that no exception escaped. Cover the parser's
installed filename/layout and ANTLR environment, and preserve plugin optionality
without adding a reverse dependency cycle. Coordinate B16 and B21.

### B5

Preview edits must preserve statement separation and the dialect assigned to untouched text. This group comprises B5a, B5b, and B5c.

Source for all three: [TranspilePreviewDialog.kt:244-255](src/main/kotlin/dev/sort/sqltranspiler/TranspilePreviewDialog.kt#L244-L255), [TranspileFlow.kt:29-44](src/main/kotlin/dev/sort/sqltranspiler/TranspileFlow.kt#L29-L44).

Completion: all three subfindings fixed, with exact-document assertions for whole blocks and partial selections, adjacent blocks, and undo behavior.

### B5a

Replacement consumes the newline before the next marker and inserts rendered SQL without a trailing separator. A standalone probe using the actual range and rendered SQL reproduced:

```sql
-- dialect: mysql
SELECT 1-- dialect: duckdb
select 2;
```

Completion: preserve the next marker on its own line and maintain valid statement separation.

### B5b

Whole-block replacement excludes the governing marker, leaving the source dialect declared above target-dialect output. For example, PostgreSQL output remains under `-- dialect: mysql`.

Evidence: source review; B5a's reproduced replacement also retained its original marker.

Completion: update the declaration for a replaced complete block. For partial selections, avoid relabeling untouched SQL; introduce appropriate boundaries instead.

### B5c

Insert After adds a target-dialect marker but does not restore the original dialect before an untouched suffix. Selecting the first statement in a multi-statement MySQL block and inserting PostgreSQL output therefore relabels the remaining MySQL statements.

Evidence: source review of marker inheritance and insertion text.

Completion: bound the inserted target block and preserve the original dialect of the untouched suffix.

### B6

Comment-only text after a semicolon becomes another statement slice. Both `SELECT 1; -- done` and `SELECT 1; /* end */` were reproduced as failures because `SqlFragment` contains no statement for the comment. The `.bsql` annotator uses the same transpilation path.

Source: [BrikkTranspiler.kt:140-154](src/main/kotlin/dev/sort/sqltranspiler/BrikkTranspiler.kt#L140-L154).

Completion: distinguish comments/empty slices from statements without splitting quoted semicolons. Test line comments, block comments, comment-only input, and actual syntax errors after comments.

### B7

The plugin detects nested pipes, but the dependency desugars only a root pipeline. A reproduced Doris-to-DuckDB conversion of `SELECT * FROM (FROM t2 |> SELECT id)` retained `|>` while returning `pipesDesugared=true`.

Source: [BrikkTranspiler.kt:88-96](src/main/kotlin/dev/sort/sqltranspiler/BrikkTranspiler.kt#L88-L96).

Completion: transform the complete tree while preserving certification and source maps. Test nested pipelines in subqueries and CTEs, and distinguish pipe operators from string contents. A dependency fix may be needed.

Peer impact: Doris tests `fragment.ast is PipeQuery` only at the root in
[DorisPipesEngine.kt:45-47](../doris-intellij-plugin/src/main/kotlin/dev/sort/doris/pipes/DorisPipesEngine.kt#L45-L47).
A nested-only pipeline is therefore `NotPipe` there and falls through to stock
execution. A local BrikkTranspiler fix is not enough for both consumers. An upstream
fix must preserve the I1 contracts and be paired with Doris classification or an
explicit rejection of unsupported nested pipelines. Include this case in B18.

### B8

`allAccepted` filters out unverified statements. A reproduced report containing one accepted statement and one unavailable statement returned `allAccepted=true` with no displayed rejections. The dialog then describes the output as accepted by the native parser.

Source: [VerifierService.kt:59-66](src/main/kotlin/dev/sort/sqltranspiler/VerifierService.kt#L59-L66), [TranspilePreviewDialog.kt:135-149](src/main/kotlin/dev/sort/sqltranspiler/TranspilePreviewDialog.kt#L135-L149).

Completion: full acceptance requires every statement to have been verified and accepted. Display partial/unavailable checks explicitly and test all report combinations.

### B9

Marker discovery treats each physical line independently. A marker-shaped line inside a PostgreSQL dollar-quoted string was reproduced as a second dialect marker. Block comments have the same source-level defect.

Source: [DialectMarker.kt:34-40](src/main/kotlin/dev/sort/sqltranspiler/DialectMarker.kt#L34-L40).

Completion: recognize only actual marker line comments, respecting strings, dollar quotes, and block comments. Preserve real markers after those constructs and test their scope ranges.

### B10

Slices retain line offsets but lose first-line column offsets when trimmed. `SELECT )`, an indented version, and `SELECT 1; SELECT )` all reproduced column 8 rather than their distinct document positions.

Source: [BrikkTranspiler.kt:97-103](src/main/kotlin/dev/sort/sqltranspiler/BrikkTranspiler.kt#L97-L103), [BrikkTranspiler.kt:140-145](src/main/kotlin/dev/sort/sqltranspiler/BrikkTranspiler.kt#L140-L145).

Completion: preserve source offsets and map first-line columns correctly. Assert actual error ranges for indentation, same-line statements, later lines, and CRLF input.

Peer boundary: Doris independently forwards one-based, end-anchored engine columns
into a zero-based annotation model at
[DorisPipesEngine.kt:165-171](../doris-intellij-plugin/src/main/kotlin/dev/sort/doris/pipes/DorisPipesEngine.kt#L165-L171).
Fix our wrapper without silently changing the shared engine's coordinate convention;
the peer conversion needs its own correction if brought into scope.

### B11

For adjacent markers with an empty first block, the gutter handler moves the caret to the second marker. Scope resolution then chooses the second block while the handler supplies the first marker's source dialect.

Evidence: source review. Source: [DialectMarkerLineMarkerProvider.kt:51-61](src/main/kotlin/dev/sort/sqltranspiler/DialectMarkerLineMarkerProvider.kt#L51-L61).

Completion: resolve the clicked marker directly and report its block as empty rather than operating on its neighbor. Exercise the gutter action, not just icon registration.

### B12

The AST action parses the complete scope and recursively constructs its tree on the UI thread. The 50-row expansion limit does not limit parsing or tree construction. Large input can freeze the IDE.

Evidence: source review; no UI responsiveness benchmark performed. Source: [AstViewerToolWindow.kt:48-50](src/main/kotlin/dev/sort/sqltranspiler/AstViewerToolWindow.kt#L48-L50), [AstViewerToolWindow.kt:82-98](src/main/kotlin/dev/sort/sqltranspiler/AstViewerToolWindow.kt#L82-L98).

Completion: snapshot editor state, parse/build off the UI thread with cancellation, and publish only the current result while the project/content remain alive. Test refresh races and disposal.

### B13

Each statement recomputes its line offset by copying and scanning all preceding input. Many short statements therefore cause quadratic prefix work.

Evidence: source-level complexity analysis, not a benchmark. Source: [BrikkTranspiler.kt:140-145](src/main/kotlin/dev/sort/sqltranspiler/BrikkTranspiler.kt#L140-L145).

Completion: count newlines incrementally or index them once. Preserve correct offsets and test large multi-statement input without brittle timing assertions.

### B14

On a subsequent push to a release branch with the same version, the workflow overwrites the existing release asset without moving its tag. The downloaded ZIP can then differ from the tagged source.

Evidence: workflow review; no published release was changed. Source: [.github/workflows/ci.yml:76-81](.github/workflows/ci.yml#L76-L81).

Completion: make versioned releases immutable. Reject conflicting reuse of an existing version, or permit only a demonstrably identical artifact. Test new-version and existing-version paths without modifying a real release.

### B15

The existing suite covers helpers, policy pieces, and registration better than complete execution and edit workflows. The reproduced defects coexist with 56 passing tests. For example, the test named `parse failure reports position` asserts only the failure type.

Evidence: test review and V2. Source: [BrikkTranspilerTest.kt:39-47](src/test/kotlin/dev/sort/sqltranspiler/BrikkTranspilerTest.kt#L39-L47), [DialectMarkerGutterPlatformTest.kt](src/test/kotlin/dev/sort/sqltranspiler/DialectMarkerGutterPlatformTest.kt).

Completion: add behavioral coverage for execution eligibility, cancellation, exact preview edits, and actual error ranges. Regression tests delivered with B1, B2, B5, B10, and B11 count toward this work; do not duplicate them merely to close B15.

Paired-plugin loading, binary-consumer compatibility, and trusted parser provisioning
are tracked separately by B18, not established by the existing helper tests.

### B16

Plugin Verifier flagged `PluginManager.findEnabledPlugin` as internal on `IU-262.8665.81`. This is a compatibility-maintenance risk, not a reproduced runtime failure.

Evidence: V4. Source: [VerifierService.kt:139-140](src/main/kotlin/dev/sort/sqltranspiler/VerifierService.kt#L139-L140).

Completion: replace the lookup with a supported mechanism and rerun verification across the supported range. Coordinate with B4 to avoid implementing parser discovery twice.

### B17

CI runs tests and builds the ZIP, but does not invoke `verifyPlugin`, so the configured compatibility matrix is not a release gate.

Evidence: [.github/workflows/ci.yml:25-29](.github/workflows/ci.yml#L25-L29), [build.gradle.kts:118-127](build.gradle.kts#L118-L127).

Completion: run Plugin Verifier in CI with cached IDE downloads and uploaded reports. Make compatibility failures block release and explicitly decide how internal-API warnings are handled.

Peer impact: Doris has a verifier job, but its release job depends only on build.
Neither workflow currently gates a provider release on running the installed pair.
B17 owns this plugin's IDE-verifier gate; B18 owns downstream integration coverage.
Source: [Doris CI:46-86](../doris-intellij-plugin/.github/workflows/ci.yml#L46-L86).

### B18

Current status: superseded for this task by peer embedding. Do not implement the
provider/consumer release gate below. Retain independent co-installation/classloader
tests and trusted-parser tests in P1/P3. The original evidence follows.

Engine-provider releases lack a test/release gate for the actual Doris consumer.
Each repository's test allowlist excludes the other plugin. Pure engine tests can
still see dependency JARs on a flat test classpath, which does not establish optional
descriptor loading, plugin-classloader visibility, or installed parser discovery.
Doris verification's existing test even accepts an unavailable verifier.

Evidence: source review plus existing test logs showing only the respective plugin
loaded. No current provider/consumer ABI failure was reproduced.
Source: [our test allowlist](build.gradle.kts#L144-L147),
[Doris test allowlist](../doris-intellij-plugin/build.gradle.kts#L106-L123),
[permissive verifier test](src/test/kotlin/dev/sort/sqltranspiler/VerifierServiceTest.kt#L77-L88).

Owner: both repositories' integration/build setup, with the provider release gate here.
Completion:

- Build/test current Doris against the candidate SQL Transpiler artifact, not only its fixed Marketplace dependency.
- Run installed-pair tests with actual plugin classloaders on supported IDE generations, and genuine companion-absent tests.
- Exercise optional actions, ordinary SQL fallback, pipe generation, stage splitting/completion, and exact source-map use.
- Assert native Doris verification succeeds with the trusted companion and is safely unavailable without one.
- Record artifact versions/commits and make failures block engine-provider release. A green B17 verifier alone does not satisfy this.

Initial scope is a focused compatibility suite, not a claim to repair every
peer-owned execution defect. Cases exposing those defects should remain clearly
tracked rather than become tests asserting unsafe behavior is correct.

### B19

Current status: retired for work here. Doris removed this provider guard with direct
embedding; the user assigns Trino's transition to that peer. No provider API or
consumer handshake work is planned here. The original September evidence follows.

Doris's runtime feature guard only checks that `SqlFragment` exists. It does not
check the methods, constructors, default-argument bridges, or semantics consumed in
I1. Its optional dependency accepts an installed plugin by ID, not the Gradle build
pin. An incompatible engine can pass the guard and fail later; the catch-all Execute
path can then treat that failure as unclaimed SQL, covered by DORIS-B10.

Evidence: source-confirmed guard and consumption; a version-induced failure was not
reproduced. Source: [DorisPipes.kt:28-44](../doris-intellij-plugin/src/main/kotlin/dev/sort/doris/pipes/DorisPipes.kt#L28-L44),
[DorisPipesEngine.kt](../doris-intellij-plugin/src/main/kotlin/dev/sort/doris/pipes/DorisPipesEngine.kt),
[optional descriptor](../doris-intellij-plugin/src/main/resources/META-INF/plugin.xml#L265-L269).

Owner: provider compatibility contract plus Doris's capability guard. Completion:
preserve the existing exported ABI or coordinate a versioned migration; detect
unsupported providers before enabling engine features; fail closed with an actionable
message. Test supported and deliberately incompatible providers through B18. Do not
invent a plugin.xml version constraint or require 0.3.0 solely because it is newer.

### B20

Current scope: our explicit companion-test artifact selection should fail on an
invalid requested path and identify the actual version. Reciprocal provider build
overrides and a provider-pair default are obsolete. P1 owns the remaining local
test configuration. The original September description follows.

The default SQL Transpiler sandbox installs Doris `0.5.0`, which predates Pipes in
`0.7.0`. It therefore cannot exercise the present consumer integration. A supplied
but nonexistent `-PdorisPluginZip` silently falls back to that old Marketplace
release. Doris's own build also fixes the provider at Marketplace `0.2.0`, with no
candidate-provider override.

Evidence: [our sandbox dependency](build.gradle.kts#L62-L75),
[Doris Pipes introduction](../doris-intellij-plugin/src/main/resources/META-INF/plugin.xml#L162-L175),
[Doris provider pin](../doris-intellij-plugin/build.gradle.kts#L54-L58).

Owner: both development configurations. Completion: provide explicit candidate
artifact overrides in both directions, fail on an invalid requested local artifact,
and use a known compatible Pipes-capable default pair. Print the selected artifacts
so manual testing cannot silently use a different plugin. B18 may deliver this setup;
record verification here rather than implementing it twice.

### B21

Current scope: only our verifier availability cache, parser property and resource
lifecycle remain work here. Peers' old engine-availability caching is historical.
If P3 removes companion parser borrowing, replace these cross-plugin requirements
with owned-parser lifecycle tests. The original September evidence follows.

Companion state is cached for the lifetime of loaded classes/services. Doris caches
`engineAvailable`; this plugin caches verifier absence/presence and leaves its
process-wide parser path set after disposal. If the platform permits a companion
change without restart, a previously missing engine can stay missing, or a replaced
parser path can stay selected. Dynamic failure was not reproduced; Doris documents
installation followed by restart, so the finding is conditional on lifecycle support.

Source: [Doris availability](../doris-intellij-plugin/src/main/kotlin/dev/sort/doris/pipes/DorisPipes.kt#L35-L44),
[verifier cache/property/disposal](src/main/kotlin/dev/sort/sqltranspiler/VerifierService.kt#L108-L164),
[restart instructions](../doris-intellij-plugin/src/main/resources/META-INF/plugin.xml#L172-L175).

Owner: both plugins' companion lifecycle behavior. Completion: either enforce and
test restart-required changes, or invalidate caches and clean up only service-owned
property values on supported dynamic transitions. Cover absent, present, disabled,
updated, and re-enabled companions without borrowing a disabled plugin's parser.

### B22

The new Brikk Kotlin integrations present injected SQL containing bind/slot and
inspection placeholders. The shared helper explicitly marks that context
nonexecutable. Our selected-text actions allow those strict SQL hosts, then offer
Replace/Insert and Send to Console without checking the host's Brikk context.
The peers' own run/preview guards do not guard this plugin's explicit actions.

Evidence: source-level integration review, not an interactive corruption or live
execution reproduction. See [action enablement](src/main/kotlin/dev/sort/sqltranspiler/TranspileActions.kt#L23-L26),
[preview edits](src/main/kotlin/dev/sort/sqltranspiler/TranspilePreviewDialog.kt#L232-L255),
[console handoff](src/main/kotlin/dev/sort/sqltranspiler/SendToConsole.kt#L27-L75),
[Doris editing boundary](../doris-intellij-plugin/BRIKK-HOUSE.md#L78-L80) and
[DuckDB execution guard](../duckdb-intellij-plugin/src/main/kotlin/dev/sort/duckdb/pipes/DuckdbPipesRunQueryAction.kt#L54-L63).
Direct Execute is currently conditional on console lookup succeeding; Send to
Console is available through the ordinary conversion dialog independently of that.

Completion: recognize resolved/pending Brikk hosts through an optional shared-helper
adapter, preserve authored Kotlin, and prevent implicit executable export or edit
of inspection placeholders. Enforce at invocation/apply as well as action display.
Cover Kotlin absent, pending resolution, ordinary injected SQL and `.bsql` workflows.
P5 defines the proposed conservative first implementation.

### B23

`ExecuteFlow` executes the original text after any failed transpilation when source
equals target. That also includes real PIPE syntax and lowering refusals, not just
ordinary SQL containing a quoted `|>` string. In a multi-statement scope, falling
back can expose earlier statements to execution before a later invalid pipe fails.

Evidence: [ExecuteFlow.kt:95-109](src/main/kotlin/dev/sort/sqltranspiler/ExecuteFlow.kt#L95-L109).
The unconditional same-dialect failure branch is source-confirmed. No live query
or partial-execution reproduction was performed in this planning pass.

Completion: distinguish lexically unclaimed ordinary SQL from claimed PIPE input;
a claimed parse/lowering/verification failure never submits raw SQL. Propagate
cancellation, preflight the complete chosen scope, and test quoted/commented markers
and mixed scripts with a recording console. Coordinate B1/B2 and the newer engine's
typed `UnsupportedError` refusals in P2/P4.

### B24

Real product-loader testing exposed ShardingSphere SPI discovery through the IDE
thread context loader. A flattened fixture could instead fail with a provider
"not a subtype" error; after removing its duplicate private JARs, the actual plugin
still returned an unavailable PostgreSQL verifier. Both creation and verification
must run with the owning plugin loader for SPI discovery. The previous loader must
be restored on every exit.

Fixed in `VerifierService.withVerifierLoader`. `PluginIsolationPlatformTest` uses
the distribution's real loader and requires successful native/advisory checks.
`CancellationPlatformTest` checks restoration after platform/coroutine cancellation
and a fatal error. No cross-plugin objects or global loader changes persist.

## Quality assessments

- Q1: The plugin is a small, readable codebase with separate transpilation, verification, execution policy, and UI components. This is a reviewer assessment, not a measured quality score. See [BrikkTranspiler.kt](src/main/kotlin/dev/sort/sqltranspiler/BrikkTranspiler.kt), [VerifierService.kt](src/main/kotlin/dev/sort/sqltranspiler/VerifierService.kt), and [FindingPolicy.kt](src/main/kotlin/dev/sort/sqltranspiler/FindingPolicy.kt).
- Q2: Statement-level findings and approval invalidation are useful foundations. The approval key includes source text and the dialect pair, and reuse checks generated SQL. These strengths do not negate B1 or B2. See [BrikkTranspiler.kt:22-49](src/main/kotlin/dev/sort/sqltranspiler/BrikkTranspiler.kt#L22-L49) and [TranspileApprovalCache.kt](src/main/kotlin/dev/sort/sqltranspiler/TranspileApprovalCache.kt).

## Verification records

These are results from the 2026-09-05 review, not promises about future commits.

- V1: Reviewed commit `5aae5d6`. `git diff v0.3.0..HEAD --stat` showed only README changes, so the reviewed plugin code matches the release. The original review changed no tracked files; this documentation update is subsequent work.
- V2: `./gradlew test buildPlugin verifyPluginProjectConfiguration --console=plain` passed. The test XML reports contained 56 tests across nine suites, zero failures, zero errors, and zero skips.
- V3: The same command built `build/distributions/sql-transpiler-intellij-plugin.zip`. Configuration validation warned about the explicit `untilBuild` cap; the cap remains the published policy described by R3.
- V4: `./gradlew verifyPlugin --console=plain` passed. Results were compatible for `DB-261.24374.56`, `IU-261.25134.95`, and `IU-262.8665.81`. The last target had one internal-API warning, recorded as B16. Reports were written to `build/reports/pluginVerifier/`.
- V5: No end-to-end interactive database execution or IDE cancellation test was performed. Helper probes and the controlled DuckDB file probe ran outside the repository. B2 and the interactive consequences of other source findings need platform-level regression tests. The security review did not attempt exfiltration or load an untrusted JAR.
- V6: The peer-dependency follow-up inspected Doris `28b56ea` and SQL Transpiler `5aae5d6`, current descriptors/build scripts, existing test logs, and engine API signatures. It made documentation-only changes in this repository, did not edit Doris, and did not rerun either suite or launch both plugins together. Existing separate-suite results are not proof of paired compatibility. The Doris distribution ZIP present locally was older than its current source version and was not treated as a current candidate build.
- V7: On 2026-10-10, traced the specified embedding and 1.5.x commits through Doris `178918b` and DuckDB `60fb207`; read current source, the shared helper, verifier source and uncommitted DuckDB validation notes. Doris was clean at inspection. Confirmed Central POMs for core 0.18.0 and Kotlin helper 0.1.0. Only planning/review documentation changed here. No source fixes, builds, tests, artifact rebuilds, peer changes or publications were performed. Earlier verification counts remain historical.

## Release facts

- R1: As checked on 2026-09-05, the latest stable release was `0.3.0`, published July 15, 2026, on [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/32900-sql-transpiler/versions/stable/1108624) and [GitHub Releases](https://github.com/sort-dev/sql-transpiler-intellij-plugin/releases/tag/v0.3.0). Recheck both services when asked for the latest release in the future.
- R2: The reviewed checkout declares `version = "0.3.0"` in [build.gradle.kts:8](build.gradle.kts#L8).
- R3: Published compatibility is build branches `261` through `262.*`, corresponding to IDE 2026.1 and 2026.2. It does not promise unrestricted future `2026.1+` compatibility. See [build.gradle.kts:85-88](build.gradle.kts#L85-L88) and the Marketplace release linked in R1.

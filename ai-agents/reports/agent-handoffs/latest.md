# Latest agent handoff

## Automatic entry vanish and player completion checkpoint, 2026-10-05

Owner extended OWNER-INVESTIGATION-TOOLS with IT-09 (enable vanish after fresh durable Staff Mode activation, never toggle; rollback that same session on persistence failure; preserve recovery/transfer visibility and existing exit policy) and IT-10 (bounded, permission/visibility-filtered player-name completion at actual target positions, including `/alts` and both `/alt` targets; preserve non-player arguments). Paper uses cached online names plus existing inventory offline cache; Velocity uses asynchronous known-player lookup. No new migration or companion API in this extension.

PR #322 product head `3c60152c90c8007b22e73e6420177fbc665450ce` normally incorporates authoritative main `ba6dcabc9a731e7e3e21c8405778764abdc626f4` (merged PR #321). Clean Java 25/release 21 build followed by final-head test/check/runtime build: 1,645 discovered, 1,643 passed, two existing Windows symlink skips, zero failures/errors. All runtime JARs build and pass ZIP CRC; database integration tests compile but are excluded from execution. Wiki validates 41 pages; diff check passes. New regression tests cover routes, permissions, hidden-name filtering, offline alts, queue limits and rejection; entry tests are wiring evidence, not runtime acceptance. Static review's 31 findings at previous head were refactored or narrowly suppressed for deliberate test-array allocation/test fixtures; exact-head Codacy check 111625293673 succeeds with zero annotations. Hosted fork workflows require maintainer approval and provide no execution evidence. No EARS/state helpers exist; manual requirements/evidence are maintained. Orchestration has the unchanged 460-finding main baseline, not a pass.

Unmerged LOCAL TEST ONLY Paper SHA-256 `7680a0a48c2a63bf873bf1b29f5d23e793418b81b8e1b398b25b8a6726039251`, Velocity `2a9d4fb7b5aa87838cb9bce0b9a51181b4f9e072f62aea68aa95b29e4ae6485a`, version `0.1.0-entry-completion-local-test`. PR #323 publishes this docs-only checkpoint; it does not validate or merge product source. Status remains PARTIAL / ACTIONABLE_CONTINUATION pending hosted checks, MariaDB/runtime and Java/Bedrock acceptance, migration sequencing, review and authorized merge. Production untouched. Prior checkpoints below are historical and superseded by this entry.


## GUI cleanup verification, 2026-10-05

Owner extended the same package with GUI streamlining; implementation PR #322 remains draft/open/unmerged at frozen product head `85d4db419ee6dcf0266a3cf686a89fb5b96c1e9e`, based on freshly verified main `18d4f4b05af94ee325842c68d482feedabe5d27f`. Fixed grouped Staff Dashboard tools, player investigation shortcuts, permission-aware help, loading Back, and separate Close/confirmed Exit are implemented. Existing command/service checks remain authoritative; no schema/provider API was added by this GUI slice. Delayed delivery checks the original inventory, active session, inspector/action permissions and visibility. Other owner/transfer/Discord package work is preserved.

Manual requirements IT-06..08: permission filtering retains fixed slots and prevents hidden routing; Close keeps Staff Mode; Exit requires confirmation; player shortcuts expose only permitted existing workflows; delayed actions recheck authority/visibility; evidence save and punishment confirmation stay explicit; help shows authorized shortcuts. Two new fixed-slot/filter assertions failed on previous code, then all eight routing/view tests passed. No EARS/state helpers exist.

Java 25.0.3 clean test/check/runtimeJars passes for the frozen product tree: 1,629 discovered, 1,627 passed, two existing Windows symlink skips, zero failures/errors. Wiki passes 41 pages; four runtime JAR CRCs pass; 31 contract source paths checked with zero provider leaks. Local artifacts use `0.1.0-investigation-gui-local-test` and are unmerged test artifacts. Paper SHA-256 `419b628063e9c0729ca1b0297694fa5711d6ac841eb25aa9fa4ef73f580d1451`.

Status: PARTIAL / ACTIONABLE_CONTINUATION. Exact-head Codacy Static Code Analysis check 111616091447 succeeds with zero annotations; live inline review comments are empty. Coverage 37263635829, Sentinel artifact 37263635877, Wiki 37263635847 and state-reset proof 37263635842 stop at ACTION_REQUIRED for maintainer approval of fork workflows; none proves hosted product execution. Pi supersession 37263633748 is skipped, not a pass. MariaDB integration was excluded (prior Docker unavailability), staging/runtime providers and Java/Bedrock usability remain unverified. Existing unchanged orchestration baseline remains 460 findings, not a pass. Proposed V24 migration sequencing across pending V22/V23 remains unresolved. Next: address real exact-head findings, obtain missing hosted/database/runtime evidence and reviewed delivery. Unified timeline, durable ownership/handoffs, follow-up inbox and evidence bundles remain future work.

This docs-only status delta does not change product code or rerun product gates; executable results belong to the frozen product head. It supersedes older current-head summaries below while retaining historical evidence. No merge, deployment, restart, production or player-data change is authorized. Canonical status publication remains unfinished until docs PR #323 reaches main.


Owner-directed current work: **OWNER-INVESTIGATION-TOOLS — PARTIAL / ACTIONABLE_CONTINUATION**. See [canonical handoff](../package-handoffs/2026-10-04-owner-investigation-tools.md) and [contract](../../work-packages/packages/OWNER-INVESTIGATION-TOOLS.md). This supersedes routing for this worker only; the historical Market handoff below is preserved. No merge or deployment authorization.


Current handoff: **ES-X03 — EnthusiaMarket destructive provider** — **PARTIAL / ACTIONABLE_CONTINUATION**.

Canonical package handoff:
ai-agents/reports/package-handoffs/2026-09-22-es-x03-marketcase-completion-validation.md.

Market [PR #7](https://github.com/wsg138/EnthusiaMarket/pull/7) is
OPEN/DRAFT/CLEAN on `package/es-x03-market-static-remediation` at
`81b14c349be0ad404edeedbac5e109e2a375c255`; Staff
[PR #139](https://github.com/wsg138/EnthusiaStaff/pull/139) is
OPEN/non-draft/UNSTABLE on `package/es-x03-market-provider` at
`f6732816f35e3a9634068badb56c220b5d679bd4`. Clean-clone component comparison
found no product-file delta and hash
`e7082c5bb1aacbcd95ac8457aa17392a740c3fe5df6154e743eebb4bc6019839`.

Market hosted runs `35601165548` and `35601165577` passed. Exact Staff Coverage
`35733465364` / job `106764602834`, Sentinel artifact `35733465456` / job
`106764605492`, and durable Sentinel restart job `516` (`PAPER_RESTART_OK`)
passed. Codacy Diff Coverage and Coverage Variation passed. No live review
thread remains.

Codacy static check `106765372218` remains `ACTION_REQUIRED` with 1,141
reported issues. Canonical Pi `35733463593` / job `106764599729` stopped before
private dispatch when its workflow-history lookup returned HTTP 401 Bad
credentials; no private Pi, Paper, or MariaDB runtime ran.

Continue only small paired fixes for validated static findings, preserving
Market #7, Staff #139, and exact component parity. The staging owner must
repair the least-privilege bridge credential before a fresh canonical Pi run.
Do not use a personal credential or bypass the public bridge.

No production listing, balance, item, player data, database, deployment,
authority, LiteBans, cutover, or issue #43 acceptance was performed. D09
remains preserved while X03 owns branch-local V21.

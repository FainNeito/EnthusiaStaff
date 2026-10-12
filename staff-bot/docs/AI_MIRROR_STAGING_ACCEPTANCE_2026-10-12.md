# AI mirror registration: isolated STAGING acceptance and rollback

**Status:** CODE MERGED, NOT DEPLOYED OR ACTIVATED. **Date:** 2026-10-12. This is an operator gate, not authority to deploy. Live Minecraft/Discord moderation, retrospective deletion, and AI-derived punishments remain OFF.

## Evidence and release scope

- Public EnthusiaStaff PR #551, merged at `67077a0c14f0e5f5bc9f20d6664b5a593b8c9d51`: one off-JDA post-send observer, encrypted fsync, bounded immediate HTTP registration, exclusive fast/slow claims, exact authenticated receipt revisions, persistently rate-limited slow recovery. Off-by-default STAGING-only.
- Public PR #554, merged at `e9aa123f2e98aca0189dbbd0ed5da4581b37563a`: numeric-only process-lifetime `mirrorRecovery` counters on loopback `GET /ready` when the durable STAGING session is selected.
- Private Moderation API PR #122, merged at `87e4efcd0ea65578dc4523afcd63efe52e742f46`: actual Java-to-disposable Python FastAPI + SQLite contract for 70 distinct healthy messages, real canonical PENDING 409 then FINAL recovery, exact aliases, idempotency, rejected auth/scope conflicts and crashed-JVM replay. No punitive outputs.
- Local Windows full StaffBot check PASS; Ubuntu WSL POSIX mirror/health tests PASS; public exact-head GitHub checks were green when PRs merged.
- **Not proven:** target Bloom STAGING readiness, real JDA callback completeness, production message-loss rate, operational rollback, filesystem retention/backup, 503 outage recovery at scale, API model accuracy, or deletion transport.

## Authorization and isolation preflight (owner action required)

1. Identify a **distinct** STAGING StaffBot process, its own isolated Discord test guild/channel and dedicated staging Moderation API database. If there is no isolated instance, create one only with explicit owner permission; do not repurpose or restart production StaffBot.
2. Confirm the exact artifacts derived from main commits, target Linux runtime filesystem semantics (POSIX, atomic move/fsync), process user and expected private directory ownership; keep secrets out of logs, GitHub, screenshots and chat.
3. Back up applicable staging config and record existing startup/health result. Prepare a verified rollback artifact and startup toggle *before* enabling the new worker.
4. Establish an explicit privacy policy for original chat text in encrypted receipts (default code TTL **60 minutes**, max **128** saved receipts), key backup/rotation, and emergency disk-full handling. Do not copy production chat data into staging.
5. Approve separate non-production API `mirror:register` bearer authorization scoped to only the test channels and a stable `discord_general` profile. Use a distinct secret from Discord, production API and the journal encryption key.
6. The code requires both `ENTHUSIA_STAFF_BOT_AI_MIRROR_REGISTER_ENABLED=true` and `ENTHUSIA_STAFF_BOT_AI_MIRROR_DURABLE_ENABLED=true`, STAGING environment, a configured registration URL/client identity/token file, and chat-bridge authorized channels. See `AiMirrorPublicationConfig` and `AiMirrorDurableStagingSettings` for the source-of-truth validation. Neither flag is approved to turn on by this document.
7. If approved, provision a **new random 32-byte binary** journal key directly on the isolated host, held by the StaffBot process owner. Journal directory must be absolute with POSIX `0700`; key regular file must be absolute with `0400` or `0600`, same owner, no symlink. Use separate staging filesystem paths. Avoid exposing key bytes anywhere. Do not silently overwrite an existing key or journal.

## Stepwise isolated STAGING test order (after owner authorizes)

1. Verify legacy environment remains healthy with the durable flag **false**; confirm `/ready` does **not** contain `mirrorRecovery` and that no production process was changed.
2. Bring up the isolated staging process with the approved secret provisioning and both flags. Verify single JDA observer, no duplicate forwarded Discord posts, `/ready` health and numeric `mirrorRecovery` JSON; `/health` retains previous shape.
3. Send a small set of synthetic public Minecraft messages through the **actual staging JDA post-send transport**, not through direct test helpers. Confirm exact Discord message snowflakes, normalized original undecorated message text, canonical linkage, one final API classification per original, and authorized guild/channel/profile. Verify private/ticket/staff/exempt traffic is not registered.
4. Send at least 70 distinct synthetic healthy messages, with bursts near normal chat load. Reconcile an independently counted source-event/JDA-posted list with the service's canonical message aliases, encrypted commits and `eligiblePostSend`, `persisted`, `fastConfirmed`, `slowConfirmed`. Require **zero unexpected** missing/wrong/duplicate aliases, capacity drops, terminal rejections, expired receipts and storage failures for the healthy case. Counters reset at JVM restart and are **not** an independent proof of completeness.
5. Test typed `409 canonical_pending` against actual staging API: receipt stays encrypted, source later becomes FINAL, safe slow recovery produces one alias. Separately test transient `503`/network outage, hard process restart after HTTP 200-before-local-ACK, and fast queue saturation; verify encrypted records survive where within admission/TTL budgets.
6. Verify the conservative one-per-minute slow throttle across restart, and the five-second catch-up limit after an authenticated HTTP 200 plus durable ACK. A failure or interrupted attempt must revert to the persisted one-minute interval. New first sends must not consume slow budget. Simulate an invalid private scope, typed permanent 409, corrupted ciphertext or key permissions to confirm fail-closed readiness and no secret leakage.
7. Verify rollback *in staging*: stop new admission, cleanly terminate ingress/fast/slow, retain exclusive journal lock until workers exit, restart in safe default mode. Be aware that reverting to the volatile path does **not** automatically drain the previously encrypted durable backlog. Preserve the key/journal until a reviewed recovery/retention decision; never wipe a journal as a rollback shortcut.
8. Archive **aggregate-only** metrics, test counts and approval decisions (not raw messages/IDs/keys) in the release issue. A human must separately authorize any production-facing behavior. AI-issued/recommended strikes, mutes, bans or punishment escalations remain forbidden. Message blocking/deletion remains disabled.

## Capacity/TTL conflict — explicit owner decision before claiming zero loss

The encrypted journal currently holds up to **128** receipts, each expires at **60 minutes**, and the slow worker attempts **one** receipt **per minute**. The fast worker sends a new record promptly, but a record retained after a temporary outage is eligible only for the slow worker. These are independently verified current code constants, not staging observations.

Consequently, a synchronized backlog of **70** retained receipts cannot be guaranteed to drain before expiration even if the API is restored immediately: 70 sequential minute-spaced recovery attempts exceed a one-hour retention window, and failures/restarts reduce capacity further. The healthy 70-registration benchmark does **not** demonstrate 70-message outage recovery. Never assert zero message loss for sustained outages or approve retrospective deletion that assumes this.

Before broad staging acceptance, decide and document one of:

- **Bounded-loss/privacy-first policy:** Keep a 60-minute content TTL, accept that sustained/large outages can leave aliases permanently missing, and explicitly scope any eventual deletion feature to confirmed linked messages only, with visible aggregate expiry/capacity alarms.
- **Loss-minimizing recovery redesign:** Review a bounded, authenticated catch-up pathway and API load limits (e.g. a safe faster replay budget after health recovery or batch alias registration), with strict privacy boundaries and fresh real-API outage tests. Any change to persistent rate limits or retention requires owner approval and a new scoped PR.

**Recommended default:** Do not authorize retrospective cross-platform deletion as complete while this tradeoff remains unresolved.

## Gate closure

Only close Staff issue #541 / private API issue #116 when (a) exact approved staging artifact and configuration are documented, (b) real JDA/API proof exists with observed aggregate counts and expected loss policy, (c) rollback and key security are verified, (d) any required capacity/recovery redesign is independently tested, and (e) owner signs off. No unconditional production deployment follows automatically.

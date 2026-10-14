# AI mirror registration: durable reconciliation proposal (staging-only)

**Status:** design and threat model, **NOT implemented or approved for activation**. Tracks [AI-Moderation-API #116](https://github.com/wsg138/AI-Moderation-API/issues/116). The bounded volatile HTTP retries were merged in EnthusiaStaff #516; the real FastAPI pending-to-final contract tests were merged in AI-Moderation-API #119.

## Objective and non-goals

When an actual JDA post-send public Discord mirror exists but `POST /v1/mirror-aliases` cannot confirm registration in the 15-second volatile window, retain only enough authenticated evidence to retry **the same actual receipt** after an ordinary restart or transient outage. Registration cannot create an original moderation event; it only attaches the exact, independently authorized mirror alias to an already-FINAL public Minecraft event.

This plan **does not** authorize staging or production deletion, strikes, mutes, bans, moderation enforcement, classifier rollout, Discord posting, Pterodactyl config changes, or production bot restarts. No Discord API scrape or message-history reconstruction is permitted. Completeness remains unproven even after implementation.

## Hard source and trust contract

The only ingest source is `DiscordChatPublication` emitted after a successful actual JDA send. The receipt contains an actual Discord snowflake, original source canonical ID, undecorated original text, returned guild/channel, and the configured exact Discord public profile. Revalidate allowed guild/channel and staging-only config both at receipt admission and replay. Never guess a snowflake from time, username, source text, chat formatting, or a pre-send ID.

The existing private API requires `canonical_text`: it verifies `sha256(_json({"text": canonical_text}))` against the stored canonical source fingerprint. Its `mirror:register`-authenticated endpoint validates that the source is FINAL, platform and public profile/scopes match, and an existing alias is identical. **A plain text hash stored by StaffBot cannot be substituted in this existing API contract.** Do not add a fingerprint-only bypass or weaken authorization to enable recovery. The client bearer credential stays in its existing private token file and is never journaled.

Existing response contract (unchanged):

| Outcome | Handling of a durable record |
| --- | --- |
| HTTP 200 | API has confirmed an idempotent exact registration; durably remove the journal record |
| HTTP 409 + `canonical_pending` | Retry within bounded age and attempts, with backoff |
| HTTP 503 + `timeout` or `storage_unavailable` | Retry within bounds |
| IOException / HTTP timeout | Retry within bounds, since the API may already have committed |
| HTTP 404 + `canonical_missing` | **Terminal**; don't poll or invent a source, retain only aggregate failure accounting |
| HTTP 409 + `mirror_conflict` | **Terminal**; never overwrite the alias or retry blindly |
| Auth, schema, redirect, unknown code/status | **Terminal** and fail closed |

Do not retry a recorded terminal rejection on process restart. Real API successful replay is idempotent; client deduplication helps capacity only and is not the security boundary.

## Proposed opt-in data protection

A new **separately enabled, staging-only** durable outbox is proposed. Preserve the present memory-only behavior by default. Require an explicit, separately provisioned **32-byte random journal key file**, a configured isolated directory on the StaffBot volume, and an explicit TTL. Do not use the Discord bot token or mirror API bearer token as an encryption key. Do not silently create persistent mode when the key or storage is absent.

One bounded journal record contains only the schema version, created/expiry timestamps, actual JDA receipt fields (`guildId`, `channelId`, actual `discordMessageId`, `canonicalMessageId`, `canonicalPlainText`), the selected public `channelProfile`, and a non-secret immutable destination/client binding. No author identity, Discord message body decorations, punishment data, IP, staff/ticket/private messages, other channel data, token, or unrelated context.

At rest, use a proven authenticated encryption primitive (AES-256-GCM or ChaCha20-Poly1305) with fresh unique nonce, explicit version, and authenticated additional data including the destination/environment binding. File names must be independent random names, **not** raw message IDs. Validate key size and uniqueness and fail closed on tampering/invalid key. Never print decrypted records, paths with identifying information, request/response bodies, record IDs, keys, tokens, or endpoints to logs. Enforce permissions / ownership on the actual target OS; reject symlinks, untrusted traversal, insecure key files, wrong owner/ACL, and an already-locked journal. If secure storage cannot be proven on Bloom, do **not** turn this feature on.

A proposed initial safety budget for review (not a deployed default): at most **128 encrypted records**, at most **1 MiB** of journal data, and **60 minutes** maximum lifetime per receipt; no unbounded quarantine. Size/accounting includes pending temporary writes. The actual submitted HTTP request is the original authorized body, not an encrypted or redacted substitute.

## Crash consistency and runtime

The JDA callback must remain nonblocking: it offers an immutable receipt to a strictly bounded queue, never performs disk I/O, waits for an API, or reposts Discord. A dedicated bounded persistence/recovery worker should:

1. Validate source scope and admission quota before queuing.
2. Encrypt the receipt and durably persist **before the first HTTP registration attempt**. Write a temporary file exclusively, force/flush it, atomically rename within the locked directory and sync directory metadata where supported; do not claim persistence when this fails.
3. Schedule an immediate registration, sharing a strict concurrency budget with normal receipts. Preserve the existing <=4 attempts / <=15 seconds fast retry contract; after that, only coded transient failures enter slower durable recovery. No unbounded requests, delays, threads or retained message objects.
4. Recover persisted records on startup **only after** staging config, key, directory lock, destination binding and guild/channel allowlist checks pass. Expire, reject or quarantine invalid records before network I/O. Never replay to a changed endpoint, client, guild, profile, or channel authorization.
5. On HTTP 200 or a terminal status, durably remove the file; on typed temporary failures retain within TTL, quota and a separate strict slow-recovery request budget. An interrupted delete may lead to another identical idempotent request after restart, not a duplicate message.
6. On graceful shutdown, stop admission, flush already accepted persistence operations under a bounded deadline, cancel live retry timers, and leave only durably written transient records for restart. A crash before the first fsync **can** still lose a receipt; do not report this as guaranteed zero-loss.

Proposed slow recovery limits for review: one background registration per minute (separate from the fast path) and **up to eight slow attempts per record** before terminal expiration; add bounded randomized delay to avoid stampedes. A prolonged outage beyond the TTL, queue/full-disk, 404 source missing, tampered journal, unrecoverable key loss or crash before persist remain explicit observed/possible loss modes, **not silently resolved**.

Concurrency invariant: only one in-flight POST per unique (platform, scope, actual Discord snowflake, canonical ID); no early permit release during pause/close; a partial journal write must never be replayed as complete. A single process file lock protects journal ownership. Recoveries must not starve normal chat/bridge traffic.

## Required aggregation and privacy review

Expose counters for JDA receipts admitted/dropped, successfully persisted, persistence failure, pending API response, HTTP-confirmed, terminal 404, terminal identity conflict, auth/other reject, temporary timeout/unavailable, slow attempts, recovered after restart, TTL expiry, record-capacity expiry, corrupt/key/config mismatch and shutdown losses. **Only counts** are logged; no per-message identifiers or raw payloads. Aggregate count differences are diagnostics, not proof that all Discord copies are registered; crashes before callback/journal admission are fundamentally unobservable to this code.

Review the necessity of retaining public-chat original text for <=60 minutes and the operator's data-retention policy before any staging activation. Review OS ACLs, volume backups/snapshots (which may extend effective retention), key lifecycle and breach scope. No source or identity repair by fuzzy matching.

## Acceptance tests required before code can merge

- Synthetic actual JDA post-send receipt -> encrypted durable file -> API returns real `409 canonical_pending` -> test source becomes FINAL -> eventual `200`, exactly two structured aliases, zero sanctions; no Discord repost.
- Synthetic actual receipt -> force-close/kill process *after journal fsync and before API confirmation* -> new JVM instance loads same encrypted record and gets real `200` once. Repeat with an interrupted success/delete for idempotent duplicate replay.
- A source in real API storage that remains unknown `404` or conflicts `409 mirror_conflict` is never retried after restart; private/exempt/channel/profile mismatch and unauthorized attempts cannot be registered.
- Real API `503`, timeout, API-down across restart, slow recovery backoff, 15-second fast limit, TTL expiry, full quota, disk full/read-only, atomically incomplete temp files, corrupt ciphertext, key rotation/mismatch, wrong endpoint/guild/profile, symlinks, insecure permissions, concurrent second process.
- Concurrent close/pause/resume versus in-flight HTTP, crash during persist, queue saturation, and reauthorization checks: no overbooking, no duplicate Discord posts, no unbounded memory/tasks, no raw contents/IDs/tokens/endpoints in captured logs.
- Java `:staff-bot:check` plus exact-head public hosted CI; actual Java HTTP client -> disposable FastAPI+SQLite loopback; Python full pytest/Ruff if API protocol changes. No $-spending or real Discord traffic.
- Separate reviewer sign-off on the retention/crypto/OS-permissions threat model and measurable loss/recovery budget. **Even passing all tests does not authorize deletion or production deployment.**

## Implementation sequence and release gates

1. Implement isolated encryption/locking/atomic-store component with focused crash, corruption, quota and permissions tests. Keep no changes to production configs.
2. Add optional staging-only config and staged persistence worker; integrate only with the **actual** post-send observer, not chat content listeners. Preserve current publisher semantics when disabled.
3. Exercise the above cross-JVM and Java↔Python real HTTP matrix, no fake identity normalization; audit races and metrics.
4. Review, merge safely, measure actual staging coverage under separately authorized non-destructive test traffic, and only then propose a separate opt-in deletion acceptance decision based on **structured `related_messages`** alone.

Related reference files: `docs/AI-MODERATION-STAGING-MIRROR-PUBLISHER-2026-10-11.md` and `staff-bot/README.md`.

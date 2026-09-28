# EnthusiaStaff Staff Bot

This module is the standalone Java 21 Discord runtime for EnthusiaStaff. It runs beside Paper/Velocity; it is not a Minecraft plugin. This runbook describes a safe initial deployment of the functionality already merged on `main`. It does not authorize production punishment enforcement or merge any parked Discord feature package.

## Build and artifact

Build the exact source you intend to deploy:

```bash
./gradlew :staff-bot:clean :staff-bot:check :staff-bot:shadowJar --no-daemon --console=plain
```

The executable is `staff-bot/build/libs/EnthusiaStaff-StaffBot-<version>.jar` with main class `net.enthusia.staff.discordbot.StaffBotApplication`.

GitHub also publishes the fixed staging prerelease assets:

- `EnthusiaStaff-StaffBot.jar`
- `EnthusiaStaff-StaffBot.jar.sha256`
- `staff-bot-staging-source.txt`

Verify both the recorded source SHA and JAR SHA-256 before replacing a running copy.

## Safe initial launch mode

Use Java 21. For a normal runtime, configuration comes from environment variables. Do not put secrets on the Java command line.

```bash
java -jar EnthusiaStaff-StaffBot.jar
```

For initial deployment, leave `ENTHUSIA_STAFF_BOT_DISCORD_ENFORCEMENT_ENABLED=false` (or unset). This preserves the current read/identity/authorization integration without enabling Discord punishment mutations.

A staging Discord-only connectivity check can use:

```bash
java -jar EnthusiaStaff-StaffBot.jar --smoke-test
```

The smoke test connects to Discord, validates the fixed staging application/guild/channel identity fence, sends no test message, and exits nonzero if readiness is not achieved.

## Required configuration

Always required:

- `ENTHUSIA_STAFF_BOT_ENVIRONMENT`: `staging` or `production`.
- `ENTHUSIA_STAFF_BOT_TOKEN`: token for the selected fixed Discord application.

To enable the existing account-link/moderation read integration, configure this complete group together:

- `ENTHUSIA_STAFF_BOT_DB_JDBC_URL`
- `ENTHUSIA_STAFF_BOT_DB_USERNAME`
- `ENTHUSIA_STAFF_BOT_DB_PASSWORD`
- `ENTHUSIA_STAFF_BOT_AUTHORITY_URL`
- `ENTHUSIA_STAFF_DISCORD_AUTHORITY_SECRET`
- `ENTHUSIA_STAFF_BOT_COMPONENT_SECRET`

`ENTHUSIA_STAFF_BOT_AUTHORITY_URL` must target `/v1/staff-rank`. For separate Bloom/Pterodactyl splits, set `ENTHUSIA_STAFF_BOT_AUTHORITY_TRANSPORT=bloom-private-split`; loopback is the default transport when Paper authority is colocated. Authority and component secrets must each contain at least 32 characters and should be distinct.

Optional safe tuning:

- `ENTHUSIA_STAFF_BOT_HEALTH_HOST` (default `127.0.0.1`; loopback only)
- `ENTHUSIA_STAFF_BOT_HEALTH_PORT` (default `8765`; production may not use port `0`)
- `ENTHUSIA_STAFF_BOT_WORKER_THREADS` (default `4`, range `1..16`)
- `ENTHUSIA_STAFF_BOT_WORKER_QUEUE_CAPACITY` (default `256`, range `1..4096`)
- `ENTHUSIA_STAFF_BOT_INTERACTION_CAPACITY` (default `4096`, range `16..65536`)
- `ENTHUSIA_STAFF_BOT_INTERACTION_TTL_SECONDS` (default `900`, maximum 24 hours)
- `ENTHUSIA_STAFF_BOT_DB_POOL_SIZE` (default `4`, range `2..16`)
- `ENTHUSIA_STAFF_BOT_DB_TIMEOUT_MILLIS` (default `3000`, range `250..60000`)

See `runtime.env.example` for a copyable key inventory containing placeholders only.

## MariaDB and Paper authority

The Staff Bot's merged read runtime connects to the same logical EnthusiaStaff MariaDB used by the Staff platform. It opens the read surface without running Flyway; schema initialization/migration belongs to the authorized Staff/Paper deployment path, not the bot process.

Paper remains the staff-rank authority. Staff Bot authority requests are HMAC authenticated, short-lived, replay protected, and response authenticated. Discord roles are not treated as the authoritative Minecraft staff rank.

For a split deployment, keep the Paper authority listener on private/loopback networking. Do not add a public allocation for the authority port merely to make the bot connect.

Velocity is not a direct startup transport dependency of the standalone bot. Features that depend on data produced by the wider Staff platform still require that data to be current, but a Velocity restart by itself should not require restarting Staff Bot.

## Health and dependency behavior

The runtime exposes loopback-only `GET /health` and `GET /ready`.

- `/health` is process liveness. A terminal runtime failure returns `503` while the listener remains available.
- `/ready` is `200` only after the Discord identity/guild fence passes. Gateway disconnects remove readiness until a valid session is re-established.
- Discord disconnect: JDA reconnects; interactions remain unavailable until the session is revalidated.
- Invalid Discord token/application/guild: fail closed; do not keep restarting with the wrong credential.
- MariaDB unavailable during moderation-runtime startup: startup fails rather than running an apparently healthy partially configured moderation surface. Fix connectivity and let the process supervisor restart it.
- MariaDB/authority failure during a request: authorization/data operations fail closed; do not fall back to Discord roles or cached privilege guesses.
- Paper authority unavailable: staff-sensitive reads/actions must remain unavailable until authority returns.
- Optional staging tunnel failure: the staging preview path fails closed; it is not a reason to expose loopback ports publicly.

Because DB initialization currently occurs before the health listener starts, a DB-startup failure is observed through the process exit/console rather than `/health`. Pterodactyl/Bloom should therefore use process restart policy plus the HTTP readiness check after successful startup.

## Startup and recovery order

For a normal split deployment:

1. Ensure the shared MariaDB schema is current through the authorized Staff migration path.
2. Start Paper and verify its private signed staff-rank authority endpoint is available.
3. Start Staff Bot with the exact reviewed JAR and runtime secrets.
4. Verify the process remains up, then check `http://127.0.0.1:8765/health` and `/ready` from inside the container/split.
5. In staging, run the non-destructive `--smoke-test` against the candidate configuration before promoting the artifact.
6. Keep destructive Discord enforcement disabled until separately accepted and authorized.

The optional Discord punishment runtime already has database-backed work/reconciliation and restart recovery, but it is opt-in. Do not enable it simply as a launch test.

## Bloom / Pterodactyl requirements

Repository-tested staging uses an isolated Java 21 Bloom split. Keep health (`8765`), moderation preview (`8766`), and Paper authority (`8771`) private/loopback unless a documented private tunnel is explicitly in use; none needs a public game-panel allocation.

Recommended normal startup command:

```text
java -Dterminal.jline=false -Dterminal.ansi=true -jar EnthusiaStaff-StaffBot.jar
```

Set the normal runtime values through the panel's environment/secret facility. Do not paste the token, MariaDB password, HMAC secret, or component secret into startup arguments.

The D16 staging moderation-web preview has additional staging-only token/config/tunnel files and flags. Keep that separate from the normal bot launch; see `../docs/staff-bot-staging-ui-preview.md` if that preview is intentionally being operated.

## Update and rollback procedure

1. Record the currently running source SHA and JAR checksum.
2. Obtain/build the replacement JAR and verify its source metadata/checksum.
3. Run repository tests and, for staging, the non-destructive smoke test.
4. Stop Staff Bot cleanly. Do not restart Paper/Velocity solely because the bot JAR changes.
5. Replace only the Staff Bot JAR; preserve runtime secrets/configuration unless a reviewed change requires otherwise.
6. Start Staff Bot and require healthy process plus `/ready=200` before considering the update complete.
7. If startup/readiness fails, stop it, restore the previous known-good JAR, and investigate from sanitized logs. Do not weaken identity, authority, replay, or network fences to make an update start.

Normal shutdown gives JDA a graceful window before forced shutdown, closes moderation/database resources, and stops bounded workers.

## Production cutover checklist

Before the production application is actually turned on, all of these must be true:

- exact candidate artifact and checksum/source provenance are recorded;
- Java 21 runtime is selected;
- production Discord token is supplied only by the runtime secret store;
- shared MariaDB connectivity/schema and the Paper authority route are verified privately;
- `/health` and `/ready` behavior is wired into operations;
- the production bot is installed only in the fixed Enthusia guild expected by the runtime identity fence;
- `ENTHUSIA_STAFF_BOT_DISCORD_ENFORCEMENT_ENABLED` remains `false` unless destructive Discord moderation has separately passed acceptance and been explicitly authorized;
- no token, database credential, HMAC secret, private evidence, reporter data, PM content, coordinates, or staff notes appear in GitHub/console evidence.

Parked Discord packages such as cross-platform enforcement, investigations, or role-sync replacement are not prerequisites for starting the merged base Staff Bot and must not be merged merely to satisfy this checklist.

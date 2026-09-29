# Private production moderation workspace

The production Discord Staff Bot and private moderation read API run in the same Bloom Staff Bot split. A separate Cloudflare Worker serves the HTTPS workspace at `https://staff.enthusia.info`. The bot issues two-minute, one-use links from `/moderate`, **Moderate User**, and **Moderate Message**. The Worker creates a short-lived staff session and signs exact read requests to `https://moderation-read.enthusia.info`, which reaches the bot's loopback listener through a dedicated remotely managed Cloudflare Tunnel. Every read checks current HUB staff authority and Discord guild membership.

The site remains private behind signed staff launches. Visiting it directly yields HTTP 401. Punishment review in the site is simulation-only: it never sends a punishment, DM, permission change, or message deletion. Keep `discord-enforcement.enabled=false` in the private `m` configuration. This does not alter the bot's eight default-disabled staff commands except that `/moderate` may omit a target to browse the current channel and its three entry points open the web workspace.

## Deployment inputs

- Deploy `moderation-web/wrangler.production.jsonc` to the account owning `enthusia.info`, using the reviewed `moderation-web/deploy-production.ps1` script. Run it locally with `-TokenFile` pointing to the existing production bot token file. The script builds production assets, uploads only the two derived keys to the Worker, and deletes its temporary secrets file. It does not print the token or derived keys.
- Set the Worker's `LAUNCH_SIGNING_KEY_HEX` and `READ_API_SIGNING_KEY_HEX` secrets to SHA-256 digests of the production Discord bot token prefixed by the existing distinct launch and read domain separators. Never upload the raw bot token to Cloudflare. This token-derived signing is bootstrap debt; migrate both sides to dedicated random keys in one coordinated cutover.
- Create a dedicated remotely managed tunnel for `moderation-read.enthusia.info` to `http://127.0.0.1:8766` with a 404 fallback. Keep its connector token in `prod-tunnel` on the Bloom split. The production connector must not reuse the staging tunnel token.
- Keep `cloudflared`, `tp`, `m`, and `prod-tunnel` private on the split. The tunnel token file is passed to `cloudflared` with `--token-file`, not a command-line secret.
- `moderation-web/deploy-production-bloom.py` reads the existing private SFTP details and verifies the pinned Bloom host key. It installs the dedicated tunnel connector (`python moderation-web/deploy-production-bloom.py`), checks the live JAR and enforcement setting (`--audit`), or backs up and uploads the built Staff Bot JAR (`--upload-jar`). It never prints credential values.

## Bloom APP FLAGS

First run the smoke test:

```text
--environment=production --token-file=tp --moderation-config-file=m --tunnel-binary-file=cloudflared --tunnel-token-file=prod-tunnel --moderation-web-url=https://staff.enthusia.info --smoke-test
```

After `staff_bot_smoke_ready environment=production` and a clean exit, remove only `--smoke-test` and start normally. Verify `staff_bot_ready environment=production`, the eight production commands, the Worker's production health, direct HTTP 401 at `/moderation`, one successful signed launch, read data from the Bloom API, and replay rejection. Test with a nonstaff account to confirm that the commands and site reads are denied. Confirm `discord-enforcement.enabled=false` before and after the restart.

## Rollback

Remove `--moderation-web-url`, `--tunnel-binary-file`, and `--tunnel-token-file` from APP FLAGS and restart the prior production JAR. The existing read-only Discord menus return; the new Worker remains inaccessible without valid signed launches. Keep the pre-deployment JAR backup until the web launch has passed staff testing.

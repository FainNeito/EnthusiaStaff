# Frontier staff profile

Frontier's production punishment catalog retires the three market reasons and four reputation reasons. The existing names are retained for history. The active catalog is versioned independently so that drafts cannot carry a stale policy into confirmation.

Apply [reason-policies.patch](reason-policies.patch) to a copy of Frontier's installed `reason-policies.yml` at version `2026-10-10.1`. From the directory containing the copy, run `git apply --check /path/to/reason-policies.patch` followed by `git apply /path/to/reason-policies.patch`. Keep the original as the rollback copy. Only install the resulting file on Frontier and reload with `estaff reload`.

The EnthusiaStaff presentation uses the Frontier palette when `network.server-id` is `FRONTIER`. Staff and Guild Staff RoseChat tags use `#E06B52` with `#EFE5D0` text and a `#B5ACA0` separator. Those tags are configured in Frontier's RoseChat `channels.yml`.

The production loader accepted 78 active reasons, compared them with the original catalog, and resolved all seven retired IDs only as history descriptors. Bloom accepted the Frontier-only reload on November 25, 2026. Policy v2 was disabled in that runtime. The RoseChat color change was reloaded on Frontier. The EnthusiaStaff Java changes require a separate plugin rollout and in-game check.

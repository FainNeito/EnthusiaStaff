# Staff message theme — SPEAR evidence

## Specification
ED-STAFF-THEME-01: Staff chat output using the shared Paper and Velocity message styles should use the established warm gold, cream and copper palette while preserving messages, click actions, severity semantics, permissions and other behavior.
ED-STAFF-THEME-02: The active-ban login screen should use the prior LiteBans presentation where supported by authoritative data, with website colors and current secure appeal code. Do not disclose case IDs, internal punishment codes or an unverified issuer identity. Name comes from the login player; dates and reason come from the active sanction.
ED-STAFF-THEME-03: Existing externally configured or independently styled messages are not silently overwritten; identify further paths before claiming coverage.

## Evidence and boundaries
The starting source is origin/main fe015d97. The old LiteBans template was obtained from the previously captured message-patches.json before-value, not inferred from a screenshot. The ActiveSanction record provides target UUID, reason and issued/expiry dates but not a verified issuer name; the issuer and old LiteBans punishment ID are deliberately omitted. Public appeal codes continue to follow existing availability policy. No production files were read or changed for this patch; provider, loaded artifact, actual client rendering and coverage of independent messages remain unverified. No project EARS/state helper found in the scoped review; this record documents the scaled SPEAR process.

Refinement: public punishment announcements now use gold/crimson/cream and review-request alerts use gold/cream/copper; their existing public/private boundaries, wording and click action remain unchanged. The complete Paper and Velocity unit-test tasks passed after updating the preexisting color expectation. In-game and current live-config acceptance remain open.

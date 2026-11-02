# Website account route reconciliation

Base: EnthusiaStaff main 73a92a46. The website expects signed `/v1/website/accounts` and `/v1/website/accounts/link`; these routes are absent on that base. A signed-in website user reported `competition_identity_unavailable`. This source mismatch is a plausible cause, not proof of the installed proxy's loaded code or configuration.

Requirement: a request that has passed the existing loopback, bearer and HMAC authentication may retrieve only current Minecraft identities owned by the requested Discord user, or issue/poll that user's existing in-game linking challenge. Reject malformed inputs, wrong owners, expired links and unexpected query parameters. Preserve existing account ownership, unlink, migration and moderation behavior.

Implementation: reconcile the previously prepared source from 0fc7fcd3 without replacing current Staff changes. The website derives the Discord identity from its authenticated session; browser input must not provide it. The server's existing authenticator runs before router dispatch. Account queries return bounded identity data and retain the existing Discord-to-Minecraft challenge lifecycle.

Verification: offline Java 25 `:velocity:test` passes 249 tests with one platform-dependent symlink test skipped on Windows. Focused route tests cover lookup, challenge creation and malformed inputs. The current MariaDB runtime, signed website-to-proxy integration and an actual player's link remain unverified. No production files or settings changed.

SPEAR: no project-local EARS validator or phase-state helper was identified. This record captures the scoped requirement, existing-source baseline, changes, contract review and verification; no historical failing test is claimed. A real database/provider test, independent review, hosted checks and canonical release remain necessary before activation.

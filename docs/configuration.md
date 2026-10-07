# Configuration

EnthusiaStaff configuration is being migrated toward a versioned, modular configuration model. The migration is tracked by issue #425.

Current production behavior remains the compatibility baseline. A setting becoming configurable must not weaken authorization, transaction safety, privacy boundaries, recovery, audit integrity, or concurrency guarantees.

## Current versioned configuration families

The Paper runtime currently validates these configuration families:

| File | Version metadata | Purpose |
| --- | --- | --- |
| `config.yml` | `config-version` | Core Paper/runtime settings, including reloadable and restart-owned sections |
| `reason-policies.yml` | `version` | Current punishment reason-policy definitions |
| `messages.yml` | `schema-version` | Player/staff-facing message catalog; C1 initially covers `/estaff` operator responses |
| `reports.yml` | `version` | Report policy |
| `gui/reports.yml` | `version` | Report GUI presentation |
| `policy-v2.yml` | `schema-version` plus versioned policy snapshots | Policy v2 publication/shadow configuration |

Additional files will join this validation surface as the configurability migration proceeds.

## Validate without applying

Use:

```text
/estaff config validate
```

This parses and validates the registered versioned configuration files and reports their versions. It is read-only: it does not publish a candidate, reload a subsystem, or mutate runtime state.

A failed file does not prevent the command from validating independent files, so one run can report useful information about the rest of the tree.

The command uses the existing `enthusiastaff.reload` authorization boundary.

## Reload

These commands use the same existing safe reload coordinator:

```text
/estaff reload
/estaff config reload
```

`/estaff config reload` is an alias, not a second reload implementation.

The existing coordinator validates a candidate before publication, rejects restart-owned changes, preserves the previous runtime on validation failure, and performs the existing rollback/reconciliation behavior for reloadable subsystems.

`messages.yml` participates in that same reload chain. A message candidate is parsed and validated before the existing reload runs, then published as one immutable snapshot only after the delegated reload succeeds. If message validation or the delegated reload fails, the previous message catalog remains active.

## Message configuration

C1 introduces a strict `messages.yml` catalog and begins migration with the bounded `/estaff` command family. Shipped defaults reproduce the current wording.

Message templates currently use plain text plus explicitly declared placeholders such as `{label}` and `{operations}`. Every configured key is required, unknown keys are rejected, and each template must use exactly the placeholder set defined for that message. Placeholder values are inserted literally.

This first phase intentionally does **not** enable arbitrary MiniMessage click, hover, or run-command markup. Existing Adventure/`StaffMessageStyle` presentation remains responsible for colors and severity. Rich configurable formatting can be added later behind an explicit safety/validation contract rather than silently changing message behavior during the initial migration.

Internal audit/security log messages and raw runtime diagnostic details are not automatically operator-editable merely because ordinary chat responses become configurable.

## Restart-owned settings

Not every setting is safe to rebuild while a server is running. Settings that own process/runtime resources remain restart-required until that subsystem has an explicit safe replacement lifecycle.

A reload must never claim that a restart-owned change was applied.

## Migration direction

The staged migration is:

1. shared validation/version/reload foundation;
2. centralized messages;
3. rank capabilities;
4. Staff Mode profiles/tools;
5. Policy v2 punishment configuration;
6. punishment GUI presentation;
7. remaining GUIs and integrations.

Policy and presentation remain separate. Security/correctness invariants remain code-owned.

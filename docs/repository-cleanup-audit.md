# Repository Cleanup Audit

_Status: initial audit started 2026-10-07. No destructive cleanup is authorized merely by this document._

## Why this exists

EnthusiaStaff grew through many parallel implementation, review, staging, migration, and AI-worker phases. Useful evidence is mixed with current documentation and active source, which makes the repository look less finished than the live plugin actually is.

The cleanup should reduce clutter without destroying migration history, audit evidence, ADRs, active worker contracts, or files still consumed by CI.

## Initial observations

### Root-level current files

Keep obvious project entry points at the root:

- `README.md`
- `ENTHUSIASTAFF-GOALS.md`
- `UPGRADE-MANIFEST.md` while it remains operationally useful
- Gradle/settings files
- source modules
- `docs/`, `scripts/`, `tools/`, `validation/`

Review whether these need to remain root-level or become historical/coordination material:

- `WORKSPACE-MANIFEST.md`
- `reports/`
- `ai-agents/`
- legacy workspace-era instructions that describe a multi-repo layout no longer matching the current checkout.

Do not move them until references from workflows/scripts/current issues have been checked.

### Documentation

The current docs tree contains a mixture of:

- current operator/developer documentation;
- current wiki source;
- explicit `docs/wiki/legacy/` snapshots;
- old migration/cutover assumptions;
- historical "pre-release" language;
- active feature-specific development notes.

Historical snapshots should remain historical. Current-facing docs should describe the plugin as live and identify unfinished features individually.

### Reports and agent coordination

`reports/` and `ai-agents/` contain substantial historical validation and worker state. They may be valuable evidence, but they should not dominate normal project navigation.

Candidate end state:

```text
docs/
  current/ or normal topic docs
  history/
    handoffs/
    old completion audits/
    superseded coordination records/
```

Whether files are moved or simply indexed/marked historical should be decided after reference analysis.

### Workflows

There are multiple staging, diagnostic, Codacy, artifact, and wiki workflows. Some were intentionally temporary during earlier validation campaigns.

Before removing any workflow:

1. search current docs/issues for references;
2. inspect recent GitHub runs/use;
3. verify no active PR/package depends on it;
4. remove in a dedicated cleanup PR with CI coverage.

### Bundled components

`components/` is intentional source aggregation, but it increases visual size and can make the repo look duplicated. Cleanup should document why each component is present, its source/ownership, and whether it is authoritative, mirrored, or transitional.

Do not delete bundled provider source just to make the tree smaller.

## Stale-status cleanup

Current-facing files should no longer make global claims such as:

- "the project is pre-release";
- "there is no production EnthusiaStaff deployment";
- "LiteBans remains the current production authority";

unless the statement is explicitly historical or feature-specific.

Do not blindly replace those phrases in:

- dated handoffs;
- historical reports;
- ADRs describing the decision at the time;
- legacy wiki snapshots;
- old package records.

Those are records, not current status pages.

## Planned follow-up

- build a reference map for `ai-agents/`, `reports/`, root manifests, and temporary workflows;
- identify zero-reference/superseded candidates;
- propose archive/move/delete operations in a separate PR;
- reconcile stale GitHub issue acceptance language;
- add a concise documentation index once the physical layout stabilizes.

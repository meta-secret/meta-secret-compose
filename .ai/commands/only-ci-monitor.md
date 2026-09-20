---
description: Run Stage 11 Compose CI monitoring for a completed workflow run
---

# Command — Only CI Monitor

Read `.ai/skills/ci-monitoring/SKILL.md`, `.ai/rules/ci-monitoring.md`, and
`.ai/agents/ci-monitor.md`. Inspect the supplied GitHub Actions run by ID or
URL, bind evidence to its exact head SHA, redact and bound failed logs, classify
the outcome, and write:

`.ai/artifacts/run/MS-<run-id>-011-ci-monitor.md`

This command is read-only. The separate `cursor-fix.yml` workflow is the only
component allowed to invoke guarded Cursor remediation.

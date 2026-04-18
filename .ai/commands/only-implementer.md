---
description: Run code-implementer only — Agent mode, formatted summary, next-step hints.
---

# Only implementer

Arguments: approved plan text or path context. Example: `/only-implementer <approved plan>`

Delegate to subagent **code-implementer** with input: `$ARGUMENTS`

## Session mode

- **Use Agent mode** — implementation requires **Write** / **Edit** on source files.
- **Scope:** Implement only what the user has **already approved** in a written plan (or explicit narrow instruction).

## Presentation (required)

When reporting results to the user:

1. Use **Markdown** with **emoji section headers** (examples: layers for shared vs platform code, wrench for key edits, phone for platform adapters).
2. **Bold** file paths and public API changes; use bullet lists for behavioral changes.
3. Summarize **what changed** and **what was left out** if scope was trimmed.

## Next steps — pick a command

- If workspace root is **MetaSecret**, use **`/compose-only-*`**; if only **meta-secret-compose**, use **`/only-*`**.

| Slash (MetaSecret) | Slash (repo only) | What it does |
|--------------------|-------------------|--------------|
| `/compose-only-test-author` | `/only-test-author` | Add or extend tests for the change |
| `/compose-only-test-verifier` | `/only-test-verifier` | Run Gradle / KMP tests and report pass/fail |
| `/compose-only-reviewer` | `/only-reviewer` | Review the diff for architecture/style |
| `/compose-only-debug-rca` | `/only-debug-rca` | If build or tests fail unexpectedly |

Typical next step: **`/compose-only-test-author`** or **`/compose-only-test-verifier`** (MetaSecret), or **`/only-test-author`** / **`/only-test-verifier`** (repo root).

See [WORKFLOW.md](../WORKFLOW.md).

---
description: Run test-author only — Agent mode, formatted summary, next-step hints.
---

# Only test author

Arguments: what to cover (plan snippet, file list, or changed behavior). Example: `/only-test-author Add tests for use case X`

Delegate to subagent **test-author** with input: `$ARGUMENTS`

## Session mode

- **Use Agent mode** — adding or changing test files requires **Write** / **Edit**.
- **Yes:** this command is for **writing or updating automated tests** (not running the full suite — use test-verifier for that).

## Presentation (required)

When reporting results to the user:

1. Use **Markdown** with **emoji section headers** (examples: test tube for new tests, layers for common vs platform tests).
2. **Bold** test file paths and covered scenarios; list **edge cases** briefly.
3. Note any **skipped** or **deferred** tests and why.

## Next steps — pick a command

- If workspace root is **MetaSecret**, use **`/compose-only-*`**; if only **meta-secret-compose**, use **`/only-*`**.

| Slash (MetaSecret) | Slash (repo only) | What it does |
|--------------------|-------------------|--------------|
| `/compose-only-test-verifier` | `/only-test-verifier` | Run Gradle / KMP tests and report results |
| `/compose-only-reviewer` | `/only-reviewer` | Review tests + code together |
| `/compose-only-debug-rca` | `/only-debug-rca` | If tests fail or are flaky |

Typical next step: **`/compose-only-test-verifier`** (MetaSecret) or **`/only-test-verifier`** (repo root).

See [WORKFLOW.md](../WORKFLOW.md).

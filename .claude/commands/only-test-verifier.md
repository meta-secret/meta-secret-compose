---
description: Run test-verifier only — Agent mode (Bash/tests), formatted report, next-step hints.
---

# Only test verifier

Arguments: optional scope (module, task). Example: `:composeApp:testDebugUnitTest` or as documented in PROJECT_CONTEXT.

Delegate to subagent **test-verifier** with input: `$ARGUMENTS`

## Session mode

- **Use Agent mode** (or any mode that allows **Bash**) — running **Gradle / KMP** test tasks requires command execution, not Plan-only.
- This phase is **verification**: run tests and report pass/fail; it is **not** the same as writing tests (**test-author**).

## Presentation (required)

When presenting results:

1. Lead with a **short summary** (emoji ok: pass/fail overall).
2. Put **command lines** and **long log excerpts** in **fenced code blocks**; keep emoji mainly in the summary, not inside raw build output.
3. **Bold** failing module/test name; bullet **actionable** next checks if red.

## Next steps — pick a command

- If workspace root is **MetaSecret**, use **`/compose-only-*`**; if only **meta-secret-compose**, use **`/only-*`**.

| Slash (MetaSecret) | Slash (repo only) | What it does |
|--------------------|-------------------|--------------|
| `/compose-only-debug-rca` | `/only-debug-rca` | If failures need root-cause analysis |
| `/compose-only-planner` | `/only-planner` | If failures imply a design change |
| `/compose-only-implementer` | `/only-implementer` | If a small code fix is enough |
| `/compose-only-release-notes` | `/only-release-notes` | If tests are green and you want MR text |

Typical next step if **red**: **`/compose-only-debug-rca`** or **`/compose-only-implementer`**. If **green**: **`/compose-only-reviewer`** or **`/compose-only-release-notes`**.

See [WORKFLOW.md](../WORKFLOW.md).

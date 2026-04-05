# AI workflow (agents, phases, approvals)

This document describes how to run the **multi-phase delivery pipeline** with **human approval** after each phase, and how to invoke **individual subagents** at any time without the full chain.

**Canonical project rules:** [CLAUDE.md](CLAUDE.md), [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md), [ARCHITECTURE.md](ARCHITECTURE.md), [SECURITY.md](SECURITY.md), [CODE_STYLE.md](CODE_STYLE.md).

## Subagent definitions

| Role | Subagent name (invoke by name) | Purpose |
|------|----------------------------------|---------|
| GitHub issue fetch + Summary | `only-issue-coordinator` command + `github-issue-coordinator` agent ([`.claude/agents/github-issue-coordinator.md`](.claude/agents/github-issue-coordinator.md)) | Load issue via `gh`, summarize, list next steps |
| Plan only | `feature-planner` | Structured plan, no code |
| Implement | `code-implementer` | Code per approved plan |
| Tests | `test-author` | Add/update tests |
| Run tests | `test-verifier` | Gradle test report |
| Debug / RCA | `debug-rca` | Root cause, no repo writes by default |
| Review | `code-reviewer` | Architecture/style findings |
| Release notes | `release-notes` | MR/changelog text, no git |
| Release / MR | `release-manager` | Branch from `main`, commit/push only after explicit user ok |
| Pattern → skill/command (optional) | `workflow-pattern-capture` | 0–2 durable suggestions or “no change”; not every MR |

Files: [`.cursor/agents/`](.cursor/agents/) and [`.claude/agents/`](.claude/agents/) (same prompts; frontmatter may differ).

## Two entry points (same pipeline after planning)

| Entry | First phase | Artifact before your approval |
|-------|-------------|--------------------------------|
| **GitHub issue** (number or URL) | `/only-issue-coordinator <n|URL>` (or MetaSecret **`/compose-only-issue-coordinator`**) → **Summary** approval → **`/only-planner`** or `feature-planner` (your next step) | Issue summary (title, description, acceptance) |
| **Manual prompt** (feature or bug description) | `/only-from-prompt` (or MetaSecret **`/compose-only-from-prompt`**) or use skill `workflow-manual-task-brief` then `feature-planner` | Task brief + plan |

After the first approved plan, the pipeline is identical.

## Phased pipeline (default order)

1. **Context** — issue path: coordinator output; manual path: your task brief + planner.
2. **Plan** — `feature-planner` → you approve.
3. **Implement** — `code-implementer` → you approve diff.
4. **Tests** — `test-author` → you approve test diff.
5. **Verify** — `test-verifier` → you review pass/fail stats.

**If tests fail or build fails:** `debug-rca` → approve → back to **Plan** (`feature-planner`) → **Implement** → **Tests** → **Verify** (loop until green).

**Optional (after Gradle / KMP build failure or unclear shared-code errors):** run skill **`kmp-doctor`** for Kotlin Multiplatform / Gradle diagnostics before or alongside narrow RCA.

**Optional (after iOS runtime / device / simulator / Xcode issues):** run skill **`ios-device-doctor`** when the failure is platform-side (linking, signing hints, device logs)—still no substitute for human review of secrets and team settings.

**If verify is green:** check build; if build errors, treat like failure branch (debug → plan → …).

**If green:** `code-reviewer` → if must-fix items → back to **Plan** → **Implement** (and tests as needed).

**If review ok:** `release-notes` (draft MR body) → approve → `release-manager` (branch from `main`, **commit and push only after explicit “ok”**, MR via `glab` when available).

**Optional — pattern capture (not every MR):** when a **trigger** applies—large feature, **new** error class, **same** review correction **three or more** times, or **toolchain/stack** change—run **`workflow-pattern-capture`** (skill **`workflow-pattern-capture`**) after `code-reviewer` or after `release-notes`. Output is **0–2** concrete proposals (new or extended skill, slash command, Cursor rule, or—only if justified—Claude hook for security/session enforcement) **or** explicit **No changes recommended**. Skip for trivial fixes.

## Approval rule

After **every** phase, require a clear **artifact** (summary, plan, diff, test report, review notes) and **your explicit approval** before starting the next phase. Do not skip approval for “small” changes unless you explicitly choose to.

## Standalone invocation (no chain)

You can invoke **any** subagent alone with a direct prompt (logs, files, partial context):

- **Claude Code:** delegate to the named subagent or use slash commands under [`.claude/commands/`](.claude/commands/) (`/only-planner`, `/only-implementer`, etc.).
- **Cursor:** in Agent chat, use `/subagent-name` or natural language (“use the feature-planner subagent to …”) per [Cursor subagents](https://cursor.com/docs/subagents). See [`.cursor/commands/README.md`](.cursor/commands/README.md) for parity.

## Skills (templates)

| Skill folder | Use |
|--------------|-----|
| `workflow-issue-handoff` | Build **Summary** after `gh issue view` (or `glab issue view`) |
| `workflow-manual-task-brief` | Structure a manual task before planning |
| `workflow-plan-output` | Plan shape; aligns with `write-implementation-plan` |
| `workflow-mr-body` | MR title/body checklist |
| `kmp-doctor` | Optional: Gradle / KMP build diagnostics after failures |
| `ios-device-doctor` | Optional: iOS runtime / device / Xcode diagnostics |
| `workflow-pattern-capture` | Optional: repeating patterns → skill/command/rule/hook; cap 0–2 |

Paths: [`.claude/skills/`](.claude/skills/).

## Tool limits

- **Cursor:** subagents do not nest; run phases sequentially.
- **Claude Code:** subagents do not spawn subagents; chain from the **main** session or run one phase per command.

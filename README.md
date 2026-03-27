This is a Kotlin Multiplatform project targeting Android, iOS.

* `/composeApp` is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - `commonMain` is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    `iosMain` would be the right folder for such calls.

* `/iosApp` contains iOS applications. Even if you’re sharing your UI with Compose Multiplatform, 
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

## AI-assisted development

This repository defines a **phased workflow** (plan → implement → test → verify → review → release) with **human approval** between phases. Canonical rules live in markdown at the repo root; agents and skills automate the same discipline.

### Read first (everyone)

| Document | Purpose |
|----------|---------|
| [CLAUDE.md](CLAUDE.md) | How AI tools should behave in this repo (short index). |
| [WORKFLOW.md](WORKFLOW.md) | Full pipeline: agents, approvals, optional steps, skills table. |
| [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) | Product scope, platforms, build commands. |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Layers, MVVM, FFI boundary. |
| [SECURITY.md](SECURITY.md) | Secrets, logging, permissions. |
| [CODE_STYLE.md](CODE_STYLE.md) | Kotlin/Swift conventions. |

Skills (templates and playbooks) live under [`.claude/skills/`](.claude/skills/). Subagent prompts are mirrored in [`.cursor/agents/`](.cursor/agents/) and [`.claude/agents/`](.claude/agents/).

---

### Claude Code

1. **Open this repo** in Claude Code so it picks up [`.claude/`](.claude/).
2. **Slash commands** are defined in [`.claude/commands/`](.claude/commands/). Use them from the chat input.

**Start a full delivery chain**

| Command | When |
|---------|------|
| `/workflow-from-issue` | You have a GitLab issue number or URL (`glab` available). |
| `/workflow-from-prompt` | You only have a free-text feature/bug description. |

Each command file lists the exact steps (task brief or issue handoff → pause for approval → `feature-planner` → rest of pipeline per [WORKFLOW.md](WORKFLOW.md)).

**Run a single phase** (no full chain)

| Command | Phase |
|---------|--------|
| `/only-issue-coordinator` | GitLab issue summary |
| `/only-planner` | Plan only (`feature-planner`) |
| `/only-implementer` | Implement approved plan |
| `/only-test-author` | Add/update tests |
| `/only-test-verifier` | Run tests / interpret report |
| `/only-debug-rca` | Debug / root cause |
| `/only-reviewer` | Code review (read-only) |
| `/only-release-notes` | MR / changelog text |
| `/only-release-manager` | Branch, commit, push (only after explicit ok) |
| `/only-workflow-pattern-capture` | Optional: suggest 0–2 process improvements (skills/commands/rules/hooks) |

3. **Approval:** After each phase, confirm the artifact in chat before asking for the next step. Do not chain subagents inside subagents—run phases from the **main** session or one command at a time.

4. **Optional diagnostics** (when builds or iOS runtime fail): use skills `kmp-doctor` and `ios-device-doctor` as described in [WORKFLOW.md](WORKFLOW.md).

---

### Cursor

Cursor does **not** load `.claude/commands/` as slash commands. Use **Agent** chat with subagents and natural language; parity is documented in [`.cursor/commands/README.md`](.cursor/commands/README.md).

1. **Rules:** [`.cursor/rules/`](.cursor/rules/) (for example `ai-project-context.mdc`) pulls the same canonical markdown documents so Agent context matches the project.

2. **Invoke a phase** — either:
   - Type **`/subagent-name`** if your Cursor build supports subagent shortcuts (see Cursor docs), **or**
   - Write explicitly, e.g. “Use the **feature-planner** subagent: …”

   Subagent definitions: [`.cursor/agents/`](.cursor/agents/).

3. **Mirror Claude’s “only X” commands:** see the table in [`.cursor/commands/README.md`](.cursor/commands/README.md) (same intents as `/only-planner`, `/only-implementer`, etc.).

4. **Skills:** Cursor does not auto-load `.claude/skills/` by name. When you need a template (e.g. `workflow-manual-task-brief`, `workflow-plan-output`), ask Agent to **read** the `SKILL.md` under [`.claude/skills/<name>/`](.claude/skills/) and follow it.

5. **Limits:** Subagents do not nest; run phases **sequentially**. Respect `readonly` / plan-style agents: they output text only unless you switch to a normal edit session.

6. **Same pipeline:** Follow the order and optional branches in [WORKFLOW.md](WORKFLOW.md) (GitLab path vs manual path, then plan → implement → tests → verify → review → release notes → release manager).

---

### Optional: capture repeating patterns

When a **trigger** applies (large change, new error class, same review feedback ≥3×, toolchain change), run subagent **`workflow-pattern-capture`** with skill **`workflow-pattern-capture`** ([WORKFLOW.md](WORKFLOW.md)). Expect **0–2** concrete suggestions or **No changes recommended**—not every MR.

---

### Historical note

Older notes may refer to `docs/ai-skills.md`. The **current** entry points are this section and [WORKFLOW.md](WORKFLOW.md).

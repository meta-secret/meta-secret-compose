# meta-secret-compose

🎯 **KMM (Kotlin Multiplatform Mobile) application** for iOS and Android UI

Features: **Jetpack Compose** (Android) + **SwiftUI** (iOS) + **Shared Kotlin** logic

---

## 📖 Documentation

### For AI/Development Workflow
👉 **Start here:** [`.ai/README.md`](.ai/README.md)

- [ORCHESTRATOR.md](.ai/ORCHESTRATOR.md) — How agents orchestrate
- [INDEX.md](.ai/INDEX.md) — Full resource map
- [Commands](.ai/commands/README.md) — All available `/only-*` commands

### For Developers
- Rules: [`.ai/rules/`](.ai/rules/) — Kotlin/Swift style + KMM principles
- Skills: [`.ai/skills/`](.ai/skills/) — KMP Doctor, iOS Device Doctor, debugging

### For IDE Setup
- **Claude Code:** Read [`.claude/INDEX.md`](.claude/INDEX.md)
- **Cursor:** See [`.cursor/rules/00-entry.mdc`](.cursor/rules/00-entry.mdc) (auto-applies)
- **Codex CLI:** Read [`.codex/INDEX.md`](.codex/INDEX.md)

---

## 🚀 Quick Start

### Plan a Feature
```bash
/only-planner "add dark mode toggle"
```

### Implement
```bash
/only-implementer
```

### Test & Review
```bash
/only-test-author "your feature"
/only-test-verifier
/only-reviewer
```

---

## 📦 Project Structure

```
meta-secret-compose/
├── composeApp/          ← Shared KMM module
│   ├── commonMain/      ← Shared Kotlin code
│   ├── androidMain/     ← Android-specific (Compose)
│   ├── iosMain/         ← iOS-specific (SwiftUI)
├── iosApp/              ← iOS app entry point
├── .ai/                 ← AI automation brain (NEW)
│   ├── ORCHESTRATOR.md  ← Brain (agents, commands, flow)
│   ├── INDEX.md         ← Resource map
│   ├── agents/          ← AI personas
│   ├── commands/        ← Slash commands
│   ├── skills/          ← Reusable workflows
│   ├── rules/           ← Architecture & style
│   └── artifacts/       ← Generated outputs (git-ignored)
├── gradle/              ← Build configuration
└── docs/                ← Project documentation
```

---

## 🛠 Build & Run

### Android
```bash
./gradlew :composeApp:installDebug
```

### iOS
```bash
cd iosApp && xcodebuild -scheme iosApp
```

### Tests
```bash
./gradlew :composeApp:testDebugUnitTest
```

---

## 🤖 AI Workflow

This project uses **PlayPal AI pattern** for development automation:

1. **Plan** — `/only-planner` creates implementation plan
2. **Implement** — `/only-implementer` writes code
3. **Test** — `/only-test-author` + `/only-test-verifier`
4. **Review** — `/only-reviewer` checks quality
5. **Repeat** — Loop until done

All agents defined in [`.ai/agents/`](.ai/agents/) — see each agent’s `.md` file for details.

---

## 📚 Key Resources

| Resource | Purpose |
|----------|---------|
| [`.ai/ORCHESTRATOR.md`](.ai/ORCHESTRATOR.md) | Brain (how things run) |
| [`.ai/INDEX.md`](.ai/INDEX.md) | Full index (find anything) |
| [`.ai/rules/kmp-principles.md`](.ai/rules/kmp-principles.md) | KMM architecture |
| [`.ai/skills/kmp-doctor/`](.ai/skills/kmp-doctor/) | KMP troubleshooting |
| [`.ai/skills/ios-device-doctor/`](.ai/skills/ios-device-doctor/) | iOS issues |

---

## 🔗 Related

- **Parent:** MetaSecret routing layer (for multi-repo coordination)
- **Sister project:** meta-secret-core (Rust crypto, separate)
- **Architecture:** MVVM + Coordinator pattern

---

✅ **Ready to code.** Start with [`.ai/README.md`](.ai/README.md) or run `/only-planner "your task"`.
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

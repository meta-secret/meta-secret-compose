# AI Automation — meta-secret-compose

🤖 **Automated 9-stage workflow** for issue → PR automation.

Works with **Claude Code**, **Cursor**, and **OpenAI Codex CLI**.

This is the **KMM/iOS/Android UI layer** of MetaSecret. Crypto protocol logic lives in `meta-secret-core/`.

---

## 🚀 Quick Start

### In Claude Code

```bash
/run issue 123                # Full pipeline (9 stages)
/run issue "my custom task"   # Use text instead
```

### In Cursor

```
Cmd+K: run issue 123
```

### OpenAI Codex CLI

```bash
codex run issue 123
```

**All three execute the same 9-stage workflow:**
1. Understanding (read issue)
2. Planning (create plan)
3. Implementation (write code)
4. Testing (write tests)
5. Build (compile with auto-retry)
6. Test Run (execute tests with auto-retry)
7. Code Review (review with auto-retry)
8. Commit (git commit + push)
9. PR (create pull request)

**Output:** All artifacts in `.ai/artifacts/run/MS-<timestamp>/`

---

## 📂 What's Here

| Folder | Purpose |
|--------|---------|
| **agents/** | 10 AI personas (planner, implementer, reviewer, tester, etc.) |
| **commands/** | Slash commands for Claude Code and Codex CLI |
| **skills/** | Reusable workflows (KMP, iOS, debugging, testing, release) |
| **rules/** | KMM architecture + Kotlin/Swift style guides |
| **artifacts/** | Generated outputs (git-ignored) |

---

## 🔗 How It Works

Each IDE has **explicit entry points** (no symlinks):

```
.claude/INDEX.md ──→ Reads from .ai/
.cursor/rules/00-entry.mdc ──→ Reads from .ai/
.codex/INDEX.md ──→ Reads from .ai/
```

**Single source of truth:** All agents, commands, rules defined once in `.ai/`  
**Edit once, works everywhere.** ✅

---

## 📖 Full Documentation

**Start here:**
1. **AGENTS.md** — Workflow overview (9 stages, entry point)
2. **WORKFLOW.md** — Orchestration brain  
3. **PIPELINE.md** — Detailed stage specifications

**Then read:**
- **agents/** — Individual agent definitions
- **rules/** — Architecture and style rules
- **skills/*/SKILL.md** — Reusable helper skills

---

## 📁 Folder Structure

```
.ai/
├── AGENTS.md              ← Read first! (overview)
├── WORKFLOW.md            ← Main orchestration
├── PIPELINE.md            ← Stage specifications
├── agents/                ← 10 agent definitions
├── rules/                 ← Architecture & style
├── skills/                ← Helper workflows
├── artifacts/run/         ← Generated output
└── commands/              ← Slash commands
```

---

## 📚 Key Resources

| File | Purpose |
|------|---------|
| **AGENTS.md** | Workflow overview & entry point |
| **WORKFLOW.md** | Orchestration logic |
| **PIPELINE.md** | Stage-by-stage details |
| **agents/*.md** | Individual agent definitions |
| **rules/kmp-principles.md** | Architecture rules |
| **skills/*/SKILL.md** | Helper skills |

---

## 🔗 IDE-Specific Entry Points

| IDE | File | Command |
|-----|------|---------|
| **Claude Code** | `.claude/ORCHESTRATE.md` | `/run issue 123` |
| **Cursor** | `.cursor/WORKFLOW.md` | `Cmd+K: run issue 123` |
| **Codex CLI** | `.codex/ORCHESTRATE.md` | `codex run issue 123` |

---

## 🎯 Project Context

- **Type:** KMM (Kotlin Multiplatform Mobile)
- **Platforms:** iOS (SwiftUI) + Android (Jetpack Compose)
- **Architecture:** MVVM + Coordinator pattern
- **Layer:** UI layer only (crypto in meta-secret-core)

---

✅ **IDE Support:** Claude Code • Cursor • OpenAI Codex CLI  
🏗️ **Workflow:** 9-stage automated pipeline with auto-retry  
📅 **Last updated:** 2026-04-20  
🚀 **Status:** Production-ready

# AI Automation — meta-secret-compose

🤖 Unified AI agents, commands, and rules for **Claude Code**, **Cursor**, and **OpenAI Codex CLI**.

This is the **KMM/iOS/Android UI layer** of MetaSecret. Crypto protocol logic lives in `meta-secret-core/`.

---

## 🎯 Quick Start

### In Claude Code

```bash
/help                      # List all commands
/only-planner "your task"  # Plan feature
/only-implementer          # Implement from plan
/only-reviewer             # Review code
```

### In Cursor

- Cursor loads `.cursor/rules/00-entry.mdc` automatically
- Rules auto-discovered from `.ai/rules/`
- Use in custom rules or inline chat

### OpenAI Codex CLI

```bash
codex --agent feature-planner --context "add dark mode"
codex --agent code-reviewer --context "$(cat changes.diff)"
codex --rule kmp-principles
```

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

See **ORCHESTRATOR.md** for:
- Complete architecture
- All agents & responsibilities
- Workflow patterns
- Failure detection

See **INDEX.md** for:
- Full resource directory
- Find things by task type
- Getting started checklist

---

## 🚀 Common Tasks

### Plan a feature
```bash
/only-planner "add dark mode toggle"
```
→ Creates `implementation-plan.md`

### Implement from plan
```bash
/only-implementer
```
→ Writes code + `implementer.md`

### Review code
```bash
/only-reviewer
```
→ Creates `review-report.md`

### Write & verify tests
```bash
/only-test-author "test dark mode"
/only-test-verifier
```
→ `test-report.md`

### Debug a KMP issue
Use skill **kmp-doctor** for build diagnostics

### Debug iOS device issue
Use skill **ios-device-doctor** for device/simulator problems

### Root-cause analysis
```bash
/only-debug-rca
```
→ Uses skill **systematic-debugging**, creates `rca-report.md`

### Generate UniFFI bindings
```bash
/only-generate-uniffi
```
→ Pulls bindings from `meta-secret-core`

---

## 📚 Resources

| Resource | Purpose |
|----------|---------|
| **ORCHESTRATOR.md** | Brain (what runs what) |
| **INDEX.md** | Full resource directory |
| **commands/README.md** | All commands listed |
| **agents/*.md** | Agent definitions |
| **skills/*/SKILL.md** | Skill documentation |
| **rules/RULES.md** | Architecture & style rules |

---

## 🔗 Parent Context

- **MetaSecret root** — Routing layer (smart `/route` command)
- **meta-secret-core** — Crypto/protocol (separate, independent)
- **.claude/INDEX.md** — Claude Code entry point
- **.cursor/rules/00-entry.mdc** — Cursor entry point
- **.codex/INDEX.md** — Codex CLI entry point

---

✅ **IDE Support:** Claude Code • Cursor • OpenAI Codex CLI  
🏗️ **Architecture:** MVVM + Coordinator (KMM)  
📅 **Last updated:** 2026-04-18  
🚀 **Status:** Production-ready

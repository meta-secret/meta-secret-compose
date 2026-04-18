# meta-secret-compose AI Orchestrator

🎯 **Master orchestration for KMM/iOS/Android UI development**

Single source of truth for AI automation across Claude Code, Cursor, and OpenAI Codex CLI.

---

## 📦 Structure

```
.ai/                           ← AI Brain (single source of truth)
│
├── agents/                    ← 10 specialized agents
│   ├── feature-planner.md     ← Plan UI/KMM features
│   ├── code-implementer.md    ← Implement code changes
│   ├── code-reviewer.md       ← Code review & quality
│   ├── test-author.md         ← Write tests
│   ├── test-verifier.md       ← Run & verify tests
│   ├── debug-rca.md           ← Root-cause analysis
│   ├── release-manager.md     ← Release orchestration
│   ├── release-notes.md       ← Generate release notes
│   ├── github-issue-coordinator.md ← Issue management
│   └── workflow-pattern-capture.md ← Workflow learning
│
├── commands/                  ← Slash commands
│   ├── only-planner.md
│   ├── only-implementer.md
│   ├── only-reviewer.md
│   ├── only-test-author.md
│   ├── only-test-verifier.md
│   ├── only-debug-rca.md
│   ├── only-release-manager.md
│   ├── only-release-notes.md
│   ├── only-issue-coordinator.md
│   ├── only-from-prompt.md
│   ├── only-workflow-pattern-capture.md
│   ├── only-generate-uniffi.md
│   ├── help.md
│   └── README.md
│
├── skills/                    ← Reusable workflows
│   ├── kmp-doctor/            ← KMP build troubleshooting
│   ├── ios-device-doctor/     ← iOS simulator/device issues
│   ├── systematic-debugging/  ← Debugging framework
│   ├── write-implementation-plan/
│   ├── workflow-issue-handoff/
│   ├── workflow-manual-task-brief/
│   ├── workflow-mr-body/
│   ├── workflow-plan-output/
│   ├── workflow-pattern-capture/
│   └── feature-brainstorm/    ← App feature brainstorm
│
├── rules/                     ← Architecture & style rules
│   ├── RULES.md               ← Rules index
│   ├── code-style.md          ← Kotlin/Swift style
│   ├── kmp-principles.md      ← KMM architecture patterns
│   ├── ios-guidelines.md      ← iOS-specific patterns
│   ├── android-guidelines.md  ← Android-specific patterns
│   └── ...
│
├── artifacts/                 ← Generated outputs (ignore in git)
│   └── runs/
│       └── <task-id>/         ← Results from each task run
│
├── ORCHESTRATOR.md            ← This file (brain)
├── INDEX.md                   ← Full resource index
└── README.md                  ← Quick reference
```

---

## 🔗 IDE Integration

Each IDE has **explicit entry points** (no symlinks):

| IDE | Entry Point | Uses |
|-----|-------------|------|
| **Claude Code** | `.claude/INDEX.md` | agents/, commands/, skills/ |
| **Cursor** | `.cursor/rules/00-entry.mdc` | agents/, rules/ |
| **Codex CLI** | `.codex/INDEX.md` | agents/, commands/, rules/ |

### How It Works

1. Developer opens repo in IDE
2. IDE loads **entry point** file (INDEX.md or 00-entry.mdc)
3. Entry point points to `.ai/` as source of truth
4. All agents, commands, rules defined once in `.ai/`
5. Changes in `.ai/` automatically reflected everywhere

**No symlinks needed** — just explicit configuration.

---

## 🚀 How to Use

### From Claude Code

```bash
# List all commands
/help

# Plan a feature
/only-planner "add dark mode toggle"

# Implement
/only-implementer

# Review changes
/only-reviewer

# Write tests
/only-test-author "test dark mode toggle"

# Verify tests
/only-test-verifier

# Other workflows
/only-from-prompt              # Manual task from description
/only-generate-uniffi          # Generate FFI bindings from core
/only-debug-rca                # Debug an issue
```

### From Cursor

Cursor reads `.cursor/rules/00-entry.mdc` and `.ai/agents/` automatically.

```
Cmd+K in editor: /only-planner "your task"
Cursor shows guidance + links to agent docs
```

### From OpenAI Codex CLI

```bash
# Available agents
codex --agent feature-planner --context "add login UI"
codex --agent code-implementer --context "implement..."
codex --agent code-reviewer --context "review PR"

# Available rules
codex --rule code-style
codex --rule kmp-principles
```

---

## 📋 Agents Overview

| Agent | Purpose | Output |
|-------|---------|--------|
| **feature-planner** | Analyze task → create plan | implementation-plan.md |
| **code-implementer** | Write code from plan | code changes + implementer.md |
| **code-reviewer** | Review changes | review-report.md |
| **test-author** | Write tests | test files |
| **test-verifier** | Run tests | test-report.md |
| **debug-rca** | Root-cause analysis | rca-report.md |
| **release-manager** | Coordinate release | release plan |
| **release-notes** | Generate release notes | RELEASE.md |
| **github-issue-coordinator** | Manage issues | issue updates |
| **workflow-pattern-capture** | Learn from workflow | pattern doc |

Each agent:
1. Reads its definition from `.ai/agents/<name>.md`
2. Executes its responsibilities
3. Writes artifact to `.ai/artifacts/runs/<task-id>/`
4. Checks for failure markers
5. Returns to orchestrator

---

## 🎯 Command Patterns

All commands follow `only-*` pattern (for compose-specific execution):

```
/only-planner <task>           # Direct: plan this task
/only-implementer              # Direct: implement current plan
/only-reviewer                 # Direct: review current changes
/only-test-author <task>       # Direct: write tests for task
```

Convention: "only" = "specific to this repo only" (vs MetaSecret root which has `/route`, `/core-only-*`, `/compose-only-*`)

---

## 🧪 Workflow Example

### Typical Feature Development

```
1. /only-planner "add dark mode toggle"
   ↓ Output: .ai/artifacts/runs/task-123/implementation-plan.md
   
2. Review plan (user approval)

3. /only-implementer
   ↓ Output: code changes + .ai/artifacts/runs/task-123/implementer.md
   ↓ Check: does artifact contain FAIL marker?
   
4. /only-test-author "dark mode toggle"
   ↓ Output: test files + .ai/artifacts/runs/task-123/test-author.md

5. /only-test-verifier
   ↓ Output: .ai/artifacts/runs/task-123/test-report.md
   ↓ Check: did tests pass?

6. /only-reviewer
   ↓ Output: .ai/artifacts/runs/task-123/review-report.md
   ↓ Check: does it contain FAIL marker?
   
7. If FAIL: loop back to /only-implementer for fix
   If PASS: Ready to commit/PR
```

---

## 📝 Failure Detection

An artifact has **FAILED** if it contains:
```
Return to Planning: YES
Status: FAILED
**FAIL**
FAIL ❌
```

On failure:
- Stop pipeline
- Display reason
- Offer next steps (fix-pass, replan, etc)

---

## 🎨 For Developers

### Adding a New Agent

1. Create `.ai/agents/my-agent.md`
2. Define responsibilities clearly
3. Specify:
   - Inputs (what artifacts/context it reads)
   - Outputs (what artifact it writes)
   - Failure markers (what indicates failure)
4. Automatically available in all IDEs via entry points

### Adding a Skill

1. Create `.ai/skills/my-skill/SKILL.md`
2. Reference in agents: `Use skill **my-skill**`
3. Available across all IDEs

### Adding a Rule

1. Create `.ai/rules/my-rule.md`
2. Mark context: "For Cursor" or "For Codex CLI"
3. Auto-discovered via entry points

### Adding a Command

1. Create `.ai/commands/only-my-command.md`
2. Define: what it does, who calls it, expected inputs/outputs
3. Available as `/only-my-command` in Claude Code

---

## 📊 Project Context

| Aspect | Details |
|--------|---------|
| **Type** | KMM (Kotlin Multiplatform Mobile) |
| **Platforms** | iOS (SwiftUI) + Android (Jetpack Compose) |
| **Language** | Kotlin (shared), Swift/Kotlin (platforms) |
| **Architecture** | MVVM + Coordinator pattern |
| **Build System** | Gradle with KMM plugin |
| **Protocol** | FFI via UniFFI (shared ↔ platforms) |

---

## 🔗 Related Documentation

| Document | Purpose |
|----------|---------|
| **INDEX.md** | Full resource index (read if you're new) |
| **README.md** | Quick reference |
| **.claude/INDEX.md** | Claude Code specific entry |
| **.cursor/rules/00-entry.mdc** | Cursor specific rules |
| **.codex/INDEX.md** | Codex CLI specific entry |
| **agents/*.md** | Agent definitions |
| **commands/README.md** | Command reference |
| **skills/*/SKILL.md** | Skill documentation |

---

## 📌 Important Notes

- **No symlinks** — Explicit entry points in each IDE folder
- **Single source of truth** — Edit in `.ai/`, changes propagate everywhere
- **Independent repo** — Nothing shared with `meta-secret-core` (it's separate)
- **FFI boundary** — Uses UniFFI bindings from core via `only-generate-uniffi`
- **Artifacts are ephemeral** — Stored in `.ai/artifacts/runs/` (git-ignored)
- **Project-specific skills** — kmp-doctor, ios-device-doctor, feature-brainstorm are compose-only

---

✅ **Last updated:** 2026-04-18  
🚀 **Status:** Production-ready  
📖 **Next:** Read INDEX.md for full resource guide

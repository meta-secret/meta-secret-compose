# meta-secret-compose — Complete Resource Index

**Full map of all AI resources** for the Compose (KMM/iOS/Android) layer.

---

## 🧭 Quick Navigation

### By IDE

**Claude Code**
- Entry: `.claude/INDEX.md`
- Commands: `/only-planner`, `/only-implementer`, `/only-reviewer`, etc.
- Skills: Referenced automatically

**Cursor**
- Entry: `.cursor/rules/00-entry.mdc`
- Rules: Auto-loaded from `.ai/rules/`
- Agents: Available in rules

**Codex CLI**
- Entry: `.codex/INDEX.md`
- Usage: `codex --agent feature-planner --context "..."`

### By Task Type

**Planning a Feature**
- Agent: `.ai/agents/feature-planner.md`
- Command: `/only-planner "description"`
- Output: implementation-plan.md

**Implementing Code**
- Agent: `.ai/agents/code-implementer.md`
- Command: `/only-implementer`
- Output: code changes + implementer.md

**Code Review**
- Agent: `.ai/agents/code-reviewer.md`
- Command: `/only-reviewer`
- Output: review-report.md

**Testing**
- Author: `.ai/agents/test-author.md` (`/only-test-author`)
- Verifier: `.ai/agents/test-verifier.md` (`/only-test-verifier`)
- Output: test files + test-report.md

**Debugging Issues**
- Agent: `.ai/agents/debug-rca.md`
- Command: `/only-debug-rca`
- Skill: `.ai/skills/systematic-debugging/SKILL.md`
- Output: rca-report.md

**Releases**
- Manager: `.ai/agents/release-manager.md` (`/only-release-manager`)
- Notes: `.ai/agents/release-notes.md` (`/only-release-notes`)
- Output: release plan + RELEASE.md

---

## 📂 Directory Structure

### `.ai/agents/` — Agent Definitions (10 agents)

| Agent | File | Purpose |
|-------|------|---------|
| Feature Planner | `feature-planner.md` | Analyze task → create implementation plan |
| Code Implementer | `code-implementer.md` | Write code from approved plan |
| Code Reviewer | `code-reviewer.md` | Code quality review + feedback |
| Test Author | `test-author.md` | Write unit/integration tests |
| Test Verifier | `test-verifier.md` | Run tests + verify coverage |
| Debug RCA | `debug-rca.md` | Root-cause analysis for bugs |
| Release Manager | `release-manager.md` | Coordinate release process |
| Release Notes | `release-notes.md` | Generate release notes |
| Issue Coordinator | `github-issue-coordinator.md` | GitHub issue management |
| Pattern Capture | `workflow-pattern-capture.md` | Learn from workflows |

**How to use:** Read agent doc → understand what it does → run `/only-<name>` command

---

### `.ai/commands/` — Slash Commands

| Command | File | Runs | Output |
|---------|------|------|--------|
| `/only-planner` | `only-planner.md` | feature-planner | plan.md |
| `/only-implementer` | `only-implementer.md` | code-implementer | code + impl.md |
| `/only-reviewer` | `only-reviewer.md` | code-reviewer | review.md |
| `/only-test-author` | `only-test-author.md` | test-author | tests |
| `/only-test-verifier` | `only-test-verifier.md` | test-verifier | test-report.md |
| `/only-debug-rca` | `only-debug-rca.md` | debug-rca | rca.md |
| `/only-release-manager` | `only-release-manager.md` | release-manager | release plan |
| `/only-release-notes` | `only-release-notes.md` | release-notes | RELEASE.md |
| `/only-issue-coordinator` | `only-issue-coordinator.md` | github-issue-coordinator | issues |
| `/only-from-prompt` | `only-from-prompt.md` | manual workflow | varies |
| `/only-workflow-pattern-capture` | `only-workflow-pattern-capture.md` | pattern-capture | pattern |
| `/only-generate-uniffi` | `only-generate-uniffi.md` | FFI generator | bindings |
| `/help` | `help.md` | show this list | — |

**How to use:** Type `/only-<name> "context"` in Claude Code

---

### `.ai/skills/` — Reusable Workflows

**KMP-Specific**
- `kmp-doctor/SKILL.md` — Diagnose KMP build issues
  - Error patterns, launch policies, build commands
  - When: Build fails or project structure confused

- `ios-device-doctor/SKILL.md` — iOS simulator/device issues
  - Device setup, Xcode linking, SwiftUI preview
  - When: iOS side fails

**Debugging**
- `systematic-debugging/SKILL.md` — Debugging framework
  - Root-cause methodology, hypothesis testing
  - When: Using `/only-debug-rca`

**Workflows**
- `write-implementation-plan/` — Plan writing template
- `workflow-manual-task-brief/` — Manual task structure
- `workflow-issue-handoff/` — Issue handoff template
- `workflow-mr-body/` — PR description template
- `workflow-plan-output/` — Plan formatting
- `workflow-pattern-capture/` — Pattern learning

**Feature Development**
- `feature-brainstorm/SKILL.md` — Brainstorm new features
  - App context, user needs, design patterns
  - When: Planning new features

**How to use:** Referenced in agents via `Use skill **name**`, automatically loaded

---

### `.ai/rules/` — Architecture & Style Rules

**Index**
- `RULES.md` — Rules overview

**Code Style**
- `code-style.md` — Kotlin/Swift naming, formatting, patterns

**Architecture**
- `kmp-principles.md` — KMM patterns, MVVM+Coordinator
- `ios-guidelines.md` — iOS/SwiftUI specific patterns
- `android-guidelines.md` — Android/Compose specific patterns

**How to use:** Referenced in Cursor rules, Codex CLI rules; agents consult during code review

---

### `.ai/artifacts/` — Generated Outputs

```
artifacts/
└── runs/
    └── <task-id>/
        ├── implementation-plan.md
        ├── implementer.md
        ├── code-review.md
        ├── test-report.md
        ├── rca-report.md
        └── ... (one artifact per stage)
```

**Note:** Git-ignored. Temporary working space for agent outputs.

---

### Master Files

- **ORCHESTRATOR.md** — This brain (what runs what)
- **INDEX.md** — This file (resource map)
- **README.md** — Quick start for developers

---

## 🔍 Finding Things

**Need a command?**
- `.ai/commands/README.md` — All commands listed
- Or: `/help` in Claude Code

**Need to write an agent?**
- See: `.ai/agents/feature-planner.md` as example
- Follow same structure

**Need to add a skill?**
- See: `.ai/skills/systematic-debugging/SKILL.md` as example
- Create new folder with SKILL.md

**Need a rule for Cursor?**
- See: `.ai/rules/code-style.md` as example
- Add to `.ai/rules/`; Cursor auto-discovers

---

## 🚀 Getting Started

### First Time Here?

1. Read **ORCHESTRATOR.md** ← You are here
2. Read **README.md** ← Quick start
3. Open `.claude/INDEX.md` or `.cursor/rules/00-entry.mdc`
4. Try: `/only-planner "add dark mode"`

### Familiar with MetaSecret?

- This is **compose layer only** (UI/KMM)
- Core layer is in `meta-secret-core/` (separate)
- Root coordination is in `MetaSecret/` (routing + multi-repo)

### Want to Extend?

- Add agent: Create `.ai/agents/my-agent.md`
- Add command: Create `.ai/commands/only-my-command.md`
- Add skill: Create `.ai/skills/my-skill/SKILL.md`
- Add rule: Create `.ai/rules/my-rule.md`

All automatically available in all IDEs.

---

## 📋 Checklist: Before Starting Work

- [ ] Read ORCHESTRATOR.md ← Brain
- [ ] Read README.md ← Quick ref
- [ ] Understand agents (pick one that fits your task)
- [ ] Know which command to run (`/only-*`)
- [ ] Understand KMM context (see `.ai/rules/kmp-principles.md`)
- [ ] Check skill availability (kmp-doctor, ios-device-doctor, etc)

---

## 📞 Quick Links

**By IDE:**
- Claude Code: `.claude/INDEX.md`
- Cursor: `.cursor/rules/00-entry.mdc`
- Codex CLI: `.codex/INDEX.md`

**By Role:**
- Planner: `feature-planner.md`
- Implementer: `code-implementer.md`
- Reviewer: `code-reviewer.md`
- Tester: `test-author.md` + `test-verifier.md`

**By Problem:**
- KMP build fails: `skills/kmp-doctor/`
- iOS issue: `skills/ios-device-doctor/`
- Debug needed: `debug-rca.md` + `skills/systematic-debugging/`
- New feature: `feature-planner.md` + `skills/feature-brainstorm/`

---

✅ **Status:** Up-to-date  
🎯 **Context:** KMM (Compose/iOS)  
📖 **Next:** Read ORCHESTRATOR.md or try `/only-planner "your task"`

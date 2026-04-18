# Claude Code — meta-secret-compose Entry Point

> This is the entry point for Claude Code when working specifically in the **meta-secret-compose** repository.
> This is a KMM (Kotlin Multiplatform Mobile) project for iOS and Android UI.

---

## Quick Start

### Plan a feature
```bash
/compose-only-planner "add dark mode toggle"
```

### Implement a feature
```bash
/compose-only-implementer "your task"
```

### Review code
```bash
/compose-only-reviewer
```

### Run tests
```bash
/compose-only-test-verifier
```

---

## What To Read First

1. **`.ai/ARCHITECTURE.md`** — Project structure, KMM setup, UI patterns
2. **`.ai/agents/feature-planner.md`** — How to plan features
3. **`.ai/agents/code-implementer.md`** — Implementation approach
4. **`.ai/skills/`** — Helper skills (kmp-doctor, systematic-debugging, etc.)

---

## Available Commands

| Command | Does What |
|---------|-----------|
| `/compose-only-planner <task>` | Plan a feature for compose |
| `/compose-only-implementer <task>` | Implement a feature |
| `/compose-only-reviewer` | Review current changes |
| `/compose-only-test-author <task>` | Write tests |
| `/compose-only-test-verifier` | Run and verify tests |
| `/compose-only-debug-rca` | Root-cause analysis for bugs |
| `/help` | List all commands |

---

## Project Context

| Aspect | Details |
|--------|---------|
| **Type** | KMM (Kotlin Multiplatform Mobile) |
| **Platforms** | iOS (Swift/SwiftUI) + Android (Jetpack Compose) |
| **Architecture** | MVVM + Coordinator pattern |
| **Build system** | Gradle (shared module) |

---

## Key Rules

- **MVVM + Coordinator** — State lives in ViewModel, UI doesn't orchestrate
- **Shared via KMM** — Logic in shared module, platform-specific UI only
- **Design tokens** — No hardcoded colors/spacing; use theme system
- **Compose best practices** — Immutable state, proper recomposition scopes
- **SwiftUI patterns** — State management via @StateObject, @State as needed

---

## Important Files

| File | Purpose |
|------|---------|
| `.ai/ARCHITECTURE.md` | Full architecture decisions |
| `.ai/agents/feature-planner.md` | How to plan features |
| `.ai/skills/kmp-doctor/` | KMM troubleshooting guide |
| `.ai/skills/systematic-debugging/` | Debugging framework |

---

## Workflow

1. **Plan** — Use `/compose-only-planner` to create implementation plan
2. **Implement** — Use `/compose-only-implementer` to write code
3. **Test** — Use `/compose-only-test-author` + `/compose-only-test-verifier`
4. **Review** — Use `/compose-only-reviewer` for code review
5. **Debug** — Use `/compose-only-debug-rca` if issues arise

---

## Multi-Repo Context

This is **meta-secret-compose** (UI layer). There's also **meta-secret-core** (crypto/protocol).

To switch repos, go back to the **MetaSecret folder** and use:
- `/route "task"` — Smart routing to core or compose
- `/core-only-*` — Direct core commands

---

**Entry point:** `.claude/INDEX.md`  
**Architecture:** `.ai/ARCHITECTURE.md`  
**Root coordination:** `../../.ai/CONFIG.md` (MetaSecret layer)

# Codex CLI — meta-secret-compose Entry Point

> This is the entry point for OpenAI Codex CLI when working with **meta-secret-compose** (KMM UI layer).

---

## Quick Start

```bash
# Plan a feature
codex --agent feature-planner --context "add dark mode"

# Implement a feature
codex --agent code-implementer --context "add dark mode toggle"

# Review code
codex --agent code-reviewer --context "$(cat your-changes.diff)"
```

---

## Available Agents

| Agent | Purpose |
|-------|---------|
| `feature-planner` | Plan UI/KMM features |
| `code-implementer` | Write implementation code |
| `code-reviewer` | Review code changes |
| `test-author` | Write tests |
| `test-verifier` | Run and verify tests |
| `debug-rca` | Root-cause analysis |

All agents are in `.ai/agents/`.

---

## Project Type

- **Language:** Kotlin (shared), Swift/Kotlin (platforms)
- **Pattern:** KMM (Kotlin Multiplatform Mobile)
- **UI:** Jetpack Compose (Android) + SwiftUI (iOS)
- **Architecture:** MVVM + Coordinator

---

## Key References

| What | Where |
|------|-------|
| Architecture | `.ai/ARCHITECTURE.md` |
| Agents | `.ai/agents/*.md` |
| KMM help | `.ai/skills/kmp-doctor/SKILL.md` |
| Debugging | `.ai/skills/systematic-debugging/SKILL.md` |

---

## Example Usage

```bash
cd meta-secret-compose
codex --agent feature-planner --context "add biometric auth UI"
# Agent reads from .ai/ automatically, returns plan
```

---

## This is KMM UI

- **meta-secret-compose** = Compose/iOS UI
- **meta-secret-core** = Crypto/protocol (separate)
- **MetaSecret root** = Coordination layer

---

**Entry point:** `.codex/INDEX.md`
**Agents:** `.ai/agents/`
**Architecture:** `.ai/ARCHITECTURE.md`

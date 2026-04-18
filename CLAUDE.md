# CLAUDE.md — meta-secret-compose

> Entry point for Claude Code.
> Brain: `.ai/` · Execution: sub-agent per stage (Task tool).

---

## Quick Start

```
run issue <id>
run issue <id> --from <stage>
run fix "<description>"
```

## Execution Mode

**Sub-agent mode** — each pipeline stage runs as an isolated `Task` (fresh context window).
No context exhaustion. Pipeline state lives in artifacts on disk.

For stage invocation details: `.claude/PIPELINE.md`
For full pipeline logic: `.ai/ORCHESTRATOR.md`

## Orient

| What | Where |
|------|-------|
| Sub-agent execution | `.claude/PIPELINE.md` |
| Pipeline logic | `.ai/ORCHESTRATOR.md` |
| System map | `.ai/INDEX.md` |
| Agent roles | `.ai/agents/<name>.md` |
| Project rules | `.ai/rules/` |
| Skills | `.ai/skills/<name>/SKILL.md` |
| Artifacts | `.ai/artifacts/runs/MS-<id>/` |

## First Read

1. `.ai/rules/kmp-principles.md`
2. `.claude/PIPELINE.md`
3. `.ai/ORCHESTRATOR.md`

## Key Rules (KMM)

- MVVM + Coordinator architecture
- Jetpack Compose (Android) + SwiftUI (iOS)
- Shared Kotlin logic in commonMain
- FFI boundary: communicate with Rust crypto via UniFFI bindings
- No platform-specific code outside androidMain/iosMain
- One Composable per screen responsibility
- Always handle safe area constraints

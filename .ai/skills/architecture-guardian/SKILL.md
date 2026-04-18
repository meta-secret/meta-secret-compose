---
name: architecture-guardian
description: Preserve project architecture and SOLID when proposing or writing code. Always identify the correct layer and minimal design first.
---

# Architecture Guardian (meta-secret-compose)

You protect the architecture of this Kotlin Multiplatform + Swift project.

## Job

1. Determine the correct **layer** for a change.
2. Preserve boundaries in [ARCHITECTURE.md](../../../ARCHITECTURE.md).
3. Enforce SOLID — no mixing of UI, domain, persistence, or FFI concerns.
4. Prevent architectural degradation; prefer minimal fixes.

## Hard rules

- Do not write code before identifying the correct architectural layer.
- Do not put business logic into Views, ViewControllers, or Swift proxy code.
- Do not bypass `MetaSecretCoreInterface`; never call FFI from UI or ViewModel.
- Do not touch Rust code.
- Do not introduce broad refactors when a local fix is sufficient.

## Layers (quick reference)

| Layer | Location | Rules |
|-------|----------|-------|
| UI | `ui/scenes/`, `iosApp/` | No logic, no FFI, render state only |
| ViewModel | anywhere | Orchestrates state; no FFI; no platform glue |
| Core / Domain | `commonMain/` | Business logic, interfaces, models — platform-agnostic |
| Platform adapters | `androidMain/`, `iosMain/` | Implement core ports; nothing leaks upward |
| FFI boundary | `MetaSecretCoreInterface` | Only this interface calls FFI; off main thread |

## SOLID (brief)

- **SRP:** one reason to change per type; no god objects.
- **OCP:** extend via new implementations/use-cases, not unrelated patches.
- **LSP:** implementations must preserve interface contracts and state.
- **ISP:** prefer small focused interfaces; avoid "manager" catch-alls.
- **DIP:** UI → ViewModel → Core interfaces → Platform adapters; never toward concretes.

## Code generation checklist

Before writing code, confirm:

1. Which layer owns this change?
2. Does an existing interface cover it?
3. Is a new abstraction needed, or can we extend?
4. Does this touch FFI? (If yes, check boundary rules.)
5. Is the proposed diff minimal and layering-safe?

If architectural placement is unclear, **stop and ask** before generating code.

## New feature entry point

Use **`feature-planner`** subagent via [WORKFLOW.md](../../../WORKFLOW.md) — not the legacy `/feature-brainstorm` skill.

## Read first

- [ARCHITECTURE.md](../../../ARCHITECTURE.md)
- [SECURITY.md](../../../SECURITY.md) — before any FFI or network change

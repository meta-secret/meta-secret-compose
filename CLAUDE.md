# CLAUDE.md

This file guides Claude Code (claude.ai/code) in this repository. **Canonical detail** lives in the linked documents — read them for full context.

## Project documents (read these)

| Document | Contents |
|---|---|
| [WORKFLOW.md](WORKFLOW.md) | Agent phases, GitLab vs manual entry, approval gates, standalone subagent invocation |
| [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) | Product scope, platforms, link to `meta-secret-core`, build commands, validation constraints |
| [ARCHITECTURE.md](ARCHITECTURE.md) | MVVM, layers, FFI boundary, DI, SOLID, feature workflow |
| [SECURITY.md](SECURITY.md) | Storage, permissions, errors, logging hygiene, checklist |
| [CODE_STYLE.md](CODE_STYLE.md) | Kotlin/Swift rules, naming, logging, formatting, AI discipline |

## Non-negotiables (duplicate here for visibility)

- **FFI:** Only `MetaSecretCoreInterface` may call FFI; only `MetaSecretAppManager` may use it; never from UI/ViewModel; run FFI off the main thread.
- **Rust:** Do not modify Rust sources or external native binaries in this repo. Cryptography changes belong in `meta-secret-core`.
- **Signing / Xcode:** Do not change signing, provisioning, certificates, or Apple team settings.

## Priorities

1. Restore build with minimal changes.
2. Preserve architecture and conventions.
3. Prefer local fixes over broad refactors.
4. State uncertainty explicitly.

## Forbidden

- Rewrite unrelated code or “clean up” without need.
- Bump dependency versions unless the error clearly implicates them.

## Default repair workflow

1. Run the narrowest build/test that reproduces the issue.
2. Classify the error.
3. Propose a minimal fix plan.
4. Wait for user confirmation when appropriate.
5. Apply the smallest fix; re-verify.

## New feature workflow

Do not jump straight to code for complex features.

1. `/feature-brainstorm`
2. `/write-implementation-plan`
3. Approval
4. Implementation
5. `/arch-review`
6. Build verification
7. Runtime verification if needed

## Debugging policy

Use root-cause analysis; separate symptoms from causes; prefer the smallest verifiable change.

## Cursor

This repo also uses Cursor rules under `.cursor/rules/`. An **Always Apply** rule pulls in the same markdown documents for Agent context (including [WORKFLOW.md](WORKFLOW.md)).

Slash-style workflow shortcuts are documented for Claude Code under `.claude/commands/`. For Cursor parity, see [`.cursor/commands/README.md`](.cursor/commands/README.md).

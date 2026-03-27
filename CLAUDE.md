# CLAUDE.md

This file guides Claude Code (claude.ai/code) in this repository. **Canonical detail** lives in the linked documents — read them for full context.

## Project documents (read these)

| Document | Contents |
|---|---|
| [WORKFLOW.md](WORKFLOW.md) | Agent phases, entry points, approval gates, skills table, optional diagnostics |
| [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) | Product scope, platforms, link to `meta-secret-core`, build commands |
| [ARCHITECTURE.md](ARCHITECTURE.md) | MVVM, layers, FFI boundary, DI, SOLID |
| [SECURITY.md](SECURITY.md) | Storage, permissions, errors, logging hygiene, checklist |
| [CODE_STYLE.md](CODE_STYLE.md) | Kotlin/Swift rules, naming, logging, formatting, AI discipline |

## Non-negotiables

- **FFI:** Only `MetaSecretCoreInterface` may call FFI; only `MetaSecretAppManager` may use it; never from UI/ViewModel; run FFI off the main thread.
- **Rust:** Do not modify Rust sources or external native binaries in this repo. Cryptography changes belong in `meta-secret-core`.
- **Signing / Xcode:** Do not change signing, provisioning, certificates, or Apple team settings.

## Priorities

1. Restore build with minimal changes.
2. Preserve architecture and conventions.
3. Prefer local fixes over broad refactors.
4. State uncertainty explicitly.

## Forbidden

- Rewrite unrelated code or "clean up" without need.
- Bump dependency versions unless the error clearly implicates them.

## AI workflow

Follow [WORKFLOW.md](WORKFLOW.md). Slash commands: [`.claude/commands/`](.claude/commands/). Cursor parity: [`.cursor/commands/README.md`](.cursor/commands/README.md).

## Cursor

Rules under [`.cursor/rules/`](.cursor/rules/) apply. An **Always Apply** rule (`ai-project-context.mdc`) pulls in the same markdown documents for Agent context (including [WORKFLOW.md](WORKFLOW.md)).

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

Follow [WORKFLOW.md](WORKFLOW.md).

**Unified AI structure:** All AI automation lives in [`.ai/`](.ai/) — **single source of truth** for Claude Code, Cursor, and OpenAI Codex CLI.

- **Agents:** [`.ai/agents/`](.ai/agents/)
- **Commands:** [`.ai/commands/`](.ai/commands/) (slash commands for Claude Code + Codex CLI)
- **Skills:** [`.ai/skills/`](.ai/skills/) (reusable workflows, including KMP + iOS diagnostics)
- **Rules:** [`.ai/rules/`](.ai/rules/) (Cursor + Codex CLI)

Symlinks from `.claude/`, `.cursor/`, and `.codex/` point to `.ai/`:
- `.claude/agents → .ai/agents`
- `.cursor/rules → .ai/rules`
- `.codex/agents → .ai/agents` (etc.)

👉 **See [`.ai/ARCHITECTURE.md`](.ai/ARCHITECTURE.md)** for complete AI structure and IDE integration details.

**Agent output:** When this repo sits under the MetaSecret parent workspace, follow [Agent output conventions](../CLAUDE.md#agent-output-conventions) in the root `CLAUDE.md`. Otherwise use the same norms (emojis in replies; `##`/`###` headings, **bold**, blockquotes; optional HTML color where the UI supports it).

## IDE Support

| IDE | Support | Where |
|-----|---------|-------|
| **Claude Code** | Slash commands | `/help` → lists all commands |
| **Cursor** | Agents + Rules | Via `.cursor/agents` + `.cursor/rules` (symlinks) |
| **OpenAI Codex CLI** | Agents + Commands + Rules | Via `.codex/` (symlinks) |

Rules under [`.ai/rules/`](.ai/rules/) apply to Cursor and Codex. When using Cursor, rules auto-load from symlinks.

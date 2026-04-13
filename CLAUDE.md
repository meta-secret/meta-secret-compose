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

Follow [WORKFLOW.md](WORKFLOW.md). Slash command catalog (`/help`): [`.claude/commands/README.md`](.claude/commands/README.md) (`/only-issue-coordinator`, `/only-from-prompt`, `/only-*`). Command files: [`.claude/commands/`](.claude/commands/). Cursor parity: [`.cursor/commands/README.md`](.cursor/commands/README.md).

**Agent output:** When this repo sits under the MetaSecret parent workspace, follow [Agent output conventions](../CLAUDE.md#agent-output-conventions) in the root `CLAUDE.md`. Otherwise use the same norms (emojis in replies; `##`/`###` headings, **bold**, blockquotes; optional HTML color where the UI supports it).

## Cursor

Rules under [`.cursor/rules/`](.cursor/rules/) apply. An **Always Apply** rule (`ai-project-context.mdc`) pulls in the same markdown documents for Agent context (including [WORKFLOW.md](WORKFLOW.md)).

## UniFFI bindings sync (from meta-secret-core)

Generated Kotlin (`composeApp/.../uniffi/.../mobile_uniffi.kt`) and Swift/C headers under **`iosApp/.../UniffiGenerated/`** (both copies: `iosApp/UniffiGenerated` and `MetaSecretCoreService/UniffiGenerated`) are **not committed** — they are produced by **`uniffi-bindgen-runner`** in **meta-secret-core** (UDL: `meta-secret/mobile/uniffi/src/mobile_uniffi.udl`).

**When to regenerate:** after changing `mobile_uniffi.udl` or Rust exports in **meta-secret-core**, or when cloning **meta-secret-compose** without those files.

**How (pick one):**

1. **Gradle (recommended):** from the compose repo root, run **`./gradlew :composeApp:generateUniffiBindings`** — runs `scripts/sync-uniffi-from-core.sh`. **`./gradlew build`** depends on this task so Kotlin, Kotlin/Native, and swiftklib compile after bindings exist. Override the core checkout with **`-PmetaSecretCoreRoot=...`** or **`META_SECRET_CORE_ROOT`** (default: **`../meta-secret-core`** relative to the compose repo root). Requires **`cargo`** on `PATH`; the task fails with a clear error if the core root or script is wrong.
2. **Manual:** `./scripts/sync-uniffi-from-core.sh ../meta-secret-core` (same env var / argument rules).

**After regenerating bindings**, rebuild the native library (`libmetasecret_mobile` / `metasecret-mobile.a`) from core so symbols match the bindings.

**CI / agents:** check out **both** repositories (or point **`META_SECRET_CORE_ROOT`** at the core checkout), install the Rust toolchain, then run **`generateUniffiBindings`** or **`build`** before compiling the app.


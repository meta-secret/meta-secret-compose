---
description: Regenerate UniFFI Kotlin/Swift bindings from meta-secret-core via Gradle (delegates to gradlew; requires Rust cargo).
---

# Generate UniFFI bindings — meta-secret-compose

**Goal:** Run the same step as **`./gradlew :composeApp:generateUniffiBindings`** so Kotlin, Swift, and swiftklib sources exist before compile.

**Repository root:** this file is written for **`meta-secret-compose/`** as the workspace root.

## Instructions for the assistant

1. Ensure **`meta-secret-core`** is available locally (sibling clone or any path).

2. From **`meta-secret-compose/`**, run:

   ```bash
   ./gradlew :composeApp:generateUniffiBindings
   ```

   If core is not at **`../meta-secret-core`**, set **`META_SECRET_CORE_ROOT`** or **`-PmetaSecretCoreRoot=<absolute-or-relative-path>`** to the **meta-secret-core** repository root (the directory that contains **`meta-secret/Cargo.toml`**).

3. **Requires:** **`bash`**, **`cargo`** (Rust toolchain). If **`cargo`** is missing, fail with an explicit message — do not skip generation.

4. **Alternative (no Gradle):** **`./scripts/sync-uniffi-from-core.sh <path-to-meta-secret-core>`** — same outputs; see [CLAUDE.md](../../CLAUDE.md).

5. Do **not** commit generated files under **`UniffiGenerated/`** or **`.../uniffi/`** — they are gitignored.

**Arguments:** `$ARGUMENTS` — optional note for the user (e.g. non-default core path); still apply **`META_SECRET_CORE_ROOT`** / **`-PmetaSecretCoreRoot`** when the user specifies a path.

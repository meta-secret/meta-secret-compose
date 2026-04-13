# Code style

Formatting, naming, and AI implementation discipline for this repository.  
See also: [CLAUDE.md](CLAUDE.md), [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md), [ARCHITECTURE.md](ARCHITECTURE.md), [SECURITY.md](SECURITY.md).

## General principles

Prefer clarity over cleverness, explicit logic over magic, small focused changes, and existing conventions. Do not refactor unrelated code.

## Kotlin

**Forbidden:** `!!`, `GlobalScope`

**Required:** explicit null handling, structured concurrency, `viewModelScope` or injected scopes

**Prefer:** `val`, immutability, small functions, sealed domain errors

Exceptions must not cross module boundaries.

## Swift / iOS bridge

Thin bridge only: no business logic in Swift, no new architecture patterns, do not bypass shared layers.

## ViewModels

All UI events go through `handle(event)`. ViewModels update state and call use-cases; no platform glue or FFI.

## Repositories

Expose `XRepository` interfaces; `XRepositoryImpl` stays behind DI. Never expose `*Impl` in public APIs.

## Logging

Short, structured, readable. Example:

```kotlin
println("👤ProfileViewModel: ✅ User loaded")
println("👤ProfileViewModel: ❌ Failed to load user: $reason")
```

Use a consistent emoji convention within a file and its paired View/ViewModel. Never log seed phrases, tokens, secrets, or private user data.

## Naming

- Packages: `lowercase.with.dots`
- Types: `PascalCase`; members: `camelCase`; constants: `UPPER_SNAKE_CASE`
- Use cases: `SomethingUseCase`; ViewModels: `SomethingViewModel`
- Repositories: `XRepository` / `XRepositoryImpl`
- **FFI bridge (`MetaSecretNative`, Android static JNI):** public Kotlin functions use **camelCase**, aligned with generated UniFFI Swift and [Kotlin conventions](https://kotlinlang.org/docs/coding-conventions.html#function-names). Do not mirror Rust/UDL snake_case in this facade when generated bindings already expose camelCase (e.g. `metaWsStart`, not `meta_ws_start`). Legacy snake_case names may remain until refactored intentionally.

## Comments

Do not add comments unless explicitly requested. Code should be self-explanatory.

## Formatting

Match existing formatting; avoid mass reformatting or unrelated formatting churn.

## AI behavior when writing code

1. Follow [ARCHITECTURE.md](ARCHITECTURE.md) and SOLID.
2. Prefer the smallest viable change.
3. Avoid unnecessary abstractions and speculative design.
4. For large features, propose a plan before implementation.

## Security-related style constraints

Secrets must not be stored in plaintext. iOS: Keychain. Android: Keystore. UI must not handle raw secrets. Details: [SECURITY.md](SECURITY.md).

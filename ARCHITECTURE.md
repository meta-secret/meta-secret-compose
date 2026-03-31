# Architecture

Canonical layering and design rules for this repository.  
See also: [CLAUDE.md](CLAUDE.md), [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md), [SECURITY.md](SECURITY.md), [CODE_STYLE.md](CODE_STYLE.md).

## Principles

**MVVM** with **unidirectional data flow**. No `expect/actual` — platform differences use **interfaces** and adapters.

## Module structure

```
composeApp/src/
├── commonMain/   — domain, use-cases, models, interfaces, DI (KoinModules)
├── androidMain/  — Android adapters, UniFFI bindings to native code, DI (PlatformModule.android)
└── iosMain/      — iOS adapters, Swift/ObjC bridge, DI (PlatformModule.ios)
iosApp/           — Xcode target (UIKit + SwiftBridge entrypoint)
```

### UniFFI bindings (`mobile_uniffi`)

Generated artifacts are produced in **`meta-secret-core`**; this repo consumes them as-is.

- **Android:** Kotlin bindings live under `composeApp/src/androidMain/kotlin/.../uniffi/` (JNA loads `metasecret_mobile`; see `MetaSecretCoreService.android.kt`). Regenerate when the Rust API changes; avoid hand-editing except any project-specific post-step documented next to the generator.
- **iOS:** Swift and C headers exist in **two** locations so SwiftPM (`swiftklib`) and the Xcode target both see the same API: `iosApp/iosApp/UniffiGenerated/` and `iosApp/iosApp/MetaSecretCoreService/UniffiGenerated/`. After regeneration, **keep both copies in sync** (copy from one canonical build output or run the project’s generation script) to avoid checksum/version drift between targets.

### KMP layout vs AGP 9+

Multi-module migration for Kotlin Multiplatform + Android is tracked as technical debt; see the `BACKLOG(AGP 9+)` comment in `composeApp/build.gradle.kts` and [KMP project structure migration](https://kotl.in/kmp-project-structure-migration).

### Layers

| Layer | Location | Rule |
|---|---|---|
| `ui/scenes/` | View + ViewModel per screen | No business logic; no FFI |
| `core/` | Domain, use-cases, interfaces | All platform interfaces declared here |
| Platform adapters | `androidMain/` / `iosMain/` | Implement core ports; no upward deps |
| `models/apiModels/` | JSON models from Rust lib | Mapped to `appInternalModels/` in core |

## MVVM

- **Views:** no business logic; render state; send events to the ViewModel.
- **ViewModels:** orchestrate state; handle UI events; call use-cases; **never** call FFI directly.
- **Core:** business logic and interfaces; platform-agnostic.
- **Platform adapters:** implement core interfaces; platform-specific code only.

## FFI boundary

`MetaSecretCoreInterface` is the **only** interface allowed to call FFI. Only **`MetaSecretAppManager`** may use it. FFI calls must run off the main thread (e.g. `withContext(Dispatchers.IO)`).

Rust code (`meta-secret-lib`) must **not** be modified in this repo. No direct UniFFI calls or ad hoc Swift/C bridge usage outside the defined interface (`MetaSecretCoreInterface` path).

### Key types

- `MetaSecretAppManagerInterface` / `MetaSecretAppManager` — façade over meta-secret-lib
- `MetaSecretSocketHandlerInterface` / `MetaSecretSocketHandler` — event sync
- `MetaSecretStateResolverInterface` / `MetaSecretStateResolver` — type-state helper (ViewModels only)

## Dependency direction

**UI → ViewModel → Core interfaces → Platform adapters**

Never: UI → concrete repository, UI → FFI, core depending on Android/iOS frameworks.

## DI (Koin)

- Android: `commonModule + PlatformModule.android`
- iOS (SwiftBridge): `commonModule + PlatformModule.ios`
- Every interface has a binding where its implementation lives
- Public APIs never expose `*Impl` types

## Interface placement

When adding a capability:

1. Define an **interface** in `core/commonMain`
2. Platform-agnostic implementation → `commonMain`, bind in `di/KoinModules`
3. Platform-specific / FFI → `androidMain` or `iosMain`, bind in `PlatformModule.android` / `.ios`
4. Public APIs expose interfaces only

## Model mapping

`apiModels` are transport/native data. Map to internal models before use. UI must not consume raw API/native models directly.

## ViewModel contract

```kotlin
interface CommonViewModel {
    val state: StateFlow<UiState>
    val navigationEvent: SharedFlow<NavigationEvent>  // optional
    fun handle(event: CommonViewModelEventsInterface)
}
```

All UI inputs go through `handle(event)`. Events are scene-specific sealed types implementing `CommonViewModelEventsInterface`.

## Navigation

- **Android:** Navigation-Compose
- **iOS:** UIKit push/pop

## SOLID (summary)

Single responsibility; open/closed via new implementations; Liskov-respecting implementations; small interfaces; depend on abstractions.

## Architecture decisions before coding

1. Identify the layer for the change.
2. Prefer extending an existing interface or use-case over stuffing logic into View / ViewModel / bridge.
3. Keep Swift minimal; business logic stays in shared/core unless clearly platform-specific.
4. Never place FFI outside the allowed boundary.

## Forbidden architectural patterns

- Business logic in SwiftUI/UIView/UIKit views
- Direct FFI outside `MetaSecretCoreInterface` and its approved façade path
- Platform logic leaking into shared domain without an explicit design
- God managers mixing unrelated responsibilities
- UI depending on concrete data/network/native implementations
- Shortcuts that bypass core interfaces

## Feature workflow

For non-trivial features:

1. Determine placement
2. Produce an implementation plan
3. Wait for approval
4. Generate minimal code
5. Verify build
6. Verify runtime when needed
7. Architecture review

Do not jump straight to large code dumps for complex features. If placement is unclear, ask first.

## Code generation discipline

- Respect module boundaries; extend existing abstractions
- No parallel architecture; no god objects
- Minimal compliant implementation

## Absolute prohibitions (architecture-related)

- Modify Rust libraries or signing/provisioning/certificates/team settings
- FFI calls outside the defined interface
- Platform code smuggled into shared modules without a deliberate design

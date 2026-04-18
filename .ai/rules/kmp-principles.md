# KMM Principles — meta-secret-compose

## Architecture

- **MVVM** — Model-View-ViewModel with Coordinator pattern
- **Shared Logic** — `commonMain/` for business logic
- **Platform-Specific UI** — `androidMain/` for Jetpack Compose, `iosMain/` for SwiftUI
- **No platform code in commonMain** — Ever

## Jetpack Compose (Android)

- One screen = one Composable function (top-level)
- ViewModels are injected, not created in UI
- State flows down, events flow up
- Modifiers for styling (no inline colors)
- Material Design 3 tokens
- Safe area: use `WindowInsets.safeDrawing`

## SwiftUI (iOS)

- One screen = one View (top-level)
- MVVM with @StateObject for ViewModels
- @Published for reactive state
- Environment for dependency injection
- Always respect safe area with `.safeAreaInset()`
- Use system colors and spacing

## Shared Kotlin

- Pure business logic in `commonMain/`
- Use `expect/actual` for platform-specific APIs
- No UI framework imports in commonMain
- Result<T, E> for error handling
- Sealed classes for state machines

## FFI with Rust Core

- UniFFI bindings for crypto operations
- Never hardcode keys or secrets
- All crypto calls go through FFI layer
- Error handling: map Rust errors to Kotlin exceptions
- No blocking operations in UI thread

## Testing

- Unit tests for shared logic
- UI tests (Compose testing, XCTest)
- Integration tests for FFI boundary
- Property-based tests for crypto integration

## Build System

- Gradle with KMP plugin
- `composeApp` is the shared KMP module
- Platform-specific build variants in `androidMain/` and `iosMain/`
- CocoaPods for iOS native dependencies

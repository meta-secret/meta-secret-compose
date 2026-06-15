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
- Platform abstractions must be done via `interface + DI` (platform implementations in `androidMain/` and `iosMain/`)
- `expect/actual` is forbidden in this project
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

## File Structure & Modularity

### File Size Constraint
- **Maximum 400 lines per file** — This is hard limit
- If file exceeds 400 lines → split into logical modules
- Example: If `SettingsScreen.kt` grows beyond 400 lines:
  - Extract subcomposables to separate files
  - Move utility functions to `SettingsUtils.kt`
  - Create `SettingsDialog.kt`, `SettingsList.kt` for reusable components

### Component Reusability
- **If UI element is used 2+ times** → extract to separate file
- Shared components location: `ui/` directory
- Example:
  - ❌ Don't: Define `CustomButton` twice in different screens
  - ✅ Do: Create `ui/components/CustomButton.kt`, import everywhere

### Method Parameters
- **Maximum 5 parameters per method/function** (hard limit)
- If need more parameters → create **Input data class**
- Example:
  ```kotlin
  // ❌ Bad (6 parameters)
  fun createVault(
    name: String,
    description: String,
    threshold: Int,
    timeout: Int,
    autoSync: Boolean,
    notifications: Boolean
  ) { }
  
  // ✅ Good (use Input model)
  data class CreateVaultInput(
    val name: String,
    val description: String,
    val threshold: Int,
    val timeout: Int,
    val autoSync: Boolean,
    val notifications: Boolean
  )
  fun createVault(input: CreateVaultInput) { }
  ```

## Visibility & Encapsulation

- **Maximize `private` visibility** — Default to private, expose only what's necessary
- All helper functions should be `private`
- All data models should be `private` unless used across modules
- All state should be `private` unless explicitly exposed via public API
- Example:
  ```kotlin
  class VaultManager {
    private fun validateInput() { }      // private, not public
    private val internalState = State()  // private
    val publicState = State()            // public only if needed
  }
  ```

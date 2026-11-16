# KMM Principles — meta-secret-compose

## Architecture

- **MVVM** — Model-View-ViewModel with Coordinator pattern
- **Shared Client Logic** — `commonMain/` for FFI adapters, client models, ViewModel orchestration, and platform-independent presentation logic; security/domain decisions remain in Rust Core
- **Shared UI** — Compose Multiplatform screens/components in `commonMain/`; Android/iOS source sets implement injected platform ports and host integration
- **No platform SDK code in commonMain** — shared Compose UI is allowed; OS-specific APIs stay behind interfaces

## Compose Multiplatform (Android and iOS)

- One screen = one Composable function (top-level)
- ViewModels are injected, not created in UI
- State flows down, events flow up
- Modifiers for styling (no inline colors)
- Unrelated legacy screens retain their theme. Approved registration and Liveboard use generated, versioned shared design tokens/assets and scoped components from the approved design package.
- Safe area: use `WindowInsets.safeDrawing`

## iOS host integration

- Shared Compose renders application screens; Swift/UIKit code hosts it and implements OS integration ports.
- Keep Apple sign-in, biometry, camera permissions and safe-area handling behind existing platform interfaces.
- Do not introduce a parallel SwiftUI registration or Liveboard implementation.

## Shared Kotlin

- Client integration logic in `commonMain/` may map Core responses, coordinate
  ViewModels, and prepare UI state. It must not implement cryptography, Key
  Share operations, K-of-N/quorum, recovery claims, Approve/Decline ordering,
  JOIN/DELETE authorization, resharing, synchronization conflict resolution, or
  Secret reveal eligibility; those decisions belong to Rust Core. Simple
  non-security form validation and platform UI behavior are allowed, but Core
  remains authoritative.
- Platform abstractions must be done via `interface + DI` (platform implementations in `androidMain/` and `iosMain/`)
- `expect/actual` is forbidden in this project
- Compose Multiplatform UI imports are allowed in commonMain; Android/iOS-specific SDK imports are not
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

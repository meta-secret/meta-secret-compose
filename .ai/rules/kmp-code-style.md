# KMP Code Style — meta-secret-compose

Coding standards and conventions for MetaSecret Compose app.

---

## Strings & Localization

### Rule: Use `appString()` ONLY

❌ **FORBIDDEN:**
```kotlin
text = stringResource(Res.string.emailSelectionTitle)
text = stringResource(R.string.something)
```

✅ **REQUIRED:**
```kotlin
text = appString(AppString.addDevice)
text = appString(AppString.vaultName)
```

### Where strings live:
- **Central location:** `core/AppString.kt`
- **What it contains:** All localized strings for the app
- **How to add:** Define enum case in `AppString`, then use `appString(AppString.yourString)`

### Why:
- Single source of truth for all strings
- Consistent localization handling
- Easier refactoring and translation management

---

## Typography & Text Styles

### Rule: Use only `AppTextStyles` — No custom styles

❌ **FORBIDDEN:**
```kotlin
text = "Hello",
style = TextStyle(
  fontSize = 20.sp,
  fontWeight = FontWeight.Bold,
  color = Color.Black
)

text = "Title",
fontSize = 18.sp,  // Don't use raw fontSize
fontWeight = FontWeight.SemiBold,
```

✅ **REQUIRED:**
```kotlin
text = appString(AppString.greeting),
style = AppTextStyles.ScreenTitle()

text = appString(AppString.vaultName),
style = AppTextStyles.BodyMedium()
```

### Available styles:
- **Location:** `ui/theme/AppTextStyles.kt`
- **Examples:** `ScreenTitle()`, `BodyMedium()`, `LabelSmall()`, `HeadlineLarge()`
- **If style missing:** Don't invent one → ask to add it to `AppTextStyles.kt`

### Why:
- Consistent visual hierarchy
- Unified typography system
- Easy to update all text at once
- Respects design system

---

## Visibility & Encapsulation

### Rule: Maximize `private` — Expose only public API

✅ **Default to `private`:**
```kotlin
private fun validateEmail(email: String): Boolean { }
private val cacheData = mutableStateOf<Data?>(null)
private class InternalHelper { }
```

✅ **Expose only what's needed:**
```kotlin
class VaultManager(
  private val repository: VaultRepository,  // private
  private val validator: Validator,         // private
) {
  // Public API only
  fun createVault(input: CreateVaultInput): Result<Vault> { }
  fun deleteVault(id: String): Result<Unit> { }
}
```

### When to use `public`:
- Top-level APIs (services, managers)
- Data classes shared between modules
- Only when explicitly needed across boundaries

### Why:
- Reduces API surface
- Prevents accidental coupling
- Makes code easier to refactor
- Clear separation of public vs internal

---

## Composables & UI Functions

### File-level composables:
```kotlin
// ✅ Good: Top-level, clear intent
@Composable
fun VaultScreen(viewModel: VaultViewModel) { }

// ✅ Good: Private helper
@Composable
private fun VaultListItem(vault: Vault) { }
```

### Parameter order:
```kotlin
// ✅ Standard order: required → optional → callbacks
@Composable
fun CustomButton(
  text: String,                    // required
  onClick: () -> Unit,             // callback
  modifier: Modifier = Modifier,   // optional
  enabled: Boolean = true          // optional
) { }
```

### State management:
```kotlin
// ✅ State lives in ViewModel, not Composable
@Composable
fun VaultScreen(viewModel: VaultViewModel) {
  val vaults by viewModel.vaults.collectAsState()  // collect from ViewModel
  
  // Never:
  // val vaults = remember { mutableStateOf(...) }  // ❌ Don't create state here
}
```

---

## Naming Conventions

### Composables:
```kotlin
@Composable
fun VaultScreen() { }           // PascalCase, Screen suffix
@Composable
private fun VaultListItem() { } // PascalCase
```

### Functions:
```kotlin
fun calculateHash() { }         // camelCase
private fun validateEmail() { } // camelCase
```

### Variables & Properties:
```kotlin
val userName: String = ""       // camelCase
private val internalCache = mutableStateOf<Data?>(null)
```

### Classes & Data Models:
```kotlin
class VaultRepository { }       // PascalCase
data class CreateVaultInput(    // PascalCase
  val name: String,
  val threshold: Int
)

private data class InternalState(  // private if internal
  val isLoading: Boolean
)
```

---

## Comments & Documentation

### KDoc for public APIs:
```kotlin
/**
 * Creates a new vault with the given name and threshold.
 *
 * @param input Configuration for vault creation
 * @return Result containing created vault or error
 */
fun createVault(input: CreateVaultInput): Result<Vault>
```

### Inline comments for complex logic:
```kotlin
// ✅ Explain WHY, not WHAT
// We validate threshold before creation to avoid partial state
if (!isValidThreshold(input.threshold)) {
  return Result.failure(...)
}

// ❌ Don't explain obvious code
val name = input.name  // Set name from input
```

### No commented-out code:
```kotlin
// ❌ Don't leave this
// val oldImplementation = deprecated()
// oldMethod()

// ✅ Delete it, use git history if needed
```

---

## Common Patterns

### Input Models (when 5+ parameters):
```kotlin
data class JoinDeviceInput(
  val deviceName: String,
  val deviceType: DeviceType,
  val publicKey: String,
  val threshold: Int,
  val timeout: Duration,
  val autoApprove: Boolean
)

fun joinDevice(input: JoinDeviceInput): Result<Device>
```

### Result Handling:
```kotlin
// ✅ Use Result<T, E> for business logic
fun validateVault(vault: Vault): Result<ValidVault, ValidationError>

// Handle results clearly
when (val result = validateVault(vault)) {
  is Result.Success -> { /* proceed */ }
  is Result.Failure -> { /* handle error */ }
}
```

### State in ViewModel:
```kotlin
class VaultViewModel(
  private val repository: VaultRepository
) : ViewModel() {
  private val _vaults = MutableStateFlow<List<Vault>>(emptyList())
  val vaults: StateFlow<List<Vault>> = _vaults.asStateFlow()
  
  fun loadVaults() {
    viewModelScope.launch {
      _vaults.value = repository.getVaults()
    }
  }
}
```

---

## Checklist for Code Review

- [ ] All strings use `appString(AppString.xxx)`
- [ ] All text styles use `AppTextStyles`
- [ ] File is ≤ 400 lines
- [ ] Methods have ≤ 5 parameters (or use Input model)
- [ ] Default visibility is `private`
- [ ] No custom TextStyle definitions
- [ ] No inline colors (use theme)
- [ ] Composables are at top-level or `private`
- [ ] No commented-out code
- [ ] State flows down, events flow up


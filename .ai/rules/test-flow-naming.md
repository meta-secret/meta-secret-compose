# Test Flow Naming Rules

Naming convention for Maestro test files.

---

## File Naming Pattern

All Maestro test files go in: `.maestro/`

### 1. Cross-Platform Test
```
<test-name>.yaml
```

**Runs on:** iOS AND Android (using same YAML)

**Example:**
- `onboarding.yaml` — runs on both platforms
- `login-flow.yaml` — runs on both platforms
- `vault-creation.yaml` — runs on both platforms

**When to use:** Tests that have identical UI flow on both platforms

---

### 2. Android-Only Test
```
android-<test-name>.yaml
```

**Runs on:** Android only

**Example:**
- `android-back-button.yaml` — tests Android back behavior
- `android-permissions.yaml` — tests Android permissions flow

**When to use:** Android-specific features or behaviors

---

### 3. iOS-Only Test
```
ios-<test-name>.yaml
```

**Runs on:** iOS only

**Example:**
- `ios-biometry-setup.yaml` — iOS biometry setup
- `ios-notification-prompt.yaml` — iOS notification permission prompt

**When to use:** iOS-specific features or behaviors

---

## Test Name Rules

### Good Test Names
✅ `onboarding.yaml` — Clear, describes the flow
✅ `join-device.yaml` — Action-based, easy to understand
✅ `login-with-email.yaml` — Specific feature
✅ `vault-sharing.yaml` — Feature-specific
✅ `android-deep-linking.yaml` — Platform + feature

### Bad Test Names
❌ `test.yaml` — Too generic
❌ `test1.yaml` — Not descriptive
❌ `user-flow-part-2.yaml` — Vague reference
❌ `ui-check.yaml` — Not specific enough
❌ `android_onboarding.yaml` — Wrong separator (use dash, not underscore)

---

## Naming Conventions

### Use Dashes (Not Underscores)
```
✅ login-flow.yaml
❌ login_flow.yaml

✅ android-permissions.yaml
❌ android_permissions.yaml
```

### Use Lowercase
```
✅ vault-creation.yaml
❌ Vault-Creation.yaml
❌ VaultCreation.yaml
```

### Platform Prefix Position
```
✅ android-join-device.yaml  ← platform first
❌ join-device-android.yaml  ← platform last (wrong)
```

---

## Test Name Examples by Feature

| Feature | Cross-Platform | Android | iOS |
|---|---|---|---|
| Onboarding | `onboarding.yaml` | `android-onboarding.yaml` | `ios-onboarding.yaml` |
| Login | `login-flow.yaml` | — | — |
| Join Device | `join-device.yaml` | `android-join-device.yaml` | `ios-join-device.yaml` |
| Settings | `settings-navigation.yaml` | — | — |
| Share Secret | `secret-sharing.yaml` | — | — |
| Biometry | — | — | `ios-biometry-auth.yaml` |
| Back Navigation | — | `android-back-button.yaml` | — |
| Notifications | — | — | `ios-notification-permission.yaml` |

---

## How Agents Use This

### write-test-flow Command

**User input:**
```
write test-flow "User opens app and sees onboarding"
```

**Agent action:**
1. User doesn't specify platform → **cross-platform**
2. Generate test name from description: `user-opens-app` → `onboarding`
3. Create: `.maestro/onboarding.yaml`

**User input:**
```
write test-flow "Android: User taps back button" --android
```

**Agent action:**
1. User specifies `--android` → **Android-only**
2. Generate test name: `android-back-button`
3. Create: `.maestro/android-back-button.yaml`

---

## Directory Listing Example

After creating several tests:

```
.maestro/
├── onboarding.yaml                    ← Cross-platform
├── login-flow.yaml                    ← Cross-platform
├── join-device.yaml                   ← Cross-platform
├── vault-sharing.yaml                 ← Cross-platform
├── android-back-button.yaml           ← Android only
├── android-permissions.yaml           ← Android only
├── ios-biometry-setup.yaml            ← iOS only
├── ios-notification-permission.yaml   ← iOS only
├── android-onboarding.yaml            (existing - created before this system)
└── ios-launch.yaml                    (existing - created before this system)
```

---

## Test Discovery

When user runs:
```bash
check test-flow join-device
```

Agent logic:
1. Look for: `.maestro/join-device.yaml` (cross-platform)
2. If found → run on both platforms
3. If not found → look for:
   - `.maestro/android-join-device.yaml`
   - `.maestro/ios-join-device.yaml`
4. Run on whichever platforms are found

---

## Renaming Tests

If you need to rename a test:

1. Rename file following this guide:
   ```bash
   mv .maestro/old-name.yaml .maestro/new-name.yaml
   ```

2. Update in documentation/comments if referenced

3. Use new name in `check test-flow <new-name>`


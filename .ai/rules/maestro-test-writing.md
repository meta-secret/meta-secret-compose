# Maestro Test Writing Guide

How to write Maestro YAML test flows.

---

## YAML Structure

Every Maestro test has this structure:

```yaml
appId: org.metasecret.vault  # iOS
# OR
appId: metasecret.project.com  # Android

---
- launchApp
- [test steps...]
```

---

## Common Test Steps

### Launch App
```yaml
- launchApp
```

### Tap Element
```yaml
- tapOn:
    text: "Next"
```

Alternative selectors:
```yaml
- tapOn:
    id: "button_next"

- tapOn:
    point: "50%, 50%"
```

### Enter Text
```yaml
- inputText: "hello@example.com"
```

### Wait for Element
```yaml
- waitForAnimationToEnd

- assertVisible:
    text: "Welcome"
```

### Take Screenshot
```yaml
- takeScreenshot: "screen_1"
```

### Scroll
```yaml
- scroll:
    direction: down
    amount: 3
```

### Swipe
```yaml
- swipe:
    direction: left
```

### Check Visibility
```yaml
- assertVisible:
    text: "Home"

- assertNotVisible:
    text: "Loading"
```

### Back Button
```yaml
- pressKey: back  # Android only

- tapOn:
    id: "back_button"  # iOS (explicit tap)
```

---

## Test Examples

### Example 1: Onboarding Flow
```yaml
appId: org.metasecret.vault
---
- launchApp
- waitForAnimationToEnd
- assertVisible:
    text: "Welcome to MetaSecret"
- tapOn:
    text: "Next"
- waitForAnimationToEnd
- assertVisible:
    text: "Create Vault"
- takeScreenshot: "onboarding_complete"
```

### Example 2: Login Flow (iOS)
```yaml
appId: org.metasecret.vault
---
- launchApp
- assertVisible:
    text: "Email"
- inputText: "user@example.com"
- tapOn:
    text: "Continue"
- waitForAnimationToEnd
- assertVisible:
    text: "Biometry"
```

### Example 3: Join Device (Android)
```yaml
appId: metasecret.project.com
---
- launchApp
- tapOn:
    text: "Settings"
- tapOn:
    text: "Add Device"
- assertVisible:
    text: "Device Code"
- takeScreenshot: "join_screen"
```

---

## Cross-Platform Tests

When creating cross-platform test (`<test-name>.yaml`), use **generic selectors**:

✅ Good (works on both):
```yaml
- tapOn:
    text: "Next"

- assertVisible:
    text: "Home"
```

❌ Bad (Android/iOS specific):
```yaml
- pressKey: back  # Android only

- tapOn:
    id: "com.example:id/button"  # Android specific resource ID
```

---

## Platform-Specific Tests

### Android-only test: `android-<name>.yaml`

Can use Android-specific commands:
```yaml
appId: metasecret.project.com
---
- launchApp
- pressKey: back
- tapOn:
    id: "android_specific_id"
```

### iOS-only test: `ios-<name>.yaml`

Can use iOS-specific commands:
```yaml
appId: org.metasecret.vault
---
- launchApp
- tapOn:
    id: "ios_specific_id"
```

---

## Testing Checklist

Before running a test:

- [ ] `appId` is correct (iOS: `org.metasecret.vault`, Android: `metasecret.project.com`)
- [ ] All `text` selectors match UI text exactly
- [ ] `waitForAnimationToEnd` after navigation
- [ ] `assertVisible` after each screen change
- [ ] Screenshots named clearly (`screen_1`, `login_complete`, etc.)
- [ ] No platform-specific IDs in cross-platform tests
- [ ] YAML syntax is valid (check indentation)

---

## Best Practices

1. **Start with launchApp:**
   Every test should begin with `- launchApp`

2. **Wait for animations:**
   After navigation, always add:
   ```yaml
   - waitForAnimationToEnd
   ```

3. **Assert visibility:**
   After navigation, verify you're on the correct screen:
   ```yaml
   - assertVisible:
       text: "Expected Screen Title"
   ```

4. **Use text selectors:**
   Prefer visible text over IDs (more readable, works cross-platform)

5. **One flow per test:**
   Each test file should test ONE feature flow, not multiple scenarios

6. **Name tests clearly:**
   `onboarding.yaml` — obvious what it tests
   `join-device.yaml` — clear purpose

---

## Debugging Tests

### Run with verbose output
```bash
maestro test --verbose .maestro/test.yaml
```

### View UI hierarchy before running test
```bash
maestro hierarchy
```

This shows:
- All visible elements
- Element IDs (for selectors)
- Text content
- Button names

### Common Failures

| Error | Cause | Fix |
|---|---|---|
| "Element not found: Next" | Text doesn't match exactly | Check UI hierarchy for exact text |
| "Timeout waiting for animation" | Animation takes too long | Increase timeout or remove waitForAnimationToEnd |
| "App crashed" | Test action caused crash | Check app logs, simplify test |
| "App not installed" | APK/APP not installed | Run `launch android` or `launch ios` first |


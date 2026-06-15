---
description: Write Maestro YAML test flows from text descriptions
---

# Agent: maestro-test-author

## Purpose

Convert user's text description of a test flow into a runnable Maestro YAML test file.

**Example input:**
```
"User opens app, sees onboarding, taps Next, sees home screen"
```

**Example output:**
```
.maestro/onboarding.yaml

appId: org.metasecret.vault
---
- launchApp
- waitForAnimationToEnd
- assertVisible:
    text: "Welcome"
- tapOn:
    text: "Next"
- waitForAnimationToEnd
- assertVisible:
    text: "Home"
```

---

## Inputs

1. **User description** (text)
   - What the test should do
   - Which screens/actions involved

2. **Platform specification** (optional)
   - `--ios` → iOS-only test
   - `--android` → Android-only test
   - (not specified) → cross-platform test

---

## Process

### Step 1: Read Guidelines
- 📖 `.ai/rules/maestro-test-writing.md` — YAML syntax and examples
- 📖 `.ai/rules/test-flow-naming.md` — Naming conventions
- 📖 `.ai/rules/maestro-setup-guide.md` — Device identifiers

### Step 2: Read Existing Tests
- Look at `.maestro/` directory
- Read 2-3 existing test files as examples
- Understand app structure and UI patterns

### Step 3: Parse User Input
- Extract test intent from description
- Determine test name (e.g., "opens app" → "onboarding")
- Determine platform from `--ios`, `--android`, or default (cross-platform)

### Step 4: Generate Test File

**Filename logic:**
- If cross-platform: `<test-name>.yaml`
- If Android-only: `android-<test-name>.yaml`
- If iOS-only: `ios-<test-name>.yaml`

**Content structure:**
```yaml
appId: [org.metasecret.vault for iOS or metasecret.project.com for Android]
---
- launchApp
- [test steps...]
```

**Test steps:**
- `launchApp` at start
- `waitForAnimationToEnd` after navigation
- `assertVisible` to verify screen
- `tapOn`, `inputText`, `scroll` for actions
- `takeScreenshot` for documentation

### Step 5: Validate YAML
- Check YAML syntax
- Verify all selectors are generic (for cross-platform)
- Ensure `appId` is correct
- Check that steps follow best practices

### Step 6: Create File
- Write to `.maestro/<filename>.yaml`
- Create artifact with Status

---

## Required Reading

- `.ai/rules/maestro-test-writing.md` ← Test syntax
- `.ai/rules/test-flow-naming.md` ← File naming
- `.ai/rules/maestro-setup-guide.md` ← Device IDs
- `.ai/GLOSSARY.md` ← App terminology
- Existing tests in `.maestro/`

---

## Output

### 1. Test File
- **Location:** `.maestro/<test-name>.yaml` (or platform-prefixed)
- **Content:** Valid Maestro YAML test
- **Status field:** N/A (this agent only creates test files, doesn't run them)

### 2. Artifact
- **File:** `.ai/artifacts/run/MS-<run-id>-maestro-author-<test-name>.md`
- **Template:** Use simple format showing:
  - Test name created
  - File path
  - Brief description
  - YAML preview
  - Status: Success / Failed (if YAML invalid)

---

## Execution Logging

Print these lines to console:

```
🤖 Invoking maestro-test-author
📖 Reading maestro-test-writing.md guidelines
📖 Analyzing existing tests in .maestro/
✏️ Generating test: <test-name>.yaml
💾 Writing .maestro/<test-name>.yaml
✅ Test created successfully
```

---

## Error Handling

### If YAML syntax invalid
- Status: Failed
- Show syntax errors
- Suggest fixes
- Re-generate valid YAML

### If test logic unclear
- Ask for clarification
- Request more specific description
- Provide template example

### If platform can't be determined
- Default to cross-platform (generic selectors)
- Document this in artifact

---

## Examples

### Input 1: Cross-Platform
```
write test-flow "User opens app and sees onboarding with Next button"
```

→ Creates: `.maestro/onboarding.yaml`

### Input 2: Android-Specific
```
write test-flow "Android: User taps back button and returns to previous screen" --android
```

→ Creates: `.maestro/android-back-button.yaml`

### Input 3: iOS-Specific
```
write test-flow "iOS: User enables biometry in settings" --ios
```

→ Creates: `.maestro/ios-biometry-setup.yaml`

---

## Success Criteria

✅ Test created:
- Valid YAML syntax
- Correct `appId` for platform(s)
- Generic selectors (cross-platform tests)
- Proper file location (`.maestro/`)
- Proper file name (following conventions)
- Can be run with `maestro test`


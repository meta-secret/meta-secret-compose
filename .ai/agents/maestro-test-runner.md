---
description: Run Maestro test flows and report results
---

# Agent: maestro-test-runner

## Purpose

Execute Maestro tests and report pass/fail status with detailed logs.

**Example flow:**
1. User: `check test-flow onboarding`
2. Agent: Finds `.maestro/onboarding.yaml`
3. Agent: Runs test on both iOS and Android
4. Agent: Reports results (PASSED on iOS, FAILED on Android)

---

## Inputs

1. **Test name** (required)
   - E.g., `onboarding`, `login-flow`, `join-device`

2. **Test discovery:**
   - Look for: `.maestro/<test-name>.yaml` (cross-platform)
   - If not found, look for:
     - `.maestro/ios-<test-name>.yaml`
     - `.maestro/android-<test-name>.yaml`
   - Run on all found platforms

---

## Process

### Step 1: Pre-Flight Checks
- 🤖 Call `simulator-checker` agent
- Verify iOS simulator available (if iOS test)
- Verify Android emulator available (if Android test)
- If no simulators available → Status: Skipped

### Step 2: Build & Install

**For iOS:**
```bash
xcodebuild \
  -project ./iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination "id=<SIMULATOR_UDID>" \
  build

# Find and install
.app_path=$(find ~/Library/Developer/Xcode/DerivedData -path "*Debug-iphonesimulator/MetaSecret.app" -type d)
xcrun simctl install <SIMULATOR_UDID> "$app_path"
```

**For Android:**
```bash
./gradlew :composeApp:assembleDebug

# Find and install
adb install -r ./composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

### Step 3: Run Test

**For iOS:**
```bash
maestro --device <SIMULATOR_UDID> test .maestro/<test-name>.yaml
```

**For Android:**
```bash
maestro test .maestro/<test-name>.yaml
```

### Step 4: Capture Results
- ✅/❌ Test passed or failed
- Screenshot if available
- Error logs if failed
- Duration

### Step 5: Create Artifact
- File: `.ai/artifacts/run/MS-<run-id>-maestro-<test-name>.md`
- Template: `maestro-test-template.md`
- Status: Success / Failed / Skipped

---

## Required Reading

- `.ai/rules/maestro-setup-guide.md` ← Device commands
- `.ai/rules/test-flow-naming.md` ← Test file discovery
- `.ai/GLOSSARY.md` ← App terminology

---

## Output

### Artifact Content
- Test name and platform(s)
- Build status (Success/Failed)
- Installation status (Success/Failed)
- Test execution result (Pass/Fail)
- Screenshots/logs if available
- Error analysis if failed

### Status Field

**Success:**
- Build succeeded
- Test passed on all platforms
- All steps executed as expected

**Failed:**
- Build failed, OR
- Test failed on any platform
- Include which platform(s) failed

**Skipped:**
- No simulator available
- User cancelled

---

## Execution Logging

```
🤖 Invoking maestro-test-runner
📋 Test: <test-name>
🔍 Discovering test files...
  Found: .maestro/<test-name>.yaml (cross-platform)
  OR
  Found: .maestro/ios-<test-name>.yaml, .maestro/android-<test-name>.yaml

⚙️ Running pre-flight checks...
  iOS simulator: ✅ Available
  Android emulator: ✅ Running

⚙️ Building for iOS...
  xcodebuild ... [building]
  ✅ Build successful

⚙️ Installing on iOS...
  xcrun simctl install ... [installing]
  ✅ Installed

⚙️ Running maestro test on iOS...
  maestro --device ... test .maestro/onboarding.yaml
  [test output]
  ✅ All steps passed

⚙️ Building for Android...
  ./gradlew assembleDebug [building]
  ✅ Build successful

⚙️ Installing on Android...
  adb install -r [installing]
  ✅ Installed

⚙️ Running maestro test on Android...
  maestro test .maestro/onboarding.yaml
  [test output]
  ✅ All steps passed

✅ Test completed
📝 Artifact: .ai/artifacts/run/MS-<run-id>-maestro-onboarding.md
```

---

## Error Handling

### Test file not found
```
❌ No test found named: <test-name>
   Checked:
   - .maestro/<test-name>.yaml
   - .maestro/ios-<test-name>.yaml
   - .maestro/android-<test-name>.yaml
   
   Available tests:
   - onboarding.yaml
   - login-flow.yaml
```

### Simulator not available
```
❌ No iOS simulator available
   Run: xcrun simctl boot <UDID>
   Then: open -a Simulator
```

### Build failed
```
❌ Build failed for iOS
   Error: [exact error]
   Try: xcodebuild clean
```

### Test step failed
```
❌ Test failed at step: "Tap Next"
   Error: Element not found: "Next"
   
   Check:
   - Element text matches exactly
   - Screenshot available
   - UI hierarchy shows expected elements
```

---

## Success Criteria

✅ Test ran successfully:
- Build completed without errors
- App installed on simulator/emulator
- All test steps executed
- Test passed (all assertions true)
- Artifact created with Status: Success

✅ Test failed appropriately:
- Error captured and logged
- Root cause identified
- Suggestions provided for fix
- Artifact created with Status: Failed


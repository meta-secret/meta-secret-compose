# Maestro Test: <test-name>

**Status:** Success | Failed | Skipped

---

## Test Details

- **Test name:** [onboarding, join-device, etc.]
- **Test file:** `.maestro/[test-name].yaml`
- **Platforms:** iOS / Android / Both
- **Duration:** [seconds]
- **Execution date:** [timestamp]
- **Simulator/Emulator:** [device name and UDID]

---

## Build & Install Info

If applicable:
- **Build command:** `./gradlew :composeApp:assembleDebug` (Android) or `xcodebuild ...` (iOS)
- **Build status:** ✅ Success / ❌ Failed
- **Build duration:** [seconds]
- **App installed on:** [device]
- **Installation status:** ✅ Success / ❌ Failed

---

## Test Execution

### Steps Executed

1. [Step 1]: Launch app
   - Result: ✅ Pass / ❌ Fail
   
2. [Step 2]: [Action]
   - Result: ✅ Pass / ❌ Fail
   
3. [Step 3]: [Action]
   - Result: ✅ Pass / ❌ Fail

### Overall Result

✅ **All steps passed** — Test successful

or

❌ **Failed at step 2** — [Element not found / Assertion failed / App crashed]

---

## Error Details (if failed)

### Step that failed
- **Step:** [which step]
- **Expected:** [what should happen]
- **Actual:** [what happened]
- **Error message:** [exact error]
- **UI state:** [what was on screen]

### Root cause
[Analysis of why it failed]

### Suggested fix
[How to fix the test or the app]

---

## Screenshots

If available:
- `screenshot_1.png` — Launch screen
- `screenshot_2.png` — Onboarding screen
- `screenshot_failure.png` — Where test failed

---

## Logs

### Maestro output
```
[maestro test output]
...
Test PASSED / FAILED
```

### App logs (if error)
```
[logcat / Xcode logs if available]
```

---

## Conclusion

✅ Test is **ready for production** / ❌ Test **needs debugging** / 🔄 Test **partially working**

Next steps:
- [ ] Review failed steps
- [ ] Update test YAML if needed
- [ ] Re-run test
- [ ] Check app for bugs if test logic is correct


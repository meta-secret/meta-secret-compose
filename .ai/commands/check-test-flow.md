---
description: Run a Maestro test flow
---

# Command: check-test-flow

## Trigger

```
check test-flow <test-name>
check test-flow onboarding
check test-flow android-back-button
check test-flow ios-biometry
```

## Purpose

Execute Maestro test and report results.

## Flow

1. Read `.ai/agents/simulator-checker.md`
2. Invoke simulator-checker agent (check device availability)
3. Read `.ai/agents/maestro-test-runner.md`
4. Invoke maestro-test-runner agent with:
   - Test name (discover file from `.maestro/`)
   - Build + install + run + report
5. Create artifact with Status

## Expected Input

- **Test name** (required)
  - `onboarding` — looks for `.maestro/onboarding.yaml`
  - `android-join-device` — looks for `.maestro/android-join-device.yaml`
  - `ios-biometry` — looks for `.maestro/ios-biometry.yaml`

## Expected Output

- Artifact: `.ai/artifacts/run/MS-<run-id>-maestro-<test-name>.md`
- Status: Success / Failed / Skipped
- Test results with logs and screenshots (if available)

## Examples

```bash
# Run cross-platform test
check test-flow onboarding
→ Runs on iOS and Android

# Run Android-only test
check test-flow android-back-button
→ Runs on Android only

# Run iOS-only test
check test-flow ios-biometry
→ Runs on iOS only
```

## Process

1. Check simulator/emulator availability
2. Build app (if not already built)
3. Install app on device(s)
4. Run maestro test
5. Report results (PASSED/FAILED)
6. Show logs and screenshots

## What Happens on Failure

- Error message shown
- Root cause analysis provided
- Logs captured
- Screenshots (if available)
- Suggestion for fix

## Notes

- Pre-builds app (Debug configuration)
- Works on iOS and Android simultaneously (if cross-platform)
- Cleans up between runs (optional)
- See `.ai/rules/test-flow-naming.md` for test discovery logic


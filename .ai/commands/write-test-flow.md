---
description: Write a new Maestro test flow from description
---

# Command: write-test-flow

## Trigger

```
write test-flow "<description>"
write test-flow "<description>" --ios
write test-flow "<description>" --android
```

## Purpose

Convert text description of a test into a runnable Maestro YAML file.

## Flow

1. Read `.ai/agents/maestro-test-author.md`
2. Invoke maestro-test-author agent with:
   - User's description
   - Platform flag (if provided)
3. Agent creates `.maestro/<test-name>.yaml`
4. Create artifact with Status

## Expected Input

- **Description:** What the test should do
  - "User opens app, sees onboarding, taps Next"
  - "iOS: User enables biometry in settings"
  - "Android: User taps back button"

- **Platform (optional):**
  - `--ios` → iOS-only test
  - `--android` → Android-only test
  - (nothing) → cross-platform test

## Expected Output

- Maestro YAML file at `.maestro/<test-name>.yaml` (or platform-prefixed)
- Artifact: `.ai/artifacts/run/MS-<run-id>-maestro-author-<test-name>.md`
- Status: Success / Failed

## Examples

```bash
# Cross-platform test
write test-flow "User opens app and sees home screen"
→ Creates: .maestro/home-screen.yaml

# iOS-only
write test-flow "User enables biometry" --ios
→ Creates: .maestro/ios-biometry.yaml

# Android-only
write test-flow "User taps back button" --android
→ Creates: .maestro/android-back-button.yaml
```

## Notes

- Agent auto-generates test name from description
- Platform defaults to cross-platform (generic selectors)
- See `.ai/rules/maestro-test-writing.md` for test syntax
- See `.ai/rules/test-flow-naming.md` for naming conventions


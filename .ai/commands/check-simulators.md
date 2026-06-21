---
description: Check availability of iOS simulators and Android emulators
---

# Command: check-simulators

## Trigger

```
check-simulators
check-simulators ios
check-simulators android
```

## Purpose

View which simulators/emulators are available and ready to use.

## Flow

1. Read `.ai/agents/simulator-checker.md`
2. Invoke simulator-checker agent with:
   - Platform filter (ios, android, or both)
3. Report which devices are available
4. Show commands to boot/start if needed

## Expected Input

- **Platform (optional):**
  - `ios` → check only iOS simulators
  - `android` → check only Android emulators
  - (nothing) → check both

## Expected Output

- Artifact: `.ai/artifacts/run/MS-<run-id>-simulator-check.md`
- Status: Available Devices Found / No Devices Found / Skipped
- List of devices with status (booted, running, offline, shutdown)

## Examples

```bash
# Check both
check-simulators
→ Lists iOS simulators + Android emulators

# Check iOS only
check-simulators ios
→ iPhone 14 Pro (BOOTED) ✅
   iPhone 13 (SHUTDOWN)
   iPad Pro (OFFLINE)

# Check Android only
check-simulators android
→ Pixel_5 (RUNNING) ✅
   Pixel_4a (AVAILABLE)
   Pixel_6 (OFFLINE)
```

## Output Format

### iOS Simulators
- Device name (iPhone 14 Pro)
- iOS version (17.0)
- UDID (BF38FBB6-...)
- Status (🟢 Booted / 🟡 Shutdown / 🔴 Offline)

### Android Emulators
- AVD name (Pixel_5)
- Status (🟢 Running / 🟡 Available / 🔴 Offline)
- ADB connection status

### Recommendations
- Which device to use for testing
- Commands to boot/start if needed

## When to Use

- Before `write test-flow` (to verify devices available)
- Before `check test-flow` (simulator-checker runs automatically)
- Troubleshooting device issues
- Planning test runs

## Notes

- Non-destructive (only checks, doesn't change state)
- Shows how to boot/start devices if offline
- See `.ai/rules/maestro-setup-guide.md` for detailed device setup


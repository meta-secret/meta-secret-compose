---
description: Check availability of iOS simulators and Android emulators
---

# Agent: simulator-checker

## Purpose

Check which iOS simulators and Android emulators are available and ready to use.

**Example output:**
```
✅ iOS: iPhone 14 Pro (booted, ready)
✅ Android: Pixel_5 (running, ready)
```

---

## Inputs

1. **Platform filter** (optional)
   - `ios` → check only iOS
   - `android` → check only Android
   - (not specified) → check both

---

## Process

### Step 1: Read Setup Guide
- 📖 `.ai/rules/maestro-setup-guide.md` — All device commands

### Step 2: Check iOS Simulators

**List available:**
```bash
xcrun simctl list devices available
```

**Parse output:**
- Device name (iPhone 14 Pro, iPhone 13, etc.)
- iOS version
- UDID
- Booted status

**Check boot status:**
- 🟢 Booted = Ready
- 🟡 Shutdown = Can be booted
- 🔴 Offline = Not available

### Step 3: Check Android Emulators

**List available AVDs:**
```bash
~/Library/Android/sdk/emulator/emulator -list-avds
```

**List running emulators:**
```bash
~/Library/Android/sdk/platform-tools/adb devices
```

**Parse output:**
- AVD name (Pixel_4a, Pixel_5, etc.)
- ADB status (device, offline)
- Connection state

### Step 4: Report Status

**For each device:**
- Device name
- Status (Available/Running/Offline)
- UDID or identifier
- Ready for testing (YES/NO)

### Step 5: Provide Recommendations

**If iOS not ready:**
```bash
# Boot simulator
xcrun simctl boot <UDID>

# Open Simulator app
open -a Simulator
```

**If Android not ready:**
```bash
# Start emulator
~/Library/Android/sdk/emulator/emulator @<AVD_NAME>

# Verify connection
~/Library/Android/sdk/platform-tools/adb devices
```

### Step 6: Create Artifact
- File: `.ai/artifacts/run/MS-<run-id>-simulator-check.md`
- Template: `simulator-check-template.md`
- Status: Available Devices Found / No Devices Found / Skipped

---

## Required Reading

- `.ai/rules/maestro-setup-guide.md` ← All commands

---

## Output

### Artifact Format

```markdown
# Simulator / Emulator Availability Check

**Status:** Available Devices Found | No Devices Found

---

## Android Emulators

| AVD Name | Status | ADB Connection |
|---|---|---|
| Pixel_4a | 🟢 Available | — |
| Pixel_5 | 🟢 Running | Connected |

...

## iOS Simulators

| Device Name | iOS Version | UDID | Status |
|---|---|---|---|
| iPhone 14 Pro | 17.0 | BF38FB... | 🟢 Booted |

...

## Ready to Run Tests

✅ Android: Can run on Pixel_5
✅ iOS: Can run on iPhone 14 Pro
```

---

## Execution Logging

```
🤖 Invoking simulator-checker
📖 Reading maestro-setup-guide.md

⚙️ Checking iOS simulators...
  xcrun simctl list devices available
  Found:
  - iPhone 14 Pro (17.0) — BOOTED ✅
  - iPhone 13 (16.5) — SHUTDOWN
  - iPad Pro (17.0) — OFFLINE

⚙️ Checking Android emulators...
  emulator -list-avds
  Available:
  - Pixel_4a
  - Pixel_5
  - Pixel_6
  
  adb devices
  Running:
  - Pixel_5 ✅

✅ Check complete
📝 Artifact: .ai/artifacts/run/MS-<run-id>-simulator-check.md
```

---

## Error Cases

### No iOS simulators available
```
❌ No iOS simulators found

To create one:
1. Open Xcode
2. Preferences > Devices and Simulators
3. Click + to add simulator
```

### No Android emulators
```
❌ No Android AVDs found

To create one:
~/Library/Android/sdk/tools/bin/sdkmanager --list
```

### Simulator/Emulator offline
```
⚠️ iPhone 14 Pro is SHUTDOWN
   Run: xcrun simctl boot BF38FBB6-DC04-4487-BA55-1C9420592FE8
   
⚠️ Pixel_5 is OFFLINE
   Try: killall qemu-system-x86_64
        emulator @Pixel_5
```

---

## Success Criteria

✅ Status: **Available Devices Found**
- At least one iOS simulator available (booted or can be booted)
- At least one Android emulator available (running or can be started)
- Full list of devices with details
- Recommendations for next steps

✅ Status: **No Devices Found**
- Clearly lists what's missing
- Provides instructions to set up devices
- Suggests which devices to create/boot


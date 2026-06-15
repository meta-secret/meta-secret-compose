# Simulator / Emulator Availability Check

**Status:** Available Devices Found | No Devices Found | Skipped

---

## Check Date & Time

- **Executed:** [timestamp]
- **Duration:** [seconds]

---

## Android Emulators

### Available AVDs
```
Pixel_4a
Pixel_5
Pixel_6
```

### Emulator Status

| AVD Name | Status | ADB Connection |
|---|---|---|
| Pixel_4a | 🟢 Available (not running) | — |
| Pixel_5 | 🟢 Running | Connected |
| Pixel_6 | 🔴 Offline | Not reachable |

### Recommendation
✅ Can run Android tests on: `Pixel_5` (already running)

If not running:
```bash
~/Library/Android/sdk/emulator/emulator @Pixel_4a
```

---

## iOS Simulators

### Available Simulators

| Device Name | iOS Version | UDID | Status |
|---|---|---|---|
| iPhone 14 Pro | 17.0 | BF38FBB6-DC04... | 🟢 Booted |
| iPhone 13 | 16.5 | CD49GC12-AC15... | 🟡 Shutdown |
| iPad Pro 12.9 | 17.0 | EA91HD78-BC26... | 🟡 Shutdown |

### Recommendation
✅ Can run iOS tests on: `iPhone 14 Pro` (already booted)

If not booted:
```bash
xcrun simctl boot BF38FBB6-DC04-4487-BA55-1C9420592FE8
open -a Simulator
```

---

## Ready to Run Tests

### Android
- ✅ Emulator available: `Pixel_5`
- ✅ Can run Android Maestro tests

### iOS
- ✅ Simulator available: `iPhone 14 Pro`
- ✅ Can run iOS Maestro tests

### Overall
✅ **Both platforms ready for testing**

or

❌ **Missing iOS simulators** — Please boot simulator first

or

❌ **Missing Android emulators** — Please start emulator with: `~/Library/Android/sdk/emulator/emulator @Pixel_4a`

---

## Device Details

### Android (Pixel_5)
- **ADB Status:** Connected
- **App ID:** metasecret.project.com
- **Can install APK:** ✅ Yes

### iOS (iPhone 14 Pro)
- **Bundle ID:** org.metasecret.vault
- **iOS Version:** 17.0
- **Can install APP:** ✅ Yes

---

## Troubleshooting

If no devices found:

### For Android
```bash
# Start emulator
~/Library/Android/sdk/emulator/emulator -list-avds
~/Library/Android/sdk/emulator/emulator @<AVD_NAME>

# Check connection
~/Library/Android/sdk/platform-tools/adb devices
```

### For iOS
```bash
# List simulators
xcrun simctl list devices available

# Boot simulator
xcrun simctl boot <UDID>

# Open Simulator app
open -a Simulator
```

---

## Next Steps

✅ Ready to run: `write test-flow` or `check test-flow`

or

❌ Need to prepare: Start emulator/simulator first, then re-run this check


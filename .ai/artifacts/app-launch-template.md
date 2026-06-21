# App Launch: iOS / Android

**Status:** Success | Failed | Skipped

---

## Launch Details

- **Platform:** iOS / Android
- **Build type:** Debug
- **Execution date:** [timestamp]
- **Total duration:** [seconds]

---

## Build Stage

| Step | Status | Duration | Details |
|---|---|---|---|
| Check simulator/emulator | ✅ / ❌ | [s] | [device found or error] |
| Build app | ✅ / ❌ | [s] | [./gradlew or xcodebuild] |
| Find built app | ✅ / ❌ | [s] | [APK path or APP path] |
| Install on device | ✅ / ❌ | [s] | [adb install or simctl install] |
| Launch app | ✅ / ❌ | [s] | [adb shell am start or simctl launch] |

---

## iOS-Specific (if applicable)

- **Xcode Project:** `./iosApp/iosApp.xcodeproj`
- **Scheme:** `iosApp`
- **Configuration:** `Debug`
- **SDK:** `iphonesimulator`

### Build output
```
xcodebuild \
  -project ./iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'id=BF38FBB6-DC04-4487-BA55-1C9420592FE8' \
  build
```

### App Location
```
~/Library/Developer/Xcode/DerivedData/iosApp-xxx/Build/Products/Debug-iphonesimulator/MetaSecret.app
```

---

## Android-Specific (if applicable)

- **Module:** `composeApp`
- **Build type:** `assembleDebug`
- **Configuration:** `Debug`

### Build output
```
./gradlew :composeApp:assembleDebug
```

### APK Location
```
./composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

---

## Device Info

### iOS
- **Simulator:** iPhone 14 Pro
- **Simulator UDID:** BF38FBB6-DC04-4487-BA55-1C9420592FE8
- **Bundle ID:** org.metasecret.vault
- **iOS Version:** 17.0

### Android
- **Emulator:** Pixel_5
- **ADB Serial:** emulator-5554
- **App ID:** metasecret.project.com
- **Android Version:** 13

---

## Launch Result

### ✅ SUCCESS

```
App is running on simulator/emulator
Device is ready for:
- Manual testing
- Maestro automated tests
- Development
```

**What to do next:**
- Test the app manually
- Run Maestro tests: `check test-flow <test-name>`
- Continue development

### ❌ FAILED

**Step where failed:** [Build / Install / Launch]

**Error message:**
```
[exact error output]
```

**Root cause:** [analysis]

**Fix:** [suggested solution]

---

## Build Logs

### Compilation output
```
[./gradlew or xcodebuild output]
...
BUILD SUCCESSFUL / FAILED
```

### Installation logs (if failed)
```
[adb or simctl error output]
```

---

## Troubleshooting

| Error | Solution |
|---|---|
| "Simulator not found" | Boot simulator: `xcrun simctl boot <UDID>` |
| "Emulator not responding" | Kill and restart: `killall qemu-system-x86_64` then `emulator @<AVD>` |
| "APK installation failed" | Clear app: `adb shell pm clear metasecret.project.com` then retry |
| "Build failed" | Check `gradlew :composeApp:clean` then rebuild |
| "App crashes on launch" | Check logs: `adb logcat` (Android) or Xcode console (iOS) |

---

## Next Steps

✅ If successful:
- [ ] App is running
- [ ] Can test manually
- [ ] Can run Maestro tests

❌ If failed:
- [ ] Check error message above
- [ ] Follow troubleshooting steps
- [ ] Run `launch ios` or `launch android` again


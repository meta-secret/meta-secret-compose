# Maestro Setup Guide

All commands for Android and iOS device management and Maestro testing.

---

## Android Emulator Commands

### List available emulators
```bash
~/Library/Android/sdk/emulator/emulator -list-avds
```

### Start emulator (first available)
```bash
~/Library/Android/sdk/emulator/emulator @<AVD_NAME>
```

Example:
```bash
~/Library/Android/sdk/emulator/emulator @Pixel_4a
```

### Build Debug APK
```bash
cd meta-secret-compose
./gradlew :composeApp:assembleDebug
```

Output location:
```
./composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

### Find APK file
```bash
find . -name "composeApp-debug.apk"
```

### Find Android application ID
```bash
~/Library/Android/sdk/platform-tools/adb shell pm list packages | grep -i meta
```

Current app ID:
```
metasecret.project.com
```

### Install APK on emulator
```bash
~/Library/Android/sdk/platform-tools/adb install -r \
./composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

### Launch app on emulator
```bash
~/Library/Android/sdk/platform-tools/adb shell am start -n \
metasecret.project.com/metasecret.project.com.MainActivity
```

### Run Maestro test
```bash
maestro test .maestro/android-onboarding.yaml
```

### View UI hierarchy
```bash
maestro hierarchy
```

### Get list of connected Android devices
```bash
~/Library/Android/sdk/platform-tools/adb devices
```

---

## iOS Simulator Commands

### List available simulators
```bash
xcrun simctl list devices available
```

### Boot simulator by UDID
```bash
xcrun simctl boot BF38FBB6-DC04-4487-BA55-1C9420592FE8
```

### Open Simulator app
```bash
open -a Simulator
```

### Find Xcode project
```bash
find . -name "*.xcodeproj"
```

### List build schemes
```bash
xcodebuild -project ./iosApp/iosApp.xcodeproj -list
```

### Build app for simulator
```bash
xcodebuild \
  -project ./iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'id=BF38FBB6-DC04-4487-BA55-1C9420592FE8' \
  build
```

Replace UDID with your simulator ID.

### Find built .app file
```bash
find ~/Library/Developer/Xcode/DerivedData \
  -path "*Debug-iphonesimulator/MetaSecret.app" \
  -type d
```

### Install app on simulator
```bash
xcrun simctl install \
  BF38FBB6-DC04-4487-BA55-1C9420592FE8 \
  ~/Library/Developer/Xcode/DerivedData/iosApp-xxx/Build/Products/Debug-iphonesimulator/MetaSecret.app
```

### Find iOS Bundle ID
```bash
xcrun simctl listapps \
  BF38FBB6-DC04-4487-BA55-1C9420592FE8 \
  | grep -i metasecret
```

Current Bundle ID:
```
org.metasecret.vault
```

### Launch app on simulator
```bash
xcrun simctl launch \
  BF38FBB6-DC04-4487-BA55-1C9420592FE8 \
  org.metasecret.vault
```

### Run Maestro test
```bash
maestro --device BF38FBB6-DC04-4487-BA55-1C9420592FE8 \
  test .maestro/ios-launch.yaml
```

### View UI hierarchy
```bash
maestro --device BF38FBB6-DC04-4487-BA55-1C9420592FE8 \
  hierarchy
```

### List all simulators (with status)
```bash
xcrun simctl list devices
```

---

## Key Device Identifiers

### Android
- **App ID:** `metasecret.project.com`
- **Main Activity:** `metasecret.project.com.MainActivity`
- **SDK Location:** `~/Library/Android/sdk/`

### iOS
- **Bundle ID:** `org.metasecret.vault`
- **Xcode Project:** `./iosApp/iosApp.xcodeproj`
- **Scheme:** `iosApp`

---

## Minimal Maestro YAML Templates

### Android
```yaml
appId: metasecret.project.com
---
- launchApp
```

### iOS
```yaml
appId: org.metasecret.vault
---
- launchApp
```

---

## Troubleshooting

### Emulator not starting
```bash
# Kill all emulator processes
killall qemu-system-x86_64

# Try again
~/Library/Android/sdk/emulator/emulator @Pixel_4a
```

### Simulator stuck
```bash
# Kill simulator
pkill -9 com.apple.CoreSimulator.CoreSimulatorService

# Open again
open -a Simulator
```

### APK installation fails
```bash
# Clear app data
~/Library/Android/sdk/platform-tools/adb shell pm clear metasecret.project.com

# Try install again
~/Library/Android/sdk/platform-tools/adb install -r ./composeApp/build/outputs/apk/debug/composeApp-debug.apk
```


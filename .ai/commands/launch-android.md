---
description: Build, install, and launch MetaSecret app on Android emulator
---

# Command: launch-android

## Trigger

```
launch android
```

## Purpose

One-command Android app launch for manual testing and development.

Builds Debug APK → installs on emulator → launches app.

## Flow

1. Read `.ai/agents/simulator-checker.md`
2. Invoke simulator-checker (verify Android emulator available)
3. Read `.ai/agents/app-launcher-android.md`
4. Invoke app-launcher-android agent to:
   - Build Debug APK
   - Install on emulator
   - Launch app
5. Create artifact with Status

## Expected Input

None. Just `launch android`

## Expected Output

- Artifact: `.ai/artifacts/run/MS-<run-id>-launch-android.md`
- Status: Success / Failed / Skipped
- App running on Android emulator

## What Happens

```
1. Check: Is Android emulator available? (running or can start)
   YES -> continue
   NO -> Status: Skipped

2. Build: ./gradlew :composeApp:assembleDebug
   SUCCESS -> continue
   FAILED -> Status: Failed (show error)

3. Install: adb install -r composeApp-debug.apk
   SUCCESS -> continue
   FAILED -> Status: Failed (show error)

4. Launch: adb shell am start -n metasecret.project.com/MainActivity
   SUCCESS -> App running (Status: Success)
   FAILED -> Status: Failed (show error)
```

## Example Output

```
Invoking app-launcher-android
Checking Android emulator...
  Pixel_5 (emulator-5554) OK

Building app...
  ./gradlew :composeApp:assembleDebug
  Build successful (60s)

Installing app...
  adb install -r composeApp-debug.apk
  Installation successful

Launching app...
  adb shell am start -n metasecret.project.com/MainActivity
  App launched

App is running on Pixel_5
```

## Use Cases

- Quick manual testing after code changes
- Development testing
- Screenshots/videos for documentation
- Before running Maestro tests

## Notes

- Builds current code with all changes
- Uses Debug configuration
- Installs fresh (overwrites previous version)
- See .ai/rules/maestro-setup-guide.md for Android setup


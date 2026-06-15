---
description: Build, install, and launch MetaSecret app on Android emulator
---

# Agent: app-launcher-android

## Purpose

One-command Android app launch:
1. Check emulator availability
2. Build Debug APK
3. Install on emulator
4. Launch app
5. Report status

Perfect for manual testing and development.

---

## Inputs

None. Just `launch android`

---

## Process

### Step 1: Pre-Flight Check
- 🤖 Call `simulator-checker` agent (Android only)
- If no emulator available → Status: Skipped
- If available → continue

### Step 2: Build APK

```bash
cd meta-secret-compose

./gradlew :composeApp:assembleDebug
```

**Expected output location:**
```
./composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

**Log:**
- ✅ Build successful (duration)
- ❌ Build failed (error message)

### Step 3: Find APK

```bash
find . -name "composeApp-debug.apk" -type f
```

**Result:**
- Found at: `./composeApp/build/outputs/apk/debug/composeApp-debug.apk`
- Not found → Status: Failed

### Step 4: Install on Emulator

```bash
~/Library/Android/sdk/platform-tools/adb install -r \
./composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

**Log:**
- ✅ Installation successful
- ❌ Installation failed (error message)

### Step 5: Launch App

First, find the main Activity:

```bash
aapt dump badging ./composeApp/build/outputs/apk/debug/composeApp-debug.apk | grep "launchable-activity"
```

Then launch:

```bash
~/Library/Android/sdk/platform-tools/adb shell am start -n \
metasecret.project.com/metasecret.project.com.MainActivity
```

**Log:**
- ✅ App launched
- ❌ Launch failed (error)

### Step 6: Create Artifact

- File: `.ai/artifacts/run/MS-<run-id>-launch-android.md`
- Template: `app-launch-template.md`
- Status: Success / Failed / Skipped

---

## Required Reading

- `.ai/rules/maestro-setup-guide.md` ← Android commands
- `.ai/GLOSSARY.md` ← App terminology

---

## Output

### Console Output

```
🤖 Invoking app-launcher-android
⚙️ Checking Android emulator availability...
  Pixel_5 (emulator-5554) — Running ✅

⚙️ Building MetaSecret for Android...
  ./gradlew :composeApp:assembleDebug
  Building Debug APK...
  ✅ Build successful (60 seconds)

⚙️ Finding APK...
  Searching for composeApp-debug.apk...
  Found: ./composeApp/build/outputs/apk/debug/composeApp-debug.apk ✅

⚙️ Installing on emulator...
  adb install -r ./composeApp/build/outputs/apk/debug/composeApp-debug.apk
  ✅ Installation successful

⚙️ Launching app...
  adb shell am start -n metasecret.project.com/metasecret.project.com.MainActivity
  ✅ App launched successfully

✅ App is now running on Pixel_5
📝 Artifact: .ai/artifacts/run/MS-<run-id>-launch-android.md
```

### Artifact Content

```markdown
# App Launch: Android

**Status:** Success

---

## Device
- Emulator: Pixel_5
- Serial: emulator-5554
- App ID: metasecret.project.com
- Main Activity: metasecret.project.com.MainActivity

## Build
✅ Success (60s)

## Installation
✅ Success

## Launch
✅ App running

Next: Test manually or run Maestro tests
```

---

## Error Cases

### Emulator not available
```
❌ No Android emulator running
   Start emulator:
   ~/Library/Android/sdk/emulator/emulator @Pixel_4a
```

### Build failed
```
❌ Build failed
   Error: [gradle error]
   Try: ./gradlew clean :composeApp:assembleDebug
```

### APK not found
```
❌ Could not find APK file
   Try: ./gradlew clean :composeApp:assembleDebug
        Then rebuild
```

### Installation failed
```
❌ Installation failed
   Error: [adb error]
   Try: adb uninstall metasecret.project.com
        Then reinstall
```

### App crash on launch
```
❌ App crashed immediately
   Check logs: adb logcat | grep metasecret
```

### Activity not found
```
❌ Could not find MainActivity
   Check app structure
   Verify app ID: metasecret.project.com
```

---

## Success Criteria

✅ **App Launched:**
- Build completed
- APK found
- Installation successful
- App running on emulator
- Artifact status: Success

❌ **Failed:**
- Any step failed
- Error captured and logged
- Troubleshooting steps provided
- Artifact status: Failed

🔄 **Skipped:**
- No emulator available
- User cancelled
- Artifact status: Skipped

---

## Next Steps

After successful launch:
- Manual testing in Emulator
- Run Maestro tests: `check test-flow <test-name>`
- Continue development


---
description: Build, install, and launch MetaSecret app on iOS simulator
---

# Agent: app-launcher-ios

## Purpose

One-command iOS app launch:
1. Check simulator availability
2. Build Debug .app
3. Install on simulator
4. Launch app
5. Report status

Perfect for manual testing and development.

---

## Inputs

None. Just `launch ios`

---

## Process

### Step 1: Pre-Flight Check
- 🤖 Call `simulator-checker` agent (iOS only)
- If no simulator available → Status: Skipped
- If available → continue

### Step 2: Build App

```bash
cd meta-secret-compose

xcodebuild \
  -project ./iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination "id=<SIMULATOR_UDID>" \
  build
```

**Log:**
- ✅ Build successful (duration)
- ❌ Build failed (error message)

### Step 3: Find Built App

```bash
find ~/Library/Developer/Xcode/DerivedData \
  -path "*Debug-iphonesimulator/MetaSecret.app" \
  -type d
```

**Result:**
- Found at: `/path/to/MetaSecret.app`
- Not found → Status: Failed

### Step 4: Install on Simulator

```bash
xcrun simctl install \
  <SIMULATOR_UDID> \
  /path/to/MetaSecret.app
```

**Log:**
- ✅ Installation successful
- ❌ Installation failed (error message)

### Step 5: Launch App

```bash
xcrun simctl launch \
  <SIMULATOR_UDID> \
  org.metasecret.vault
```

**Log:**
- ✅ App launched
- ❌ Launch failed (error)

### Step 6: Create Artifact

- File: `.ai/artifacts/run/MS-<run-id>-launch-ios.md`
- Template: `app-launch-template.md`
- Status: Success / Failed / Skipped

---

## Required Reading

- `.ai/rules/maestro-setup-guide.md` ← iOS commands
- `.ai/GLOSSARY.md` ← App terminology

---

## Output

### Console Output

```
🤖 Invoking app-launcher-ios
⚙️ Checking iOS simulator availability...
  iPhone 14 Pro (UDID: BF38FBB6-...) — Ready ✅

⚙️ Building MetaSecret for iOS...
  xcodebuild -project ./iosApp/iosApp.xcodeproj ...
  Building for iphonesimulator...
  ✅ Build successful (45 seconds)

⚙️ Finding built .app...
  Searching in DerivedData...
  Found: ~/Library/Developer/Xcode/DerivedData/iosApp-xxx/Build/Products/Debug-iphonesimulator/MetaSecret.app ✅

⚙️ Installing on simulator...
  xcrun simctl install BF38FBB6-... <path>
  ✅ Installation successful

⚙️ Launching app...
  xcrun simctl launch BF38FBB6-... org.metasecret.vault
  ✅ App launched successfully (pid: 12345)

✅ App is now running on iPhone 14 Pro
📝 Artifact: .ai/artifacts/run/MS-<run-id>-launch-ios.md
```

### Artifact Content

```markdown
# App Launch: iOS

**Status:** Success

---

## Device
- Simulator: iPhone 14 Pro
- UDID: BF38FBB6-DC04-4487-BA55-1C9420592FE8
- Bundle ID: org.metasecret.vault

## Build
✅ Success (45s)

## Installation
✅ Success

## Launch
✅ App running

Next: Test manually or run Maestro tests
```

---

## Error Cases

### Simulator not available
```
❌ No iOS simulator available
   Boot simulator: xcrun simctl boot <UDID>
   Open: open -a Simulator
```

### Build failed
```
❌ Build failed
   Error: [xcodebuild error]
   Try: xcodebuild clean -project ./iosApp/iosApp.xcodeproj
        Then rebuild
```

### .app not found
```
❌ Could not find built .app file
   Try: xcodebuild clean
        Then rebuild
```

### Installation failed
```
❌ Installation failed
   Error: [simctl error]
   Try: xcrun simctl uninstall <UDID> org.metasecret.vault
        Then reinstall
```

### App crash on launch
```
❌ App crashed immediately
   Check logs: xcrun simctl spawn <UDID> log stream --predicate 'process == "MetaSecret"'
```

---

## Success Criteria

✅ **App Launched:**
- Build completed
- .app found
- Installation successful
- App running on simulator
- Artifact status: Success

❌ **Failed:**
- Any step failed
- Error captured and logged
- Troubleshooting steps provided
- Artifact status: Failed

🔄 **Skipped:**
- No simulator available
- User cancelled
- Artifact status: Skipped

---

## Next Steps

After successful launch:
- Manual testing in Simulator
- Run Maestro tests: `check test-flow <test-name>`
- Continue development


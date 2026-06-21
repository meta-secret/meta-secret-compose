---
description: Build, install, and launch MetaSecret app on iOS simulator
---

# Command: launch-ios

## Trigger

```
launch ios
```

## Purpose

One-command iOS app launch for manual testing and development.

Builds Debug .app → installs on simulator → launches app.

## Flow

1. Read `.ai/agents/simulator-checker.md`
2. Invoke simulator-checker (verify iOS simulator available)
3. Read `.ai/agents/app-launcher-ios.md`
4. Invoke app-launcher-ios agent to:
   - Build Debug .app
   - Install on simulator
   - Launch app
5. Create artifact with Status

## Expected Input

None. Just `launch ios`

## Expected Output

- Artifact: `.ai/artifacts/run/MS-<run-id>-launch-ios.md`
- Status: Success / Failed / Skipped
- App running on iOS simulator

## What Happens

```
1. Check: Is iOS simulator available? (booted or can boot)
   ✅ YES → continue
   ❌ NO → Status: Skipped

2. Build: xcodebuild -project ... build
   ✅ SUCCESS → continue
   ❌ FAILED → Status: Failed (show error)

3. Install: xcrun simctl install ... MetaSecret.app
   ✅ SUCCESS → continue
   ❌ FAILED → Status: Failed (show error)

4. Launch: xcrun simctl launch ... org.metasecret.vault
   ✅ SUCCESS → App running (Status: Success)
   ❌ FAILED → Status: Failed (show error)
```

## Example Output

```
🤖 Invoking app-launcher-ios
⚙️ Checking iOS simulator...
  iPhone 14 Pro (UDID: BF38FBB6-...) ✅

⚙️ Building app...
  xcodebuild -project ./iosApp/iosApp.xcodeproj ...
  ✅ Build successful (45s)

⚙️ Installing app...
  xcrun simctl install BF38FBB6-... MetaSecret.app
  ✅ Installation successful

⚙️ Launching app...
  xcrun simctl launch BF38FBB6-... org.metasecret.vault
  ✅ App launched (pid: 12345)

✅ App is running on iPhone 14 Pro
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
- See `.ai/rules/maestro-setup-guide.md` for iOS setup


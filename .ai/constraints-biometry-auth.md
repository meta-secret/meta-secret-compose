# Constraints: Biometry & Authorization

See CONSTRAINTS.md for quick reference. This file has full details.

**Use this when planning:** Biometry, approval flows, PIN fallback, biometry enrollment changes.

---

## 1. Biometry as Confirmation

**MetaSecret does NOT:**
- Store biometric data (fingerprint, face model)
- Implement biometric comparison
- Decrypt keys using biometry directly

**MetaSecret DOES:**
- Call OS biometric API
- Get "approved" or "denied" response
- Proceed only if approved

**Examples:**
- iOS: Face ID / Touch ID via `LocalAuthentication` framework
- Android: `BiometricPrompt` system dialog

---

## 2. System PIN as Fallback

**If biometry unavailable:**
- Too many failed attempts (rate limit)
- Finger wet / face not recognized
- User didn't set up biometry
- Biometry disabled in settings

**Fallback:** System PIN / pattern / face unlock

**Important:** Not a MetaSecret PIN (that's forbidden).

---

## 3. No Separate MetaSecret PIN

**Forbidden:**
- ❌ No MetaSecret-specific password
- ❌ No MetaSecret PIN code
- ❌ No MetaSecret pattern unlock
- ❌ No "sign in" flow with credentials

**Allowed only:**
- ✅ System biometry (Face ID, Touch ID, etc.)
- ✅ System PIN / pattern (OS-level unlock)

**Why?** User shouldn't need to remember separate credentials for MetaSecret.

---

## 4. Operations Requiring Approval

**Biometry/PIN required for:**

| Operation | Why | Approval level |
|---|---|---|
| Join device | Critical | 1 approval |
| Remove device | Critical | 1 approval |
| Recover secret* | Critical | 1 approval |
| View secret value | Sensitive | Device only |
| Operations requiring remote device | Critical | Remote device biometry |

*Only if current device doesn't have enough shares.

---

## 5. Biometry Enrollment Changes

**Scenario:** User adds new fingerprint to phone

```
Before:
  Device has Touch ID with 3 fingerprints
  MetaSecret DMK in Keychain
  
User action:
  Adds 4th fingerprint to phone
  
MetaSecret state:
  ✅ Still works
  ✅ DMK still accessible
  ✅ No re-authentication needed
  ✅ App continues normally
```

**Constraint:** DMK must NOT be invalidated by biometry enrollment changes.

---

## 6. Biometry Removal

**Scenario:** User removes all fingerprints, uses PIN only

```
Before:
  Face ID configured
  
User action:
  Disables Face ID in system settings
  
MetaSecret state:
  ✅ Falls back to PIN automatically
  ✅ No app restart needed
  ✅ App continues normally
```

---

## 7. iOS Keychain Config (Biometry)

**Critical rule:** Don't hardcode biometry requirement

❌ Wrong:
```swift
kSecAttrAccessControl(protection: .userPresenceRequired)
// Works with Face ID, Touch ID, or PIN
// But iOS may cache after biometric enrollment change
```

❌ Also wrong:
```swift
kSecAttrAccessControl(protection: .biometryCurrentSet)
// INVALIDATES when biometry settings change!
```

✅ Right:
```swift
kSecAttrAccessControl(
  protection: .userPresenceRequired,
  flags: .userPresenceRequired
)
// Allows any method (face, touch, PIN)
// Doesn't invalidate on biometry changes
```

---

## 8. iOS Face ID Prompt

**When Face ID is triggered:**
```
User taps "Confirm operation"
  ↓
OS shows: "Use Face ID to approve?"
  ↓
User faces phone (or enters PIN)
  ↓
OS returns: APPROVED / DENIED
  ↓
App proceeds or blocks based on result
```

**App never sees:** Face model, captured image, anything biometric.

---

## 9. Android BiometricPrompt

**When biometry is triggered:**
```
User taps "Confirm operation"
  ↓
System shows: BiometricPrompt dialog
  ↓
User provides fingerprint (or PIN)
  ↓
System returns: success / failure
  ↓
App proceeds or blocks based on result
```

**App never sees:** Fingerprint template, captured data.

---

## 10. Rate Limiting

**After N failed biometry attempts:**
```
Failed attempts: 1, 2, 3, 4, 5
  ↓
At threshold: System rate-limits
  ↓
User gets message: "Use PIN instead"
  ↓
Fallback to PIN unlock
```

**MetaSecret doesn't control this** — it's OS behavior.

---

## 11. Timeout Behavior

**What if user goes offline during approval?**

```
Device A: Initiates join request
  ↓
Device B: Receives request
  ↓
Device B: Prompts biometry (user steps away)
  ↓
15 minutes pass...
  ↓
OS auto-locks phone
  ↓
Device B: Prompt timeout? (OS decides)
  ↓
User unlocks phone
  ↓
Prompt re-appears or request expires
```

**Status:** Exact timeout behavior to be verified per platform.

---

## 12. Multi-User Device (Android)

**On Android multi-user device:**
```
Device registered for User 1
  ↓
User 2 picks up device
  ↓
Biometry / PIN of User 2?
  
Answer: TBD (separate investigation)
```

---

## Decision Checklist: Biometry & Auth

Planning approval/auth feature? Check:

- [ ] Using OS biometry APIs (not custom)?
- [ ] Showing OS system prompts?
- [ ] DMK survives biometry enrollment changes?
- [ ] No MetaSecret-specific PIN?
- [ ] Fallback to system PIN if biometry fails?
- [ ] All critical ops need biometry approval?
- [ ] Biometry config won't invalidate keys?
- [ ] Single approval sufficient (not multi-sig)?
- [ ] What happens if user denies approval?
- [ ] Timeout behavior defined?

---

Last updated: 2026-06-10

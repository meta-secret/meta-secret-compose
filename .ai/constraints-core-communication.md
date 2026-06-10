# Constraints: Core & FFI Communication

See CONSTRAINTS.md for quick reference. This file has full details.

**Use this when planning:** Mobile-to-Core integration, FFI calls, AppManager, JSON transmission.

---

## 1. UniFFI Bridge

**What is it:**
- Language boundary bridge between Kotlin/Swift and Rust
- Allows calling Rust functions from mobile code
- Handles marshalling (converting types between languages)

**Data format:** JSON strings (not binary)

```
Mobile app → JSON string
            ↓
UniFFI bridge → Parse JSON
            ↓
Rust Core → Process
            ↓
Rust Core → Return JSON string
            ↓
UniFFI bridge → Return to mobile
            ↓
Mobile app → Parse JSON
```

---

## 2. AppManager Creation

**First launch:**
```
Core via FFI: "generateMasterKey()"
              ↓
Returns: DMK string (JSON)
```

**Subsequent launches:**
```
Mobile app: Load DMK from Keychain
            ↓
Core via FFI: "initWithSavedKey(dmk)"
              ↓
Core: Creates AppManager internally
            ↓
Returns: Success / Error (JSON)
            ↓
Mobile app: Now ready for other FFI calls
```

---

## 3. AppManager Lifetime

**AppManager lives in Core** (not in mobile app):

```
Mobile launches:
  → initWithSavedKey()
  → Core creates AppManager
  → AppManager stays in Core

Mobile makes FFI calls:
  → Each call uses same AppManager
  → Stateful (remembers Vault state)

Mobile closes:
  → AppManager might persist in process memory
  → Or cleared on process kill

Next launch:
  → New AppManager created
  → Previous one discarded
```

**Mobile app doesn't instantiate AppManager directly** — only Core does.

---

## 4. FFI Method Pattern

**Typical FFI call:**

```
Mobile: metaSecretCore.getAppState()
        ↓
Rust Core: Calls AppManager.getAppState()
        ↓
Rust Core: Serializes result to JSON
        ↓
Returns: JSON string
        ↓
Mobile: Parses JSON
        ↓
Mobile: Constructs AppStateModel object
        ↓
Mobile: Updates UI
```

---

## 5. JSON Data Format

**All transmission is JSON:**
- No binary protocol
- Human-readable (for debugging)
- Encoded UTF-8 strings

**Examples:**
```json
// AppStateModel
{
  "state": {
    "vault": {
      "fullInfo": {
        "member": { ... }
      }
    }
  }
}

// MasterKeyModel
{
  "success": true,
  "message": "base64-encoded-key",
  "error": null
}
```

---

## 6. Stateless Design Principle

**Core principle:**
- Each FFI call is independent
- No global state held between calls
- AppManager is exception (session state)

**Implication:**
```
Call 1: metaSecretCore.getAppState()
        → Reads DB
        → Builds state from scratch
        → Returns

Call 2: metaSecretCore.getAppState()
        → Reads DB again
        → Same result as Call 1
        → Returns

Call 3: User does something, DB changes

Call 4: metaSecretCore.getAppState()
        → Reads DB again
        → Different result
        → Returns
```

**Not cached** (state fetched fresh each time).

---

## 7. Error Handling

**All FFI calls return:**
- Success: result JSON
- Error: error JSON with message

```kotlin
val result = metaSecretCore.someMethod()
val model = SomeModel.fromJson(result)

if (model.success) {
  // Process result
} else {
  // Handle error
  val appError = ErrorMapper.map(model.error)
  val userMessage = ErrorMapper.getUserMessage(appError)
  notificationCoordinator.showError(userMessage)
}
```

**No exceptions thrown across FFI** (JSON-based errors only).

---

## 8. Concurrency

**Question:** Can multiple threads call FFI simultaneously?

**Status:** Not fully specified.

**Likely:** 
- AppManager may not be thread-safe
- Mobile should serialize FFI calls (one at a time)
- Or use Dispatcher.IO to ensure serial execution

**Current:** Investigation needed.

---

## 9. Large Data Transfer

**What if FFI data is large?**

Example: User with 1000 secrets → JSON is huge

**Concerns:**
- String allocation overhead
- JSON parsing overhead
- Memory usage

**Status:** Not analyzed (performance consideration).

---

## 10. Device Master Key Transmission

**DMK transfer via FFI:**

```
Core FFI: "generateMasterKey()"
          ↓
Returns: "AGE-SECRET-KEY-1abc2def3ghi..."
          ↓
Mobile: Receives as String
          ↓
Mobile: Saves to Keychain immediately
          ↓
Mobile: Can forget string after save
```

**Never:**
- ❌ Log DMK
- ❌ Send to server
- ❌ Store in SharedPreferences
- ❌ Keep in memory longer than needed

---

## 11. Share Data Over FFI

**Share transmission:**

```
Mobile: "I need to recover secret X"
        ↓
Core: "You need 2 shares. Get from Device B"
        ↓
Mobile: Requests from Device B via server
        ↓
Device B: Sends encrypted share
        ↓
Mobile: Receives share
        ↓
Mobile: Passes to Core via FFI
        ↓
Core: Combines shares
        ↓
Core: Recovers secret
        ↓
Returns: Secret to mobile
```

**Share is encrypted in transit** (server doesn't see plain share).

---

## 12. Test/Debug FFI Calls

**For testing:**

```kotlin
// Test: generateMasterKey works
val result = metaSecretCore.generateMasterKey()
assertEquals(true, result.success)
assertNotNull(result.message)

// Test: initWithSavedKey works
val initResult = metaSecretCore.initWithSavedKey(dmk)
assertEquals(true, initResult.success)
```

**No mocking of Core** (talks to real Rust library in tests).

---

## Decision Checklist: Core & FFI

Planning Core integration? Check:

- [ ] Using UniFFI for all Core calls?
- [ ] JSON format for all transmission?
- [ ] initWithSavedKey() called once at startup?
- [ ] AppManager created by Core (not mobile)?
- [ ] Each FFI call is independent?
- [ ] DMK passed once, then forgotten by mobile?
- [ ] Never logging DMK?
- [ ] Error handling via JSON (no exceptions)?
- [ ] Shares encrypted in transit?
- [ ] Concurrency strategy defined?

---

Last updated: 2026-06-10

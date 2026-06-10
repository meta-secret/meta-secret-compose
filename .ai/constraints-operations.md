# Constraints: Device Operations (Join, Remove, Resharing)

See CONSTRAINTS.md for quick reference. This file has full details.

**Use this when planning:** Join device, remove device, resharing, compromised device removal.

---

## 1. Join New Device (Process)

**Requirement:** New device cannot join without approval

```
New Device:
  "Hello, I want to join vault-name"
  ↓
Existing Device (any device in vault):
  Gets notification
  ↓
User approves via biometry/PIN
  ↓
Existing Device:
  Generates approval
  ↓
Resharing protocol:
  1. All existing secrets decrypt (with current shares)
  2. Each secret re-split for new device count
  3. New shares generated
  4. Shares distributed to ALL devices
  5. Verify success
  ↓
New Device:
  Receives shares
  ↓
Status: JOINED ✅
```

**Key rules:**
- One approval = sufficient
- Only existing Vault member can approve
- Must complete resharing BEFORE join is final
- Resharing failure = join failed

---

## 2. Resharing During Join

**Trigger:** New device joins

**What happens:**
```
Before join:
  Device 1: k-of-(n) shares for each secret
  Device 2: k-of-(n) shares for each secret

During resharing:
  1. Decrypt all secrets with current k-of-n
  2. New count: n+1 devices
  3. New threshold: k = (n+1) - 1
  4. Each secret: split into n+1 shares
  5. Distribute to n+1 devices

After resharing complete:
  Device 1: k'-of-(n+1) shares (new shares!)
  Device 2: k'-of-(n+1) shares (new shares!)
  Device N+1: k'-of-(n+1) shares (new shares!)
  
Old shares INVALID
```

**Example:**
```
Before: 2 devices, 1-of-2 schema
  Device 1: share_old_1
  Device 2: share_old_2

After new device 3 joins:
  Device 1: share_new_1 (different from share_old_1!)
  Device 2: share_new_2 (different from share_old_2!)
  Device 3: share_new_3

share_old_1 + share_old_2 = CANNOT recover secret
share_new_1 + share_new_2 = recovery works ✅
```

---

## 3. Remove Device (Process)

**Constraint:** Device cannot remove itself

**Process:**

```
Remove request:
  "Please remove Device X"
  ↓
Who can initiate?
  ✅ Another device in vault (Device Y)
  ❌ Device X itself (blocked)
  
Device Y (approver):
  Gets removal request
  ↓
User approves via biometry/PIN on Device Y
  ↓
Resharing BEFORE removal:
  Same as join resharing
  Generate new shares for n-1 devices
  Distribute to remaining devices
  ✓ Verify success
  ↓
If resharing failed:
  ❌ Block removal
  Return to vault state before removal attempt
  ↓
If resharing succeeded:
  ✅ Remove device from vault
  Device X no longer member
```

**Critical rule:** Resharing MUST succeed before device is removed.

---

## 4. Last Device Protection

**Rule:** Cannot remove last device

```
if (vault.device_count == 1) {
  removal_request → DENIED
  reason: "Last device cannot be removed"
}
```

**Why?** 
- Vault must always have at least 1 device
- User needs somewhere to store their shares

---

## 5. Approval Model

**Current model:** One approval sufficient

```
Operation | Who approves | How | Sufficient?
-----------|-------------|-----|------------
Join device | Any existing member | Biometry/PIN | ✅ Yes (1 approval)
Remove device | Another device | Biometry/PIN | ✅ Yes (1 approval)
Recover secret* | Remote device | Biometry/PIN | ✅ Yes (1 approval)

*Only if current device doesn't have enough shares
```

**Future:** May require multiple approvals, but not now.

---

## 6. Resharing Failure Handling

**Scenario:** Resharing started but failed midway

```
Device 1: Started resharing
  ↓
Device 2: Failed to decrypt old shares
  ↓
Recovery:
  Both devices ROLLBACK to old state
  Old shares still valid
  Resharing = ATOMIC (all-or-nothing)
```

**No partial resharing:**
- Either all devices have new shares
- Or all devices keep old shares
- Never: Device 1 has new, Device 2 has old

---

## 7. Compromised Device Removal

**Discovery:** Device X is compromised (malware, stolen)

**Action required:**

```
Step 1: Initiate removal from Device Y
  User confirms removal on Device Y (biometry)
  ↓
Step 2: Resharing (generates NEW shares for all)
  Old share on Device X becomes INVALID
  Device 1, Device 2, ... get NEW shares
  ↓
Step 3: Verify resharing success
  All other devices confirm they have new shares
  ↓
Step 4: Remove Device X from vault
  Device X formally removed
  Its old share is useless (doesn't match new polynomial)
```

**After removal:**
- Device X still has OLD share from before removal
- But it's cryptographically useless
- Even if attacker extracts it: cannot recover secrets
- Secrets now protected by NEW shares on trusted devices

---

## 8. Offline Device During Join

**Scenario:** Device 3 wants to join, but Device 2 is offline

```
Device 1: Online ✓
Device 2: Offline ✗
Device 3: Requests join

Device 1: Cannot complete resharing alone
Reason: Need to distribute shares to Device 2
Result: ❌ Join fails (blocked)

Option: Wait for Device 2 to come online
Or: Remove Device 2, then Device 3 can join
```

**Rule:** All devices must be reachable for resharing

---

## 9. Device Loss vs Removal

**Device lost (crashed, stolen):**
- May still be vault member
- Cannot receive new shares during resharing
- Blocks future operations

**Device removed (via process):**
- No longer vault member
- Future resharing includes n-1 devices
- User must approve removal on another device

---

## 10. Old Shares After Resharing

**Critical rule:** Old shares are invalid after resharing

```
Secret S
Before resharing:
  Shares: s1_old, s2_old (1-of-2)
  Works: s1_old → recover S ✓

After resharing (3 devices now):
  Shares: s1_new, s2_new, s3_new (2-of-3)
  
Check old share:
  s1_old + s2_old → try recover → FAILS ❌
  
Why?
  Different polynomial degree
  Old shares from degree-1 polynomial
  New shares from degree-2 polynomial
  Incompatible
```

---

## Decision Checklist: Join

Planning a join feature? Check:

- [ ] Approval from existing device required?
- [ ] Resharing protocol integrated?
- [ ] Can device join if offline? (No! must be reachable)
- [ ] Old shares invalidated? (Must happen!)
- [ ] New shares distributed to all devices?
- [ ] Rollback plan if resharing fails?
- [ ] Can join if Device 1 is last? (Yes, allowed)
- [ ] What if Device 2 is compromised? (Still allowed to join, but remove Device 2 first)

---

## Decision Checklist: Remove

Planning a remove feature? Check:

- [ ] Only other devices can approve? (Can't self-remove)
- [ ] Last device protected? (Cannot remove if count == 1)
- [ ] Resharing happens BEFORE removal?
- [ ] If resharing fails, removal blocked?
- [ ] Old shares become invalid after resharing?
- [ ] Compromised device removal = resharing + removal?
- [ ] Offline device blocks removal? (Yes, need to reach it for resharing)

---

Last updated: 2026-06-10

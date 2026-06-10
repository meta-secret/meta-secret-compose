# Constraints: Lifecycle & Data Loss Scenarios

See CONSTRAINTS.md for quick reference. This file has full details.

**Use this when planning:** Error handling, device loss, recovery, data loss mitigation.

---

## 1. Normal Secret Lifecycle

```
Create: User creates/imports secret
  ↓
Split: Secret divided by Shamir
  ↓
Distribute: Shares go to devices
  ↓
Recover: Combine shares to use secret
  ↓
Delete: Remove secret (not yet implemented)
```

---

## 2. Device Loss (1 of 2)

**Scenario:** User has 2 devices (1-of-2 schema), loses one (theft/damage)

```
Before:
  Device 1: share_1 → can recover any secret ✓
  Device 2: share_2 → can recover any secret ✓

Device 2 lost:
  Device 1: share_1 → STILL can recover ✓
  Device 2: gone

Secrets: Still accessible ✅
  Reason: share_1 alone is sufficient (1-of-2)
```

---

## 3. Device Loss (1 of 3)

**Scenario:** User has 3 devices (2-of-3 schema), loses one

```
Before:
  Device 1: share_1 (insufficient alone)
  Device 2: share_2 (insufficient alone)
  Device 3: share_3 (insufficient alone)

Device 3 lost:
  Device 1: share_1 + Device 2: share_2 = RECOVER ✓
  Device 3: gone

Secrets: Still accessible ✅
  Reason: 2 shares of 3 is threshold
```

---

## 4. Device Loss (2 of 3)

**Scenario:** Lose 2 devices from 3-device Vault

```
Before:
  Device 1: share_1
  Device 2: share_2
  Device 3: share_3
  Threshold: need 2 shares

Lose Devices 2 & 3:
  Device 1: share_1 alone
  Devices 2, 3: gone

Can recover? NO ❌
  Reason: 1 share < 2 shares needed

Secrets: LOST (unrecoverable) ⚠️
```

---

## 5. Device Compromise

**Not a loss, but a threat:**

```
Device has malware:
  → Attacker can see shares
  → Attacker can see database
  → Attacker can initiate recovery
  → Attacker can approve operations

Action: Remove device immediately via other device
  1. Other device: Approve removal + reshare
  2. New shares generated (old shares useless)
  3. Remove device
  4. Compromised device's old share = useless
```

---

## 6. Stolen Unlocked Device

**More dangerous than locked:**

```
Stolen (unlocked):
  → Can access everything on device
  → Can see shares
  → Can trigger operations
  = COMPROMISE

Stolen (locked):
  → Cannot access without PIN/biometry
  → TBD: Less well analyzed
```

**Action:** Treat as compromise, remove device.

---

## 7. Factory Reset

**Device completely wiped:**

```
User hits "Erase All Data":
  ↓
Keychain wiped (DMK lost)
Database wiped (backup wiped)
App deleted

Result:
  Device Master Key = LOST
  Shares = LOST
  Device state = LOST
  = Same as device loss
```

**This is accepted** (like any app losing data on factory reset).

---

## 8. App Crash During Operation

**What if app crashes mid-resharing?**

```
Resharing started:
  Device 1: Generating new shares
  Device 2: Receives notification
  Device 1: APP CRASHES

State after crash:
  Device 1: Partially reshared (incomplete)
  Device 2: Old shares (expected new ones)
  = INCONSISTENT STATE

Recovery:
  Resharing must be atomic
  Either all devices have new shares
  Or all keep old shares
  Never: partial new + partial old
```

---

## 9. Network Failure During Resharing

**Devices need to communicate:**

```
Device 1: "Let's reshare, Device 2"
Device 2: Offline ❌

Result:
  Resharing blocked
  Devices keep old shares
  Resharing = NOT ATOMIC
  Try again when Device 2 online
```

---

## 10. Backup Loss (iOS KeyChain scenario)

**Device A:** Has backup in keychain
**User:** Deletes app

```
App deleted:
  Keychain (backup) survives (OS level)
  ✅ Can restore via backup on reinstall

Factory reset:
  Keychain wiped (OS level)
  ❌ Backup lost
  = Data loss
```

---

## 11. Backup Corruption

**What if backup file is corrupted?**

```
Mobile app: Try to restore backup
            ↓
Read backup file
Decompress/decrypt
Parse JSON
            ↓
Corrupted: Cannot parse
            ↓
Result: Restore fails
Fallback: Start fresh with new DMK
```

**Recovery:** Not yet fully specified.

---

## 12. One Device Left (from 2)

**User had 2 devices, lost 1:**

```
Before:
  Device 1 (1-of-2 share): Can recover secrets
  Device 2 (1-of-2 share): Can recover secrets

After loss:
  Device 1 (1-of-2 share): STILL can recover ✓
  Device 2: Gone

Vault state: Reduced to single device
  No resharing happens (Device 2 offline)
  Device 1 still member of Vault (formally)
  Shares unchanged
```

**No automatic downgrade to "1 device mode"** — Device 1 still expects Device 2.

---

## 13. One Device Left (from 3)

**User had 3 devices (2-of-3), lost 2:**

```
Before:
  Device 1: share_1 (need 2 total)
  Device 2: share_2 (need 2 total)
  Device 3: share_3 (need 2 total)

After loss:
  Device 1: share_1 alone
  Devices 2, 3: Gone

Can recover secrets? NO ❌
  Need 2 shares, have 1
  = DATA LOST
```

---

## 14. Offline Device (Temporary)

**Device temporarily unreachable:**

```
Device 1: Online, needs to recover secret
         → Needs 2-of-3 shares
         → Has share_1 (1 of 3)
         → Asks Device 2: online ✓
         → Asks Device 3: offline ❌

Recovery:
  Can use: share_1 + share_2
  = Recover secret ✓

Vault operation:
  Device offline but other devices available
  = Operation succeeds
```

---

## 15. Prevention Strategies

**To minimize data loss:**

1. **3+ devices recommended** (lose 1 safely)
2. **Regular backup verification**
3. **Contact devices regularly** (detect loss early)
4. **Monitor vault health** (alerts if risky state)
5. **Recovery Key?** (Not yet, future work)

---

## 16. Error Scenarios

**What can go wrong:**

| Scenario | Recovery? | Mitigation |
|---|---|---|
| Lose 1 device (2 total) | ✅ Yes (1-of-2) | Common case, acceptable |
| Lose 1 device (3 total) | ✅ Yes (2-of-3) | Recommended config |
| Lose 2 devices (3 total) | ❌ No | Data lost, preventable |
| App crash mid-reshare | ⚠️ Atomic rollback | Must design carefully |
| Backup corrupted | ❌ No | App restart = fresh |
| Factory reset | ❌ No | Accepted (same as normal app) |
| Compromised device | ✅ Yes (via removal) | Requires user action |

---

## Decision Checklist: Lifecycle & Recovery

Planning error handling? Check:

- [ ] Vault can survive 1 device loss (2-of-3)?
- [ ] 2 device loss = data loss accepted?
- [ ] Resharing is atomic (all-or-nothing)?
- [ ] Network failure handling defined?
- [ ] Backup corruption handling defined?
- [ ] App crash mid-resharing = rollback?
- [ ] Factory reset = data loss accepted?
- [ ] Compromised device removal workflow works?
- [ ] User gets alerts for risky states?
- [ ] Recovery without Recovery Key = impossible?

---

Last updated: 2026-06-10

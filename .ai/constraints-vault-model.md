# Constraints: Vault Model & Shamir Secret Sharing

See CONSTRAINTS.md for quick reference. This file has full details.

---

## 1. Vault Definition

**Vault** = trusted group of user's devices

- One user creates one Vault
- Then connects their own devices to it
- Examples: iPhone + Android + MacBook
- All devices belong to SAME user
- Vault has a globally unique name (user-provided)

**Supported configurations:**
- 1 device (initial, allowed)
- 2 devices (basic replication)
- 3 devices (maximum supported configuration)
- 4 or more devices are rejected by Core until a new redistribution protocol is designed

The policy is intentionally explicit; there is no generic `k=n−1` rule. The
supported threshold is `k=1` for one device, `k=1` with full replication for two
devices, and `k=2` for the three-device Vault.

---

## 2. Single Device (1-Device Mode)

When Vault has only 1 device:

- **Shamir sharing NOT used**
- Secret stored **whole** on device
- Opened after biometry/PIN
- **No decentralization at this stage**
- Allowed starting mode

```
User creates Vault
  ↓
Device 1 added
  ↓
Device 1 = only participant
  ↓
Secret creation: stored WHOLE on Device 1
  ↓
Recovery: biometry → Device 1 → full secret
```

---

## 3. Two Devices (1-of-2 Schema)

When second device joins:

```
Device 1 (has Secret whole)
  ↓
Device 2 requests join
  ↓
Device 1 approves + reshares
  ↓
Secret is fully replicated
  ↓
Full copy → Device 1
Full copy → Device 2
```

**Schema: 1-of-2**
- Threshold: k=1
- Either device alone can recover the Secret
- This is full replication, not SSS

**Trade-off:** Accessibility > Threshold security
- Can lose 1 device, still have access
- Compromised 1 device = risk (share exposed)

---

## 4. Three Devices (2-of-3 Schema)

When the third device joins:

```
Before: 1-of-2 (Device 1, Device 2)
  ↓
Device 3 requests join
  ↓
Device 2 approves + reshares
  ↓
Secrets re-split with 3 shares
  ↓
New schema: 2-of-3 (k=2; any 2 of 3)
  ↓
Share 1 → Device 1
Share 2 → Device 2
Share 3 → Device 3
```

**Schema: 2-of-3**
- Need: 2 shares to recover
- 1 share alone = useless
- Lose 1 device = still have access (2 left)
- Lose 2 devices = **DATA LOST** (only 1 share remains)

**Device limit:**
- A fourth device cannot join the Vault.
- Core returns a device-limit error; clients show the error and keep the existing
  three-device state unchanged.

---

## 5. Shamir Secret Sharing (SSS)

**Algo:** Shamir Secret Sharing

**Key facts:**
- Each secret split **separately**
- NOT one Vault key split among devices
- Polynomial-based: k points determine degree k-1 polynomial
- k-1 shares = cannot recover (infinite possibilities)
- k shares = recover exactly

**Example (2-of-3):**
```
Secret = 42
Polynomial: y = 42 + a*x + b*x²  (degree 2)
Points generated:
  Share 1: f(1) = value1
  Share 2: f(2) = value2
  Share 3: f(3) = value3

Recovery:
  value1 + value2 → reconstruct polynomial → recover 42
  value1 alone → impossible
```

---

## 6. Resharing Protocol

When devices added/removed:

```
Each user secret:
  1. Decrypt current shares (with old k-of-n)
  2. Recover secret
  3. Split with NEW shares count
  4. Distribute one new share to each device
  5. Verify receipt and remove temporary outbound copies from the sender
```

**Important:**
- Old shares become invalid (old polynomial)
- New shares use new polynomial
- Even if compromised device keeps old share, it's useless for new secrets
- Resharing is ATOMIC: success or full rollback

---

## 7. Decentralization

MetaSecret is **serverless for data storage**:

- ❌ Server does NOT store secrets
- ❌ Server does NOT store plaintext shares
- ❌ Server does NOT store device databases
- ✅ Server = only message delivery
- ✅ Server = device signaling

**Why?**
- If server hacked: no user secrets leaked
- User data stays on devices
- Only communication goes through server

**Consequence:**
- Encrypted Key Shares may be temporarily queued on the server for delivery
- Resharing is coordinated by Core; clients only render the resulting state
- The server removes the temporary encrypted workflow after delivery

---

## 8. No Vault Data Key

**MetaSecret does NOT use:**
- One Vault key that encrypts all secrets
- Then split that key among devices

**Instead:**
- Each secret split separately
- No intermediate "Vault key"
- Direct secret → shares distribution

---

## 9. Device Master Key ≠ Share

**Important distinction:**

| What | What it is | Stored | Purpose |
|---|---|---|---|
| Device Master Key | Per-device secret | Device (KeyChain) | Decrypt device DB, init AppManager |
| Share | Part of secret | Device DB (encrypted) | Combined to recover user secret |

They are DIFFERENT:
- DMK: one per device, permanent
- Share: many per device (one per user secret), changes with resharing

---

## Decision Tree

**When planning a feature:**

```
Does feature touch:
  ├─ Single device mode?
  │  └─ See: constraints-device-storage.md
  ├─ Device join/remove?
  │  └─ See: constraints-operations.md
  ├─ Resharing?
  │  └─ See: constraints-operations.md + this file (section 6)
  ├─ Recovering secret?
  │  └─ See: constraints-lifecycle.md
  ├─ Device loss scenario?
  │  └─ See: constraints-lifecycle.md
  └─ Data decentralization?
     └─ This file (section 7)
```

---

Last updated: 2026-09-20

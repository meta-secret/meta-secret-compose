# Security

## Responsibility

Verification of security guarantees related to local authentication, access control, data exposure prevention, and global approval enforcement across the application.

---

## TC-SECUR-001 — Biometric retry limits

**Preconditions:**
- Biometric authentication is enabled on the device
- User is prompted for biometric authentication

**Steps:**
1. Fail biometric authentication repeatedly

**Expected Result:**
- Number of biometric retry attempts is limited by the system
- After reaching the limit, biometric authentication is blocked
- User is required to use the PIN fallback

---

## TC-SECUR-002 — PIN retry limits

**Preconditions:**
- User is prompted to enter a PIN code

**Steps:**
1. Enter an incorrect PIN code repeatedly

**Expected Result:**
- Number of PIN retry attempts is limited
- Further attempts are blocked after the limit is reached
- No sensitive data is exposed during failed attempts

---

## TC-SECUR-003 — Sensitive data never logged

**Preconditions:**
- Application is running
- Secrets exist in the application

**Steps:**
1. Perform common actions:
    - Add secret
    - Show secret
    - Approve or decline requests
2. Inspect application logs

**Expected Result:**
- No sensitive data (secret values, split data, keys) appears in logs
- Logs contain only non-sensitive technical information

---

## TC-SECUR-004 — Secrets inaccessible without approval

**Preconditions:**
- Secret requires approval from other devices
- Approval has not been granted

**Steps:**
1. Attempt to view a secret

**Expected Result:**
- Secret value is not revealed
- Approval flow is enforced
- No partial or cached secret data is displayed

---

## TC-SECUR-005 — Approval popup blocks access globally

**Preconditions:**
- Approval request is received on a device

**Steps:**
1. Navigate between different screens (Secrets, Devices, Profile)

**Expected Result:**
- Approval popup is displayed regardless of the current screen
- Access to underlying screens is blocked until approval is resolved
- No sensitive actions can be performed while approval is pending
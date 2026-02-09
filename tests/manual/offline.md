# Offline Behavior

## Responsibility

Verification of application behavior when network connectivity is unavailable, including user actions, error handling, state preservation, and recovery after connectivity is restored.

---

## TC-OFF-001 — App behavior without internet

**Preconditions:**
- User is authenticated
- Device has no internet connectivity

**Steps:**
1. Disable internet connection
2. Launch the application

**Expected Result:**
- Application launches successfully
- No crash or unexpected behavior occurs
- Network-dependent actions are unavailable or deferred
- User is informed about limited functionality, if applicable

---

## TC-OFF-002 — Add secret while offline

**Preconditions:**
- User is authenticated
- No internet connectivity
- Secrets screen is displayed

**Steps:**
1. Tap “+” Add Secret
2. Enter valid secret description and value
3. Confirm biometric or PIN authentication
4. Attempt to add the secret

**Expected Result:**
- Secret is not added to the network
- Add Secret modal is closed
- Error toast is displayed indicating network unavailability
- No partial or inconsistent secret state is created locally

---

## TC-OFF-003 — Show secret while offline

**Preconditions:**
- User is authenticated
- No internet connectivity
- At least one secret exists

**Steps:**
1. Tap an existing secret
2. Attempt to show the secret

**Expected Result:**
- Biometric or PIN authentication may be requested
- Secret value is not revealed
- Error toast is displayed indicating network unavailability
- Show Secret modal is closed or remains in a safe state

---

## TC-OFF-004 — Device join while offline

**Preconditions:**
- User attempts to join or approve a device
- No internet connectivity

**Steps:**
1. Initiate device join or approval flow

**Expected Result:**
- Join or approval request is not completed
- User is informed about network unavailability
- No invalid device state is created
- Pending actions are not duplicated

---

## TC-OFF-005 — Sync resumes after connectivity restored

**Preconditions:**
- Application was used while offline
- Network-dependent actions were attempted or deferred

**Steps:**
1. Restore internet connectivity
2. Bring the application to foreground or relaunch it
3. Navigate to the Secrets screen

**Expected Result:**
- Synchronization process resumes automatically
- Device and secrets state are updated
- No duplicate or inconsistent data appears
- Application returns to normal online behavior
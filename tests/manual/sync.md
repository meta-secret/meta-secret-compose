# Synchronization

## Responsibility

Distributed consistency of secrets and device state across all devices in the network, including initial synchronization, recovery, and conflict-free data propagation.

---

## TC-SYNC-001 — Sync on app launch

**Preconditions:**
- User is authenticated
- Device is part of an existing device network
- Application was previously closed
- Network connectivity is available

**Steps:**
1. Launch the application
2. Navigate to the Secrets screen

**Expected Result:**
- Synchronization process is triggered on application launch
- Local data is updated with the latest network state
- Secrets list reflects the most recent data

---

## TC-SYNC-002 — Sync after device join

**Preconditions:**
- A new device has been accepted into the device network
- Existing secrets are present in the network

**Steps:**
1. Accept a new device on an existing device
2. Observe the newly joined device

**Expected Result:**
- Secrets are synchronized to the newly joined device
- Secret count matches across all devices
- Device network state is consistent

---

## TC-SYNC-003 — Sync after reinstall and restore

**Preconditions:**
- Device was previously part of the device network
- Application was uninstalled
- Backup and restore mechanisms are available

**Steps:**
1. Reinstall the application on the device
2. Complete onboarding and authentication
3. Restore application state
4. Open the Secrets screen

**Expected Result:**
- Device rejoins the existing network
- Secrets are restored from the network
- No data loss occurs

---

## TC-SYNC-004 — No duplicate secrets after sync

**Preconditions:**
- Multiple devices are part of the network
- Secrets exist on the network

**Steps:**
1. Trigger synchronization on all devices (app launch or screen navigation)

**Expected Result:**
- Each secret appears exactly once on each device
- No duplicated secrets are displayed

---

## TC-SYNC-005 — Ordering consistency across devices

**Preconditions:**
- Multiple devices are part of the network
- Multiple secrets exist

**Steps:**
1. Open the Secrets screen on all devices

**Expected Result:**
- Secrets are displayed in the same order across all devices
- Ordering is stable and consistent
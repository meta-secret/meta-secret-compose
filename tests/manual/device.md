# Devices

## Responsibility

Device network management, including device discovery, join requests, approval flows, and synchronized device state across the network.

---

## Devices List

### TC-DEV-001 — Devices list not empty

**Preconditions:**
- User is authenticated
- At least one device is part of the device network

**Steps:**
1. Open the Devices screen

**Expected Result:**
- Devices list is displayed
- At least one device item is present

---

### TC-DEV-002 — Current device displayed correctly

**Preconditions:**
- Devices screen is displayed

**Steps:**
1. Observe the device list

**Expected Result:**
- Current device is displayed in the list
- Device status is marked as `current device`

---

### TC-DEV-003 — Device item shows type, status, secret count

**Preconditions:**
- Devices screen is displayed

**Steps:**
1. Observe any device item in the list

**Expected Result:**
- Device type is displayed (e.g. phone, tablet, desktop)
- Device status is displayed (current, member, pending, declined)
- Number of secrets associated with the device is displayed

---

## Add Device Flow

### TC-DEV-010 — Add device button opens add device screen

**Preconditions:**
- Devices screen is displayed

**Steps:**
1. Tap the “+” Add Device button

**Expected Result:**
- Add Device screen or modal is opened

---

### TC-DEV-011 — Incoming join request notification

**Preconditions:**
- Another device initiates a join request
- Current device is part of the network

**Steps:**
1. Receive a join request while the app is running or in background

**Expected Result:**
- Push notification is received
- Notification indicates a new device join request

---

### TC-DEV-012 — Red badge on Devices tab

**Preconditions:**
- Join request notification has been received
- User is not currently on the Devices tab

**Steps:**
1. Observe the tab bar

**Expected Result:**
- Red badge is displayed on the Devices tab indicating new activity

---

### TC-DEV-013 — Join request banner displayed

**Preconditions:**
- One or more join requests are pending
- Devices screen is not currently open

**Steps:**
1. Navigate to any main tab

**Expected Result:**
- Banner is displayed indicating the number of pending join requests
- Banner provides a shortcut to the Devices screen

---

### TC-DEV-014 — Banner and badge cleared after viewing

**Preconditions:**
- Join request banner and tab badge are visible

**Steps:**
1. Open the Devices screen

**Expected Result:**
- Join request banner is dismissed
- Red badge on Devices tab is removed

---

### TC-SEC-015 — Banner “+Add” opens Devices screen

**Preconditions:**
- Security banner is visible

**Steps:**
1. Tap “+Add” on the banner

**Expected Result:**
- Devices screen is opened
- Add Device flow can be initiated

---

## Device Approval

### TC-DEV-020 — Pending device appears in list

**Preconditions:**
- One or more join requests are pending

**Steps:**
1. Open the Devices screen

**Expected Result:**
- Pending devices are displayed in the list
- Each pending device shows status `pending`
- Secret count is shown as 0

---

### TC-DEV-021 — Open device approval modal

**Preconditions:**
- At least one pending device is displayed

**Steps:**
1. Tap on a pending device item

**Expected Result:**
- Device approval modal is opened
- Device information is displayed
- “Accept” and “Decline” actions are available

---

### TC-DEV-022 — Decline device with biometric

**Preconditions:**
- Device approval modal is open
- Biometric authentication is available

**Steps:**
1. Tap “Decline”
2. Complete biometric authentication successfully

**Expected Result:**
- Loader is displayed
- Decline request is sent to the server
- Approval modal is closed
- Devices list is refreshed
- Device remains in the list with updated status (declined or pending, according to server response)

---

### TC-DEV-023 — Accept device with biometric

**Preconditions:**
- Device approval modal is open
- Biometric authentication is available

**Steps:**
1. Tap “Accept”
2. Complete biometric authentication successfully

**Expected Result:**
- Loader is displayed
- Accept request is sent to the server
- Approval modal is closed
- Devices list is refreshed
- Device status is updated to `member`

---

### TC-DEV-024 — Device status updated

**Preconditions:**
- Device approval action has completed

**Steps:**
1. Observe the device list

**Expected Result:**
- Device status reflects the latest server response
- UI state matches backend state

---

### TC-DEV-025 — Secret count updated after accept

**Preconditions:**
- Device has been accepted into the network
- Secrets already exist in the network

**Steps:**
1. Observe the accepted device item

**Expected Result:**
- Secret count reflects the number of synchronized secrets
- Secret count is greater than 0 if secrets exist

---

### TC-DEV-026 — Approval state persistence after app restart

**Preconditions:**
- Device approval flow is in progress

**Steps:**
1. Force close the application
2. Relaunch the application
3. Open the Devices screen

**Expected Result:**
- Approval state is restored
- Pending devices are still displayed correctly
- No duplicate approval requests are created

---

## Multiple Device Requests

### TC-DEV-030 — Multiple device join requests processed sequentially

**Preconditions:**
- Multiple join requests are pending

**Steps:**
1. Open the Devices screen
2. Approve or decline the first pending device
3. Return to the Devices screen

**Expected Result:**
- Join requests are processed one at a time
- Next pending device is available only after the previous request is resolved
- Device list remains consistent throughout the process
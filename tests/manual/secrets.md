# Secrets

## Responsibility

Secrets lifecycle management, secure storage, synchronization, and approval-based access flows across single and multiple devices.

---

## Backup & Initial State

### TC-SEC-001 — Backup file check on first Secrets screen open

**Preconditions:**
- User is authenticated
- Secrets screen is opened for the first time
- No backup path has been configured

**Steps:**
1. Navigate to the Secrets screen

**Expected Result:**
- Application checks for the existence of a backup file
- Backup state is evaluated before showing secrets content

---

### TC-SEC-002 — No backup file → prompt to select location

**Preconditions:**
- No backup file exists

**Steps:**
1. Open the Secrets screen

**Expected Result:**
- Alert popup is shown requesting the user to select a backup location

---

### TC-SEC-003 — Cancel backup selection

**Preconditions:**
- Backup location selection popup is visible

**Steps:**
1. Tap the “Cancel” button

**Expected Result:**
- Backup location dialog is closed
- Warning popup is displayed

---

### TC-SEC-005 — Select backup location → encrypted backup created

**Preconditions:**
- Backup location selection popup is visible

**Steps:**
1. Select a valid backup location

**Expected Result:**
- Encrypted backup file is created
- Operation is logged internally
- User is returned to the Secrets screen

---

### TC-SEC-006 — Backup validation on app restart

**Preconditions:**
- Backup location has been selected
- Backup file exists

**Steps:**
1. Restart the application
2. Navigate to the Secrets screen

**Expected Result:**
- Backup file existence is verified
- No backup-related prompts are shown

---

### TC-SEC-007 — Empty secrets state displayed

**Preconditions:**
- No secrets have been added

**Steps:**
1. Open the Secrets screen

**Expected Result:**
- Empty state is displayed
- No secrets are listed

---

## Secrets Screen UI

### TC-SEC-010 — Add Secret button visible

**Preconditions:**
- Secrets screen is displayed

**Steps:**
1. Observe the UI

**Expected Result:**
- “+” Add Secret button is visible and enabled

---

### TC-SEC-011 — Security banner shown when devices < 3

**Preconditions:**
- Device count is less than 3

**Steps:**
1. Open the Secrets screen

**Expected Result:**
- Security banner is displayed indicating insufficient devices

---

### TC-SEC-012 — Banner visible on all main tabs

**Preconditions:**
- Device count is less than 3

**Steps:**
1. Navigate between Secrets, Devices, and Profile tabs

**Expected Result:**
- Security banner is visible on all tabs

---

### TC-SEC-013 — Banner dismissible

**Preconditions:**
- Security banner is visible

**Steps:**
1. Tap the close (X) icon on the banner

**Expected Result:**
- Banner is dismissed

---

### TC-SEC-014 — Banner reappears after app restart

**Preconditions:**
- Banner was previously dismissed
- Device count is still less than 3

**Steps:**
1. Restart the application

**Expected Result:**
- Security banner is displayed again

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

## Add Secret

### TC-SEC-020 — Open add secret modal

**Preconditions:**
- Secrets screen is displayed

**Steps:**
1. Tap the “+” Add Secret button

**Expected Result:**
- Add Secret modal is displayed

---

### TC-SEC-021 — Description field validation

**Preconditions:**
- Add Secret modal is open

**Steps:**
1. Leave description field empty
2. Fill secret value
3. Tap “Add Secret”

**Expected Result:**
- Validation error is shown
- Secret is not added

---

### TC-SEC-022 — Secret value field validation

**Preconditions:**
- Add Secret modal is open

**Steps:**
1. Fill description
2. Leave secret value empty
3. Tap “Add Secret”

**Expected Result:**
- Validation error is shown
- Secret is not added

---

### TC-SEC-023 — Biometric request before adding secret

**Preconditions:**
- Add Secret modal is open
- Fields are valid

**Steps:**
1. Tap “Add Secret”

**Expected Result:**
- Biometric authentication is requested

---

### TC-SEC-024 — Biometric success → split request sent

**Preconditions:**
- Biometric authentication is available

**Steps:**
1. Successfully authenticate via biometrics

**Expected Result:**
- Loader is shown
- Split request is sent to the server

---

### TC-SEC-025 — Biometric failure → PIN fallback

**Preconditions:**
- Biometric authentication fails

**Steps:**
1. Fail biometric authentication

**Expected Result:**
- PIN code entry is requested

---

### TC-SEC-026 — Any other error → close modal + error toast

**Preconditions:**
- Unexpected error occurs

**Steps:**
1. Attempt to add a secret

**Expected Result:**
- Add Secret modal is closed
- Error toast is displayed for 3 seconds

---

## Add Secret (Single & Multi-Device)

### TC-SEC-030 — Add secret on single device

**Preconditions:**
- Only one device in the network

**Steps:**
1. Successfully add a secret

**Expected Result:**
- Secret is added locally

---

### TC-SEC-031 — Success toast shown

**Preconditions:**
- Secret is added successfully

**Steps:**
1. Observe UI

**Expected Result:**
- “Secret successfully added” toast is shown for 3 seconds

---

### TC-SEC-032 — Secret appears in list

**Preconditions:**
- Secret was added

**Steps:**
1. Observe Secrets list

**Expected Result:**
- New secret appears in the list

---

### TC-SEC-033 — Secret sync to existing devices

**Preconditions:**
- Two or more devices in the network

**Steps:**
1. Add a secret on one device

**Expected Result:**
- Secret appears on other devices

---

### TC-SEC-034 — Secret sync on app launch

**Preconditions:**
- Another device was offline

**Steps:**
1. Launch application on offline device

**Expected Result:**
- Secrets are synchronized on launch

---

### TC-SEC-035 — Secret sync when navigating to Secrets screen

**Preconditions:**
- App is open on another tab

**Steps:**
1. Navigate to Secrets screen

**Expected Result:**
- Secrets list is updated

---

## Secret List Representation

### TC-SEC-040 — Secret displays description

**Expected Result:**
- Secret description matches input value

---

### TC-SEC-041 — Secret displays device count

**Expected Result:**
- Correct number of devices is shown

---

### TC-SEC-042 — Protection status: weak / strong / maximum

**Expected Result:**
- Status reflects device count correctly

---

## Show Secret (Single Device)

### TC-SEC-050 — Open show secret modal

**Steps:**
1. Tap on a secret item

**Expected Result:**
- Show Secret modal is opened

---

### TC-SEC-051 — Secret masked by default

**Expected Result:**
- Secret value is masked with asterisks

---

### TC-SEC-052 — Show secret after biometric success

**Steps:**
1. Tap “Show”
2. Pass biometric authentication

**Expected Result:**
- Loader is shown
- Secret value is revealed

---

### TC-SEC-053 — PIN fallback after biometric failure

**Expected Result:**
- PIN is requested
- On success, secret is shown

---

### TC-SEC-054 — PIN failure → close modal + error toast

**Expected Result:**
- Modal is closed
- Error toast is shown for 3 seconds

---

## Show Secret (Multi-Device Approval)

### TC-SEC-060 — Approval request sent to other devices

**Expected Result:**
- Approval request is sent to other devices

---

### TC-SEC-061 — Approval popup appears on any screen

**Expected Result:**
- Approval popup is shown regardless of current screen

---

### TC-SEC-062 — Decline approval

**Expected Result:**
- Request is declined
- UI is cleaned up on all devices

---

### TC-SEC-063 — Approve approval with biometric

**Expected Result:**
- Biometric requested on approving device
- Secret is revealed on requesting device

---

### TC-SEC-064 — Approval biometric failure

**Expected Result:**
- Approval is canceled
- Error flow is triggered

---

### TC-SEC-065 — UI cleanup after approval/decline

**Expected Result:**
- All loaders, popups, and modals are dismissed

---

### TC-SEC-066 — Approval state persistence after app restart

**Steps:**
1. Restart app during approval flow

**Expected Result:**
- Approval state is restored correctly

---

## Multiple Approval Requests (Stacked)

### TC-SEC-070 — Multiple secret approval requests handled sequentially

**Expected Result:**
- Only one approval request is shown at a time

---

### TC-SEC-071 — Next approval shown only after previous resolved

**Expected Result:**
- Next request is shown only after previous is approved or declined
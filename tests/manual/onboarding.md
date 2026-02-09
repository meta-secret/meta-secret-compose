# Onboarding
## Notes on Multi-Device Scenarios
The onboarding flow is identical for the first, second, and subsequent devices.

Onboarding does not:
- create or join a device network
- generate or restore sensitive data
- determine the device role within the network

Device-specific behavior (first device, additional device, restored device) is handled during authentication and synchronization flows and is covered in dedicated test cases.

## TC-ONB-001 — First launch onboarding flow (single device)

**Preconditions:**
- Application is installed for the first time
- No existing application data
- Device has not previously joined any device network

**Steps:**
1. Launch the application
2. Observe the first onboarding screen
3. Swipe through all onboarding pages sequentially
4. On the final onboarding screen, tap the "Continue" button

**Expected Result:**
- All onboarding pages are displayed in the correct order
- No errors or interruptions occur during onboarding
- After completing onboarding, the user is navigated to the authentication screen
- No sensitive data is created or stored during onboarding

---

## TC-ONB-002 — Skip onboarding on first launch

**Preconditions:**
- Application is installed for the first time
- No existing application data

**Steps:**
1. Launch the application
2. On the first onboarding screen, tap the "Skip" button

**Expected Result:**
- Onboarding is skipped immediately
- User is navigated to the authentication screen
- Onboarding is marked as completed
- No sensitive data is created or stored

---

## TC-ONB-003 — Onboarding is displayed only once

**Preconditions:**
- Onboarding has been completed or skipped previously
- Application data exists

**Steps:**
1. Launch the application
2. Close the application
3. Launch the application again

**Expected Result:**
- Onboarding screens are not displayed
- User is navigated directly to the authentication screen

---

## TC-ONB-004 — Application restart during onboarding

**Preconditions:**
- Application is installed for the first time
- Onboarding has not been completed

**Steps:**
1. Launch the application
2. Navigate to any onboarding page
3. Force close the application
4. Relaunch the application

**Expected Result:**
- Onboarding flow starts again from the first page
- No onboarding progress is persisted
- No sensitive data is created or stored

---

## TC-ONB-005 — Navigation through onboarding pages

**Preconditions:**
- Application is installed for the first time
- Onboarding has not been completed

**Steps:**
1. Launch the application
2. Navigate forward and backward between onboarding pages

**Expected Result:**
- Navigation between onboarding pages works correctly
- Page content is displayed correctly on each screen
- No unexpected navigation occurs

---


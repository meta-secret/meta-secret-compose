# Splash Screen & Local Authentication

## Notes on Multi-Device Scenarios

The Splash Screen flow is identical for the first, second, and subsequent devices.

The Splash Screen is responsible only for local access protection and routing.
It does not create, restore, or modify any network or application state.

---

## TC-SPL-001 — Splash screen checks biometric availability

**Preconditions:**
- Application is launched
- Application data may or may not exist

**Steps:**
1. Launch the application

**Expected Result:**
- Splash screen is displayed
- Biometric authentication availability is checked on the device
- No user interaction is required at this stage

---

## TC-SPL-002 — Biometric authentication not available, fallback to PIN

**Preconditions:**
- Biometric authentication is not available on the device
- Application data exists

**Steps:**
1. Launch the application

**Expected Result:**
- Splash screen is displayed
- User is prompted to enter a PIN code
- No biometric prompt is shown

---

## TC-SPL-003 — Biometric authentication available and succeeds

**Preconditions:**
- Biometric authentication is available and enabled
- Application data exists

**Steps:**
1. Launch the application
2. Confirm biometric authentication successfully

**Expected Result:**
- Biometric authentication prompt is shown
- Authentication succeeds
- User is automatically navigated to:
    - onboarding screen, if onboarding has not been completed
    - authentication screen, if onboarding is completed but authentication is not
    - main screen, if both onboarding and authentication are completed

---

## TC-SPL-004 — Biometric authentication fails, fallback to PIN

**Preconditions:**
- Biometric authentication is available
- Application data exists

**Steps:**
1. Launch the application
2. Fail biometric authentication (e.g. wrong fingerprint / face)

**Expected Result:**
- Biometric authentication prompt is shown
- Authentication fails
- User is prompted to enter a PIN code

---

## TC-SPL-005 — Biometric authentication permission not granted

**Preconditions:**
- Biometric authentication is supported by the device
- Biometric permission is not granted at the system level
- Application data exists

**Steps:**
1. Launch the application

**Expected Result:**
- Splash screen is displayed
- User is redirected to system settings to enable biometric authentication
- Application does not proceed until biometric permission is resolved

---

## TC-SPL-006 — Successful PIN authentication after biometric fallback

**Preconditions:**
- User is prompted to enter a PIN code
- Correct PIN is known

**Steps:**
1. Enter the correct PIN code

**Expected Result:**
- PIN authentication succeeds
- User is navigated according to application state:
    - onboarding screen
    - authentication screen
    - main screen

---

## TC-SPL-007 — Incorrect PIN authentication

**Preconditions:**
- User is prompted to enter a PIN code

**Steps:**
1. Enter an incorrect PIN code

**Expected Result:**
- PIN authentication fails
- User remains on the PIN entry screen
- No navigation occurs
- No application data is exposed

---

## TC-SPL-008 — Splash screen authentication state persistence

**Preconditions:**
- Application is in biometric or PIN authentication state

**Steps:**
1. Force close the application
2. Relaunch the application

**Expected Result:**
- Splash screen authentication flow is restored
- User is prompted again for biometric authentication or PIN
- No unauthorized access to application data occurs
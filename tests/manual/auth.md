# Authentication

## TC-AUTH-001 — First authentication attempt on initial device (happy path)

**Preconditions:**
- Application is installed for the first time
- Onboarding has been completed
- Device is not part of any existing device network

**Steps:**
1. Open the authentication screen
2. Enter a valid username (e.g. `user_123`)
3. Tap the "Next" button
4. Wait for the authentication process to complete

**Expected Result:**
- Username format is accepted
- A loading indicator is displayed
- Authentication request is sent to the server
- User is successfully authenticated
- User is navigated to the main screen

---

## TC-AUTH-002 — Username validation fails on client side

**Preconditions:**
- Authentication screen is displayed

**Steps:**
1. Enter an invalid username (e.g. `u`, `user@name`, `this_username_is_too_long_123`)
2. Tap the "Next" button

**Expected Result:**
- Username input field is highlighted in red
- Validation hint is displayed below the input field
- No network request is sent
- User remains on the authentication screen

---

## TC-AUTH-003 — Username is already taken, join request pending

**Preconditions:**
- Username already exists on the server
- Another device with the same account is active

**Steps:**
1. Enter a valid but already taken username
2. Tap the "Next" button

**Expected Result:**
- Loading indicator is displayed
- Authentication request is sent to the server
- A persistent toast message is shown at the top:
  "Please approve the request on another device"
- A "Cancel" button is displayed over the loading state
- Authentication screen remains blocked until a response is received or canceled

---

## TC-AUTH-004 — Cancel join request while waiting for approval

**Preconditions:**
- Join request is pending
- Approval toast and loading indicator are visible

**Steps:**
1. Tap the "Cancel" button

**Expected Result:**
- Loading indicator is dismissed
- Approval toast is removed
- Authentication screen returns to its initial state
- Username input becomes editable again
- User can attempt authentication with a different username

---

## TC-AUTH-005 — Join request declined by another device

**Preconditions:**
- Join request is pending
- Approval toast is visible

**Steps:**
1. Receive a decline response from the server

**Expected Result:**
- Current approval toast is dismissed
- A new toast message is displayed:
  "Registration denied. You may be trying to join a different account."
- Loading indicator is dismissed
- Authentication screen returns to its initial state
- User can attempt authentication again

---

## TC-AUTH-006 — Join request approved by another device

**Preconditions:**
- Join request is pending
- Approval toast and loading indicator are visible

**Steps:**
1. Receive an approval response from the server

**Expected Result:**
- Loading indicator is dismissed
- Approval toast is removed
- Device successfully joins the existing device network
- User is navigated to the main screen

---

## TC-AUTH-007 — Application restart while join request is pending

**Preconditions:**
- Join request is pending
- Approval toast and loading indicator are visible

**Steps:**
1. Force close the application
2. Relaunch the application

**Expected Result:**
- Authentication screen is restored to the same pending state
- Loading indicator is visible
- Approval toast is displayed again
- "Cancel" button is available
- No duplicate join requests are created

---

## TC-AUTH-008 — Server error during authentication

**Preconditions:**
- Authentication screen is displayed

**Steps:**
1. Enter a valid username
2. Tap the "Next" button
3. Server returns an unexpected error

**Expected Result:**
- Loading indicator is dismissed
- An error toast is displayed at the top:
  "Authentication error. Please try again later."
- Authentication screen returns to its initial state
- User can retry authentication

---

## TC-AUTH-009 — Authentication state persistence after success

**Preconditions:**
- User has been successfully authenticated

**Steps:**
1. Close the application
2. Relaunch the application

**Expected Result:**
- Authentication screen is not shown
- User is navigated directly to the main screen
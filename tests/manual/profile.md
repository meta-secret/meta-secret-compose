# Profile

## Responsibility

Account overview and informational elements related to the user profile and overall security status.

---

## TC-PRO-001 — Profile displays username

**Preconditions:**
- User is authenticated
- Profile screen is accessible

**Steps:**
1. Open the Profile screen

**Expected Result:**
- Username is displayed
- Displayed username matches the authenticated account

---

## TC-PRO-002 — Displays device count

**Preconditions:**
- User is authenticated
- Profile screen is displayed

**Steps:**
1. Observe the device count section

**Expected Result:**
- Number of devices in the network is displayed
- Displayed count matches the actual number of connected devices

---

## TC-PRO-003 — Displays secrets count

**Preconditions:**
- User is authenticated
- Profile screen is displayed

**Steps:**
1. Observe the secrets count section

**Expected Result:**
- Number of stored secrets is displayed
- Displayed count matches the actual number of secrets

---

## TC-PRO-004 — Security banner visibility rules

**Preconditions:**
- Device count is less than 3

**Steps:**
1. Open the Profile screen

**Expected Result:**
- Security banner is displayed indicating insufficient devices
- Banner content matches banners shown on other main screens

---

## TC-PRO-005 — Logout button present (inactive)

**Preconditions:**
- Profile screen is displayed

**Steps:**
1. Observe the Logout button

**Expected Result:**
- Logout button is visible
- Logout action is disabled or inactive
- No logout behavior is triggered when tapped

---

## TC-PRO-006 — Privacy Policy link (inactive)

**Preconditions:**
- Profile screen is displayed

**Steps:**
1. Tap the Privacy Policy link

**Expected Result:**
- Link is present
- No navigation or external browser is opened

---

## TC-PRO-007 — Terms & Conditions link (inactive)

**Preconditions:**
- Profile screen is displayed

**Steps:**
1. Tap the Terms & Conditions link

**Expected Result:**
- Link is present
- No navigation or external browser is opened
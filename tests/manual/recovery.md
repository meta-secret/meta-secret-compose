# Recovery

## Responsibility

Validation of backup/restore correctness after reinstall and enforcement of secret recovery restrictions in single-device and multi-device setups.

---

## TC-REC-001 — Reinstall and restore from backup (baseline E2E/manual)

**Preconditions:**
- Device A is authenticated and has at least one saved secret.
- Backup destination has been configured.
- Last successful backup timestamp is visible in logs/diagnostic output.

**Steps:**
1. On Device A, create or update a secret.
2. Trigger backup flow and verify backup completion log/marker.
3. Uninstall the application from Device A.
4. Reinstall the application on Device A.
5. Complete onboarding and authentication.
6. Allow restore flow to run on splash/startup.
7. Open Secrets screen.

**Expected Result:**
- Restore flow runs without fatal errors.
- Restored vault state matches expected pre-uninstall state.
- Previously saved secrets are present and readable according to policy.
- Diagnostic markers show restore start/completion (and no silent failure).

---

## TC-REC-002 — Missing backup source handling

**Preconditions:**
- App is installed without a valid backup URI/configured destination.

**Steps:**
1. Trigger restore on startup.

**Expected Result:**
- App does not crash.
- User remains able to continue onboarding/sign-in flow.
- Diagnostic log clearly marks skipped restore reason (`no backup destination` / equivalent).

---

## TC-REC-003 — Corrupted backup input handling

**Preconditions:**
- Backup source is present but contains invalid/corrupted DB content.

**Steps:**
1. Trigger restore flow.

**Expected Result:**
- Restore failure is surfaced with user-friendly error.
- App avoids partial state corruption and remains operational.
- Diagnostic markers include restore failure reason and exception path.

---

## TC-REC-004 — Single-device cannot recover 3-part protected secret

**Preconditions:**
- Secret was created under policy requiring multi-device (3-part) recovery approvals.
- Only one active device is currently available in the vault network.

**Steps:**
1. On the single active device, attempt to recover/show that protected secret.

**Expected Result:**
- Secret is not revealed directly.
- UI indicates pending/insufficient approvals (or equivalent restriction message).
- No bypass path exists from local-only actions.
- Diagnostic markers show recovery blocked by policy, not by random failure.

---

## Evidence to Capture

- Build/test run identifier (branch + commit SHA).
- Device type + OS version for each participating device.
- Screenshots of key states (restore complete, recovery blocked).
- Relevant log snippets proving backup/restore transitions and policy enforcement.

# Approved registration UX baseline

These are target requirements agreed with the user; they do not claim current implementation compliance.
Core alone decides membership, request result and synchronization readiness. Clients present its results and invoke OS UI.

| Scenario | Required behavior |
|---|---|
| Vault name | Email; Apple/Google on iOS, Google on Android, manual entry as fallback; minimize actions |
| Approval | OS authentication on the trusted approver before submitting Accept/Decline |
| Accepted join | No second OS authentication prompt on the joining device |
| Pending | Wait indefinitely until a decision or explicit Back; Back verifies local cleanup and opens01, without revoking the server request |
| Cancel | Initiator may reset its local attempt and restart; do not claim the Core request is revoked |
| Restart after reset | Verify required key/DB/cache/subscription cleanup before generating new credentials |
| Declined, including after relaunch | Show `Request declined.` on step04 until Back; verified local incomplete-attempt cleanup then Providers01 with empty name; failure/unknown must not show false01 |
| Device name | Use the system-configured device name; no in-app naming field; do not substitute random ID or hardware model |
| Joined but synchronizing | Display `Syncing your Vault…` until Core indicates readiness; English UI throughout |
| Relaunch while pending | Restore existing request context without sending a duplicate request |
| Old result after reset | Must not drive a new attempt; verify Core request identity and client lifecycle |
| Connection failure | Reconcile with Core; expose Back/retry with the required cleanup, not blind duplicate submission |

Check both Android/iOS roles. Unit tests may fake ports; integration/E2E use actual Core. Record real-device OS-authentication evidence separately from emulator bypass. Cancellation races and synchronization readiness must be resolved from the existing Core contract before implementation. Separate domain identity from display name.

## Approved Orbit registration details

- Step 01 shows provider/manual buttons; launch the system sign-in dialog only on a provider tap. Close the system dialog before rotating to step 02.
- Create does not request biometry. Do not request a second biometric prompt on the joining device after approval. Trusted-device authorization remains governed by the existing Core/OS contract.
- Join step04 offers QR, Back and Request. Pending AND Declined Back verify local incomplete-attempt cleanup and return Providers01 with an empty name; never claim server-side request revocation.
- Create known network failure: short English error in the status position for three seconds, then restore step 02. Join network error/decline persists on step 04 until Back. Unknown outcomes require canonical reconciliation before retry.
- QR scan is an input event, not proof of membership. Do not copy the prototype mock scan-to-main shortcut without a supported Core contract.
- The approved source is the versioned Orbit prototype and motion specification under workspace-root UxRevolution; generated design assets come from design-system.

## Existing Core lifecycle mapping (Orbit integration)

- Canonical Pending or Declined + explicit Back clears the unfinished local attempt and opens Providers01. Guard the live initialized Core state for exact Vault/device after pausing/joining refreshes; do not use initialization/generic reset as destructive preflight. Preserve Member, fail closed on unknown/mismatch/partial cleanup, verify credential/cache postconditions and invalidate old collectors. This does not revoke a server request or add atomic remote-race protection.
- After Pending cleanup the old server request can still be approved later, while its old local key is gone; owner scope revision2 explicitly accepts this non-atomic limit. A new attempt starts from01. Relaunch without Back still restores the existing pending request without duplicate submission.
- Changing to a different name may clear only a canonically unregistered device attempt. Unknown/read-error/Member states prohibit destructive cleanup. Cancel/join in-flight client refresh work before replacing credentials.
- Resumed Prepared/Declined state restores only the transient client resolver wrapper, never generates duplicate credentials merely because the process restarted.
- QR reads a bounded plain Core Vault name, including non-email CLI names, preserving case; rejects URLs, JSON and control data. After collapse it automatically submits ordinary existing JOIN. Unknown names cannot auto-create a Vault.
- Scope and platform capability evidence: docs/registration-orbit.md.

# Approved registration UX baseline

These are target requirements agreed with the user; they do not claim current implementation compliance.
Core alone decides membership, request result and synchronization readiness. Clients present its results and invoke OS UI.

| Scenario | Required behavior |
|---|---|
| Vault name | Email; Apple/Google on iOS, Google on Android, manual entry as fallback; minimize actions |
| Approval | OS authentication on the trusted approver before submitting Accept/Decline |
| Accepted join | No second OS authentication prompt on the joining device |
| Pending | Wait indefinitely; no automatic expiry timer imposed by UI |
| Cancel | Initiator may reset its local attempt and restart; do not claim the Core request is revoked |
| Restart after reset | Verify required key/DB/cache/subscription cleanup before generating new credentials |
| Declined, including after relaunch | Show `Request declined.` on step 04 until Back; preserve email and return to step 02 on Back |
| Device name | Use the system-configured device name; no in-app naming field; do not substitute random ID or hardware model |
| Joined but synchronizing | Display `Syncing your Vault…` until Core indicates readiness; English UI throughout |
| Relaunch while pending | Restore existing request context without sending a duplicate request |
| Old result after reset | Must not drive a new attempt; verify Core request identity and client lifecycle |
| Connection failure | Reconcile with Core; expose Back/retry with the required cleanup, not blind duplicate submission |

Check both Android/iOS roles. Unit tests may fake ports; integration/E2E use actual Core. Record real-device OS-authentication evidence separately from emulator bypass. Cancellation races and synchronization readiness must be resolved from the existing Core contract before implementation. Separate domain identity from display name.

## Approved Orbit registration details

- Step 01 shows provider/manual buttons; launch the system sign-in dialog only on a provider tap. Close the system dialog before rotating to step 02.
- Create does not request biometry. Do not request a second biometric prompt on the joining device after approval. Trusted-device authorization remains governed by the existing Core/OS contract.
- Join step 04 offers QR, Back and Request. Pending Back returns to step 02 with the same email; do not claim server-side request revocation.
- Create known network failure: short English error in the status position for three seconds, then restore step 02. Join network error/decline persists on step 04 until Back. Unknown outcomes require canonical reconciliation before retry.
- QR scan is an input event, not proof of membership. Do not copy the prototype mock scan-to-main shortcut without a supported Core contract.
- The approved source is the versioned Orbit prototype and motion specification under workspace-root UxRevolution; generated design assets come from design-system.

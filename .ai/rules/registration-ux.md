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
| Declined, including after relaunch | Return to email selection and explain that the request was declined |
| Device name | Use the system-configured device name; no in-app naming field; do not substitute random ID or hardware model |
| Joined but synchronizing | Display “Устройство подключено. Синхронизируем Vault…” until Core indicates readiness |
| Relaunch while pending | Restore existing request context without sending a duplicate request |
| Old result after reset | Must not drive a new attempt; verify Core request identity and client lifecycle |
| Connection failure | Reconcile with Core; expose Back/retry with the required cleanup, not blind duplicate submission |

Check both Android/iOS roles. Unit tests may fake ports; integration/E2E use actual Core. Record real-device OS-authentication evidence separately from emulator bypass. Cancellation races and synchronization readiness must be resolved from the existing Core contract before implementation. Separate domain identity from display name.

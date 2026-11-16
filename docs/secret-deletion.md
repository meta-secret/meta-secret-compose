# Vault-wide Secret deletion

Android and iOS consume Core's `deletionStates`; they never calculate deletion quorum. One/two devices use local OS biometry/PIN confirmation. At three devices Core supplies `requiresApproval`; other members see `NeedApprove` and submit the exact request UUID. Approve requires OS authentication; Decline follows recovery's decline presentation. Core owns first-response ordering and terminal statuses.

The shared app manager delegates `deleteSecret(opaqueSecretId)` and `decideDeleteSecret({requestId,accepted})` through string-only UniFFI. Core resolves identity, signs requests, synchronizes, applies tombstones and erases ciphertext. Libraries and generated Kotlin/Swift bindings must come from the same Core revision/UDL.

A startup/reconnect refresh covers the cached secret list with a loader. Synchronization failure blocks cached reveal and provides Retry. Successful canonical state reconciles the cache by opaque ID before the loader completes; deletion never optimistically edits preferences. A same-name recreated Secret has a new opaque identity. Existing reveal dialogs are covered during refresh; removal of their opaque Secret ID closes them, clears plaintext and rejects late asynchronous reveal results. A reveal’s own refresh does not restart or discard an otherwise valid reveal.

See ../meta-secret-core/docs/secret-deletion.md for the protocol and atomic outcome/tombstone contract. Logical deletion cannot erase disconnected devices until they reconnect, disk-sector remnants or external backups.

## Additive Liveboard binding

The new typed adapter offers `deleteSecretChecked(opaqueSecretId)` and preserves
its version1 producer receipt separately from legacy deletion responses. The
immutable original source reference and known UUID correlate later read-only
observations; a producer phase or disappearance alone does not prove deletion.
No automatic continuation or legacy fallback is added. An entered mutation
without a usable receipt returns Uncertain for the owning session to fence.
Existing screens/app-manager paths remain legacy until the mobile Liveboard
session and UI migration. See [native integration](liveboard.md); parser/unit
checks do not replace real deletion UI E2E or OS-auth verification.

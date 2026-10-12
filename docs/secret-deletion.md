# Vault-wide Secret deletion

Android and iOS consume Core's `deletionStates`; they never calculate deletion quorum. One/two devices use local OS biometry/PIN confirmation. At three devices Core supplies `requiresApproval`; other members see `NeedApprove` and submit the exact request UUID. Approve requires OS authentication; Decline follows recovery's decline presentation. Core owns first-response ordering and terminal statuses.

The shared app manager delegates `deleteSecret(opaqueSecretId)` and `decideDeleteSecret({requestId,accepted})` through string-only UniFFI. Core resolves identity, signs requests, synchronizes, applies tombstones and erases ciphertext. Libraries and generated Kotlin/Swift bindings must come from the same Core revision/UDL.

A startup/reconnect refresh covers the cached secret list with a loader. Synchronization failure blocks cached reveal and provides Retry. Successful canonical state reconciles the cache by opaque ID before the loader completes; deletion never optimistically edits preferences. A same-name recreated Secret has a new opaque identity. Existing reveal dialogs are covered during refresh; removal of their opaque Secret ID closes them, clears plaintext and rejects late asynchronous reveal results. A reveal’s own refresh does not restart or discard an otherwise valid reveal.

See ../meta-secret-core/docs/secret-deletion.md for the protocol and atomic outcome/tombstone contract. Logical deletion cannot erase disconnected devices until they reconnect, disk-sector remnants or external backups.

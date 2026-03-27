# Security

Minimum security requirements for this project.  
Scope: Kotlin-first (KMM) app with a minimal Swift proxy and a prebuilt Rust binary accessed via FFI.  
Strictness level: **Baseline** (can be tightened later).

See also: [CLAUDE.md](CLAUDE.md), [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md), [ARCHITECTURE.md](ARCHITECTURE.md), [CODE_STYLE.md](CODE_STYLE.md).

---

## Secrets and storage

- **iOS:** Secrets/tokens in **Keychain** only.
- **Android:** Secrets/tokens via **Keystore** (e.g. EncryptedSharedPreferences or equivalent Keystore-backed storage).
- No extra app-level encryption layers are required at this time unless product requires it.
- Secrets/tokens must not be checked into source control, hardcoded, or shipped in bundles.
- Access goes through `core/` interfaces; `ui/` must never handle raw secrets.

---

## Permissions

Request only what is needed:

- **Biometrics** — authentication/lock
- **Camera** — camera features
- **Push** — messaging/notifications

No other permissions without an explicit decision. Show a clear in-app rationale before the system prompt where applicable.

---

## Input validation

Beyond platform defaults: when user input or external data appears (forms, files, FFI buffers), validation lives in **core/** — type-safe, length-bounded, sealed errors, reject unknown fields by default.

---

## Anti-injection

No SQL/templating/command execution or network APIs in scope today. If added later, update this document (parameterized queries, escaping, sandboxing, etc.).

---

## Logging and PII

Avoid secrets and PII in logs; mask identifiers when possible. See [CODE_STYLE.md](CODE_STYLE.md) for logging format.

---

## App lock (biometrics / PIN)

If enabled: lock on demand and on inactivity timeout; re-authenticate on resume when locked; store lock state in Keychain/Keystore; never log lock state.

---

## Error messages

User-facing text must be safe (no stack traces, file paths, raw codes). Map internal errors to sealed types in `core/` and localized strings in `ui/`. Do not expose raw FFI/native error strings in the UI.

---

## Clipboard and screenshots

No extra restrictions today. If secrets are copied to clipboard, add time-based clearing and UX warning (define later).

---

## FFI / native binary integrity

No mandatory integrity checks for the prebuilt library today. Revisit if distribution or threat model changes (hash pinning, signature verification, secure loading).

---

## Backups, data retention, CI/CD, signing, configuration

Reserved; extend when policies exist.

---

## Security checklist (baseline)

- [ ] Secrets/tokens only in Keychain (iOS) / Keystore-backed storage (Android)
- [ ] Only biometrics, camera, and push permissions unless explicitly expanded
- [ ] Safe user-facing errors; sealed internal errors
- [ ] No secrets/PII in logs (until a formal logging policy exists)
- [ ] App lock re-auth on resume/timeout when enabled
- [ ] No injection surfaces without documented defenses
- [ ] No FFI from `ui/`; only via `core/` interfaces

---

## Change control

Changes that introduce new data flows, storage, permissions, or external interfaces should include an ADR, an update to this file, and tests where security-critical behavior applies.

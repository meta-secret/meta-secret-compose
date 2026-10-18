# Orbit registration

The registration-only Compose module consumes design-system.lock.json version0.2.0,
Manrope and generated resources in ui/design/generated and composeResources.
The approved visual source is the workspace-root Orbit05.13 prototype at eb9f704.
Legacy main, approval, secrets, recovery and settings retain their existing UI.

## Design resources during builds

Normal Gradle Android/iOS builds run `prepareDesignSystem` before resource
preprocessing and Kotlin compilation. In this workspace, it checks the sibling
`../design-system` package name/version against this client's pin, installs
lockfile-pinned npm dependencies with lifecycle scripts disabled when necessary,
builds the shared exports, and syncs **Compose only**. It never changes the Web
client. Workspace mode requires installed Node >=22 and npm. Gradle searches the
build environment's PATH first; on macOS only, missing tools also use the known
Homebrew locations `/opt/homebrew/bin` and `/usr/local/bin`. This supports IDE
builds with a minimal PATH without changing the user's global shell settings.
An old Node found on PATH fails its version check rather than silently switching
to a Homebrew installation. Unchanged generation/sync inputs remain up-to-date.

Independent `-PdesignSystemNode=/absolute/path/to/node` and
`-PdesignSystemNpm=/absolute/path/to/npm` overrides take precedence. Invalid
overrides fail with a diagnostic; they do not select a different installation.
Bare override names search PATH only. Relative paths/PATH entries use the shared
design package as the execution working directory. Gradle passes absolute argv
and prepends the selected Node directory, then npm directory, to each design
process's original PATH so npm's `env node` shebang and children work too.
The selected Node must also be discoverable as `node` (`node.exe` on Windows)
in that same directory and resolve to the same executable. A renamed override
without that entry fails clearly instead of letting npm use a different Node.
No extra shell or Node-owned npm launcher is introduced; npm keeps its existing
Gradle Exec invocation. Windows execution is not tested by this macOS repair.

Tooling regressions run the actual Gradle Kotlin script in isolated fixtures:
`python3 gradle/tests/test_design_system_toolchain.py`. They require an installed
macOS Homebrew Node for the fallback cases; they do not install tools or launch
the app. Logs default to `build/test-reports/toolchain`; set
`DESIGN_SYSTEM_TEST_RECEIPTS` to retain a separate audit directory.

A standalone Compose checkout uses its committed generated resources and lock.
JVM SHA-256 verification needs no Node/npm or sibling repository. Use
`./gradlew prepareDesignSystem -PdesignSystemMode=pinned` to select that same
path explicitly; `-PdesignSystemMode=workspace` requires the sibling package.
The default is `auto`. A source package version change must first be adopted
through the explicit shared design export/sync operation; Gradle does not
silently replace the pinned package identity/version. Resource hash mismatches
fail before compilation. Generated PNG captures under `docs/registration-orbit-visual`
are ignored; its README and other authored documentation remain versioned.

## Layers

RegistrationScreen is a Voyager screen. Its injected screen-scoped
RegistrationViewModel renders RegistrationUiState and accepts RegistrationIntent.
A unique screen key and Voyager LifecycleEffectOnce dispose the session with the screen, preventing a completed VM/session from being
reused on logout. CoreRegistrationSession serializes existing Core ports and
secure-storage lifecycle. Composables never call native/Core bindings.

Preparation checks availability and prepares local credentials; it never submits
registration. Create/Request are explicit single-flight operations. Signals from
the existing authenticated request SSE trigger canonical state reconciliation;
matching Vault/device identity is required. Core remains the membership authority.
A successful relevant canonical refresh precedes the visual Syncing→main transition.

On canonical readiness, registration replaces itself with the one navigated
MainScreen. Voyager owns the 900 ms fade and centered scale (.965→1) reveal;
registration never composes a live main-screen preview. The per-screen override
applies only to Replace, preserving the existing transitions on other routes.

Back while canonical Pending or after Declined clears only the unfinished local registration and
returns Providers01 with an empty name. The live initialized Core snapshot must
still identify the exact same Vault/device as Pending or Declined after refreshes are paused
and joined. No initialization or generic reset is used to authorize deletion.
Member is preserved; unreadable/mismatched/unknown results or failed/partial cleanup
remain actionable04, never a false successful01. The existing iOS storage Boolean
is propagated and master/pending credential absence is checked. This is not an
atomic filesystem or future remote-approval guarantee. Old observation is stopped;
the VM cancels/joins its collector and invalidates its local observation generation.

This local cleanup does not revoke the server request. An old Pending request may
be approved later after its local keys were erased; the owner explicitly accepted
this scope limitation. A retry starts from01 and may select another Vault. Existing
pre-Request/manual/other-error Back paths remain unchanged. The separate generic
prepare/name-change cleanup and nondestructive reset port retain their existing
behavior. Resumed Prepared/Declined Core state restores only the transient client
resolver wrapper; relaunch alone does not generate another identity.

## Presentation contract

01 offers providers/manual input without opening a system dialog automatically.
Provider cancellation remains01. 02 checks the selected name and offers
Create/Join/Enter. 03 manual input rotates clockwise on OK and counterclockwise
on Back. 04 offers QR/Back/Request. Request circles merge before the loader.
Pending/Declined remain04 until explicit Back, which performs the guarded cleanup above. Other join errors keep their existing Back→02 path; known Create failure shows its red
status for three seconds with no actions, then restores02. Unknown Create
outcomes retain a working Back and require canonical reconciliation.

Create adds no biometric prompt. Approved JOIN adds no second joining-device
prompt. Existing Member startup still requires its existing local OS unlock;
a startup refresh failure cannot route through registration to bypass it.
Trusted-device approval retains the legacy biometric flow.

QR contains a bounded plain Vault name (including non-email CLI names), with
case preserved. Empty/control/URL/JSON payloads are rejected. Scan collapses the
sheet, prepares that name and automatically submits the ordinary existing JOIN;
an unknown name cannot implicitly create a Vault. QR alone never grants access.

Connected devices display that QR through the existing legacy Devices → Add
device dialog. `DevicesScreenViewModel.vaultName` comes from the existing
Core-derived VaultStatsProvider StateFlow, not cached sign-in/provider data.
The QR contains the exact current name; the full string is visible beside it.
Blank/loading names disable Add device. Changing/disappearing canonical names,
Close and leaving Devices remove the selected QR; an old name returning does
not automatically reopen the dialog. The existing qr-kit renderer uses a white
margin and the content scrolls in compact windows. Generation itself requests
no camera permission and changes no JOIN authorization.

QR-generation unit tests cover canonical-source selection and dialog lifecycle.
Kotlin compilation and unit checks do not establish real camera recognition,
native portrait/landscape rendering or real-Core UI E2E; those remain separate
acceptance evidence.

The camera is mounted only after the tile expands, removed before collapse and
removed while the lifecycle is not resumed. Permission UI is native. The glass
uses a capture of actual static step04 content, blurred through platform DI:
Android CPU processing supports API25; iOS uses Skia. Camera frames are never
captured or stored. The backdrop is refreshed on size/orientation changes. The tile keeps60/40 logical insets from the full window, respecting horizontal
cutouts. The
camera square is centered in the tile; compact landscape centers camera+Back
as a group to fit. Reduced motion respects the OS animation scale.

## Actual platform boundaries

Android Apple uses the existing provider port and an optional HTTPS email broker
in the Core repository's web-server. It stays unavailable when the public
`APPLE_EMAIL_BROKER_URL` build property is absent or invalid; no browser/network
is started in that state. Google/manual retain their existing behavior.
iOS accepts Apple relay addresses; repeated authorization can use its credential's
email claim as a name label, never as Core authorization. Physical Apple/Google
account permission behavior must be reported separately from component tests.
System device names are used when exposed by the OS. iOS can supply a generic
name without Apple's user-assigned-name entitlement; the client does not invent
an identity or request an in-app naming field.

### Android Apple transport and manual acceptance

The Android build property `APPLE_EMAIL_BROKER_URL` is a public HTTPS origin,
default empty. It must match the separately configured and deployed broker.
Do not put a client secret, Apple key, token, query or callback URL into this
property. No HTTPS server currently exists for this continuation; the app must
remain unconfigured until the owner supplies one. Building the adapter is not
live provider acceptance or deployment.

The broker receives Apple's registered HTTPS form POST at `/apple/callback`.
Android generates a process-memory43character random verifier and sends its
PKCE-style SHA256 challenge to `/apple/start`. The verifier is sent only in
HTTPS POST bodies to `/apple/take` or `/apple/cancel`; no preference, saved-state,
file, URL or log contains it. The broker validates the Apple signature and
transaction claims, including state/nonce and hybrid code binding, then returns
only a verified email/relay through single-use take. See the Core repository's
`docs/apple-email-provider.md` for the authoritative server wire/config contract.

The broker's separate app return is exactly
`metasecret://apple-auth/callback?requestId=<opaque-id>`. Its ID is only a wakeup;
the app never accepts Intent email/token/extras as authority. A foreign, old,
duplicate, cancelled or cold-start Intent cannot select a name. Fixed paths,
HTTPS only, no redirects,30second HTTP deadline and16KiB response cap apply.
The external browser owns Apple's credential/consent UI; no WebView is used.

One provider operation may be active. Callback and resume share its take guard;
a genuine callback arriving during a pending resume probe gets one event-driven
follow-up. Browser Back with pending result returns Cancelled/Providers01.
Activity host detach cancels its pending attempt; the application-scoped provider
and weak browser attachment let a retained ViewModel make a fresh tap after
recreation. Old host destruction cannot detach a newly attached host. Process
death loses the proof and cannot resume that attempt; start a new tap. Server
TTL300seconds bounds abandoned RAM state. No polling substitutes for callback.

Deterministic adapter/HTTP/lifecycle tests use synthetic broker responses. They
do not prove real Apple account behavior. By the owner's explicit request,
actual first/repeat/Hide My Email/cancel/network-error/browser-return checks are
performed manually by the owner after real HTTPS/Services ID configuration.
Automated real-provider UI E2E is not run or marked PASS in this task. Existing
Test51 runtime history and its prerequisites remain separate evidence.

## Validation

Stage evidence lives in .ai/artifacts/run/MS-20261018000730-*.md.
Native visual instrumentation uses sample state and an explicitly labeled
synthetic camera; it exercises real Compose rendering and actual blur, and makes
no Core calls. It is not real camera/auth or final Core E2E evidence. Production
QR uses the existing real camera scanner. Root owns the final local Core UI E2E
gate and application release decisions.80% overall coverage remains advisory.

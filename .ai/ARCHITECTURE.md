# AI architecture — meta-secret-compose

.ai/WORKFLOW.md is the canonical implementation stage contract; .ai/ORCHESTRATOR.md owns command routing. AGENTS.md and CLAUDE.md bootstrap those sources, not separate workflows. See INDEX.md for agents, templates and commands.

## Application boundary

- composeApp/commonMain owns shared Compose Multiplatform views, presentation/ViewModels and platform-independent client integration.
- androidMain and iosMain implement injected platform ports; iosApp hosts the shared UI and native integration.
- MVVM + Coordinator; typed state flows down and intents flow up. Local focus/hover/animation state is allowed in views; application operations belong in ViewModels/controllers.
- Interface + DI is the platform abstraction. This project forbids expect/actual.
- Existing Core ports/integration adapters consume UniFFI. Views/ViewModels never call native bindings directly. Rust Core alone owns crypto, membership, approvals and synchronization decisions; this repository does not edit Rust.
- Current targets are Android and iOS; phone/tablet/orientation support is a UI responsibility. A standalone desktop target is not implicitly introduced by the registration redesign.

## Liveboard native boundary

The existing Core aggregate includes eight additive String Liveboard ports.
Focused platform delegates use the existing native runtime and the same iOS
SwiftBridge instance. The common stateless adapter validates native object
receipts, exact operation/Claim identities and immutable lossless producer R/U
references; it adds no authorization, quorum, replay or synchronization policy.
Optional metadata stays on the same decoded Member. Ambiguous raw keys disable
only its Liveboard projection. See [Native Liveboard integration](../docs/liveboard.md)
for the implemented boundary and current verification limits.

The existing shared MainScreen entry renders LiveboardScreen→LiveboardRootCoordinator,
which projects the keyed Session/read owner into the board, Secrets, Profile, invite
and Create overlays. The legacy Main ViewModel is not a second Liveboard owner.
Koin injects existing Core/auth/synchronization services and Android/iOS platform
ports for clipboard, attachment/Back and reduced motion. Generated shared design
exports drive materials, typography, glyphs and motion. This wiring is implemented;
actual SDK builds, OS consent and visual fidelity remain unverified gates.

NativeLiveboardOperations adds eight async routes with REQUIRED frozen expected
scope. The same pure adapter preparation rejects invalid input before admission;
SAME-owner actual IO leases drain before key effects and release before strict
decoding/results. Entered A typed truth remains A after retirement; retired queued
A returns NotEntered with zero Core. No UI/cache/notifications or generic stale
result drop is introduced. A direct facade caller can still discard entered truth;
the keyed Session independently owns closed typed command lifetime.

LiveboardSessionFactory requires the exact current successful physical Member
frame and decoded owner/identity, reusing ONE keyed Session. Remove uses Core's
removable noncurrent device projection. A fixed private Queued→Admitted/Cancelled
CAS runs at the existing leased actual entry before Core, without callbacks.
Skipped is NotEntered. The independent worker releases the native lease, retains
exact A typed truth and settles native/private work before UI. Successful
phaseUnknown R/U differs from thrown Uncertain without usable fabricated sources.
View cancellation cannot lose admitted truth; only the original queued caller
can release its exact unentered slot. Pending duplicates reject only themselves;
retained identities return the SAME result/no resubmit. Exact cleanup preserves
a newer slot/factory Session. Selected/pending/completed Remove truth uses one
identity tuple CAS; remaining typed writes share a private journal. Reservation
precedes auth; Actions.remove/raw Remove share the SAME original command, with
no duplicate prompt/cancel authority. Raw Remove gains no extra prompt. Create
claims only its selected clearable revision at actual entry, dropping FFI payload
references afterward. Immutable completed R/U/Unknown survives all view/privacy
changes; exact typed terminal correlation+post-command proof alone releases new
selection. Reconcile is explicit fresh read-only SAME-ref observation, continuation
separately authenticated; raw bool decisions retain safe attempt metadata only.

Presentation requires current undisposed Member plus exact privacy/entry context;
private truth is independent. Required SAME privacy relay reaches NativeReadRuntime,
actual NotificationCoordinator, Socket BG/FG and captured key hooks. Private
technical admission closes before any observable wipe/error/visibility callback,
including equal errors and failed/member-loss read publication. Raw Remove fixed
entry and typed/Show/Copy paths consult it. A new foreground same-scope requirement
uses the SAME existing lane; it cannot restore old readiness/cache. Current actual
Member plus exact covered target OR its parentless fulfilled SUCCESS certifies
readiness across frame conflation. Manual04 display stays pinned independently.
Scope/attempt rechecks follow inline privacy callbacks before canonical effects.
These source-ordering properties exceed direct intermediate-observer unit coverage.

Sensitive transient per-card values/capsules never enter producer journal or
Deferred; held UI closures carry opaque tokens. Hide/Copy is exact by secret ID.
Ordinary physical reads temporarily mask disclosure/Copy. Private previously
authorized values restore only after a later SAME-owner/scope successful frame,
current projection and foreground idle/readiness proof. Local selection stays exact.
Recovered cards bind original Accepted context to the exact native receipt, then
require equal same-Claim Done context after ANY later physical read, even if Secret
appears unchanged. Only `showRecovered/exactClaim`→ready `recover/no-claim` may differ;
all remaining Secret fields, owner/epoch/Member/frame/card/generation fences stay exact.
One payload-free per-card helper shares the private observe/capture/restore/Copy
predicate and monotonic Done-required phase with bounded attempt scalars. The same
publicly delivered disclosure/reference is reused; held native/UI returns gain no
restoration right. No additional Show, native reveal, auth prompt or read occurs.
Strict additive context/receipt decoding reuses the existing raw duplicate guard,
Core v1 UTF-8/compact-metadata bounds, sequence/order grammar and redacted DTO/errors.
Absent proof preserves first Show only; malformed recovered proof rejects, while
Local envelope extraction preserves its legacy behavior. Native String ABI is unchanged.
The [R32 implementation report](artifacts/run/MS-20261019210543-004b-recovered-show-r32-implementation.md)
records bounded host evidence; full/static/Kover/native/UI gates remain separate.
The null-frame
baseline permits only a later genuine successful read, never manufactured readiness.
Hide, route/filter changes, selected identity/Claim change, errors, background,
key/member loss and disposal revoke disclosure rather than renewing it on refresh.
Ordinary error/auth Cancel preserves authored draft while revoking disclosure,
Copy and queued selected payload. Hard BG/key/member/dispose/idle close clears
input too; private admitted Unknown/R-U survives. No post-callback cancel sweep
can wipe a newly issued generation. Exact candidate attach/detach/disposal cannot
clobber B; same-key disposed journal remains a tombstone. Show local/recovered
and outgoing/continuation/both decisions use existing OS consent and exact frozen
Core identity/permission checks. Requester Recover/reconcile add no prompt;
clipboard policy stays unchanged. OS callbacks only answer; UI state changes run
on existing UI context. Real OS/snapshot/native UI acceptance remains pending.

Koin supplies one NativeCanonicalReadOwner to all seven existing manager/Socket/
LocalState physical read sites, using the SAME Core/cache and initialization
owner. Opaque owner/epoch requirements receive coverage only from an actual
successful entry-time read. The read/parse mutex is released before publication;
failure preserves pending requirements. The cache owns one immutable state/frame
pair with read-only projections; current values/replay use that pair while legacy
collectors retain structural duplicate suppression. No extra runtime, poller,
business executor or snapshot store is introduced.

The same owner has a private read/receiver-entry handoff: READ starts precede the
three existing synchronization publishers and the standalone LocalState read;
outer deletion passes its exact captured scope/token through nested reconciliation.
Read-read publication remains overlapping, physical/native fences remain original.
Only a receiver decision may retain payload-free PREPARING metadata across a
technical mask, with one original auth. Genuine current SUCCESS/demand proof and
fresh SAME full identity/permission bind one new gate to the same journal command.
Actual reads started at registration count as overlap; FAILED/cancel/retirement
ends that preparation before wake, never satisfies a requirement. Exact live-watch
membership closes synchronously before deferred privacy callbacks; new ENTRY checks
that same membership in its enqueue/grant CAS. Deferred completion is notification,
not permission, and the original already-committed entry boundary remains unchanged.
Hard privacy invalidation has a separate stamp; old selection/generation is never
revived and NotEntered is terminal. Runnable lane.pending reserves its FIFO position
at original request/coalescing before later ENTRY; same captured scope/generation
keeps that position. Reservation alone creates no overlap, proof, failure or begin
counter. Paused/hidden pending parks without a barrier; original resume/foreground
or pending handoff reserves runnable work, and initial EXPLICIT still checks only
!hidden && !paused. FIFO ENTRY excludes read starts through original checks/CAS and
one native call, releasing after facade/actual lease return, before settlement.
No actual lease/mutex spans auth/read awaiting, callback enters CAS/native, extra
reader/worker is created, or producer/removal policy changes. Internal Manager
state-reader and Socket state/claim delegates keep original bodies/ports, one set
of flows/flags, one lane/scope and effect-free acyclic construction. Host/native
acceptance of this coordination remains pending separate actual gates.

With owner/cache on the SAME immediate Main UI dispatcher, current-scope and
monotonic-attempt admission precede cache/storage effects; older SUCCESS/FAILED/
raw-thrown reads cannot replace/revoke a newer snapshot or show a stale Socket
error. Exact frame/scope checks precede captured SUCCESS completion; scope is
rechecked after each inline target callback. Stable parentless Deferred replies
retain exact proof across awaiter cancellation/conflation; cache/loading is no
proof. Retired targets complete payload-free outside all native/lifecycle locks.
Scoped retirement revokes only an A-owned frame on UI before callbacks, preserving
fresh B published before or inline during retirement; no later unconditional
Manager invalidate remains.

The SAME initialization owner admits actual-entry read/auth leases. KeyLifecycle
owns the single private initialized-key comparison and expected-token reservation:
close admission, drain real FFI, existing key/init effects, finally settlement,
then observer/presentation callbacks outside locks. Same key preserves owner/epoch;
failed replacement fails closed, including distinct uninitialized generations.
KeyLifecycle owns prior-scope retirement in its existing NonCancellable finally
AFTER reservation finish, even for caller cancellation or a null stamp. SAME-key
stampA preserves A; a cancelled foreign non-owner waiter cannot retire it. This
cleanup uses the captured finish stamp instead of public scope after callbacks;
Manager/Reset no longer depend on consuming a receipt to retire old promises.
Profile queued cleanup checks its captured token and successor receipt before
cache/navigation; false clear keeps its existing no-navigation policy. The actual
Join transport rethrows provider CancellationException instead of converting it
to terminal authorization error; HTTP/SSE/reconnect policy is unchanged.

The stateless NativeOperationPort shares that owner/environment through required
grouped runtimes. Fourteen existing Manager native branches and Socket claim
lookup acquire actual-entry leases, released before parsing/UI. Scope checks
span Manager awaits, consumption and delayed caller restoration; stale value/error
results are suppressed and cancellation is rethrown. Socket also retains the
originating actual-read attempt and rechecks processing identity between observable
effects. Queued cancellation owns no native lease. These checks add no proof,
permission, second runtime or retry, and do not certify arbitrary callbacks.

Registration uses the SAME exclusive drained key reservation for private
nonpublishing snapshots, ordered generate/save/init, submit and exact incomplete
cleanup. No nested normal Manager init/read or second reader is created. Resolver2
uses the actual-entry operation port with frozen prepared-wrapper scope. Receipts
and callback effects are rechecked after finish/unlock. Member-never-clear,
pending-email-before-signUp, ambiguous exactly-once reconciliation and observation-only
Back remain unchanged. Main rechecks removal receipts inside the actual queued UI
task; valid false clear still navigates, unlike Profile's false-clear policy.

One ephemeral startup receipt records exact uninitialized originA→successful initB
and epochB. Reset/replaced/failed/missing init invalidates it; SAME-key preserves it.
Only an eligible unbound resume can claim it before keys under the same gate.
Accepted current own prepare/submit/cleanup receipts (Failed included), or a resume
snapshot/owned failure, spend Session eligibility. Pure observe/no-key empty resume
do not bind; no pre-await/finally flip. Bound failed attempts receive no successor
receipt from external init; strict current-owner resume remains legal. Foreign
cancel before owned CAS spends nothing; after owned CAS the fail-closed one-shot
stays spent even without Session adoption. The flag does not restore metadata.

All seven normal read paths freeze nullable origin before the first observable,
schedule or suspension, through the physical mutex and actual IO/native gate.
Null stays null; retired key owner A cannot read successor B or cover late B
tickets. Socket delegates existing scheduling to NativeRefreshLane, with distinct
visibility metadata and
one active/coalesced pending origin. Cancelled A remains owned until actual
Job/native completion and synchronization finish before successor start. A
parentless begin/settled handshake handles pre-body cancellation; exact current
flight/attempt guards retain the ReadOwner check. Background invalidates before
disconnect, hidden events enter no read, and foreground itself queues fresh work
after drain. Initial explicit refresh is retained; key owner/epoch stays stable
without init/signUp. Callbacks hold no native/physical/lifecycle locks.

The integrated Create state owns Plain and 12/24 Seed input. Known errors close the
modal and retain the retry draft; deliberate reopen restores it, while auth Cancel
leaves the form open. Ordinary same-owner refresh preserves input/form state and
the original payload-free Saved completion token; the generated 520ms deadline does
not restart. Hard privacy and explicit idle Cancel clear input. SAME-token lifetime
is tested in common services; actual 519–520ms graphics/remount timing is not proven.

Tile 04 handles only its visible eligible queue head in retained arrival order;
there is no picker. Decisions keep exact request identity and existing OS consent.
Errors cover the queue until explicit tile dismissal, which does not consume a
request or bypass fresh canonical readiness.

After the reviewed mechanical update to 96 Kotlin files and two configuration
files, current validation is one fresh full run: 260 methods/64 suites in 57.068s,
zero failures/errors/skips; no selected rerun occurred. Main/common/Android debug Kotlin compiled for units, not
an APK, native SDK, iOS app or rendered UI. The
[independent actual review](../../.ai/artifacts/run/MS-20261019210543-native-static-unit-coverage-root-review.md)
and [configured validation report](artifacts/run/MS-20261019210543-005b-native-static-unit-coverage-green.md)
accept that transaction. Earlier selected 56/33 and full 260/64 belong to the preceding
input's [ordinary-refresh review](artifacts/run/MS-20261019210543-005c-native-ordinary-refresh-attempt-2-actual-review.md),
covering exact disclosure/Copy restoration, revocation/current-owner controls,
original Create token/overlay lifetime and null-frame recovery with controlled ports.

Configured coverage now passes at 4744/16236 lines (29.22%), generated code included;
all 101 unit output records are byte+mtime exact across coverage, with 0 additional
unit tests/tasks. The 80% goal stays advisory. Actual Detekt passes 1000/1000 at the
unchanged limit; five additional line-based LongMethod identities explain the 995
projection, and size/structural debt remains visible with no budget left. Prior
29.72% and required static FAIL 4626/1000 remain immutable historical measurements.

The separate [Web parent review](../../meta-secret-core/.ai/artifacts/run/MS-20261019210543-005c-web-create-parent-final-review.md)
accepted 58 selected methods/6 suites and 665 full executions/58 suites (656 distinct
labels), plus configured type/lint/build/coverage checks. Its draft/modal parity is
bounded component evidence, not native or real Core UI acceptance. Native passive
adapter changes in 4 test-source files and 12 controlled-input tests are independently
[source-reviewed](../../.ai/artifacts/run/MS-20261019210543-e2e-native-passive-cache-independent-review.md)
but **UNRUN**. Their source acceptance does not imply SDK compilation.

Actual APK/native SDK/iOS app builds, system biometric/credential Cancel, native
snapshot/privacy/clipboard behavior, normal/reduced motion and prototype fidelity,
519–520ms Saved graphics, iOS offMain DB cleanup with unchanged SwiftBridge, and
preserved real Core UI E2E 1–50 remain pending. Emulator autoSuccess and iOS auth
bypasses cannot certify real OS Cancel. Historical gate reports remain immutable;
no full-session security or arbitrary callback/cancellation schedule is inferred.
Core remains the sole permission, quorum, cryptography and domain synchronization
authority. Detailed ownership contracts and evidence limits are in
[docs/liveboard.md](../docs/liveboard.md).

## Approved registration migration

Read rules/ui-migration-profile.md, rules/registration-ux.md, rules/kmp-principles.md, rules/kmp-code-style.md and skills/architecture-guardian/SKILL.md. The versioned workspace-root design-system exports are the shared visual source; UxRevolution holds reference artifacts, not runtime dependencies. Unrelated screens retain their legacy design.

## Evidence

Keep stage artifacts in .ai/artifacts/run using WORKFLOW.md naming. Run python3 .ai/scripts/check-workflow-dependencies.py before execution. Code/constraint review, applicable design/motion review and required behavior tests remain gates. Overall 80% coverage is advisory under rules/coverage-policy.md. Preserve explicit session authorization separately for commit, push, PR and deployment.

The Orbit registration module is documented in docs/registration-orbit.md.
RegistrationViewModel is injected with a unique Voyager screen key; a LifecycleEffectOnce
cleanup ties session lifetime to navigation, including logout. Its client session wraps
existing Core ports and serializes unregistered local lifecycle work; it adds no
domain membership, protocol, FFI or cryptographic rules. Scoped Compose graphics
ports own the real captured backdrop blur; camera frames never enter that port.

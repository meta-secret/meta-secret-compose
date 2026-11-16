# Native Liveboard integration

The shared design-system 0.3.1 exports, native Core bindings and shared Android/iOS Liveboard renderer are
integrated. Same-owner read/cache/key lifecycle, closed Session command lifetime, per-card transient privacy,
manual tile 04 and existing OS-port orchestration are implemented. Controlled common-service tests pass; actual
platform builds, real OS prompt/Cancel, graphics/motion fidelity and real Core UI E2E remain separate pending gates.

## Shared native renderer

The existing `MainScreen` entry now renders `LiveboardScreen`, which attaches `LiveboardRootCoordinator` to the
existing keyed Session and canonical read owner. The shared board, Vault/device slots, Secrets list, Profile,
invite and Create overlays consume that root's typed state and intents; the legacy Main ViewModel is not an
additional Liveboard owner. Rendered callbacks recheck the same content, chrome revision and privacy epoch.
Koin supplies existing native/auth/synchronization services and injected Android/iOS clipboard, attachment/Back
and reduced-motion ports. This source wiring does not establish SDK compilation or real system-prompt behavior.

Shared generated tokens, materials, dot glyphs and timelines drive the renderer. Create owns full Plain and
12/24-word Seed backing state. Known errors close its modal while retaining the retry draft; deliberate reopen
restores that draft. Auth Cancel leaves the form open. Ordinary same-owner refresh preserves authored input and
form presentation while technical admission is blocked. Hard privacy changes and explicit idle Cancel clear input.

Tile 04 processes only its currently visible eligible request, in retained arrival order. The bell count represents
the queue; there is no request picker. Approve/Decline retains the exact request identity and existing OS consent.
Errors take display priority and remain until the user taps the tile; dismissing one does not consume the queued
request and requires fresh canonical readiness before another decision.

## Native phone orientation — 24 October 2026

The owner temporarily selected upright portrait for the native phone apps. iPhone's default supported-orientation
array is Portrait only; the iPad-qualified array retains its original four orientations. Android MainActivity uses
the host-only PhoneOrientationPolicy: valid smallestScreenWidthDp below600 requests portrait,600+ remains
unrestricted, and leaving the phone class releases only a still-current portrait request owned by that policy.
It does not classify a device from the current rotated width, keyboard or draft. Unknown width acquires no lock.
Current Android targetSdk remains34 (compileSdk36); newer target/OEM/display compatibility rules may affect
enforcement, so a requested value alone is not runtime proof. No global manifest portrait/fullscreen workaround
or user orientation-setting write is added to the app.

The original native renderer tests now distinguish a physical landscape request rejected by phone policy from
supported tablet landscape rendering. Phone captures use `phone-portrait-policy` labels; they retain actual IME,
nonzero contained form/actions, authored input and later privacy/reset assertions. The Android controlled host
invokes the same production policy, uses UiAutomation for physical rotation instead of overriding the Activity's
request, and restores its captured rotation baseline. iOS keeps the two-stable-observation geometry requirement.
These source changes and eight selected host policy unit checks are not native rotation/IME acceptance: fresh
owning builds and actual policy runs remain pending. The earlier Android51 VisualFAIL remains historicalFAIL.

Tablets retain current orientations only subject to fit verification; no phone run certifies tablet fit. Web remains
adaptive and unchanged. The proposed compact landscape layout is deferred, not approved. Design-system0.3.1
styles, input composition, draft/controller behavior and OS-auth policy are unchanged by this platform policy.

## Same Member snapshot

`AppStateModel` retains optional `UserMemberFullInfo.liveboardState` as a JSON element. The unrelated legacy Member
model and `CommonResponseModel` retain their existing contracts. Before the permissive legacy parser runs, a raw
guard checks JSON literal grammar and duplicate keys, including escaped equivalent keys. It also checks the actual
scalar types of the envelope Boolean and consumed Member, Vault, Device and Secret identity/label fields. Quoted
Booleans, numeric string identities/labels, quoted word counts and bare labels cannot become new Liveboard data
through legacy coercion. Empty string labels and actual optional null fields retain their existing meaning; no new
business range or public-key rule is added. A failed guard removes only Liveboard metadata; legacy callers still
read Member.

`LiveboardStateDecoder` projects one successful native Member. It validates schema1, required types and known enum
values, Vault/current-member identity, distinct Secret/operation/peer IDs and same-snapshot Secret labels. Core
supplies storage mode, primary action, exact recovery Claim, readiness, removal IDs and decision permissions. The
client does not derive these from device counts. Missing, null, unsupported or mismatched metadata returns
unavailable projection.

Operation requester/responder IDs remain independent of the current member list: a Join candidate can be an
outsider and a verified response can outlive current eligibility. Displaying these IDs does not grant decision
authority.

## Native envelopes and platform ports

`MetaSecretLiveboardOperationsInterface` composes eight synchronous String calls with the existing Core aggregate.
Android forwards through its existing native facade/library/bootstrap. A focused iOS delegate receives the same
SwiftBridge instance already used by its service. The new Swift extension calls actual generated free functions;
generated glue and manual compatibility remain intact.

Checked calls are Create, Recover, Join decision and Remove decision. Delete Secret, Remove Device, producer
observation and explicit local Remove continuation return a separate version1 producer receipt. Native success is
`{success:true,message:OBJECT}`. A bare Web receipt or legacy String message is not this contract. Decoding rejects
wrong shapes, unknown required values, duplicate keys and forbidden producer fields without logging raw payloads.

Native fixed failures map to payload-free codes: NotInitialized, OperationUnavailable, InvalidDecisionInput and
InvalidProducerReference. Unknown failure codes or malformed envelopes become InvalidResponse. Actual Rust Option
fields may be absent or null; required identities still must exist. A Delete producer forbids decisionStage
presence, including null. A Remove null stage is absent; the canonical reference builder always omits absent
values.

## Exact identity and producer references

The stateless `LiveboardCoreAdapter` forwards submitted subject IDs and original decision JSON exactly. Decisions
validate the bounded two-field request UUID and Boolean shape before entering a native call, then correlate the
returned kind and UUID. Core still owns authorization, signatures, quorum and synchronization. Chosen opaque Claim
IDs pass directly to the existing Show/Approve/Decline recovery ports; there is no first-Claim lookup or local-Show
fallback.

Producer sources retain complete unsigned64-bit decimal tokens as strings. Validation checks their numeric
grammar/range, predecessor and lowercase source commitment. Serialization emits numeric literals without
Float/Double rounding, including values above2^53 and the full u64 maximum.

`LiveboardProducerReference.fromReceipt` captures immutable original kind, subject, known UUID and request sourceR
plus any actually attempted decision sourceU. Its wire JSON contains only version/kind, known UUID and source
references. Observations, phase, material readiness and subject fields are never serialized as caller authority. A
known UUID and original sources cannot change; an initially unknown UUID can become the actual Core-returned UUID.
Observation validates returned correlation without adopting altered source/subject identity.

Read-only reconcile makes exactly one observation call. It never submits, continues, retries or reveals anything.
Only an explicit Remove continuation intent can call the continuation port with the same captured reference. Delete
references cannot enter that port. Queued/Unknown is not canonical completion.

## Uncertain outcomes and remaining ownership

After an entered mutation, a thrown call, malformed response or mismatched receipt returns identity-free
`NativeResult.Uncertain`. A fixed native failure returns its safe Unavailable code. A malformed or thrown read-only
observation is Unavailable, with no invented write admission. No automatic retry occurs.

The adapter stores no session lock, keys, plaintext, producer registry or readiness owner. The keyed Session
implements the closed command admission lifetime below and is attached to the shared native root. The adapter
cannot establish screen readiness or OS-auth consent. The existing manager/cache/socket remain the native
synchronization owners.

## Checked async entry

`NativeLiveboardOperations` requires a frozen expected scope for each of the eight routes. It shares the
synchronous adapter's pure preparation, strict codecs and identity correlation; invalid decision/reference input is
rejected before native admission. The SAME operation port validates that origin at actual IO entry and drains
entered work before key effects. Decoding and returned typed outcomes occur after lease release. Entered A truth
stays stamped A after retirement, including Uncertain and producer R/U; unentered retired A returns NotEntered with
zero Core. The service publishes no UI/cache/notifications and never filters actual entered truth into
never-entered. A direct facade caller can still cancel and discard its result; the keyed Session supplies
independent closed-command lifetime below.

## Keyed Session command lifetime

`LiveboardSessionFactory` opens/reuses ONE keyed Session only from the exact current SUCCESS physical Member frame,
current owner/epoch and decoded identity. Generic cached Member data or an old frame is not readiness. Remove
selection uses that same Core-projected removable noncurrent device, without count rules.

An opaque intent owns one private Queued→Admitted/Cancelled CAS. Only the fixed nonobservable CAS runs inside the
SAME actual native lease immediately before Core; a skipped entry is NotEntered, never an uncertain admitted write.
The independent Session worker retains actual A typed truth after lease release and settles native/private work
BEFORE UI. Successful phaseUnknown with original R/U differs from thrown NativeResult.Uncertain with no invented
usable reference. Closing/cancelling a view cannot drop admitted truth or resubmit it for a new view. An original
queued caller can cancel only its exact unentered slot; an old runnable cannot clear a newer slot. A pending
duplicate rejects only itself; a retained intent returns the SAME typed result without another Core call.

Remove and the remaining closed typed writes share atomic selected/pending/completed registration. Reservation
precedes OS authentication; Actions.remove and raw Session.removeDevice use the SAME original identity/worker,
without a second prompt or cancellation authority. Raw infrastructure Remove keeps its prior unauthenticated API.
Complete/cancel registration cannot replay via a stale retained miss or empty-tuple ABA. Create claims only its
selected clearable draft revision at actual leased entry; no queued native/auth closure captures plaintext.
Completed records retain typed metadata only, dropping worker/native/input references.

Receiver decisions alone have one unentered preparation lifetime across an ordinary technical refresh.
The original journal registration retains exact complete request identity/accepted bit and a hard stamp, with one
original OS confirmation. Technical masking cancels the old admission/generation without reviving it. A fresh SAME
identity/permission selection and gate bind once to that SAME unfinished command only after actual current SUCCESS
proof; loading/spinner completion is not proof. A read already active at registration counts as overlap. If its
physical entry missed the registered demand, only the needed existing Socket-lane follow-up supplies coverage.
Read failure/cancellation/retirement closes the exact preparation, including its pending auth/wait; later success
cannot reuse that click. User Hide/Cancel, route/filter/background/error, identity/key loss and dispose remain hard
revocations before effects. No NotEntered retry, second confirmation, producer/removal waiting policy or Core rule
is added. An exclusive logical entry turn encloses both original currentness checks, original CAS and one native
call; it releases only after the existing facade/actual lease unwinds, before journal settlement. It is not a
native/key lease or held mutex across auth/read waiting. Original admitted truth and finished-CAS winner remain
independent of views. These are private source contracts pending focused/full/native validation, not an E2E PASS.

Entered R/U/Unknown survives view cancellation, error, route/background and owner retirement. Repeated issued
writes return the SAME outcome with zero resubmit. Closing/editing cannot unlock Unknown. Terminal release requires
exact original correlation and post-command physical proof: Create's actual secret ID+readiness, producer
UUID/ref+subject/current actor, or Recover's exact Claim/Pass/actor Done AND current ready primaryRecover.
For Recover's verified Done/waiting case, the supplied successful physical frame must be the current frame of the
same live Session/native owner/scope, coherently decoded to Member and resolving the original journal target.
The original same-scope typed Recover receipt binds exact Claim/Pass; requester is the current Session device,
not a receipt field. Exactly one current operation must match Done/accepted/verified/waiting, absent target and
false decision permissions. The current Secret equals the original frozen selection in full and remains
recover/ready with no active Claim or deletion. Only the prior selection/admission is released; retained journal
outcome/target stay unchanged, so the old intent returns the same truth without replay and cannot clear a newer
selection. Successful Show completes Core recovery; Hide only conceals locally. A subsequent Recover is a new
explicit action with fresh admission and all original guards, never an automatic Recover/Show/auth/read.
Counts/names/phase/absence do not unlock. Explicit reconcile performs a new read-only SAME immutable-reference
observation each time; explicit continuation is separately frozen and authenticated. Raw Accept/Decline/Delete
decisions retain only fixed safe attempted/returned metadata from actual bool/error envelopes, never raw
response/error text or inferred canonical approval.

Current undisposed Member/privacy-generation presentation is separate from private A truth. One required relay
directly handles every Notification.showError invocation (including equal text), real Socket BG/FG, captured key
effects and eligible failed/member/identity read publication. Private technical admission closes BEFORE observable
conceal/error/visibility callbacks. Raw Remove, typed selections and Show/Copy honor that fence at entry.
Scope/attempt are rechecked after inline privacy hooks; no native/physical/lifecycle lock spans callbacks.
FAILED/unsupported data is soft unavailability, not invented hard membership/key loss. These intermediate ordering
properties are source-reviewed; finite tests do not inspect every possible cache observer.

Sensitive state owns independent revealed values by secret ID, exact Hide/Copy, and clearable opaque pending
delivery capsules. Native+decode inserts transient plaintext after lease exit; held UI continuations carry only
opaque tokens. No reveal enters producer journal/Deferred. An ordinary physical refresh temporarily masks revealed
values and disables Copy, retaining only private, previously authorized per-card disclosure. Local restoration
requires the exact unchanged Secret selection. Recovered restoration requires original selected Accepted context
A, the exact successful native call's receipt R and current same-Claim Done context D, with A == R.context ==
D.context. Only the primary `showRecovered/exactClaim` to ready `recover/no-claim` transition may differ; every
remaining Secret field stays exact. The same private predicate governs observation, capture, restore and Copy.
The original Accepted frame permits display/Copy before a later read, including legacy first Show without proof.
After any later physical read, bounded attempt metadata seals the card to Done; unchanged or stale Accepted cannot
restore it. Restored cards retain that phase across subsequent reads. All existing SAME owner/epoch/scope/current
Member/frame/card/generation, foreground and idle/readiness fences remain required. The identical publicly delivered
disclosure is reused, with no second Show, native reveal, auth prompt or physical read. Pending native/UI returns
are never restorable cards. The null-frame mask permits only a later genuine successful read to restore readiness.

Native recovered Show retains `success/message.secret` plus optional `message.revealReceipt`; no String ABI changes.
The same raw `LiveboardJson` duplicate guard validates state and reply before object normalization. Recover operation
`revealContext` is correlated to the same Member/current device, Secret and Accepted/Done operation. Present proof
must follow Core's strict nested v1 grammar: absent differs from null, unknown/duplicate fields reject, source kinds
are exact, commitments are lowercase hex64, and decimal sequences are contiguous safe53-bit integers. One or two
Recovery sources are strictly unique/sorted by unsigned UTF-8 identities and numeric sequence. Identities allow
nonempty strict UTF-8 up to1024 bytes, compact context up to8192 and receipt up to12288 bytes, with no UUID/ASCII
restriction or invalid-surrogate replacement. Limits apply to metadata only, never the plaintext envelope. DTO/error
representations are redacted. Missing proof preserves legacy first Show but grants no continuity; malformed present
recovered receipt rejects publication. Local envelope extraction keeps its legacy unknown-field behavior.
See [R32 bounded implementation evidence](../.ai/artifacts/run/MS-20261019210543-004b-recovered-show-r32-implementation.md).
Current host checks do not imply native packages, real OS/auth/render/clipboard acceptance or real Core UI E2E.

Errors/auth Cancel discard disclosure and queued payload while preserving authored retry draft. Background, key or
actual Member/identity loss, disposal and idle close clear the draft too. Explicit Hide, route/filter changes and
changed selected Secret/Claim/source proof or read failure revoke the applicable disclosure; later matching Done
cannot revive those values.
Queued identities are revoked before public wipe and settled afterward without cancelling new callback generations.
Same-key dispose leaves a tombstone: it cannot replace the private journal and resubmit Unknown. Losing,
invalidated and stale factory candidates clean up only their own attachment/observers, never B.

Local/recovered deliberate Show and outgoing/continuation/both-decision actions use the existing OS port and
revalidate exact frozen item/kind/actor/Claim/generation/Core projection after its callback and at entry. Requester
Recover/reconcile gain no invented prompt. Callback only answers; UI denial/Hide occurs on the existing UI
dispatcher. Copy after current authorized Show adds no prompt and does not change system clipboard policy. These
injected OS-port tests do not prove a real biometric/credential prompt or Cancel.

Create success clears input and issues one payload-free completion token. The root and modal retain that SAME token
across ordinary successful refresh; the generated 520ms receipt timeline is keyed to the original token, so refresh
does not restart its deadline. A stale completion cannot close a later form or replay on a new attachment. Controlled
service tests cover token/overlay lifetime; actual 519–520ms Compose graphics and remount timing remain unrun.
Web parent draft/error-close parity has separately passed its bounded component review; native pixels and actual
platform behavior still require their own validation.

## Shared physical read and cache publication

Koin injects the SAME `NativeCanonicalReadOwner` and existing Core/cache instances into all seven previous physical
read sites: the manager's `getStateModel`, `getVaultFullInfoModel`, noncached `getVaultSummary`, `getUserDataBy`
and noncached `getSecretsFromVault`; Socket's `searchRequest`; and registration `LocalState.new`. There is no
per-call owner, second Core runtime, polling loop or business queue. The physical mutex covers the actual off-Main
`getAppState` and parsing only. Cache/error/observer publication happens after releasing that mutex.

`NativeInitializationOwner` owns one admission tracker. Its opaque local token exists even while uninitialized;
successful native initialization supplies an epoch. Tokens contain no key or permission. Read and captured Socket
auth calls acquire leases only at actual IO/native entry. Dispatch-queued cancellation owns no lease; entered
blocking work drains before key cleanup or reinitialization. Read leases release after the physical fence and
before publication/callbacks. A fresh read requirement is unavailable before initialization and otherwise retains
its exact owner/epoch and opaque identity. Pending requirements are captured inside the physical fence immediately
before Core entry. A successful actual read certifies its captured requirements; a FAILED frame certifies none and
retains them. UI admission checks the current scope and monotonic physical attempt before cache/storage effects, so
delayed older SUCCESS/FAILED frames or raw thrown Socket reads cannot overwrite/revoke a newer accepted snapshot or
show its stale error. Every actual read consumer checks its captured scope/attempt; consuming an actual result is
distinct from certifying canonical proof. Owner and cache share Main.immediate; the controlled test dispatcher
gates admission with an immediate real cache. Arbitrarily dispatched or suspending cache implementations are
outside this contract.

After an accepted publication, exact canonical frame identity and current scope are checked before fulfilling its
captured SUCCESS requirements and removing them from pending. Scope is rechecked for each target after inline
completion callbacks. Retired targets complete with a payload-free RETIRED frame outside native/lifecycle/physical
locks. Each requirement owns one stable read-only Deferred backed by a private parentless completion. Cancelling a
view's awaiter leaves it alive; later waiters receive the exact committed frame even if StateFlow conflates it with
a following ordinary frame. Cached state, generic writes, spinner completion and proof revocation cannot fulfill it
or automatically retry an operation.

The SAME cache stores one immutable publication pair of parsed state and read frame. Identity equality adopts the
latest instance even for equal payloads. Read-only StateFlow views obtain `value` and `replayCache` from the
current pair; legacy state collectors still suppress equal data, while frames distinguish physical attempts.
Generic updates revoke proof before updating state/storage; Clear explicitly removes state and proof. There is no
transient null pulse, second snapshot store or extra scope. The private projection explicitly opts into the current
StateFlow inheritance API; compilation validates that use.

## Bounded key and Profile lifecycle

Koin supplies the SAME initialization/read owners to `NativeKeyLifecycle`, Manager, Socket and `NativeLocalReset`.
KeyLifecycle replaces the Manager's sole private initialized-key comparison; it adds no duplicate key holder. An
atomic expected-owner reservation closes admission before key IO, drains entered native work outside locks, then
performs the existing key/init policy. Same saved key preserves the owner and epoch. Missing/changed key or failed
initialization fails closed; the previous initialized flag cannot survive a failed replacement. Two uninitialized
generations still have distinct tokens. Parentless transition barriers settle in NonCancellable finally; cancelling
another reservation's waiter does not cancel its completion. No native/physical/lifecycle mutex spans key IO, drain
waits, cache publication or observer/Deferred callbacks.

Profile captures ownership before queued Reset work. Its clear transition waits for entered read/auth FFI before
keys; stale cleanup cannot clear, publish or navigate a successor. The receipt's owner is checked again before
presentation cleanup/navigation, and false clear preserves the existing no-navigation policy. Actual cache
observers can Reset before the read caller returns without self-drain.

KeyLifecycle owns terminal retirement inside each transition's existing NonCancellable finally, after finish
releases the reservation/barriers. It retires the exact prior A scope even if caller cancellation prevents receipt
consumption or invalidation makes the returned stamp null. A SAME-key finish stamp preserves A's epoch and pending
requirements; a cancelled foreign waiter that never owned the reservation cannot retire A. The captured finish
stamp is authoritative for this cleanup, not public currentScope after reentrant callbacks. Manager and Reset no
longer own optional receipt-dependent retirement.

Scoped retirement removes only A's pending targets and conditionally revokes an A-owned canonical frame on the same
UI lane before completing old targets. A fresh B frame published before or inline during retirement survives;
Manager has no later unconditional revoke. All retirement callbacks run outside native, physical and lifecycle
locks, including the null-receipt/cancelled-caller paths. Socket auth providers capture their originating scope and
reject retirement without a successor token or HTTP request. The actual Join client rethrows CancellationException
before its generic authorization-error catch, preserving cancellation instead of emitting a terminal error/stale
notification. Existing HTTP authorization failures, SSE and reconnect behavior retain their contracts.

These are bounded client lifecycle checks, not a complete Liveboard session or security acceptance. Pending-A
retirement after caller cancellation, concurrent invalidation/null stamp and cancelled foreign-reservation waiter
now has its own actual regression coverage; this does not complete other lifecycle paths. Resolver's two mutations,
exclusive Registration generate/save/init and Main removed-device cleanup now use the bounded client ownership
paths below. Registration's reset remains observation-only. Core alone owns permissions and domain decisions; a
read frame grants no approval.

## Initialized operation admission

The stateless `NativeOperationPort` uses the SAME initialization owner and IO/UI environment through required
grouped Manager/Socket runtimes. Fourteen existing Manager native branches and Socket's SHOW_SECRET claim lookup
now enter through that owner. The lease is acquired at actual IO/native entry and released before parsing, UI
effects or callbacks. Cancellation while IO remains queued owns no lease or native call. CancellationException
remains cancellation.

Manager captures its scope before its first suspension and retains it across awaits. The port checks before/after
result consumption and again after delayed caller restoration; retired values and errors cannot escape to a
successor. Socket uses the originating read's scope together with its existing actual-read attempt guard. It
rechecks scope, attempt and processing-secret identity between observable NONE/recovery/processing effects,
including inline observer callbacks. These checks do not certify arbitrary multi-effect callbacks or create
canonical proof, authorization, a second runtime, executor or automatic retry.

All seven normal Manager/Socket/LocalState read paths now freeze nullable origin before their first observable,
scheduling or suspension. ReadOwner retains it through the physical mutex and revalidates it at actual IO/native
entry; null stays null. A queued retired key owner A cannot read successor B or cover B's requirements. Manager's
capture precedes beginSynchronization observers; LocalState passes its original scope and Socket retains its
scheduled origin. Delete reconciliation retains the outer captured scope and exact inherited read turn through
both original synchronization counters; its nested read never recaptures a successor. Work first invoked after successor B still needs the
original Session intent; capturing B at a legacy Manager entry is insufficient. Future manual/SSE/keyed generation
requests must carry that intent through actual entry. The checked async facade now requires an explicit expected
scope after input validation; the integrated root/Session callers retain that original intent rather than
recapturing B. UI wiring grants no local authority.

The SAME read owner now coordinates technical read starts before all three original beginSynchronization
publishers (Socket, Manager getStateModel and outer deletion); standalone LocalState reads also acquire a turn.
Read groups may overlap publication and retain the original physical mutex. A runnable original pending signal
reserves its FIFO position before the lane publishes/coalesces it; same-scope/generation replacement keeps that
position. A queued receiver entry freezes the current read group; after that entry the earlier reserved read runs
before later entries. Exact nested work inherits its parent token, not merely its scope. Reservation is not a read
start: only current owned start marks overlap and enters the original synchronization begin. Abandoned/lazy-cancelled
reservations own no counter, proof or failure. Finish/cancellation releases the exact turn after cache settlement.
Hidden/paused pending work parks without a barrier; background clears it, and the existing resume/foreground request
or pending handoff reserves it when runnable. Initial explicit refresh still requires only !hidden && !paused,
not foreground=true. Hard close removes exact live watches/turns synchronously before privacy/deferred callbacks;
new entry reservation verifies that same watch in its enqueue/grant CAS. Deferred close is only notification.
This does not strengthen the original boundary for an already handed-off/committed native entry.
Manager's state-query cluster and Socket's state/claim consumers are mechanical internal delegates over the SAME
ports/flows/flags/lane/dispatchers; constructors perform no reads/subscriptions or extra launches.

The SAME Socket delegates scheduling to NativeRefreshLane: one metadata-only visibility generation, active flight
and coalesced pending origin. Background invalidates before cancel/disconnect; cancelled A remains owned until
actual Job/native completion. Its actual outcome finishes synchronization before release and successor start. A
parentless begin/settled handshake covers cancellation before the lazy body; callbacks hold no
physical/native/lifecycle locks. Exact flight/generation/attempt checks prevent stale finally/legacy consumption
and reentrant disconnect effects from overwriting B. Hidden events enter no read; normal foreground queues fresh
work after drain without waiting for SSE. Initial explicit refresh remains supported. Key owner/epoch stays stable,
with no init or signUp on visibility changes. These are bounded controls, not arbitrary callback or
cancellation-schedule proof. The integrated root and shared mobile surfaces use the Session/privacy/manual04
controls above. Actual OS consent, platform builds, graphics/fidelity and real Core UI E2E remain pending.

## Registration and removed-device ownership

Registration captures its original owner before key effects. The SAME key lifecycle owns an exclusive reservation,
drains actual native admissions and performs ordered generate/save/init, submission or incomplete cleanup. Its
private snapshot route uses that drained permit, without a second unconstrained reader, nested normal Manager
init/read, shared cache publication or observer callbacks. Finish releases admission before owner-stamped receipts
and presentation effects; Session rechecks receipts across awaits and inline callbacks. Resolver's credential
generation and signUp also use the SAME actual-entry operation port; prepared wrappers retain their originating
scope rather than adopting a successor.

Member is never cleared. Incomplete cleanup still requires exact vault/device, Pending/Declined status and verified
absence of all saved credentials after true clear. Back/reset only observes the current identity. Submission
persists the pending vault email before signUp and reconciles an ambiguous reply once without resubmission. Main's
actual queued UI navigation checks its removal receipt inside the UI task. A valid Main removal still navigates
after false clear; Profile's false-clear no-navigation policy remains distinct.

Startup handoff uses one private ephemeral exact originA/currentB+epoch receipt, created only by actual successful
saved-key initialization from an uninitialized origin. Reset/replaced/failed/missing init invalidates it; SAME-key
init preserves it. Only an eligible unbound Session resume may claim it under the same reserved gate before any key
IO. Eligibility is spent after accepting a current own prepare/submit/cleanup receipt, including Failed, or a
resume snapshot/owned failure. Pure observation and empty no-key resume do not bind. A bound failed attempt cannot
adopt a later external init, even with the same saved key; a fresh current Session remains valid. Disabled foreign
handoff returns no successor receipt.

No flag changes before await or in finally. A cancelled foreign waiter before owned CAS spends nothing; after owned
CAS the existing fail-closed one-shot is spent even if Session never receives the result. An unchanged eligibility
flag does not restore that receipt. These checks do not prove every arbitrary registration cancellation schedule or
complete platform cleanup. In particular iOS blocking DB cleanup still needs its offMain/platform gate with
SwiftBridge unchanged. The shared native root and bound UI intents are integrated; their actual platform cleanup,
OS/privacy graphics and real Core UI behavior remain separate gates.

## Verification scope

These are bounded results for run `MS-20261019210543`, not release or real Core UI E2E acceptance:

- **Native common-service tests: PASS.** After the reviewed mechanical update (96 Kotlin files and two
  configuration files), one fresh full run executed 260 distinct methods in 64 suites in 57.068s, with zero
  failures/errors/skips. No duplicate selected run occurred. Main/common/Android debug Kotlin compiled for this unit
  run; this is not an APK, native SDK, iOS app, rendered UI or real Core UI E2E result. See the
  [independent actual review](../../.ai/artifacts/run/MS-20261019210543-native-static-unit-coverage-root-review.md)
  and [configured validation report](../.ai/artifacts/run/MS-20261019210543-005b-native-static-unit-coverage-green.md).
  The preceding input's selected 56/33 and full 260/64
  [ordinary-refresh review](../.ai/artifacts/run/MS-20261019210543-005c-native-ordinary-refresh-attempt-2-actual-review.md)
  remains historical evidence for exact disclosure/Copy restoration, revocation/current-owner controls, SAME Create
  completion/overlay lifetime and null-frame recovery. Controlled Core/key/OS ports do not establish real OS consent.
- **Configured native coverage: PASS, 29.22%.** Current LINE coverage is 4744/16236, generated code included. All 101
  unit output records retain exact bytes and mtimes across coverage; additional unit tests/tasks executed=0. The
  overall 80% goal remains advisory. Earlier 29.72% coverage belongs to the preceding source/line-map state.
- **Required static check: PASS.** Actual Detekt reported 1000 findings at unchanged `maxIssues=1000`. Five additional
  line-based LongMethod identities explain the 995→1000 projection difference; no budget remains. Existing size and
  structural debt stays visible. The earlier 4626/1000 failure remains immutable historical evidence; neither the
  threshold nor the coverage goal was raised to obtain this result.
- **Web parent parity: bounded PASS.** The separate
  [Web final review](../../meta-secret-core/.ai/artifacts/run/MS-20261019210543-005c-web-create-parent-final-review.md)
  accepted selected 58 methods/6 suites and full 665 executions/58 suites (656 distinct labels), plus its configured
  type/lint/build/coverage checks. This establishes controlled draft/modal behavior, not native or real Core UI parity.
- **Native passive E2E adapters: source-reviewed, UNRUN.** The
  [independent four-source review](../../.ai/artifacts/run/MS-20261019210543-e2e-native-passive-cache-independent-review.md)
  accepts same-current cached canonical observations and 12 authored controlled-input tests. It proves no Android
  instrumented-test compilation/execution, real device behavior or overall E2E success.

**iOS SecretCard primary tap contract (test orchestration).** For nonempty `lb-primary-*` targets only, the
existing helper retains exact uniqueness, keyboard dismissal and observed visibility/enabled readiness
(default 30 seconds), then calls the same standard `XCUIElement.tap()` once. Native hit-point resolution and
any native failure belong to XCTest; there is no coordinate fallback, retry or inferred success. The skipped
prewait `isHittable` observation remains JSON `null`; every non-primary and standalone hittable/privacy guard
is unchanged. R6 remains a failed pre-tap baseline, not proof of an application accessibility defect or of this
adapter's success. Current owning Swift compilation and the original strict value/auth/privacy scenario are
separate pending validation gates; native tap duration is not claimed to fit inside the 30-second prewait.

**Proposed R7 same-sheet observation (source candidate, UNRUN).** The reviewed candidate may observe only the
first primary tap transition per test invocation, using selector state reset in each existing actor setup. It
consumes that opportunity before one `isHittable` read; only a false result collects the six exact known roles
and one bounded direct sheet snapshot. The observation never gates or changes the following native tap.
Unknown/leaf/truncated/oversized metadata is explicitly inconclusive; no identifiers, names, values or native
hit-winner claim are persisted. This candidate is not current native acceptance or an application AX fix.

Pending gates include actual APK/native SDK/iOS app builds, real system biometric/credential Cancel, measured
normal/reduced motion and prototype fidelity, native snapshot/privacy/clipboard interaction, actual 519–520ms Saved
rendering, platform cleanup, and the preserved real Core UI E2E 1–50 scenarios. Android emulator autoSuccess and iOS
auth bypasses cannot certify actual OS Cancel. Existing historical stage reports, including earlier failures and
superseded inventories, remain immutable evidence; they are not the current passing inventory. No Core permission,
quorum, cryptography or domain synchronization rule is changed by this client renderer or documentation update.

**Proposed R9 incoming sender observation (source candidate, UNRUN).** The actual incoming reader retains its unique visible head, typed request, lazy sender visibility/label, total count and exact expected-request checks. A first qualifying sender-guard failure per existing test selector reset can emit cached guard metadata to safe stdout before one public live app snapshot, then one sequential six-role/linked-ancestor projection and attachment. Static uppercase keys/enums with numeric flags or UNKNOWN use cached stdout before acquisition, then at most six role/64 linked-node/fixed summary records, each at most 512 bytes including prefix/newline, under the existing scenario redactor; raw identifiers, labels, values and names are never persisted. Projection limits are 512 nodes, depth16, 64 retained nodes and 4096 bytes; missing/truncated/oversized results are inconclusive. The first observed failure is not terminal state, canonical sender data, hit ownership or native acceptance. Snapshot acquisition itself is not bounded by the projection, stdout is best effort, and no action, admission condition, business marker or deadline changes.

**Proposed R10 incoming scroll reveal (source candidate, UNRUN).** Incoming observation first resolves one strict identifier-bound Home/Inbox-owning native scroll view and requires the entire Inbox inside the finite app/scope/Home clip. Only measured vertical overflow permits a standard gesture on that scope, at most four per existing observe invocation; another gesture requires strictly improved geometry under the same original deadline. No app-wide swipe, coordinates, added wait or name fallback. The original incoming reader and real cached diagnostic run before a unique/nonempty/full sender geometry postcondition and the original expected-request/capture checks. Missing scope, no progress, oversize or unavailable geometry fails closed. R10 remains an incomplete business scenario baseline; revealing the viewport is not proof of canonical sender data or an AX fix. Current owning Swift compilation and strict original scenario remain separate validation gates.

**Proposed R11 request identity alignment (source candidate, UNRUN).** Recover uses the exact opaque Core ClaimId, which is not a UUID or a fixed fourteen-character format. The reader and recovery FIFO context validate a nonempty unchanged suffix bounded to 512 UTF8 bytes without control characters; exact kind/request/cardinality/requester/subject/count/eligible-peer/FIFO/decision/terminal checks remain intact. Join/DeleteSecret/RemoveDevice retain their existing UUID validation. UUID_VALID records the cached actual UUID parse result (0 for a valid non-UUID Recover), not canonical acceptance. This changes test adapter validation only; no Core/crypto/protocol rule, identifier normalization, geometry/deadline/action or business scenario changes. Native R11 remains the failed baseline; owning Swift compilation and strict recovery approval/value/privacy/terminal validation remain pending. The R12 terminal observation candidate preserves the same lazy iOS error/head/badge reads and 180s Android completion barrier. Each poll resets nullable cached fields; only the original consulted iOS conjuncts and descriptive metadata from the one returned Android view/completed result are recorded at the original failure boundary. Null or unconsulted fields are UNKNOWN; Android inner selected-secret/receiver/admission outcomes stay UNOBSERVED, and diagnostic failure cannot replace the identical primary Throwable. One fixed uppercase numeric/enum stdout record is bounded to 512 bytes including framing/newline, with an optional existing iOS attachment bounded to 4096 bytes; no extra queries, snapshot, Core/cache reads, actions, identifiers or policy changes. Native R12 remains the failed baseline; these source-only observations neither diagnose canonical processing nor establish native acceptance. R13 decision observation is diagnostic-only and default OFF: iOS enables it only from the already-admitted simulator test identity, Debug binary, local environment and MODE=true. The Inbox callback caches its original rendered-owner Boolean once; the unchanged Root command registers and returns the same Job before any sink work. Root-local opaque tickets and atomic immutable metadata reserve at most 32 deliveries and 64 fixed records (512 bytes including framing each); foreign/reused tickets cannot extend that budget. Existing owned IO delivers only fixed stages/flags/typed outcome categories, with CURRENT=UNOBSERVED and no identifiers, values, raw errors or extra guard/Core reads. Completion may precede handler registration; absent/unreturned/cancelled/dropped/reordered records are inconclusive, and close adds no flush or await. Version3 retains the eleven fixed fields and adds only closed POST_CAS first-consulted-false REJECTION_SOURCE refinements to the same actual second currentness check after admission CAS. Sensitive/member/inbox/Decision categories describe sequential consulted results, not an atomic state or background-refresh cause; unconsulted or unavailable metadata remains coarse/UNOBSERVED. The same original lazy guards/getter order/count and rollback stay authoritative. Decision any() records identity absence or the actually consulted permission failure only after its final false, never an interim mismatch before a later true. One private provisional enum is promoted only at that same failed post-CAS call; the existing winning journal finish alone publishes, so losing/cancelled/Entered paths retain their rules. Null/defaultOFF creates no diagnostic context or scheduling and pure metadata has no UI, IDs, values, callbacks or sink references. The real hidden-selection component RED/GREEN does not reproduce the post-CAS race: integration remains SOURCE-only until a current owning native run. This changes no admission/auth/result lifetime, diagnoses no R15 cause and certifies no native/business acceptance.


**Test16 cycle9 incoming-observe diagnostic (source proposal, UNRUN).** The existing incoming180-second wait keeps its original lazy reveal/reader/sender-containment/exact-match guards, query and callback order, gesture state, result and assertion. Each real poll starts a fresh payload-free scalar sample; only actually consulted original counts, booleans and finite width/height/vertical-deficit values are cached. Unreached values remain UNKNOWN, including after an earlier richer poll. The unchanged unsuccessful-wait callback alone may emit one fixed OBSERVE_WAIT_FAILURE event as a ≤512-byte framed stdout summary and a ≤4096-byte fixed-name JSON attachment. No additional AX query/snapshot/clock/action, coordinate, name, identifier, label, payload, raw error or credential is recorded. Static inconclusive serialization/size fallbacks cannot change the original outcome. Existing first sender projections retain their independent behavior. This metadata does not repair geometry, identify a Core defect or establish native/business PASS; normal owning iOS compilation/provenance and original whole Test16 all nine cycles remain separate gates.

**T30 incoming-observe progress (UITest source candidate, validation pending).** Each completed original incoming predicate may additionally emit a fixed OBSERVE_WAIT_PROGRESS record after its original lazy effects, before that predicate returns. Invocation-local state admits the first vector and differences from the last admitted vector, including a returning earlier vector, with at most eight attempted records; the cap is checked before cached projection and a slot is reserved before the existing bounded encoder/printer or its fallback. Only the same fixed 23 closed cached fields form both payload and dedup key: the original 16 summary flags/categories plus GC/PP/NP/DL/DX/DP/UP. Missing or unconsulted fields remain UNKNOWN from the fresh current poll; dimensions, deficit floats, timestamps, names, IDs, labels and private data are excluded. The existing late OBSERVE_WAIT_FAILURE 16-field/full-attachment behavior remains separate and unchanged. No extra AX read, clock, gesture, action, attachment, wait, approval or result is introduced. A blocked first predicate, parent preemption, exhausted cap or unavailable/fallback output can still omit useful records; absence is inconclusive. These sequential observations do not diagnose the actual Test24 guard, fix Join or grant canonical/current/native/business acceptance. Actual host effect/privacy/cap coverage, owning changed-UITest compilation/provenance and the original whole Test24 39 cycles remain required; the failed first attempt and earlier UNRUN/history are retained.


**Test22 iOS Show phase contract (harness source proposal, UNRUN).** Only the Test22 sender carries private case22/role/cycle/step/random-nonce context. The optional Show callback reports INPUT_RETURNED after the existing physical tap, system confirmation and tap-complete trace, before the existing value lookup; the actual existing reveal early-return may instead report ALREADY_VISIBLE. Both are preparation observations, never approval or business success. Default callers retain the original query/action/callback order with no extra AX, clock, authentication or selection read. Full-list global uniqueness and exact identity/window checks remain mandatory. The original exact plaintext or ordered seed plus Copy assertion precedes the original visible marker; Hide/Close and both real actor results remain required. Fresh current-child stdout with exact context alone starts the response phase; cached diagnostics, foreign/stale/duplicate/out-of-order events cannot do so. This intentionally changes the old approval-started45s allowance to bounded180s preparation followed by the original45s value and60s Close allowances, each limited by the remaining original ordinary600000ms actor deadline sampled at its first arm. No timer is renewed and fail-fast/cancel/drain rules remain. This is a test-harness timing correction, not a product/domain change or native acceptance; owning native qualification and the original whole Test22 all39 cycles remain pending.

### Show-only debug observations (source protocol; native verification pending)

`LIVEBOARD_SHOW_OBSERVATION` is separate from Inbox decision V3. It is default OFF;
only the already-admitted simulator test identity plus Debug/local/MODE=true enables it.
The actual Show render callback caches its one original rendered-owner result. The same
Root session lookup and original Job keep all auth, admission, native, result and privacy authority.
Root-local metadata caps at32 attempts/64 immutable records; each encoded record including
`E2E_DIAG ` framing/newline is at most512 UTF8 bytes. The twelve fixed fields are VERSION=1,
KIND=SHOW, ATTEMPT, STAGE, RENDERED, SESSION, PHASE, ENTRY, RAW, RETURNED, COMPLETION,
and REJECTION_SOURCE. No identifiers, names, values, raw results/errors or hashes are recorded.
The registration snapshot is provisional. Only the same original Job completion freezes
its rejection trace once, outside native lease/CAS and after the original finally, using
cached checked-call-started metadata; then it reserves the final immutable record.
Missing, held, dropped or unconsulted observations remain UNKNOWN/UNOBSERVED. Checked
closure start is not physical FFI/Core processing; returned value/disclosure is not a
claim of later visibility or whole-scenario PASS. Logging/encoding stays best-effort on
existing Root-owned IO with no timer, collector, retry, close flush or extra business lifetime.
Host component controls cover real Root/Show ports, not genuine render/touch/OS/Core-server
execution. Actual consumption requires a separate reviewed current-app/PID/UTC-window-bound
ShowV1 reader; the old InboxV3 reader and historical bindings cannot be adopted. After any
app-source diagnostic change, real regression resumes the original range1–50 from Test1;
all75 scenario JSONs, roles, eighteen recovery cycles, FIFO15 and Test50 remain unchanged.


### Collected post-delivery state and cached value-branch commits (native validation pending)

The opt-in `LIVEBOARD_SHOW_CONTINUITY` version1 diagnostic uses the SAME existing Show ticket and eleven fixed fields: VERSION, KIND, ATTEMPT, POINT, RESULT, CAUSE, A, R, MATCH, BOUND, COVERAGE. Iteration1 records SELECT Accepted availability, actual BIND A/R/match/CAS operands/result, and positive winning CAPTURE/STAGE/RESTORE inclusion only; skipped operands remain UNOBSERVED, false bind CAS is not unbound, and UI commit is no restoration gate. Its twelve supported typed shapes are checked before reservation/wake and encoding; contradictory/unsupported rows, including RETIRE and negative inclusion rows, are rejected. First TRUE/FALSE per implemented point plus separate CONTROL/CAPPED coverage stays within13 records/attempt,416/Root and512 framed UTF8 bytes per record; old Show/Post480 reservations remain separate. The cap is coverage metadata, never a fabricated guard failure. FIRST_FIXED_GUARD_OBSERVATIONS_ONLY is incomplete best-effort coverage, not first cause, permission, current pixels, runtime cure or real Test5 acceptance. The existing ShowView scope drains fixed metadata and closes with its owner; opt-in has nonzero allocation/scheduling/logging cost, and OFF is not a zero-allocation promise or authority for extra IO/actions. Detailed FALSE capture/STAGE/proof/full-Secret/Done/restore and RETIRE producers are deferred unless actual diagnostic evidence demonstrates need under a later bounded plan. All business/privacy/read/receipt guards and negative tests remain unchanged.

The separate `LIVEBOARD_SHOW_POST_DELIVERY` variant uses the same typed platform port and cached
admitted Debug/local/simulator-identity/MODE gate. Original ShowV1 records/fields remain unchanged.
Its13 closed fields are VERSION=1, KIND=SHOW_POST_DELIVERY, ATTEMPT, EVENT, ORDER, STORE, CYCLES,
LAST_PRESENT, LAST_EMPTY, ASSOCIATION_LOSS, COVERAGE=COLLECTED_SNAPSHOTS_ONLY, REASON,
and COMMIT (VALUE_BRANCH_COMMITTED or UNOBSERVED). ORDER is a collected-store semantic-edge ordinal;
a cached commit borrows that ordinal and cannot change store/order/cycle facts. File timestamp/order
is asynchronous transport, not an atomic draw or AX observation. No IDs, names, values, claims,
raw results/errors, hashes, session/scope objects or callbacks enter the record/ticket.

Disclosure's original constructor, generated copy/components/equality/hash and redacted toString
remain original. Its nullable private-set body ticket is initialized only on a fresh unpublished
object; generated copy intentionally drops correlation. The R30 source candidate explicitly extends the
earlier byte-whole SensitiveSnapshot boundary with one nullable private-set closed-enum body slot.
Its original five primary fields, constructor, generated copy/components/equality/hash and redacted
toString remain unchanged. Only fresh-construction factories initialize the slot on the same copy or
replacement already made by the existing writer; a normal generated copy drops it to UNOBSERVED.
There is no arbitrary-instance annotator, second snapshot/wrapper or old-value witness. Metadata
cannot change original StateFlow equal-value/no-op suppression; only original delivered snapshots
are projected, without feedback or an additional collector. A nonempty map missing a ticket is
association-UNOBSERVED, not proof that its value disappeared. Existing suspended-card restoration
carries the very same Disclosure/ticket, without a second Show/auth/native call or plaintext copy.
An optional SideEffect reports only one cached SecretValue branch commit; it does not establish
store-current, pixels, AX visibility or restore an empty store. Lost/conflated/unconsulted samples
and the interval at a later external AX predicate remain UNKNOWN.

The metadata owner retains at most32 current tickets and clears that registry at existing Root close.
Each has independent first-sample/empty/reappearance/commit/CAPPED reservations. Repeated same-store
samples and duplicate commits emit nothing; observed PRESENT→EMPTY→PRESENT cycles summarize at
1,4,16,64,256,1024,4096,16384. Up to13 new records/ticket (416/Root lifetime), each≤512 framed UTF8
bytes, are separate from oldV1's64. At65535 observed cycles, or exhausted finite ordinal, CAPPED is
emitted once and later store facts are UNOBSERVED; a still-unused commit reservation survives.
Summaries can precede later coalesced edges, so silence never proves persistence or a final state.
REASON names only the constructor writer of the previously published snapshot object admitted by
Root's existing sequential identity guard: HIDE_CARD, READ_MASK, READ_RESTORE or CONCEAL; missing
metadata is UNOBSERVED. Delivery and ordinary copies remain UNOBSERVED. COMMIT/CAPPED rows always
retain UNOBSERVED, without cached writer attribution. The encoder rejects typed COMMIT/CAPPED records
with another writer by returning null before JSON construction; it never normalizes them into accepted
output. No reason-only row is emitted. Root's guard
precedes another existing presentation update, so reentry can change current state before transport;
the label is not atomic current-at-emission, latest state or the first loss of a particular ticket.
Hide followed by another non-equal empty writer may conflate; the collected object's label cannot
identify the earlier loss. An equal second empty candidate remains suppressed and cannot relabel
the published object. Losing candidates have no diagnostic effects; no payload history bridges gaps.

OFF adds nullable body slots and a singleton-enum assignment on existing fresh writer candidates,
not zero instrumentation work; it adds no ticket/emitter/wrapper/job or authority read. The UI-only
emitter captures the existing views scope, platform and pure metadata owner; each queued IO job
captures platform+immutable record only. No new scope, watcher, timer, await or close flush exists.
The existing business/guard/getter/auth/native/CAS/error/cancellation/teardown order remains the oracle.
Current source/security review, selected/full units, Detekt≤1000, Kover and owning native compilation
are separate pending gates. Component controls do not prove actual Compose/AX. A separate reviewed
new-schema/current-app/PID/UTC-window-bound Root consumer is required; old ShowV1 readers cannot be
adopted. Actual regression after app changes remains the original1→50 from Test1, with all75 JSONs,
roles, eighteen cycles, FIFO15, Test50 and manual genuine-OS classification unchanged.

R30's first three real-Root host component tests completed their expected REASON-only RED and then
GREEN3. On the preceding source, the selected33 host regression completed32PASS and one intended
codec RED: all eight invalid COMMIT/CAPPED writer pairs were accepted instead of rejected (expected8,
actual0). Its passing components covered hard-conceal/copy-loss/no-op, conflation/reentry, ON/OFF,
the typed numeric envelope and metadata budget. After the encoder rejection guard, current33GREEN
is pending. These component results do not establish full-unit/native/Compose/AX/Core UI E2E acceptance
or identify the writer behind the actual R29 iOS failure.


A rendered, nondeleting Secret card keeps its terminal local Hide action enabled independently of new Show/Recover readiness; Copy and value-absent actions retain their existing guards.


## Manual Recover Approve after a proven pre-dispatch failure

The existing native `accept_recover` String result may add `decisionAttempt` to a false envelope: schemaVersion1, kind recoverApprove, exact opaque claimId, actorId, vaultName and phase notSubmitted. Core grants this only for this invocation's first pre-sync failure plus its locked local audit. It is not server acknowledgement, historical absence, permission or a reusable retry token. Legacy strict clients reject the extra field and remain blocked; explicit new decoder and current native packages must be qualified together.

The client strictly validates the optional receipt against the same session, native scope, actor, Vault and original Recover Approve intent. Unknown, duplicate, foreign or malformed metadata grants nothing. Successful receipt decoding still renders Unknown and the ordinary sanitized error, never Submitted or a successful approval. The dedicated observer cannot fall through generic successful-decision completion.

A new manual selection requires the exact settled original outcome, its post-return required successful physical read, and an additional successful read demanded by the user's dismissal of the actual published error. Publication binds the exact already-published intent/outcome set to that visible error event; an unpublished proof or unrelated error cannot borrow acknowledgement. The set is captured before clearing display, with same-owner/generation/no-replacement checks after synchronous callbacks. A later recognized error wave may include prior still-held published outcomes. Hard privacy, Hide, background, Reset or scope replacement invalidates prior acknowledgement.

Only the same full canonical operation with current canApprove releases its presentation reservation. The old intent always returns the exact immutable original result; the journal keeps its original settlement signal. A NEW user click performs fresh platform authentication, post-authentication checks and Core's final locked permission/source recheck. Dismissal and canonical observation never invoke approval automatically. Generic or post-dispatch uncertain outcomes remain blocked.

An exact verified accepted/declined terminal result with a real responder and both decision permissions false may retire presentation with zero new approval. Receiver readiness may still be waiting, and peer approval may leave clientStatus needApprove; those fields do not replace verified outcome authority. Observed own Decline with null outcome/responder and unknown resolution remains an unverified negative. No broader legacy synchronization policy is changed.

This source contract does not certify OS dialogs, new native packages or real UI E2E. Those remain separately required against current artifacts.


### Caller cancellation and private command completion

The journal registers the result observer and public cancellable-continuation cancellation handler before starting its same lazy worker. A cancellation signal revokes an unentered admission synchronously; it must not wait for the caller's dispatcher to resume an await catch. Late authentication cannot revive that revoked action. After actual native admission the existing admission CAS refuses revocation: cancelling the caller detaches its result observer, while the original private worker finishes once and retains its original outcome and settlement signal. No native cancellation, retry, new dispatcher or domain permission is introduced.

Historical host evidence before the passive Recover diagnostic: all374 configured client unit methods in89 suites passed with no failures/errors/skips; Detekt999 at unchanged limit1000; Kover6418/17872 lines (35.91%, advisory goal). Whole-body native test-helper closed-port checks are9 Swift +9 Kotlin, separately from actual platform/framework/E2E qualification. These measurements do not replace fresh native packaging and final original1–50 UI E2E.

## Passive Recover action observations (isolated Test4 diagnostic)

The ordinary platform capability defaults false. The test-owned local DEBUG CaseFour wrapper emits only fixed primitive sequence/stage/outcome/rejection flags. ROOT_CALLBACK means the coordinator was reached, not that a physical click, native invocation or Core commit succeeded. Null intent and an unavailable inner lease trace have UNKNOWN cause. Existing Core-port observation is independent.

At most six tickets and four independently reserved stages per ticket are observed; RETURNED and same-job completion have no global order. A view job cancellation/completion is not private worker settlement. Missing final CLOSE, logging failure, overflow or pending/late callbacks make coverage unknown. A continuous close requires an admitted owner-close summary and independently observed ROOT_CALLBACK, POST_REGISTRATION and completed same-job records, plus RETURNED when a body with an intent ran. Faults or omissions observed before close yield UNKNOWN. Even a clean close covers only its open interval; it never excludes work after close. Nothing in this diagnostic permits retry or changes native admission, authentication, reads, synchronization, Core policy or result retention.

Records contain no identifiers, names, values, native JSON, Throwable payloads or credentials; complete rows are bounded to256UTF-8 bytes and at most26 including READY/CLOSE. Ordinary Android/iOS presentation and all business controls remain unchanged. Host tests are component evidence; actual real-Core Test4 results must be reported separately.

The passive Recover worker diagnostic distinguishes the cancellation operand already consumed by the worker from a failed existing selection check. If selection was checked, its already-recorded fixed currentness category is reported; an unobserved or absent selection uses `WORKER_SELECTION_INVALID`. Cancellation short-circuits the selection check exactly as before. These flags do not change guards, native entry, result retention or retry permissions and do not prove Core persistence. A disabled UI control or `REQUEST_SENT` test marker remains insufficient evidence of canonical submission; the original canonical FIFO/business assertions remain required.

### Marked E2E navigation observations
Secrets navigation may emit closed `E2E_NAV_V1` records only for admitted local debug UI-test identities.
Records contain local bounded sequence/stage/booleans and route enums, never identity or secret payloads.
Consumed rendered/current/session/native-scope checks remain authoritative and unchanged; observations are
buffered until the original decision completes. A route-write record follows the original successful state update.
Missing callback records without healthy complete coverage mean unknown, not a delivered-gesture failure.
Diagnostics never retry navigation or relax guards; logging failure cannot change original results or errors.


### iOS E2E list settling (MS-20261101103546)

The test helper observes finite scoped list geometry until two consecutive immutable snapshots agree before and after each ordinary directed gesture. Idle observations never count as an endpoint: each list end requires three distinct directed gestures with no movement between settled states. Full relative row frames and bounds replace ID/clipping-only equality; movement, bounds changes, duplicates, invalid scope or deadline expiry cannot establish completion. The original 60-second direction/shared phase budgets and 64-gesture cap remain, with at most64 settling observations per wait. No sleep, app/Core change or additional business action is introduced. Progress I/U now counts completed gestures/consecutive no-movement gestures; historical logs keep their former meaning.

Actual whole-body controlled host checks:27 PASS, scoped host coverage94.70% lines (not app/native coverage). The old body produces the expected structural no-end refusal in a gesture-reset motion model; this does not prove the physical cause of G46. Normal owning Swift compilation and original wholeTest9 remain separate runtime gates.

**T35 current-request receiver phase (TEST source candidate; owning compilation and whole24 pending).** Exact Test24 Recover13–39 keeps its original real pre-request Ready and both real current incoming heads before the sole Approve. Scoped current sender progress is emitted only after the unchanged actual request UI call/postcondition; only that acceptedSENT starts the receiver180s notification interval. New invocation-local run/test/attempt/block/cycle/step/platform/role context and current owned live-stream markers cannot replace Core Device/Claim/receiver eligibility, actual UI/ownership/value/auth/Copy/Hide assertions or successful native actor results. Other callers stay legacy. Required/malformed context, replay, wrong scope/role/stream, abort, missing receiver or transport/protocol failure fails closed; metadata never approves or reveals.

The Test coordinator supplies immediate canonical bounded phase snapshots, not long-poll or business approval. Native TEST clients use a closed ASCII≤2048-byte12-field context/7-field response grammar and short cancellable≤1s whole calls (within the approved≤2s ceiling), including byte/known-length/redirect/status checks, actual Task/thread cancellation and one-shot late-completion suppression. Native clocks debit full RTT from conservative integer remaining durations; no UTC/shared monotonic origin is assumed and a response never grants a fresh180s.

The common cycle600s cap is **new and stricter**, beginning before actor registration/startup and shortening to the actual already existing iOS ordinary600s deadline. Android had no former overall600s timer. Original Ready240/SENT180/badge180 and later operation numbers remain ceilings; no timer is reset. Root B=acceptedSENT+180 and D are authoritative, including Swift's existing serial Home/wait operations; a timeout parameter alone is not an absolute budget. Failure closes new grants/proofs/old opted-in approval reads and enters the existing actual owned-process shutdown. Already admitted/issued remote input is not claimed retroactively revoked. No app/Core/FFI behavior, scenario39 roles/repetitions or source/provenance exception is introduced.

Actual original healthy/causalRED, new-helper9+9 and whole-caller23 host/API invocations with exact legacy effect parity are recorded separately in T35. Root also records actual JS-produced wire→same native codec, four indexed rows per platform; independent wire-result/remaining gates are separate. Synthetic ports, generated proposals and source reviews are not mobile UI acceptance; actual owning Android instrumentation/iOS UITest compilation, complete strict normalV5 provenance and original whole24 from its beginning remain mandatory. G65 FAILED12/39 and previous whole22/49 acceptance/evidence stay historical. No result/index graft, reseal or skip to13.


### T36 native Recover invocation scope — exact24/25 only

This TEST-only extension admits only Test24 / `ios-multi-secret-lifecycle` and Test25 / `web-secondary-creator-lifecycle`, Recover13–39. It does not activate26–36 or46–49; direct Show and other legacy callers remain unchanged. Retaining the historical `Test24RecoveryPhase` type names does not alias a selected25 invocation to24.

Every newly compiled required native entry receives `recoveryPhaseSelectedProfile` independently from the actual selected scenario/native launch before either context encoding. Exact values are `test24:ios-multi-secret-lifecycle` and `test25:web-secondary-creator-lifecycle`. Missing, malformed or foreign selection refuses before phase codec/client, auth or UI. Selected25 additionally requires a separate canonical JSON String `recoveryPhaseExpected` from the admitted actual run/attempt/selected scenario/normalized Recover block/cycle/step/platform/role and registered primitive IDs. It must not be derived from raw context, contextFor or snapshot. The strict closed Context12 decoder validates both sides; canonical whole-string equality preserves all12 fields, and current native role/cycle/step/static platform are still checked independently.

Only selected24 permits genuinely absent expected metadata and exact literal24 context; a supplied null/empty/wrong-type expected value is not absence. The pure historical24 codec API remains compatible separately from this stricter new private UI launch contract. Android checks Bundle presence/type, exact String true/false and nonempty new metadata; iOS validates supplied metadata before Optional JSON decoding can erase null presence. Unopted legacy requires absent new selected/expected metadata and absent/empty raw context. No metadata grants business approval, Claim ownership or reveal authority.

Reply7, immediate bounded transport, cancellation/late-completion suppression, monotonic clock/RTT debit, Root deadline600 and remaining badge180 semantics are unchanged. All pre-request empty/Ready, real request postcondition, current head/name/request/controls, two receiver/sole approval, alert/dismiss, auth/value/Copy/Hide and actor/result checks retain their original order and limits. No context is shared across actions.

T36 candidate host/config/API/wire receipts must be distinguished from real mobile UI results. Adoption requires independent source/security review, actual candidate positives before mismatch negatives, ordinary owning Android instrumentation/iOS UITest compilation and strict fresh source/product provenance. Historical G66 whole24 PASS and interrupted25 do not qualify these changed sources or establish whole25 success.



### T40 iOS Show procedure boundary — literal25 only

The TEST-only `test25ShowPhase` private field is a canonical eight-key JSON String, ordered testNumber/role/nonce/cycle/step/runId/contextId/invocationId. It admits only selected25/`web-secondary-creator-lifecycle`, iOS sender, logical step1 and actual Recover rows19–21/28–30/37–39. Existing independent full Context12/expected/selected entry remains necessary. The actual Node builder/registry and normal SDK provenance independently bind selector/source/owner; wire fields alone do not prove those facts.

The existing raw metadata key-list rejects supplied null/nonstring/empty. New25 pure metadata preflight uses the already-read routing before identity/app.launch; actual handler and procedural encoder recheck current scope. Legacy22/24/no-new-field branches are unchanged. No new AX/file/clock read or changed Show/auth/value/Copy/Hide predicate is added.

Only the existing actual callback after Show input/system-confirmation return, or its existing already-authorized value-present branch, can produce the bounded stdout `IOS_TEST25_SHOW_PREP_V1` event. It is preparation, never value/authorization/PASS. Cancellation, nonzero/missing XCTest failure count or invalid current metadata produces no marker and the actual caller records XCTFail under continueAfterFailure=false. Actual exact-value/ordered-words/Copy, Hide/hidden and selected-suite terminal remain mandatory.

The clock-origin correction separates preparation≤180s from the unchanged45s value window after INPUT_RETURNED/ALREADY_VISIBLE and60s close after value. Every stage is clipped to the SAME armed ordinary600s actor receipt AND current phaseD; no fresh600/285s total or increased45. Old22's helper/producer, lbShow/reveal and Test24 phase codec/transport stay exact. Already issued input is not claimed retroactively revoked after host abort; no new phase/procedure proof is accepted after abort.

Source/host tests are not UI acceptance. New TEST bytes require genuine owning iOS UITest compilation and normal F1/F2/V5; whole25 reruns from1, preserving failed18/39 history and all39 original actions. Future26–36/46–49 remain outside T40 and require separate source-bound T37 rebase/qualification. No old-index graft, alias22 or partial PASS transfer.


### Finite recovery test profiles

The TEST-only request metadata codec admits literal24/25 and the exact additional
profiles26–36/46–49. Each requires its exact selected mode/cycle bounds and the
same independently supplied canonical full12 expected tuple as its raw tuple;
literal24 retains its absent-expected rule. Unknown numbers/modes and foreign
selected/raw pairs are refused. This metadata grants no Core authorization.

The additional iOS sender profiles use private String `recoveryShowPhase`,
canonical keys `testNumber,role,nonce,cycle,step,runId,contextId,invocationId` and
`IOS_RECOVERY_SHOW_PREP_V1` with literal profile number. Exactly132 source-bound
iOS sender rows are permitted. The existing25 helper/field/prefix remains
separate. Supplied null/type/empty, receiver/Android/legacy/foreign scope and old
plus new field conflicts fail before the affected test entry's side effects.
The original input/already-visible callback emits a procedure receipt only after
Task/failure/current-entry checks. It is neither Secret availability nor a PASS.

The parent retains the original armed600/current-phase deadlines, preparation
at most180, unchanged45 value interval after the callback and60 close interval.
Actual value/Copy/Hide, approval/observer checks, roles/repetitions and participant
terminal results remain required. Host dependency-port tests cannot establish
real OS consent, clipboard behavior, SDK/ABI provenance or whole UI acceptance.

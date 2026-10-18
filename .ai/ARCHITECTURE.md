# AI architecture — meta-secret-compose

.ai/WORKFLOW.md is the canonical implementation stage contract; .ai/ORCHESTRATOR.md owns command routing. AGENTS.md and CLAUDE.md bootstrap those sources, not separate workflows. See INDEX.md for agents, templates and commands.

## Application boundary

- composeApp/commonMain owns shared Compose Multiplatform views, presentation/ViewModels and platform-independent client integration.
- androidMain and iosMain implement injected platform ports; iosApp hosts the shared UI and native integration.
- MVVM + Coordinator; typed state flows down and intents flow up. Local focus/hover/animation state is allowed in views; application operations belong in ViewModels/controllers.
- Interface + DI is the platform abstraction. This project forbids expect/actual.
- Existing Core ports/integration adapters consume UniFFI. Views/ViewModels never call native bindings directly. Rust Core alone owns crypto, membership, approvals and synchronization decisions; this repository does not edit Rust.
- Current targets are Android and iOS; phone/tablet/orientation support is a UI responsibility. A standalone desktop target is not implicitly introduced by the registration redesign.

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

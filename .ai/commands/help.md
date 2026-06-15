---
description: Display available commands with descriptions
---

# Help

## 🚀 Full Workflow

**`run <payload>`**  
Execute complete 10-stage automated workflow.

**`run <payload> --from stage-<n>`**  
Resume workflow from specific stage (for retries/debugging).

Where `<payload>`:
- Issue number (e.g., `#42`)
- Issue URL
- Free-text task description

---

## 📋 Individual Stages

**`only-issue-coordinator <payload>`**  
Stage 1: Analyze GitHub issue or task description. Detect Figma links.

**`only-grill-me <payload>`**  
Stage 2: "Grill Me" interview session (adaptive duration). Simple task = 5 min. Complex = 45 min. Ask only needed questions until shared understanding reached.

**`only-planner <payload>`**  
Stage 3: Create detailed implementation plan from issue analysis and clarifications.

**`only-constraint-validator <payload>`**  
Stage 3.5: Validate plan against MetaSecret architecture constraints. MANDATORY before implementation.

**`only-tdd-test-author <payload>`**  
Stage 4a: Write failing tests from implementation plan (TDD).

**`only-tdd-implementer <payload>`**  
Stage 4b: Red-Green-Refactor cycle (TDD). Implement minimal code to pass tests.

**`only-tdd-refactorer <payload>`**  
Stage 4c: Major refactoring after 3-5 red-green cycles. Clean code, extract utilities, add docs.

**`only-reviewer <payload>`**  
Stage 6: Code review of implementation + 80% coverage check.

**`only-release-manager <payload>`**  
Stage 10: Create branch, commit, and pull request.

---

## 🧪 Maestro E2E Testing

**`write test-flow "<description>"`**  
Write a new Maestro test flow from text description. Creates `.maestro/<test-name>.yaml`.

Examples:
- `write test-flow "User opens app and sees onboarding"`
- `write test-flow "iOS: User enables biometry" --ios`
- `write test-flow "Android: User taps back button" --android`

**`check test-flow <test-name>`**  
Run Maestro test on simulator/emulator. Builds, installs, runs test, reports results.

Examples:
- `check test-flow onboarding`
- `check test-flow android-join-device`

**`check-simulators [ios|android]`**  
Check availability of iOS simulators and Android emulators. Shows which devices are ready to use.

---

## 🚀 Quick App Launch

**`launch ios`**  
Build Debug .app, install on iOS simulator, and launch app. For manual testing.

**`launch android`**  
Build Debug APK, install on Android emulator, and launch app. For manual testing.

---

## 🛠️ Utilities

**`only-glossary-update "<feature>"`**  
Build or update project glossary. Ensures consistent terminology across AI, code, docs.

**`only-from-prompt "<description>"`**  
Start workflow from manual feature/bug description (no GitHub issue needed).

**`only-debug-rca <failed-artifact>`**  
Analyze failed workflow artifact and generate root cause analysis.

**`only-release-notes <payload>`**  
Generate release notes from implementation.

**`only-workflow-pattern-capture <payload>`**  
Capture and document workflow patterns for future reuse.

---

## 📖 Documentation

See `.ai/WORKFLOW.md` for 10-stage workflow specification.  
See `.ai/ORCHESTRATOR.md` for command routing.  
See `.ai/INDEX.md` for complete file structure.  
See `.ai/rules/maestro-test-writing.md` for Maestro YAML syntax.  
See `.ai/rules/maestro-setup-guide.md` for iOS/Android device commands.

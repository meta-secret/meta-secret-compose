# Stage 3: Implementation Plan

**Status:** Success | Failed | Skipped

---

## Summary

Overview of implementation approach.

---

## Architecture Alignment

How this design aligns with MetaSecret architecture:
- MVVM layers affected: [list]
- Coordinator flow: [description]
- Data flow: [description]

---

## Feature Breakdown

### Feature 1: [Name]
- **Description:** [What it does]
- **Tests required:** [number]
- **Files to create/modify:** [list]
- **Dependencies:** [list]

### Feature 2: [Name]
- **Description:** [What it does]
- **Tests required:** [number]
- **Files to create/modify:** [list]
- **Dependencies:** [list]

---

## Constraint Compliance Preview

Key constraints to validate (Stage 3.5):
- Vault model: [how design respects k=1 for 1–2 devices, k=2 for 3 devices, and the 3-device limit]
- Device storage: [DMK handling, backup strategy]
- Approval model: [when approval required]
- Biometry: [if applicable]

---

## Documentation Impact

- **Status:** Required / Not required
- **Affected files or sections:**
  - `CONSTRAINTS.md`:
  - `GLOSSARY.md`:
  - `ARCHITECTURE.md` / protocol/API docs:
  - README / E2E scenario docs:
- **If not required, concrete reason:**
- **Verification that updates are complete:**

---

## Test Strategy

- Total tests planned: [number]
- Coverage target: 80%+ (90%+ for business logic)
- Test tools: Kotlin Test + XCTest

---

## Timeline & Effort

- Estimated cycles: 3-5 red-green cycles
- Major refactor: [scope]
- Total effort: [estimate]

---

## Ready for Stage 3.5 Constraint Validation

Proceed to constraint check: YES / NO

## UX acceptance and layer ownership

| Approved behavior / transition | Responsible layer and Core response | Test level and platform | Evidence / result |
|---|---|---|---|

For registration include every applicable row from `.ai/rules/registration-ux.md`. Record unresolved Core contract questions before implementation. OS authentication needs real-device evidence; emulator auto-success is not evidence of a real prompt.

Coverage policy: [mobile coverage policy](../rules/coverage-policy.md). Report validation and passing tests are mandatory; the overall 80% goal is advisory.

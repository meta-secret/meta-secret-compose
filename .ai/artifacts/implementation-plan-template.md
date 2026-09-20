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

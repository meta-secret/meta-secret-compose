# Agent Artifact Integration Guide

## Overview

Every agent in the workflow must:
1. Read the appropriate template from `.ai/artifacts/`
2. Write output to `.ai/artifacts/run/` with proper naming
3. Set Status field (Success/Failed/Skipped)
4. Print start/end logs

---

## Stage-by-Stage Integration

### Stage 1: github-issue-coordinator

**What agent reads:**
- User's issue/task input
- `.ai/GLOSSARY.md` (for terminology)
- `.ai/artifacts/issue-analysis-template.md` (template)

**What agent writes:**
- File: `.ai/artifacts/run/MS-<run-id>-001-understanding.md`
- Status: Success / Failed / Skipped

**Example log output:**
```
Start stage 1: Issue Analysis
Reading issue #42...
Analyzing requirements...
Stage 1: Issue Analysis completed
```

---

### Stage 2: requirements-clarifier

**What agent reads:**
- `.ai/artifacts/run/MS-<run-id>-001-understanding.md` (Stage 1 output)
- `.ai/GLOSSARY.md` (for terminology)
- `.ai/artifacts/clarification-template.md` (template)
- `.ai/skills/grill-me/SKILL.md` (questioning methodology)

**What agent writes:**
- File: `.ai/artifacts/run/MS-<run-id>-002-clarification.md`
- Status: Success / Failed / Skipped

**Duration guidance:**
- Simple tasks: 5-10 minutes
- Medium tasks: 15-25 minutes
- Complex tasks: 30-45 minutes

---

### Stage 3: feature-planner

**What agent reads:**
- `.ai/artifacts/run/MS-<run-id>-001-understanding.md` (Stage 1)
- `.ai/artifacts/run/MS-<run-id>-002-clarification.md` (Stage 2)
- `.ai/GLOSSARY.md` (for terminology)
- `.ai/artifacts/implementation-plan-template.md` (template)
- `.ai/rules/kmp-principles.md` (KMM architecture)

**What agent writes:**
- File: `.ai/artifacts/run/MS-<run-id>-003-planning.md`
- Status: Success / Failed / Skipped

---

### Stage 3.5: constraint-validator

**What agent reads:**
- `.ai/artifacts/run/MS-<run-id>-003-planning.md` (Stage 3)
- `.ai/artifacts/run/MS-<run-id>-002-clarification.md` (Stage 2)
- `.ai/CONSTRAINTS.md` (main index - MANDATORY)
- Category-specific constraint files (as needed):
  - `.ai/constraints-vault-model.md`
  - `.ai/constraints-device-storage.md`
  - `.ai/constraints-operations.md`
  - `.ai/constraints-biometry-auth.md`
  - `.ai/constraints-core-communication.md`
- `.ai/artifacts/constraint-validation-template.md` (template)

**What agent writes:**
- File: `.ai/artifacts/run/MS-<run-id>-0035-constraints.md`
- Status: Pass / Fail / Skipped

**Critical:** If Status: Fail -> BLOCK Stage 4, return to Stage 3

---

### Stage 4a: tdd-test-author

**What agent reads:**
- `.ai/artifacts/run/MS-<run-id>-003-planning.md` (Stage 3)
- `.ai/artifacts/run/MS-<run-id>-002-clarification.md` (Stage 2)
- `.ai/GLOSSARY.md` (for terminology)
- `.ai/artifacts/test-authoring-template.md` (template)
- `.ai/skills/test-driven-development/SKILL.md` (TDD methodology)

**What agent writes:**
- File: `.ai/artifacts/run/MS-<run-id>-004a-tests.md`
- Status: Success / Failed / Skipped
- Test files themselves in `src/commonTest/kotlin/` and `iosApp/Tests/`

**Key requirement:** All tests must FAIL at this stage (feature doesn't exist)

---

### Stage 4b: tdd-implementer

**What agent reads:**
- Test files (from Stage 4a)
- `.ai/artifacts/run/MS-<run-id>-003-planning.md` (Stage 3)
- `.ai/artifacts/run/MS-<run-id>-004a-tests.md` (Stage 4a)
- `.ai/GLOSSARY.md` (for terminology)
- `.ai/artifacts/implementation-template.md` (template)
- `.ai/skills/red-green-refactor/SKILL.md` (red-green-refactor methodology)

**What agent writes:**
- File: `.ai/artifacts/run/MS-<run-id>-004b-implementation.md`
- Status: Success / Failed / Skipped
- Implementation code in `src/commonMain/kotlin/` and `iosApp/`

**Batch strategy:** 3 red-green-refactor cycles, then major refactor

---

### Stage 4c: tdd-refactorer

**What agent reads:**
- Implementation code (from Stage 4b)
- Test files (from Stage 4a)
- `.ai/artifacts/run/MS-<run-id>-003-planning.md` (Stage 3)
- `.ai/artifacts/run/MS-<run-id>-004b-implementation.md` (Stage 4b)
- `.ai/GLOSSARY.md` (for terminology)
- `.ai/artifacts/refactoring-template.md` (template)
- `.ai/skills/test-driven-development/SKILL.md` (refactoring best practices)

**What agent writes:**
- File: `.ai/artifacts/run/MS-<run-id>-004c-refactored.md`
- Status: Success / Failed / Skipped
- Refactored code in `src/commonMain/kotlin/` and `iosApp/`

**Requirement:** All tests must still pass after refactoring

---

### Stage 5: Build

**What to run:**
```bash
./gradlew build -x test --no-daemon --parallel --console=plain
```

**Reads:**
- Implementation code
- `.ai/artifacts/build-report-template.md` (template)

**Writes:**
- File: `.ai/artifacts/run/MS-<run-id>-005-build.md`
- Status: Success / Failed / Skipped
- On retry: `.ai/artifacts/run/MS-<run-id>-005-build -retry-1.md`

**Retry budget:** 1 retry max. On second failure, escalate to `only-debug-rca`

---

### Stage 6: code-reviewer

**What agent reads:**
- Implementation code
- `.ai/artifacts/run/MS-<run-id>-003-planning.md` (Stage 3)
- `.ai/artifacts/run/MS-<run-id>-004b-implementation.md` (Stage 4b)
- `.ai/artifacts/run/MS-<run-id>-0035-constraints.md` (Stage 3.5)
- `.ai/GLOSSARY.md` (for terminology)
- `.ai/artifacts/review-report-template.md` (template)
- `.ai/rules/kmp-principles.md` (KMM architecture)

**What agent writes:**
- File: `.ai/artifacts/run/MS-<run-id>-006-review.md`
- Status: Success / Failed / Skipped

**Critical checks:**
- Test coverage >= 80%
- All clarifications from Stage 2 addressed
- No constraint violations
- Code follows KMM architecture

---

### Stage 7: design-reviewer

**Condition:** Only if Stage 1 has Figma link

**What agent reads:**
- Figma design (URL from Stage 1)
- Implementation code
- `.ai/artifacts/run/MS-<run-id>-001-understanding.md` (Stage 1 - Figma context)
- `.ai/artifacts/design-review-report-template.md` (template)

**What agent writes:**
- File: `.ai/artifacts/run/MS-<run-id>-007-design-review.md`
- Status: Success / Failed / Skipped

---

### Stage 8: Coverage Verification

**What to run:**
```bash
./gradlew koverReport
```

**Reads:**
- Test coverage report
- `.ai/artifacts/coverage-report-template.md` (template)

**Writes:**
- File: `.ai/artifacts/run/MS-<run-id>-008-coverage.md`
- Status: Pass / Fail / Skipped

**Requirement:** Coverage >= 80% (business logic: 90%+)

---

### Stage 9: test-verifier

**What to run:**
```bash
./gradlew test --no-daemon --parallel --console=plain
```

**Reads:**
- Test files
- `.ai/artifacts/test-report-template.md` (template)

**Writes:**
- File: `.ai/artifacts/run/MS-<run-id>-009-test-run.md`
- Status: Success / Failed / Skipped
- On retry: `.ai/artifacts/run/MS-<run-id>-009-test-run -retry-1.md`

**Retry budget:** 2 retries max. On third failure, escalate to `only-debug-rca`

---

### Stage 10: release-manager

**What to do:**
- Create feature branch
- Stage and commit changes
- Create pull request

**Reads:**
- All previous artifacts (for PR description)
- `.ai/artifacts/pr-template.md` (template)

**Writes:**
- File: `.ai/artifacts/run/MS-<run-id>-010-pr.md`
- Status: Success / Failed / Skipped

---

## Artifact Directory Structure After Complete Run

```
.ai/artifacts/run/
├── MS-42-001-understanding.md
├── MS-42-002-clarification.md
├── MS-42-003-planning.md
├── MS-42-0035-constraints.md
├── MS-42-004a-tests.md
├── MS-42-004b-implementation.md
├── MS-42-004c-refactored.md
├── MS-42-005-build.md
├── MS-42-006-review.md
├── MS-42-007-design-review.md
├── MS-42-008-coverage.md
├── MS-42-009-test-run.md
└── MS-42-010-pr.md
```

Each file contains **Status** at the top and full documentation of what happened.

---

## Error Handling Pattern

If a stage fails:

1. **Write artifact with Status: Failed**
2. **If retryable** (build, tests):
   - Analyze error
   - Write artifact with suffix ` -retry-1`
   - If fails again: ` -retry-2`
   - If still fails: escalate
3. **If not retryable** (planning, review):
   - Write artifact with Status: Failed
   - Return to Stage 3 (Planning)
4. **Escalate if retries exhausted:**
   - Run `only-debug-rca <failed-artifact-path>`

---

## Run ID Rules for Artifact Naming

When determining run-id:

- **If user says "run issue #42"** -> run-id = `42`
- **If user says "run compose the widget"** -> run-id = `20260610143045` (UTC timestamp YYYYMMDDHHMMSS)

Pass run-id to all subsequent stages in that run.


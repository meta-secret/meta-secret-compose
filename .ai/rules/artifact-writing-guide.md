# Artifact Writing Guide

## Purpose

Every stage in the workflow must produce an artifact in `.ai/artifacts/run/` that documents:
- What happened at that stage
- The status (Success / Failed / Skipped)
- Key decisions and findings
- Next steps

---

## Artifact Naming Convention

All artifacts follow this pattern:

```
MS-<run-id>-<stage-number>-<stage-name>[ -retry-N ].md
```

### Run ID Rules

- **GitHub issue input (e.g., #42):** Use issue number -> `MS-42-001-...`
- **Free-text input:** Use UTC timestamp -> `MS-20260610143045-001-...`

### Stage Numbers

```
001 = Stage 1 (Issue Analysis)
002 = Stage 2 (Grill Me / Clarification)
003 = Stage 3 (Planning)
0035 = Stage 3.5 (Constraint Validation)
004a = Stage 4a (TDD Test Author)
004b = Stage 4b (TDD Implementation)
004c = Stage 4c (TDD Refactoring)
005 = Stage 5 (Build)
006 = Stage 6 (Code Review)
007 = Stage 7 (Design Review)
008 = Stage 8 (Coverage Verification)
009 = Stage 9 (Test Run)
010 = Stage 10 (PR / Release)
```

### Retry Suffix

If retrying after failure, add ` -retry-1` or ` -retry-2`:

```
MS-42-005-build.md            (first attempt)
MS-42-005-build -retry-1.md   (second attempt)
MS-42-005-build -retry-2.md   (third attempt - max)
```

---

## How to Write an Artifact

### 1. Determine Run ID

Extract from user input:
- If user says "run issue #42" -> run-id = `42`
- If user says "run compose the widget" -> run-id = `20260610143045` (current UTC timestamp)

Store run-id for all subsequent stages in that run.

### 2. Get the Template

Load the template file for your stage from `.ai/artifacts/`

### 3. Fill in the Status

At the top of artifact:
```markdown
**Status:** Success | Failed | Skipped
```

Choose ONE:
- **Success** — Stage completed as expected, ready for next stage
- **Failed** — Stage encountered errors, needs retry or redesign
- **Skipped** — Stage was skipped (e.g., Design Review when no Figma)

### 4. Write to Disk

Always write to `.ai/artifacts/run/`:

```
.ai/artifacts/run/MS-<run-id>-<stage-number>-<stage-name>.md
```

### 5. Log to Console

Print these exact lines:

```
Start stage <n>: <name>
[... work happens ...]
Stage <n>: <name> completed
```

---

## Status Field Rules

### For Implementation Stages (4a, 4b, 4c, 5, 6, 9)

```markdown
**Status:** Success | Failed | Skipped
```

### For Validation Stages (3.5, 8)

```markdown
**Status:** Pass | Fail | Skipped
```

---

## Checklist for Stage Authors

Before finishing a stage:

- [ ] Determined correct run-id
- [ ] Retrieved correct template
- [ ] Set Status field (Success/Failed/Skipped or Pass/Fail/Skipped)
- [ ] Filled in all required sections
- [ ] Wrote artifact to `.ai/artifacts/run/MS-<id>-<stage>-<name>.md`
- [ ] Printed start and end log lines
- [ ] Documented decisions and findings

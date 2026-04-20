# Cursor: Execute 9-Stage Pipeline

**Command:** `Cmd+K` then type: `run issue 123`

---

## 🎯 What Happens

Cursor reads this file and executes all 9 stages in sequence.

Each stage:
1. Runs specific agent
2. Writes artifact to `.ai/artifacts/run/MS-123/`
3. Checks for failures
4. Continues or retries

---

## 📋 EXACT STAGES (Copy-Paste These Prompts)

### STAGE 1: Understanding

**Prompt to send to Cursor:**
```
Read .ai/agents/github-issue-coordinator.md
Fetch GitHub issue #123 using: gh issue view 123
Extract: problem, goal, requirements, assumptions
Write to: .ai/artifacts/run/MS-123-001-understanding.md
```

**What you get:**
- File: `MS-123-001-understanding.md`
- Contains: issue analysis

**Next step:** Proceed to Stage 2

---

### STAGE 2: Planning

**Prompt:**
```
Read .ai/agents/feature-planner.md
Read: .ai/artifacts/run/MS-123-001-understanding.md
Read: .ai/rules/kmp-principles.md
Create detailed plan:
- List all files to modify (with paths)
- Tech stack alignment
- Deferred items
- Risks
Write to: .ai/artifacts/run/MS-123-002-planning.md
```

---

### STAGE 3: Implementation

**Prompt:**
```
Read .ai/agents/code-implementer.md
Read: .ai/artifacts/run/MS-123-002-planning.md
Implement ALL code changes per plan
Write to: .ai/artifacts/run/MS-123-003-implementation.md
Include: summary, list of modified files
```

---

### STAGE 4: Test Writing

**Prompt:**
```
Read .ai/agents/test-author.md
Read: .ai/artifacts/run/MS-123-003-implementation.md
Write tests for all changes
Write to: .ai/artifacts/run/MS-123-004-testing.md
```

---

### STAGE 5: Build

**Command:**
```bash
./gradlew build -x test --no-daemon --parallel --console=plain
```

**Then create file:** `.ai/artifacts/run/MS-123-005-build.md`

**Contents:**
```markdown
# Build Report

## Command
./gradlew build -x test --no-daemon --parallel --console=plain

## Result
SUCCESS (or FAILED)

## Output
[build logs here]
```

**Check result:**
- If SUCCESS → go to Stage 6
- If FAILED → go to Stage 5a

---

### STAGE 5a: Debug/RCA (If Build Failed)

**Prompt:**
```
Read .ai/agents/debug-rca.md
Analyze the build error from Stage 5
Identify root cause
Suggest specific fixes
Write to: .ai/artifacts/run/MS-123-005-build-rca-retry-1.md
```

**Then:**
- Go back to Stage 2 (Replan with RCA insights)
- Continue Stages 3-5 again
- Max 2 retries total

---

### STAGE 6: Test Run

**Command:**
```bash
./gradlew testDebugUnitTest --no-daemon --parallel --console=plain
```

**Then create file:** `.ai/artifacts/run/MS-123-006-test-run.md`

**Contents:**
```markdown
# Test Report

## Command
./gradlew testDebugUnitTest --no-daemon --parallel --console=plain

## Result
PASSED (or FAILED)

## Summary
X tests passed, Y tests failed

## Failed Tests (if any)
[list here]
```

**Check result:**
- If PASSED → go to Stage 7
- If FAILED → go back to Stage 2 (Replan, max 2 retries)

---

### STAGE 7: Code Review

**Prompt:**
```
Read .ai/agents/code-reviewer.md
Review all code changes against:
- .ai/rules/kmp-principles.md
- ARCHITECTURE.md
- CODE_STYLE.md
- SECURITY.md
Check: architecture, style, security, dead code
Write to: .ai/artifacts/run/MS-123-007-review.md
Include sections:
- Summary
- Must-fix
- Should-fix
- Nice-to-have
```

**Check result:**
- If PASSED → go to Stage 8
- If FAILED → go back to Stage 2 (Replan, max 2 retries)

---

### STAGE 8: Commit

**Prompt:**
```
Read .ai/agents/release-manager.md

1. Parse issue title from Stage 1
   Extract: [Task], [Feature], or [Bug]

2. Create branch:
   git fetch origin
   git checkout main
   git checkout -b {Prefix}/kuklin/MS-123

3. Stage changes:
   git add [all changed files]

4. Commit:
   git commit -m "[Issue #123] Summary of changes"

5. Push:
   git push -u origin {Prefix}/kuklin/MS-123

Write to: .ai/artifacts/run/MS-123-008-commit.md
Include: branch name, commit SHA, push status
```

---

### STAGE 9: Create PR

**Prompt:**
```
Read .ai/agents/release-manager.md

Run: gh pr create \
  --title "[Issue #123] Summary" \
  --body "Changes from MS-123 implementation" \
  --base main

Write to: .ai/artifacts/run/MS-123-009-pr.md
Include: PR URL, PR number, status
```

---

## ⚠️ FAILURE DETECTION

After each stage, check artifact for:
```
Status: FAILED
**FAIL**
❌
FAILED
```

If found → follow retry logic (see below)

---

## 🔄 RETRY LOGIC (Max 2 Per Type)

### Build Failed
```
Stage 5a: Debug/RCA
Stage 2: Replan (use RCA insights)
Stage 3: Re-implement
Stage 5: Retry build
(max 2 times)
```

### Test Failed
```
Stage 2: Replan
Stage 3: Re-implement
Stage 5: Build
Stage 6: Retry tests
(max 2 times)
```

### Review Failed
```
Stage 2: Replan
Stage 3: Re-implement
Stage 5: Build
Stage 6: Tests
Stage 7: Retry review
(max 2 times)
```

---

## 📂 Artifact Location

All artifacts in:
```
.ai/artifacts/run/
├── MS-123-001-understanding.md
├── MS-123-002-planning.md
├── MS-123-003-implementation.md
├── MS-123-004-testing.md
├── MS-123-005-build.md
├── MS-123-005-build-rca-retry-1.md (if needed)
├── MS-123-006-test-run.md
├── MS-123-007-review.md
├── MS-123-008-commit.md
└── MS-123-009-pr.md
```

---

## ✅ Done

When all 9 stages complete:
- PR created on GitHub
- All artifacts saved
- Workflow complete

---

**Version:** 2.0 (explicit instructions)  
**Last updated:** 2026-04-20

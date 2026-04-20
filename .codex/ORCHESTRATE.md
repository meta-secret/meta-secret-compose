# Codex: Execute 9-Stage Pipeline

**Purpose:** Run `run issue 49` and execute all 9 stages automatically.

---

## 📋 Quick Reference (Copy-Paste These Commands)

```bash
# Stage 1: Read issue
gh issue view 49 --json title,body,number

# Stage 2-9: Spawn agent for each stage (see below)
```

---

## 🚀 STEP-BY-STEP EXECUTION

### STAGE 1: Understanding (Read Issue)

**Agent:** `github-issue-coordinator`  
**Input:** GitHub issue #49  
**Output:** `.ai/artifacts/run/MS-49-001-understanding.md`

**DO THIS:**
1. Run: `gh issue view 49 --json title,body,number`
2. Extract: problem, goal, requirements
3. Write artifact to: `.ai/artifacts/run/MS-49-001-understanding.md`
4. **STOP** — check artifact for failures before continuing

**Failure check:** If artifact contains `FAILED` or `❌` → stop and report

---

### STAGE 2: Planning (Create Plan)

**Agent:** `feature-planner`  
**Input:** artifact from Stage 1  
**Output:** `.ai/artifacts/run/MS-49-002-planning.md`

**DO THIS:**
1. Read: `.ai/artifacts/run/MS-49-001-understanding.md`
2. Read: `.ai/rules/kmp-principles.md` (architecture rules)
3. Create plan:
   - List files to modify (with paths)
   - Tech stack alignment (MVVM, FFI boundary)
   - Risks
4. Write artifact: `.ai/artifacts/run/MS-49-002-planning.md`
5. **STOP** — check artifact for failures

---

### STAGE 3: Implementation (Write Code)

**Agent:** `code-implementer`  
**Input:** artifact from Stage 2  
**Output:** `.ai/artifacts/run/MS-49-003-implementation.md`

**DO THIS:**
1. Read: `.ai/artifacts/run/MS-49-002-planning.md`
2. Implement code changes per plan
3. Create `.ai/artifacts/run/MS-49-003-implementation.md` with:
   - Summary of changes
   - List of modified files
4. **STOP** — check for failures

---

### STAGE 4: Test Writing

**Agent:** `test-author`  
**Input:** artifact from Stage 3  
**Output:** `.ai/artifacts/run/MS-49-004-testing.md`

**DO THIS:**
1. Read: `.ai/artifacts/run/MS-49-003-implementation.md`
2. Write tests for changes
3. Create artifact: `.ai/artifacts/run/MS-49-004-testing.md`
4. **STOP** — check for failures

---

### STAGE 5: Build

**Command:** `./gradlew build -x test`  
**Output:** `.ai/artifacts/run/MS-49-005-build.md`

**DO THIS:**
1. Run: `./gradlew build -x test --no-daemon --parallel --console=plain`
2. Capture output
3. Write artifact with:
   - Command used
   - Result (SUCCESS or FAILED)
   - Any errors
4. **CHECK FOR FAILURE:**
   - If FAILED: go to Stage 5a (Debug/RCA)
   - If SUCCESS: go to Stage 6

---

### STAGE 5a: Debug/RCA (If Build Failed)

**Agent:** `debug-rca`  
**Input:** build error output  
**Output:** `.ai/artifacts/run/MS-49-005-build-rca-retry-1.md`

**DO THIS (only if Stage 5 FAILED):**
1. Read build error
2. Analyze root cause
3. Suggest fixes
4. Write artifact: `.ai/artifacts/run/MS-49-005-build-rca-retry-1.md`
5. **GO BACK TO STAGE 2** (Replan with RCA insights)
6. After replan: continue Stages 3-5 again
7. **MAX 2 retries** (if still fails after retry 2: STOP)

---

### STAGE 6: Test Run

**Agent:** `test-verifier`  
**Input:** Stage 5 succeeded  
**Output:** `.ai/artifacts/run/MS-49-006-test-run.md`

**DO THIS:**
1. Run: `./gradlew testDebugUnitTest --no-daemon --parallel --console=plain`
2. Capture test results
3. Write artifact:
   - Command used
   - Result (PASSED or FAILED)
   - Test summary
4. **CHECK FOR FAILURE:**
   - If FAILED: go back to Stage 2 (Replan, max 2 retries)
   - If PASSED: go to Stage 7

---

### STAGE 7: Code Review

**Agent:** `code-reviewer`  
**Input:** all changes from Stage 3  
**Output:** `.ai/artifacts/run/MS-49-007-review.md`

**DO THIS:**
1. Review all changes against:
   - `.ai/rules/kmp-principles.md` (architecture)
   - Code style rules
   - Security rules
2. Write artifact with:
   - Summary
   - Must-fix issues
   - Should-fix issues
3. **CHECK FOR FAILURE:**
   - If FAILED: go back to Stage 2 (Replan, max 2 retries)
   - If PASSED: go to Stage 8

---

### STAGE 8: Commit

**Agent:** `release-manager`  
**Input:** all stages 1-7 passed  
**Output:** `.ai/artifacts/run/MS-49-008-commit.md`

**DO THIS:**
1. Parse issue title from Stage 1 to extract prefix (Task/Feature/Bug)
2. Create branch: `{Prefix}/kuklin/MS-49`
3. Stage all changes
4. Create commit message: `[Issue #49] Brief summary`
5. Commit and push
6. Write artifact with:
   - Branch name
   - Commit SHA
   - Push status

---

### STAGE 9: Create PR

**Agent:** `release-manager`  
**Input:** Stage 8 succeeded  
**Output:** `.ai/artifacts/run/MS-49-009-pr.md`

**DO THIS:**
1. Create PR targeting `main`
2. Use commit message as title
3. Run: `gh pr create --title "..." --body "..." --base main`
4. Write artifact with:
   - PR URL
   - PR number
   - Status

---

## ⚠️ FAILURE HANDLING

### Retry Logic

```
Build FAILED (Stage 5)?
  → Debug/RCA (5a)
  → Replan (Stage 2)
  → Retry (Stages 3-5)
  → Max 2 times

Test FAILED (Stage 6)?
  → Replan (Stage 2)
  → Retry (Stages 3-6)
  → Max 2 times

Review FAILED (Stage 7)?
  → Replan (Stage 2)
  → Retry (Stages 3-7)
  → Max 2 times
```

---

## 🎯 RESUME FROM STAGE

If previous run failed at Stage 6:

```
codex run issue 49 --from stage-2
```

This skips Stages 1-5 (already done). All prior artifacts already on disk.

---

## 📝 Artifact Naming

```
MS-49-001-understanding.md
MS-49-002-planning.md
MS-49-003-implementation.md
MS-49-004-testing.md
MS-49-005-build.md
MS-49-005-build-rca-retry-1.md      (if build failed)
MS-49-005-build-retry-1.md           (retry)
MS-49-006-test-run.md
MS-49-007-review.md
MS-49-008-commit.md
MS-49-009-pr.md
```

All in: `.ai/artifacts/run/`

---

## ✅ Done

When Stage 9 completes: **PR created**

All artifacts in: `.ai/artifacts/run/MS-49/`

---

**Version:** 2.0 (explicit instructions)  
**Last updated:** 2026-04-20

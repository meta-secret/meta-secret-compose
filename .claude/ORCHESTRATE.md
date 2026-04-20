# Claude Code: Execute 9-Stage Pipeline

**Command:** `/run issue 123`

---

## 🎯 What This Does

Spawns 9 Tasks in sequence. Each Task:
1. Reads stage definition below
2. Runs agent from `.ai/agents/`
3. Writes artifact to `.ai/artifacts/run/MS-123/`
4. Checks for failures
5. Continues or retries

---

## 📋 STAGE DEFINITIONS (Self-Contained)

### STAGE 1: Understanding

```
Agent: github-issue-coordinator
Input: issue ID (e.g., 123)
Output: .ai/artifacts/run/MS-123-001-understanding.md

Task Prompt:
"Read .ai/agents/github-issue-coordinator.md
Execute for issue 123.
Output MUST go to: .ai/artifacts/run/MS-123-001-understanding.md
Artifact MUST contain: problem, goal, requirements, assumptions"
```

---

### STAGE 2: Planning

```
Agent: feature-planner
Input: artifact from Stage 1
Output: .ai/artifacts/run/MS-123-002-planning.md

Task Prompt:
"Read .ai/agents/feature-planner.md
Read artifact: .ai/artifacts/run/MS-123-001-understanding.md
Read: .ai/rules/kmp-principles.md
Create plan (scope, tech stack, deferred items).
Output to: .ai/artifacts/run/MS-123-002-planning.md"
```

---

### STAGE 3: Implementation

```
Agent: code-implementer
Input: artifact from Stage 2
Output: .ai/artifacts/run/MS-123-003-implementation.md

Task Prompt:
"Read .ai/agents/code-implementer.md
Read artifact: .ai/artifacts/run/MS-123-002-planning.md
Implement code changes.
Output to: .ai/artifacts/run/MS-123-003-implementation.md
Include: summary, list of modified files"
```

---

### STAGE 4: Test Writing

```
Agent: test-author
Input: artifact from Stage 3
Output: .ai/artifacts/run/MS-123-004-testing.md

Task Prompt:
"Read .ai/agents/test-author.md
Read artifact: .ai/artifacts/run/MS-123-003-implementation.md
Write tests for the changes.
Output to: .ai/artifacts/run/MS-123-004-testing.md"
```

---

### STAGE 5: Build

```
Command: ./gradlew build -x test --no-daemon --parallel
Output: .ai/artifacts/run/MS-123-005-build.md

Bash Task:
"./gradlew build -x test --no-daemon --parallel --console=plain 2>&1 | tee /tmp/build.log
if [ $? -ne 0 ]; then
  echo 'FAILED' > /tmp/build-status.txt
else
  echo 'SUCCESS' > /tmp/build-status.txt
fi"

Then create artifact with build output.
```

---

### STAGE 5a: Debug/RCA (If Stage 5 Failed)

```
Agent: debug-rca
Input: build error from Stage 5
Output: .ai/artifacts/run/MS-123-005-build-rca-retry-1.md

Task Prompt:
"Read .ai/agents/debug-rca.md
Analyze build error from: /tmp/build.log
Write RCA to: .ai/artifacts/run/MS-123-005-build-rca-retry-1.md
Include: root cause, suggested fixes"

Then GO BACK TO STAGE 2 (Replan based on RCA)
```

---

### STAGE 6: Test Run

```
Agent: test-verifier
Input: build succeeded
Output: .ai/artifacts/run/MS-123-006-test-run.md

Task Prompt:
"Read .ai/agents/test-verifier.md
Run: ./gradlew testDebugUnitTest --no-daemon --parallel --console=plain
Capture results.
Output to: .ai/artifacts/run/MS-123-006-test-run.md
Include: test summary, passed/failed count"
```

---

### STAGE 7: Code Review

```
Agent: code-reviewer
Input: all changes from Stage 3
Output: .ai/artifacts/run/MS-123-007-review.md

Task Prompt:
"Read .ai/agents/code-reviewer.md
Review all code changes against:
  - .ai/rules/kmp-principles.md
  - ARCHITECTURE.md
  - CODE_STYLE.md
Output to: .ai/artifacts/run/MS-123-007-review.md
Include: summary, must-fix, should-fix, nice-to-have"
```

---

### STAGE 8: Commit

```
Agent: release-manager
Input: all stages passed
Output: .ai/artifacts/run/MS-123-008-commit.md

Task Prompt:
"Read .ai/agents/release-manager.md
Create branch: {Prefix}/kuklin/MS-123
Stage all changes.
Commit message: [Issue #123] Summary
Push to remote.
Output to: .ai/artifacts/run/MS-123-008-commit.md
Include: branch name, commit SHA, push status"
```

---

### STAGE 9: Create PR

```
Agent: release-manager
Input: branch pushed (Stage 8)
Output: .ai/artifacts/run/MS-123-009-pr.md

Task Prompt:
"Read .ai/agents/release-manager.md
Create PR: gh pr create --title '...' --body '...' --base main
Output to: .ai/artifacts/run/MS-123-009-pr.md
Include: PR URL, PR number, status"
```

---

## ⚡ FAILURE CHECKING (After Each Stage)

```python
def check_failure(artifact_path):
    content = read_file(artifact_path)
    failure_markers = ["FAILED", "Status: FAILED", "❌"]
    if any(marker in content for marker in failure_markers):
        return True
    return False
```

**If Stage N fails:**
- For Stage 5 (Build): spawn Stage 5a (Debug/RCA) → go to Stage 2
- For Stage 6 (Tests): go to Stage 2
- For Stage 7 (Review): go to Stage 2
- Max 2 retries per failure type

---

## 🔄 RETRY LOGIC

```
Attempt 1: Build fails
  ├─ Stage 5a (Debug/RCA) 
  └─ Stage 2 (Replan)
     → Stages 3-5

Attempt 2: Build fails again
  ├─ Stage 5a (Debug/RCA 2nd time)
  └─ Stage 2 (Replan 2nd time)
     → Stages 3-5

If still fails: STOP (max retries reached)
```

---

## 🎯 INPUT HANDLING

When user says: `/run issue 123`

```python
if isinstance(input_str, int):
    issue_id = input_str
    timestamp = None
else:
    # Custom text input
    timestamp = time.strftime("%Y%m%d%H%M%S")
    issue_id = timestamp
```

---

## 📦 ARTIFACT DIRECTORY

Create before starting:
```bash
mkdir -p .ai/artifacts/run/
```

All artifacts: `.ai/artifacts/run/MS-<id>-<stage-number>-<stage-name>.md`

---

## ✅ SUCCESS CRITERIA

Pipeline complete when:
- Stage 9 succeeds
- PR created
- All artifacts in `.ai/artifacts/run/MS-<id>/`

---

**Version:** 2.0 (explicit instructions)  
**Last updated:** 2026-04-20

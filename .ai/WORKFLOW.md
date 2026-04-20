# Automated Workflow Orchestration

## 🎯 Purpose

Unified orchestration system for **`run issue <id>`** command across Claude Code, Cursor, and Codex CLI.

**Single source of truth** for all stages, retry logic, and artifact management.

---

## 🔄 Pipeline Overview

```
┌─────────────────────────────────────────────────────────────────┐
│ INPUT: run issue 123 (or "my task description")                │
└────────────────┬────────────────────────────────────────────────┘
                 │
        ┌────────▼─────────┐
        │  Stage 1: Read   │ → MS-<id>-001-understanding.md
        │ Issue/Prompt     │
        └────────┬─────────┘
                 │
        ┌────────▼─────────┐
        │  Stage 2: Plan   │ → MS-<id>-002-planning.md
        │                  │
        └────────┬─────────┘
                 │
        ┌────────▼──────────────┐
        │ Stage 3: Implement    │ → MS-<id>-003-implementation.md
        │                       │
        └────────┬──────────────┘
                 │
        ┌────────▼──────────────┐
        │ Stage 4: Write Tests  │ → MS-<id>-004-testing.md
        │                       │
        └────────┬──────────────┘
                 │
        ┌────────▼──────────────┐
        │ Stage 5: Build        │ → MS-<id>-005-build.md
        │                       │
        │ ❌ FAIL?             │
        │   ├─ Debug/RCA (5a)  │ → MS-<id>-005-build-rca-retry-1.md
        │   ├─ Replan (Stage 2)│ (max 2 times)
        │   └─ Retry Build     │ → MS-<id>-005-build-retry-1.md
        │                       │
        └────────┬──────────────┘
                 │
        ┌────────▼──────────────┐
        │ Stage 6: Run Tests    │ → MS-<id>-006-test-run.md
        │                       │
        │ ❌ FAIL?             │
        │   ├─ Replan (Stage 2)│ (max 2 times)
        │   └─ Retry Build+    │
        │                       │
        └────────┬──────────────┘
                 │
        ┌────────▼──────────────┐
        │ Stage 7: Code Review  │ → MS-<id>-007-review.md
        │                       │
        │ ❌ FAIL?             │
        │   ├─ Replan (Stage 2)│ (max 2 times)
        │   └─ Retry Build+    │
        │                       │
        └────────┬──────────────┘
                 │
        ┌────────▼──────────────┐
        │ Stage 8: Commit       │ → MS-<id>-008-commit.md
        │ (create branch+push)  │
        │                       │
        └────────┬──────────────┘
                 │
        ┌────────▼──────────────┐
        │ Stage 9: Create PR    │ → MS-<id>-009-pr.md
        │                       │
        └────────┬──────────────┘
                 │
        ┌────────▼─────────┐
        │  ✅ COMPLETE     │
        │  All artifacts   │
        │  in .ai/         │
        │  artifacts/run/  │
        └──────────────────┘
```

---

## 📋 Stage Definitions

### Stage 1: Understanding (Issue Reading)

**Agent:** `github-issue-coordinator`

**Input:**
- GitHub issue number (from `run issue 123`)
- OR custom text (use timestamp instead)

**Actions:**
1. If issue number: `gh issue view <id>` to fetch details
2. If text: use as-is
3. Extract: goal, requirements, context, assumptions
4. Identify affected areas

**Output:** `.ai/artifacts/run/MS-<id>-001-understanding.md`

**Failure markers:**
```
Status: FAILED
**FAIL**
❌
Return to Planning: YES
```

---

### Stage 2: Planning

**Agent:** `feature-planner`

**Input:**
- Read artifact from Stage 1

**Actions:**
1. Analyze understanding artifact
2. Create detailed implementation plan
3. List files to modify (with paths)
4. Identify tech stack alignment
5. List deferred items (if any)

**Output:** `.ai/artifacts/run/MS-<id>-002-planning.md`

**Failure markers:** Same as above

---

### Stage 3: Implementation

**Agent:** `code-implementer`

**Input:**
- Read artifact from Stage 2

**Actions:**
1. Follow plan exactly
2. Implement code changes
3. Update affected files
4. Keep diffs minimal

**Output:** `.ai/artifacts/run/MS-<id>-003-implementation.md`

**Contains:**
- Summary of changes
- List of modified files
- Any deviations from plan

---

### Stage 4: Test Writing

**Agent:** `test-author`

**Input:**
- Read Stage 3 artifact
- Look at implementation changes

**Actions:**
1. Write unit tests
2. Write integration tests (if needed)
3. Cover edge cases
4. Update test suite

**Output:** `.ai/artifacts/run/MS-<id>-004-testing.md`

**Contains:**
- List of test files created/modified
- Coverage summary
- Edge cases tested

---

### Stage 5: Build

**Command:** `./gradlew build --no-daemon --parallel --console=plain`

**Actions:**
1. Run build command (full build with test compilation/linking)
2. Capture output
3. Check for compilation and linking errors

**Output:** `.ai/artifacts/run/MS-<id>-005-build.md`

**On failure:**
- Check failure markers (see below)
- If FAIL detected:
  - Spawn Debug/RCA agent (Stage 5a)
  - After RCA: go to Stage 2 (Replan)
  - Max 2 retry loops

**Failure markers:**
```
Status: FAILED
**FAIL**
❌
```

---

### Stage 5a: Debug/RCA (On Build Failure)

**Agent:** `debug-rca`

**Input:**
- Build error output from Stage 5
- Implementation changes from Stage 3

**Actions:**
1. Analyze root cause of build failure
2. Identify which part failed
3. Suggest specific fixes
4. Write detailed RCA report

**Output:** `.ai/artifacts/run/MS-<id>-005-build-rca-retry-1.md`

**Then:**
- Go to Stage 2 (Replan with RCA insights)
- Implement fixes
- Retry build (Stage 5 again)
- Max 2 times total

---

### Stage 6: Test Run

**Agent:** `test-verifier`

**Input:**
- Build completed successfully
- Tests from Stage 4 exist

**Actions:**
1. Execute test suite: `./gradlew testDebugUnitTest`
2. Capture results (passed/failed)
3. Analyze failures if any

**Output:** `.ai/artifacts/run/MS-<id>-006-test-run.md`

**Contains:**
- Test command used
- Result (PASSED/FAILED)
- Test summary
- Failed tests (if any)

**On failure:**
- Go back to Stage 2 (Replan)
- Max 2 retries (then ask user)

---

### Stage 7: Code Review

**Agent:** `code-reviewer`

**Input:**
- All changes from Stage 3
- Project rules from `.ai/rules/`

**Actions:**
1. Review architecture compliance
2. Review code style
3. Check security rules
4. Identify dead code or issues
5. Suggest improvements

**Output:** `.ai/artifacts/run/MS-<id>-007-review.md`

**Contains:**
- Summary of review
- Must-fix issues
- Should-fix issues
- Nice-to-have improvements

**On failure:**
- Go back to Stage 2 (Replan)
- Max 2 retries (then ask user)

---

### Stage 8: Commit

**Agent:** `release-manager`

**Input:**
- All changes from Stage 3
- All stages completed

**Actions:**
1. Fetch latest from `main`
2. Create branch: `{Prefix}/kuklin/MS-{id}`
3. Stage all changes (from diff)
4. Create commit with message
5. Push to remote

**Output:** `.ai/artifacts/run/MS-<id>-008-commit.md`

**Branch naming:**
```
{Prefix}/kuklin/MS-{issueNumber}
```
- Prefix from issue title: `Task`, `Feature`, `Bug` (case-insensitive)
- Example: `Feature/kuklin/MS-123`

**Commit message:**
- Derived from changes
- Format: `[Issue #123] Brief description of changes`
- No secrets, no team IDs

---

### Stage 9: Create PR

**Agent:** `release-manager`

**Input:**
- Branch created and pushed (Stage 8)

**Actions:**
1. Create PR targeting `main`
2. Use commit message as PR title
3. Add PR description from changes
4. If `gh` available: `gh pr create`
5. If not available: print exact command

**Output:** `.ai/artifacts/run/MS-<id>-009-pr.md`

**Contains:**
- PR URL
- PR number
- PR status

---

## 🔄 Retry Logic Detail

### Build Failure Retry (Max 2)

```
Attempt 1: Build fails
  ├─ Spawn Debug/RCA agent
  ├─ Analyze error
  └─ Output: 005-build-rca-retry-1.md
     │
     └─ Go to Stage 2 (Replan)
        ├─ Implementer fixes per RCA insights
        ├─ Retry Stage 5 (Build)
        └─ Output: 005-build-retry-1.md

Attempt 2: Build fails again
  ├─ Spawn Debug/RCA agent (2nd time)
  ├─ Analyze error
  └─ Output: 005-build-rca-retry-2.md
     │
     └─ Go to Stage 2 (Replan 2nd time)
        ├─ Implementer fixes
        ├─ Retry Stage 5 (Build)
        └─ Output: 005-build-retry-2.md

If still failing: ⛔ STOP - Display error and ask user
```

### Test/Review Failure Retry (Max 2)

```
Attempt 1: Tests/Review fails
  ├─ Go to Stage 2 (Replan)
  ├─ Implementer fixes per feedback
  ├─ Retry Stage 5 (Build) + Stage 6/7 (Test/Review)
  └─ Output: 002-planning-retry-1.md

Attempt 2: Tests/Review fails again
  ├─ Go to Stage 2 (Replan 2nd time)
  ├─ Implementer fixes
  ├─ Retry Stage 5 (Build) + Stage 6/7 (Test/Review)
  └─ Output: 002-planning-retry-2.md

If still failing: ⛔ STOP - Display feedback and ask user
```

---

## 📦 Artifact Format

### File Naming

```
MS-{timestamp}-{stage-number}-{stage-name}.md

Examples:
MS-20260420143022-001-understanding.md
MS-20260420143022-002-planning.md
MS-20260420143022-003-implementation.md
MS-20260420143022-005-build-rca-retry-1.md
```

**Timestamp format:** `YYYYMMDDhhmmss` (compact)

### Artifact Location

```
.ai/artifacts/run/
├── MS-20260420143022-001-understanding.md
├── MS-20260420143022-002-planning.md
├── ...
└── MS-20260420143022-009-pr.md
```

### Failure Detection

After each stage, check artifact for markers:

```markdown
# Failure markers (stop if found)

Status: FAILED
**FAIL**
❌
Return to Planning: YES
```

If ANY marker found → stop pipeline and report.

---

## 🎨 Terminal Output

### Stage Start

```
🟢 Starting Stage 5: Build
Command: ./gradlew build --no-daemon --parallel --console=plain
```

(Green, emoji, clear stage name)

### Stage In Progress

```
🟡 Building... (this may take a minute)
```

(Yellow, emoji, concise message)

### Stage Complete Success

```
✅ Stage 5: Build completed successfully
```

(Green checkmark, emoji, stage name, status)

### Stage Complete Failure

```
❌ Stage 5: Build failed
Reason: Compilation error in xyz.kt line 42
Next step: Spawn Debug/RCA agent
```

(Red, emoji, stage name, reason, next action)

---

## 🚀 Entry Points (IDE-Specific)

### Claude Code

**File:** `.claude/ORCHESTRATE.md`

**Execution:**
1. User runs: `/run issue 123`
2. Claude Code reads `.claude/ORCHESTRATE.md`
3. Orchestrator spawns each stage as Task
4. Each Task reads `.ai/WORKFLOW.md` for stage definition
5. Task runs agent from `.ai/agents/`
6. Artifact written to `.ai/artifacts/run/`
7. Next Task checks artifact and continues or retries

### Cursor

**File:** `.cursor/WORKFLOW.md`

**Execution:**
1. User presses Cmd+K and runs: `run issue 123`
2. Cursor reads `.cursor/WORKFLOW.md`
3. WORKFLOW.md redirects to `.ai/WORKFLOW.md`
4. Same as Claude Code flow

### Codex CLI

**File:** `.codex/ORCHESTRATE.md`

**Execution:**
1. User runs: `codex run issue 123`
2. Codex CLI reads `.codex/ORCHESTRATE.md`
3. Orchestrator manages CLI-specific sub-agent spawning
4. Follows same stage flow as above

---

## 📊 Status Checks

### Before Each Stage

```yaml
Check:
  - Previous stage artifact exists
  - Previous stage artifact is readable
  - No failure markers in previous artifact
```

### After Each Stage

```yaml
Read artifact:
  - Look for failure markers
  - If FAIL found: stop and report
  - If SUCCESS: continue to next stage
```

### Retry Counter

```yaml
Track:
  - Build retry count (max 2)
  - Test/Review retry count (max 2)
  - If max reached and still failing: stop
```

---

## ⚙️ Configuration

### Environment Variables (Optional)

```bash
GITHUB_TOKEN=gh_...          # For gh command (auto-detected usually)
MS_ARTIFACTS_DIR=.ai/artifacts/run/  # Default: .ai/artifacts/run/
MS_TIMESTAMP_FORMAT=compact  # Default: YYYYMMDDhhmmss
```

### Branch Prefix Logic

Extract from issue title:
```
[Feature] ...  → Feature/kuklin/MS-{id}
[Bug] ...      → Bug/kuklin/MS-{id}
[Task] ...     → Task/kuklin/MS-{id}
(no bracket)   → Task/kuklin/MS-{id}  (default)
```

---

## 📚 References

| Document | Purpose |
|----------|---------|
| **AGENTS.md** | Entry point (this level) |
| **WORKFLOW.md** | This file (orchestration) |
| **PIPELINE.md** | Detailed stage specs |
| **.ai/agents/*** | Individual agent definitions |
| **.ai/rules/kmp-principles.md** | Architecture rules |
| **.claude/ORCHESTRATE.md** | Claude Code execution |
| **.cursor/WORKFLOW.md** | Cursor execution |
| **.codex/ORCHESTRATE.md** | Codex execution |

---

## ✅ Next Steps

1. ✅ Create `.claude/ORCHESTRATE.md` - Claude Code entry point
2. ✅ Create `.cursor/WORKFLOW.md` - Cursor entry point  
3. ✅ Create `.codex/ORCHESTRATE.md` - Codex entry point
4. ✅ Test workflow on all three IDEs
5. ✅ Cleanup unused files

---

**Status:** Ready for implementation
**Last updated:** 2026-04-20

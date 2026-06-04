---
description: Display available commands with descriptions
---

# Help

## 🚀 Full Workflow

**`run <payload>`**  
Execute complete 9-stage automated workflow.

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

**`only-planner <payload>`**  
Stage 2: Create detailed implementation plan from issue analysis.

**`only-implementer <payload>`**  
Stage 3: Implement code changes (Logic + UI).

**`only-reviewer <payload>`**  
Stage 5: Code review of implementation.

**`only-test-author <payload>`**  
Stage 7: Write test cases.

**`only-test-verifier <payload>`**  
Stage 8: Execute tests and verify results.

**`only-release-manager <payload>`**  
Stage 9: Create branch, commit, and pull request.

---

## 🛠️ Utilities

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

See `.ai/WORKFLOW.md` for complete 9-stage workflow specification.  
See `.ai/ORCHESTRATOR.md` for command routing details.

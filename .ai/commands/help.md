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

**`only-implementer <payload>`**  
Stage 4: Implement code changes (Logic + UI).

**`only-reviewer <payload>`**  
Stage 6: Code review of implementation.

**`only-test-author <payload>`**  
Stage 8: Write test cases.

**`only-test-verifier <payload>`**  
Stage 9: Execute tests and verify results.

**`only-release-manager <payload>`**  
Stage 10: Create branch, commit, and pull request.

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

See `.ai/WORKFLOW.md` for complete 10-stage workflow specification.  
See `.ai/ORCHESTRATOR.md` for command routing details.

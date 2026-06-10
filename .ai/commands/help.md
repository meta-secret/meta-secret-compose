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

**`only-constraint-validator <payload>`**  
Stage 3.5: Validate plan against MetaSecret architecture constraints. MANDATORY before implementation.

**`only-tdd-test-author <payload>`**  
Stage 4a: Write failing tests from implementation plan (TDD).

**`only-tdd-implementer <payload>`**  
Stage 4b: Red-Green-Refactor cycle (TDD). Implement minimal code to pass tests.

**`only-tdd-refactorer <payload>`**  
Stage 4c: Major refactoring after 3-5 red-green cycles. Clean code, extract utilities, add docs.

**`only-reviewer <payload>`**  
Stage 6: Code review of implementation + 80% coverage check.

**`only-release-manager <payload>`**  
Stage 10: Create branch, commit, and pull request.

---

## 🛠️ Utilities

**`only-glossary-update "<feature>"`**  
Build or update project glossary. Run monthly or when codebase grows. Ensures consistent terminology across AI, code, docs, and communication.

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

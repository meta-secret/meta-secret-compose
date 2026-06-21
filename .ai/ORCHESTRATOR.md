# AI Orchestrator

Command routing and execution for meta-secret-compose.

---

## Command Routing

### Full Workflow

#### `implement issue <payload>`

Execute:
1. **FIRST:** Read `.ai/rules/implement-issue-execution-checklist.md` (MANDATORY)
2. **SECOND:** Read `.ai/rules/no-stage-skipping-even-for-simple-tasks.md` (ENFORCEMENT)
3. Read `.ai/commands/implement-issue.md`
4. Execute stages in strict order: 1 → 2 → 3 → 3.5 → 4a → 4b → 4c → 5 → 6 → 7 → 8 → 9 → 10
5. Do NOT skip stages 6, 7, 8 — they are CRITICAL
6. Do NOT optimize away stages because task looks "simple"
7. For Stage 10: Ask user for approval before committing and creating PR

---

### Individual Stages

#### `only-issue-coordinator <payload>`

Execute:
1. Read `.ai/commands/only-issue-coordinator.md`

---

#### `only-grill-me <payload>`

Execute:
1. Read `.ai/commands/only-grill-me.md`

---

#### `only-planner <payload>`

Execute:
1. Read `.ai/commands/only-planner.md`

---

#### `only-constraint-validator <payload>`

Execute:
1. Read `.ai/commands/only-constraint-validator.md`

---

#### `only-implementer <payload>`

Execute:
1. Read `.ai/commands/only-implementer.md`

---

#### `only-tdd-test-author <payload>`

Execute:
1. Read `.ai/commands/only-tdd-test-author.md`

---

#### `only-tdd-implementer <payload>`

Execute:
1. Read `.ai/commands/only-tdd-implementer.md`

---

#### `only-tdd-refactorer <payload>`

Execute:
1. Read `.ai/commands/only-tdd-refactorer.md`

---

#### `only-reviewer <payload>`

Execute:
1. Read `.ai/commands/only-reviewer.md`

---

#### `only-test-author <payload>`

Execute:
1. Read `.ai/commands/only-test-author.md`

---

#### `only-test-verifier <payload>`

Execute:
1. Read `.ai/commands/only-test-verifier.md`

---

#### `only-release-manager <payload>`

Execute:
1. Read `.ai/commands/only-release-manager.md`

---

### Utilities

#### `only-glossary-update "<feature-description>"`

Execute:
1. Read `.ai/commands/only-glossary-update.md`

---

#### `only-from-prompt "<description>"`

Execute:
1. Read `.ai/commands/only-from-prompt.md`

---

#### `only-debug-rca <failed-artifact>`

Execute:
1. Read `.ai/commands/only-debug-rca.md`

---

#### `only-release-notes <payload>`

Execute:
1. Read `.ai/commands/only-release-notes.md`

---

#### `only-workflow-pattern-capture <payload>`

Execute:
1. Read `.ai/commands/only-workflow-pattern-capture.md`

---

### Maestro Testing

#### `write test-flow "<description>"`

Execute:
1. Read `.ai/commands/write-test-flow.md`

---

#### `check test-flow <test-name>`

Execute:
1. Read `.ai/commands/check-test-flow.md`

---

#### `check-simulators [ios|android]`

Execute:
1. Read `.ai/commands/check-simulators.md`

---

### Quick App Launch

#### `launch ios`

Execute:
1. Read `.ai/commands/launch-ios.md`

---

#### `launch android`

Execute:
1. Read `.ai/commands/launch-android.md`

---

### Help

#### `help`

Execute:
1. Read `.ai/commands/help.md`


Last updated: 2026-06-11

---
description: Stage 4b - Red-Green-Refactor cycle for each test
---

# Command: only-tdd-implementer

## Trigger

Tests are written and failing. Ready to implement code using TDD.

## Purpose

Execute TDD Implementer agent to run Red-Green-Refactor cycles.

## Flow

1. Read `.ai/INDEX.md`
2. Read `.ai/ORCHESTRATOR.md`
3. Load agent: `.ai/agents/tdd-implementer.md`
4. Load skill: `.ai/skills/red-green-refactor/SKILL.md`
5. Load rule: `.ai/rules/tdd-principles.md`
6. Invoke tdd-implementer agent with:
   - Failing test files
   - implementation-plan.md
7. Agent executes Red-Green-Refactor for each test:
   - RED: Confirm test fails
   - GREEN: Write minimal code to pass
   - REFACTOR: (skip mini refactor for now)
8. Repeat for 3 consecutive tests
9. Output: All 3 tests passing

## Expected Input

- test-<feature>.kt files (failing)
- implementation-plan.md
- Confirmation tests fail

## Expected Output

- Implementation code (Kotlin/Swift)
- All tests passing
- Code ready for Stage 4c (Major Refactor)

## Notes

Run 3 tests per batch, then invoke only-tdd-refactorer for major refactor.


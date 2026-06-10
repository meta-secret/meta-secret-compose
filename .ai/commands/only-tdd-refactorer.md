---
description: Stage 4c - Major refactoring after 3-5 red-green cycles
---

# Command: only-tdd-refactorer

## Trigger

3-5 red-green cycles completed, all tests passing. Ready for major refactor.

## Purpose

Execute TDD Refactorer agent to clean up code, extract utilities, improve documentation.

## Flow

1. Read `.ai/INDEX.md`
2. Read `.ai/ORCHESTRATOR.md`
3. Load agent: `.ai/agents/tdd-refactorer.md`
4. Load rule: `.ai/rules/tdd-principles.md`
5. Invoke tdd-refactorer agent with:
   - Implementation code (from red-green cycles)
   - All passing tests
   - implementation-plan.md
6. Agent refactors:
   - Extract duplication
   - Improve naming
   - Add documentation
   - Organize code
7. Run full test suite: confirm all pass
8. Output refactored code ready for review

## Expected Input

- Implementation code (passing all tests)
- Test files
- implementation-plan.md

## Expected Output

- Refactored, clean code
- All tests passing
- Documentation added
- Ready for Stage 5 (Code Review)

## Notes

Refactor ONLY when all tests pass.
All tests must pass after refactoring.


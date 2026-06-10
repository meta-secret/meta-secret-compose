---
description: Stage 4a - Write failing tests that drive implementation
---

# Command: only-tdd-test-author

## Trigger

User wants to write tests for a feature before implementation.

## Purpose

Execute TDD Test Author agent to create failing test cases from implementation plan.

## Flow

1. Read `.ai/INDEX.md`
2. Read `.ai/ORCHESTRATOR.md`
3. Load agent: `.ai/agents/tdd-test-author.md`
4. Load skill: `.ai/skills/test-driven-development/SKILL.md`
5. Load rule: `.ai/rules/tdd-principles.md`
6. Invoke tdd-test-author agent with:
   - clarification-report.md
   - implementation-plan.md
7. Agent writes test files (test-<feature>.kt or .swift)
8. Verify tests fail (as expected)
9. Output test failure report

## Expected Input

- clarification-report.md (from Stage 2)
- implementation-plan.md (from Stage 3)

## Expected Output

- test-<feature>.kt files (Kotlin) or .swift (iOS)
- Test failure report showing each test fails
- Ready for Stage 4b (TDD Implementer)


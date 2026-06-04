# Command — Implementer

## Trigger

```
only-implementer <payload>
```

## Purpose

Implement code changes based on approved plan.

## Flow

Executes **code-implementer** agent:
- Reads implementation plan
- Implements code changes in Logic and UI
- May delegate to **logic-implementer** and **ui-implementer** agents
- Writes changes to source files

## Expected Input

- `implementation-plan.md` artifact

## Output

- Modified source files
- Implementation summary artifact

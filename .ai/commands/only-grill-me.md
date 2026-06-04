# Command — Grill Me

## Trigger

```
only-grill-me <payload>
```

## Purpose

Conduct deep clarification session. Ask hard questions about constraints, boundary cases, error handling, and dependencies until complete mutual understanding is achieved.

## Flow

Executes **requirements-clarifier** agent:
- Reviews issue analysis
- Asks probing questions
- Explores constraints and boundaries
- Identifies error scenarios
- Maps dependencies
- Gets user approval

## Expected Input

- `issue-analysis.md` artifact

## Output

- `clarification-report.md` artifact with:
  - Constraint catalog
  - Boundary cases
  - Error handling strategy
  - Dependency map
  - User sign-off

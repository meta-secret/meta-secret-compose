# Command — Planner

## Trigger

```
only-planner <payload>
```

## Purpose

Create detailed implementation plan from issue analysis.

## Flow

Executes **feature-planner** agent:
- Reads issue analysis artifact
- Reads design analysis (if Figma present)
- Creates file-level execution plan
- Documents risks and edge cases
- Aligns with architecture rules

## Expected Input

- `issue-analysis.md` artifact
- `design-analysis.md` artifact (if Figma)

## Output

- `implementation-plan.md` artifact with detailed steps and considerations

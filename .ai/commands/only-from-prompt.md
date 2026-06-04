# Command — From Prompt

## Trigger

```
only-from-prompt "<description>"
```

## Purpose

Start workflow from manual feature/bug description without GitHub issue.

## Flow

1. Apply **workflow-manual-task-brief** skill
2. Generate task brief from description
3. Get user approval
4. Execute **feature-planner** with approved brief
5. Continue with normal workflow

## Expected Input

- Free-text task description (e.g., "Fix crash when opening vault on Android")

## Output

- Task brief artifact
- Implementation plan artifact

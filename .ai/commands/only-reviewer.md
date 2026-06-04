# Command — Reviewer

## Trigger

```
only-reviewer <payload>
```

## Purpose

Review implementation for architecture, style, and best practices.

## Flow

Executes **code-reviewer** agent:
- Reviews code changes
- Checks against architecture rules
- Checks against KMP best practices
- Provides feedback and recommendations

## Expected Input

- Implementation changes (staged or committed)

## Output

- Code review report with findings and recommendations

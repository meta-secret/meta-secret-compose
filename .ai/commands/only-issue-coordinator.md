# Command — Issue Coordinator

## Trigger

```
only-issue-coordinator <payload>
```

## Purpose

Analyze GitHub issue or task description. Detect and extract Figma design links if present.

## Flow

Executes **github-issue-coordinator** agent:
- Reads GitHub issue (or free-text task)
- Extracts: problem, goals, requirements, assumptions
- Detects Figma links
- Produces issue analysis artifact

## Expected Input

- GitHub issue number (e.g., `#42`)
- GitHub issue URL
- Free-text task description

## Output

- `issue-analysis.md` artifact with structured issue summary
- Figma present: YES/NO indicator

# Command — Debug RCA

## Trigger

```
only-debug-rca <payload>
```

## Purpose

Analyze failed artifacts and perform root-cause analysis.

## Flow

Executes **debug-rca** agent:
- Analyzes failure logs/stack traces
- Identifies root cause
- Proposes diagnostic steps
- Suggests fixes

## Expected Input

- Failed artifact or error logs
- Stack trace or failure description

## Output

- RCA analysis artifact with root cause and recommendations

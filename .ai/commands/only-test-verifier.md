# Command — Test Verifier

## Trigger

```
only-test-verifier <payload>
```

## Purpose

Execute and verify test suite.

## Flow

Executes **test-verifier** agent:
- Runs Gradle/KMP test tasks
- Collects test results
- Reports pass/fail status
- Identifies failing tests

## Expected Input

- Optional: test scope/module to run

## Output

- Test execution report with results

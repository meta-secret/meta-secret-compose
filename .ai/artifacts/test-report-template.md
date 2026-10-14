# Stage 9: Test Run Report

**Status:** Success | Failed | Skipped

---

## Test Execution

Command: `./gradlew test --no-daemon --parallel --console=plain`

---

## Test Results

- **Total tests:** [number]
- **Passed:** [number] ✅
- **Failed:** [number] ❌
- **Skipped:** [number] ⏭️
- **Coverage:** [%]

---

## Failed Tests

If any tests failed:

### Test 1: [test name]
- **Module:** [name]
- **Failure reason:** [assertion that failed]
- **Stack trace:** [first 5 lines]
- **Fix required:** [what to do]

---

## Pass/Fail

- **Status:** PASSED ✅ / FAILED ❌
- **valid coverage report; 80% advisory goal:** YES / NO
- **Ready for Stage 10:** YES / NO

If failed:
- Root cause: [analysis]
- Escalate to debug-rca: YES / NO

Coverage policy: [mobile coverage policy](../rules/coverage-policy.md). Report validation and passing tests are mandatory; the overall 80% goal is advisory.

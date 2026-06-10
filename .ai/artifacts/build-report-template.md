# Stage 5: Build Report

**Status:** Success | Failed | Skipped

---

## Build Command

```
./gradlew build -x test --no-daemon --parallel --console=plain
```

---

## Build Result

- **Status:** ✅ SUCCESS / ❌ FAILED
- **Duration:** [seconds]
- **Start time:** [timestamp]
- **End time:** [timestamp]

---

## Build Output

If successful:
- All modules compiled
- No warnings/errors

If failed:
- **Error message:** [exact error from gradle]
- **Root cause:** [analysis]
- **Affected module:** [name]
- **Solution:** [how to fix]

---

## Retry Status

- Retry #: [1 or 2]
- Ready for Stage 6: YES / NO

If failed after 2 retries:
- Escalate to `only-debug-rca`

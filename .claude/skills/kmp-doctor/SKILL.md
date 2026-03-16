---
name: kmp-doctor
description: Diagnose and repair Kotlin Multiplatform + Swift build failures. Always plan first, wait for confirmation, then apply minimal fixes.
---

# KMP Doctor

You are the build doctor for this Kotlin Multiplatform + Swift project.

Your job is to:
1. run the most appropriate build command,
2. collect build errors,
3. classify the failure,
4. produce a repair plan,
5. wait for confirmation,
6. apply the minimal fix,
7. rebuild and verify.

## Absolute rules
- Never modify code before presenting a plan.
- Prefer the narrowest possible build command first.
- Prefer minimal repair over refactor.
- If the failure is ambiguous, ask for clarification instead of guessing.
- Before proposing a fix, use systematic root-cause analysis.
- Do not jump directly from symptom to patch.

## Read these files before acting
- .claude/skills/kmp-doctor/project-context.md
- .claude/skills/kmp-doctor/build-commands.md
- .claude/skills/kmp-doctor/error-categories.md
- .claude/skills/kmp-doctor/launch-policy.md
- .claude/skills/kmp-doctor/runtime-error-categories.md
- .claude/skills/architecture-guardian/solid-rules.md
- .claude/skills/architecture-guardian/layer-rules.md
- .claude/skills/systematic-debugging/root-cause-framework.md
- CLAUDE.md

## Workflow

### Phase 1 — Diagnose
1. Determine which layer is failing:
    - Gradle/KMP config
    - shared Kotlin code
    - Swift/iOS integration
    - environment / generated artifacts
2. Run the narrowest build command first.
3. Read the error output carefully.
4. Summarize:
    - primary failure
    - secondary failures
    - likely root cause
    - confidence level

### Phase 2 — Plan
Respond using exactly this structure:

## Build Summary
- Build command:
- Failed step:
- Primary error:
- Secondary errors:

## Diagnosis
- Category:
- Likely root cause:
- Confidence: low / medium / high

## Root Cause Analysis
- Symptoms:
- Observations:
- Most likely root cause:
- Confidence: low / medium / high

## Repair Plan
1.
2.
3.

## Files Likely To Change
- path/to/file
- path/to/file

## Risk
- low / medium / high

## Waiting
Say: "Waiting for approval before making changes."

## Architecture Impact
- Correct layer for the fix:
- Existing abstraction to use:
- New abstraction needed: yes / no
- Architecture risk: low / medium / high

Do not edit any file in this phase.

### Phase 3 — Apply
Only after explicit user approval:
- apply the smallest viable patch
- explain each file change briefly
- rerun the relevant build command
- report whether the issue is fixed

### Phase 4 — Verify
After applying changes, respond using this structure:

## Changes Applied
- file:
- file:

## Verification
- Build command:
- Result: success / failed

## Remaining Issues
- none
  or
- list of remaining errors

## Next Recommendation
- one small next step only

### Phase 5 — Runtime Gate
Build success is not sufficient for completion.

After a successful build repair:
1. determine whether runtime verification is required
2. for iOS, assume physical-device-only verification
3. ask the user to launch the app on a real iPhone and provide any runtime/crash logs
4. for Android, emulator or device verification is acceptable
5. if runtime is not yet verified, do not declare the issue fully resolved

Use this response structure:

## Runtime Verification Status
- Build status: success / failed
- iOS runtime verified: yes / no / pending
- Android runtime verified: yes / no / pending

## Next Required Step
- one concrete verification step only

If iOS launch fails on a physical device, recommend using `/ios-device-doctor`.

## Completion policy
Do not say the issue is fully solved if:
- build passes
- but iOS device launch has not been verified

Instead say:
- build repaired
- waiting for physical-device runtime verification

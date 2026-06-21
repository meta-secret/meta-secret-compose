# Rule: No Stage Skipping — Even for Simple Tasks

**CRITICAL ENFORCEMENT RULE**

---

## ⚠️ MANDATORY: Execute ALL 11 stages for EVERY task

**Even if:**
- Task requires only 1 line of code changes
- Task is "trivial" or "simple"
- Task is a bug fix with obvious solution
- Task only touches one file
- Task seems to require no testing

**Execute ALL 11 stages in strict order:**

```
Stage 1 → Stage 2 → Stage 3 → Stage 3.5 → Stage 4a → Stage 4b → Stage 4c → 
Stage 5 → Stage 6 → Stage 7 → Stage 8 → Stage 9 → Stage 10
```

---

## ❌ DO NOT Skip These Stages

### Stage 2 (Requirements Clarifier)
- **MUST execute** even for "obvious" tasks
- "Obvious" to you might be wrong
- Clarification can catch hidden assumptions
- Always grill user with questions

### Stage 3 (Feature Planner)
- **MUST execute** even for 1-line fixes
- Plan ensures architectural alignment
- Plan documents the decision
- Shortcut only leads to bugs later

### Stage 3.5 (Constraint Validator)
- **MUST execute** even for small changes
- Constraints are not "optional suggestions"
- One-line change can violate architectural rules
- Gate must not be skipped

### Stage 4a (TDD Test Author)
- **MUST execute** even if "change is obvious"
- Write failing tests FIRST
- Tests define expected behavior
- No exceptions

### Stage 4b (TDD Implementer)
- **MUST execute** even for simple fixes
- Red-Green-Refactor is mandatory
- Not "optional for small changes"
- 1-line change still needs RED → GREEN cycle

### Stage 4c (TDD Refactorer)
- **MUST execute** after 3-5 cycles
- Even 1-line changes might have refactoring opportunities
- Clean code is not "optional for simple tasks"

### Stage 5 (Build)
- **MUST execute** always
- Compilation is non-negotiable
- Even "obvious" changes can break builds

### Stage 6 (Code Review)
- **MANDATORY** even for trivial changes
- Coverage check is CRITICAL
- 1-line change must still meet 80% coverage requirement
- Constraints re-check is not "optional"

### Stage 7 (Design Review)
- **MUST execute** if Figma exists
- Or explicitly mark "Skipped" if no Figma
- Not: "Skip because change is simple"

### Stage 8 (Coverage Verification)
- **MANDATORY** even for bug fixes
- Coverage >= 80% is non-negotiable
- No exceptions for "trivial" changes
- Must run `./gradlew koverReport`

### Stage 9 (Test Run)
- **MUST execute** always
- All tests must pass
- Not "optional for 1-line changes"

### Stage 10 (User Approval)
- **MANDATORY** gate before PR
- Must ask user: "Should we proceed to Stage 10?"
- Never auto-commit or auto-create PR
- User approval required 100% of the time

---

## Why All Stages for All Tasks?

1. **Quality assurance** — Stages catch bugs early
2. **Consistency** — Same process prevents skips
3. **Architectural compliance** — Constraints apply to all changes
4. **Test coverage** — All code needs tests
5. **Code review** — All code needs review
6. **Audit trail** — All stages create artifacts

---

## Examples: Tasks That Look "Simple" But Need All Stages

### Example 1: Fix one line in EmailConfirmationScreen.kt
```kotlin
// Before
navigator?.popUntilRoot()

// After
navigator?.popUntilRoot()
navigator?.push(SignInScreen())
```

**This STILL needs:**
- ✅ Stage 2: Grill user — "Is this the only change needed?"
- ✅ Stage 3: Plan — "How does this fit architecture?"
- ✅ Stage 3.5: Validate — "Violates any constraints?"
- ✅ Stage 4a: Test — "What tests cover this?"
- ✅ Stage 4b: Implement — "Red-Green cycle"
- ✅ Stage 4c: Refactor — "Better way to do this?"
- ✅ Stage 5: Build — "Compiles?"
- ✅ Stage 6: Review — "Coverage OK? Constraints OK?"
- ✅ Stage 7: Design — "UI still matches Figma?"
- ✅ Stage 8: Coverage — ">= 80%?"
- ✅ Stage 9: Tests — "All pass?"
- ✅ Stage 10: Approval — "User approves?"

### Example 2: Button color change
```kotlin
// Before
Button(backgroundColor = Color.Blue)

// After
Button(backgroundColor = Color.Red)
```

**This STILL needs all 11 stages** because:
- Color might violate design constraints
- Need to verify design
- Need test coverage
- Need code review
- Need user approval

### Example 3: Typo fix
```kotlin
// Before
println("Emai confirmation")

// After
println("Email confirmation")
```

**This STILL needs all 11 stages** (though will be quick):
- Stage 2: "Is this the only typo?"
- Stage 3: "Any other places this typo appears?"
- etc.

---

## Red Flags: You're About to Skip Stages If You Think...

- ❌ "This is so simple, I'll skip Stage 2"
- ❌ "1 line of code, don't need Stage 3"
- ❌ "It's obvious, can skip Stage 4a (tests)"
- ❌ "No point in Stage 6 (review) for this"
- ❌ "Coverage check is overkill for small changes"
- ❌ "Can auto-commit without Stage 10 (approval)"

**ALL of these are WRONG. Execute ALL stages.**

---

## How to Handle Simple vs. Complex Tasks

**Simple task:** All 11 stages execute quickly
- Stage 2: 2 minutes (quick clarification)
- Stage 3: 5 minutes (simple plan)
- Stage 4a-4c: 10 minutes (trivial tests + refactoring)
- Stage 5-10: 15 minutes (build, review, coverage)

**Total: ~35 minutes for "simple" task through all stages**

**Complex task:** All 11 stages take longer
- Stage 2: 30 minutes (deep clarification)
- Stage 3: 45 minutes (complex plan)
- Stage 4a-4c: 120 minutes (extensive TDD cycles)
- Stage 5-10: 60 minutes (build, review, coverage, tests)

**Total: ~4+ hours for complex task**

**Both execute ALL stages. Difference is duration, not skipping.**

---

## Enforcement: Check Artifacts

After any `implement issue <task>` execution, verify:

```
.ai/artifacts/run/MS-<id>-001-*.md       ← Stage 1
.ai/artifacts/run/MS-<id>-002-*.md       ← Stage 2
.ai/artifacts/run/MS-<id>-003-*.md       ← Stage 3
.ai/artifacts/run/MS-<id>-0035-*.md      ← Stage 3.5
.ai/artifacts/run/MS-<id>-004a-*.md      ← Stage 4a
.ai/artifacts/run/MS-<id>-004b-*.md      ← Stage 4b
.ai/artifacts/run/MS-<id>-004c-*.md      ← Stage 4c
.ai/artifacts/run/MS-<id>-005-*.md       ← Stage 5
.ai/artifacts/run/MS-<id>-006-*.md       ← Stage 6 (CRITICAL - must exist)
.ai/artifacts/run/MS-<id>-007-*.md       ← Stage 7
.ai/artifacts/run/MS-<id>-008-*.md       ← Stage 8 (CRITICAL - must exist)
.ai/artifacts/run/MS-<id>-009-*.md       ← Stage 9
.ai/artifacts/run/MS-<id>-010-*.md       ← Stage 10
```

**If any artifact is missing → workflow is INCOMPLETE → FAIL**

---

## For Claude/Codex Implementers

When executing `implement issue`:

1. **DO NOT judge task complexity** — Execute all stages regardless
2. **DO NOT skip "obvious" stages** — All stages are required
3. **DO NOT optimize away stages** — Use the time, don't skip
4. **DO NOT assume** — Always run Stages 2, 3, 3.5 for questions
5. **DO verify all artifacts exist** — Check `.ai/artifacts/run/` folder
6. **DO ask user at Stage 10** — No auto-commit for ANY task

---

**Status:** This is a hard rule. No exceptions.

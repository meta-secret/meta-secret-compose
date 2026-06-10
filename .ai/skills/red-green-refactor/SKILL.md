# Skill: Red-Green-Refactor Cycle

## Overview

Execute one complete Red-Green-Refactor cycle: write failing test, implement minimal code, refactor and improve. This is the core unit of TDD work.

## When to Use

For each test in TDD stage:
- Test Author writes failing test
- Implementer executes Red-Green-Refactor using this skill

## The Cycle (Detailed Steps)

### 1. RED — Test Must Fail First ✋

**Prerequisite:** Test already written by Test Author

**Steps:**
1. Read test code
2. Run tests: ./gradlew commonTest
3. Confirm test FAILS
4. Reason: feature doesn't exist yet
5. Record failure message

**Example failure:**
```
FAILED: testValidateEmailWithValidEmail
Error: class EmailValidator not found
```

### 2. GREEN — Minimal Code to Pass ✅

**Goal:** Make ONLY this test pass. Nothing more.

**Steps:**
1. Create class/function mentioned in test
2. Implement MINIMAL logic to make test pass
3. Resist urge to add "better" code
4. No extra features
5. Run test: ./gradlew commonTest
6. Confirm test PASSES

**Example:**

❌ WRONG (over-engineered):
```kotlin
class EmailValidator {
    fun isValid(email: String): Boolean {
        val emailRegex = Regex(...)  // Complex regex
        return email.matches(emailRegex)
    }
}
```

✅ RIGHT (minimal):
```kotlin
class EmailValidator {
    fun isValid(email: String): Boolean {
        return email.contains("@")  // Test only needs this
    }
}
```

### 3. REFACTOR — Improve Code Quality

**Prerequisite:** Test PASSES

**Steps:**
1. All tests still pass? Run: ./gradlew commonTest
2. Look for: duplication, poor naming, missing comments
3. Improve code
4. Run tests again: confirm all still pass

---

Last updated: 2026-06-05

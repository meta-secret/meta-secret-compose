# Skill: Test-Driven Development (TDD)

## Overview

Implement features using Test-Driven Development (TDD) methodology. Write test first, implement minimal code, refactor iteratively. This discipline ensures code quality, maintainability, and comprehensive test coverage.

## When to Use

When:
- Writing new features (required for all features)
- Fixing bugs (write test that catches bug, then fix)
- Refactoring existing code (ensure tests pass before and after)

## The TDD Cycle (Red-Green-Refactor)

### Phase 1: RED — Write Failing Test
1. Understand requirement
2. Write test that describes desired behavior
3. Test must fail (because feature doesn't exist)
4. Commit test to demonstrate it fails

**Example:**
```kotlin
// Test for email validation
@Test
fun testValidateEmailWithValidAddress() {
    val validator = EmailValidator()
    val result = validator.isValid("user@example.com")
    assertTrue(result)
}
// Test FAILS because EmailValidator doesn't exist yet
```

### Phase 2: GREEN — Write Minimal Code
1. Write simplest code to make test pass
2. No optimization, no extra features
3. Test should pass
4. Code quality is secondary now

**Example:**
```kotlin
// Minimal implementation
class EmailValidator {
    fun isValid(email: String): Boolean {
        return email.contains("@") && email.contains(".")
    }
}
// Test PASSES
```

### Phase 3: REFACTOR — Improve Quality
1. All tests still pass
2. Improve code clarity
3. Extract reusable functions
4. Improve naming
5. Add documentation

**Example:**
```kotlin
// Refactored
class EmailValidator {
    fun isValid(email: String): Boolean {
        val pattern = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        return email.matches(pattern)
    }
}
```

## Workflow Integration

### Stage 4: TDD Implementation

```
Input: 
  - clarification-report.md (requirements)
  - implementation-plan.md (feature breakdown)

Process:
  Loop for each feature/function:
    1. Test Author: Write failing test
    2. Implementer: Red-Green-Refactor cycle
       └─ Repeat: test → minimal code → refactor
    3. Every 3 cycles: Major Refactor Agent
       └─ Full code review, optimize, documentation
    4. Code Review Agent: Final validation + 80% coverage check

Output:
  - All tests passing
  - Code refactored and clean
  - 80%+ coverage report
  - Ready for next stage
```

## Key Rules

### 1. One Test → One Feature
- Each test validates ONE behavior
- Test name describes behavior: `testEmailValidationWithValidEmail`

### 2. Minimal Code Only
- Write ONLY code to make test pass
- No premature optimization
- No speculative features
- If test doesn't ask for it, don't code it

### 3. All Tests Pass
- Every step: full test suite passes
- No skipping tests
- No "works locally" excuses

### 4. Refactor When Safe
- All tests pass before refactoring
- All tests pass after refactoring
- Refactoring = behavior unchanged

### 5. 80% Coverage Minimum
- Business logic: 90%+
- UI rendering: may be lower
- Generated code: may be lower

## Common Pitfalls

❌ **Writing implementation first, test second**
- This defeats TDD. Test must come first.

❌ **Test is too broad**
- "Test everything" in one test
- Break into smaller tests

❌ **Ignoring red phase**
- Confirming test fails first is critical
- Ensures test is actually testing something

❌ **Over-engineering minimal code**
- Resist adding "probably needed" code
- Add only what test requires

❌ **Refactoring without safety net**
- Refactor ONLY when all tests pass
- Never refactor without tests

## Tools & Setup

### For Kotlin (commonTest)
```kotlin
import kotlin.test.*

class MyTest {
    @Test
    fun testBehavior() {
        // Arrange
        val system = MySystem()
        
        // Act
        val result = system.doSomething()
        
        // Assert
        assertEquals(expected, result)
    }
}
```

Run:
```bash
./gradlew commonTest
./gradlew koverReport  # Coverage report
```

### For Swift (iosApp)
```swift
import XCTest

class MyTest: XCTestCase {
    func testBehavior() {
        // Arrange
        let system = MySystem()
        
        // Act
        let result = system.doSomething()
        
        // Assert
        XCTAssertEqual(expected, result)
    }
}
```

Run in Xcode:
```
Product → Test (Cmd+U)
```

## Example: Complete TDD Cycle

**Feature:** Validate vault password (min 8 chars, 1 number, 1 uppercase)

### Cycle 1: Red
```kotlin
@Test
fun testPasswordValidationWithValidPassword() {
    val validator = PasswordValidator()
    assertTrue(validator.isValid("MyPassword123"))
}
// FAILS: PasswordValidator doesn't exist
```

### Cycle 1: Green
```kotlin
class PasswordValidator {
    fun isValid(password: String): Boolean {
        return true  // Minimal: make test pass
    }
}
// PASSES (but obviously wrong)
```

### Cycle 1: Refactor
(Skip for now, continue testing)

### Cycle 2: Red
```kotlin
@Test
fun testPasswordValidationWithShortPassword() {
    val validator = PasswordValidator()
    assertFalse(validator.isValid("short"))
}
// FAILS: Still returns true for everything
```

### Cycle 2: Green
```kotlin
class PasswordValidator {
    fun isValid(password: String): Boolean {
        return password.length >= 8
    }
}
// PASSES both tests
```

### Cycle 3: Red
```kotlin
@Test
fun testPasswordValidationWithoutNumber() {
    val validator = PasswordValidator()
    assertFalse(validator.isValid("NoNumbers"))
}
// FAILS
```

### Cycle 3: Green
```kotlin
class PasswordValidator {
    fun isValid(password: String): Boolean {
        if (password.length < 8) return false
        return password.any { it.isDigit() }
    }
}
// PASSES all 3 tests
```

### Cycle 4: Red & Green & Refactor
(Uppercase requirement - follow same pattern)

### After 3-5 Cycles: MAJOR REFACTOR

```kotlin
// Clean, well-structured, documented
class PasswordValidator {
    companion object {
        private const val MIN_LENGTH = 8
    }
    
    fun isValid(password: String): Boolean {
        return hasMinLength(password) && 
               hasDigit(password) && 
               hasUppercase(password)
    }
    
    private fun hasMinLength(password: String) = password.length >= MIN_LENGTH
    private fun hasDigit(password: String) = password.any { it.isDigit() }
    private fun hasUppercase(password: String) = password.any { it.isUpperCase() }
}
```

---

**Benefits of TDD:**
- ✅ Code is tested from day 1
- ✅ Tests serve as documentation
- ✅ Refactoring is safe
- ✅ Bugs caught early
- ✅ Design improves iteratively
- ✅ 80%+ coverage guaranteed

Last updated: 2026-06-05

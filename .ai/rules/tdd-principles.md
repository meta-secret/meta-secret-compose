# TDD Principles for meta-secret-compose

## Core Principles

**Red-Green-Refactor Cycle:**
1. **Red** — Write a failing test first
2. **Green** — Write minimal code to make test pass
3. **Refactor** — Improve code quality without changing behavior

## Minimal Implementation Definition

"Minimal" means:
- ✅ Exactly what the test requires (no more)
- ✅ No premature optimization
- ✅ No extra features that aren't tested
- ✅ Simple, direct solutions

Examples of minimal:
```kotlin
// Test: should add two numbers
fun testAdd() {
    assertEquals(5, add(2, 3))
}

// Minimal implementation (NOT this):
fun add(a: Int, b: Int): Int {
    return a + b
}

// NOT this (premature optimization):
fun add(vararg numbers: Int): Int = numbers.sum() // Test didn't ask for varargs!
```

## Refactoring Rules

**When to refactor?**
1. After every test (if code quality issue detected)
2. After 3-5 red-green cycles (major refactor stage)
3. Before moving to next feature area

**What to refactor?**
- Extract duplicated code
- Improve naming
- Extract utility functions
- Add documentation
- Optimize performance (IF tested)

**Refactor safety rules:**
- ✅ All tests must still pass after refactoring
- ✅ No behavior changes
- ✅ Run full test suite before moving on

## Test Organization

### Test Structure (Kotlin Test)
```kotlin
class UserValidatorTest {
    
    @Test
    fun testValidateEmailWithValidEmail() {
        // Arrange
        val validator = UserValidator()
        val email = "user@example.com"
        
        // Act
        val result = validator.validateEmail(email)
        
        // Assert
        assertTrue(result)
    }
    
    @Test
    fun testValidateEmailWithInvalidEmail() {
        val validator = UserValidator()
        val email = "invalid-email"
        
        val result = validator.validateEmail(email)
        
        assertFalse(result)
    }
}
```

### Test Naming Convention
- `test<Function><Scenario>` — e.g., `testValidateEmailWithValidEmail`
- One assertion per test (preferred)
- Test should be self-documenting

## Test Coverage Requirements

- **Minimum:** 80% code coverage
- **Target:** 90%+ for business logic
- **Exception:** Generated code, UI rendering (may be lower)

**Coverage calculation:**
```
coverage = (lines tested / total lines) * 100
```

Tool: Use `./gradlew koverReport` (or equivalent)

## Batch Strategy (Token/Context Optimization)

**Group Red-Green cycles into batches:**
1. TDD Test Author writes 1 failing test
2. TDD Implementer runs red-green-refactor on that test (1 cycle)
3. Repeat steps 1-2 for 3 consecutive tests
4. Major Refactor Agent improves entire code block
5. Move to next feature area

**This balances:**
- ✅ Quality (tests drive implementation)
- ✅ Token efficiency (batch refactoring)
- ✅ Context preservation (fewer agent switches)

## Swift (iOS) Test Guidelines

XCTest framework:
```swift
import XCTest

final class UserValidatorTests: XCTestCase {
    
    var validator: UserValidator!
    
    override func setUp() {
        super.setUp()
        validator = UserValidator()
    }
    
    func testValidateEmailWithValidEmail() throws {
        let result = validator.validateEmail("user@example.com")
        XCTAssertTrue(result)
    }
}
```

Same principles apply: Red-Green-Refactor, minimal code, 80%+ coverage.

## Integration with KMM

**Shared tests (commonTest):**
- Pure Kotlin logic
- Use `kotlin("test")` framework
- Platform-agnostic validation

**Platform-specific tests:**
- androidTest: Android-specific + Compose previews
- iosTest: Swift + XCTest

**FFI Testing:**
- Rust function calls via UniFFI should be tested in isolation
- Mock or stub Rust calls when possible
- Integration tests verify full round-trip

## CI/CD Integration

Tests run automatically on:
- Every commit (pre-submit)
- Before merging to main
- Coverage must be 80%+

Failure = block merge

---

Last updated: 2026-06-05

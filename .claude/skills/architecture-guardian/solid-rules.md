# SOLID Rules

## Single Responsibility
A type must have one reason to change.
Do not mix:
- UI rendering
- navigation
- business decisions
- persistence
- networking
- native bridge logic

## Open / Closed
Prefer:
- new use-cases
- focused adapters
- new implementations behind interfaces
  Instead of modifying unrelated code paths.

## Liskov Substitution
Implementations must preserve interface contracts.
Do not create implementations that:
- behave inconsistently
- return incompatible states
- introduce hidden side effects outside the interface expectation

## Interface Segregation
Prefer small interfaces.
Do not create large “manager” interfaces combining unrelated capabilities.

## Dependency Inversion
Views, ViewModels, and platform glue must depend on abstractions from core.
Do not depend directly on concrete implementations when an abstraction should exist.
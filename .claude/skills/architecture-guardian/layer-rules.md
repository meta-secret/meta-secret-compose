# Layer Rules

## UI layer
Location:
- ui/scenes/
- iOS UIKit / Swift proxy integration points

Rules:
- no business logic
- no FFI
- no direct persistence
- no direct networking unless explicitly designed as a UI adapter
- UI should forward user intent and render state only

## ViewModel layer
Rules:
- orchestrates state and user events
- may use use-cases / services / resolvers
- must not contain platform glue or FFI calls
- should not become a god object

## Core layer
Location:
- commonMain/core
- domain/use-cases/interfaces/models

Rules:
- owns domain logic
- defines interfaces
- contains platform-agnostic behavior
- no Android/iOS-specific framework details

## Platform adapters
Location:
- androidMain
- iosMain

Rules:
- implement core ports
- contain platform-specific logic only
- must not leak upward into core abstractions

## FFI boundary
Rules:
- MetaSecretCoreInterface is the only interface allowed to call FFI
- only the approved façade path may use it
- all FFI calls must stay off the main thread as defined in project rules

## Mapping boundary
Rules:
- apiModels must be mapped into internal/app domain models
- UI must not work directly with raw native/Rust-facing payloads unless explicitly required
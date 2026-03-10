# Project Context

## Scope
This project is Kotlin Multiplatform plus Swift iOS integration.

## In scope
- Gradle build logic
- KMP source sets
- shared Kotlin code
- Swift integration code
- generated framework usage issues
- import mismatches
- API usage mismatches between shared and iOS

## Out of scope
- external native libraries
- infra unrelated to the current build error
- signing / provisioning / Apple account settings

## Repair style
- Minimal fix first
- No architecture rewrite
- No broad dependency churn
- No mass formatting changes

## Preferred behavior
- Diagnose first
- Plan second
- Wait for approval
- Repair third
- Verify fourth
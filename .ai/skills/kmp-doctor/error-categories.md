# Error Categories

## 1. Gradle / KMP Config
Examples:
- sourceSets misconfiguration
- target mismatch
- dependency resolution issues
- plugin configuration issues
- cocoapods block issues

## 2. Shared Kotlin Code
Examples:
- compilation errors in commonMain / iosMain
- expect/actual mismatch
- API rename fallout
- coroutine / serialization compile issues

## 3. Swift / iOS Integration
Examples:
- framework import issues
- symbol not found
- signature mismatch after shared Kotlin API changed
- nullability mismatch
- Swift call site outdated

## 4. Environment / Generated Artifacts
Examples:
- stale generated framework
- cache / derived artifact issue
- packaging mismatch

## Classification rule
Pick one primary category first.
Do not mix categories unless there is hard evidence.
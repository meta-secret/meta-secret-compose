# Runtime Error Categories

## 1. Startup Crash
Examples:
- app crashes immediately on launch
- fatal error on app initialization
- framework load failure
- missing symbol at runtime

## 2. KMP / Swift Interop Runtime Issue
Examples:
- wrong nullability assumptions
- Swift calls outdated generated API
- Kotlin/Native bridge mismatch
- runtime serialization mismatch
- incorrect platform-specific implementation behavior

## 3. App Configuration / Environment Issue
Examples:
- plist/config mismatch
- missing runtime resource
- entitlement-related runtime failure
- initialization order/configuration issue

## 4. First Screen / Navigation Failure
Examples:
- blank screen
- initial view model crash
- startup navigation fails
- state initialization fails

## 5. Android Runtime Issue
Examples:
- startup crash on emulator/device
- manifest/config issue
- DI/container initialization crash
- runtime permissions issue

## Classification Rule
- classify one primary runtime category first
- do not mix build failure and runtime failure
- do not assume build success means app launch success
# Test Plan

## 1. Purpose

The purpose of this test plan is to ensure that the application is stable, secure, and behaves consistently across all supported platforms.

The main focus of testing is:
- secure storage of sensitive data
- correct data management across multiple devices
- no use of a master key at any stage
- correct handling of biometric and local authentication data
- data integrity during failure scenarios
- predictable and consistent behavior across iOS, Android, and Desktop platforms

---

## 2. Scope of Testing

### In Scope

- Client application built with Compose Multiplatform
- Splash screen and local authentication (biometrics / PIN)
- Local encrypted storage and backup mechanisms
- Secure synchronization, storage, and restoration of sensitive data across multiple devices
- Application behavior under offline and error conditions
- Cross-platform consistency across iOS, Android, and Desktop platforms

### Out of Scope

- UI animations and visual effects
- Performance benchmarking on low-end devices
- Jailbreak / rooted device scenarios (at this stage)
- Third-party service availability

---

## 3. Test Objects

The following components are subject to testing:

- Splash screen and local authentication flow
- User onboarding flow
- Authentication and access control
- Secure storage and backup mechanisms
- Establishment of a secure network across user devices
- Data synchronization logic within the device network
- Error handling and recovery mechanisms

---

## 4. Test Types

The following types of testing will be performed:

- Manual functional testing
- Security-focused testing
- Offline and failure scenario testing
- Regression testing (smoke tests before each release)

The following automated tests are planned or partially implemented:

- Unit tests for core business logic
- Integration tests for storage, encryption, and synchronization layers

---

## 5. Test Environment

### Platforms

- iOS (Latest Supported Version and LSV - 2)
- Android (Latest Supported Version and LSV - 2)
- Desktop (macOS / Windows, if applicable)

### Environment Conditions

- Clean application installation
- Production build configuration
- Stable and unstable network conditions
- Device restarts and application restarts

---

## 6. Risk Areas and Critical Scenarios

The following areas are considered high risk and must be thoroughly tested:

1. Initial installation on the first device and creation of an encrypted database backup
2. Complete application removal and reinstallation with full data and state restoration when a single device is present in the device network
3. Initial installation on a second device, joining the existing device network, and creation of an encrypted database backup
4. Redistribution of existing sensitive data across a newly formed two-device network
5. Distribution of newly created sensitive data across the two-device network
6. Access and viewing of sensitive data within a two-device network
7. Application removal on any device followed by reinstallation and data resynchronization within a two-device network
8. Initial installation on a third device, joining the existing device network, and creation of an encrypted database backup
9. Redistribution of existing sensitive data across an expanded three-device network
10. Distribution of newly created sensitive data across the expanded three-device network
11. Application removal on any device followed by reinstallation and data resynchronization within a three-device network

Each risk area must be covered by dedicated test cases.

---

## 7. Entry Criteria

Testing can begin when the following conditions are met:

- Core functionality is implemented
- Application can be built and launched on all target platforms
- Test environment is prepared

---

## 8. Exit Criteria

Testing is considered complete when:

- All high-priority test cases have passed
- No critical or blocking defects remain open
- No data loss is observed in critical scenarios
- Local authentication (biometrics / PIN) works as expected
- Application behavior is consistent across platforms

---

## 9. Regression Strategy

Before each release, a smoke regression test suite will be executed to verify:

- Application startup and splash screen flow
- Local authentication (biometrics / PIN)
- Access to stored sensitive data
- Device network join operations
- Basic read/write and synchronization operations

Regression test cases are documented separately in the regression test suite.

---

## 10. Test Documentation

The following documents are related to this test plan:

- Manual test cases (`tests/manual/`)
- Regression test cases (`tests/regression/`)
- Security-related test cases (`tests/manual/security.md`)

---

## 11. Responsibilities

- Test case creation and maintenance: Project owner
- Manual test execution: Project owner
- Bug fixing and verification: Development team

---

## 12. Approval

This test plan is reviewed and approved by the project owner prior to each major release.
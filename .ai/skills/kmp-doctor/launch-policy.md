# Launch Policy

## iOS
- iOS runtime verification must assume physical-device-only testing.
- Do not assume iOS Simulator is usable.
- After build repair, request physical iPhone verification from the user.
- Ask for:
    - exact runtime error
    - crash text
    - Xcode console logs
    - stack trace
    - screenshots if useful

## Android
- Android launch verification may happen on emulator or physical device.
- If Android logs are available, use them for runtime diagnosis.

## Completion rule
A fix is not considered complete if:
- build passes
- but launch/runtime was not verified

Instead respond with:
- build fixed
- runtime not yet verified
- next required verification step
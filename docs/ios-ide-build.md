# iOS app build and IDE flags

Android Studio's KMP integration can replace `OTHER_SWIFT_FLAGS` with selector
stub options, dropping the project's `-Xcc -fmodule-map-file=…` setting.
Generated UniFFI Swift conditionally imports `mobile_uniffiFFI`; when that module
is not discoverable, its C types/functions must instead be visible inline.
Missing declarations caused the `RustBuffer` / `ForeignBytes` / `RustCallStatus`
and checksum error cascade in the app's Swift module.

The existing `MetaSecretCoreLibrary-Bridging-Header.h` imports the unchanged
`../UniffiGenerated/mobile_uniffiFFI.h`. This is the generator-supported inline
fallback and remains compatible with the normal separate-module setting. No
generated binding/modulemap, Swift version/concurrency, native archive or Core
contract is changed. Current IDE flag evidence, not historical beta-SDK logs,
established this cause.

Validate the **full app**, not only the Kotlin framework. From this repository,
using the installed Xcode and existing `iosApp/DerivedData`:

```sh
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath iosApp/DerivedData \
  CODE_SIGNING_ALLOWED=NO ARCHS=arm64 ONLY_ACTIVE_ARCH=YES build
```

The IDE regression uses `-workspace iosApp/iosApp.xcodeproj/project.xcworkspace`
instead of `-project`, with the actual replacements:

```text
OTHER_CFLAGS= -fno-objc-msgsend-selector-stubs -fno-objc-msgsend-class-selector-stubs
OTHER_SWIFT_FLAGS= -Xcc -fno-objc-msgsend-selector-stubs -Xcc -fno-objc-msgsend-class-selector-stubs
```

Use argument-safe quoting when invoking these settings. The physical-SDK compile
uses `-sdk iphoneos -destination generic/platform=iOS`, still no signing or run.
Gradle's framework phase uses Android Studio JBR and the existing project
`.gradle-home`. Do not clean caches, modify signing or regenerate Core to address
this declaration-visibility problem; do not print build environment credentials.

Actual command logs and compiler outcomes are recorded under
`.ai/artifacts/run/MS-20261018210022-*`: IDE override RED before the include,
same-command GREEN, normal Simulator and no-sign device-SDK app compilation.
Their build results do not claim installation, device execution, biometry or
cold-start visual acceptance. Previous framework-only checks established shared
Kotlin compilation, not complete native app build success.

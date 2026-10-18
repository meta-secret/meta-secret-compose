# Mobile startup surface

Shared Splash and native launch resources use solid Orbit0.2.0 backgroundCenter
`#2C2E2F` and two centered lines: **MetaSecret** / **Your network of Truth**.
Startup has no logo, gradient, controls, spinner or artificial wait. Registration
Step01 and Web retain their existing `Your network of trust` copy.

Shared `StartupSurface` uses existing `OrbitTextStyles`: bundled Manrope Regular,
heading29sp/34.8sp line height/-1.15sp tracking in `#F1F1EE`; tagline11sp/16.5sp
line height/.2sp tracking in `#C0C4C1`; line-box gap7dp. Splash VM, effects,
biometry and navigation are unchanged. Legacy logo/background images remain for
other consumers; only Splash's old consumption is replaced.

## Native resources and maintenance

Android's activity-only theme covers the launch window and system bars in gray.
Legacy Android uses `startup_window.xml`; Android12+ uses a static text-only
`startup_lockup.xml`, with transparent icon background and no branding footer.
Android13+ requests that text slot via `icon_preferred`. Launcher icons remain
unchanged. OS launch entrance and display duration remain OS-controlled.

iOS uses `StartupLaunchScreen.storyboard`: gray standard UIKit root view and a
160×64pt outlined `StartupLockup` PDF centered on the root window in every
orientation. No custom launch class, runtime font loading, action or delay.
The initial Compose controller UIView also has a gray underlay. Existing host
lifecycle/Koin/native calls and other Info/project/signing settings are unchanged.

Both checked-in native assets come from the exact bundled font:
`composeApp/src/commonMain/composeResources/font/orbit_manrope_regular.otf`,
SHA256 `b40311560415d436cbfc5ca0dd44d461ab14895b38cc0a224cae613c9eea0b39`.
Existing shared package0.2.0 manifest identity:
`ad8b58fee8ce55788f719ec557e76b353b5fbeef2579090b297f53c119b71526`.
The optional maintenance recipe is deterministic, embeds real glyph contours
without fonts/timestamps/random PDF IDs, and verifies transformed bounds:

```sh
xcrun swift scripts/export-startup-lockup.swift
xcrun swift scripts/export-startup-lockup.swift --check
```

Run from the Compose repository on macOS. Ordinary Gradle/Xcode/CI builds only
consume checked-in XML/PDF; they do not invoke CoreText/AppKit/font tooling or
read UxRevolution. No new dependency or shared package/version change is needed.

Android's288dp viewport has a192dp visible circle. CoreText measured title advance
147.275dp and tagline112.44dp, with58.3dp line-box block. Conservative rectangular
fit radius79.197dp; actual transformed glyph-bound maximum77.10848dp is below96dp.
No typography shrink or launcher/logo tile is used. The iOS PDF retains identical
point-size outlines in its160×64pt canvas.

## Verification limits

Default-scale native geometry is verified, but native outlined text cannot follow
Compose's arbitrary accessibility font scaling or supply dynamic text semantics.
OS entrance motion, snapshot caching and launch duration cannot be guaranteed by
these resources. Shared text retains existing font scaling; no timing hold is
added. Supported dimensions/appearance resources do not imply every device was
executed.

Final evidence lives in `.ai/artifacts/run/MS-20261018204434-*`: export checks,
Android/iOS builds, ibtool/actool, existing host tests/Kover and independent review.
Actual cold-start capture is separate from resource/pure renderer proof. The
restored runtime configuration is remote; do not start the real app/VM without
root's safe local provenance. If that cannot be established without new harness,
test branches, rebuilding Core/WASM or resetting data, final0095 remains Pending.
No live cold-start, account operation or Core business E2E PASS is claimed here.

Primary platform references: [Android SplashScreen](https://developer.android.com/develop/ui/views/launch/splash-screen),
[Apple launch-screen configuration](https://developer.apple.com/documentation/xcode/specifying-your-apps-launch-screen).
Root owns the corresponding `UxRevolution/LEGACY-MIGRATION.md` replacement record.

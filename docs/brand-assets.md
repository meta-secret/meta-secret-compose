# Gray01 brand assets

The owner approved Gray01 on 18 October 2026 for current MetaSecret marks,
including marks inside the application. The shared static pack is
`design-system/brand` version 1.0.0; clients keep local resources and do not read
the sibling workspace at runtime. Normal Gradle builds have no new renderer or
dependency. The Orbit design package remains version 0.2.0.

The approved fill mapping is `#0368FF → #2C2E2F`, `#4E95FF → #555B5D`,
`#78AEFF → #858E91`. The five master paths and white keyhole are unchanged.
Launchers use the accepted opaque black backplate; OS corner masking remains.

| Local assets | Count | Integrity boundary |
|---|---:|---|
| Android launcher density/round PNGs | 6 | Same filenames and dimensions |
| iOS AppIcon catalog PNGs | 15 | Same catalog assignments; opaque RGB |
| `logo.xml`, `metasecretpicture.xml`, `executioner.xml` | 3 | Only nine brand fill attributes change |
| Common `background_logo.png` | 1 | Same glow geometry and alpha |
| `graphics/main_logo`, `top_logo`, `top_logo_bg`, `cloud_key` | 4 | Same dimensions, alpha and nonselected pixels |
| `graphics/executioner`, `first`, `second`, `third` | 4 | Same output; only the embedded emblem mask changes |

The embedded raster mask follows the original vector contour. White highlights,
neighboring clothing and the blue character remain unchanged. A thin original
antialias fringe outside the contour remains; independent review accepted it as
negligible at display size. The mask must not be expanded into the clothing.
Unrelated blue controls, shields, backgrounds, provider marks and historical
research/screenshots are outside this recolor.

Consumed shared manifest SHA-256:
`54708faf26cd3320f736004f8f2cdf36e245cc7567a7967396fa5764c067b62a`.
The shared pack retains pristine raster inputs, an explicit maintenance exporter,
palette, output hashes and destination map. Regenerate there and validate its
manifest before copying; do not recolor already transformed client PNGs.

Validation uses resource integrity checks, a resource gallery and packaged-asset
inspection. These are not live application-screen or business E2E evidence.
No user data, keys, authentication state or icon cache is reset. An installed
older application may keep its old launcher until the owner updates its build;
this task does not uninstall or install over that application.

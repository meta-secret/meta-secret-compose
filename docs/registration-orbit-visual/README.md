# Native registration rendering evidence

These20 PNGs are actual Android Compose component captures on Pixel_4a API36,
1080×2340 portrait and rotated landscape. Pure states are supplied by the fixture;
the camera area is explicitly labeled synthetic sample. Core, account authorization,
real camera permissions and membership are not exercised by these images.

Portrait/landscape cover01,02,03,04,Pending and actual captured/blurred QR backdrop.
Additional portrait images capture available/taken/declined/Create error, clockwise
and counterclockwise motion, and QR expansion/collapse. The sheet uses60/40logical
insets from the full window and a compact centered camera+Back group in landscape.

**portrait-04-request-fusion.png was captured before final motion alignment.** It
revealed the early Back label. The final source uses frozen fusion keyframes,
verified offline against1032 assertions and compiled for Android/iOS. This PNG is
historical review evidence, not a golden for that final fusion state. It was not
retouched; a current runtime fusion frame belongs to the next combined UI gate.
The other static, QR and rotation frames remain relevant.

Successful bounded fixture runs:portrait1test/3.97s;landscape1test/4.53s;deterministic
motion1test/4.978s. An earlier Back semantics ambiguity was fixed before retest.
No full business E2E or iOS rendering pass is implied. See child stages007/0095.

The single AVD launched for these samples was stopped after exact AVD/PID/start-time
ownership verification. Its original orientation was restored. No iOS simulator was
booted. ADB was left untouched because its service ownership was not established.

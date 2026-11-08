# Riptide Android v0.2

Native Android client for Riptide, Join only, wire compatible with Riptide PC v1.3.

## v0.5.1: launch crash fix (duplicate DataStore)

- v0.5 crashed on launch. Root cause: ReceiveFolderRepository
  declared its own `preferencesDataStore(name = "riptide")` alongside
  PeerRepository's. Each delegate creates its own DataStore for the
  same file, and DataStore throws IllegalStateException ("multiple
  DataStores active for the same file") when both are used. The fix
  is a single shared delegate (`data/RiptideDataStore.kt`) that both
  repositories use. The receive folder also no longer loads
  synchronously during SessionManager construction; it starts at the
  default and loads in the background.

## v0.5: Tier 2 phone disconnect fix, Session corrections, Settings

- Tier 2 phone side of the disconnect fix: disconnect() is
  fire-and-forget, and its cleanup used to close whatever socket the
  shared field held and reset the state to IDLE. A rejoin tapped in
  the same instant could have its new socket closed and its CONNECTED
  state clobbered by the old cleanup. The cleanup now owns only what
  it captured at tap time (socket, reader and heartbeat jobs) and
  resets the state only when no new connect started since the tap.
  The PC side (bye fix v1.3.2 and rejoin fix v1.3.3) is already
  released; this is the matching phone hardening.
- Session header: Disconnect is now centered against the combined
  name/IP block, with the status dot and label on a separate line 8 dp
  below the header row, outside the alignment. The IP is 15/22 sp
  monospace. The Disconnect label is off-white with a coral power
  glyph. The button only shows while connected.
- One session state at a time, from the same state as the header.
  Connected idle shows "Make the move." (with the live receive folder
  name), a transfer shows the active panel, and a loss shows
  "Connection lost" with Back to Join and hides the send/download
  controls and any stale busy text. The interrupted-transfer copy
  ("The file was not completed...") only appears when a transfer
  actually stopped; an idle loss says "Reconnect to send or receive
  files." Completed session history stays visible.
- The info panels moved from the fixed footer into the scrolling
  body, as the last item after the history or device list, with 24 dp
  above and 24 dp trailing padding below. They move with the list.
- Settings is rebuilt as one scrollable column: Device, Storage,
  Identity, About, Diagnostics. Device name edits through a dialog
  (blank rejected); the fingerprint is untouched by a rename.
  Receive folder is a system folder picker with persisted access;
  the choice is saved only after a writable check, and Downloads/
  Riptide stays the default. A receive that finds its folder
  inaccessible waits for the user to Choose folder or Use default
  folder instead of failing. Identity shows the live code (29/36 sp
  monospace) and a Reset identity action that regenerates the
  fingerprint (disabled during a session, rechecked at confirmation,
  serialized with the hello exchange). About reads the installed
  version and links to the GitHub repository. Diagnostics keeps the
  log share, now opening the system share sheet.
- New icons in the existing stroke style: folder, pencil, reset,
  external link, share.

## v0.4: MainThread fix, Session polish, bottom info panels, icon padding

- Root cause of the background disconnect, found from the v0.3.4
  diagnostics log: verifyAlive ran on the main thread, and its ping
  write threw NetworkOnMainThreadException instantly on reopen. The
  catch-all treated that as "Connection lost" and closed a healthy
  socket. The ping write now runs on Dispatchers.IO, so the check
  can no longer fail a live session this way. This also covers the
  pre-send and pre-download checks, which can run on the main thread
  too. A cancelled check no longer fails the session either.
- Session header is now one compact row: peer name, host and a green
  dot with Connected (or Disconnected, in red) on the left, and a
  contained tonal Disconnect button with a power glyph on the right.
  The old CONNECTED pill and the floating Disconnect text are gone.
  On narrow screens or large text the whole button drops below the
  peer details, aligned end. Disconnect keeps its confirmation, with
  the transfer warning while a transfer is in progress.
- The transfer card no longer wraps size and speed into one line.
  Below the progress bar there are two fixed columns, Transferred
  on the left and Speed on the right, with the shared unit shown once
  (423.8 / 504.9 MB). Speed shows an em dash when it is not being
  measured instead of a made-up 0 MB/s. Cancel is in its own footer
  row, end aligned, below the metrics.
- A reusable BottomInfoPanel (info icon plus message, no heading)
  now sits in the bottom bar. Session shows "Interrupted transfers
  restart from the beginning." above the Send/Download strip, and
  Devices shows "Saved identity does not mean the PC is online."
  directly above navigation. The old inline copies are removed.
- The Block trident icon gets more padding: the foreground and
  monochrome groups go from scale 1.10 to 0.95 around the same
  pivot, so the mark is 86.4% of its previous size. Nothing else
  in the icon changed.

## v0.3.4: diagnostic test build for the background disconnect

- Test build only, to find why the session dies when the app is
  minimised and reopened. It changes no connection behaviour: the
  verifyAlive retry from 0.3.3 is untouched.
- Every connect, reader event (ping, pong, bye, error, ack, file),
  heartbeat ping, verifyAlive attempt, session failure and activity
  and service lifecycle event is logged with a wall-clock timestamp
  to logcat and to a diagnostics file. The failure line names the
  caller (verifyAlive, heartbeat, reader) and the exact exception,
  so the log shows whether the session died while minimised or on
  return, and what killed it.
- The log is mirrored live to Downloads/Riptide/riptide-diagnostics.log
  and can also be exported from Settings, Share diagnostics log. The
  internal copy is the reliable one; the Downloads mirror is best
  effort. Logging is fully guarded and can never crash the app, break
  a transfer, or fail a session.

## v0.3.3: do not kill a live session on one slow pong

- Returning to the foreground verifies the session with a ping and
  waits for the pong. That check used to try once: a single slow or
  lost pong (the process just came back from the background, threads
  rescheduling, a network blip) failed the session and closed the
  socket, so the PC showed disconnected the moment the app was
  reopened. The check now retries once, with a fresh baseline each
  time, and only fails the session when no pong arrives on either
  attempt. A late pong still counts for the retry.
- Note: when the session really is dead on return, the automatic
  rejoin still cannot succeed until Create Server is started on the
  PC again: the PC listener accepts one connection and then stops
  listening, and nothing re-arms it after a session ends. That is a
  PC-side fix, not an Android one.

## v0.3.2: Block trident launcher icon (design 27)

- Launcher icon is now design 27, Block trident, from the enlarged /
  shaded v2 set, selected by the user on 2026-11-08. The seven
  supplied Android XML resources were copied verbatim into
  app/src/main/res (no trimming, recoloring or rescaling): coral
  shaded foreground (#FF8C85 to #FF6B6B to #DF5B65, gradient from
  (40,22) to (68,84)), full-bleed #1C1C1C background, flat white
  monochrome, 108 x 108 dp canvas with the supplied 1.10 group
  scale, adaptive definitions for v26 and v33 (monochrome on 33+).
- Manifest now points at `@mipmap/ic_launcher` and
  `@mipmap/ic_launcher_round`. The old `@drawable/ic_launcher` is
  still in the project, unused by the manifest.
- Nothing else changed: the in-app dot-wave wordmark and the Stacked
  Blocks UI are untouched, and there is no custom splash screen.

## v0.3.1: saved peers show the device name

- Recent devices (Transfer tab) and the Devices tab showed (ip, ip):
  the row's first line is the saved peer name, and new peers were
  saved with the name set to the host, because the PC's device name
  is only known after the hello exchange. A successful connect now
  corrects the saved name from the hello_ok reply, so rows show
  (device name, ip/hostname). Peers saved by older versions are fixed
  the same way, on their next successful connect.

No APK built for 0.3.1 yet.

## v0.3: session history and label cleanup

- The Session page's THIS SESSION list now starts empty on every
  session: the in-memory transfer history is cleared when a session
  connects, so a previous session's transfers never show up there.
  The durable received history (Received tab) is separate and
  untouched.
- Devices tab: removed the "YOUR SAVED CONNECTIONS" label and the
  "Tap a device to join. Use its menu to forget it." footer line.
- Received tab: removed the "ON THIS PHONE" label.

No APK built for 0.3 yet.

## v0.2.2: background disconnect fix

Minimising the app for more than a few seconds, or opening the system
file picker (which backgrounds the app the same way), broke the
connection. The cause was the foreground service: it only ran while a
file was actively moving, and stopped itself the moment a transfer
finished. While the user was connected and picking a file, no service
was running at all, so the app was an ordinary cached process holding
a socket, and Android reclaimed it within seconds.

- The foreground service now runs for the whole session: it starts
  when a session connects (from the app foreground, so the background
  start restriction never applies) and stops only on disconnect or
  session failure. A persistent "Connected to PC" notification shows
  while idle, with a Disconnect action; the transfer progress
  notification shows while a file moves. `startForeground` is called
  synchronously in `onStartCommand`, so the 5-second start timeout
  can never fire.
- The partial wake lock is held for the session, with no timeout, and
  released when the service stops.
- Coming back to the foreground now verifies the session with a ping
  and waits for the pong (a write into a dead socket proves nothing).
  When the session died while the app was away, the app rejoins the
  last peer on its own, using the identity already in the Keystore. A
  deliberate disconnect clears the last host, so this never
  reconnects after the user chose to leave. A rejoin of a Known peer
  still waits for the PC's approval tap. Send and download now run
  the same liveness check before starting.
- No battery-setting change is needed: a running foreground service
  is exempt from Doze network restrictions and App Standby throttling
  while it runs.
- Known limit: the `dataSync` service type is stopped by Android
  after 6 hours. Real sessions last minutes; a session that ran that
  long shows the interrupted card and rejoins on the next return.

No APK was built for 0.2.2; the next release is 0.3.

## v0.2.1: Broken pipe diagnosis and fix

Live test of v0.2 on the phone: send failed again with "send failed:
Broken pipe (0 of 382450369 bytes sent)", shown on Home as
"Couldn't reach this PC". The PC side was exonerated by reproduction:
a simulated phone (same TLS handshake, signed hello challenge, same
framing, same 128 KB chunk send) transfers 5 MB and 365 MB to real PC
v1.3.1 code with matching SHA-256 and ack, including a threaded run
with reader + heartbeat and a 2 MB/s throttle. The bug was in the
phone's session handling, not the protocol:

- The heartbeat's ping write used to fail silently when the socket
  died. The reader blocks forever on a silently dead socket, so
  nothing marked the session dead until the next send tried to write
  to it and got Broken pipe at 0 bytes. The heartbeat now marks the
  session FAILED with "Connection lost." within 10 s of the socket
  dying, and closes the dead socket.
- Send and download now refuse when the session is not CONNECTED
  instead of writing to a dead socket.
- The reader no longer strands the UI on a dead session: a PC "bye"
  closes the socket and drops to FAILED (the interrupted card, which
  is now reachable from Home), and a PC "error" no longer kills the
  reader loop.
- Transfer errors live on their own flow now: a failed send shows on
  the Session screen, never on Home as "Couldn't reach this PC".
- A clean disconnect no longer leaves a stale error on Home.
- The address placeholder no longer shows the user's real DDNS
  hostname; it is now 203.0.113.10 (documentation range).

What is still NOT proven: the exact event that killed the socket in
the user's test (most likely the network dropping during the long
spool of a cloud video, or the PC sleeping). The fixes above make the
app detect a dead session within 10 s and report it truthfully
instead of failing the next send with Broken pipe, but the underlying
drop itself is environmental and can still happen.

## v0.2: final approved UI

Exact implementation of the approved Stacked Blocks design. Home is three
widgets with no enclosing card: a full-width THIS DEVICE tile, then an
ivory MY CODE tile (62.5%) and a charcoal Fingerprint tile (37.5%), with a
final 32 dp blank gap before the Join PC heading. Bottom tabs are Transfer,
Devices, Received; the gear opens Settings.

- After Allow connection, the same trust sheet becomes "Waiting for
  approval from host" and shows the PHONE'S six-digit code. The host code
  stays on the preceding trust screen. Only the host's hello_ok opens the
  Session; local Allow alone never does. Cancel aborts and ignores a late
  success.
- Join never navigates on its own. The Transfer tab shows Home while idle
  and the live Session once connected; switching tabs never cancels a
  transfer or disconnects.
- Received history is persisted across restarts. Only files that pass
  SHA-256 verification AND save to Downloads/Riptide are recorded, with
  the actual saved name, source PC, size, and timestamp. Tapping a file
  opens it; a missing file reports "File no longer available" and its
  stale record is removed. Removing a record never deletes the file.
- Recent devices sort by last successful connection. Failed attempts never
  reorder them. Forget removes the peer, not the phone identity.
- A second transfer is blocked while preparing, transferring, verifying,
  waiting for the PC ack, or waiting for download approval. Send waits for
  the PC ack; receive waits for hash verification and saving. A download
  decline is shown as its own state with retry, distinct from a transport
  error. Cancel and disconnect warn that the file must restart.
- File picking uses single-document ACTION_OPEN_DOCUMENT.

## v0.1.1 fix

Send failed with "Broken pipe" on the first live test. Cause: the file
size in the send header came from the content provider, which can report
0 or a stale size for cloud-backed files, while the stream delivers the
real bytes. The PC then reads the leftover file bytes as a header, fails
with "bad header length", and closes the session. The fix spools the
picked file to a local temp copy first, so size, SHA-256 and bytes all
come from the same file, and the phone now waits for the PC's ack before
calling a send successful, so a rejection or a lost connection is
reported instead of a false success.

## What v0.2 does

- Join a PC on Create Server at port 50000, by hostname, IPv4, IPv6, or user@host.
- Host fingerprint approval: Known, New, Changed, with the 6-digit code first and the full fingerprint available. Changed needs an explicit Replace.
- My code on the Home screen, computed from the phone identity.
- Send one file at a time, phone to PC, via the system file picker.
- Download one file at a time, PC to phone, with PC approval.
- Receive a push from the PC, with .part plus SHA-256 verification, rename on duplicates, and saved-name reporting.
- Receive folder is fixed at Downloads/Riptide.
- Foreground service with a notification for an active transfer.

## What v0.2 does not do

- No Create/listener mode on the phone.
- No share sheet, multi-file send, or resume. Interrupted transfers restart.
- PC-initiated download from the phone is denied explicitly.
- No custom port or receive folder, no Play Store or F-Droid, no identity backup.

## Build

Requires JDK 17 and Android SDK 34.

```
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew assembleRelease   # needs keystore.properties, see below
```

Note: `gradle/wrapper/gradle-wrapper.jar` is not in this repo, because the
GitHub push used here only handles text files. Before running `./gradlew`
for the first time, put the jar in `gradle/wrapper/` by running
`gradle wrapper --gradle-version 8.7` with a system Gradle, or by
downloading it from
https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar
On this build machine the wrapper was used with a local Gradle 8.7 install
and the `preferIPv4Stack` fix in `gradle.properties`.

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## v0.5.1 release APK

- `app-release.apk`, 47 MB, versionCode 13, versionName 0.5.1, package `com.riptide`, min SDK 29, target SDK 34.
- SHA-256: `0e6d5430426493d81816bb047febc83e7b1ecb094938a6433cf91960df6c99d6`
- Signed with APK Signature Scheme v2, 1 signer, RSA-2048, certificate DN `CN=riptide, OU=Riptide, O=Riptide`, certificate SHA-256 `c7b6448578a36cb9b7bf6f393573fe5baaa3680000f81a2a56b6ad4681c3d0c6` (same key as v0.1/v0.1.1/v0.2/v0.2.1/v0.3.2/v0.3.3/v0.3.4/v0.4/v0.5, installs as an update).
- Fixes the v0.5 launch crash (duplicate DataStore).

## v0.5 release APK

- `app-release.apk`, 47 MB, versionCode 12, versionName 0.5, package `com.riptide`, min SDK 29, target SDK 34.
- SHA-256: `d51fec0cfebc943620633f204243d9ba5e407954cdd947e80f6d3bc8e2df4cc4`
- Signed with APK Signature Scheme v2, 1 signer, RSA-2048, certificate DN `CN=riptide, OU=Riptide, O=Riptide`, certificate SHA-256 `c7b6448578a36cb9b7bf6f393573fe5baaa3680000f81a2a56b6ad4681c3d0c6` (same key as v0.1/v0.1.1/v0.2/v0.2.1/v0.3.2/v0.3.3/v0.3.4/v0.4, installs as an update).
- Tier 2 phone disconnect fix, Session corrections, and the Settings
  rebuild from the v0.5 design update.

## v0.4 release APK

- `app-release.apk`, 47 MB, versionCode 11, versionName 0.4, package `com.riptide`, min SDK 29, target SDK 34.
- SHA-256: `0ef03f3e35dd2b0f646c7d6bdee4654b954c7bc07af40684dedcdf324a1b2ba9`
- Signed with APK Signature Scheme v2, 1 signer, RSA-2048, certificate DN `CN=riptide, OU=Riptide, O=Riptide`, certificate SHA-256 `c7b6448578a36cb9b7bf6f393573fe5baaa3680000f81a2a56b6ad4681c3d0c6` (same key as v0.1/v0.1.1/v0.2/v0.2.1/v0.3.2/v0.3.3/v0.3.4, installs as an update).
- Fixes the NetworkOnMainThreadException that killed the session on
  reopen, plus the Session polish, bottom info panels and icon
  padding from the v0.4 design update.

## v0.3.4 release APK

- `app-release.apk`, 48 MB, versionCode 10, versionName 0.3.4, package `com.riptide`, min SDK 29, target SDK 34.
- SHA-256: `4b22491de19f8a9242b9a97e14a742227a6a3ede5b80602178ee8f887cf9cdf0`
- Signed with APK Signature Scheme v2, 1 signer, RSA-2048, certificate DN `CN=riptide, OU=Riptide, O=Riptide`, certificate SHA-256 `c7b6448578a36cb9b7bf6f393573fe5baaa3680000f81a2a56b6ad4681c3d0c6` (same key as v0.1/v0.1.1/v0.2/v0.2.1/v0.3.2/v0.3.3, installs as an update).
- Diagnostic test build only. No connection behaviour changed.

## v0.3.3 release APK

- `app-release.apk`, 47 MB, versionCode 9, versionName 0.3.3, package `com.riptide`, min SDK 29, target SDK 34.
- SHA-256: `ca0418db2eefe6da35ae31b9dcd6a04942cbc0115ca34d770fb6c48aacedd904`
- Signed with APK Signature Scheme v2, 1 signer, RSA-2048, certificate DN `CN=riptide, OU=Riptide, O=Riptide`, certificate SHA-256 `c7b6448578a36cb9b7bf6f393573fe5baaa3680000f81a2a56b6ad4681c3d0c6` (same key as v0.1/v0.1.1/v0.2/v0.2.1/v0.3.2, installs as an update).

## v0.3.2 release APK

- `app-release.apk`, 47 MB, versionCode 8, versionName 0.3.2, package `com.riptide`, min SDK 29, target SDK 34.
- SHA-256: `d5e5761a2e5d042e9433360e88c2a93cca65925e034f10d0bf727b14084e7131`
- Signed with APK Signature Scheme v2, 1 signer, RSA-2048, certificate DN `CN=riptide, OU=Riptide, O=Riptide`, certificate SHA-256 `c7b6448578a36cb9b7bf6f393573fe5baaa3680000f81a2a56b6ad4681c3d0c6` (same key as v0.1/v0.1.1/v0.2/v0.2.1, installs as an update).
- Launcher icon is design 27, Block trident, enlarged/shaded v2.

## v0.2.1 release APK

- `app-release.apk`, 47 MB, versionCode 4, versionName 0.2.1, package `com.riptide`, min SDK 29, target SDK 34.
- SHA-256: `4b73eee3b5165c5df38e09fa52d893bc71b826b22c52c34e846fb60704e1dc96`
- Signed with APK Signature Scheme v2, 1 signer, RSA-2048, certificate DN `CN=riptide, OU=Riptide, O=Riptide`, certificate SHA-256 `c7b6448578a36cb9b7bf6f393573fe5baaa3680000f81a2a56b6ad4681c3d0c6` (same key as v0.1/v0.1.1/v0.2, installs as an update).

## v0.2 release APK

- `app-release.apk`, 47 MB, versionCode 3, versionName 0.2, package `com.riptide`, min SDK 29, target SDK 34.
- SHA-256: `59f9690e317774313ac86afe5f4fab8f80bcbbb5bc7ba681b1b2bd680282d97c`
- Signed with APK Signature Scheme v2, 1 signer, RSA-2048, certificate DN `CN=riptide, OU=Riptide, O=Riptide`, certificate SHA-256 `c7b6448578a36cb9b7bf6f393573fe5baaa3680000f81a2a56b6ad4681c3d0c6` (same key as v0.1/v0.1.1, installs as an update).

## v0.1.1 release APK

- `app-release.apk`, 48 MB, versionCode 2, versionName 0.1.1, package `com.riptide`, min SDK 29, target SDK 34.
- SHA-256: `bf50d9b90720f312552e71279768cd9813f31c9e0fb1b113725f18bbb8548953`
- Signed with APK Signature Scheme v2, 1 signer, RSA-2048, certificate DN `CN=riptide, OU=Riptide, O=Riptide`, certificate SHA-256 `c7b6448578a36cb9b7bf6f393573fe5baaa3680000f81a2a56b6ad4681c3d0c6`.
- Debug APK SHA-256: `1d9f8be6a20b6e9f8503132dcc6f123499f590484b9879b09bcb9196f72a8ff1` (v0.1 debug build, unchanged in v0.1.1)

## Signing a release

Create `keystore.properties` in the repo root (never commit it, it is gitignored):

```
storeFile=/path/to/riptide-release.keystore
storePassword=...
keyAlias=riptide
keyPassword=...
```

Then run `./gradlew assembleRelease`. Keep the keystore file and its passwords backed up somewhere safe. Losing the keystore means future updates cannot be signed with the same key, and Android will treat them as a different app.

## Protocol

See PROTOCOL.md. The PC session code in Riptide v1.3 is the source of truth. Any protocol change must update both sides and be interop tested before shipping.

## Known limits, not yet live tested on a phone

- Live interop against a real PC v1.3 listener has not been run from this build machine, because there is no Android device here. Install the APK on a Nothing Phone (2) and test Join, send, download, and receive against a PC on Create Server.
- Certificate generation uses Android Keystore plus BouncyCastle. That path is exercised on a real device, not in JVM unit tests, which use an in-memory keypair.

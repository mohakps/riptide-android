# Riptide Android v0.2

Native Android client for Riptide, Join only, wire compatible with Riptide PC v1.3.

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

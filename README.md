# Riptide Android v0.1.1

Native Android client for Riptide, Join only, wire compatible with Riptide PC v1.3.

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

## What v0.1 does

- Join a PC on Create Server at port 50000, by hostname, IPv4, IPv6, or user@host.
- Host fingerprint approval: Known, New, Changed, with the 6-digit code first and the full fingerprint available. Changed needs an explicit Replace.
- My code on the Home screen, computed from the phone identity.
- Send one file at a time, phone to PC, via the system file picker.
- Download one file at a time, PC to phone, with PC approval.
- Receive a push from the PC, with .part plus SHA-256 verification, rename on duplicates, and saved-name reporting.
- Receive folder is fixed at Downloads/Riptide.
- Foreground service with a notification for an active transfer.

## What v0.1 does not do

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

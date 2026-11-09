# Riptide Android

Riptide Android is the phone client for Riptide. It lets an Android phone send files to a PC, and receive files from a PC, over a direct encrypted connection. There is no cloud upload, no account, and no middleman holding a copy.

The phone is the Join side. It dials a PC that is running Create Server. The PC stays the listener, the phone stays the client.

## Features

- Join a PC by hostname, IPv4 address, or IPv6 address, on port 50000.
- Send one file, or send several files as one batch in picker order. Each file in a batch starts after the PC confirms the previous one.
- Download a file from the PC by asking for its path. The PC has to approve the request.
- Receive files pushed from the PC. Files land in Downloads/Riptide by default.
- Live transfer progress with speed, transferred amount, and time remaining, for single files and batches.
- Saved peers. A PC you have connected to before can be joined again without retyping its address, and forgotten from Devices.
- Transfer history for the current session, plus a persistent Received list.
- A foreground service keeps the session alive while the app is minimised or the phone is reopened, with default battery settings. No system setting change is needed.
- Device rename, a receive folder picker, identity reset, and diagnostics sharing in Settings.
- SHA-256 verification on every file. A file is written as a `.part` file first and renamed only after its hash matches.
- Received files never overwrite. A duplicate is saved as `report (1).pdf`, then `(2)`, and so on.

## Install

Requirements: Android 10 or later, and a PC (Windows/Linux) running Riptide with Create Server open.

1. Download the [latest APK](https://github.com/mohakps/riptide-android/releases/latest) and install it. (Allow install from the browser or file manager once, if Android asks.)
2. On the PC, start Create Server and note the address shown.
3. On the phone, enter that address and tap Join PC.

## How a connection is made

1. On the PC, open Riptide and start Create Server. It shows the PC address to share: its IPv4 address, and its IPv6 address when it has one.
2. On the phone, open Riptide Android and enter that address on the Home screen, then tap Join PC. A saved peer can be tapped instead.
3. The phone shows the PC fingerprint and 6-digit code for the first-connection check. Allow it to continue. A known PC skips this step.
4. The phone sends a hello with its device name and certificate. The PC replies with a random challenge, the phone signs it, and the PC checks the signature against the certificate.
5. The PC shows the phone fingerprint and a 6-digit code. Compare the code with the code shown on the phone, out of band, and approve on the PC. The session opens.
6. Send, download, or wait for a push. Either side can disconnect. The phone can rejoin without the PC restarting Create Server.

The fingerprint is the SHA-256 of the peer certificate. Known means it matches a saved peer, New means first connect, Changed means a saved device now has a different fingerprint and must be verified again before it is trusted.

See PROTOCOL.md for the full wire protocol.

## How it differs from the terminal version

- The terminal version runs on Windows and Linux with a keyboard driven interface. This is a touch interface built with Kotlin and Jetpack Compose for Android 10 and later.
- The terminal version can both Create and Join. This app can only Join. It cannot listen for incoming connections.
- The terminal version has a file manager for picking and sending files. This app uses the Android system file picker.
- The terminal version sends and receives in the folder where it was started. This app receives into Downloads/Riptide by default, and the folder can be changed in Settings.
- The terminal version is installed with pipx from GitHub. This app is installed as an APK from GitHub.
- A phone on mobile data is usually behind CGNAT on IPv4, so it joins over IPv6 or over the same Wi-Fi LAN when that is available, instead of creating a server of its own.
- A download started from the PC is denied on the phone. The phone can ask the PC for a file, and the PC can push a file to the phone, but the PC cannot pull a file from the phone.

## Future Scope

- Create Server on the phone, so a phone can listen and a PC or another phone can join it.
- Resume for interrupted transfers, instead of restarting a file from the start.
- Sending folders.
- A share sheet, so files can be sent to Riptide from other apps.

## How this was built

- Muse, app development and building.
- Codex (GPT 6.1 Sol), UI designer.
- Tested on Android 16.

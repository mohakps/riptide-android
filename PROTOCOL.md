# Riptide PC v1.3 wire protocol

Source of truth: PC `src/riptide/core/session.py` and `identity.py`, v1.3.

- One TLS connection, Join dials Create, port 50000.
- Framing: 4-byte big-endian length plus JSON header, header at most 1 MB. A file message is followed by exactly size bytes.
- Message types: hello, hello_challenge, hello_proof, hello_ok, error, file, download_ask, download_denied, ack, ping, pong, bye, cancel, cancel_ack.
- One file transfer at a time. Send lock makes header plus bytes atomic.
- Cancelling a transfer (since PC v1.6.0, Android v0.9.8): two new messages, cancel and cancel_ack, both carrying the filename. No capability field in hello/hello_ok and no old-peer fallback: both peers are expected to be new. The session stays open for the next transfer.
  - Receiver-initiated cancel: the canceller sends cancel, stops writing to disk, and keeps reading payload bytes, discarding them, until exactly size bytes are consumed (drain to the next message boundary). The sender's reader sees cancel and sets a flag the send loop polls between 128KB chunks; on seeing it the sender stops reading the file, sends zero bytes for the remainder of the declared size, then sends cancel_ack. The canceller, having drained size bytes, reads the next framed message, expects cancel_ack, deletes the .part file (plus any resume sidecar), records the transfer as Cancelled, and the session is ready for the next transfer. Nothing is added to any resume or pending record.
  - Sender-initiated cancel: the canceller stops reading the file, sends zero bytes for the remainder of the declared size, then sends cancel as the post-payload message. The receiver reads the full payload as normal, then BEFORE the SHA-256 check, rename, and ack, peeks for a pending framed message (zero-timeout check). If a cancel for this file is pending: delete the .part (plus sidecar), record Cancelled, send cancel_ack, and do NOT send ack.
  - One terminal outcome per file: if the receiver already completed the file and sent ack before the cancel landed, completion stands, and the sender treats a late ack for a file it tried to cancel as success, not as a cancel. If the receiver sees cancel before acking, cancel wins and any late ack or success update is suppressed. A genuine connection loss follows the existing connection-lost flow, not this one.
  - On Android the receiver's post-payload peek is a FramedReader peek with a short socket timeout, because Java has no select on an SSLSocket; the wire behaviour is identical to the PC's select(0) peek.
- Keepalive: blocking socket, TCP keepalive, ping every 10 seconds, pong in reply.
- Receiver writes to a .part file and renames only after SHA-256 matches the header. A mismatch is reported, not renamed. The ack carries the actual saved name.
- Since v1.2, a received file never silently overwrites: report.pdf becomes report (1).pdf, then (2), up to 1000. archive.tar.gz becomes archive.tar (1).gz, .bashrc becomes .bashrc (1).
- Progress is throttled to at most every 0.1 seconds, plus always on the final chunk.
- Identity: RSA-2048 self-signed, CN riptide, 10-year. Fingerprint is SHA-256 of the certificate DER, 64 hex characters.
- 6-digit code: first 3 bytes of the fingerprint as an integer, modulo 1,000,000, formatted with leading zeros, grouped 123-456. Examples: fingerprint starting 123456 gives 193-046, starting 000001 gives 000-001. Empty or invalid gives ------. The code is a Short Authentication String, about 20 bits, not a TOTP code.
- TLS context is CERT_NONE on PC, because a self-signed client cert fails Python verification even with CERT_OPTIONAL. Android uses a custom TrustManager that accepts the peer certificate, captures its DER, and lets the app check the fingerprint itself.
- Client authentication is a signed hello challenge, not a TLS client certificate:
  1. Phone sends hello with device, host, and its certificate as base64 DER.
  2. PC replies hello_challenge with a 32-byte random nonce, base64, or hello_ok directly on a v1.0 host.
  3. Phone signs the nonce with its private key, RSA PKCS1v15 plus SHA-256, and sends hello_proof with the signature, base64.
  4. PC verifies the signature against the presented certificate, computes the fingerprint from the certificate DER, never from a sent string, and only then asks for approval.
  5. PC replies hello_ok with its device name, or error.
- Trust: Known means fingerprint matches a saved peer, Changed means a saved device label now has a different fingerprint and must never be silently replaced, New means first connect.
- Android sharing: each address row's Share sends only that row's bare address as plain text (IPv6 with brackets stripped, no port, no scheme, no device name, no code). The riptide://join deep link is still accepted on intake (old links carrying &port=50000 still parse; any other explicit port is rejected); it never carries a fingerprint or code.

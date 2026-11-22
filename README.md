# DINESH THAKUR WALKIE TALKIE v3.13 — LOCAL WI-FI TWO-WAY CALL

## What changed
- Fixed packet addressing: every local packet now carries sender ID and recipient ID. The earlier ring implementation sent a 36-character UUID string while the receiver expected 16 bytes, so ring packets were ignored.
- Fixed PTT packet routing and separated broadcast PTT audio from private-call audio.
- Added CALL beside each online staff member.
- CALL sends a private call invitation to that user; recipient can ANSWER or DECLINE.
- Accepted call is full-duplex: both phones send and receive audio at the same time, like a normal mobile call.
- Added END TWO-WAY CALL.
- RING remains a separate buzzer alert.
- Local Wi-Fi only: no internet, mobile data, cloud, or external server.

## How to use
1. Connect all phones/tablets to the same Wi-Fi router.
2. Enter a unique staff name on each device.
3. Tap JOIN LOCAL WI-FI CHANNEL on all devices.
4. Use RING for an alert, or CALL for a two-way conversation.
5. The recipient taps ANSWER. Both sides can speak and listen simultaneously.
6. Use END TWO-WAY CALL to hang up.
7. HOLD TO TALK remains available for channel-wide PTT.

## Network requirements
All devices must be on the same LAN and router must allow multicast/client-to-client traffic. Guest Wi-Fi or AP/client isolation may block communication. This is an initial UDP LAN prototype; Wi-Fi congestion, multicast filtering, and device audio routing may affect call quality. It does not include encryption/authentication or guaranteed echo cancellation.

Background operation uses an Android foreground service and persistent notification. Android force-stop, reboot before app launch, battery-management policies, or Wi-Fi disconnection can stop it.

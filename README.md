# DINESH THAKUR WALKIE TALKIE v3.12 — LOCAL WI-FI / RING / BACKGROUND

Features:
- Direct local Wi-Fi multicast; no internet, mobile data, cloud, or external signalling server.
- Each device enters its own staff name; names are advertised per device.
- Online/offline staff list.
- RING button beside each online user; only the selected device receives the ring packet.
- Receiving device sounds a buzzer, vibrates, and posts an incoming-ring notification.
- Foreground service owns the LAN socket, heartbeat, audio receive and microphone transmit.
- Service is configured to continue when Home is pressed or the task is swiped from Recents (`stopWithTask=false` and `onTaskRemoved` does not stop it).

Important Android limitation:
- This is designed to keep running for A and B (Home and swipe from Recents) while the foreground service and its persistent notification remain active.
- Android/OEM battery managers may still kill background services. Exempt the app from battery optimization / allow auto-start if the device offers those settings.
- Force Stop from Android Settings, reboot before the app is relaunched, disabling Wi-Fi, or the router isolating clients will stop communication. No ordinary app can guarantee operation after Force Stop.
- Incoming ring notification requires notification permission; microphone permission is required for voice.
- App is kept alive with a foreground service; expect a persistent notification while connected.

Router must allow multicast and client-to-client communication. This is an initial LAN prototype and does not provide encryption or authentication.

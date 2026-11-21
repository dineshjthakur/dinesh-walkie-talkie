# DINESH THAKUR WALKIE TALKIE v3.9 — BACKGROUND LOCAL WI-FI

- Local Wi-Fi multicast only; no cloud server, internet, or mobile data.
- Staff list shows online/offline status.
- A foreground service starts when the user joins the channel.
- A persistent notification shows that JB HOSPITALITY Walkie Talkie is active.
- Wi-Fi and CPU locks help maintain LAN connectivity while the screen is off.
- Leave Channel stops the foreground service and releases locks.

## Usage
1. Connect all devices to the same Wi-Fi router.
2. Open the app, grant microphone and notification permissions.
3. Enter a staff name and tap JOIN LOCAL WI-FI CHANNEL.
4. You can press Home or turn off the screen; the service is intended to keep presence and incoming audio running.
5. Return to the app to use the on-screen hold-to-talk button.
6. Tap LEAVE CHANNEL when finished.

## Important limitations
This build keeps the LAN connection and incoming audio active in the background using an Android foreground service. The push-to-talk control remains in the app; when the app is backgrounded, return to the app to press and hold to talk. A future notification/headset push-to-talk control would be needed for true talking without reopening the app.

Router must allow client-to-client traffic and multicast. This initial prototype does not include encryption, user authentication, or guaranteed compatibility with routers that suppress multicast. Android may show a persistent foreground-service notification while connected.

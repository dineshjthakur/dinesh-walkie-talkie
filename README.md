# DINESH THAKUR WALKIE TALKIE v3.15 — JAVA SYNTAX FIX

- Removed the Leave Channel control from the main screen.
- Staff enters their own name once; the name is saved on that device.
- Auto-connect service starts when the app opens with a saved name.
- Service waits for the restaurant Wi-Fi, connects when it becomes available, and attempts to reconnect after Wi-Fi loss.
- Foreground notification stays visible while auto-connect is enabled.
- RING and CALL buttons remain beside each online staff member.
- Local Wi-Fi only; no mobile data, internet, cloud, or external server.

IMPORTANT:
- First launch requires entering the staff name and tapping ENABLE AUTO-CONNECT once.
- Allow microphone and notification permissions.
- Android force-stop, reboot before the app is opened, OEM battery restrictions, or disabling Wi-Fi can stop the service.
- Router must allow multicast and device-to-device communication.
- Ring/call/PTT are LAN UDP prototype features and require testing on two or more devices on the same Wi-Fi.


## Version 3.16 Android 16 compatibility patch
- Declares FOREGROUND_SERVICE_MICROPHONE, required for a foreground microphone service on modern Android versions.
- Runtime microphone permission is still required before enabling auto-connect and voice features.
- This is a source patch; build and device testing are still required.


## Version 3.17 Wi-Fi interface and peer routing patch
- Binds the UDP socket to the selected Android Wi-Fi Network.
- Joins the multicast group on the explicit Wi-Fi NetworkInterface.
- Learns each peer's source IP from heartbeat packets and routes private RING/CALL/audio/control packets directly to that peer.
- Fixes the literal \\n in the app title.
- Build and two-device testing are still required; router multicast/client-isolation policies can still affect operation.


## Version 3.18
- Enlarged push-to-talk control to 112dp height with 23sp bold label.
- Routes individual control and call packets over the same multicast LAN channel as the working device-presence path, with recipient UUID filtering at the receiver.
- Uses a broadly supported alert beep for incoming RING.
- Reports receive-loop exceptions in the app status instead of silently swallowing them.
- Source update only; GitHub build and two-device RING/CALL verification remain required.


## Version 3.19 Network thread fix
- Fixes NetworkOnMainThreadException by queuing UDP packet sends on a dedicated single-thread executor.
- Applies to RING, CALL/control packets, heartbeats, and outgoing audio packets that use sendPacket().
- This is a source fix; compile with GitHub Actions and verify RING/CALL on two physical devices.


## Version 3.20 Loud ring and call alerts
- Adds repeating system ringtone and vibration for incoming RING alerts.
- Adds repeating ringtone and vibration for incoming CALL alerts until answered/declined; caller also hears ringback until answer/reject/end.
- Adds a dedicated high-importance notification channel for audible alerts.
- Android users may need to set this channel to Alerting and enable sound/vibration in App info > Notifications; ringtone volume must be turned up. Android does not permit apps to override a user's silent/DND/volume settings.
- Source update only; build and two-device alert tests required.


## Version 3.21 Call timeout and prominent answer notification
- Outgoing caller device does not play the ringtone; it shows calling status and waits silently.
- Incoming call alert rings/vibrates for up to 30 seconds, then stops and records a missed call status.
- Caller gives up after 30 seconds if nobody answers.
- Incoming call notification is ongoing, public, expanded with BigTextStyle and has a prominent ACCEPT CALL action plus DECLINE.
- Answer/decline cancels timeout, ringtone, vibration and incoming-call notification.
- Source change only; GitHub build and real two-device testing required.

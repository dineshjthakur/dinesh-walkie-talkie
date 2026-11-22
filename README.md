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

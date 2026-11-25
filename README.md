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


## Version 3.22 Press-and-hold RING
- Staff RING control now sends a ring alert only after a long press.
- A normal tap displays an instruction toast and does not send a ring.
- Button label changed to HOLD TO RING.
- Preserves the v3.21 working communication and call behaviour.
- Source update only; build and two-device test required.


## Version 3.23 Hold-to-ring release behaviour
- RING starts when the user presses the RING button.
- RING_STOP is sent when the user releases the button or touch is cancelled.
- Receiver stops ringtone, vibration and ring notification on RING_STOP.
- Source update only; build and two-device test required.


## Version 3.24 Always connected and faster discovery
- Hides the manual ENABLE AUTO-CONNECT / channel button from the main screen.
- Starts the walkie-talkie service automatically when the app opens.
- Sends an immediate presence heartbeat after LAN join.
- Reduces heartbeat interval to one second and stale-peer timeout to six seconds.
- Keeps local Wi-Fi-only operation; devices must still be connected to the same reachable LAN.
- Android may still stop background services after Force Stop, reboot, or manufacturer battery restrictions.
- Source update only; build and two-device verification required.


## Version 3.25 — Three-router LAN / Access Point setup
- Adds an in-app router setup guide for a single shared LAN across three routers and switches.
- Adds Wi-Fi interface, IP address and default gateway diagnostics to help verify that phones are on the same subnet.
- Recommended topology: one main router with DHCP enabled; routers 2 and 3 in Access Point mode with DHCP disabled; connect APs to switches through LAN ports; do not use WAN ports for the LAN-to-LAN AP configuration.
- Disable client/AP isolation and guest isolation. Use unique management IPs for APs in the main subnet.
- The app itself cannot change router configuration. Configure the routers through their own administration pages.
- Keeps the existing local Wi-Fi multicast communication protocol and existing PTT/RING/CALL implementation. For this to cross all three routers, they must bridge one LAN and pass multicast; if a router/switch filters multicast, additional network configuration or a relay will be needed.
- Source update only; GitHub build and testing across all three access points required.


## Version 3.26 build compatibility fix
- Fixes Java compilation error: RouteInfo.isDefault() is not available in the Android API compile target.
- Detects the default IPv4/IPv6 route by checking destination prefix length == 0 instead.
- No changes to communication, RING, CALL, or PTT behaviour.
- Source update only; run GitHub Actions to verify compilation.


## Version 3.27 — Scan devices
- Adds a SCAN DEVICES button to the main screen.
- Scan sends an immediate multicast heartbeat and UDP subnet-broadcast heartbeat on available IPv4 interfaces.
- Devices running the app on the same bridged LAN can respond through their regular heartbeat loop and appear in the staff list.
- Adds a NETWORK SETUP button for the three-router LAN/AP guide.
- Scanning cannot cross routed/NAT subnets or router broadcast isolation. For this to work across two/three routers, configure secondary routers as APs, LAN-to-LAN, DHCP disabled on secondary routers, and disable client isolation.
- Source update only; build and test on all devices required.


## Version 3.28 — Scan broadcast API compilation fix
- Fixes the Android Java compilation error in `WalkieKeepAliveService.java` by calling `InterfaceAddress.getBroadcast()` instead of the non-existent `getBroadcastAddress()` method.
- Retains the v3.27 scan-devices, local Wi-Fi discovery, PTT, RING, CALL, and network setup behaviour.
- Increments Android versionName to 3.28 and versionCode to 32.
- Source update only; run the included GitHub Actions workflow and test on multiple devices.

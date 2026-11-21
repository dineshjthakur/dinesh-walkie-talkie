package com.dinesh.walkietalkie;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class WalkieKeepAliveService extends Service {
    private static final String CHANNEL_ID = "jb_walkie_active";
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock cpuLock;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        try {
            WifiManager wm = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "JBWalkie:WifiLock");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        } catch (Exception ignored) {}
        try {
            PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
            cpuLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JBWalkie:CpuLock");
            cpuLock.setReferenceCounted(false);
            cpuLock.acquire();
        } catch (Exception ignored) {}
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "Walkie Talkie active", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Keeps the JB HOSPITALITY local Wi-Fi walkie-talkie connected.");
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(c);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification n = b.setSmallIcon(R.drawable.ic_walkie_notification)
                .setContentTitle("JB HOSPITALITY Walkie Talkie")
                .setContentText("Connected on local Wi-Fi • Return to app to talk")
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(41, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else startForeground(41, n);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Exception ignored) {}
        try { if (cpuLock != null && cpuLock.isHeld()) cpuLock.release(); } catch (Exception ignored) {}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}

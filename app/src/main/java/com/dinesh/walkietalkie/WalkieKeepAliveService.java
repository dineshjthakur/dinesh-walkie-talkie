package com.dinesh.walkietalkie;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.*;
import android.net.wifi.WifiManager;
import android.os.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WalkieKeepAliveService extends Service {
    private final Handler callTimeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable callTimeoutRunnable;
    private String pendingCallPeer;
    private final java.util.concurrent.ExecutorService networkSendExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    static final String UPDATE="com.dinesh.walkietalkie.LOCAL_UPDATE",GROUP="239.10.10.10";
    static final int PORT=47655,RATE=16000,FRAME=640,HEADER=33;
    static final byte HEARTBEAT=1,AUDIO=2,RING=3,CALL=4,ACCEPT=5,REJECT=6,END=7,PTT_AUDIO=8,RING_STOP=9;
    final UUID id=UUID.randomUUID();
    final Map<String,Long> seen=new ConcurrentHashMap<>();
    final Map<String,String> names=new ConcurrentHashMap<>();
    final Map<String,InetAddress> peerAddresses=new ConcurrentHashMap<>();
    Network wifiNetwork; NetworkInterface wifiInterface;
    volatile boolean running=false,ptt=false,callActive=false;
    volatile boolean autoEnabled=false;
    ConnectivityManager.NetworkCallback wifiCallback;
    volatile String peerId=null,peerName=null;
    String myName="Staff";
    MulticastSocket socket;InetAddress group;
    WifiManager.MulticastLock multicastLock;WifiManager.WifiLock wifiLock;PowerManager.WakeLock cpuLock;
    AudioRecord recorder;AudioTrack player;ToneGenerator tone; Ringtone alertRingtone;
    Thread micThread;

    @Override public void onCreate(){super.onCreate();createChannel();}
    void createChannel(){if(Build.VERSION.SDK_INT>=26){
        NotificationManager nm=getSystemService(NotificationManager.class);
        NotificationChannel c=new NotificationChannel("jb_walkie_active","JB Hospitality Walkie Talkie",NotificationManager.IMPORTANCE_HIGH);
        c.setDescription("Walkie Talkie foreground connection");
        nm.createNotificationChannel(c);
        NotificationChannel alerts=new NotificationChannel("jb_walkie_loud_alerts","JB Hospitality • Loud Ring and Calls",NotificationManager.IMPORTANCE_HIGH);
        alerts.setDescription("Loud repeating ringtone and vibration for staff rings and incoming calls");
        alerts.enableVibration(true);
        alerts.setVibrationPattern(new long[]{0,500,250,500,250,900});
        alerts.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        alerts.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        nm.createNotificationChannel(alerts);
    }}
    Notification makeNotification(String text){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));return new Notification.Builder(this,Build.VERSION.SDK_INT>=26?"jb_walkie_active":"").setSmallIcon(R.drawable.ic_walkie_notification).setContentTitle("JB HOSPITALITY Walkie Talkie").setContentText(text).setOngoing(true).setContentIntent(pi).build();}
    void notifyStatus(String t){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager.class).notify(41,makeNotification(t));}
    @Override public int onStartCommand(Intent i,int flags,int startId){if(i==null)return START_STICKY;String a=i.getAction();
        if("AUTO_START".equals(a)){autoEnabled=true;myName=i.getStringExtra("name");startForeground(41,makeNotification("Auto-connect enabled • waiting for Wi-Fi"));registerWifiMonitor();joinLan();}
        else if("JOIN".equals(a)){autoEnabled=true;myName=i.getStringExtra("name");if(myName==null||myName.trim().isEmpty())myName="Staff";startForeground(41,makeNotification("Joining local Wi-Fi"));joinLan();}
        else if("LEAVE".equals(a)){autoEnabled=false;unregisterWifiMonitor();leaveLan();stopForeground(true);stopSelf();}
        else if("PTT_START".equals(a)){ptt=true;startMicrophone();}
        else if("PTT_STOP".equals(a)){ptt=false;if(!callActive)stopMicrophone();}
        else if("RING".equals(a)){String target=i.getStringExtra("target");if(running&&target!=null){sendTarget(RING,target,new byte[0]);}else emit("Not connected to Wi-Fi channel",false);}
        else if("RING_STOP".equals(a)){String target=i.getStringExtra("target");if(running&&target!=null)sendTarget(RING_STOP,target,new byte[0]);}
        else if("CALL".equals(a)){callTarget(i.getStringExtra("target"),i.getStringExtra("targetName"));}
        else if("CALL_ACCEPT".equals(a)){acceptCall(i.getStringExtra("peer"));}
        else if("CALL_REJECT".equals(a)){String peer=i.getStringExtra("peer");cancelCallTimeout();stopLoudAlert();getSystemService(NotificationManager.class).cancel(43);sendTarget(REJECT,peer,new byte[0]);emitCall("Call declined",false);}
        else if("CALL_END".equals(a)){endCall(true);}
        return START_STICKY;
    }
    void joinLan(){if(running)return;try{ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);Network wifi=null;for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){wifi=n;break;}}if(wifi==null){emit("Waiting for restaurant Wi-Fi…",false);notifyStatus("Auto-connect enabled • waiting for Wi-Fi");return;}
        wifiNetwork=wifi;
        LinkProperties lp=cm.getLinkProperties(wifi);
        if(lp==null||lp.getInterfaceName()==null){emit("Cannot identify Wi-Fi interface",false);return;}
        wifiInterface=NetworkInterface.getByName(lp.getInterfaceName());
        if(wifiInterface==null){emit("Wi-Fi interface unavailable",false);return;}
        if(!cm.bindProcessToNetwork(wifi)){emit("Cannot bind Wi-Fi network",false);return;}
        WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);multicastLock=wm.createMulticastLock("JBWalkieMulticast");multicastLock.setReferenceCounted(false);multicastLock.acquire();wifiLock=wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"JBWalkieWifi");wifiLock.acquire();PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);cpuLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"JBWalkieCpu");cpuLock.acquire();
        group=InetAddress.getByName(GROUP);
        socket=new MulticastSocket(null);
        socket.setReuseAddress(true);
        wifi.bindSocket(socket);
        socket.setNetworkInterface(wifiInterface);
        socket.bind(new InetSocketAddress(PORT));
        socket.setTimeToLive(1);
        socket.joinGroup(new InetSocketAddress(group,PORT),wifiInterface);
        running=true;new Thread(this::receiveLoop,"LAN-RX").start();new Thread(this::heartbeatLoop,"LAN-Presence").start();emit("Connected • local Wi-Fi",true);notifyStatus("Connected • "+myName+" • background active");
    }catch(Exception e){emit("Wi-Fi error: "+e.getMessage(),false);leaveLan();stopSelf();}}
    void registerWifiMonitor(){
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            if(wifiCallback!=null)return;
            wifiCallback=new ConnectivityManager.NetworkCallback(){
                @Override public void onAvailable(Network n){
                    NetworkCapabilities c=cm.getNetworkCapabilities(n);
                    if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&autoEnabled&&!running){
                        new Handler(Looper.getMainLooper()).postDelayed(()->{if(autoEnabled&&!running)joinLan();},1200);
                    }
                }
                @Override public void onLost(Network n){
                    if(running){leaveLan();emit("Wi-Fi lost • waiting to reconnect",false);notifyStatus("Waiting for Wi-Fi");}
                }
            };
            cm.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),wifiCallback);
        }catch(Exception ignored){}
    }
    void unregisterWifiMonitor(){
        try{if(wifiCallback!=null){((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).unregisterNetworkCallback(wifiCallback);}}catch(Exception ignored){}
        wifiCallback=null;
    }
    byte[] uuidBytes(String s){try{UUID u=UUID.fromString(s);return ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();}catch(Exception e){return new byte[16];}}
    String uuidString(byte[] b,int o){ByteBuffer x=ByteBuffer.wrap(b,o,16);return new UUID(x.getLong(),x.getLong()).toString();}
    void sendPacket(byte type,String target,byte[] data){
        final byte[] payload = data == null ? new byte[0] : Arrays.copyOf(data, data.length);
        final String targetId = target;
        networkSendExecutor.execute(() -> {
            try{
                MulticastSocket currentSocket = socket;
                InetAddress currentGroup = group;
                if(currentSocket==null||currentSocket.isClosed()||currentGroup==null)return;
                byte[] p=new byte[HEADER+payload.length];
                p[0]=type;
                System.arraycopy(uuidBytes(id.toString()),0,p,1,16);
                System.arraycopy(uuidBytes(targetId),0,p,17,16);
                System.arraycopy(payload,0,p,HEADER,payload.length);
                currentSocket.send(new DatagramPacket(p,p.length,currentGroup,PORT));
            }catch(Exception e){
                emit("Network send error: "+e.getClass().getSimpleName(),false);
            }
        });
    }
    void sendTarget(byte type,String target,byte[] data){if(target!=null)sendPacket(type,target,data);}
    void heartbeatLoop(){while(running){try{byte[] n=myName.getBytes(StandardCharsets.UTF_8);if(n.length>48)n=Arrays.copyOf(n,48);sendPacket(HEARTBEAT,null,n);long now=System.currentTimeMillis();for(String k:new ArrayList<>(seen.keySet()))if(now-seen.get(k)>45000){seen.remove(k);names.remove(k);peerAddresses.remove(k);}publishUsers();Thread.sleep(2500);}catch(InterruptedException e){return;}}}
    void receiveLoop(){byte[] buf=new byte[1500];while(running){try{DatagramPacket p=new DatagramPacket(buf,buf.length);socket.receive(p);if(p.getLength()<HEADER)continue;byte type=p.getData()[p.getOffset()];String from=uuidString(p.getData(),p.getOffset()+1),target=uuidString(p.getData(),p.getOffset()+17);if(from.equals(id.toString()))continue;int len=p.getLength()-HEADER;int off=p.getOffset()+HEADER;
        if(type==HEARTBEAT){
            seen.put(from,System.currentTimeMillis());
            names.put(from,new String(p.getData(),off,len,StandardCharsets.UTF_8));
            peerAddresses.put(from,p.getAddress());
            publishUsers();
        }
        else if(!target.equals(id.toString())&&!target.equals("00000000-0000-0000-0000-000000000000"))continue;
        else if(type==AUDIO||type==PTT_AUDIO){if(type==PTT_AUDIO||callActive&&from.equals(peerId))play(p.getData(),off,len);}
        else if(type==RING){incomingRing(from);}
        else if(type==RING_STOP){stopLoudAlert();getSystemService(NotificationManager.class).cancel(44);emit("Ring stopped",false);}
        else if(type==CALL){incomingCall(from);}
        else if(type==ACCEPT){if(from.equals(peerId)){cancelCallTimeout();getSystemService(NotificationManager.class).cancel(43);callActive=true;notifyStatus("Two-way call with "+peerName);emitCall("Connected to "+peerName,true);startMicrophone();}}
        else if(type==REJECT){if(from.equals(peerId)){cancelCallTimeout();peerId=null;peerName=null;emitCall("Call declined",false);}}
        else if(type==END){if(from.equals(peerId)){stopLoudAlert();endCall(false);}}
    }catch(Exception e){if(running)emit("Receive error: "+e.getClass().getSimpleName(),false);}}}
    void publishUsers(){ArrayList<String> ns=new ArrayList<>(),ids=new ArrayList<>(),st=new ArrayList<>();ns.add(myName);ids.add("SELF");st.add("1");long now=System.currentTimeMillis();for(String k:seen.keySet()){ns.add(names.getOrDefault(k,"Staff"));ids.add(k);st.add(now-seen.get(k)<=9000?"1":"0");}Intent x=new Intent(UPDATE);x.setPackage(getPackageName());x.putStringArrayListExtra("users",ns);x.putStringArrayListExtra("ids",ids);x.putStringArrayListExtra("online",st);x.putExtra("count",(int)st.stream().filter("1"::equals).count());x.putExtra("joined",true);x.putExtra("ok",true);x.putExtra("status","Connected • local Wi-Fi");x.putExtra("callActive",callActive);sendBroadcast(x);}
    void emit(String s,boolean ok){Intent x=new Intent(UPDATE);x.setPackage(getPackageName());x.putExtra("status",s);x.putExtra("ok",ok);x.putExtra("joined",ok);sendBroadcast(x);}
    void emitCall(String s,boolean active){Intent x=new Intent(UPDATE);x.setPackage(getPackageName());x.putExtra("callStatus",s);x.putExtra("callActive",active);x.putExtra("joined",running);x.putExtra("ok",running);sendBroadcast(x);}
    void callTarget(String target,String name){if(!running||target==null){emitCall("Not connected to local Wi-Fi",false);return;}if(callActive){emitCall("Already in a call",true);return;}peerId=target;peerName=name;sendTarget(CALL,target,new byte[0]);startCallTimeout(target);emitCall("Calling "+name+"…",false);notifyStatus("Calling "+name);}
    void incomingCall(String from){
        String caller=names.getOrDefault(from,"Staff");
        startLoudAlert();
        startCallTimeout(from);
        Intent x=new Intent(UPDATE);x.setPackage(getPackageName());x.putExtra("incomingCaller",caller);x.putExtra("incomingId",from);x.putExtra("joined",true);x.putExtra("ok",true);sendBroadcast(x);
        showIncomingCallNotification(caller,from);
    }
    void showIncomingCallNotification(String caller,String from){
        Intent accept=new Intent(this,WalkieKeepAliveService.class);
        accept.setAction("CALL_ACCEPT");accept.putExtra("peer",from);
        PendingIntent acceptPi=PendingIntent.getService(this,101,accept,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Intent decline=new Intent(this,WalkieKeepAliveService.class);
        decline.setAction("CALL_REJECT");decline.putExtra("peer",from);
        PendingIntent declinePi=PendingIntent.getService(this,102,decline,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder b=new Notification.Builder(this,Build.VERSION.SDK_INT>=26?"jb_walkie_loud_alerts":"")
            .setSmallIcon(R.drawable.ic_walkie_notification)
            .setContentTitle("INCOMING CALL • JB HOSPITALITY")
            .setContentText(caller+" is calling • answer within 30 seconds")
            .setStyle(new Notification.BigTextStyle().bigText(caller+" is calling you on JB HOSPITALITY Walkie Talkie. The phone will ring for 30 seconds. Press ACCEPT CALL to answer."))
            .setCategory(Notification.CATEGORY_CALL).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setPriority(Notification.PRIORITY_MAX).setOngoing(true).setAutoCancel(false)
            .addAction(new Notification.Action.Builder(null,"ACCEPT CALL",acceptPi).build())
            .addAction(new Notification.Action.Builder(null,"DECLINE",declinePi).build());
        getSystemService(NotificationManager.class).notify(43,b.build());
    }
    void startCallTimeout(String peer){
        cancelCallTimeout();
        pendingCallPeer=peer;
        callTimeoutRunnable=()->{
            if(peer!=null&&peer.equals(pendingCallPeer)&&!callActive){
                stopLoudAlert();
                getSystemService(NotificationManager.class).cancel(43);
                if(peer.equals(peerId)){
                    sendTarget(END,peer,new byte[0]);
                    peerId=null;peerName=null;
                    emitCall("No answer • call timed out after 30 seconds",false);
                }else{
                    sendTarget(REJECT,peer,new byte[0]);
                    emitCall("Missed call from "+names.getOrDefault(peer,"Staff"),false);
                }
                pendingCallPeer=null;
            }
        };
        callTimeoutHandler.postDelayed(callTimeoutRunnable,30000);
    }
    void cancelCallTimeout(){
        if(callTimeoutRunnable!=null)callTimeoutHandler.removeCallbacks(callTimeoutRunnable);
        callTimeoutRunnable=null;pendingCallPeer=null;
    }
    void acceptCall(String peer){cancelCallTimeout();stopLoudAlert();getSystemService(NotificationManager.class).cancel(43);if(peer==null)return;peerId=peer;peerName=names.getOrDefault(peer,"Staff");sendTarget(ACCEPT,peer,new byte[0]);callActive=true;emitCall("Two-way call with "+peerName,true);notifyStatus("Two-way call with "+peerName);startMicrophone();}

    void startLoudAlert(){
        try{
            stopLoudAlert();
            Uri uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            if(uri==null)uri=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            alertRingtone=RingtoneManager.getRingtone(getApplicationContext(),uri);
            if(alertRingtone!=null){
                if(Build.VERSION.SDK_INT>=21)alertRingtone.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                if(Build.VERSION.SDK_INT>=28)alertRingtone.setLooping(true);
                alertRingtone.play();
            }
            if(Build.VERSION.SDK_INT>=26){
                Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);
                if(v!=null)v.vibrate(VibrationEffect.createWaveform(new long[]{0,500,250,500,250,900},0));
            }
        }catch(Exception e){emit("Alert playback error: "+e.getClass().getSimpleName(),false);}
    }
    void stopLoudAlert(){
        try{if(alertRingtone!=null&&alertRingtone.isPlaying())alertRingtone.stop();}catch(Exception ignored){}
        alertRingtone=null;
        try{Vibrator v=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(v!=null)v.cancel();}catch(Exception ignored){}
    }

    void incomingRing(String from){
        String caller=names.getOrDefault(from,"Staff");
        startLoudAlert();
        Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,44,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder b=new Notification.Builder(this,Build.VERSION.SDK_INT>=26?"jb_walkie_loud_alerts":"").setSmallIcon(R.drawable.ic_walkie_notification)
            .setContentTitle("Staff ring • JB HOSPITALITY").setContentText(caller+" is ringing this device").setAutoCancel(true).setContentIntent(pi);
        getSystemService(NotificationManager.class).notify(44,b.build());
        Intent x=new Intent(UPDATE);x.setPackage(getPackageName());x.putExtra("callStatus","RING: "+caller+" is calling");x.putExtra("joined",true);x.putExtra("ok",true);sendBroadcast(x);
    }
    void startMicrophone(){if(!running||recorder!=null)return;try{AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);am.setMode(AudioManager.MODE_IN_COMMUNICATION);am.setSpeakerphoneOn(true);int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,FRAME*8));if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Microphone init failed");recorder.startRecording();micThread=new Thread(()->{byte[] frame=new byte[FRAME];while(running&&(ptt||callActive)){int n=recorder!=null?recorder.read(frame,0,frame.length):-1;if(n>0){if(callActive&&peerId!=null)sendPacket(AUDIO,peerId,Arrays.copyOf(frame,n));else if(ptt)sendPacket(PTT_AUDIO,null,Arrays.copyOf(frame,n));}}},"LAN-Mic");micThread.start();}catch(Exception e){stopMicrophone();emitCall("Microphone error: "+e.getMessage(),false);}}
    void stopMicrophone(){try{if(recorder!=null){recorder.stop();recorder.release();}}catch(Exception ignored){}recorder=null;try{((AudioManager)getSystemService(AUDIO_SERVICE)).setMode(AudioManager.MODE_NORMAL);}catch(Exception ignored){}}
    synchronized void play(byte[] d,int off,int len){try{if(player==null){int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);player=new AudioTrack(AudioManager.STREAM_VOICE_CALL,RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,FRAME*16),AudioTrack.MODE_STREAM);player.play();}player.write(d,off,len);}catch(Exception ignored){}}
    void endCall(boolean send){cancelCallTimeout();stopLoudAlert();getSystemService(NotificationManager.class).cancel(43);String old=peerId;if(send&&old!=null)sendTarget(END,old,new byte[0]);callActive=false;peerId=null;peerName=null;if(!ptt)stopMicrophone();emitCall("Call ended",false);notifyStatus("Connected • local Wi-Fi");}
    void leaveLan(){running=false;ptt=false;endCall(false);stopMicrophone();try{if(socket!=null){socket.leaveGroup(new InetSocketAddress(group,PORT),wifiInterface);socket.close();}}catch(Exception ignored){}socket=null;wifiNetwork=null;wifiInterface=null;peerAddresses.clear();try{if(multicastLock!=null&&multicastLock.isHeld())multicastLock.release();}catch(Exception ignored){}try{if(wifiLock!=null&&wifiLock.isHeld())wifiLock.release();}catch(Exception ignored){}try{if(cpuLock!=null&&cpuLock.isHeld())cpuLock.release();}catch(Exception ignored){}try{if(player!=null){player.stop();player.release();}}catch(Exception ignored){}player=null;try{((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).bindProcessToNetwork(null);}catch(Exception ignored){}}
    @Override public void onTaskRemoved(Intent i){super.onTaskRemoved(i);}
    @Override public void onDestroy(){cancelCallTimeout();stopLoudAlert();networkSendExecutor.shutdownNow();autoEnabled=false;unregisterWifiMonitor();leaveLan();if(tone!=null)tone.release();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}

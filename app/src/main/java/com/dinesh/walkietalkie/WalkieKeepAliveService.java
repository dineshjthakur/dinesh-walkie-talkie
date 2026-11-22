package com.dinesh.walkietalkie;

import android.app.*;
import android.content.*;
import android.media.*;
import android.net.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.media.ToneGenerator;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WalkieKeepAliveService extends Service {
    static final String UPDATE="com.dinesh.walkietalkie.LOCAL_UPDATE";
    static final String GROUP="239.10.10.10";
    static final int PORT=47655,RATE=16000,FRAME=640;
    static final byte HEARTBEAT=1,AUDIO=2,RING=3;
    final UUID id=UUID.randomUUID();
    final Map<String,Long> lastSeen=new ConcurrentHashMap<>();
    final Map<String,String> names=new ConcurrentHashMap<>();
    volatile boolean running=false,talking=false;
    String myName="Staff";
    MulticastSocket socket; InetAddress group;
    WifiManager.MulticastLock multicastLock; WifiManager.WifiLock wifiLock;
    PowerManager.WakeLock cpuLock;
    AudioRecord recorder; AudioTrack player;
    Handler handler=new Handler(Looper.getMainLooper());
    ToneGenerator tone;
    @Override public void onCreate(){super.onCreate();createNotificationChannel();}
    void createNotificationChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("jb_walkie_active","JB Hospitality Walkie Talkie",NotificationManager.IMPORTANCE_HIGH);c.setDescription("Local Wi-Fi walkie-talkie, staff rings and incoming alerts");getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    Notification notification(String text){
        Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        return new Notification.Builder(this,Build.VERSION.SDK_INT>=26?"jb_walkie_active":"")
            .setSmallIcon(R.drawable.ic_walkie_notification).setContentTitle("JB HOSPITALITY Walkie Talkie").setContentText(text).setOngoing(true).setContentIntent(pi).build();
    }
    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(i==null)return START_STICKY;
        String a=i.getAction();
        if("JOIN".equals(a)){myName=i.getStringExtra("name");if(myName==null||myName.trim().isEmpty())myName="Staff";startForeground(41,notification("Connecting to local Wi-Fi channel"));joinLan();}
        else if("LEAVE".equals(a)){leaveLan();stopForeground(true);stopSelf();}
        else if("TALK_START".equals(a))startTalk();
        else if("TALK_STOP".equals(a))stopTalk();
        else if("RING".equals(a)){String target=i.getStringExtra("target");String nm=i.getStringExtra("targetName");sendRing(target);broadcast("Ringing "+nm,true,null);}
        return START_STICKY;
    }
    private void joinLan(){
        if(running)return;
        try{
            ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);Network wifi=null;
            for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){wifi=n;break;}}
            if(wifi==null){broadcast("Connect to the restaurant Wi-Fi",false,null);stopSelf();return;}
            if(!cm.bindProcessToNetwork(wifi)){broadcast("Could not bind to Wi-Fi network",false,null);stopSelf();return;}
            WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            multicastLock=wm.createMulticastLock("JBWalkieMulticast");multicastLock.setReferenceCounted(false);multicastLock.acquire();
            wifiLock=wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"JBWalkie:WifiLock");wifiLock.acquire();
            PowerManager pm=(PowerManager)getSystemService(POWER_SERVICE);cpuLock=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"JBWalkie:CpuLock");cpuLock.acquire();
            group=InetAddress.getByName(GROUP);socket=new MulticastSocket(null);socket.setReuseAddress(true);socket.bind(new InetSocketAddress(PORT));socket.setTimeToLive(1);socket.joinGroup(group);
            running=true;
            new Thread(this::receiveLoop,"LocalWiFiReceiver").start();
            new Thread(this::heartbeatLoop,"LocalWiFiPresence").start();
            broadcast("Connected to local JB HOSPITALITY Wi-Fi",true,null);
            updateNotification("Connected • "+myName+" • background active");
        }catch(Exception e){broadcast("Wi-Fi channel error: "+e.getMessage(),false,null);leaveLan();stopSelf();}
    }
    byte[] idBytes(){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
    void send(byte type,byte[] data,int len){try{byte[] p=new byte[17+len];p[0]=type;System.arraycopy(idBytes(),0,p,1,16);if(len>0)System.arraycopy(data,0,p,17,len);socket.send(new DatagramPacket(p,p.length,group,PORT));}catch(Exception ignored){}}
    void heartbeatLoop(){while(running){try{byte[] n=myName.getBytes(StandardCharsets.UTF_8);if(n.length>48)n=Arrays.copyOf(n,48);send(HEARTBEAT,n,n.length);long now=System.currentTimeMillis();for(String k:new ArrayList<>(lastSeen.keySet()))if(now-lastSeen.get(k)>45000){lastSeen.remove(k);}publishUsers();Thread.sleep(3000);}catch(InterruptedException e){return;}}}
    void receiveLoop(){byte[] b=new byte[1500];while(running){try{DatagramPacket p=new DatagramPacket(b,b.length);socket.receive(p);if(p.getLength()<17)continue;ByteBuffer ib=ByteBuffer.wrap(p.getData(),p.getOffset()+1,16);UUID from=new UUID(ib.getLong(),ib.getLong());if(from.equals(id))continue;String key=from.toString();int len=p.getLength()-17;byte type=p.getData()[p.getOffset()];if(type==HEARTBEAT){lastSeen.put(key,System.currentTimeMillis());names.put(key,new String(p.getData(),p.getOffset()+17,len,StandardCharsets.UTF_8));publishUsers();}else if(type==AUDIO&&len>0)play(p.getData(),p.getOffset()+17,len);else if(type==RING&&len==16){String target=new String(p.getData(),p.getOffset()+17,len,StandardCharsets.UTF_8);if(target.equals(id.toString()))incomingRing(names.getOrDefault(key,"A staff member"));}}catch(Exception ignored){}}}
    void publishUsers(){
        ArrayList<String> ns=new ArrayList<>(),ids=new ArrayList<>(),st=new ArrayList<>();ns.add(myName);ids.add("SELF");st.add("1");long now=System.currentTimeMillis();
        for(String k:lastSeen.keySet()){ns.add(names.getOrDefault(k,"Staff"));ids.add(k);st.add(now-lastSeen.get(k)<=12000?"1":"0");}
        Intent x=new Intent(UPDATE);x.setPackage(getPackageName());x.putStringArrayListExtra("users",ns);x.putStringArrayListExtra("ids",ids);x.putStringArrayListExtra("online",st);x.putExtra("count",(int)st.stream().filter(v->"1".equals(v)).count());x.putExtra("joined",true);x.putExtra("ok",true);x.putExtra("status","Connected • local Wi-Fi");sendBroadcast(x);
    }
    void broadcast(String status,boolean ok,String alert){Intent x=new Intent(UPDATE);x.setPackage(getPackageName());x.putExtra("status",status);x.putExtra("ok",ok);x.putExtra("joined",ok);if(alert!=null)x.putExtra("alert",alert);sendBroadcast(x);}
    void sendRing(String target){try{byte[] t=target.getBytes(StandardCharsets.UTF_8);send(RING,t,t.length);}catch(Exception ignored){}}
    void incomingRing(String sender){
        try{if(tone==null)tone=new ToneGenerator(AudioManager.STREAM_ALARM,100);tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,1800);}catch(Exception ignored){}
        try{((android.os.Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createWaveform(new long[]{0,350,180,350,180,700},-1));}catch(Exception ignored){}
        Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,2,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder nb=new Notification.Builder(this,Build.VERSION.SDK_INT>=26?"jb_walkie_active":"").setSmallIcon(R.drawable.ic_walkie_notification).setContentTitle("JB HOSPITALITY — Incoming ring").setContentText(sender+" is calling you").setAutoCancel(true).setContentIntent(pi);
        getSystemService(NotificationManager.class).notify(42,nb.build());broadcast("Incoming ring from "+sender,true,"RING: "+sender+" is calling");
    }
    void startTalk(){if(talking||!running)return;try{int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,FRAME*4));recorder.startRecording();talking=true;new Thread(()->{byte[] f=new byte[FRAME];while(talking&&running){int n=recorder.read(f,0,f.length);if(n>0)send(AUDIO,f,n);}},"LanMic").start();}catch(Exception e){talking=false;broadcast("Microphone error: "+e.getMessage(),false,null);}}
    void stopTalk(){talking=false;try{if(recorder!=null){recorder.stop();recorder.release();}}catch(Exception ignored){}recorder=null;}
    synchronized void play(byte[] d,int o,int l){try{if(player==null){int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);player=new AudioTrack(AudioManager.STREAM_VOICE_CALL,RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,FRAME*8),AudioTrack.MODE_STREAM);player.play();}player.write(d,o,l);}catch(Exception ignored){}}
    void leaveLan(){running=false;stopTalk();try{if(socket!=null){socket.leaveGroup(group);socket.close();}}catch(Exception ignored){}socket=null;try{if(multicastLock!=null&&multicastLock.isHeld())multicastLock.release();}catch(Exception ignored){}try{if(wifiLock!=null&&wifiLock.isHeld())wifiLock.release();}catch(Exception ignored){}try{if(cpuLock!=null&&cpuLock.isHeld())cpuLock.release();}catch(Exception ignored){}try{if(player!=null){player.stop();player.release();}}catch(Exception ignored){}player=null;try{((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).bindProcessToNetwork(null);}catch(Exception ignored){}}
    void updateNotification(String t){getSystemService(NotificationManager.class).notify(41,notification(t));}
    @Override public void onTaskRemoved(Intent rootIntent){super.onTaskRemoved(rootIntent);/* Keep foreground LAN service alive when app is swiped from Recents. */}
    @Override public void onDestroy(){leaveLan();try{if(tone!=null)tone.release();}catch(Exception ignored){}super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}

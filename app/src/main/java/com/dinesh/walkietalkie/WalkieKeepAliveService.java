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
    static final String UPDATE="com.dinesh.walkietalkie.LOCAL_UPDATE",GROUP="239.10.10.10";
    static final int PORT=47655,RATE=16000,FRAME=640,HEADER=33;
    static final byte HEARTBEAT=1,AUDIO=2,RING=3,CALL=4,ACCEPT=5,REJECT=6,END=7,PTT_AUDIO=8;
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
    AudioRecord recorder;AudioTrack player;ToneGenerator tone;
    Thread micThread;

    @Override public void onCreate(){super.onCreate();createChannel();}
    void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("jb_walkie_active","JB Hospitality Walkie Talkie",NotificationManager.IMPORTANCE_HIGH);c.setDescription("Local Wi-Fi calls and staff alerts");getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    Notification makeNotification(String text){Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));return new Notification.Builder(this,Build.VERSION.SDK_INT>=26?"jb_walkie_active":"").setSmallIcon(R.drawable.ic_walkie_notification).setContentTitle("JB HOSPITALITY Walkie Talkie").setContentText(text).setOngoing(true).setContentIntent(pi).build();}
    void notifyStatus(String t){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager.class).notify(41,makeNotification(t));}
    @Override public int onStartCommand(Intent i,int flags,int startId){if(i==null)return START_STICKY;String a=i.getAction();
        if("AUTO_START".equals(a)){autoEnabled=true;myName=i.getStringExtra("name");startForeground(41,makeNotification("Auto-connect enabled • waiting for Wi-Fi"));registerWifiMonitor();joinLan();}
        else if("JOIN".equals(a)){autoEnabled=true;myName=i.getStringExtra("name");if(myName==null||myName.trim().isEmpty())myName="Staff";startForeground(41,makeNotification("Joining local Wi-Fi"));joinLan();}
        else if("LEAVE".equals(a)){autoEnabled=false;unregisterWifiMonitor();leaveLan();stopForeground(true);stopSelf();}
        else if("PTT_START".equals(a)){ptt=true;startMicrophone();}
        else if("PTT_STOP".equals(a)){ptt=false;if(!callActive)stopMicrophone();}
        else if("RING".equals(a)){String target=i.getStringExtra("target");if(running&&target!=null){sendTarget(RING,target,new byte[0]);notifyStatus("Ringing selected staff member");}else emit("Not connected to Wi-Fi channel",false);}
        else if("CALL".equals(a)){callTarget(i.getStringExtra("target"),i.getStringExtra("targetName"));}
        else if("CALL_ACCEPT".equals(a)){acceptCall(i.getStringExtra("peer"));}
        else if("CALL_REJECT".equals(a)){sendTarget(REJECT,i.getStringExtra("peer"),new byte[0]);}
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
        try{
            if(socket==null||socket.isClosed())return;
            byte[] p=new byte[HEADER+data.length];
            p[0]=type;
            System.arraycopy(uuidBytes(id.toString()),0,p,1,16);
            System.arraycopy(uuidBytes(target),0,p,17,16);
            System.arraycopy(data,0,p,HEADER,data.length);
            InetAddress destination=group;
            boolean privatePacket=target!=null&&!target.equals("00000000-0000-0000-0000-000000000000");
            if(privatePacket){
                InetAddress peerAddress=peerAddresses.get(target);
                if(peerAddress!=null)destination=peerAddress;
            }
            socket.send(new DatagramPacket(p,p.length,destination,PORT));
        }catch(Exception e){emit("Network send error: "+e.getClass().getSimpleName(),false);}
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
        else if(type==CALL){incomingCall(from);}
        else if(type==ACCEPT){if(from.equals(peerId)){callActive=true;notifyStatus("Two-way call with "+peerName);emitCall("Connected to "+peerName,true);startMicrophone();}}
        else if(type==REJECT){if(from.equals(peerId)){peerId=null;peerName=null;emitCall("Call declined",false);}}
        else if(type==END){if(from.equals(peerId))endCall(false);}
    }catch(Exception ignored){}}}
    void publishUsers(){ArrayList<String> ns=new ArrayList<>(),ids=new ArrayList<>(),st=new ArrayList<>();ns.add(myName);ids.add("SELF");st.add("1");long now=System.currentTimeMillis();for(String k:seen.keySet()){ns.add(names.getOrDefault(k,"Staff"));ids.add(k);st.add(now-seen.get(k)<=9000?"1":"0");}Intent x=new Intent(UPDATE);x.setPackage(getPackageName());x.putStringArrayListExtra("users",ns);x.putStringArrayListExtra("ids",ids);x.putStringArrayListExtra("online",st);x.putExtra("count",(int)st.stream().filter("1"::equals).count());x.putExtra("joined",true);x.putExtra("ok",true);x.putExtra("status","Connected • local Wi-Fi");x.putExtra("callActive",callActive);sendBroadcast(x);}
    void emit(String s,boolean ok){Intent x=new Intent(UPDATE);x.setPackage(getPackageName());x.putExtra("status",s);x.putExtra("ok",ok);x.putExtra("joined",ok);sendBroadcast(x);}
    void emitCall(String s,boolean active){Intent x=new Intent(UPDATE);x.setPackage(getPackageName());x.putExtra("callStatus",s);x.putExtra("callActive",active);x.putExtra("joined",running);x.putExtra("ok",running);sendBroadcast(x);}
    void callTarget(String target,String name){if(!running||target==null){emitCall("Not connected to local Wi-Fi",false);return;}if(callActive){emitCall("Already in a call",true);return;}peerId=target;peerName=name;sendTarget(CALL,target,new byte[0]);emitCall("Calling "+name+"…",false);notifyStatus("Calling "+name);}
    void incomingCall(String from){
        String caller=names.getOrDefault(from,"Staff");
        Intent x=new Intent(UPDATE);x.setPackage(getPackageName());x.putExtra("incomingCaller",caller);x.putExtra("incomingId",from);x.putExtra("joined",true);x.putExtra("ok",true);sendBroadcast(x);
        showIncomingCallNotification(caller,from);
    }
    void showIncomingCallNotification(String caller,String from){
        Intent accept=new Intent(this,WalkieKeepAliveService.class);accept.setAction("CALL_ACCEPT");accept.putExtra("peer",from);
        PendingIntent acceptPi=PendingIntent.getService(this,101,accept,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Intent decline=new Intent(this,WalkieKeepAliveService.class);decline.setAction("CALL_REJECT");decline.putExtra("peer",from);
        PendingIntent declinePi=PendingIntent.getService(this,102,decline,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder b=new Notification.Builder(this,Build.VERSION.SDK_INT>=26?"jb_walkie_active":"")
            .setSmallIcon(R.drawable.ic_walkie_notification).setContentTitle("Incoming call • JB HOSPITALITY")
            .setContentText(caller+" is calling").setCategory(Notification.CATEGORY_CALL).setPriority(Notification.PRIORITY_MAX)
            .setAutoCancel(true).addAction(new Notification.Action.Builder(null,"ANSWER",acceptPi).build())
            .addAction(new Notification.Action.Builder(null,"DECLINE",declinePi).build());
        getSystemService(NotificationManager.class).notify(43,b.build());
    }
    void acceptCall(String peer){if(peer==null)return;peerId=peer;peerName=names.getOrDefault(peer,"Staff");sendTarget(ACCEPT,peer,new byte[0]);callActive=true;emitCall("Two-way call with "+peerName,true);notifyStatus("Two-way call with "+peerName);startMicrophone();}
    void incomingRing(String from){
        String caller=names.getOrDefault(from,"Staff");
        try{if(tone==null)tone=new ToneGenerator(AudioManager.STREAM_ALARM,100);tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,1800);}catch(Exception ignored){}
        try{if(Build.VERSION.SDK_INT>=26)((android.os.Vibrator)getSystemService(VIBRATOR_SERVICE)).vibrate(VibrationEffect.createWaveform(new long[]{0,350,180,350,180,700},-1));}catch(Exception ignored){}
        Intent open=new Intent(this,MainActivity.class);PendingIntent pi=PendingIntent.getActivity(this,44,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        Notification.Builder b=new Notification.Builder(this,Build.VERSION.SDK_INT>=26?"jb_walkie_active":"").setSmallIcon(R.drawable.ic_walkie_notification)
            .setContentTitle("Staff ring • JB HOSPITALITY").setContentText(caller+" is ringing this device").setAutoCancel(true).setContentIntent(pi);
        getSystemService(NotificationManager.class).notify(44,b.build());
        Intent x=new Intent(UPDATE);x.setPackage(getPackageName());x.putExtra("callStatus","RING: "+caller+" is calling");x.putExtra("joined",true);x.putExtra("ok",true);sendBroadcast(x);
    }
    void startMicrophone(){if(!running||recorder!=null)return;try{AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);am.setMode(AudioManager.MODE_IN_COMMUNICATION);am.setSpeakerphoneOn(true);int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,FRAME*8));if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Microphone init failed");recorder.startRecording();micThread=new Thread(()->{byte[] frame=new byte[FRAME];while(running&&(ptt||callActive)){int n=recorder!=null?recorder.read(frame,0,frame.length):-1;if(n>0){if(callActive&&peerId!=null)sendPacket(AUDIO,peerId,Arrays.copyOf(frame,n));else if(ptt)sendPacket(PTT_AUDIO,null,Arrays.copyOf(frame,n));}}},"LAN-Mic");micThread.start();}catch(Exception e){stopMicrophone();emitCall("Microphone error: "+e.getMessage(),false);}}
    void stopMicrophone(){try{if(recorder!=null){recorder.stop();recorder.release();}}catch(Exception ignored){}recorder=null;try{((AudioManager)getSystemService(AUDIO_SERVICE)).setMode(AudioManager.MODE_NORMAL);}catch(Exception ignored){}}
    synchronized void play(byte[] d,int off,int len){try{if(player==null){int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);player=new AudioTrack(AudioManager.STREAM_VOICE_CALL,RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,FRAME*16),AudioTrack.MODE_STREAM);player.play();}player.write(d,off,len);}catch(Exception ignored){}}
    void endCall(boolean send){String old=peerId;if(send&&old!=null)sendTarget(END,old,new byte[0]);callActive=false;peerId=null;peerName=null;if(!ptt)stopMicrophone();emitCall("Call ended",false);notifyStatus("Connected • local Wi-Fi");}
    void leaveLan(){running=false;ptt=false;endCall(false);stopMicrophone();try{if(socket!=null){socket.leaveGroup(new InetSocketAddress(group,PORT),wifiInterface);socket.close();}}catch(Exception ignored){}socket=null;wifiNetwork=null;wifiInterface=null;peerAddresses.clear();try{if(multicastLock!=null&&multicastLock.isHeld())multicastLock.release();}catch(Exception ignored){}try{if(wifiLock!=null&&wifiLock.isHeld())wifiLock.release();}catch(Exception ignored){}try{if(cpuLock!=null&&cpuLock.isHeld())cpuLock.release();}catch(Exception ignored){}try{if(player!=null){player.stop();player.release();}}catch(Exception ignored){}player=null;try{((ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE)).bindProcessToNetwork(null);}catch(Exception ignored){}}
    @Override public void onTaskRemoved(Intent i){super.onTaskRemoved(i);}
    @Override public void onDestroy(){autoEnabled=false;unregisterWifiMonitor();leaveLan();if(tone!=null)tone.release();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}

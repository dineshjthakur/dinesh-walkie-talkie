package com.dinesh.walkietalkie;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MainActivity extends Activity {
    private static final String CHANNEL="JB HOSPITALITY", GROUP="239.10.10.10";
    private static final int PORT=47655, RATE=16000, FRAME=640;
    private static final byte HEARTBEAT=1, AUDIO=2;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final UUID id=UUID.randomUUID();
    private final Map<String,Long> users=new ConcurrentHashMap<>();
    private final Map<String,String> userNames=new ConcurrentHashMap<>();
    private EditText nameBox;
    private TextView networkLabel,statusLabel,countLabel,callLabel,userList;
    private Button joinButton,talkButton;
    private ConnectivityManager cm;
    private volatile boolean joined=false,running=false,talking=false;
    private MulticastSocket socket;
    private InetAddress group;
    private WifiManager.MulticastLock lock;
    private AudioRecord recorder;
    private AudioTrack player;
    private String myName="Dinesh";

    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable shape(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    private TextView txt(String s,int z,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);return t;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b); cm=(ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(18),dp(22),dp(18),dp(16));root.setBackgroundColor(Color.rgb(242,247,244));setContentView(root);
        TextView brand=txt("JB HOSPITALITY",15,Color.rgb(4,95,67));brand.setTypeface(null,Typeface.BOLD);root.addView(brand);
        TextView title=txt("DINESH THAKUR\nWALKIE TALKIE",25,Color.rgb(5,65,48));title.setTypeface(null,Typeface.BOLD);title.setPadding(0,dp(8),0,dp(5));root.addView(title);
        TextView ch=txt("LOCAL WI-FI CHANNEL: "+CHANNEL,12,Color.DKGRAY);ch.setPadding(0,0,0,dp(14));root.addView(ch);
        nameBox=new EditText(this);nameBox.setSingleLine(true);nameBox.setText(getPreferences(0).getString("name","Dinesh"));nameBox.setHint("Your staff name");nameBox.setPadding(dp(12),0,dp(12),0);nameBox.setBackground(shape(Color.WHITE,10));root.addView(nameBox,new LinearLayout.LayoutParams(-1,dp(50)));
        joinButton=new Button(this);joinButton.setText("JOIN LOCAL WI-FI CHANNEL");joinButton.setAllCaps(false);LinearLayout.LayoutParams jp=new LinearLayout.LayoutParams(-1,dp(50));jp.topMargin=dp(10);root.addView(joinButton,jp);joinButton.setOnClickListener(v->{if(joined)leave();else join();});
        networkLabel=txt("Wi-Fi: checking…",13,Color.DKGRAY);networkLabel.setPadding(0,dp(9),0,dp(3));root.addView(networkLabel);
        statusLabel=txt("Not connected",13,Color.RED);statusLabel.setPadding(0,dp(2),0,dp(12));root.addView(statusLabel);
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(shape(Color.WHITE,14));root.addView(card);
        callLabel=txt("Ready to join the local channel",16,Color.rgb(20,55,42));callLabel.setTypeface(null,Typeface.BOLD);card.addView(callLabel);
        talkButton=new Button(this);talkButton.setText("HOLD TO TALK");talkButton.setAllCaps(false);talkButton.setTextSize(18);talkButton.setTextColor(Color.WHITE);talkButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(20,130,80)));talkButton.setEnabled(false);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(88));tp.topMargin=dp(12);card.addView(talkButton,tp);
        talkButton.setOnTouchListener((v,e)->{if(!joined)return false;if(e.getAction()==MotionEvent.ACTION_DOWN){startTalk();return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){stopTalk();return true;}return true;});
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(0,dp(18),0,dp(8));TextView uh=txt("CHANNEL USERS",17,Color.rgb(4,95,67));uh.setTypeface(null,Typeface.BOLD);head.addView(uh,new LinearLayout.LayoutParams(0,-2,1));countLabel=txt("0 online",12,Color.DKGRAY);head.addView(countLabel);root.addView(head);
        TextView note=txt("Same Wi-Fi router required. No internet, mobile data or cloud server.",11,Color.GRAY);note.setGravity(Gravity.CENTER);note.setPadding(0,dp(12),0,0);root.addView(note);
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},11);
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},12);
        refreshWifi();
    }

    private Network getWifi(){try{for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return n;}}catch(Exception ignored){}return null;}
    private void refreshWifi(){if(getWifi()!=null){networkLabel.setText("Wi-Fi connected • Local network mode");networkLabel.setTextColor(Color.rgb(15,130,80));}else{networkLabel.setText("Connect to the restaurant Wi-Fi");networkLabel.setTextColor(Color.RED);}}
    private byte[] idBytes(){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
    private void send(byte type,byte[] data,int len){try{byte[] p=new byte[17+len];p[0]=type;System.arraycopy(idBytes(),0,p,1,16);if(len>0)System.arraycopy(data,0,p,17,len);socket.send(new DatagramPacket(p,p.length,group,PORT));}catch(Exception ignored){}}

    private void join(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},11);toast("Allow microphone permission, then join again.");return;}
        Network w=getWifi();if(w==null){refreshWifi();toast("Connect to the same Wi-Fi router first.");return;}
        try{
            if(!cm.bindProcessToNetwork(w)){toast("Could not select Wi-Fi. Reconnect and retry.");return;}
            myName=nameBox.getText().toString().trim();if(myName.isEmpty())myName="Staff";if(myName.length()>24)myName=myName.substring(0,24);getPreferences(0).edit().putString("name",myName).apply();
            WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);lock=wm.createMulticastLock("JBWalkieMulticast");lock.setReferenceCounted(false);lock.acquire();
            group=InetAddress.getByName(GROUP);socket=new MulticastSocket(null);socket.setReuseAddress(true);socket.bind(new InetSocketAddress(PORT));socket.setTimeToLive(1);socket.joinGroup(group);
            running=true;joined=true; startKeepAliveService(); new Thread(this::receive,"LanRx").start();new Thread(this::presence,"LanPresence").start();
            talkButton.setEnabled(true);joinButton.setText("LEAVE CHANNEL");statusLabel.setText("Joined local JB HOSPITALITY channel");statusLabel.setTextColor(Color.rgb(15,130,80));callLabel.setText("Press and hold to talk");refreshWifi();
        }catch(Exception e){cleanup();toast("Cannot join Wi-Fi channel: "+e.getMessage());}
    }
    private void presence(){while(running){try{byte[] n=myName.getBytes(StandardCharsets.UTF_8);if(n.length>48)n=java.util.Arrays.copyOf(n,48);send(HEARTBEAT,n,n.length);renderUsers();Thread.sleep(3000);}catch(InterruptedException e){return;}}}
    private void renderUsers(){
        long now=System.currentTimeMillis();
        int online=1;
        StringBuilder list=new StringBuilder("●  ").append(myName).append("  —  ONLINE (You)");
        for(Map.Entry<String,Long> entry:users.entrySet()){
            boolean isOnline=now-entry.getValue()<=12000;
            if(isOnline) online++;
            String nm=userNames.getOrDefault(entry.getKey(),"Unknown device");
            list.append("\\n").append(isOnline?"●  ":"○  ").append(nm).append("  —  ").append(isOnline?"ONLINE":"OFFLINE");
        }
        final String display=list.toString();
        final int total=online;
        main.post(()->{countLabel.setText(total+" online");userList.setText(display);});
    }
    private void receive(){byte[] b=new byte[1500];while(running){try{DatagramPacket p=new DatagramPacket(b,b.length);socket.receive(p);if(p.getLength()<17)continue;ByteBuffer ib=ByteBuffer.wrap(p.getData(),p.getOffset()+1,16);UUID from=new UUID(ib.getLong(),ib.getLong());if(from.equals(id))continue;int len=p.getLength()-17;byte type=p.getData()[p.getOffset()];if(type==HEARTBEAT){users.put(from.toString(),System.currentTimeMillis());String nm=new String(p.getData(),p.getOffset()+17,len,StandardCharsets.UTF_8);userNames.put(from.toString(),nm);renderUsers();}else if(type==AUDIO&&len>0)play(p.getData(),p.getOffset()+17,len);}catch(Exception ignored){}}}
    private void startTalk(){if(talking||!joined)return;try{int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,FRAME*4));if(recorder.getState()!=AudioRecord.STATE_INITIALIZED){recorder.release();recorder=null;toast("Microphone unavailable.");return;}recorder.startRecording();talking=true;talkButton.setText("TALKING… RELEASE TO STOP");talkButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.RED));callLabel.setText("You are speaking");new Thread(()->{byte[] frame=new byte[FRAME];while(talking&&running){int n=recorder.read(frame,0,frame.length);if(n>0)send(AUDIO,frame,n);}},"LanMic").start();}catch(Exception e){talking=false;toast("Microphone error: "+e.getMessage());}}
    private synchronized void play(byte[] data,int off,int len){try{if(player==null){int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);player=new AudioTrack(AudioManager.STREAM_VOICE_CALL,RATE,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,FRAME*8),AudioTrack.MODE_STREAM);player.play();}player.write(data,off,len);}catch(Exception ignored){}}
    private void stopTalk(){if(!talking)return;talking=false;try{if(recorder!=null){recorder.stop();recorder.release();}}catch(Exception ignored){}recorder=null;talkButton.setText("HOLD TO TALK");talkButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(20,130,80)));callLabel.setText("Press and hold to talk");}
    private void leave(){stopTalk();joined=false;running=false;try{stopService(new android.content.Intent(this,WalkieKeepAliveService.class));}catch(Exception ignored){}cleanup();users.clear();userNames.clear();renderUsers();talkButton.setEnabled(false);joinButton.setText("JOIN LOCAL WI-FI CHANNEL");statusLabel.setText("Not connected");statusLabel.setTextColor(Color.RED);callLabel.setText("Left local channel");countLabel.setText("0 online");userList.setText("No active channel. Join to see staff status.");try{cm.bindProcessToNetwork(null);}catch(Exception ignored){}refreshWifi();}
    private void cleanup(){running=false;try{if(socket!=null){socket.leaveGroup(group);socket.close();}}catch(Exception ignored){}socket=null;try{if(lock!=null&&lock.isHeld())lock.release();}catch(Exception ignored){}lock=null;try{if(player!=null){player.stop();player.release();}}catch(Exception ignored){}player=null;}

    private void startKeepAliveService(){
        try{
            android.content.Intent i=new android.content.Intent(this,WalkieKeepAliveService.class);
            if(android.os.Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
        }catch(Exception e){toast("Background service could not start: "+e.getMessage());}
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==11&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)toast("Microphone permission granted. Join the channel.");}
    @Override protected void onDestroy(){if(joined)leave();else cleanup();super.onDestroy();}
    @Override public void onBackPressed(){if(joined)leave();super.onBackPressed();}
}

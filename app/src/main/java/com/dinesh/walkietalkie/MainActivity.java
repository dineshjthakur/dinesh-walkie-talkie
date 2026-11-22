package com.dinesh.walkietalkie;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    static final String PKG="com.dinesh.walkietalkie";
    static final String SVC=PKG+".WalkieKeepAliveService";
    static final String UPDATE=PKG+".LOCAL_UPDATE";
    LinearLayout root, userRows;
    EditText nameBox;
    TextView networkLabel,statusLabel,countLabel,callLabel;
    Button joinButton,talkButton;
    boolean joined=false, receiverRegistered=false;
    BroadcastReceiver updates;

    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    GradientDrawable shape(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    TextView txt(String s,int z,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);return t;}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        buildUi();
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},11);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},12);
        updates=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            if(UPDATE.equals(i.getAction())){
                joined=i.getBooleanExtra("joined",joined);
                String status=i.getStringExtra("status"); if(status!=null)statusLabel.setText(status);
                boolean ok=i.getBooleanExtra("ok",joined);
                statusLabel.setTextColor(ok?Color.rgb(15,130,80):Color.RED);
                int count=i.getIntExtra("count",0); countLabel.setText(count+" online");
                renderRows(i.getStringArrayListExtra("users"),i.getStringArrayListExtra("ids"),i.getStringArrayListExtra("online"));
                joinButton.setText(joined?"LEAVE CHANNEL":"JOIN LOCAL WI-FI CHANNEL");
                talkButton.setEnabled(joined);
                String alert=i.getStringExtra("alert"); if(alert!=null)callLabel.setText(alert);
            }
        }};
        registerReceiver(updates,new IntentFilter(UPDATE),Context.RECEIVER_NOT_EXPORTED); receiverRegistered=true;
        refreshWifi();
    }
    void buildUi(){
        root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(18),dp(20),dp(18),dp(16));root.setBackgroundColor(Color.rgb(242,247,244));setContentView(root);
        TextView brand=txt("JB HOSPITALITY",15,Color.rgb(4,95,67));brand.setTypeface(null,Typeface.BOLD);root.addView(brand);
        TextView title=txt("DINESH THAKUR\\nWALKIE TALKIE",25,Color.rgb(5,65,48));title.setTypeface(null,Typeface.BOLD);title.setPadding(0,dp(8),0,dp(5));root.addView(title);
        root.addView(txt("LOCAL WI-FI CHANNEL: JB HOSPITALITY",12,Color.DKGRAY));
        nameBox=new EditText(this);nameBox.setSingleLine(true);nameBox.setText(getPreferences(0).getString("name",""));nameBox.setHint("Enter this device's staff name");nameBox.setPadding(dp(12),0,dp(12),0);nameBox.setBackground(shape(Color.WHITE,10));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(50));np.topMargin=dp(12);root.addView(nameBox,np);
        joinButton=new Button(this);joinButton.setAllCaps(false);joinButton.setText("JOIN LOCAL WI-FI CHANNEL");LinearLayout.LayoutParams jp=new LinearLayout.LayoutParams(-1,dp(50));jp.topMargin=dp(8);root.addView(joinButton,jp);
        joinButton.setOnClickListener(v->{if(joined)sendCommand("LEAVE");else join();});
        networkLabel=txt("Wi-Fi: checking…",13,Color.DKGRAY);networkLabel.setPadding(0,dp(8),0,dp(2));root.addView(networkLabel);
        statusLabel=txt("Not connected",13,Color.RED);root.addView(statusLabel);
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(12),dp(12),dp(12),dp(12));card.setBackground(shape(Color.WHITE,14));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(12);root.addView(card,cp);
        callLabel=txt("Ready to join",16,Color.rgb(20,55,42));callLabel.setTypeface(null,Typeface.BOLD);card.addView(callLabel);
        talkButton=new Button(this);talkButton.setText("HOLD TO TALK");talkButton.setAllCaps(false);talkButton.setTextSize(18);talkButton.setTextColor(Color.WHITE);talkButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(20,130,80)));talkButton.setEnabled(false);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(76));tp.topMargin=dp(10);card.addView(talkButton,tp);
        talkButton.setOnTouchListener((v,e)->{if(!joined)return false;if(e.getAction()==MotionEvent.ACTION_DOWN){sendCommand("TALK_START");talkButton.setText("TALKING… RELEASE TO STOP");return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){sendCommand("TALK_STOP");talkButton.setText("HOLD TO TALK");return true;}return true;});
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(0,dp(16),0,dp(6));TextView h=txt("STAFF • ONLINE / OFFLINE",16,Color.rgb(4,95,67));h.setTypeface(null,Typeface.BOLD);head.addView(h,new LinearLayout.LayoutParams(0,-2,1));countLabel=txt("0 online",12,Color.DKGRAY);head.addView(countLabel);root.addView(head);
        ScrollView scroll=new ScrollView(this);userRows=new LinearLayout(this);userRows.setOrientation(1);userRows.setPadding(dp(7),dp(5),dp(7),dp(5));userRows.setBackground(shape(Color.WHITE,9));scroll.addView(userRows);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView note=txt("Same Wi-Fi router required. No internet or cloud server. Keep channel active from the persistent notification.",11,Color.GRAY);note.setGravity(Gravity.CENTER);note.setPadding(0,dp(10),0,0);root.addView(note);
    }
    void refreshWifi(){ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);boolean wifi=false;for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){wifi=true;break;}}networkLabel.setText(wifi?"Wi-Fi available • local LAN only":"Connect to restaurant Wi-Fi");networkLabel.setTextColor(wifi?Color.rgb(15,130,80):Color.RED);}
    void join(){
        String n=nameBox.getText().toString().trim();if(n.isEmpty()){nameBox.setError("Enter a unique staff name");return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},11);return;}
        getPreferences(0).edit().putString("name",n).apply();
        Intent i=new Intent(this,WalkieKeepAliveService.class);i.setAction("JOIN");i.putExtra("name",n);
        if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
        joined=true;joinButton.setText("LEAVE CHANNEL");statusLabel.setText("Starting local Wi-Fi channel…");
    }
    void sendCommand(String action){Intent i=new Intent(this,WalkieKeepAliveService.class);i.setAction(action);startService(i);if("LEAVE".equals(action)){joined=false;joinButton.setText("JOIN LOCAL WI-FI CHANNEL");talkButton.setEnabled(false);}}
    void renderRows(ArrayList<String> names,ArrayList<String> ids,ArrayList<String> states){
        userRows.removeAllViews();if(names==null||names.isEmpty()){userRows.addView(txt("No staff detected yet.",13,Color.GRAY));return;}
        for(int x=0;x<names.size();x++){
            final String id=ids.get(x), name=names.get(x);boolean online="1".equals(states.get(x));
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(5),dp(5),dp(5),dp(5));
            TextView person=txt((online?"●  ":"○  ")+name+"  —  "+(online?"ONLINE":"OFFLINE"),14,online?Color.rgb(15,130,80):Color.GRAY);
            row.addView(person,new LinearLayout.LayoutParams(0,dp(42),1));
            Button ring=new Button(this);ring.setText("RING");ring.setTextSize(11);ring.setAllCaps(false);ring.setEnabled(online&&!id.equals("SELF"));ring.setOnClickListener(v->{Intent i=new Intent(this,WalkieKeepAliveService.class);i.setAction("RING");i.putExtra("target",id);i.putExtra("targetName",name);startService(i);});row.addView(ring,new LinearLayout.LayoutParams(dp(76),dp(42)));
            userRows.addView(row);
        }
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==11&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)Toast.makeText(this,"Microphone permission granted.",Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){if(receiverRegistered)unregisterReceiver(updates);super.onDestroy();}
}

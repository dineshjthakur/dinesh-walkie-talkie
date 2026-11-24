package com.dinesh.walkietalkie;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.*;
import android.os.*;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    static final String UPDATE="com.dinesh.walkietalkie.LOCAL_UPDATE";
    LinearLayout userRows;
    EditText nameBox;
    TextView networkLabel,statusLabel,countLabel,callLabel,networkDetails;
    Button joinButton,talkButton,endCallButton;
    boolean joined=false,receiverRegistered=false,dialogShowing=false;
    BroadcastReceiver updates;

    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    GradientDrawable shape(int c,int r){GradientDrawable d=new GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));return d;}
    TextView txt(String s,int z,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(z);t.setTextColor(c);return t;}

    @Override public void onCreate(Bundle b){
        super.onCreate(b);buildUi();
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},11);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},12);
        updates=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            if(!UPDATE.equals(i.getAction()))return;
            joined=i.getBooleanExtra("joined",joined);
            String st=i.getStringExtra("status");if(st!=null)statusLabel.setText(st);
            statusLabel.setTextColor(i.getBooleanExtra("ok",joined)?Color.rgb(15,130,80):Color.RED);
            countLabel.setText(i.getIntExtra("count",0)+" online");
            joinButton.setText("CONNECTED AUTOMATICALLY");
            talkButton.setEnabled(joined);
            renderRows(i.getStringArrayListExtra("users"),i.getStringArrayListExtra("ids"),i.getStringArrayListExtra("online"));
            String call=i.getStringExtra("callStatus");if(call!=null)callLabel.setText(call);
            boolean active=i.getBooleanExtra("callActive",false);endCallButton.setVisibility(active?android.view.View.VISIBLE:android.view.View.GONE);
            String caller=i.getStringExtra("incomingCaller"),peer=i.getStringExtra("incomingId");
            if(caller!=null&&peer!=null&&!dialogShowing)showIncomingCall(caller,peer);
        }};
        registerReceiver(updates,new IntentFilter(UPDATE),Context.RECEIVER_NOT_EXPORTED);receiverRegistered=true;
        refreshWifi();
    }

    void buildUi(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(18),dp(18),dp(18),dp(14));root.setBackgroundColor(Color.rgb(242,247,244));setContentView(root);
        TextView brand=txt("JB HOSPITALITY",15,Color.rgb(4,95,67));brand.setTypeface(null,Typeface.BOLD);root.addView(brand);
        TextView title=txt("DINESH THAKUR\\nWALKIE TALKIE",24,Color.rgb(5,65,48));title.setTypeface(null,Typeface.BOLD);title.setPadding(0,dp(6),0,dp(4));root.addView(title);
        root.addView(txt("LOCAL WI-FI • JB HOSPITALITY",12,Color.DKGRAY));
        nameBox=new EditText(this);nameBox.setSingleLine(true);nameBox.setText(getPreferences(0).getString("name",""));nameBox.setHint("This device's staff name");nameBox.setPadding(dp(12),0,dp(12),0);nameBox.setBackground(shape(Color.WHITE,10));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,dp(48));np.topMargin=dp(10);root.addView(nameBox,np);
        joinButton=new Button(this);joinButton.setAllCaps(false);joinButton.setText("CONNECTED AUTOMATICALLY");LinearLayout.LayoutParams jp=new LinearLayout.LayoutParams(-1,dp(48));jp.topMargin=dp(7);root.addView(joinButton,jp);joinButton.setOnClickListener(v->{if(!joined)join();else Toast.makeText(this,"Auto-connect is active. The channel stays connected while Wi-Fi is available.",Toast.LENGTH_LONG).show();});
        networkLabel=txt("Wi-Fi: checking",12,Color.DKGRAY);networkLabel.setPadding(0,dp(7),0,dp(2));root.addView(networkLabel);
        networkDetails=txt("Network details: checking…",11,Color.DKGRAY);networkDetails.setPadding(0,dp(2),0,dp(2));root.addView(networkDetails);
        Button setup=new Button(this);setup.setText("3-ROUTER NETWORK SETUP");setup.setAllCaps(false);setup.setOnClickListener(v->showRouterGuide());root.addView(setup,new LinearLayout.LayoutParams(-1,dp(42)));
        statusLabel=txt("Not connected",12,Color.RED);root.addView(statusLabel);
        LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackground(shape(Color.WHITE,12));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(9);root.addView(card,cp);
        callLabel=txt("Ready to join",15,Color.rgb(20,55,42));callLabel.setTypeface(null,Typeface.BOLD);card.addView(callLabel);
        talkButton=new Button(this);talkButton.setText("HOLD TO TALK");talkButton.setTextSize(23);talkButton.setTypeface(null,Typeface.BOLD);talkButton.setAllCaps(false);talkButton.setTextColor(Color.WHITE);talkButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(20,130,80)));talkButton.setEnabled(false);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(112));tp.topMargin=dp(8);card.addView(talkButton,tp);
        talkButton.setOnTouchListener((v,e)->{if(!joined)return false;if(e.getAction()==MotionEvent.ACTION_DOWN){command("PTT_START");talkButton.setText("SPEAKING… RELEASE TO STOP");return true;}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){command("PTT_STOP");talkButton.setText("HOLD TO TALK");return true;}return true;});
        endCallButton=new Button(this);endCallButton.setText("END TWO-WAY CALL");endCallButton.setAllCaps(false);endCallButton.setVisibility(android.view.View.GONE);endCallButton.setOnClickListener(v->command("CALL_END"));card.addView(endCallButton);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);head.setPadding(0,dp(12),0,dp(5));TextView h=txt("STAFF • ONLINE / OFFLINE",15,Color.rgb(4,95,67));h.setTypeface(null,Typeface.BOLD);head.addView(h,new LinearLayout.LayoutParams(0,-2,1));countLabel=txt("0 online",12,Color.DKGRAY);head.addView(countLabel);root.addView(head);
        ScrollView scroll=new ScrollView(this);userRows=new LinearLayout(this);userRows.setOrientation(1);userRows.setPadding(dp(6),dp(5),dp(6),dp(5));userRows.setBackground(shape(Color.WHITE,9));scroll.addView(userRows);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView note=txt("CALL = full two-way conversation. RING = buzzer alert. All devices must use the same Wi-Fi.",10,Color.GRAY);note.setGravity(Gravity.CENTER);note.setPadding(0,dp(8),0,0);root.addView(note);
    }

    void refreshWifi(){
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
        Network wifi=null;
        for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)){wifi=n;break;}}
        boolean connected=wifi!=null;
        networkLabel.setText(connected?"Wi-Fi available • Local LAN":"Connect to restaurant Wi-Fi");
        networkLabel.setTextColor(connected?Color.rgb(15,130,80):Color.RED);
        if(connected){
            LinkProperties lp=cm.getLinkProperties(wifi);
            String iface=lp!=null?lp.getInterfaceName():"unknown";
            String addresses=lp!=null?lp.getLinkAddresses().toString():"unknown";
            String gateway="unknown";
            if(lp!=null)for(RouteInfo r:lp.getRoutes())if(r.isDefault()&&r.getGateway()!=null){gateway=r.getGateway().getHostAddress();break;}
            networkDetails.setText("Interface: "+iface+"   Gateway: "+gateway+"\\nIP: "+addresses);
        }else networkDetails.setText("Network details unavailable");
    }
    void showRouterGuide(){
        String guide="JB HOSPITALITY — THREE ROUTER SETUP\\n\\n"+
            "MAIN ROUTER\\n• Keep Router mode and DHCP ON.\\n• Connect it to the main switch using a LAN port.\\n\\n"+
            "ROUTERS 2 AND 3\\n• Select Access Point (AP) mode.\\n• Disable DHCP on both (AP mode usually does this automatically).\\n• Connect each router's LAN port to the switch. Do not use WAN ports in this LAN-to-LAN AP setup.\\n"+
            "• Give each router a unique management IP in the main router's subnet.\\n\\n"+
            "ALL THREE\\n• Same IP subnet and same Wi-Fi name/password (recommended).\\n• Disable AP/client isolation and guest isolation.\\n• Do not create separate NAT networks.\\n\\n"+
            "After setup, connect both phones to Wi-Fi and compare their IP addresses. They should be in the same subnet. The app uses local-LAN discovery; this setup allows discovery to cross the access points without cloud service or internet.";
        new AlertDialog.Builder(this).setTitle("Router and switch configuration").setMessage(guide).setPositiveButton("OK",null).show();
    }
    void join(){String n=nameBox.getText().toString().trim();if(n.isEmpty()){nameBox.setError("Enter a staff name");return;}if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},11);return;}getPreferences(0).edit().putString("name",n).apply();startAutoService(n);}
    void startAutoService(String n){Intent i=new Intent(this,WalkieKeepAliveService.class);i.setAction("AUTO_START");i.putExtra("name",n);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);joined=true;joinButton.setText("CONNECTED AUTOMATICALLY");statusLabel.setText("Auto-connecting to local Wi-Fi…");}
    void command(String a){Intent i=new Intent(this,WalkieKeepAliveService.class);i.setAction(a);startService(i);}
    void renderRows(ArrayList<String> ns,ArrayList<String> ids,ArrayList<String> states){
        userRows.removeAllViews();if(ns==null||ns.isEmpty()){userRows.addView(txt("No staff detected yet.",13,Color.GRAY));return;}
        for(int x=0;x<ns.size();x++){final String id=ids.get(x),name=ns.get(x);boolean on="1".equals(states.get(x));if("SELF".equals(id))continue;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(4),dp(4),dp(4),dp(4));
            TextView who=txt((on?"● ":"○ ")+name+"  "+(on?"ONLINE":"OFFLINE"),13,on?Color.rgb(15,130,80):Color.GRAY);row.addView(who,new LinearLayout.LayoutParams(0,dp(42),1));
            Button ring=new Button(this);ring.setText("RING");ring.setAllCaps(false);ring.setTextSize(10);ring.setEnabled(on);ring.setText("HOLD TO RING");
            ring.setOnTouchListener((v,e)->{
                if(!on)return false;
                if(e.getAction()==MotionEvent.ACTION_DOWN){
                    Intent i=new Intent(this,WalkieKeepAliveService.class);
                    i.setAction("RING");i.putExtra("target",id);
                    if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
                    ring.setText("RINGING…");
                    return true;
                }
                if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){
                    Intent i=new Intent(this,WalkieKeepAliveService.class);
                    i.setAction("RING_STOP");i.putExtra("target",id);
                    if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);
                    ring.setText("HOLD TO RING");
                    return true;
                }
                return true;
            });row.addView(ring,new LinearLayout.LayoutParams(dp(72),dp(46)));
            Button call=new Button(this);call.setText("CALL");call.setAllCaps(false);call.setTextSize(10);call.setEnabled(on);call.setOnClickListener(v->{Intent i=new Intent(this,WalkieKeepAliveService.class);i.setAction("CALL");i.putExtra("target",id);i.putExtra("targetName",name);startService(i);});row.addView(call,new LinearLayout.LayoutParams(dp(72),dp(46)));
            userRows.addView(row);
        }
    }
    void showIncomingCall(String caller,String peer){dialogShowing=true;new AlertDialog.Builder(this).setTitle("Incoming call").setMessage(caller+" is calling.").setCancelable(false).setPositiveButton("ANSWER",(d,w)->{Intent i=new Intent(this,WalkieKeepAliveService.class);i.setAction("CALL_ACCEPT");i.putExtra("peer",peer);startService(i);dialogShowing=false;}).setNegativeButton("DECLINE",(d,w)->{Intent i=new Intent(this,WalkieKeepAliveService.class);i.setAction("CALL_REJECT");i.putExtra("peer",peer);startService(i);dialogShowing=false;}).setOnDismissListener(d->dialogShowing=false).show();}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g);if(r==11&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)Toast.makeText(this,"Microphone permission granted.",Toast.LENGTH_SHORT).show();}
    @Override protected void onDestroy(){if(receiverRegistered)unregisterReceiver(updates);super.onDestroy();}
}

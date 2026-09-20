package dev.hazel.livealarm;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.media.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final String HOST="appassets.androidplatform.net", BASE="https://"+HOST+"/assets/";
    private static final int AUDIO=50,EXPORT=51,IMPORT=52,NOTIFICATION_REPORT=53,COMPONENT_REPORT=54,SCHEDULE_IMAGE=55,PICK_BACKGROUND=56,NOTIFICATIONS=80;
    protected WebView web;protected Prefs prefs;private FrameLayout root;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private String pendingExport="",pendingScheduleId="";private String pendingPickRequest=null;private boolean refreshing=false;private long refreshAt=0;
    private final Handler permissionHandler=new Handler(Looper.getMainLooper());
    private boolean resumed=false,notificationRequestInFlight=false;
    private final Runnable permissionRefresh=()->{if(resumed&&web!=null){GuardianService.refreshNotifications();push();}};
    private static java.lang.ref.WeakReference<MainActivity> visible=new java.lang.ref.WeakReference<>(null);
    private boolean alarmOpening=false;
    private long notificationRequestBegan=0;
    // Only an already-visible activity can reveal the alarm. No background activity launch.
    static boolean revealAlarm(){
        MainActivity a=visible.get();
        if(a==null||!a.resumed||a.isFinishing()||!a.hasWindowFocus())return false;
        GuardianService.hideAlarmOverlay();a.push();
        if(GuardianService.ringing&&!a.alarmPage()&&!a.alarmOpening){
            a.alarmOpening=true;
            try{a.startActivity(new Intent(a,AlarmActivity.class));}catch(RuntimeException e){a.alarmOpening=false;}
        }
        return true;
    }
    protected boolean alarmPage(){return false;}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);prefs=new Prefs(this);GuardianService.channels(this);
        if(alarmPage()){if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}
        root=new FrameLayout(this);root.setBackgroundColor(Color.rgb(245,245,240));
        web=new WebView(this);root.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30){android.graphics.Insets p=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());view.setPadding(p.left,p.top,p.right,p.bottom);}
            else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);settings.setMediaPlaybackRequiresUserGesture(true);web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r){return !r.getUrl().toString().equals(BASE+(alarmPage()?"alarm.html":"index.html"));}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest r){
                Uri u=r.getUrl();String path=u.getPath();
                if("https".equals(u.getScheme())&&HOST.equals(u.getHost())&&"/cover/".equals(path)){
                    // The interface's own picture, streamed through the app so the WebView never
                    // talks to the outside; the host whitelist is the whole security model.
                    String target=u.getQueryParameter("u");
                    android.net.Uri t=target==null?null:Uri.parse(target);
                    if(t==null||!"https".equals(t.getScheme())||t.getHost()==null||!t.getHost().endsWith(".hdslb.com"))return emptyResponse();
                    try{
                        java.net.HttpURLConnection conn=(java.net.HttpURLConnection)new java.net.URL(target).openConnection();
                        conn.setConnectTimeout(8000);conn.setReadTimeout(8000);conn.setInstanceFollowRedirects(true);
                        conn.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36");
                        conn.setRequestProperty("Referer","https://live.bilibili.com/");
                        if(conn.getResponseCode()!=200){conn.disconnect();return emptyResponse();}
                        if(!conn.getURL().getHost().endsWith(".hdslb.com"))return emptyResponse();
                        Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","max-age=600");
                        return new WebResourceResponse("image/jpeg","UTF-8",200,"OK",headers,conn.getInputStream());
                    }catch(IOException e){return emptyResponse();}
                }
                if("https".equals(u.getScheme())&&HOST.equals(u.getHost())&&path!=null&&path.startsWith("/schedule/")&&!path.contains("..")){
                    String name=path.substring(10);int dot=name.lastIndexOf(".img");
                    java.io.File art=AnchorArt.scheduleImage(MainActivity.this,dot>0?name.substring(0,dot):name);
                    if(art==null)return emptyResponse();
                    try{Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store");headers.put("X-Content-Type-Options","nosniff");return new WebResourceResponse(AnchorArt.mime(art),"UTF-8",200,"OK",headers,new FileInputStream(art));}
                    catch(IOException e){return emptyResponse();}
                }
                if("https".equals(u.getScheme())&&HOST.equals(u.getHost())&&path!=null&&path.startsWith("/avatar/")&&!path.contains("..")){
                    String name=path.substring(8);int dot=name.lastIndexOf('.');
                    java.io.File art=AnchorArt.existing(MainActivity.this,dot>0?name.substring(0,dot):name);
                    if(art==null)return emptyResponse();
                    try{Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store");headers.put("X-Content-Type-Options","nosniff");return new WebResourceResponse(AnchorArt.mime(art),"UTF-8",200,"OK",headers,new FileInputStream(art));}
                    catch(IOException e){return emptyResponse();}
                }
                if("https".equals(u.getScheme())&&HOST.equals(u.getHost())&&path!=null&&path.startsWith("/background/")&&!path.contains("..")){
                    // "/background/current" is the stable address the UI asks for: the stored
                    // file name is a private UUID, so the page never has to know it. Any other
                    // segment must still be a real owned file name.
                    String segment=path.substring(12);
                    String stored="current".equals(segment)?prefs.raw().getString("backgroundPath",""):segment;
                    java.io.File art=BackgroundStore.resolve(MainActivity.this,stored);
                    if(art==null)return emptyResponse();
                    try{Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store");headers.put("X-Content-Type-Options","nosniff");return new WebResourceResponse("image/jpeg","UTF-8",200,"OK",headers,new FileInputStream(art));}
                    catch(IOException e){return emptyResponse();}
                }
                if(!"https".equals(u.getScheme())||!HOST.equals(u.getHost())||path==null||!path.startsWith("/assets/")||path.contains(".."))return emptyResponse();
                String file=path.substring(8);String mime=file.endsWith(".html")?"text/html":file.endsWith(".js")?"application/javascript":file.endsWith(".css")?"text/css":file.endsWith(".png")?"image/png":"text/plain";
                try{Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store");headers.put("X-Content-Type-Options","nosniff");return new WebResourceResponse(mime,"UTF-8",200,"OK",headers,getAssets().open(file));}catch(IOException e){return emptyResponse();}
            }
            @Override public void onPageFinished(WebView view,String url){applyTheme();refreshPermissionsAfterReturn();}
        });
        web.addJavascriptInterface(new Bridge(),"HazelNative");applyTheme();applySecureFlag();web.loadUrl(BASE+(alarmPage()?"alarm.html":"index.html"));
    }
    /** 隐藏最近任务缩略图：FLAG_SECURE 只遮本应用卡片，不影响提醒。 */
    private void applySecureFlag(){
        if(alarmPage())return;
        if(prefs.config().optBoolean("hideRecents"))getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    private WebResourceResponse emptyResponse(){return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",null,new ByteArrayInputStream(new byte[0]));}
    private void applyTheme(){
        if(web==null)return;String theme=prefs.config().optString("theme");boolean dark=theme.equals("dark")||(theme.equals("system")&&(getResources().getConfiguration().uiMode&0x30)==0x20);
        int color=dark?Color.rgb(24,29,36):Color.rgb(245,245,240);root.setBackgroundColor(color);web.setBackgroundColor(color);getWindow().setStatusBarColor(color);getWindow().setNavigationBarColor(color);
        if(Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null)c.setSystemBarsAppearance(dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}
        else{int flags=web.getSystemUiVisibility();web.setSystemUiVisibility(dark?flags&~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR):flags|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
    }
    @Override protected void onResume(){super.onResume();resumed=true;visible=new java.lang.ref.WeakReference<>(this);alarmOpening=false;if(web!=null)web.onResume();if(prefs!=null){NotificationAccess.record(this,prefs,"activity_resume",true);
        // Returning to the page is a good moment to rescue a watch the system killed — but not
        // outside the schedule, where a stopped service is the intended state. Starting it there
        // would only produce a notification that appears and disappears.
        if(!alarmPage()&&prefs.enabled()&&prefs.watchingNow()&&!GuardianService.running)GuardianService.send(this,"CHECK");}refreshPermissionsAfterReturn();}
    @Override protected void onPause(){resumed=false;if(visible.get()==this)visible.clear();alarmOpening=false;permissionHandler.removeCallbacks(permissionRefresh);if(web!=null)web.onPause();super.onPause();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused&&resumed){refreshPermissionsAfterReturn();if(GuardianService.ringing)revealAlarm();}}
    @Override protected void onDestroy(){resumed=false;permissionHandler.removeCallbacksAndMessages(null);if(web!=null){web.removeJavascriptInterface("HazelNative");web.destroy();web=null;}io.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(alarmPage()){moveTaskToBack(true);return;}if(web!=null)web.evaluateJavascript("window.appBack && window.appBack()",null);else super.onBackPressed();}
    private void push(){if(web!=null)web.evaluateJavascript("window.refreshNative && window.refreshNative(true)",null);}
    private void refreshPermissionsAfterReturn(){
        permissionHandler.removeCallbacks(permissionRefresh);
        if(!resumed||web==null)return;
        GuardianService.refreshNotifications();
        push();
        // OEM settings can publish their final permission state after Activity resume.
        permissionHandler.postDelayed(permissionRefresh,350);
        permissionHandler.postDelayed(permissionRefresh,1200);
        permissionHandler.postDelayed(permissionRefresh,2500);
    }
    private void reply(String id,boolean ok,Object value){if(web==null)return;JSONObject r=new JSONObject();Prefs.put(r,"ok",ok);Prefs.put(r,ok?"value":"error",value);web.evaluateJavascript("window.NativeReply && window.NativeReply("+JSONObject.quote(id)+","+r.toString()+")",null);}
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    /** A missing message must still name the failure; a blank error is undiagnosable. */
    private String errorText(Exception e){String message=e.getMessage();return message==null||message.isEmpty()?"操作未完成（"+e.getClass().getSimpleName()+"），请重试":message;}
    public final class Bridge {
        @JavascriptInterface public void request(String id,String action,String json){
            if(id==null||id.length()>80||action==null||action.length()>40||json==null||json.length()>131072)return;
            final String requestId=id;
            runOnUiThread(()->{try{Object result=dispatch(action,new JSONObject(json),requestId);if(result==DEFERRED)return;reply(requestId,true,result==null?JSONObject.NULL:result);}catch(Exception e){reply(requestId,false,errorText(e));}});
        }
    }
    /** Returned by an action that answers from a background thread instead of here. */
    private static final Object DEFERRED=new Object();
    private JSONObject state(){
        JSONObject j=new JSONObject();Prefs.put(j,"config",prefs.config());Prefs.put(j,"enabled",prefs.enabled());Prefs.put(j,"running",GuardianService.running);
        Prefs.put(j,"ringing",GuardianService.ringing);Prefs.put(j,"alarmTest",prefs.raw().getBoolean("alarmTest",false));Prefs.put(j,"alarmTitle",prefs.raw().getString("alarmTitle",""));Prefs.put(j,"alarmAnchor",prefs.raw().getString("alarmAnchorName",""));Prefs.put(j,"alarmAnchorId",prefs.raw().getString("alarmAnchorId",""));Prefs.put(j,"alarmCover",prefs.snapshot(prefs.raw().getString("alarmAnchorId","")).optString("cover",""));Prefs.put(j,"alarmUntil",prefs.raw().getLong("alarmUntil",0));Prefs.put(j,"alarmSilent",prefs.raw().getBoolean("alarmSilent",false));
        Prefs.put(j,"snapshot",primarySnapshot());Prefs.put(j,"anchors",anchorStates());Prefs.put(j,"networkError",prefs.raw().getString("networkError",""));Prefs.put(j,"serviceError",prefs.raw().getString("serviceError",""));
        Prefs.put(j,"startError",prefs.raw().getString("startError",""));
        Prefs.put(j,"nextCheck",prefs.raw().getLong("nextCheck",0));Prefs.put(j,"serviceHeartbeatAt",prefs.raw().getLong("serviceHeartbeatAt",0));Prefs.put(j,"snoozeAt",prefs.raw().getLong("snoozeAt",0));Prefs.put(j,"testAt",prefs.raw().getLong("testAt",0));Prefs.put(j,"inside",prefs.allowed(System.currentTimeMillis()));
        // Outside the schedule the watch is parked on purpose, so a stopped service must not be
        // read as a failure. These two are what tell the page the difference, and nextBoundary
        // (below) is when the watch comes back. hasWindow separates "between two windows" from
        // "custom mode with no rule switched on", which is paused forever rather than for a while.
        Prefs.put(j,"schedulePaused",prefs.enabled()&&!prefs.watchingNow());
        Prefs.put(j,"hasWindow",TimeRules.hasWindow(prefs.windows(prefs.config())));
        // High-frequency windows: whether the fast pace is in force right now, and the interval
        // itself. Both come from the same rule table the poll loop reads, so the page cannot show a
        // pace the loop is not keeping.
        Prefs.put(j,"highInside",prefs.highFrequencyNow());
        Prefs.put(j,"pollSecondsNow",prefs.pollSecondsNow());
        JSONObject c=prefs.config();Prefs.put(j,"zone",TimeRules.zone(c.optString("timezone")).getId());Prefs.put(j,"deviceZone",ZoneId.systemDefault().getId());
        Prefs.put(j,"now",System.currentTimeMillis());Prefs.put(j,"nextBoundary",TimeRules.nextBoundary(System.currentTimeMillis(),c.optBoolean("allDay"),prefs.windows(c),TimeRules.zone(c.optString("timezone"))));
        Prefs.put(j,"highNextBoundary",TimeRules.nextBoundary(System.currentTimeMillis(),false,prefs.windows(c,"highWindows"),TimeRules.zone(c.optString("timezone"))));
        Prefs.put(j,"permissions",permissions());Prefs.put(j,"permissionsCheckedAt",System.currentTimeMillis());
        Prefs.put(j,"notificationRequestResult",prefs.raw().getString("notificationRequestResult","not_requested"));
        Prefs.put(j,"notificationRequestAt",prefs.raw().getLong("notificationRequestAt",0));
        Prefs.put(j,"alertMode",NotificationAccess.alertMode(this,prefs).name());Prefs.put(j,"overlayVisible",GuardianService.overlayVisible);
        Prefs.put(j,"watchNotification",NotificationAccess.watchState(this,prefs));
        Prefs.put(j,"backgroundName",prefs.raw().getString("backgroundName",""));Prefs.put(j,"backgroundSet",!prefs.raw().getString("backgroundPath","").isEmpty());
        // Cache-busting revision for the stable /background/current address. The stored name is
        // deliberately not exposed, so the page only sees a short digest of it.
        String storedName=prefs.raw().getString("backgroundPath","");
        Prefs.put(j,"backgroundRevision",storedName.isEmpty()?"0":Integer.toHexString(storedName.hashCode()));
        Prefs.put(j,"recoveryAt",prefs.raw().getLong("recoveryAt",0));
        Prefs.put(j,"version",versionName());Prefs.put(j,"android",Build.VERSION.RELEASE);Prefs.put(j,"manufacturer",Build.MANUFACTURER);Prefs.put(j,"xiaomi",NotificationAccess.isXiaomi());Prefs.put(j,"preview",false);return j;
    }
    /**
     * The footer shows the version that is actually installed. Reading it from the package
     * manager is what keeps it from staying at an old release after the next build.
     */
    private String versionName(){
        try{String name=getPackageManager().getPackageInfo(getPackageName(),0).versionName;if(name!=null&&!name.isEmpty())return name;}
        catch(Exception ignored){}
        return "1.1.9";
    }
    private JSONObject permissions(){
        JSONObject p=new JSONObject();NotificationManager n=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        boolean runtimeGranted=NotificationAccess.runtimeGranted(this);
        boolean appEnabled=n.areNotificationsEnabled();
        Prefs.put(p,"notificationRuntime",runtimeGranted);Prefs.put(p,"notificationAppEnabled",appEnabled);
        Prefs.put(p,"notificationMismatch",runtimeGranted!=appEnabled);Prefs.put(p,"notifications",runtimeGranted&&appEnabled);
        String policy=NotificationAccess.policyStatus(this);Prefs.put(p,"notificationPolicy",policy);
        NotificationAccess.record(this,prefs,"state_changed",false);
        NotificationChannel channel=n.getNotificationChannel(GuardianService.ALARM_CHANNEL);Prefs.put(p,"alarmChannel",channel!=null&&channel.getImportance()>=NotificationManager.IMPORTANCE_HIGH);
        Prefs.put(p,"alarmChannelImportance",channel==null?-1:channel.getImportance());
        NotificationChannel watchChannel=n.getNotificationChannel(GuardianService.WATCH_CHANNEL);
        Prefs.put(p,"watchChannel",watchChannel!=null&&watchChannel.getImportance()>NotificationManager.IMPORTANCE_NONE);
        String detail="系统通知授权："+(runtimeGranted?"已允许":"未允许")+"；应用通知总开关："+(appEnabled?"已开启":"未开启")+"；策略检查："+policy+"；强提醒通道等级："+(channel==null?-1:channel.getImportance());
        if(!detail.equals(prefs.raw().getString("notificationState",""))){
            prefs.raw().edit().putString("notificationState",detail).apply();
            prefs.log(runtimeGranted&&appEnabled?"system":"warning","通知权限检查",detail);
        }
        Prefs.put(p,"battery",power.isIgnoringBatteryOptimizations(getPackageName()));Prefs.put(p,"fullScreen",Build.VERSION.SDK_INT<34||n.canUseFullScreenIntent());Prefs.put(p,"exact",AlarmScheduler.exact(this));
        Prefs.put(p,"dnd",n.getCurrentInterruptionFilter()!=NotificationManager.INTERRUPTION_FILTER_ALL);Prefs.put(p,"powerSave",power.isPowerSaveMode());
        Prefs.put(p,"overlay",Settings.canDrawOverlays(this));Prefs.put(p,"accessibility",WatchAccessibilityService.enabled(this));Prefs.put(p,"accessibilityConnected",WatchAccessibilityService.connected);
        AudioManager a=(AudioManager)getSystemService(AUDIO_SERVICE);Prefs.put(p,"alarmVolume",a.getStreamVolume(AudioManager.STREAM_ALARM));Prefs.put(p,"alarmMax",a.getStreamMaxVolume(AudioManager.STREAM_ALARM));return p;
    }
    private void requireAlarmAccess()throws Exception{
        if(NotificationAccess.alertMode(this,prefs)==AlertPolicy.Mode.BLOCKED)
            throw new Exception("请允许通知，或在设置中开启“通知异常时仍响铃”后继续");
    }
    private Object dispatch(String action,JSONObject data,String requestId)throws Exception{
        switch(action){
            case "state": return state();
            case "notificationDiagnostics": return NotificationAccess.diagnostics(this,prefs);
            case "notificationProbe":{
                if(!permissions().optBoolean("notifications"))throw new Exception("系统尚未允许通知，请先完成通知授权");
                NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                NotificationChannel probeChannel=manager.getNotificationChannel(GuardianService.WATCH_CHANNEL);
                if(probeChannel==null||probeChannel.getImportance()==NotificationManager.IMPORTANCE_NONE)throw new Exception("请在通知设置中开启“后台守候”通道，再发送测试通知");
                PendingIntent open=PendingIntent.getActivity(this,701,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
                Notification probe=new Notification.Builder(this,GuardianService.WATCH_CHANNEL).setSmallIcon(R.drawable.ic_bell)
                    .setContentTitle("VR闹钟 · 通知验证").setContentText("看到这条通知后，可返回应用测试正式响铃。")
                    .setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_STATUS).build();
                ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(701,probe);
                prefs.log("system","已提交验证通知","请下拉通知栏确认是否看见；提交成功不代表系统一定展示");return true;
            }
            case "zones":{
                JSONArray zones=new JSONArray();Instant now=Instant.now();
                for(String id:new TreeSet<String>(ZoneId.getAvailableZoneIds())){ZoneId zone=ZoneId.of(id);JSONObject z=new JSONObject();Prefs.put(z,"id",id);Prefs.put(z,"offset",zone.getRules().getOffset(now).toString().replace("Z","+00:00"));Prefs.put(z,"time",now.atZone(zone).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));zones.put(z);}return zones;
            }
            case "history": return prefs.history();
            case "save":
                prefs.update(data);applyTheme();applySecureFlag();AlarmScheduler.boundaries(this);
                if(GuardianService.ringing&&NotificationAccess.alertMode(this,prefs)==AlertPolicy.Mode.BLOCKED)
                    GuardianService.send(this,"DISMISS");
                if(prefs.enabled())GuardianService.send(this,"CHECK");return prefs.config();
            case "toggle":{
                boolean enable=data.getBoolean("enabled");if(enable)requireAlarmAccess();prefs.setEnabled(enable);
                if(!GuardianService.send(this,enable?"CHECK":"STOP_WATCH")){if(enable)prefs.setEnabled(false);throw new Exception("系统限制了后台启动，请检查权限后重试");}
                int targets=0;for(Anchors.Anchor a:prefs.anchors())if(a.enabled)targets++;
                prefs.log("system",enable?"已开启守候":"已停止守候",enable?"开始按你的时间规则守候 "+targets+" 位主播":"不再自动检测或响铃");return state();
            }
            case "refresh":refresh();return "正在检测直播状态";
            case "pickBackground":{
                // The answer must wait for the import: replying here would tell the page the
                // background changed before the picker even opened, so a failed import looked
                // like a success and the old picture stayed on screen.
                pendingPickRequest=requestId;
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),PICK_BACKGROUND);return DEFERRED;
            }
            case "removeBackground":{
                io.execute(()->{BackgroundStore.remove(this,prefs);runOnUiThread(()->{applyTheme();push();});});
                return true;
            }
            case "test":requireAlarmAccess();if(!GuardianService.send(this,"TEST"))throw new Exception("无法启动测试");startActivity(new Intent(this,AlarmActivity.class));return true;
            case "testLater":{
                requireAlarmAccess();if(!AlarmScheduler.exact(this))throw new Exception("锁屏定时测试需要先允许“精确闹钟”");
                long at=System.currentTimeMillis()+15000;prefs.raw().edit().putLong("testAt",at).commit();AlarmScheduler.at(this,AlarmScheduler.TEST,at);return at;
            }
            case "cancelTest":AlarmScheduler.cancel(this,AlarmScheduler.TEST);prefs.raw().edit().putLong("testAt",0).apply();if(GuardianService.running)GuardianService.send(this,"CANCEL_TEST");return true;
            case "dismiss":if(GuardianService.running)GuardianService.send(this,"DISMISS");if(alarmPage())finish();return true;
            case "snooze":if(GuardianService.running)GuardianService.send(this,"SNOOZE");if(alarmPage())finish();return true;
            case "cancelSnooze":AlarmScheduler.cancel(this,AlarmScheduler.SNOOZE);prefs.clearSnoozeKeys();return true;
            case "openAlarm":if(GuardianService.ringing)startActivity(new Intent(this,AlarmActivity.class));return true;
            case "openLive":{
                Anchors.Anchor live=anchorArg(data);
                if(alarmPage()&&GuardianService.running)GuardianService.send(this,"DISMISS");
                openLive(live.room);if(alarmPage())finish();return true;
            }
            case "openProfile":{
                Anchors.Anchor profile=anchorArg(data);
                startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://space.bilibili.com/"+profile.uid)));return true;
            }
            case "resolveAnchor":{
                long room=data.getLong("room");
                final long uid=data.optLong("uid",0);
                // Either a room (number/live link) or a uid (space link) is enough; a uid is
                // mapped to its live room first, so both entry forms end in the same place.
                if(room<=0&&uid<=0)throw new Exception("请填写主播 UID、主页链接或直播间链接");
                String id=data.optString("id","");
                final String anchorId=Anchors.validId(id)?id:UUID.randomUUID().toString();
                // Two lookups plus the avatar have to run off the UI thread, so this action
                // answers later through runOnUiThread instead of returning a value here.
                final long requested=room;final String back=requestId;
                io.execute(()->{
                    Object value;boolean ok=true;
                    try{
                        BiliApi.Profile found=BiliApi.resolve(requested>0?requested:BiliApi.roomOfUid(uid));
                        JSONObject out=new JSONObject();Prefs.put(out,"id",anchorId);Prefs.put(out,"room",found.room);Prefs.put(out,"uid",found.uid);Prefs.put(out,"uidText",Long.toString(found.uid));Prefs.put(out,"name",found.name);
                        Prefs.put(out,"avatar",downloadAvatar(anchorId,found.face));value=out;
                    }catch(Exception e){ok=false;value=errorText(e);}
                    final boolean succeeded=ok;final Object payload=value;
                    runOnUiThread(()->reply(back,succeeded,payload));
                });
                return DEFERRED;
            }
            case "saveAnchor":{
                JSONObject o=data.getJSONObject("anchor");
                String id=o.optString("id","");
                // A payload without the alarm flag means "leave the bell as it was": the
                // detection toggle from older callers must not silently re-ring a muted anchor.
                Anchors.Anchor previous=Anchors.find(prefs.anchors(),Anchors.validId(id)?id:"");
                boolean alarm=o.has("alarm")?o.optBoolean("alarm",true):previous==null||previous.alarm;
                Anchors.Anchor updated=new Anchors.Anchor(Anchors.validId(id)?id:UUID.randomUUID().toString(),
                    Anchors.fitName(o.optString("name","")),Prefs.uidOf(o),o.optLong("room",0),
                    o.optBoolean("enabled",true),alarm);
                List<Anchors.Anchor> list=prefs.anchors();
                int at=Anchors.findIndex(list,updated.id);
                if(at<0)list.add(updated);else list.set(at,updated);
                applyAnchors(list);
                if(prefs.enabled())GuardianService.send(this,"CHECK");
                return anchorStates();
            }
            case "deleteAnchor":{
                String removed=data.getString("id");
                List<Anchors.Anchor> list=prefs.anchors();
                if(list.size()<=1)throw new Exception("请至少保留一个主播");
                List<Anchors.Anchor> next=new ArrayList<>();
                for(Anchors.Anchor x:list)if(!x.id.equals(removed))next.add(x);
                if(next.size()==list.size())throw new Exception("没有找到这个主播");
                applyAnchors(next);
                if(prefs.enabled())GuardianService.send(this,"CHECK");
                return anchorStates();
            }
            case "getSchedule":{
                Anchors.Anchor a=anchorArg(data);
                JSONObject out=new JSONObject();
                Prefs.put(out,"entries",scheduleEntries(prefs.schedule(a.id)));
                Prefs.put(out,"text",prefs.scheduleText(a.id));
                Prefs.put(out,"hasImage",AnchorArt.scheduleImage(this,a.id)!=null);
                Prefs.put(out,"imageRevision",AnchorArt.scheduleImageRevision(this,a.id));
                return out;
            }
            case "saveSchedule":{
                Anchors.Anchor a=anchorArg(data);
                JSONArray arr=data.getJSONArray("entries");
                ArrayList<Schedule.Entry> list=new ArrayList<>();
                for(int i=0;i<arr.length();i++){
                    JSONObject o=arr.getJSONObject(i);
                    list.add(new Schedule.Entry(o.optString("id"),o.optInt("days"),o.optInt("start"),o.optInt("end",-1),o.optString("note","")));
                }
                prefs.saveSchedule(a.id,list);
                prefs.log("system","周表已更新",a.name+"：共 "+list.size()+" 条安排");
                return true;
            }
            case "saveScheduleText":{Anchors.Anchor a=anchorArg(data);prefs.saveScheduleText(a.id,data.optString("text",""));return true;}
            case "removeScheduleImage":{Anchors.Anchor a=anchorArg(data);AnchorArt.clearScheduleImage(this,a.id);return true;}
            case "pickScheduleImage":{
                // The reply is deferred until onActivityResult has really stored (or
                // rejected) the picture, so the editor can repaint the moment it lands.
                Anchors.Anchor a=anchorArg(data);pendingScheduleId=a.id;pendingPickRequest=requestId;
                startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),SCHEDULE_IMAGE);return DEFERRED;
            }
            case "refreshAvatars":{
                // One lookup plus one image per anchor, sequential with a gap: B 站 rate limits.
                List<Anchors.Anchor> targets=prefs.anchors();final String back=requestId;
                io.execute(()->{
                    int fixed=0;
                    for(Anchors.Anchor a:targets){
                        try{
                            String face=BiliApi.anchorFace(a.room);
                            if(!face.isEmpty()&&AnchorArt.save(this,a.id,BiliApi.download(face,2*1024*1024)))fixed++;
                        }catch(Exception ignored){}
                        try{Thread.sleep(800);}catch(InterruptedException e){break;}
                    }
                    final int n=fixed;runOnUiThread(()->reply(back,Boolean.TRUE,Integer.valueOf(n)));
                });
                return DEFERRED;
            }
            case "ocrScheduleImage":{
                Anchors.Anchor a=anchorArg(data);
                if(!prefs.config().optBoolean("aiOcr",true))throw new Exception("AI 图片识别未开启，请在设置里打开");
                JSONObject c=prefs.config();
                final String key=c.optString("aiKey",""),model=c.optString("aiModel","deepseek-flash");
                final java.io.File art=AnchorArt.scheduleImage(this,a.id);
                if(art==null)throw new Exception("该主播还没有周表图片，请先上传");
                final String back=requestId;
                io.execute(()->{
                    Object value;boolean ok=true;
                    try{
                        byte[] raw=readFileBounded(art,8*1024*1024);
                        String mime=AnchorArt.mime(art);
                        if(raw.length>1536*1024){byte[] small=shrinkForUpload(art);if(small!=null){raw=small;mime="image/jpeg";}}
                        value=AiOcr.readSchedule(raw,mime,key,model);
                    }catch(Exception e){ok=false;value=errorText(e);}
                    final boolean succeeded=ok;final Object payload=value;
                    runOnUiThread(()->reply(back,succeeded,payload));
                });
                return DEFERRED;
            }
            case "permission":permission(data.getString("kind"));return true;
            case "pickAudio":startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*"),AUDIO);return true;
            case "clearHistory":prefs.clearHistory();return true;
            case "export":{
                JSONObject payload=new JSONObject();Prefs.put(payload,"schema",1);Prefs.put(payload,"app","HazelAlarm");Prefs.put(payload,"exportedAt",System.currentTimeMillis());
                JSONObject exportSettings=prefs.config();exportSettings.remove("aiKey");exportSettings.remove("backgroundPath");exportSettings.remove("backgroundName");
                Prefs.put(payload,"settings",exportSettings);Prefs.put(payload,"anchors",anchorList());Prefs.put(payload,"history",prefs.history());Prefs.put(payload,"device",state());Prefs.put(payload,"notificationDiagnostics",NotificationAccess.diagnostics(this,prefs));pendingExport=payload.toString(2);
                startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"ManquAlarm-backup.json"),EXPORT);return true;
            }
            case "exportNotificationReport":{
                boolean components=data.optBoolean("includeComponents",false);
                startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip").putExtra(Intent.EXTRA_TITLE,"ManquAlarm-notification-report.zip"),components?COMPONENT_REPORT:NOTIFICATION_REPORT);return true;
            }
            case "import":startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"),IMPORT);return true;
            case "minimize":moveTaskToBack(true);return true;
            case "closeAlarm":if(alarmPage())finish();return true;
            default:throw new Exception("暂不支持此操作");
        }
    }
    private void openLive(long room){
        Uri uri=Uri.parse("https://live.bilibili.com/"+room);
        try{startActivity(new Intent(Intent.ACTION_VIEW,uri).setPackage("tv.danmaku.bili"));}
        catch(ActivityNotFoundException e){try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(ActivityNotFoundException missing){toast("请安装 B 站或浏览器后打开直播间");}}
    }
    /** The anchor a bridge call refers to; falls back to the first one when the id is unknown. */
    private Anchors.Anchor anchorArg(JSONObject data){
        List<Anchors.Anchor> list=prefs.anchors();
        String id=data.optString("id","");
        Anchors.Anchor found=Anchors.find(list,id.isEmpty()?Anchors.firstId(list):id);
        return found==null?list.get(0):found;
    }
    /** The list plus each anchor's own last observation, which is what the interface renders. */
    private JSONArray anchorStates(){
        JSONArray all=new JSONArray();
        for(Anchors.Anchor x:prefs.anchors()){
            JSONObject o=new JSONObject();
            Prefs.put(o,"id",x.id);Prefs.put(o,"name",x.name);Prefs.put(o,"uid",x.uid);Prefs.put(o,"uidText",x.uidText);Prefs.put(o,"room",x.room);Prefs.put(o,"enabled",x.enabled);
            Prefs.put(o,"alarm",x.alarm);
            Prefs.put(o,"snapshot",prefs.snapshot(x.id));Prefs.put(o,"avatar",AnchorArt.existing(this,x.id)!=null);
            Prefs.put(o,"schedule",scheduleEntries(prefs.schedule(x.id)));Prefs.put(o,"scheduleImage",AnchorArt.scheduleImage(this,x.id)!=null);Prefs.put(o,"scheduleImageRevision",AnchorArt.scheduleImageRevision(this,x.id));
            Prefs.put(o,"stats",statSummary(x.id));
            all.put(o);
        }
        return all;
    }
    private String primarySnapshot(){
        for(Anchors.Anchor x:prefs.anchors())if(x.enabled)return prefs.snapshot(x.id).toString();
        return "{}";
    }
    /** Definitions only: a backup carries the list, not the observation of the moment. */
    private JSONArray anchorList(){
        JSONArray all=new JSONArray();
        for(Anchors.Anchor x:prefs.anchors()){JSONObject o=new JSONObject();Prefs.put(o,"id",x.id);Prefs.put(o,"name",x.name);Prefs.put(o,"uid",x.uid);Prefs.put(o,"uidText",x.uidText);Prefs.put(o,"room",x.room);Prefs.put(o,"enabled",x.enabled);Prefs.put(o,"alarm",x.alarm);all.put(o);}
        return all;
    }
    /** Weekly/monthly counts and the latest session, computed from the local record only. */
    private JSONObject statSummary(String id){
        JSONArray a=Prefs.array(prefs.raw().getString("stats."+id,"[]"));
        long now=System.currentTimeMillis();int c7=0,c30=0;long last=0,lastLen=-1;
        for(int i=0;i<a.length();i++){
            JSONObject r=a.optJSONObject(i);if(r==null)continue;
            long s=r.optLong("s",0),e=r.optLong("e",0);
            if(e==0)e=now;
            if(now-s<=7L*86400000)c7++;
            if(now-s<=30L*86400000)c30++;
            if(s>=last){last=s;lastLen=e-s;}
        }
        JSONObject o=new JSONObject();
        Prefs.put(o,"count7",c7);Prefs.put(o,"count30",c30);Prefs.put(o,"last",last);
        Prefs.put(o,"lastMinutes",lastLen<0?JSONObject.NULL:Long.valueOf(lastLen/60000L));
        return o;
    }
    private JSONArray scheduleEntries(List<Schedule.Entry> list){
        JSONArray all=new JSONArray();
        for(Schedule.Entry e:list){JSONObject o=new JSONObject();Prefs.put(o,"id",e.id);Prefs.put(o,"days",e.days);Prefs.put(o,"start",e.start);Prefs.put(o,"end",e.end);Prefs.put(o,"note",e.note);all.put(o);}
        return all;
    }
    private List<Anchors.Anchor> readAnchorList(JSONArray a){
        ArrayList<Anchors.Anchor> list=new ArrayList<>();
        if(a==null)return list;
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);if(o==null)continue;
            list.add(new Anchors.Anchor(o.optString("id"),o.optString("name"),Prefs.uidOf(o),o.optLong("room"),o.optBoolean("enabled",true),o.optBoolean("alarm",true)));
        }
        return Anchors.sanitize(list);
    }
    /** Single write path for the anchor list: validate, persist, then drop what no longer exists. */
    private void applyAnchors(List<Anchors.Anchor> next){
        List<Anchors.Anchor> before=prefs.anchors();
        prefs.saveAnchors(Anchors.validate(next));
        for(Anchors.Anchor x:before)if(Anchors.find(next,x.id)==null)prefs.forget(x.id);
        // A brand new anchor, or one pointed at a different room, must not inherit a session:
        // the previous room's dedup state would decide whether the alarm is allowed to ring.
        for(Anchors.Anchor x:next){
            Anchors.Anchor old=Anchors.find(before,x.id);
            if(old==null||old.room!=x.room||old.uid!=x.uid)prefs.restartAnchor(x.id);
        }
        // Directory work belongs on the background thread, like every other file operation here.
        io.execute(()->AnchorArt.prune(this,prefs.anchors()));
    }
    private boolean downloadAvatar(String id,String face){
        if(face==null||face.isEmpty())return false;
        try{return AnchorArt.save(this,id,BiliApi.download(face,2*1024*1024));}
        catch(Exception e){return false;}
    }
    private void refresh(){
        if(prefs.enabled()){GuardianService.send(this,"CHECK");return;}
        if(refreshing||System.currentTimeMillis()-refreshAt<15000)return;refreshing=true;refreshAt=System.currentTimeMillis();
        final List<Anchors.Anchor> targets=new ArrayList<>();
        for(Anchors.Anchor a:prefs.anchors())if(a.enabled)targets.add(a);
        io.execute(()->{
            boolean any=false;String error=null;
            for(Anchors.Anchor a:targets){
                try{prefs.saveSnapshot(a.id,new BiliApi(a.uid,a.room).fetch().json());any=true;}
                catch(Exception e){if(error==null)error=e.getMessage();}
            }
            final boolean reached=any;final String message=error;
            prefs.raw().edit().putString("networkError",reached?"":(message==null?"暂时无法连接 B 站，请检查网络后刷新":message)).apply();
            if(reached)prefs.raw().edit().putLong("lastSuccess",System.currentTimeMillis()).apply();
            runOnUiThread(()->{refreshing=false;push();});
        });
    }
    private void permission(String kind){
        Intent i;
        switch(kind){
            case "notifications":
                if(notificationRequestInFlight)return;
                if("revoked".equals(NotificationAccess.policyStatus(this))){
                    NotificationAccess.record(this,prefs,"request_blocked_by_policy",true);
                    toast("系统策略正在拒绝通知授权，请查看“通知授权帮助”并导出排查包");push();return;
                }
                if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                    // Each explicit tap may retry the public runtime request. Cached rejection is not authority.
                    prefs.raw().edit().putBoolean("notificationRequested",true).apply();
                    notificationRequestInFlight=true;notificationRequestBegan=SystemClock.elapsedRealtime();
                    NotificationAccess.record(this,prefs,"before_runtime_request",true);
                    try{requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATIONS);return;}
                    catch(RuntimeException e){notificationRequestInFlight=false;}
                }
                // A rejected/non-displayable runtime prompt needs a user-accessible settings route.
                if(!NotificationAccess.open(this,false,false,prefs))toast("请从手机设置打开本应用的通知权限");return;
            case "notificationSettings":if(!NotificationAccess.open(this,false,false,prefs))toast("请从手机设置打开本应用的通知权限");return;
            case "standardNotifications":if(!NotificationAccess.open(this,false,true,prefs))toast("请从手机设置打开本应用的通知权限");return;
            case "appPermissions":if(!NotificationAccess.open(this,true,false,prefs))toast("请从手机设置打开本应用的权限管理");return;
            case "alarmChannel":i=new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,GuardianService.ALARM_CHANNEL);break;
            case "watchChannel":i=new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,GuardianService.WATCH_CHANNEL);break;
            case "battery":i=new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName()));break;
            case "fullScreen":if(Build.VERSION.SDK_INT<34){toast("此系统版本无需单独授权全屏提醒");return;}i=new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName()));break;
            case "exact":if(Build.VERSION.SDK_INT<31){toast("此系统版本已允许精确闹钟");return;}i=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()));break;
            case "dnd":i=new Intent("android.settings.ZEN_MODE_SETTINGS");break;
            case "overlay":i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));break;
            case "accessibility":i=new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);break;
            default:i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));
        }
        try{startActivity(i);}catch(Exception e){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}catch(Exception ignored){toast("请在手机设置中打开VR闹钟的应用设置");}}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==NOTIFICATIONS){
            notificationRequestInFlight=false;
            String result="cancelled";
            for(int k=0;k<permissions.length&&k<grants.length;k++)
                if(Manifest.permission.POST_NOTIFICATIONS.equals(permissions[k]))result=grants[k]==PackageManager.PERMISSION_GRANTED?"granted":"denied";
            prefs.raw().edit().putString("notificationRequestResult",result).putLong("notificationRequestAt",System.currentTimeMillis()).apply();
            long elapsed=notificationRequestBegan>0?SystemClock.elapsedRealtime()-notificationRequestBegan:-1;
            prefs.raw().edit().putLong("notificationRequestDurationMs",elapsed).apply();
            prefs.log("system","通知授权返回结果",result+" · "+elapsed+" ms");
            NotificationAccess.record(this,prefs,"runtime_callback_"+result,true);
            if(!"granted".equals(result))toast("revoked".equals(NotificationAccess.policyStatus(this))
                ?"系统策略拒绝了通知授权，请在“通知授权帮助”中导出排查包"
                :"通知尚未获准；可打开系统通知设置，或在应用设置中启用“通知异常时仍响铃”");
        }
        refreshPermissionsAfterReturn();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==SCHEDULE_IMAGE&&(result!=RESULT_OK||data==null||data.getData()==null)){
            // The user cancelled the picker: answer the deferred editor request instead of
            // leaving it hanging until the bridge timeout.
            String back=pendingPickRequest;pendingPickRequest=null;
            if(back!=null)reply(back,false,"未选择图片");
            return;
        }
        if(request==PICK_BACKGROUND&&(result!=RESULT_OK||data==null||data.getData()==null)){
            String back=pendingPickRequest;pendingPickRequest=null;
            if(back!=null)reply(back,false,"未选择图片");
            return;
        }
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==NOTIFICATION_REPORT||request==COMPONENT_REPORT){
            NotificationAccess.record(this,prefs,"report_export",true);
            JSONObject report=new JSONObject();Prefs.put(report,"schema",1);Prefs.put(report,"app","VR闹钟");
            Prefs.put(report,"exportedAt",System.currentTimeMillis());Prefs.put(report,"notificationDiagnostics",NotificationAccess.diagnostics(this,prefs));
            String diagnostic=report.toString();boolean components=request==COMPONENT_REPORT;
            toast("正在保存通知排查包…");
            io.execute(()->{try{
                NotificationReport.write(getApplicationContext(),getContentResolver().openOutputStream(uri,"wt"),diagnostic,components);
                runOnUiThread(()->toast("通知排查包已保存，请把 ZIP 文件发回；不会自动发送"));
            }catch(Exception e){
                try{DocumentsContract.deleteDocument(getContentResolver(),uri);}catch(Exception ignored){}
                runOnUiThread(()->toast("排查包未完整保存，请重新导出："+e.getClass().getSimpleName()));
            }});return;
        }
        if(request==SCHEDULE_IMAGE){
            final String back=pendingPickRequest;pendingPickRequest=null;
            io.execute(()->{
                try{
                    byte[] bytes=readBytesBounded(uri,8*1024*1024);
                    boolean saved=AnchorArt.saveScheduleImage(this,pendingScheduleId,bytes);
                    runOnUiThread(()->{toast(saved?"周表图片已保存":"图片保存失败，请换一张试试");if(back!=null)reply(back,saved,saved?true:"图片保存失败，请换一张试试");push();});
                }catch(Exception e){
                    runOnUiThread(()->{toast("图片未导入："+errorText(e));if(back!=null)reply(back,false,errorText(e));});
                }
            });return;
        }
        if(request==PICK_BACKGROUND){
            final String back=pendingPickRequest;pendingPickRequest=null;
            toast("正在导入背景图片…");
            io.execute(()->{try{
                BackgroundStore.importImage(getApplicationContext(),prefs,uri);
                runOnUiThread(()->{applySecureFlag();applyTheme();push();toast("背景图片已保存");if(back!=null)reply(back,true,Boolean.TRUE);});
            }catch(Exception e){
                runOnUiThread(()->{toast("图片未导入："+errorText(e));if(back!=null)reply(back,false,errorText(e));});
            }});
        }
        if(request==AUDIO){toast("正在导入铃声…");io.execute(()->importAudio(uri));}
        if(request==EXPORT){String text=pendingExport;io.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException();out.write(text.getBytes("UTF-8"));runOnUiThread(()->toast("备份已导出；自选铃声文件不包含在备份中"));}catch(Exception e){runOnUiThread(()->toast("导出失败，请检查保存位置"));}});}
        if(request==IMPORT)io.execute(()->{
            try{String text=readBounded(uri,1048576);JSONObject backup=new JSONObject(text);if(!"HazelAlarm".equals(backup.optString("app"))||backup.optInt("schema")!=1)throw new IOException("不是有效的VR闹钟备份");JSONObject settings=backup.getJSONObject("settings");if("custom".equals(settings.optString("ringtone"))&&prefs.raw().getString("customPath","").isEmpty())settings.put("ringtone","starlight");
                final List<Anchors.Anchor> anchors=backup.has("anchors")?readAnchorList(backup.optJSONArray("anchors")):new ArrayList<Anchors.Anchor>();
                runOnUiThread(()->new AlertDialog.Builder(this).setTitle("恢复提醒设置？").setMessage("将替换当前时段、声音设置和主播列表，不恢复旧通知记录。自选铃声需要另行导入。"+(settings.optBoolean("soundWithoutNotifications")?"此备份会开启通知异常时仍响铃，即使通知未获准也按所选设置播放闹铃。":"")).setNegativeButton("取消",null).setPositiveButton("恢复",(dialog,which)->{try{prefs.update(settings);if(!anchors.isEmpty()){try{applyAnchors(anchors);}catch(IllegalArgumentException e){toast("备份中的主播列表不可用，已保留当前主播");}}AlarmScheduler.boundaries(this);if(prefs.enabled())GuardianService.send(this,"CHECK");applyTheme();push();toast("设置已恢复");}catch(Exception e){toast("恢复失败："+e.getMessage());}}).show());
            }catch(Exception e){runOnUiThread(()->toast("备份读取失败："+e.getMessage()));}
        });
    }
    private byte[] readFileBounded(java.io.File f,int cap)throws IOException{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>cap)throw new IOException("图片过大");}return out.toByteArray();}}
    /** Large pictures are downscaled to ~1280 px JPEG before upload: faster, cheaper, same text. */
    private byte[] shrinkForUpload(java.io.File art){
        try{
            android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(art.getAbsolutePath(),bounds);
            int sample=1;
            while(bounds.outWidth/(sample*2)>=1280||bounds.outHeight/(sample*2)>=1280)sample*=2;
            android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();
            opts.inSampleSize=sample;
            android.graphics.Bitmap bm=android.graphics.BitmapFactory.decodeFile(art.getAbsolutePath(),opts);
            if(bm==null)return null;
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            bm.compress(android.graphics.Bitmap.CompressFormat.JPEG,85,out);
            bm.recycle();
            return out.toByteArray();
        }catch(Exception e){return null;}
    }
    /** Binary companion of readBounded: same cap rule, no charset step. */
    private byte[] readBytesBounded(Uri uri,int cap)throws IOException{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException("文件不可读");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>cap)throw new IOException("图片过大，请选择 8 MB 以内的图片");}return out.toByteArray();}}
    private String readBounded(Uri uri,int cap)throws IOException{try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IOException("文件不可读");byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>cap)throw new IOException("文件过大");}return out.toString("UTF-8");}}
    private void importAudio(Uri uri){
        File dest=new File(getFilesDir(),"tone-"+UUID.randomUUID()+".audio");
        try{
            String name="自选铃声";try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}
            try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(dest)){if(in==null)throw new IOException("无法读取音频");byte[] b=new byte[8192];long size=0;int n;while((n=in.read(b))!=-1){size+=n;if(size>30L*1024*1024)throw new IOException("请选择小于 30 MB 的音频");out.write(b,0,n);}if(size==0)throw new IOException("音频文件为空");}
            MediaMetadataRetriever metadata=new MediaMetadataRetriever();try{metadata.setDataSource(dest.getAbsolutePath());String duration=metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);if(duration==null||Long.parseLong(duration)<=0)throw new IOException("此音频无法播放，请选择 MP3、M4A、OGG 或 WAV");}finally{metadata.release();}
            String old=prefs.raw().getString("customPath","");prefs.raw().edit().putString("customPath",dest.getAbsolutePath()).putString("customName",name.length()>100?name.substring(0,100):name).commit();JSONObject change=new JSONObject();change.put("ringtone","custom");prefs.update(change);
            if(!old.isEmpty()){File oldFile=new File(old);if(oldFile.getParentFile().equals(getFilesDir()))oldFile.delete();}
            runOnUiThread(()->{toast("铃声已导入，原文件移动后仍可使用");push();});
        }catch(Exception e){dest.delete();runOnUiThread(()->toast("导入失败："+e.getMessage()));}
    }
}

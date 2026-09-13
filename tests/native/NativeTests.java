package dev.hazel.livealarm.tests;

import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.*;
import dev.hazel.livealarm.*;
import org.json.JSONObject;
import java.lang.reflect.*;
import java.util.function.BooleanSupplier;

/** Run on a disposable Android test device, after denying POST_NOTIFICATIONS. */
public final class NativeTests extends Instrumentation {
    private MainActivity activity;
    private Prefs prefs;
    private int count;
    private boolean foregroundMode;
    private final StringBuilder log=new StringBuilder();
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);count++;log.append("PASS: ").append(label).append('\n');}
    private void waitFor(BooleanSupplier condition,long timeout,String label){
        long end=SystemClock.elapsedRealtime()+timeout;
        while(!condition.getAsBoolean()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);
        check(condition.getAsBoolean(),label);
    }
    private Object dispatch(String name,JSONObject data)throws Exception{
        Method m=MainActivity.class.getDeclaredMethod("dispatch",String.class,JSONObject.class);m.setAccessible(true);
        Object[] result=new Object[1];Throwable[] failure=new Throwable[1];
        runOnMainSync(()->{try{result[0]=m.invoke(activity,name,data);}catch(Throwable t){failure[0]=t;}});
        if(failure[0]!=null)throw new Exception(failure[0]);return result[0];
    }
    private boolean playing(){
        try{
            Field ref=GuardianService.class.getDeclaredField("active");ref.setAccessible(true);
            GuardianService service=(GuardianService)((java.lang.ref.WeakReference<?>)ref.get(null)).get();
            if(service==null)return false;
            Field player=GuardianService.class.getDeclaredField("player");player.setAccessible(true);
            MediaPlayer p=(MediaPlayer)player.get(service);return p!=null&&p.isPlaying();
        }catch(Exception e){return false;}
    }
    private JSONObject patch(String key,Object value)throws Exception{return new JSONObject().put(key,value);}
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);foregroundMode=arguments!=null&&"foreground".equals(arguments.getString("mode"));start();}
    private Notification notice(int id){
        for(android.service.notification.StatusBarNotification item:((NotificationManager)getTargetContext().getSystemService(Context.NOTIFICATION_SERVICE)).getActiveNotifications())
            if(item.getId()==id)return item.getNotification();
        return null;
    }
    /** On a disposable device with notification permission and both channels allowed. */
    private void foregroundChecks(Context c)throws Exception{
        check(NotificationAccess.runtimeGranted(c),"notification runtime grant is provided by test-device setup");
        check(NotificationAccess.alertMode(c,prefs)==AlertPolicy.Mode.NORMAL,"normal alarm notifications are available");
        // No real live event is eligible during this lifecycle test.
        java.time.ZonedDateTime utc=java.time.Instant.now().atZone(java.time.ZoneOffset.UTC);
        int later=(utc.getHour()*60+utc.getMinute()+120)%1440;
        JSONObject window=new JSONObject().put("id","native-test-window").put("name","测试时段").put("start",later).put("end",(later+1)%1440).put("days",127).put("enabled",true);
        prefs.update(new JSONObject().put("allDay",false).put("timezone","UTC").put("windows",new org.json.JSONArray().put(window)));
        dispatch("toggle",patch("enabled",true));
        waitFor(()->GuardianService.running&&notice(GuardianService.WATCH_ID)!=null,10000,"foreground watch notification exists");
        check((notice(GuardianService.WATCH_ID).flags&Notification.FLAG_FOREGROUND_SERVICE)!=0,"watch ID is the actual foreground-service notification");
        dispatch("test",new JSONObject());
        waitFor(()->GuardianService.ringing&&playing()&&notice(GuardianService.ALARM_ID)!=null,15000,"real normal alarm audio and separate alarm notification start");
        check(notice(GuardianService.WATCH_ID)!=null,"watch notification stays registered while ringing");
        check((notice(GuardianService.WATCH_ID).flags&Notification.FLAG_FOREGROUND_SERVICE)!=0,"foreground ID is not swapped to the alarm");
        dispatch("dismiss",new JSONObject());
        waitFor(()->!GuardianService.ringing&&notice(GuardianService.ALARM_ID)==null,10000,"dismiss removes the alarm notice");
        check(GuardianService.running&&notice(GuardianService.WATCH_ID)!=null,"dismiss preserves monitoring and its notification");
        prefs.raw().edit().putLong("testAt",System.currentTimeMillis()+60000).putLong("snoozeAt",System.currentTimeMillis()+60000).commit();
        dispatch("toggle",patch("enabled",false));
        waitFor(()->!GuardianService.running&&notice(GuardianService.WATCH_ID)==null,10000,"explicit stop removes foreground service and watch notice");
        check(prefs.raw().getLong("testAt",0)==0&&prefs.raw().getLong("snoozeAt",0)==0,"stop clears future test and snooze requests");
        long started=prefs.raw().getLong("serviceStartedAt",0);
        GuardianService.stopWatching(c);SystemClock.sleep(1000);
        check(!GuardianService.running&&started==prefs.raw().getLong("serviceStartedAt",0),"repeated stop does not start another service");
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Context c=getTargetContext();prefs=new Prefs(c);prefs.setEnabled(false);
            prefs.update(patch("soundWithoutNotifications",false));prefs.update(patch("duration",15));
            activity=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            check("满区闹钟".contentEquals(c.getApplicationInfo().loadLabel(c.getPackageManager())),"installed app label is 满区闹钟");
            if(foregroundMode){foregroundChecks(c);result.putString("stream",log+"PASS: "+count+" native foreground assertions\n");finish(-1,result);return;}
            check(!NotificationAccess.runtimeGranted(c),"real POST_NOTIFICATIONS is denied at start");
            check(NotificationAccess.alertMode(c,prefs)==AlertPolicy.Mode.BLOCKED,"no consent blocks native alarm access");
            boolean blocked=false;try{dispatch("test",new JSONObject());}catch(Exception expected){blocked=true;}
            check(blocked&&!GuardianService.ringing,"native immediate test is blocked before consent");
            prefs.update(patch("soundWithoutNotifications",true));
            check(NotificationAccess.alertMode(c,prefs)==AlertPolicy.Mode.SOUND_ONLY,"native consent selects sound-only mode");
            dispatch("test",new JSONObject());
            waitFor(()->GuardianService.running&&GuardianService.ringing&&playing(),45000,"real foreground service and MediaPlayer run with denied notification");
            check(!NotificationAccess.runtimeGranted(c),"playing does not grant notification permission");
            check("SOUND_ONLY".equals(prefs.raw().getString("lastAlarmMode","")),"actual trigger records sound-only mode");
            check(!GuardianService.overlayVisible,"no overlay is required while app is visible");
            dispatch("dismiss",new JSONObject());
            waitFor(()->!GuardianService.ringing&&!playing(),10000,"dismiss stops native audio without a notification action");
            waitFor(()->!GuardianService.running,10000,"test-only foreground service stops after dismiss");
            activity=(MainActivity)startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            dispatch("test",new JSONObject());
            waitFor(()->GuardianService.ringing&&playing(),45000,"second native test starts");
            waitFor(()->!GuardianService.ringing&&!playing(),40000,"configured timeout stops sound-only audio");
            prefs.update(patch("soundWithoutNotifications",false));
            check(NotificationAccess.alertMode(c,prefs)==AlertPolicy.Mode.BLOCKED,"opting out restores native permission guard");
            result.putString("stream",log+"PASS: "+count+" native Android runtime assertions\n");
            finish(-1,result);
        }catch(Throwable e){result.putString("stream",log+"FAIL: "+e+"\n");result.putString("failure",e.toString());finish(0,result);}
        finally{if(prefs!=null)prefs.setEnabled(false);if(GuardianService.running)GuardianService.send(getTargetContext(),"STOP_WATCH");}
    }
}

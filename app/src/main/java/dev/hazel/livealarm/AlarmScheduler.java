package dev.hazel.livealarm;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;

public final class AlarmScheduler {
    public static final String BOUNDARY="BOUNDARY", SNOOZE="SNOOZE_FIRE", TEST="TEST_FIRE", WATCHDOG="WATCHDOG", KEEPALIVE="KEEPALIVE", PRESTREAM="PRESTREAM";
    /** How long the watch may go silent before the watchdog tries to bring the service back. */
    public static final long WATCHDOG_MINUTES=15;
    static int id(String action){return BOUNDARY.equals(action)?201:SNOOZE.equals(action)?202:TEST.equals(action)?203:WATCHDOG.equals(action)?204:KEEPALIVE.equals(action)?205:206;}
    static PendingIntent intent(Context c,String action){return PendingIntent.getBroadcast(c,id(action),new Intent(c,ActionReceiver.class).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static boolean exact(Context c){return Build.VERSION.SDK_INT<31||((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();}
    public static void cancel(Context c,String action){((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(intent(c,action));}
    public static void at(Context c,String action,long time){
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);PendingIntent p=intent(c,action);
        try{if(exact(c))a.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,p);else a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,p);}
        catch(SecurityException e){a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,p);}
    }
    /**
     * An alarm-clock alarm is the one wake-up the platform does not defer: allow-while-idle
     * alarms are pushed to the Doze floor (about nine minutes), which is precisely the delay
     * users see when a stream is noticed late. The price is a status-bar alarm icon, so this is
     * used only by continuous mode.
     */
    public static void atDozeProof(Context c,String action,long time){
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);PendingIntent p=intent(c,action);
        if(exact(c)){
            try{a.setAlarmClock(new AlarmManager.AlarmClockInfo(time,showIntent(c)),p);return;}
            catch(RuntimeException ignored){ /* fall through to the weaker wake-up */ }
        }
        try{a.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,p);}catch(SecurityException ignored){}
    }
    /** Tapping the status-bar alarm icon must land somewhere sensible, not on nothing. */
    private static PendingIntent showIntent(Context c){
        Intent i=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void boundaries(Context context){
        Prefs p=new Prefs(context);cancel(context,BOUNDARY);if(!p.enabled())return;JSONObject c=p.config();
        long next=TimeRules.nextBoundary(System.currentTimeMillis(),c.optBoolean("allDay"),p.windows(c),TimeRules.zone(c.optString("timezone")));
        if(next>0)at(context,BOUNDARY,next);
    }
    /**
     * Periodic revival. An all-day watch has no window boundary to rely on, so without this a
     * process the system killed stays dead until the user opens the app again.
     */
    public static void watchdog(Context context){
        Prefs p=new Prefs(context);cancel(context,WATCHDOG);if(!p.enabled())return;
        at(context,WATCHDOG,System.currentTimeMillis()+WATCHDOG_MINUTES*60000L);
    }
    /**
     * Re-arms the safety net behind the check loop. The ordinary cycle runs on the main-thread
     * handler, which has no wake-up of its own: a sleeping device or an OEM background limit can
     * stop it silently while the process still looks alive. Every completed check pushes this
     * alarm further out, so it only ever fires when the ordinary cycle really did not come back.
     */
    /**
     * The weekly-schedule heads-up: one exact alarm at the scheduled start minus the lead.
     * `startAt` is the start itself, so the subtraction happens in exactly one place here.
     */
    public static void preStream(Context context,long startAt){
        Prefs p=new Prefs(context);cancel(context,PRESTREAM);
        if(!p.enabled()||startAt<=0)return;
        at(context,PRESTREAM,startAt-PollPlan.PRESTREAM_LEAD_MILLIS);
    }
    public static void keepAlive(Context context){
        Prefs p=new Prefs(context);cancel(context,KEEPALIVE);if(!p.enabled())return;
        long now=System.currentTimeMillis();JSONObject c=p.config();
        boolean continuous=c.optBoolean("turbo",true);
        int seconds=PollPlan.keepAliveSeconds(c.optInt("pollSeconds",30),p.allowed(now),continuous);
        if(continuous)atDozeProof(context,KEEPALIVE,now+seconds*1000L);
        else at(context,KEEPALIVE,now+seconds*1000L);
    }
}

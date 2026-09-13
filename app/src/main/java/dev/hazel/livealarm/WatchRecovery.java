package dev.hazel.livealarm;

import android.app.*;
import android.content.*;
import android.os.SystemClock;

/** A best-effort, inexact safety check independent of the service's Handler, ported from
 *  upstream 1.0.6. Runs on ELAPSED_REALTIME_WAKEUP so a wall-clock jump (NTP correction,
 *  manual time change) cannot stall every RTC-based alarm at once; the RTC loop stays the
 *  primary path and this only notices when the process or its handler is gone. */
public final class WatchRecovery {
    public static final String ACTION="WATCH_RECOVERY";
    private static final long INTERVAL=15*60*1000L;
    private static PendingIntent intent(Context c){return PendingIntent.getBroadcast(c,207,new Intent(c,ActionReceiver.class).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    public static void cancel(Context c){
        ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).cancel(intent(c));
        new Prefs(c).raw().edit().putLong("recoveryElapsed",0).putLong("recoveryAt",0).apply();
    }
    public static void schedule(Context c,boolean reset){
        Prefs p=new Prefs(c);
        if(!p.enabled()||!p.config().optBoolean("recovery",true)){cancel(c);return;}
        long now=SystemClock.elapsedRealtime(),old=p.raw().getLong("recoveryElapsed",0);
        if(!reset&&old>now+1000&&old<=now+INTERVAL)return;
        long due=now+INTERVAL;
        try{
            ((AlarmManager)c.getSystemService(Context.ALARM_SERVICE)).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,due,intent(c));
            p.raw().edit().putLong("recoveryElapsed",due).putLong("recoveryAt",System.currentTimeMillis()+INTERVAL).apply();
        }catch(RuntimeException e){p.raw().edit().putLong("recoveryElapsed",0).putLong("recoveryAt",0).apply();p.log("warning","恢复检查未能安排","请检查系统后台设置；前台守候仍使用原有检测流程");}
    }
    public static void receive(Context c){
        Prefs p=new Prefs(c);
        if(!p.enabled()||!p.config().optBoolean("recovery",true)){cancel(c);return;}
        schedule(c,true);
        if(!GuardianService.running){
            p.raw().edit().putLong("lastRecovery",System.currentTimeMillis()).apply();
            boolean accepted=GuardianService.send(c,"CHECK");
            p.log(accepted?"system":"warning",accepted?"已请求恢复守候":"恢复守候受到系统限制",accepted?"定时恢复检查发现服务中断；实际状态以服务心跳为准":"可从桌面图标或通知返回应用重新开启；不会更改系统权限");
        }
    }
}

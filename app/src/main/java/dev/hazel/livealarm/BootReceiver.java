package dev.hazel.livealarm;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        String a=i.getAction();if(a==null)return;
        if(!a.equals(Intent.ACTION_BOOT_COMPLETED)&&!a.equals(Intent.ACTION_MY_PACKAGE_REPLACED)&&!a.equals(Intent.ACTION_TIME_CHANGED)&&!a.equals(Intent.ACTION_TIMEZONE_CHANGED)&&!a.equals("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"))return;
        Prefs p=new Prefs(c);
        if(a.equals(Intent.ACTION_BOOT_COMPLETED)){
            // Never resurrect test alarms or snoozes after a reboot.
            p.raw().edit().putLong("testAt",0).putLong("snoozeAt",0).apply();
            if(!p.config().optBoolean("boot"))return;
        }
        if(p.enabled()){p.log("system","守候恢复","系统时间变化、重启或应用更新后恢复检查");GuardianService.send(c,"CHECK");AlarmScheduler.boundaries(c);AlarmScheduler.watchdog(c);AlarmScheduler.keepAlive(c);WatchRecovery.schedule(c,true);WatchWidget.update(c);}
    }
}

package dev.hazel.livealarm;

import android.content.*;

public class ActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String action=intent.getAction();Prefs p=new Prefs(context);
        if(WatchRecovery.ACTION.equals(action)){WatchRecovery.receive(context);return;}
        if("STOP_WATCH".equals(action)){GuardianService.stopWatching(context);p.log("system","已从通知停止守候","取消检测、暂缓和锁屏测试，不会自动重新开启");return;}
        if("CHECK_NOW".equals(action)){if(p.enabled())GuardianService.send(context,"CHECK");return;}
        if("DISMISS".equals(action)||"SNOOZE".equals(action)){if(GuardianService.running)GuardianService.send(context,action);return;}
        if(AlarmScheduler.BOUNDARY.equals(action)){if(p.enabled())GuardianService.send(context,"CHECK");AlarmScheduler.boundaries(context);return;}
        if(AlarmScheduler.WATCHDOG.equals(action)){
            if(!p.enabled())return;
            AlarmScheduler.watchdog(context);
            long beat=p.raw().getLong("serviceHeartbeatAt",0),silent=System.currentTimeMillis()-beat;
            if(beat<=0||silent>AlarmScheduler.WATCHDOG_MINUTES*60000L){
                // send() logs its own failure if the system refuses a background restart.
                p.log("system","守候似乎已中断，正在尝试恢复",beat<=0?"尚未收到服务心跳":"已约 "+(silent/60000)+" 分钟没有服务心跳");
                GuardianService.send(context,"CHECK");
            }
            return;
        }
        if(AlarmScheduler.KEEPALIVE.equals(action)){
            if(!p.enabled())return;
            // Re-arm first: a refused background start must not break the chain, or the watch
            // would stay silent until the fifteen-minute watchdog happened to notice.
            AlarmScheduler.keepAlive(context);
            GuardianService.send(context,"CHECK");
            return;
        }
        if(AlarmScheduler.PRESTREAM.equals(action)){
            if(!p.enabled())return;
            long startAt=p.raw().getLong("preStreamAt",0);
            // The alarm is armed for start minus the lead, so freshness is measured against that
            // moment: a fire the device slept through stays silent instead of announcing a stream
            // that has already begun. The next cycle re-arms anyway.
            long armedFor=startAt-PollPlan.PRESTREAM_LEAD_MILLIS;
            if(startAt>0&&armedFor>0&&System.currentTimeMillis()-armedFor<120000L)GuardianService.preStreamNotice(context);
            return;
        }
        if(AlarmScheduler.SNOOZE.equals(action)){if(p.enabled()&&p.raw().getLong("snoozeAt",0)>0)GuardianService.send(context,action);return;}
        if(AlarmScheduler.TEST.equals(action)&&p.raw().getLong("testAt",0)>0){p.raw().edit().putLong("testAt",0).apply();GuardianService.send(context,"TEST");}
    }
}

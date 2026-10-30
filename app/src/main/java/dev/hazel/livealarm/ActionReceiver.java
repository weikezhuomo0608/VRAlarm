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
            // Outside the schedule a silent service is the intended state, so nothing here may
            // report an interruption or start it again. It still keeps firing, which is exactly
            // what makes it the second net for the resume: if the boundary alarm were ever lost,
            // the first watchdog that lands inside a window brings the watch back.
            if(!p.watchingNow())return;
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
            // The chain is cancelled with the watch itself when the schedule closes: re-arming it
            // would keep waking the device to ask for a service that must stay parked.
            if(!p.watchingNow()){AlarmScheduler.cancel(context,AlarmScheduler.KEEPALIVE);return;}
            // Re-arm first: a refused background start must not break the chain, or the watch
            // would stay silent until the fifteen-minute watchdog happened to notice.
            AlarmScheduler.keepAlive(context);
            GuardianService.send(context,"CHECK");
            return;
        }
        if(AlarmScheduler.PRESTREAM.equals(action)){
            if(!p.enabled())return;
            // Freshness is measured against the moment the alarm was actually set for, which is
            // stored: a fire the device slept through stays silent instead of announcing a stream
            // that has already begun. The next cycle re-arms anyway. No lead is subtracted here —
            // per-entry leads turned that arithmetic into a bug waiting to happen.
            long armAt=p.raw().getLong("preStreamArmAt",0);
            if(armAt>0&&System.currentTimeMillis()-armAt<120000L)GuardianService.preStreamNotice(context);
            return;
        }
        if(AlarmScheduler.SNOOZE.equals(action)){if(p.enabled()&&p.raw().getLong("snoozeAt",0)>0)GuardianService.send(context,action);return;}
        if(AlarmScheduler.TEST.equals(action)&&p.raw().getLong("testAt",0)>0){p.raw().edit().putLong("testAt",0).apply();GuardianService.send(context,"TEST");return;}
        // A red-packet notice's own button. Tapping the notification is what grants the app a short
        // window in which it may start an activity, which is why this lives here and not in the
        // service: a background start from the service would simply be refused on Android 10+.
        if("OPEN_ROOM".equals(action)){
            long room=intent.getLongExtra("room",0);
            if(room<=0)return;
            Intent view=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://live.bilibili.com/"+room)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try{context.startActivity(new Intent(view).setPackage("tv.danmaku.bili"));}
            catch(Exception e){
                try{context.startActivity(new Intent(view));}
                catch(Exception ignored){p.log("warning","没有可用的应用打开直播间","请安装 B 站客户端或浏览器");}
            }
        }
    }
}

package dev.hazel.livealarm;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import org.json.*;

/** Home-screen glance of the watch: status, who is live, the next schedule slot, last check. */
public class WatchWidget extends AppWidgetProvider {
    /** What the last push drew, so a refresh that would draw the same thing is skipped: every cycle
     *  and every heartbeat calls update(), and waking the launcher to hand it identical text is pure
     *  cost. The clock inside the texts only moves once a minute, so most of those calls really are
     *  identical. Process-local on purpose — after a restart one extra draw is cheap and always right. */
    private static String lastRendered=null;
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){lastRendered=null;update(c);}
    /** Refreshes every placed widget from local state only; never touches the network. */
    public static void update(Context c){
        AppWidgetManager m=AppWidgetManager.getInstance(c);
        int[] ids=m.getAppWidgetIds(new ComponentName(c,WatchWidget.class));
        // Nothing placed: no launcher to talk to at all.
        if(ids.length==0){lastRendered=null;return;}
        Prefs p=new Prefs(c);
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.watch_widget);
        boolean enabled=p.enabled();
        // Outside the schedule the watch is parked on purpose. Saying "暂未开播" there would report
        // a snapshot taken before the window closed as if it were current, so the glance says the
        // watch is paused instead.
        boolean watching=p.watchingNow(),paused=enabled&&!watching;
        String titleText=enabled?(watching?p.raw().getString("watchTitle","正在守候主播"):"时段外 · 守候暂停"):"守候未开启";
        v.setTextViewText(R.id.widget_title,titleText);
        StringBuilder live=new StringBuilder();
        for(Anchors.Anchor a:p.anchors()){
            if(!a.enabled)continue;
            if(p.snapshot(a.id).optInt("status",0)==1){
                if(live.length()>0)live.append("、");
                live.append(a.name);
            }
        }
        String liveText=paused?"⏸ 时段外不检测":live.length()>0?"🔴 正在直播："+live:"⚪ 暂未开播";
        v.setTextViewText(R.id.widget_live,liveText);
        long preAt=p.raw().getLong("preStreamAt",0);
        String preName=p.raw().getString("preStreamName","");
        // preStreamAt is the scheduled start itself, so it is printed without re-adding the lead.
        boolean upcoming=enabled&&preAt>System.currentTimeMillis();
        String nextText=upcoming?"周表预告："+preName+" "+fmt(c,preAt):"周表预告：暂无";
        v.setTextViewText(R.id.widget_next,nextText);
        long last=p.raw().getLong("lastSuccess",0);
        String checkText=last>0?"上次检测 "+fmt(c,last):"尚未检测";
        v.setTextViewText(R.id.widget_check,checkText);
        v.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,0,
            new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        // One string for everything that would be drawn, placed widgets included: identical content
        // means the launcher has nothing to redraw.
        StringBuilder signature=new StringBuilder(titleText).append('\u0000').append(liveText).append('\u0000')
            .append(nextText).append('\u0000').append(checkText).append('\u0000');
        for(int id:ids)signature.append(id).append(',');
        String rendered=signature.toString();
        if(rendered.equals(lastRendered))return;
        lastRendered=rendered;
        for(int id:ids)m.updateAppWidget(id,v);
    }
    private static String fmt(Context c,long at){
        return java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(TimeRules.zone(new Prefs(c).config().optString("timezone")))
            .format(java.time.Instant.ofEpochMilli(at));
    }
}

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
    @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){update(c);}
    /** Refreshes every placed widget from local state only; never touches the network. */
    public static void update(Context c){
        Prefs p=new Prefs(c);
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.watch_widget);
        boolean enabled=p.enabled();
        v.setTextViewText(R.id.widget_title,enabled?p.raw().getString("watchTitle","正在守候主播"):"守候未开启");
        StringBuilder live=new StringBuilder();
        for(Anchors.Anchor a:p.anchors()){
            if(!a.enabled)continue;
            if(p.snapshot(a.id).optInt("status",0)==1){
                if(live.length()>0)live.append("、");
                live.append(a.name);
            }
        }
        v.setTextViewText(R.id.widget_live,live.length()>0?"🔴 正在直播："+live:"⚪ 暂未开播");
        long preAt=p.raw().getLong("preStreamAt",0);
        String preName=p.raw().getString("preStreamName","");
        // preStreamAt is the scheduled start itself, so it is printed without re-adding the lead.
        boolean upcoming=enabled&&preAt>System.currentTimeMillis();
        v.setTextViewText(R.id.widget_next,upcoming
            ?"周表预告："+preName+" "+fmt(c,preAt)
            :"周表预告：暂无");
        long last=p.raw().getLong("lastSuccess",0);
        v.setTextViewText(R.id.widget_check,last>0?"上次检测 "+fmt(c,last):"尚未检测");
        v.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,0,
            new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
        AppWidgetManager m=AppWidgetManager.getInstance(c);
        for(int id:m.getAppWidgetIds(new ComponentName(c,WatchWidget.class)))m.updateAppWidget(id,v);
    }
    private static String fmt(Context c,long at){
        return java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .withZone(TimeRules.zone(new Prefs(c).config().optString("timezone")))
            .format(java.time.Instant.ofEpochMilli(at));
    }
}

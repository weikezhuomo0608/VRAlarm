package dev.hazel.livealarm;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Optional, temporary controls for a user-consented sound-only alarm. */
final class AlarmOverlay {
    private final Context context;
    private final WindowManager windows;
    private final Prefs prefs;
    private View view;
    AlarmOverlay(Context context, Prefs prefs) {
        this.context=context;this.prefs=prefs;
        windows=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
    }
    private int dp(int n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,boolean bold){
        TextView t=new TextView(context);t.setText(value);t.setTextSize(size);
        t.setTextColor(Color.rgb(44,57,65));if(bold)t.setTypeface(null,Typeface.BOLD);
        t.setPadding(0,dp(5),0,dp(5));return t;
    }
    private Button button(String label,Runnable action){
        Button b=new Button(context);b.setText(label);b.setAllCaps(false);
        b.setTextColor(Color.rgb(44,57,65));b.setOnClickListener(v->action.run());return b;
    }
    void show(String title,String anchorName,boolean test,int duration,int snooze,Runnable dismiss,Runnable later){
        hide();if(!Settings.canDrawOverlays(context))return;
        LinearLayout card=new LinearLayout(context);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20),dp(16),dp(20),dp(16));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(245,245,240));bg.setCornerRadius(dp(22));
        card.setBackground(bg);card.setElevation(dp(10));
        card.addView(text(test?"VR闹钟 · 响铃测试":(anchorName.isEmpty()?"VR闹钟 · 开播了":"VR闹钟 · "+anchorName+"开播了"),21,true));
        card.addView(text(title,16,false));card.addView(text(duration+" 秒后自动停止",14,false));
        card.addView(button(test?"听见了，结束测试":"关闭本次响铃",dismiss));
        if(!test)card.addView(button(snooze+" 分钟后再提醒",later));
        int width=Math.min(dp(350),context.getResources().getDisplayMetrics().widthPixels-dp(32));
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(width,WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                |WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,android.graphics.PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.CENTER;p.setTitle("VR闹钟响铃控制");
        try{windows.addView(card,p);view=card;GuardianService.overlayVisible=true;}
        catch(RuntimeException e){prefs.log("warning","系统未显示响铃悬浮窗","可打开应用关闭响铃；到时会自动停止");}
    }
    void hide(){
        if(view!=null){try{windows.removeViewImmediate(view);}catch(RuntimeException ignored){}view=null;}
        GuardianService.overlayVisible=false;
    }
}

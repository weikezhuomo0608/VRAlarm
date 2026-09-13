package dev.hazel.livealarm;
import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
/** Optional guardian: no window content, actions, gestures, key events or screenshots. */
public class WatchAccessibilityService extends AccessibilityService {
    public static volatile boolean connected=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private long lastAttempt=0;
    private final Runnable pulse=new Runnable(){@Override public void run(){recover();handler.postDelayed(this,45000);}};
    public static boolean enabled(Context c){String values=Settings.Secure.getString(c.getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);if(values==null)return false;ComponentName ours=new ComponentName(c,WatchAccessibilityService.class);for(String item:values.split(":"))if(ours.equals(ComponentName.unflattenFromString(item)))return true;return false;}
    @Override protected void onServiceConnected(){super.onServiceConnected();connected=true;new Prefs(this).log("system","无障碍守候辅助已连接","只检查本应用的守候运行情况，不读取屏幕内容");handler.removeCallbacks(pulse);handler.post(pulse);}
    private void recover(){Prefs p=new Prefs(this);long now=SystemClock.elapsedRealtime();if(p.enabled()&&!GuardianService.running&&(lastAttempt==0||now-lastAttempt>=300000)){lastAttempt=now;if(GuardianService.send(this,"CHECK"))p.log("system","守候辅助请求恢复服务","已向系统请求恢复中断的守候服务");}}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){recover();}
    @Override public void onInterrupt(){}
    @Override public boolean onUnbind(Intent i){connected=false;handler.removeCallbacksAndMessages(null);return super.onUnbind(i);}
    @Override public void onDestroy(){connected=false;handler.removeCallbacksAndMessages(null);super.onDestroy();}
}

package dev.hazel.livealarm;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import org.json.*;
import java.util.*;

/** Ordinary system settings only. No privileged helper or background dependency. */
public final class NotificationAccess {
    private NotificationAccess() {}
    public static boolean runtimeGranted(Context c){
        return Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
    }
    /** Read-only public API. A failed query is unknown, never evidence of a policy block. */
    public static String policyStatus(Context c){
        if(Build.VERSION.SDK_INT<33)return "not_applicable";
        try{return c.getPackageManager().isPermissionRevokedByPolicy(Manifest.permission.POST_NOTIFICATIONS,c.getPackageName())?"revoked":"not_revoked";}
        catch(RuntimeException e){return "unknown";}
    }
    public static synchronized void record(Context c,Prefs prefs,String event,boolean force){
        NotificationManager manager=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean granted=runtimeGranted(c),enabled=manager.areNotificationsEnabled();String policy=policyStatus(c);
        String key=granted+"/"+enabled+"/"+policy;
        if(!force&&key.equals(prefs.raw().getString("notificationTimelineState","")))return;
        JSONObject row=new JSONObject();Prefs.put(row,"at",System.currentTimeMillis());
        Prefs.put(row,"elapsedRealtimeMs",android.os.SystemClock.elapsedRealtime());Prefs.put(row,"event",event);
        Prefs.put(row,"runtimeGranted",granted);Prefs.put(row,"appEnabled",enabled);Prefs.put(row,"policyStatus",policy);
        JSONArray old=Prefs.array(prefs.raw().getString("notificationTimeline","[]")),rows=new JSONArray();
        for(int i=Math.max(0,old.length()-39);i<old.length();i++)rows.put(old.opt(i));rows.put(row);
        prefs.raw().edit().putString("notificationTimeline",rows.toString()).putString("notificationTimelineState",key).apply();
    }
    public static AlertPolicy.Mode alertMode(Context c,Prefs prefs){
        android.app.NotificationManager n=(android.app.NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        android.app.NotificationChannel channel=n.getNotificationChannel(GuardianService.ALARM_CHANNEL);
        return AlertPolicy.mode(runtimeGranted(c),n.areNotificationsEnabled(),channel==null?-1:channel.getImportance(),
            prefs.config().optBoolean("soundWithoutNotifications",false));
    }
    public static JSONObject watchState(Context c,Prefs prefs){
        JSONObject j=new JSONObject();NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean granted=runtimeGranted(c),appEnabled=n.areNotificationsEnabled();
        NotificationChannel channel=n.getNotificationChannel(GuardianService.WATCH_CHANNEL);
        int importance=channel==null?-1:channel.getImportance();boolean groupBlocked=false;
        if(Build.VERSION.SDK_INT>=28&&channel!=null&&channel.getGroup()!=null){
            NotificationChannelGroup group=n.getNotificationChannelGroup(channel.getGroup());groupBlocked=group!=null&&group.isBlocked();
        }
        boolean queried=false,registered=false;
        try{for(android.service.notification.StatusBarNotification item:n.getActiveNotifications())
            if(item.getId()==GuardianService.WATCH_ID&&c.getPackageName().equals(item.getPackageName()))registered=true;
            queried=true;
        }catch(RuntimeException ignored){}
        Prefs.put(j,"status",WatchStatus.resolve(prefs.enabled(),GuardianService.running,granted,appEnabled,importance,groupBlocked,queried,registered));
        Prefs.put(j,"serviceRunning",GuardianService.running);Prefs.put(j,"registered",queried?registered:JSONObject.NULL);
        Prefs.put(j,"channelImportance",importance);Prefs.put(j,"channelBlocked",importance==0||groupBlocked);
        Prefs.put(j,"serviceStartedAt",prefs.raw().getLong("serviceStartedAt",0));Prefs.put(j,"heartbeatAt",prefs.raw().getLong("serviceHeartbeatAt",0));
        Prefs.put(j,"submittedAt",prefs.raw().getLong("watchNotificationSubmittedAt",0));
        return j;
    }
    public static boolean isXiaomi(){
        String brand=(Build.MANUFACTURER+" "+Build.BRAND).toLowerCase(Locale.ROOT);
        return brand.contains("xiaomi")||brand.contains("redmi")||brand.contains("poco");
    }
    private static Intent extras(Context c,Intent i){
        return i.putExtra(Settings.EXTRA_APP_PACKAGE,c.getPackageName())
            .putExtra("app_package",c.getPackageName()).putExtra("app_uid",c.getApplicationInfo().uid)
            .putExtra("packageName",c.getPackageName()).putExtra("extra_pkgname",c.getPackageName());
    }
    public static Intent standard(Context c){return extras(c,new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS));}
    public static Intent details(Context c){return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+c.getPackageName()));}
    public static List<Intent> candidates(Context c,boolean appPermissions,boolean standardOnly){
        List<Intent> list=new ArrayList<>();
        if(!standardOnly&&isXiaomi()){
            if(appPermissions){
                list.add(extras(c,new Intent("miui.intent.action.APP_PERM_EDITOR").setPackage("com.miui.securitycenter")));
            }else{
                // This OEM component is present in the user's system-settings trace.
                list.add(extras(c,new Intent(Intent.ACTION_MAIN)
                    .setClassName("com.android.settings","com.android.settings.Settings$NotificationFilterActivity")));
                list.add(extras(c,new Intent("miui.intent.action.APP_NOTIFICATION_SETTINGS").setPackage("com.android.settings")));
            }
        }
        if(appPermissions)list.add(new Intent("android.intent.action.MANAGE_APP_PERMISSIONS").putExtra(Intent.EXTRA_PACKAGE_NAME,c.getPackageName()));
        list.add(standard(c));list.add(details(c));return list;
    }
    public static boolean open(Activity activity,boolean appPermissions,boolean standardOnly,Prefs prefs){
        for(Intent intent:candidates(activity,appPermissions,standardOnly)){
            try{
                record(activity,prefs,"before_settings",true);
                activity.startActivity(intent);
                String route=intent.getComponent()==null?intent.getAction():intent.getComponent().flattenToShortString();
                prefs.raw().edit().putString("notificationSettingsRoute",route).putLong("notificationSettingsOpenedAt",System.currentTimeMillis()).apply();
                prefs.log("system","打开通知授权设置",route);return true;
            }catch(ActivityNotFoundException|SecurityException unsupported){ /* Try the next normal exported system screen. */ }
        }
        return false;
    }
    public static JSONObject diagnostics(Activity a,Prefs prefs){
        JSONObject j=new JSONObject();PackageManager pm=a.getPackageManager();String pkg=a.getPackageName();
        Prefs.put(j,"package",pkg);Prefs.put(j,"uid",android.os.Process.myUid());Prefs.put(j,"sdk",Build.VERSION.SDK_INT);
        Prefs.put(j,"targetSdk",a.getApplicationInfo().targetSdkVersion);Prefs.put(j,"buildFingerprint",Build.FINGERPRINT);
        Prefs.put(j,"model",Build.MODEL);Prefs.put(j,"xiaomi",isXiaomi());
        Prefs.put(j,"buildDisplay",Build.DISPLAY);Prefs.put(j,"securityPatch",Build.VERSION.SECURITY_PATCH);
        Prefs.put(j,"runtimeGranted",runtimeGranted(a));
        Prefs.put(j,"appEnabled",((NotificationManager)a.getSystemService(Context.NOTIFICATION_SERVICE)).areNotificationsEnabled());
        Prefs.put(j,"policyStatus",policyStatus(a));
        Prefs.put(j,"policySource","not_exposed_to_this_app");
        Prefs.put(j,"watchNotification",watchState(a,prefs));
        Prefs.put(j,"timeline",Prefs.array(prefs.raw().getString("notificationTimeline","[]")));
        Prefs.put(j,"timelineScope","Snapshots at app checks and user actions; not a continuous system audit and not the identity of the writer.");
        Prefs.put(j,"systemComponents",NotificationReport.componentVersions(a));
        try{Prefs.put(j,"managedProfile",((android.os.UserManager)a.getSystemService(Context.USER_SERVICE)).isManagedProfile());}
        catch(RuntimeException e){Prefs.put(j,"managedProfile",JSONObject.NULL);}
        try{
            PackageInfo info=pm.getPackageInfo(pkg,PackageManager.GET_PERMISSIONS);
            boolean declared=false;int flags=0;
            if(info.requestedPermissions!=null)for(int k=0;k<info.requestedPermissions.length;k++)
                if(Manifest.permission.POST_NOTIFICATIONS.equals(info.requestedPermissions[k])){declared=true;if(info.requestedPermissionsFlags!=null)flags=info.requestedPermissionsFlags[k];}
            Prefs.put(j,"declared",declared);Prefs.put(j,"packagePermissionFlags",flags);
            Prefs.put(j,"versionName",info.versionName);Prefs.put(j,"versionCode",info.versionCode);
        }catch(Exception e){Prefs.put(j,"packageInfoError",e.getClass().getSimpleName());}
        if(Build.VERSION.SDK_INT>=33)Prefs.put(j,"rationale",a.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS));
        // Full per-user flags are unavailable; policy revocation IS publicly readable above.
        Prefs.put(j,"runtimeFlagsAvailable",false);
        try{
            AppOpsManager ops=(AppOpsManager)a.getSystemService(Context.APP_OPS_SERVICE);
            int mode=Build.VERSION.SDK_INT>=29?ops.unsafeCheckOpNoThrow("android:post_notification",android.os.Process.myUid(),pkg)
                :ops.checkOpNoThrow("android:post_notification",android.os.Process.myUid(),pkg);
            Prefs.put(j,"notificationAppOp",mode);
        }catch(Exception e){Prefs.put(j,"appOpReadError",e.getClass().getSimpleName());}
        Prefs.put(j,"lastSettingsRoute",prefs.raw().getString("notificationSettingsRoute",""));
        Prefs.put(j,"lastSettingsOpenedAt",prefs.raw().getLong("notificationSettingsOpenedAt",0));
        Prefs.put(j,"requestDurationMs",prefs.raw().getLong("notificationRequestDurationMs",-1));
        Prefs.put(j,"lastAlarmMode",prefs.raw().getString("lastAlarmMode",""));
        Prefs.put(j,"lastAudioStartedAt",prefs.raw().getLong("lastAudioStartedAt",0));
        try{Prefs.put(j,"installer",Build.VERSION.SDK_INT>=30?pm.getInstallSourceInfo(pkg).getInstallingPackageName():pm.getInstallerPackageName(pkg));}
        catch(Exception e){Prefs.put(j,"installerError",e.getClass().getSimpleName());}
        return j;
    }
}

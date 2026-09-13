package dev.hazel.livealarm;

import android.content.Context;
import android.content.pm.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Explicit local export. Never queries arbitrary apps, app data, or device identifiers. */
public final class NotificationReport {
    private NotificationReport() {}
    private static final String[] COMPONENTS={"com.lbe.security.miui","com.android.permissioncontroller",
        "com.google.android.permissioncontroller","com.miui.securitycenter","com.android.settings"};
    private static final long FILE_CAP=64L*1024*1024,TOTAL_CAP=160L*1024*1024;
    public static JSONArray componentVersions(Context c){
        JSONArray rows=new JSONArray();
        for(String pkg:COMPONENTS){
            JSONObject row=new JSONObject();Prefs.put(row,"package",pkg);
            try{
                PackageInfo info=c.getPackageManager().getPackageInfo(pkg,0);
                Prefs.put(row,"visible",true);Prefs.put(row,"versionName",info.versionName);Prefs.put(row,"versionCode",info.versionCode);
                Prefs.put(row,"system",isSystem(info.applicationInfo));
                if(info.applicationInfo!=null)Prefs.put(row,"uid",info.applicationInfo.uid);
                Prefs.put(row,"lastUpdateTime",info.lastUpdateTime);
            }catch(PackageManager.NameNotFoundException|RuntimeException e){
                Prefs.put(row,"visible",false);Prefs.put(row,"readError",e.getClass().getSimpleName());
            }
            rows.put(row);
        }
        return rows;
    }
    private static boolean isSystem(ApplicationInfo info){
        return info!=null&&(info.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;
    }
    /** The report says which build it came from, read from the installed package, not a constant. */
    private static String appVersion(Context c){
        try{String name=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;if(name!=null&&!name.isEmpty())return name;}
        catch(Exception ignored){}
        return "未知版本";
    }
    public static void write(Context c,OutputStream output,String diagnostic,boolean includeComponents)throws Exception{
        if(output==null)throw new IOException("保存位置不可写");
        JSONArray files=new JSONArray();long total=0;
        try(ZipOutputStream zip=new ZipOutputStream(output)){
            putText(zip,"notification-diagnostics.json",diagnostic);
            putText(zip,"README.txt","VR闹钟 "+appVersion(c)+" 通知排查包\n仅保存到你选择的位置，不会自动发送。\n"
                +"notification-diagnostics.json 包含本应用的通知状态、检查时间、系统版本和指定权限组件版本。\n"
                +"若选择包含权限组件，只复制可读取的系统权限组件 APK，不包含应用数据或个人文件。\n"
                +"export-manifest.json 记录导出结果和 SHA-256；不可见或不可读的组件会明确记录。\n"
                +"状态快照不能识别权限写入者，也不能单独证明系统开关已经成功写入。\n");
            if(includeComponents){
                for(String pkg:Arrays.copyOf(COMPONENTS,3)){
                    PackageInfo info;
                    try{info=c.getPackageManager().getPackageInfo(pkg,0);}
                    catch(PackageManager.NameNotFoundException|RuntimeException e){files.put(skipped(pkg,e.getClass().getSimpleName()));continue;}
                    ApplicationInfo app=info.applicationInfo;
                    if(!isSystem(app)){files.put(skipped(pkg,"not_a_system_component"));continue;}
                    List<String> paths=new ArrayList<>();paths.add(app.sourceDir);
                    if(app.splitSourceDirs!=null)paths.addAll(Arrays.asList(app.splitSourceDirs));
                    for(int i=0;i<paths.size();i++){
                        String source=paths.get(i),entry="components/"+pkg+"/"+(i==0?"base":"split-"+i)+".apk";
                        File file=source==null?null:new File(source);
                        if(file==null||!file.isFile()||!file.canRead()){files.put(skipped(entry,"not_readable"));continue;}
                        long size=file.length();
                        if(size<=0||size>FILE_CAP||size>TOTAL_CAP-total){files.put(skipped(entry,"size_limit"));continue;}
                        MessageDigest digest=MessageDigest.getInstance("SHA-256");long copied=0;
                        try(InputStream in=new FileInputStream(file)){
                            zip.putNextEntry(new ZipEntry(entry));byte[] buffer=new byte[32768];int n;
                            while((n=in.read(buffer))!=-1){
                                if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("导出已中断");
                                copied+=n;if(copied>size)throw new IOException("系统组件在导出中发生变化，请重试");
                                digest.update(buffer,0,n);zip.write(buffer,0,n);
                            }
                            if(copied!=size)throw new IOException("系统组件读取不完整，请重试");
                            zip.closeEntry();
                        }
                        total+=copied;JSONObject row=new JSONObject();Prefs.put(row,"entry",entry);Prefs.put(row,"status","included");
                        Prefs.put(row,"bytes",copied);StringBuilder hash=new StringBuilder();
                        for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
                        Prefs.put(row,"sha256",hash.toString());files.put(row);
                    }
                }
            }
            JSONObject manifest=new JSONObject();Prefs.put(manifest,"includeComponents",includeComponents);
            Prefs.put(manifest,"files",files);Prefs.put(manifest,"componentBytes",total);
            putText(zip,"export-manifest.json",manifest.toString(2));
        }
    }
    private static JSONObject skipped(String entry,String reason){JSONObject row=new JSONObject();Prefs.put(row,"entry",entry);Prefs.put(row,"status","skipped");Prefs.put(row,"reason",reason);return row;}
    private static void putText(ZipOutputStream zip,String name,String text)throws IOException{
        zip.putNextEntry(new ZipEntry(name));zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
}

import android.content.*;
import android.content.pm.*;
import dev.hazel.livealarm.NotificationReport;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** JVM export tests with small PackageManager doubles. They do not simulate a Xiaomi ROM. */
public class ReportTests {
    static int passed=0;
    static void check(boolean yes,String label){if(!yes)throw new AssertionError(label);passed++;}
    static class PM extends PackageManager {
        Map<String,PackageInfo> packages=new HashMap<>(); Set<String> queried=new HashSet<>();
        public PackageInfo getPackageInfo(String p,int f)throws NameNotFoundException{queried.add(p);if(!packages.containsKey(p))throw new NameNotFoundException();return packages.get(p);}
    }
    static class C extends Context { final PM pm=new PM();public PackageManager getPackageManager(){return pm;} }
    static PackageInfo info(Path path,boolean system){
        PackageInfo i=new PackageInfo();i.versionName="test";i.versionCode=192;i.applicationInfo=new ApplicationInfo();
        i.applicationInfo.sourceDir=path.toString();i.applicationInfo.flags=system?ApplicationInfo.FLAG_SYSTEM:0;return i;
    }
    static Map<String,byte[]> unzip(byte[] input)throws Exception{
        Map<String,byte[]> out=new HashMap<>();try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(input))){ZipEntry e;while((e=z.getNextEntry())!=null){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] block=new byte[4096];int n;while((n=z.read(block))!=-1)b.write(block,0,n);out.put(e.getName(),b.toByteArray());}}return out;
    }
    static Map<String,byte[]> export(C c,boolean components)throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();NotificationReport.write(c,b,"{\"policyStatus\":\"revoked\"}",components);return unzip(b.toByteArray());
    }
    public static void main(String[] a)throws Exception{
        Path dir=Files.createTempDirectory("manqu-report-tests-");
        try{
            C c=new C();Path base=dir.resolve("base.apk"),split=dir.resolve("split.apk"),privateFile=dir.resolve("private-data");
            Files.write(base,"system-apk-test".getBytes("UTF-8"));Files.write(split,"split-test".getBytes("UTF-8"));Files.write(privateFile,"must-not-export".getBytes("UTF-8"));
            PackageInfo system=info(base,true);system.applicationInfo.splitSourceDirs=new String[]{split.toString()};
            c.pm.packages.put("com.lbe.security.miui",system);
            c.pm.packages.put("com.google.android.permissioncontroller",info(privateFile,false));
            c.pm.packages.put("com.miui.securitycenter",info(privateFile,true));
            Map<String,byte[]> basic=export(c,false);
            check(basic.size()==3,"diagnostics-only has no APK");check(c.pm.queried.isEmpty(),"diagnostics-only does not read packages");
            Map<String,byte[]> full=export(c,true);String entry="components/com.lbe.security.miui/base.apk";
            check(Arrays.equals(full.get(entry),Files.readAllBytes(base)),"base APK byte equality");
            check(Arrays.equals(full.get("components/com.lbe.security.miui/split-1.apk"),Files.readAllBytes(split)),"split APK byte equality");
            check(full.size()==5,"only allowed system APKs included");
            check(!c.pm.queried.contains("com.miui.securitycenter"),"version-only component not copied");
            JSONObject m=new JSONObject(new String(full.get("export-manifest.json"),"UTF-8"));JSONArray rows=m.getJSONArray("files");
            check(m.getLong("componentBytes")==Files.size(base)+Files.size(split),"byte total");
            StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(base)))hash.append(String.format("%02x",b&255));
            check(rows.getJSONObject(0).getString("sha256").equals(hash.toString()),"SHA-256 matches bytes");
            check(rows.getJSONObject(2).getString("status").equals("skipped"),"missing controller recorded");
            check(rows.getJSONObject(3).getString("reason").equals("not_a_system_component"),"non-system package skipped");
            Path huge=dir.resolve("huge.apk");try(RandomAccessFile f=new RandomAccessFile(huge.toFile(),"rw")){f.setLength(64L*1024*1024+1);}
            c.pm.packages.put("com.lbe.security.miui",info(huge,true));full=export(c,true);
            check(full.size()==3,"oversized component not exported");
            m=new JSONObject(new String(full.get("export-manifest.json"),"UTF-8"));check(m.getJSONArray("files").getJSONObject(0).getString("reason").equals("size_limit"),"size skip explicit");
            c.pm.packages.put("com.lbe.security.miui",info(dir.resolve("missing.apk"),true));full=export(c,true);
            m=new JSONObject(new String(full.get("export-manifest.json"),"UTF-8"));check(m.getJSONArray("files").getJSONObject(0).getString("reason").equals("not_readable"),"unreadable source explicit");
            c.pm.packages.put("com.lbe.security.miui",system);Thread.currentThread().interrupt();boolean aborted=false;
            try{export(c,true);}catch(InterruptedIOException expected){aborted=true;}finally{Thread.interrupted();}
            check(aborted,"cancelled export does not report success");
            boolean rejected=false;try{NotificationReport.write(c,null,"{}",false);}catch(IOException e){rejected=true;}check(rejected,"null destination fails");
            check(NotificationReport.componentVersions(c).length()==5,"only five named component metadata records");
            System.out.println("PASS: "+passed+" report export assertions (JVM, Android package queries mocked)");
        }finally{try(java.util.stream.Stream<Path> paths=Files.walk(dir)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(IOException e){throw new UncheckedIOException(e);}});}}
    }
}

package dev.hazel.livealarm;

import android.content.Context;
import java.io.*;
import java.util.List;

/**
 * Cached anchor avatars. Art is downloaded once per anchor and served to the WebView from
 * private storage, so the offline interface keeps its no-external-request property.
 */
public final class AnchorArt {
    private static final String DIR="anchor-art";
    private AnchorArt(){}

    private static File dir(Context c){return new File(c.getFilesDir(),DIR);}
    private static File file(Context c,String id){return new File(dir(c),id+".img");}

    public static boolean save(Context c,String id,byte[] bytes){
        if(!Anchors.validId(id)||bytes==null||bytes.length==0)return false;
        File folder=dir(c);
        if(!folder.isDirectory()&&!folder.mkdirs())return false;
        File temp=new File(folder,id+".tmp"),target=file(c,id);
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);}
        catch(IOException e){temp.delete();return false;}
        if(target.exists()&&!target.delete()){temp.delete();return false;}
        return temp.renameTo(target);
    }

    public static boolean clear(Context c,String id){
        if(!Anchors.validId(id))return false;
        return file(c,id).delete();
    }

    /** A user-uploaded weekly-schedule picture, kept beside the avatar under the same rules. */
    private static File scheduleFile(Context c,String id){return new File(dir(c),id+".schedule.img");}
    public static boolean saveScheduleImage(Context c,String id,byte[] bytes){
        if(!Anchors.validId(id)||bytes==null||bytes.length==0)return false;
        File folder=dir(c);
        if(!folder.isDirectory()&&!folder.mkdirs())return false;
        File temp=new File(folder,id+".schedule.tmp"),target=scheduleFile(c,id);
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);}
        catch(IOException e){temp.delete();return false;}
        if(target.exists()&&!target.delete()){temp.delete();return false;}
        return temp.renameTo(target);
    }
    public static File scheduleImage(Context c,String id){
        if(!Anchors.validId(id))return null;
        File f=scheduleFile(c,id);
        return f.isFile()?f:null;
    }
    public static boolean clearScheduleImage(Context c,String id){
        if(!Anchors.validId(id))return false;
        return scheduleFile(c,id).delete();
    }

    /**
     * Cache-busting revision for the stable /schedule/&lt;id&gt;.img address. Replacing the picture
     * keeps the same file name, so the revision has to come from the file itself: for an unchanged
     * address the WebView serves the first image it decoded, which is exactly how the background
     * bug behaved before it got a revision (see the /background/current note in MainActivity).
     */
    public static String scheduleImageRevision(Context c,String id){
        File f=scheduleImage(c,id);
        return f==null?"0":Integer.toHexString((f.length()+"@"+f.lastModified()).hashCode());
    }

    public static File existing(Context c,String id){
        if(!Anchors.validId(id))return null;
        File f=file(c,id);
        return f.isFile()?f:null;
    }

    /** Faces are JPEG in practice, but sniff so a mislabelled file still renders. */
    public static String mime(File f){
        byte[] head=new byte[12];
        int read;
        try(InputStream in=new FileInputStream(f)){read=in.read(head);}
        catch(IOException e){return "application/octet-stream";}
        if(read<4)return "application/octet-stream";
        if((head[0]&0xff)==0x89&&head[1]=='P'&&head[2]=='N'&&head[3]=='G')return "image/png";
        if((head[0]&0xff)==0xff&&(head[1]&0xff)==0xd8)return "image/jpeg";
        if(head[0]=='G'&&head[1]=='I'&&head[2]=='F')return "image/gif";
        if(read>=12&&head[0]=='R'&&head[1]=='I'&&head[2]=='F'&&head[3]=='F'&&head[8]=='W'&&head[9]=='E')return "image/webp";
        return "application/octet-stream";
    }

    /** Drop art for anchors that no longer exist, plus any interrupted write.
     *  Avatars are <id>.img and schedule pictures <id>.schedule.img; the suffix matters here,
     *  because stripping at the first dot would mistake "hazel.schedule.img" for a stray. */
    public static void prune(Context c,List<Anchors.Anchor> keep){
        File[] files=dir(c).listFiles();
        if(files==null)return;
        for(File f:files){
            String name=f.getName();
            if(name.endsWith(".tmp")){f.delete();continue;}
            String id;
            if(name.endsWith(".schedule.img"))id=name.substring(0,name.length()-".schedule.img".length());
            else if(name.endsWith(".img"))id=name.substring(0,name.length()-".img".length());
            else{f.delete();continue;}
            if(Anchors.find(keep,id)==null)f.delete();
        }
    }
}

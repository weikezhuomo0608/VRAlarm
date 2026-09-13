package dev.hazel.livealarm;

import android.content.Context;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import java.io.*;
import java.util.UUID;

/** Bounded, private image copy for the user-picked app background. No broad media
 *  permission and no persistent URI dependency. Ported from upstream 1.0.6 with the
 *  androidx EXIF dependency replaced by a minimal in-file orientation reader, and the
 *  stored path kept relative so backups never leak device directories. */
public final class BackgroundStore {
    private static final String PREFIX="background-",SUFFIX=".jpg";
    public static void importImage(Context c,Prefs prefs,Uri uri)throws IOException{
        File temp=File.createTempFile("background-",".input",c.getCacheDir());
        File dest=new File(c.getFilesDir(),PREFIX+UUID.randomUUID()+SUFFIX);
        Bitmap bitmap=null;
        try{
            try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){
                if(in==null)throw new IOException("图片不可读取");byte[] buffer=new byte[16384];int n;long total=0;
                while((n=in.read(buffer))!=-1){total+=n;if(total>20L*1024*1024)throw new IOException("请选择小于 20 MB 的图片");out.write(buffer,0,n);}
                if(total==0)throw new IOException("图片文件为空");
            }
            if(Build.VERSION.SDK_INT>=28){
                // ImageDecoder applies the file's own EXIF rotation while resizing.
                final int orientation=1;
                bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(temp),(decoder,info,source)->{
                    int w=info.getSize().getWidth(),h=info.getSize().getHeight();float scale=Math.min(1f,1920f/Math.max(w,h));
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);decoder.setTargetSize(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)));
                });
            }else{
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(temp.getPath(),options);
                if(options.outWidth<=0||options.outHeight<=0)throw new IOException("无法识别图片");
                options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>1920)options.inSampleSize*=2;
                options.inJustDecodeBounds=false;bitmap=BitmapFactory.decodeFile(temp.getPath(),options);
                if(bitmap!=null){
                    int orientation=exifOrientation(temp);
                    Matrix matrix=new Matrix();
                    if(orientation>=5)matrix.postRotate(90);
                    if(orientation==3||orientation==4)matrix.postRotate(180);
                    if(orientation==2||orientation==4)matrix.postScale(-1,1);
                    if(!matrix.isIdentity()){Bitmap rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}
                }
            }
            if(bitmap==null)throw new IOException("此图片无法解码，请选择 JPG、PNG 或 WebP");
            try(OutputStream out=new FileOutputStream(dest)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,out))throw new IOException("图片保存失败");}
            String name="自选背景";
            try(Cursor cursor=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}catch(RuntimeException ignored){}
            if(name==null)name="自选背景";
            String old=prefs.raw().getString("backgroundPath","");
            if(!prefs.raw().edit().putString("backgroundPath",dest.getName()).putString("backgroundName",name.substring(0,Math.min(100,name.length()))).commit())throw new IOException("设置保存失败");
            deleteOwned(c,old);
        }catch(OutOfMemoryError e){dest.delete();throw new IOException("图片过大，请选择较小的图片",e);}
        catch(IOException|RuntimeException e){dest.delete();throw e;}
        finally{temp.delete();if(bitmap!=null)bitmap.recycle();}
    }
    public static void remove(Context c,Prefs p){String old=p.raw().getString("backgroundPath","");p.raw().edit().remove("backgroundPath").remove("backgroundName").commit();deleteOwned(c,old);}
    /** The stored value is a bare file name inside filesDir; anything else is rejected. */
    public static File resolve(Context c,String stored){
        if(stored==null||!stored.startsWith(PREFIX)||!stored.endsWith(SUFFIX)||stored.contains("/"))return null;
        File f=new File(c.getFilesDir(),stored);
        return f.isFile()?f:null;
    }
    private static void deleteOwned(Context c,String name){
        File f=resolve(c,name);if(f!=null)f.delete();
    }
    /** Reads the EXIF orientation tag straight from the JPEG APP1 block; 1 when absent
     *  or unreadable. Enough for rotation, far smaller than pulling in androidx. */
    private static int exifOrientation(File file){
        try(RandomAccessFile raf=new RandomAccessFile(file,"r")){
            byte[] head=new byte[2];raf.readFully(head);
            if(head[0]!=(byte)0xFF||head[1]!=(byte)0xD8)return 1;
            while(true){
                byte[] marker=new byte[2];if(raf.read(marker)!=2||(marker[0]&0xFF)!=0xFF)return 1;
                int code=marker[1]&0xFF;if(code==0xD8||code==0x01||(code>=0xD0&&code<=0xD7))continue;
                byte[] lenBytes=new byte[2];if(raf.read(lenBytes)!=2)return 1;
                int len=((lenBytes[0]&0xFF)<<8)|(lenBytes[1]&0xFF);if(len<2)return 1;
                byte[] seg=new byte[len-2];if(raf.read(seg)!=seg.length)return 1;
                if(code!=0xE1||seg.length<12||seg[0]!=0x45||seg[1]!=0x78||seg[2]!=0x69||seg[3]!=0x66)continue;
                int tiff=6;boolean big=seg[tiff]=='M';
                int ifd=unsigned(seg,big,tiff+4);
                if(ifd+2>seg.length)return 1;
                int count=unsigned(seg,big,ifd);
                for(int k=0;k<count;k++){
                    int e=ifd+2+k*12;if(e+12>seg.length)return 1;
                    int tag=unsigned(seg,big,e);
                    if(tag==0x0112){int v=unsigned(seg,big,e+8);return v>=1&&v<=8?v:1;}
                }
                return 1;
            }
        }catch(IOException ignored){return 1;}
    }
    private static int unsigned(byte[] b,boolean big,int off){
        int v=big?((b[off]&0xFF)<<24)|((b[off+1]&0xFF)<<16)|((b[off+2]&0xFF)<<8)|(b[off+3]&0xFF)
                 :((b[off+3]&0xFF)<<24)|((b[off+2]&0xFF)<<16)|((b[off+1]&0xFF)<<8)|(b[off]&0xFF);
        return v;
    }
}

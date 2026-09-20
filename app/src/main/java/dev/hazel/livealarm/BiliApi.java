package dev.hazel.livealarm;

import org.json.*;
import java.net.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public final class BiliApi {
    private final long uid,room;
    public BiliApi(long uid,long room){this.uid=uid;this.room=room;}

    public static final class Snapshot {
        public int status;public long start,checkedAt;public String title,source;public String cover="";public long latency;
        public JSONObject json(){JSONObject j=new JSONObject();Prefs.put(j,"status",status);Prefs.put(j,"start",start);Prefs.put(j,"checkedAt",checkedAt);Prefs.put(j,"title",title);Prefs.put(j,"cover",cover);Prefs.put(j,"source",source);Prefs.put(j,"latency",latency);return j;}
    }
    /** What the add-anchor flow needs: the canonical room, its owner and their picture. */
    public static final class Profile {
        public long uid,room;public String name="",face="";
    }
    public static final class ApiException extends IOException {public final boolean rateLimited;ApiException(String message,boolean rate){super(message);rateLimited=rate;}}
    public Snapshot fetch()throws IOException{
        long began=System.nanoTime();JSONObject d;String source="room/get_info";
        try {d=request("https://api.live.bilibili.com/room/v1/Room/get_info?room_id="+room);}
        catch(ApiException e){if(e.rateLimited)throw e;source="room_init";d=request("https://api.live.bilibili.com/room/v1/Room/room_init?id="+room);}
        catch(IOException e){source="room_init";d=request("https://api.live.bilibili.com/room/v1/Room/room_init?id="+room);}
        Snapshot s=parse(d,source,uid,room);s.checkedAt=System.currentTimeMillis();s.latency=(System.nanoTime()-began)/1000000;return s;
    }
    static Snapshot parse(JSONObject d,String source,long uid,long room)throws ApiException{
        // The room is the identity the user typed, and both endpoints echo it back. The owner
        // uid is NOT a second identity: the two live endpoints can legitimately disagree about
        // it, and treating that as a mismatch disabled reminders for that anchor for good.
        if(d.optLong("room_id",-1)!=room)throw new ApiException("直播间身份校验未通过，已暂停本次提醒",false);
        if(!d.has("live_status"))throw new ApiException("B 站返回缺少直播状态",false);
        int status=d.optInt("live_status",-1);if(status<0||status>2)throw new ApiException("B 站返回未知直播状态",false);
        Snapshot s=new Snapshot();s.status=status;s.title=d.optString("title","");s.cover=d.optString("user_cover","");s.source=source;
        Object value=d.opt("live_time");
        try{
            if(value instanceof Number){long t=((Number)value).longValue();s.start=t>100000000000L?t:t*1000L;}
            else if(value instanceof String && !((String)value).startsWith("0000")){
                String text=(String)value;
                if(text.matches("[0-9]+")){long t=Long.parseLong(text);s.start=t>100000000000L?t:t*1000L;}
                else s.start=LocalDateTime.parse(text,DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
            }
        }catch(Exception ignored){s.start=0;}
        if(s.start<1000000000000L)s.start=0;
        return s;
    }
    /**
     * Turn a typed room number into a stored anchor. The room is canonicalised first, so a
     * short id still passes the identity check in parse(). Name and picture are best effort.
     */
    /**
     * The live room of one user id, so a pasted space link can be added like a room link.
     * Two endpoints are asked, because neither one alone covers every account: the live_user
     * endpoint publishes room_id for anchors but is refused for some uids, while the space
     * endpoint still answers for them with its own copy of the room id.
     */
    public static long roomOfUid(long uid)throws IOException{
        IOException failure=null;
        try{
            JSONObject d=request("https://api.live.bilibili.com/live_user/v1/Master/info?uid="+uid,"https://space.bilibili.com/"+uid);
            // The live room is published at data.room_id; the nested data.info object only holds
            // the user profile and has no room_id. Reading info.room_id made every uid look like
            // an account without a live room.
            long room=d.optLong("room_id",0);
            if(room>0)return room;
            failure=new ApiException("这个 UID 还没有开通直播间，请改用直播间链接",false);
        }catch(ApiException e){if(e.rateLimited)throw e;failure=e;}
        catch(IOException e){failure=e;}
        try{
            JSONObject card=request("https://api.bilibili.com/x/web-interface/card?mid="+uid,"https://space.bilibili.com/"+uid);
            long room=card.optJSONObject("live")==null?0:card.optJSONObject("live").optLong("roomid",0);
            if(room>0)return room;
            failure=new ApiException("这个 UID 还没有开通直播间，请改用直播间链接",false);
        }catch(ApiException e){if(e.rateLimited)throw e;failure=e;}
        catch(IOException e){failure=e;}
        String message=failure==null?null:failure.getMessage();
        if(message!=null&&(message.startsWith("B 站接口")||message.startsWith("B 站暂时")))throw new ApiException("没有找到这个账号的直播间，请改用直播间号码或直播间链接",false);
        throw failure==null?new ApiException("没有找到这个账号的直播间，请改用直播间号码或直播间链接",false):failure;
    }
    public static Profile resolve(long room)throws IOException{
        String referer="https://live.bilibili.com/"+room;
        Profile p=new Profile();
        JSONObject init;
        try{init=request("https://api.live.bilibili.com/room/v1/Room/room_init?id="+room,referer);}
        catch(ApiException e){
            if(e.rateLimited)throw e;
            // A wrong room number must not be reported as "wait and retry": nothing will change.
            throw new ApiException("没有找到这个直播间，请检查房间号",false);
        }
        p.uid=init.optLong("uid",0);
        p.room=init.optLong("room_id",0);
        if(p.room<=0)p.room=room;
        // room_init publishes the owner as uid; the live-status endpoint reports the same account
        // as uid under some responses and as mid under others, so both spellings are accepted.
        if(p.uid<=0)p.uid=init.optLong("mid",0);
        if(p.uid<=0)throw new ApiException("没有找到这个直播间，请检查房间号",false);
        try{
            JSONObject d=request("https://api.live.bilibili.com/live_user/v1/UserInfo/get_anchor_in_room?roomid="+p.room,"https://live.bilibili.com/"+p.room);
            JSONObject info=d.optJSONObject("info");
            if(info!=null){
                // Name and picture only: the uid stays the room owner's, which is the value the
                // live-status endpoint reports for this room.
                p.name=Anchors.fitName(info.optString("uname",""));
                p.face=info.optString("face","");
            }
        }catch(IOException ignored){
            // The name is optional: the user can still type one, and the alarm works without it.
        }
        if(p.name.isEmpty()){
            p.name=Anchors.fitName(init.optString("uname",""));
            if(p.name.isEmpty())p.name="主播 "+p.room;
        }
        return p;
    }
    /** Bounded fetch of the avatar, so one oversized response cannot fill the device. */
    public static byte[] download(String address,int cap)throws IOException{
        URL target=new URL(address);
        if(!target.getProtocol().equals("https"))throw new IOException("头像地址不是 HTTPS");
        HttpURLConnection c=(HttpURLConnection)target.openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36");
        c.setUseCaches(false);
        try{
            int code=c.getResponseCode();
            // Redirects are followed above, so the final URL is what actually served the bytes.
            if(!c.getURL().getProtocol().equals("https"))throw new IOException("头像跳转后不是 HTTPS");
            if(code!=200)throw new IOException("头像下载失败（HTTP "+code+"）");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
            try(InputStream in=c.getInputStream()){while((n=in.read(buffer))!=-1){bytes.write(buffer,0,n);if(bytes.size()>cap)throw new IOException("头像文件过大");}}
            return bytes.toByteArray();
        }finally{c.disconnect();}
    }
    /**
     * The avatar URL of an already-canonical room: one request, for the bulk avatar refresh.
     * The room-info endpoint carries the same icon and, unlike the anchor-in-room endpoint,
     * answers for every room, so a refresh stops leaving anchors without a picture.
     */
    public static String anchorFace(long room)throws IOException{
        String referer="https://live.bilibili.com/"+room;
        try{
            JSONObject d=request("https://api.live.bilibili.com/live_user/v1/UserInfo/get_anchor_in_room?roomid="+room,referer);
            JSONObject info=d.optJSONObject("info");
            String face=info==null?"":info.optString("face","");
            if(!face.isEmpty())return face;
        }catch(ApiException e){
            if(e.rateLimited)throw e;
        }catch(IOException ignored){}
        JSONObject d=request("https://api.live.bilibili.com/room/v1/Room/get_info?room_id="+room,referer);
        return d.optString("user_cover","");
    }

    private JSONObject request(String address)throws IOException{return request(address,"https://live.bilibili.com/"+room);}
    private static JSONObject request(String address,String referer)throws IOException{
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36");
        c.setRequestProperty("Referer",referer);
        c.setRequestProperty("Accept","application/json");c.setUseCaches(false);
        try{
            int status=c.getResponseCode();if(status==412||status==429)throw new ApiException("B 站暂时限制访问，正在延长重试间隔",true);
            if(status!=200)throw new ApiException("B 站接口暂不可用（HTTP "+status+"）",false);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            try(InputStream in=c.getInputStream()){while((n=in.read(buffer))!=-1){bytes.write(buffer,0,n);if(bytes.size()>1048576)throw new IOException("响应过大");}}
            JSONObject root=new JSONObject(bytes.toString("UTF-8"));int code=root.optInt("code",-999);
            if(code!=0)throw new ApiException("B 站接口返回 "+code+"，等待重试",code==-352||code==-412||code==-509);
            JSONObject d=root.optJSONObject("data");if(d==null)throw new ApiException("B 站接口数据为空",false);return d;
        }catch(JSONException e){throw new ApiException("B 站接口格式变化，暂无法确认开播",false);}finally{c.disconnect();}
    }
}

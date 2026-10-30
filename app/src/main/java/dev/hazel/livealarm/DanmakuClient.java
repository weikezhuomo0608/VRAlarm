package dev.hazel.livealarm;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.*;

/**
 * One room's danmaku connection, anonymous. It moves bytes and nothing else: resolve the real room
 * id, ask for a server and a token, connect, authorise, then hand every command to the sink.
 *
 * A long connection is the only way to see a 舰长红包 in time — the room broadcasts it and no
 * polling endpoint reports it — and it is also the cheapest: one idle socket with a heartbeat
 * every thirty seconds, against a request every two minutes for a poll.
 */
final class DanmakuClient {
    interface Sink {
        void onCommand(JSONObject command);
        /** Progress and failures, for the on-device probe. The watch itself ignores it. */
        default void onState(String text){}
    }

    private static final int READ_TIMEOUT_MILLIS=40000;
    private static final int HEARTBEAT_SECONDS=30;
    /** Three missed heartbeats: the socket is there but nothing is coming back, so reconnect. */
    private static final long SILENCE_MILLIS=150000L;
    private static final char[] B64="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private final long room;
    private final Sink sink;
    private volatile boolean stopped=false;
    private volatile Socket socket;

    DanmakuClient(long room,Sink sink){this.room=room;this.sink=sink;}

    void stop(){
        stopped=true;
        close();
    }

    private void close(){
        Socket open=socket;
        socket=null;
        if(open!=null)try{open.close();}catch(IOException ignored){}
    }

    /** Blocks until {@link #stop()}. A dropped connection is retried with a growing pause: a live
     *  room is worth reconnecting to, and a dead one must not become a request flood. */
    void run(){
        long pause=5000;
        while(!stopped){
            try{
                listen(Long.MAX_VALUE);
                if(stopped)break;
                pause=5000;
            }catch(Exception e){
                if(stopped)break;
                sink.onState("弹幕连接中断："+readable(e));
                sleep(pause);
                pause=Math.min(60000,pause*2);
            }
        }
    }

    /**
     * The trial build's instrument: connect once, listen for a few seconds, and report exactly what
     * arrived. Whether this device can see a packet is then answered by the device itself.
     */
    static String probe(long room,int seconds){
        StringBuilder log=new StringBuilder();
        final ArrayList<String> names=new ArrayList<>();
        final ArrayList<String> pockets=new ArrayList<>();
        DanmakuClient client=new DanmakuClient(room,new Sink(){
            @Override public void onCommand(JSONObject command){
                String name=command.optString("cmd","");
                if(name.isEmpty())return;
                if(!names.contains(name))names.add(name);
                if(name.startsWith("POPULARITY_RED_POCKET"))pockets.add(command.toString());
            }
            @Override public void onState(String text){log.append(text).append('\n');}
        });
        try{client.listen(System.currentTimeMillis()+seconds*1000L);}
        catch(Exception e){log.append("试连失败：").append(readable(e)).append('\n');}
        StringBuilder out=new StringBuilder(log.toString());
        out.append("签名密钥：").append(BiliAuth.mixinSource())
            .append("；设备指纹：").append(BiliAuth.cookieOrEmpty().isEmpty()?"未取到":"已取到").append('\n');
        out.append("收到的命令（去重）：").append(names.isEmpty()?"一条都没有":join(names)).append('\n');
        if(pockets.isEmpty())out.append("这 ").append(seconds).append(" 秒里没有红包广播。\n")
            .append("注意：红包只在生成的那一刻广播一次，平台不重播，已经存在的红包也不会补发——所以这里没有，不等于房间里没有红包。\n")
            .append("要抓到它，得让这个房间在直播期间一直连着（打开该主播的「舰长红包」开关），红包生成的瞬间才会推过来。");
        else for(String pocket:pockets)out.append("红包广播原文：\n").append(pocket).append('\n');
        return out.toString();
    }

    /**
     * One connection, kept until {@code until} (a wall clock in milliseconds) or until stopped.
     * The token is asked for on every attempt, because it does not outlive the connection.
     */
    private void listen(long until)throws Exception{
        long real=BiliApi.realRoom(room);
        sink.onState("房间 "+room+" 的真实房间号是 "+real+"，正在取弹幕服务器地址");
        JSONObject info;
        try{info=BiliApi.danmuInfo(real);}
        catch(BiliApi.ApiException e){
            // One retry with freshly fetched credentials: the signing keys rotate daily and a stale
            // fingerprint is the most likely thing a refusal is about.
            sink.onState("第一次被拒（"+readable(e)+"），换成新凭据再试一次");
            BiliAuth.forget();
            info=BiliApi.danmuInfo(real);
        }
        String token=info.optString("token","");
        if(token.isEmpty())throw new IOException("没有拿到弹幕令牌");
        // The servers and their ports live inside host_list, one entry each — reading a top-level
        // wss_port silently fell back to 443, which is not the port the danmaku service publishes.
        JSONArray list=info.optJSONArray("host_list");
        ArrayList<String> hosts=new ArrayList<>();
        ArrayList<Integer> ports=new ArrayList<>();
        if(list!=null)for(int i=0;i<list.length();i++){
            JSONObject entry=list.optJSONObject(i);
            if(entry==null)continue;
            String name=entry.optString("host","");
            if(name.isEmpty())continue;
            hosts.add(name);
            ports.add(entry.optInt("wss_port",2245));
        }
        String flat=info.optString("host","");
        if(flat!=null&&!flat.isEmpty()){hosts.add(flat);ports.add(info.optInt("wss_port",2245));}
        if(hosts.isEmpty())throw new IOException("没有拿到弹幕服务器地址");
        IOException last=null;
        for(int i=0;i<hosts.size();i++){
            if(stopped)return;
            try{
                connect(hosts.get(i),ports.get(i),real,token,until);
                return;
            }catch(IOException e){
                last=e;
                if(stopped)return;
                // Another server from the same answer is the cheapest thing to try next: Bilibili
                // hands out several and one of them may be the one that accepts this connection.
                sink.onState(hosts.get(i)+" 连不上（"+readable(e)+"），换下一个弹幕服务器");
            }
        }
        throw last==null?new IOException("没有可用的弹幕服务器"):last;
    }

    private void connect(String host,int port,long real,String token,long until)throws IOException{
        sink.onState("正在连接弹幕服务器 "+host+":"+port);
        Socket fresh=SSLSocketFactory.getDefault().createSocket();
        fresh.connect(new InetSocketAddress(host,port),10000);
        fresh.setSoTimeout(READ_TIMEOUT_MILLIS);
        ((SSLSocket)fresh).startHandshake();
        socket=fresh;
        try{
            handshake(fresh,host);
            sink.onState("已连上弹幕服务器，正在鉴权");
            send(fresh,DanmakuFrames.OP_AUTH,auth(real,token));
            read(fresh,until);
        }finally{close();}
    }

    /** The upgrade handshake, by hand: one request, one 101 answer, no more. */
    private static void handshake(Socket socket,String host)throws IOException{
        byte[] nonce=new byte[16];
        new Random().nextBytes(nonce);
        String request="GET /sub HTTP/1.1\r\n"
            +"Host: "+host+"\r\n"
            +"Upgrade: websocket\r\n"
            +"Connection: Upgrade\r\n"
            +"Sec-WebSocket-Key: "+base64(nonce)+"\r\n"
            +"Sec-WebSocket-Version: 13\r\n"
            +"Origin: https://live.bilibili.com\r\n"
            +"User-Agent: "+userAgent()+"\r\n\r\n";
        OutputStream out=socket.getOutputStream();
        out.write(request.getBytes("UTF-8"));
        out.flush();
        InputStream in=socket.getInputStream();
        StringBuilder head=new StringBuilder();
        int at=0;
        while(at<8192){
            int c=in.read();
            if(c<0)throw new EOFException("弹幕服务器没有响应");
            head.append((char)c);
            at++;
            int length=head.length();
            if(length>=4&&head.charAt(length-4)=='\r'&&head.charAt(length-3)=='\n'&&head.charAt(length-2)=='\r'&&head.charAt(length-1)=='\n')break;
        }
        String text=head.toString();
        int cut=text.indexOf('\r');
        String status=cut<0?text:text.substring(0,cut);
        if(!status.contains("101"))throw new IOException("弹幕服务器拒绝了连接（"+status.trim()+"）");
    }

    private static byte[] auth(long room,String token)throws IOException{
        JSONObject body=new JSONObject();
        try{
            body.put("uid",0);
            body.put("roomid",room);
            body.put("protover",DanmakuFrames.VER_ZLIB);
            body.put("platform","web");
            body.put("type",2);
            body.put("key",token);
        }catch(org.json.JSONException e){throw new IOException("弹幕鉴权参数有误");}
        return body.toString().getBytes("UTF-8");
    }

    /** The read loop: pongs, the auth answer, then the packets, with a heartbeat of our own. */
    private void read(Socket socket,long until)throws IOException{
        InputStream in=socket.getInputStream();
        long silence=System.currentTimeMillis(),beat=silence,now=silence;
        while(!stopped&&now<until){
            DanmakuFrames.WsFrame frame=DanmakuFrames.readWsFrame(in);
            if(frame!=null){
                silence=System.currentTimeMillis();
                if(frame.opcode==DanmakuFrames.WS_PING)send(socket,DanmakuFrames.wsFrame(DanmakuFrames.WS_PONG,frame.payload));
                else if(frame.opcode==DanmakuFrames.WS_CLOSE)throw new IOException("弹幕服务器关闭了连接");
                else if(frame.opcode==DanmakuFrames.WS_BINARY){
                    for(DanmakuFrames.Frame packet:DanmakuFrames.split(frame.payload)){
                        if(packet.op==DanmakuFrames.OP_AUTH_REPLY){
                            JSONObject reply=json(packet.body);
                            int code=reply==null?0:reply.optInt("code",0);
                            if(code!=0)throw new IOException("弹幕鉴权被拒（"+code+"）");
                            sink.onState("鉴权通过，正在听这个房间");
                            continue;
                        }
                        for(byte[] body:DanmakuFrames.commands(packet)){
                            JSONObject command=json(body);
                            if(command!=null)sink.onCommand(command);
                        }
                    }
                }
            }
            now=System.currentTimeMillis();
            if(now-beat>=HEARTBEAT_SECONDS*1000L){
                beat=now;
                send(socket,DanmakuFrames.packet(DanmakuFrames.OP_HEARTBEAT,new byte[0]));
            }
            if(now-silence>SILENCE_MILLIS)throw new IOException("弹幕服务器一直没有回音");
        }
    }

    /**
     * Send one WebSocket frame. Everything this app sends is a binary frame: the danmaku operation
     * is written in the packet header by {@link DanmakuFrames#packet}, never as the frame opcode.
     */
    private static void send(Socket socket,byte[] frame)throws IOException{
        OutputStream out=socket.getOutputStream();
        out.write(frame);
        out.flush();
    }

    private static void send(Socket socket,int danmakuOp,byte[] body)throws IOException{
        send(socket,DanmakuFrames.wsFrame(DanmakuFrames.WS_BINARY,DanmakuFrames.packet(danmakuOp,body)));
    }

    private static JSONObject json(byte[] body){
        try{return new JSONObject(new String(body,"UTF-8"));}catch(Exception e){return null;}
    }

    private static String base64(byte[] data){
        StringBuilder out=new StringBuilder();
        for(int i=0;i<data.length;i+=3){
            int b0=data[i]&0xff,b1=i+1<data.length?data[i+1]&0xff:0,b2=i+2<data.length?data[i+2]&0xff:0;
            out.append(B64[b0>>2]).append(B64[((b0&3)<<4)|(b1>>4)]);
            out.append(i+1<data.length?B64[((b1&15)<<2)|(b2>>6)]:'=');
            out.append(i+2<data.length?B64[b2&63]:'=');
        }
        return out.toString();
    }

    private static String join(List<String> values){
        StringBuilder out=new StringBuilder();
        for(String value:values){
            if(out.length()>0)out.append('、');
            out.append(value);
        }
        return out.toString();
    }

    private static String readable(Exception e){
        String message=e.getMessage();
        return message==null||message.isEmpty()?e.getClass().getSimpleName():message;
    }

    private static void sleep(long millis){
        try{Thread.sleep(millis);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }

    private static String userAgent(){
        return "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36";
    }
}

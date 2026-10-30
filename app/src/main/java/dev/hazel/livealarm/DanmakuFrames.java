package dev.hazel.livealarm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * The binary framing of a danmaku connection. Every packet begins with a 16-byte header (total
 * length, header length, protocol version, operation, sequence). Version 2 carries a
 * zlib-compressed body that holds the same packets again — that is the version this app asks for,
 * because the JDK can inflate it. Version 3 would be brotli, which this app cannot read and
 * therefore skips instead of crashing on.
 *
 * Pure Java (no JSON, no sockets), so the framing and the inflate path run in the headless tests.
 */
public final class DanmakuFrames {
    public static final int OP_HEARTBEAT=2, OP_HEARTBEAT_REPLY=3, OP_COMMAND=5, OP_AUTH=7, OP_AUTH_REPLY=8;
    public static final int VER_JSON=1, VER_ZLIB=2, VER_BROTLI=3;
    public static final int HEADER=16;
    /**
     * The one WebSocket opcode this app sends data with. The danmaku operation (auth, heartbeat,
     * command) lives in the packet header instead, and passing it here as the WebSocket opcode is
     * what once made the server read an auth as a close frame and hang up: opcode 7 is CLOSE, not
     * "auth". WebSocket-level pings are answered with {@link #WS_PONG}, also at this level.
     */
    public static final int WS_BINARY=2, WS_PONG=10, WS_PING=9, WS_CLOSE=8;

    /** One packet: its version and operation, and the body between the headers. */
    public static final class Frame {
        public final int ver,op;
        public final byte[] body;
        Frame(int ver,int op,byte[] body){this.ver=ver;this.op=op;this.body=body;}
    }
    /** One WebSocket frame as it came off the wire. */
    public static final class WsFrame {
        public final int opcode;
        public final byte[] payload;
        WsFrame(int opcode,byte[] payload){this.opcode=opcode;this.payload=payload;}
    }

    private DanmakuFrames(){}

    /** A packet to send: our own headers plus the body. */
    public static byte[] packet(int op,byte[] body){
        byte[] source=body==null?new byte[0]:body;
        byte[] out=new byte[HEADER+source.length];
        writeInt(out,0,out.length);writeShort(out,4,HEADER);writeShort(out,6,VER_JSON);
        writeInt(out,8,op);writeInt(out,12,1);
        System.arraycopy(source,0,out,HEADER,source.length);
        return out;
    }

    /** The packets inside one frame body. A trailing partial packet is dropped rather than guessed at. */
    public static List<Frame> split(byte[] data){
        ArrayList<Frame> out=new ArrayList<>();
        if(data==null)return out;
        int at=0;
        while(at+HEADER<=data.length){
            int total=readInt(data,at),header=readShort(data,at+4);
            if(total<HEADER||header<HEADER||at+total>data.length)break;
            out.add(new Frame(readShort(data,at+6),readInt(data,at+8),
                Arrays.copyOfRange(data,at+header,at+total)));
            at+=total;
        }
        return out;
    }

    /** zlib inflate of one version-2 body. */
    public static byte[] inflate(byte[] body)throws IOException{
        Inflater inflater=new Inflater();
        try{
            inflater.setInput(body);
            ByteArrayOutputStream out=new ByteArrayOutputStream(Math.max(256,body.length*4));
            byte[] buffer=new byte[8192];
            while(!inflater.finished()){
                int n=inflater.inflate(buffer);
                if(n>0){out.write(buffer,0,n);continue;}
                // No progress and nothing left to feed: the body is not a complete zlib stream.
                if(inflater.needsInput()||inflater.needsDictionary())break;
            }
            return out.toByteArray();
        }catch(DataFormatException e){throw new IOException("弹幕数据解压失败",e);}
        finally{inflater.end();}
    }

    /**
     * The JSON bodies carried by one packet. Version 2 is inflated and then split again, version 1
     * holds the JSON directly, and version 3 is skipped — an unreadable packet is never an error.
     */
    public static List<byte[]> commands(Frame frame){
        ArrayList<byte[]> out=new ArrayList<>();
        if(frame==null||frame.op!=OP_COMMAND)return out;
        if(frame.ver==VER_ZLIB){
            byte[] plain;
            try{plain=inflate(frame.body);}catch(IOException e){return out;}
            for(Frame inner:split(plain))out.addAll(commands(inner));
            return out;
        }
        if(frame.ver==VER_BROTLI)return out;
        if(frame.body.length>0)out.add(frame.body);
        return out;
    }

    /** One WebSocket frame as a client sends it: FIN set, masked, with the payload as given. */
    public static byte[] wsFrame(int opcode,byte[] payload){
        byte[] body=payload==null?new byte[0]:payload;
        byte[] mask=new byte[4];
        new Random().nextBytes(mask);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(0x80|opcode);
        int length=body.length;
        if(length<126)out.write(0x80|length);
        else if(length<65536){out.write(0x80|126);out.write(length>>>8);out.write(length);}
        else{
            out.write(0x80|127);
            for(int i=7;i>=0;i--)out.write((int)(((long)length>>>(i*8))&0xff));
        }
        out.write(mask,0,4);
        for(int i=0;i<length;i++)out.write(body[i]^mask[i%4]);
        return out.toByteArray();
    }

    /**
     * One WebSocket frame as the server sends it. A read timeout before the first byte comes back as
     * null, so a quiet socket keeps its heartbeat instead of being torn down.
     */
    public static WsFrame readWsFrame(InputStream in)throws IOException{
        byte[] head=readFully(in,2,true);
        if(head==null)return null;
        int opcode=head[0]&0x0f,masked=head[1]&0x80,length=head[1]&0x7f;
        if(length==126)length=readShort(readFully(in,2,false),0);
        else if(length==127)length=(int)readLong(readFully(in,8,false));
        byte[] mask=masked!=0?readFully(in,4,false):null;
        byte[] payload=length>0?readFully(in,length,false):new byte[0];
        if(mask!=null)for(int i=0;i<payload.length;i++)payload[i]^=mask[i%4];
        return new WsFrame(opcode,payload);
    }

    /** Read exactly {@code length} bytes; a timeout before the first one reads as null. */
    private static byte[] readFully(InputStream in,int length,boolean allowTimeout)throws IOException{
        byte[] out=new byte[length];
        int at=0;
        while(at<length){
            int n;
            try{n=in.read(out,at,length-at);}
            catch(java.net.SocketTimeoutException e){
                if(allowTimeout&&at==0)return null;
                continue;
            }
            if(n<0)throw new java.io.EOFException("弹幕连接已断开");
            at+=n;
        }
        return out;
    }

    private static long readLong(byte[] d){long v=0;for(int i=0;i<8;i++)v=(v<<8)|(d[i]&0xffL);return v;}
    private static int readInt(byte[] d,int at){return ((d[at]&0xff)<<24)|((d[at+1]&0xff)<<16)|((d[at+2]&0xff)<<8)|(d[at+3]&0xff);}
    private static int readShort(byte[] d,int at){return ((d[at]&0xff)<<8)|(d[at+1]&0xff);}
    private static void writeInt(byte[] d,int at,int v){d[at]=(byte)(v>>>24);d[at+1]=(byte)(v>>>16);d[at+2]=(byte)(v>>>8);d[at+3]=(byte)v;}
    private static void writeShort(byte[] d,int at,int v){d[at]=(byte)(v>>>8);d[at+1]=(byte)v;}
}

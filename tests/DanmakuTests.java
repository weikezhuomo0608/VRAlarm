import dev.hazel.livealarm.DanmakuFrames;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.zip.Deflater;

/**
 * The danmaku framing: the 16-byte header, the zlib version the app asks for, and what an
 * unreadable packet has to do (nothing at all, never crash).
 */
public final class DanmakuTests {
    private static int count;
    private static void ok(boolean yes,String label){count++;if(!yes)throw new AssertionError(label);}

    /** A packet built here rather than by the class under test, so the header is checked twice. */
    private static byte[] packet(int ver,int op,byte[] body){
        byte[] out=new byte[16+body.length];
        int total=out.length;
        out[0]=(byte)(total>>>24);out[1]=(byte)(total>>>16);out[2]=(byte)(total>>>8);out[3]=(byte)total;
        out[5]=16;
        out[6]=(byte)(ver>>>8);out[7]=(byte)ver;
        out[8]=(byte)(op>>>24);out[9]=(byte)(op>>>16);out[10]=(byte)(op>>>8);out[11]=(byte)op;
        out[15]=1;
        System.arraycopy(body,0,out,16,body.length);
        return out;
    }
    private static byte[] zlib(byte[] data){
        Deflater deflater=new Deflater();
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] buffer=new byte[4096];
        while(!deflater.finished())out.write(buffer,0,deflater.deflate(buffer));
        deflater.end();
        return out.toByteArray();
    }

    public static void main(String[] args)throws Exception{
        byte[] danmu="{\"cmd\":\"DANMU_MSG\"}".getBytes("UTF-8");

        // A version-1 packet carries its JSON directly.
        List<DanmakuFrames.Frame> packets=DanmakuFrames.split(packet(DanmakuFrames.VER_JSON,DanmakuFrames.OP_COMMAND,danmu));
        ok(packets.size()==1,"one packet in, one packet out");
        ok(packets.get(0).ver==DanmakuFrames.VER_JSON&&packets.get(0).op==DanmakuFrames.OP_COMMAND,"the header survives the round trip");
        List<byte[]> bodies=DanmakuFrames.commands(packets.get(0));
        ok(bodies.size()==1&&new String(bodies.get(0),"UTF-8").equals(new String(danmu,"UTF-8")),
            "a plain packet is handed on as it is");
        ok(DanmakuFrames.packet(DanmakuFrames.OP_AUTH,danmu).length==16+danmu.length,"a packet we send is header plus body");

        // Version 2 wraps those same packets again, zlib-compressed.
        byte[] pocket="{\"cmd\":\"POPULARITY_RED_POCKET_V2_START\"}".getBytes("UTF-8");
        ByteArrayOutputStream inner=new ByteArrayOutputStream();
        inner.write(packet(DanmakuFrames.VER_JSON,DanmakuFrames.OP_COMMAND,pocket));
        inner.write(packet(DanmakuFrames.VER_JSON,DanmakuFrames.OP_COMMAND,danmu));
        List<DanmakuFrames.Frame> top=DanmakuFrames.split(packet(DanmakuFrames.VER_ZLIB,DanmakuFrames.OP_COMMAND,zlib(inner.toByteArray())));
        ok(top.size()==1&&top.get(0).ver==DanmakuFrames.VER_ZLIB,"the outer packet announces the compressed version");
        List<byte[]> two=DanmakuFrames.commands(top.get(0));
        ok(two.size()==2,"both inner packets come out of the compressed one");
        ok(new String(two.get(0),"UTF-8").contains("POPULARITY_RED_POCKET_V2_START"),"in the order they were sent");
        ok(new String(two.get(1),"UTF-8").contains("DANMU_MSG"),"and the second one too");

        // What must never happen: a crash on something this app cannot read.
        ok(DanmakuFrames.commands(DanmakuFrames.split(packet(DanmakuFrames.VER_BROTLI,DanmakuFrames.OP_COMMAND,new byte[]{1,2,3})).get(0)).isEmpty(),
            "a brotli packet is skipped, not guessed at");
        ok(DanmakuFrames.commands(DanmakuFrames.split(packet(DanmakuFrames.VER_ZLIB,DanmakuFrames.OP_COMMAND,new byte[]{9,9,9,9})).get(0)).isEmpty(),
            "a body that is not zlib either yields nothing");
        ok(DanmakuFrames.commands(DanmakuFrames.split(packet(DanmakuFrames.VER_JSON,DanmakuFrames.OP_HEARTBEAT_REPLY,new byte[]{1})).get(0)).isEmpty(),
            "a heartbeat answer carries no command");
        ok(DanmakuFrames.split(new byte[]{0,0,1}).isEmpty(),"a truncated packet is dropped");
        ok(DanmakuFrames.split(null).isEmpty(),"no bytes is no packets");

        // Several packets in one frame, with a torn one at the end.
        ByteArrayOutputStream many=new ByteArrayOutputStream();
        many.write(packet(DanmakuFrames.VER_JSON,DanmakuFrames.OP_COMMAND,danmu));
        many.write(packet(DanmakuFrames.VER_JSON,DanmakuFrames.OP_COMMAND,danmu));
        many.write(new byte[]{1,2,3});
        ok(DanmakuFrames.split(many.toByteArray()).size()==2,"a trailing partial packet is dropped, the good ones are kept");

        // The WebSocket layer: the danmaku operation lives in the packet header, and the frame opcode
        // is always binary. Sending op=7 (auth) as the *frame* opcode made the server read a close
        // frame and hang up, so this round trip is the test that has to exist.
        byte[] authBody="{\"uid\":0,\"roomid\":1,\"protover\":2,\"platform\":\"web\",\"type\":2,\"key\":\"k\"}".getBytes("UTF-8");
        byte[] authFrame=DanmakuFrames.wsFrame(DanmakuFrames.WS_BINARY,DanmakuFrames.packet(DanmakuFrames.OP_AUTH,authBody));
        ok((authFrame[0]&0x0f)==DanmakuFrames.WS_BINARY,"a data frame is sent as binary");
        ok((authFrame[1]&0x80)!=0,"a client frame is masked");
        ok((authFrame[1]&0x7f)==authFrame.length-6,"a short payload carries its length in the single byte");
        DanmakuFrames.WsFrame back=DanmakuFrames.readWsFrame(new java.io.ByteArrayInputStream(authFrame));
        ok(back!=null&&back.opcode==DanmakuFrames.WS_BINARY,"the frame reads back with its opcode");
        List<DanmakuFrames.Frame> sent=DanmakuFrames.split(back.payload);
        ok(sent.size()==1&&sent.get(0).op==DanmakuFrames.OP_AUTH,"and its payload is the auth packet");
        ok(new String(sent.get(0).body,"UTF-8").contains("\"protover\":2"),"with the body intact through the mask");
        ok((DanmakuFrames.wsFrame(DanmakuFrames.WS_PONG,new byte[]{1,2})[0]&0xff)==(0x80|DanmakuFrames.WS_PONG),
            "a pong is answered at the WebSocket level, with its own opcode");
        byte[] big=new byte[300];
        byte[] bigFrame=DanmakuFrames.wsFrame(DanmakuFrames.WS_BINARY,big);
        ok((bigFrame[1]&0x7f)==126,"a payload past 125 bytes announces the two-byte length");
        ok((((bigFrame[2]&0xff)<<8)|(bigFrame[3]&0xff))==300,"and writes it big-endian");
        ok(DanmakuFrames.readWsFrame(new java.io.ByteArrayInputStream(bigFrame)).payload.length==300,
            "and it reads back at that length");

        System.out.println("PASS: "+count+" danmaku framing assertions");
    }
}

import dev.hazel.livealarm.Packets;

import java.util.*;

/** Red packets on the danmaku stream: which kind is worth announcing, and which ones still need it. */
public final class PacketsTests {
    private static int count;
    private static void ok(boolean yes,String label){count++;if(!yes)throw new AssertionError(label);}
    private static Packets.Item item(String id,int type){return new Packets.Item(id,"某个老板",type,300000);}

    public static void main(String[] args){
        // rp_type: 1 礼物红包, 2 上舰红包 (the 舰长红包), 3 电池红包. The middle one is what the feature
        // exists for — a 0.1-折 boarding slot anybody may grab, exactly why it is worth hurrying for,
        // not a packet reserved for guards. During the trial the kind no longer decides what is
        // announced (see below), it only decides the wording.
        ok(Packets.RP_GIFT==1&&Packets.RP_GUARD==2&&Packets.RP_BATTERY==3,"the three kinds keep the protocol's own numbers");
        ok(Packets.guard(2),"the 上舰红包 is the kind the wording singles out");
        ok(!Packets.guard(1)&&!Packets.guard(3)&&!Packets.guard(0),"gifts, batteries and unknown kinds are left alone");
        ok(Packets.typeName(1).equals("礼物红包")&&Packets.typeName(2).equals("舰长红包")&&Packets.typeName(3).equals("电池红包"),
            "each kind has a name for the interface");
        ok(Packets.typeName(9).equals("红包"),"an unknown kind still reads as a packet");

        // Every new, identifiable packet is announced during the trial: the kind table comes from a
        // community dictionary and has never been checked against a real broadcast, so nothing may
        // be dropped for looking like the wrong kind.
        List<Packets.Item> items=Arrays.asList(item("a",2),item("b",2),item("gift",1),item("battery",3),
            new Packets.Item("","某个老板",2,300000));
        List<Packets.Item> fresh=Packets.fresh(Collections.singletonList("a"),items);
        ok(fresh.size()==3&&fresh.get(0).id.equals("b")&&fresh.get(1).id.equals("gift")&&fresh.get(2).id.equals("battery"),
            "only the already announced one is held back; every kind still comes through");
        ok(Packets.fresh(null,items).size()==4,"with nothing announced all four usable packets come through");
        ok(Packets.fresh(null,new ArrayList<Packets.Item>()).isEmpty(),"an empty stream announces nothing");
        ok(Packets.fresh(null,null).isEmpty(),"a missing list is not an error");
        ok(!new Packets.Item("",null,2,1).usable(),"a packet without an id cannot be announced");
        ok(!new Packets.Item(null,null,2,-5).usable(),"a null id is not usable either");
        ok(new Packets.Item("x",null,2,-5).leftMillis==0,"a missing duration reads as zero, not negative");

        // Remembered ids expire on their own so the store cannot grow without bound.
        List<String> ids=Arrays.asList("old","new");
        List<Long> at=Arrays.asList(0L,Packets.SEEN_MILLIS);
        List<String> kept=Packets.prune(ids,at,Packets.SEEN_MILLIS);
        ok(kept.size()==2,"an id at the edge of the window is still remembered");
        ok(Packets.prune(ids,at,Packets.SEEN_MILLIS+1).equals(Collections.singletonList("new")),
            "an id older than the window is forgotten");
        ok(Packets.prune(new ArrayList<String>(),new ArrayList<Long>(),System.currentTimeMillis()).isEmpty(),
            "nothing remembered prunes to nothing");
        ok(Packets.SEEN_MILLIS==30*60*1000L,"the memory window is half an hour");

        System.out.println("PASS: "+count+" red-packet assertions");
    }
}

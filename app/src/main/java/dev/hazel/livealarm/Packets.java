package dev.hazel.livealarm;

import java.util.*;

/**
 * The red packets of one live room, with the 舰长红包 — the kind this feature exists for — singled out.
 *
 * A 舰长红包 (Bilibili calls it 上舰红包) is not "a packet only guards may grab". It is a
 * 0.1-折 boarding slot somebody else paid for: whoever grabs it can take the room's 舰长
 * seat for a fraction of the price, so the whole point is to hear about it while it is still
 * there. It never shows up in the room's static information — it arrives as a broadcast on
 * the room's danmaku connection:
 *
 *   POPULARITY_RED_POCKET_V2_START, whose {@code data.rp_type} tells the three kinds apart:
 *   1 礼物红包 (gifts), 2 上舰红包 (the one we want), 3 电池红包 (batteries).
 *
 * Pure Java: the JSON stays in BiliApi, the rule stays here, so the rule runs in the headless
 * kernel tests.
 */
public final class Packets {
    /** The three values of {@code rp_type}, as the room protocol broadcasts them. */
    public static final int RP_GIFT=1, RP_GUARD=2, RP_BATTERY=3;

    /** One packet id is announced once. Thirty minutes is far longer than any packet lives
     *  (they are gone in five to ten), and remembering less would re-announce the same one. */
    public static final long SEEN_MILLIS=30*60*1000L;

    /** One packet as a broadcast describes it. Fields the server may omit simply read as empty,
     *  because a shape change has to mean "nothing to announce", never a crash. */
    public static final class Item {
        public final String id, sender;
        public final int rpType;
        public final long leftMillis;
        public Item(String id,String sender,int rpType,long leftMillis){
            this.id=id==null?"":id; this.sender=sender==null?"":sender;
            this.rpType=rpType; this.leftMillis=leftMillis>0?leftMillis:0;
        }
        /** A packet without an id cannot be deduplicated, so it cannot be announced: announcing
         *  it again on every broadcast is worse than missing it. */
        public boolean usable(){return !id.isEmpty();}
    }

    private Packets(){}

    /** Whether this is the kind the whole feature exists for. Used for the wording of a notice, and
     *  kept public because the interface and the tests both ask it. During the trial {@link #fresh}
     *  no longer narrows the announcements to this kind. */
    public static boolean guard(int rpType){return rpType==RP_GUARD;}

    /** The name of a packet kind, for the interface and the record. */
    public static String typeName(int rpType){
        switch(rpType){
            case RP_GIFT:return "礼物红包";
            case RP_GUARD:return "舰长红包";
            case RP_BATTERY:return "电池红包";
            default:return "红包";
        }
    }

    /** The packets worth announcing: usable and not announced before. During the trial every kind
     *  comes through, because the type table is community-sourced and this project has never seen a
     *  real broadcast: a mislabelled extra notice costs one glance, while a silently dropped
     *  舰长红包 costs the packet. The caller stores the ids afterwards; nothing here remembers. */
    public static List<Item> fresh(Collection<String> announced,List<Item> items){
        ArrayList<Item> out=new ArrayList<>();
        if(items==null)return out;
        for(Item item:items){
            if(item==null||!item.usable())continue;
            if(announced!=null&&announced.contains(item.id))continue;
            out.add(item);
        }
        return out;
    }

    /** Drop remembered ids older than the window. Both the ids and their moments arrive as
     *  parallel pairs so the pruning rule stays pure and testable. */
    public static List<String> prune(List<String> ids,List<Long> seenAt,long now){
        ArrayList<String> keep=new ArrayList<>();
        for(int i=0;i<ids.size();i++){
            long at=i<seenAt.size()?seenAt.get(i):0;
            if(now-at<=SEEN_MILLIS)keep.add(ids.get(i));
        }
        return keep;
    }
}

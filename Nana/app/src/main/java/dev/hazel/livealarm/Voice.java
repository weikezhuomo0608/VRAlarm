package dev.hazel.livealarm;

import java.util.Locale;

/** The single place the app's copy lives: a room title becomes a kind, a kind becomes a line. */
public final class Voice {
    public static final String SONG="歌回", COLLAB="联动", HORSE="马", GAME="游戏", TALK="杂谈", LIVE="直播";
    public static final String END_TITLE="收工了のだ";
    /** Order matters: the first family that hits wins, so a title carrying both 歌 and 杂 is a 歌回. */
    private static final String[] SONG_WORDS={"歌回","歌杂","歌枠","唱","歌"};
    private static final String[] COLLAB_WORDS={"联动","连麦","联合","合作"};
    private static final String[] HORSE_WORDS={"小马","马娘","赛马","ウマ","马"};
    private static final String[] GAME_WORDS={"游戏","game"};
    private static final String[] TALK_WORDS={"杂谈","闲聊","杂"};
    private static final String[] NAME_SUFFIXES={"official","_official","-official"};

    private Voice(){}

    public static String kindOf(String title){
        String text=title==null?"":title.toLowerCase(Locale.ROOT);
        if(hits(text,SONG_WORDS))return SONG;
        if(hits(text,COLLAB_WORDS))return COLLAB;
        if(hits(text,HORSE_WORDS))return HORSE;
        if(hits(text,GAME_WORDS))return GAME;
        if(hits(text,TALK_WORDS))return TALK;
        return LIVE;
    }
    private static boolean hits(String text,String[] words){
        for(String word:words)if(text.contains(word))return true;
        return false;
    }
    /** Bilibili account names carry a marketing suffix that has no place on a notification. */
    public static String shortName(String name){
        String value=name==null?"":name.trim();
        String low=value.toLowerCase(Locale.ROOT);
        for(String suffix:NAME_SUFFIXES){
            if(!low.endsWith(suffix))continue;
            value=value.substring(0,value.length()-suffix.length()).trim();
            break;
        }
        return value;
    }
    public static String alarmHeadline(String anchorName,String title){
        String who=shortName(anchorName);
        if(who.isEmpty())who="主播";
        String kind=kindOf(title);
        return kind.equals(LIVE)?who+" 上线了のだ":who+" 上线了のだ · "+kind;
    }
    /** Prefix for a record line, empty when the title says nothing about the kind of stream. */
    public static String tag(String title){
        String kind=kindOf(title);
        return kind.equals(LIVE)?"":"【"+kind+"】";
    }
    public static String duration(long millis){
        if(millis<=0)return "";
        long minutes=Math.max(1,millis/60000L),hours=minutes/60L,rest=minutes%60L;
        if(hours<=0)return rest+" 分";
        return rest==0?hours+" 小时":hours+" 小时 "+rest+" 分";
    }
    public static String endText(String who,long millis){
        String name=shortName(who);
        if(name.isEmpty())name="主播";
        String length=duration(millis);
        return length.isEmpty()?name+" 收工了のだ":name+" 收工了のだ · 本场 "+length;
    }
}

import dev.hazel.livealarm.*;
import java.util.*;

public class VoiceTests {
    static int count=0;
    static void check(boolean value,String test){count++;if(!value)throw new AssertionError(test);}
    static void same(String actual,String expected,String test){
        count++;
        if(!expected.equals(actual))throw new AssertionError(test+" (got "+actual+", wanted "+expected+")");
    }
    public static void main(String[] args){
        same(Voice.kindOf("【午间】小马歌杂"),Voice.SONG,"a song stream wins over the 杂 in the same title");
        same(Voice.kindOf("歌枠"),Voice.SONG,"the japanese spelling is the same kind");
        same(Voice.kindOf("杂谈回"),Voice.TALK,"talk");
        same(Voice.kindOf("马娘日常"),Voice.HORSE,"horse");
        same(Voice.kindOf("联动"),Voice.COLLAB,"collab");
        same(Voice.kindOf("游戏回"),Voice.GAME,"game");
        same(Voice.kindOf("GAME NIGHT"),Voice.GAME,"the latin spelling is matched case-insensitively");
        same(Voice.kindOf("歌联动"),Voice.SONG,"priority is the table order, not the position in the title");
        same(Voice.kindOf(""),Voice.LIVE,"an empty title is just a live stream");
        same(Voice.kindOf(null),Voice.LIVE,"a missing title is not a crash");

        same(Voice.shortName("辉月七七official"),"辉月七七","the marketing suffix is dropped");
        same(Voice.shortName("辉月七七"),"辉月七七","a plain name is untouched");
        same(Voice.shortName("  "),"","a blank name stays blank");
        same(Voice.shortName(null),"" ,"a missing name is not a crash");

        same(Voice.alarmHeadline("辉月七七official","【午间】小马歌杂"),"辉月七七 上线了のだ · 歌回","the alarm line carries the kind");
        same(Voice.alarmHeadline("辉月七七","画面测试"),"辉月七七 上线了のだ","an unclassified title carries no suffix");
        same(Voice.alarmHeadline("",""),"主播 上线了のだ","an unnamed anchor still reads as a line");
        same(Voice.tag("小马歌杂"),"【歌回】","the record prefix names the kind");
        same(Voice.tag(""),"","an unclassified title adds no prefix");

        same(Voice.duration(0),"","no start time means no length claim");
        same(Voice.duration(60_000L),"1 分","a single minute rounds up");
        same(Voice.duration(45*60_000L),"45 分","minutes only");
        same(Voice.duration(2*3_600_000L+13*60_000L),"2 小时 13 分","hours and minutes");
        same(Voice.duration(3*3_600_000L),"3 小时","a whole number of hours drops the minute part");
        same(Voice.endText("辉月七七official",0),"辉月七七 收工了のだ","the end line drops the marketing suffix");
        same(Voice.endText("辉月七七",45*60_000L),"辉月七七 收工了のだ · 本场 45 分","the end line carries the length");
        System.out.println("PASS: "+count+" voice assertions");
    }
}

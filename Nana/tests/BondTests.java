import dev.hazel.livealarm.*;

public class BondTests {
    static int count=0;
    static void check(boolean value,String test){count++;if(!value)throw new AssertionError(test);}
    static void same(Object actual,Object expected,String test){
        count++;
        if(!expected.equals(actual))throw new AssertionError(test+" (got "+actual+", wanted "+expected+")");
    }
    public static void main(String[] args){
        Bond.State fresh=Bond.of(0,0);
        same(fresh.level,1,"a new device starts at the first level");
        same(fresh.title,"初见","the first level is named");
        same(fresh.next,"边苔桑","the next level is named");
        same(fresh.points,0L,"no sessions, no points");
        same(fresh.remain,200L,"the gap to the next level is the whole threshold");
        same(fresh.percent,0,"no progress yet");

        same(Bond.of(1,60).points,100L,"a session is worth forty points plus its minutes");
        same(Bond.of(1,60).percent,50,"halfway to the second level");
        same(Bond.of(2,120).level,2,"two hundred points reach the second level");
        same(Bond.of(2,120).title,"边苔桑","the second level carries the medal name");
        same(Bond.of(5,400).level,3,"six hundred points reach the third level");
        same(Bond.of(10,1100).level,4,"fifteen hundred points reach the fourth level");
        same(Bond.of(23,2200).level,5,"three thousand points reach the fifth level");

        Bond.State top=Bond.of(60,3600);
        same(top.level,6,"six thousand points reach the last level");
        same(top.title,"小马娘同盟","the last level is named");
        same(top.next,"","there is nothing above the last level");
        same(top.percent,100,"the last level is full");
        same(top.remain,0L,"nothing remains above the last level");

        same(Bond.of(-3,-100).points,0L,"negative input cannot earn points");
        same(Bond.of(-3,-100).level,1,"negative input cannot skip a level");
        check(Bond.of(100,100000).percent==100,"progress never overflows");
        System.out.println("PASS: "+count+" bond assertions");
    }
}

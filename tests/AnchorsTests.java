import dev.hazel.livealarm.Anchors;
import java.util.*;

/** Anchor list validation and repair, independent of Android and org.json. */
public final class AnchorsTests {
    private static int count;
    private static void check(boolean yes,String label){count++;if(!yes)throw new AssertionError(label);}
    private static void rejects(List<Anchors.Anchor> list,String label){
        count++;try{Anchors.validate(list);}catch(IllegalArgumentException e){return;}
        throw new AssertionError(label);
    }
    private static String repeated(char c,int times){
        StringBuilder b=new StringBuilder();for(int i=0;i<times;i++)b.append(c);return b.toString();
    }

    public static void main(String[] args){
        List<Anchors.Anchor> seed=Anchors.defaults();
        check(seed.size()==1,"the shipped default is a single anchor");
        check(Anchors.DEFAULT_ID.equals(seed.get(0).id),"default anchor keeps the legacy id");
        check(seed.get(0).uid==1298779265L&&seed.get(0).room==1713546334L,"default anchor keeps the legacy uid and room");
        check(seed.get(0).enabled,"default anchor starts enabled");
        check(Anchors.validate(seed)==seed,"a valid list is returned unchanged");

        check(Anchors.validate(Arrays.asList(new Anchors.Anchor("a","小满",1,2,true))).size()==1,"one named anchor passes");

        rejects(null,"a null list is rejected");
        rejects(new ArrayList<Anchors.Anchor>(),"an empty list is rejected");
        rejects(Arrays.asList(seed.get(0),new Anchors.Anchor("hazel","重复",3,4,true)),"a duplicate id is rejected");
        rejects(Arrays.asList(new Anchors.Anchor("a","n",1,2,false)),"a list with nothing enabled is rejected");
        rejects(Arrays.asList(new Anchors.Anchor("a","n",0,2,true)),"a zero uid is rejected");
        rejects(Arrays.asList(new Anchors.Anchor("a","n",1,0,true)),"a zero room is rejected");
        rejects(Arrays.asList(new Anchors.Anchor("a","",1,2,true)),"a blank name is rejected");
        rejects(Arrays.asList(new Anchors.Anchor("a",repeated('n',Anchors.MAX_NAME+1),1,2,true)),"a name past the limit is rejected");
        rejects(Arrays.asList(new Anchors.Anchor("",  "n",1,2,true)),"a blank id is rejected");
        rejects(Arrays.asList(new Anchors.Anchor("../etc/passwd","n",1,2,true)),"a path in the id is rejected");
        rejects(Arrays.asList(new Anchors.Anchor("a/b","n",1,2,true)),"a slash in the id is rejected");

        List<Anchors.Anchor> full=new ArrayList<Anchors.Anchor>();
        for(int i=0;i<Anchors.MAX;i++)full.add(new Anchors.Anchor("a"+i,"n"+i,100L+i,200L+i,true));
        check(Anchors.validate(full).size()==Anchors.MAX,"a full list passes");
        List<Anchors.Anchor> over=new ArrayList<Anchors.Anchor>(full);
        over.add(new Anchors.Anchor("extra","n",1,2,true));
        rejects(over,"a list past the limit is rejected");

        check(Anchors.validate(Arrays.asList(new Anchors.Anchor("a","n",1,2,false),new Anchors.Anchor("b","n",3,4,true))).size()==2,"a disabled anchor is kept while another one is enabled");
        check(Anchors.fitName("x").equals("x"),"a short name is kept");
        check(Anchors.fitName("  x  ").equals("x"),"a resolved name is trimmed");
        check(Anchors.fitName(repeated('n',Anchors.MAX_NAME+5)).length()==Anchors.MAX_NAME,"a long resolved name is shortened");
        check(Anchors.fitName(null).isEmpty(),"a missing resolved name is empty");

        check(Anchors.validId("hazel"),"a plain id is valid");
        check(Anchors.validId("3f1a-9_b"),"uuid style ids are valid");
        check(!Anchors.validId(".."),"a dot id is invalid");
        check(!Anchors.validId("a b"),"a spaced id is invalid");
        check(!Anchors.validId(null),"a null id is invalid");

        check(Anchors.find(seed,Anchors.DEFAULT_ID)!=null,"find reaches the default anchor");
        check(Anchors.find(seed,"missing")==null,"find misses an unknown id");
        check(Anchors.find(seed,null)==null,"find misses a null id");
        check(Anchors.findIndex(seed,Anchors.DEFAULT_ID)==0,"findIndex locates the default anchor");
        check(Anchors.findIndex(seed,"missing")==-1,"findIndex reports a miss");
        check(Anchors.findIndex(null,"x")==-1,"findIndex tolerates a null list");
        check(Anchors.firstId(seed).equals(Anchors.DEFAULT_ID),"firstId reads the first anchor");
        check(Anchors.firstId(new ArrayList<Anchors.Anchor>()).equals(Anchors.DEFAULT_ID),"firstId falls back to the legacy id");
        check(Anchors.firstId(null).equals(Anchors.DEFAULT_ID),"firstId tolerates a null list");

        // sanitize is the read path: stored data must never take the watch down.
        List<Anchors.Anchor> mixed=new ArrayList<Anchors.Anchor>();
        mixed.add(new Anchors.Anchor("keep","保留",1,2,true));
        mixed.add(new Anchors.Anchor("keep","重复编号",3,4,true));
        mixed.add(new Anchors.Anchor("zero","零 uid",0,2,true));
        mixed.add(new Anchors.Anchor("blank","",1,2,true));
        mixed.add(null);
        List<Anchors.Anchor> repaired=Anchors.sanitize(mixed);
        check(repaired.size()==1&&repaired.get(0).id.equals("keep"),"sanitize keeps only the usable entry");
        check(Anchors.sanitize(new ArrayList<Anchors.Anchor>()).size()==1,"sanitize restores the default anchor when nothing survives");
        check(Anchors.sanitize(null).size()==1,"sanitize tolerates a null list");
        List<Anchors.Anchor> garbage=new ArrayList<Anchors.Anchor>();
        for(int i=0;i<Anchors.MAX+8;i++)garbage.add(new Anchors.Anchor("g"+i,"n"+i,1L+i,2L+i,true));
        check(Anchors.sanitize(garbage).size()==Anchors.MAX,"sanitize caps the list at the limit");
        check(Anchors.sanitize(Arrays.asList(new Anchors.Anchor("a","n",1,2,false))).size()==1,"sanitize keeps an all-disabled list instead of resurrecting the default");
        check(Anchors.sanitize(mixed).get(0).enabled,"sanitize preserves the enabled flag");

        check(seed.get(0).withEnabled(false).enabled==false&&seed.get(0).enabled,"withEnabled returns a copy");
        check(seed.get(0).withEnabled(false).id.equals(seed.get(0).id),"withEnabled keeps identity");

        Anchors.Anchor silent=new Anchors.Anchor("x","名字",1,2,true,false);
        check(!silent.alarm,"a constructed anchor can carry the alarm-off flag");
        check(silent.withAlarm(true).alarm,"withAlarm flips the flag without touching the rest");
        check(new Anchors.Anchor("x","名字",1,2,true).alarm,"the old constructor defaults the bell to on");
        check(Anchors.defaults().get(0).alarm,"the default anchor rings by default");
        check(silent.withAlarm(false).enabled,"withAlarm keeps the enabled flag");

        // The uid travels as text as well as a long: JavaScript cannot represent a 16-digit uid
        // past 2^53-1, so the text copy is the one the web interface reads back. The two must be
        // derived from one source or they can disagree, which would show one account and watch another.
        String big="9999999999999999";
        Anchors.Anchor wide=new Anchors.Anchor("w","大 UID",big,2,true,true);
        check(wide.uid==9999999999999999L,"a 16-digit uid parses into the long exactly");
        check(wide.uidText.equals(big),"a 16-digit uid keeps its exact text");
        check(Anchors.parseUid(big)==9999999999999999L,"parseUid reads the largest 16-digit uid exactly");
        check(Anchors.parseUid(Anchors.DEFAULT_UID+"")==Anchors.DEFAULT_UID,"parseUid reads a legacy uid");
        check(Anchors.parseUid("  3546729368520811  ")==3546729368520811L,"parseUid trims surrounding space");
        check(Anchors.parseUid("")==0,"parseUid reads a blank uid as unset");
        check(Anchors.parseUid(null)==0,"parseUid reads a missing uid as unset");
        check(Anchors.parseUid("0")==0,"parseUid reads a zero uid as unset");
        check(Anchors.parseUid("12a")==0,"parseUid rejects a non-numeric uid");
        check(Anchors.parseUid("-5")==0,"parseUid rejects a negative uid");
        check(Anchors.parseUid("99999999999999999999")==Long.MAX_VALUE,"a uid too large for a long is clamped, never wrapped");
        check(new Anchors.Anchor("x","n",Anchors.DEFAULT_UID,2,true).uidText.equals("1298779265"),"the long constructor derives the text copy");
        check(new Anchors.Anchor("x","n","007",2,true,true).uidText.equals("7"),"a padded uid cannot leave the two copies disagreeing");
        check(Anchors.defaults().get(0).uidText.equals("1298779265"),"the default anchor carries its uid as text");
        check(Anchors.sanitize(seed).get(0).uidText.equals("1298779265"),"sanitize preserves the uid text");
        check(seed.get(0).withEnabled(false).uidText.equals("1298779265"),"withEnabled carries the uid text across");
        check(seed.get(0).withAlarm(false).uidText.equals("1298779265"),"withAlarm carries the uid text across");
        System.out.println("PASS: "+count+" anchor list assertions");
    }
}

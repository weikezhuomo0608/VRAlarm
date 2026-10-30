import dev.hazel.livealarm.Wbi;

import java.util.*;

/** The wbi signature the newer interfaces ask for: the mixed key and the signed query. */
public final class WbiTests {
    private static int count;
    private static void ok(boolean yes,String label){count++;if(!yes)throw new AssertionError(label);}

    public static void main(String[] args){
        // The pair published by the nav endpoint (and used in its own documentation).
        String img="7cd084941338484aae1ad9425b84077c",sub="4932caff0ff746eab6f01bf08b70ac45";
        String mixin=Wbi.mixinKey(img,sub);
        ok(mixin.equals("ea1db124af3c7062474693fa704f4ff8"),"the two daily keys mix into the documented 32-character key");
        ok(Wbi.mixinKey(img,sub).length()==32,"the signing key is always 32 characters");
        ok(Wbi.mixinKey(null,null).isEmpty(),"a key we never received mixes into nothing");

        Map<String,String> params=new LinkedHashMap<>();
        params.put("web_location","444.8");
        params.put("roomid","6");
        ok(Wbi.query(params,mixin,1790139598L).equals("roomid=6&w_rid=3c0ca19659794612c6d8369aa93bb286&web_location=444.8&wts=1790139598"),
            "the parameters are sorted by name and signed together with the stamp");
        ok(Wbi.query(params,mixin,1790139599L).endsWith("wts=1790139599"),"the stamp travels in the query");
        ok(!Wbi.query(params,mixin,1790139598L).equals(Wbi.query(params,mixin,1790139599L)),
            "a different stamp gives a different signature");
        ok(Wbi.query(params,mixin,1790139598L).contains("w_rid="),"the signature is appended to the query");
        ok(Wbi.query(new HashMap<String,String>(),mixin,1790139598L).contains("w_rid="),"an empty parameter set is still signed");

        System.out.println("PASS: "+count+" wbi signature assertions");
    }
}

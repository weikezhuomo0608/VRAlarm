package dev.hazel.livealarm;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.*;

/**
 * The wbi signature the newer Bilibili interfaces ask for. Two daily keys are fetched from
 * {@code nav}, mixed through a fixed permutation into a 32-character key, and every request
 * signs its sorted parameters with an MD5 of "query + key".
 *
 * Pure Java (strings and a digest), so the algorithm is covered by the headless tests.
 */
public final class Wbi {
    /** The permutation that turns the two daily keys into the signing key. */
    private static final int[] MIXIN={
        46,47,18,2,53,8,23,32,15,50,10,31,58,3,45,35,27,43,5,49,33,9,42,19,29,28,14,39,12,38,41,13,
        37,48,7,16,24,55,40,61,26,17,0,1,60,51,30,4,22,25,54,21,56,59,6,63,57,62,11,36,20,34,44,52};

    private Wbi(){}

    /** The signing key for a pair of daily keys: their 64 characters in the table's order, first 32 kept. */
    public static String mixinKey(String imgKey,String subKey){
        String raw=(imgKey==null?"":imgKey)+(subKey==null?"":subKey);
        StringBuilder out=new StringBuilder();
        for(int at:MIXIN){
            if(at>=raw.length())continue;
            out.append(raw.charAt(at));
        }
        return out.length()<=32?out.toString():out.substring(0,32);
    }

    /**
     * The query string to send: the parameters sorted by name, plus the current stamp and the
     * signature. Values have the characters the algorithm refuses (!'()*) removed rather than
     * escaped, which is what the web client does and what the servers accept.
     */
    public static String query(Map<String,String> params,String mixinKey,long wts){
        TreeMap<String,String> sorted=new TreeMap<>();
        if(params!=null)for(Map.Entry<String,String> e:params.entrySet())sorted.put(e.getKey(),clean(e.getValue()));
        sorted.put("wts",Long.toString(wts));
        StringBuilder query=new StringBuilder();
        for(Map.Entry<String,String> e:sorted.entrySet()){
            if(query.length()>0)query.append('&');
            query.append(e.getKey()).append('=').append(e.getValue());
        }
        String signed=query.toString();
        sorted.put("w_rid",md5(signed+mixinKey));
        StringBuilder out=new StringBuilder();
        for(Map.Entry<String,String> e:sorted.entrySet()){
            if(out.length()>0)out.append('&');
            out.append(e.getKey()).append('=').append(e.getValue());
        }
        return out.toString();
    }

    private static String clean(String value){
        String text=value==null?"":value;
        StringBuilder out=new StringBuilder();
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(c=='!'||c=='\''||c=='('||c==')'||c=='*')continue;
            out.append(c);
        }
        String encoded;
        try{encoded=URLEncoder.encode(out.toString(),"UTF-8");}
        catch(UnsupportedEncodingException e){encoded=out.toString();}
        return encoded.replace("+","%20");
    }

    private static String md5(String text){
        try{
            byte[] digest=MessageDigest.getInstance("MD5").digest(text.getBytes("UTF-8"));
            StringBuilder out=new StringBuilder();
            for(byte b:digest)out.append(String.format("%02x",b));
            return out.toString();
        }catch(Exception e){return "";}
    }
}

package dev.hazel.livealarm;

import org.json.JSONObject;
import java.io.IOException;
import java.util.Random;

/**
 * What an anonymous visitor needs to be let in: a device fingerprint cookie and the two daily
 * keys the wbi signature is built from. Both are fetched once per run and kept in memory — the
 * app has no account to log in with, and nothing here is tied to a person.
 */
final class BiliAuth {
    /** How long the signing keys are trusted. They rotate daily, so an hour is safely inside one. */
    private static final long KEY_MILLIS=60*60*1000L;
    /**
     * Bilibili's own long-lived web keys, mixed into a signing key. They are the pair the site
     * publishes and the community documents, and they are the fallback used whenever {@code nav}
     * refuses to hand out the day's pair — which it does for anonymous callers for stretches at a
     * time (observed as code -101 on 2026-09-23). A refused key request must not take the whole
     * feature down: without this, signing is impossible and nothing else can be attempted.
     */
    private static final String BUILT_IN=Wbi.mixinKey("7cd084941338484aae1ad9425b84077c","4932caff0ff746eab6f01bf08b70ac45");
    private static String cookie,mixin,mixinFrom="";
    private static long mixinAt;

    private BiliAuth(){}

    /** The fingerprint Bilibili hands out to anyone who asks, plus the ids the web client makes up. */
    static synchronized String cookie()throws IOException{
        if(cookie==null||cookie.isEmpty()){
            JSONObject d=BiliApi.request("https://api.bilibili.com/x/frontend/finger/spi","https://live.bilibili.com/",null);
            String b3=d.optString("b_3",""),b4=d.optString("b_4","");
            if(b3.isEmpty())throw new IOException("B 站没有发放设备标识");
            cookie="buvid3="+b3+"; buvid4="+b4+"; b_nut="+(System.currentTimeMillis()/1000L)
                +"; _uuid="+random(8)+"-"+random(4)+"-"+random(4)+"; b_lsid="+random(8)+random(5);
        }
        return cookie;
    }

    /** The fingerprint, or an empty string: the danmaku interface answers without one, so a
     *  refused fingerprint must not stop the connection from being attempted at all. */
    static synchronized String cookieOrEmpty(){
        try{return cookie();}catch(Exception e){return "";}
    }

    /**
     * The signing key: the day's pair from {@code nav} when it answers, the built-in pair when it
     * does not. {@link #mixinSource()} says which one is in use, so the on-device probe can show it.
     */
    static synchronized String mixin(){
        if(mixin!=null&&!mixin.isEmpty()&&System.currentTimeMillis()-mixinAt<=KEY_MILLIS)return mixin;
        if(mixin==null||mixin.isEmpty()||mixinFrom.equals("nav")){
            try{
                JSONObject d=BiliApi.request("https://api.bilibili.com/x/web-interface/nav","https://live.bilibili.com/",cookieOrEmpty());
                JSONObject img=d.optJSONObject("wbi_img");
                if(img!=null){
                    String fresh=Wbi.mixinKey(part(img.optString("img_url","")),part(img.optString("sub_url","")));
                    if(!fresh.isEmpty()){mixin=fresh;mixinFrom="nav";mixinAt=System.currentTimeMillis();}
                }
            }catch(Exception ignored){
                // Expected often enough to be the normal path; the built-in pair below is the answer.
            }
        }
        if(mixin==null||mixin.isEmpty()||System.currentTimeMillis()-mixinAt>KEY_MILLIS){
            mixin=BUILT_IN;
            mixinFrom="内置";
            mixinAt=System.currentTimeMillis();
        }
        return mixin;
    }

    /** Where the key in use came from: "nav" or "内置". Only the probe reads this. */
    static synchronized String mixinSource(){return mixinFrom.isEmpty()?"未取":mixinFrom;}

    /** Drop the keys after a refusal, so the next attempt fetches fresh ones. */
    static synchronized void forget(){mixin=null;}

    /** The key out of ".../bfs/wbi/7cd084941338484aae1ad9425b84077c.png" is the file name. */
    private static String part(String address){
        int cut=address.lastIndexOf('/');
        String name=cut<0?address:address.substring(cut+1);
        int dot=name.indexOf('.');
        return dot<0?name:name.substring(0,dot);
    }

    private static String random(int length){
        String chars="ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random generator=new Random();
        StringBuilder out=new StringBuilder();
        for(int i=0;i<length;i++)out.append(chars.charAt(generator.nextInt(chars.length())));
        return out.toString();
    }
}

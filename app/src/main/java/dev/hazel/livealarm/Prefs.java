package dev.hazel.livealarm;

import android.content.*;
import org.json.*;
import java.util.*;
import java.time.*;

public final class Prefs {
    private final SharedPreferences db;
    public Prefs(Context c){db=c.getSharedPreferences("hazel",Context.MODE_PRIVATE);}
    public SharedPreferences raw(){return db;}
    public static void put(JSONObject j,String key,Object value){try{j.put(key,value);}catch(JSONException e){throw new IllegalArgumentException(e);}}
    public static JSONObject obj(String s){try{return new JSONObject(s);}catch(Exception e){return new JSONObject();}}
    public static JSONArray array(String s){try{return new JSONArray(s);}catch(Exception e){return new JSONArray();}}
    public JSONObject defaults(){
        JSONObject j=new JSONObject();
        put(j,"soundWithoutNotifications",false); put(j,"allDay",true); put(j,"timezone","device"); put(j,"catchUp",false);
        put(j,"pollSeconds",30); put(j,"reliable",true); put(j,"boot",true);
        put(j,"ringtone","starlight"); put(j,"customName",db.getString("customName","未选择"));
        put(j,"volume",85); put(j,"ramp",true); put(j,"vibrate",true); put(j,"duration",60);
        put(j,"snoozeMinutes",5); put(j,"quietCalls",true); put(j,"theme","light");
        put(j,"preStream",true); put(j,"ringQueue",false); put(j,"aiOcr",true); put(j,"aiKey",""); put(j,"aiModel","deepseek-flash");
        put(j,"seedColor",""); put(j,"amoled",false); put(j,"hideRecents",false); put(j,"recovery",true); put(j,"backgroundDim",40); put(j,"cardOpacity",94);
                JSONArray rules=new JSONArray(); JSONObject w=new JSONObject();
        put(w,"id","night");put(w,"name","凌晨守候");put(w,"start",60);put(w,"end",360);put(w,"days",127);put(w,"enabled",true);
        rules.put(w);put(j,"windows",rules);return j;
    }
    public synchronized JSONObject config(){
        JSONObject base=defaults(), saved=obj(db.getString("config","{}"));
        Iterator<String> it=saved.keys();while(it.hasNext()){String k=it.next();put(base,k,saved.opt(k));}
        put(base,"customName",db.getString("customName","未选择"));return base;
    }
    public synchronized void update(JSONObject patch) throws JSONException {
        JSONObject j=config();
        String[] bools={"allDay","catchUp","reliable","boot","ramp","vibrate","quietCalls","soundWithoutNotifications","aiOcr","preStream","ringQueue","amoled","hideRecents","recovery"};
        for(String k:bools) if(patch.has(k)){if(!(patch.get(k) instanceof Boolean))throw new JSONException("开关值无效");put(j,k,patch.getBoolean(k));}
        intSetting(j,patch,"volume",1,100);intSetting(j,patch,"backgroundDim",0,90);intSetting(j,patch,"cardOpacity",75,100);
        if(patch.has("seedColor")){String color=patch.getString("seedColor").trim();if(!color.isEmpty()&&!color.matches("#[0-9a-fA-F]{6}"))throw new JSONException("请输入六位 HEX 颜色，如 #536B81");put(j,"seedColor",color.toUpperCase(java.util.Locale.ROOT));}
        choiceInt(j,patch,"pollSeconds",new int[]{15,30,60,120});
                choiceInt(j,patch,"duration",new int[]{15,30,60,120,300});choiceInt(j,patch,"snoozeMinutes",new int[]{3,5,10,15});
        if(patch.has("timezone")){String zone=patch.getString("timezone");try{TimeRules.zone(zone);}catch(Exception e){throw new JSONException("时区无效");}put(j,"timezone",zone);}
        if(patch.has("aiKey")){String v=patch.getString("aiKey").trim();put(j,"aiKey",v.length()>300?"":v);}
        if(patch.has("aiModel")){String v=patch.getString("aiModel").trim();if(v.isEmpty()||v.length()>80)throw new JSONException("模型名无效");put(j,"aiModel",v);}
        if(patch.has("theme")){String v=patch.getString("theme");if(!Arrays.asList("light","dark","system").contains(v))throw new JSONException("主题无效");put(j,"theme",v);}
                if(patch.has("ringtone")){String v=patch.getString("ringtone");if(!Arrays.asList("starlight","morning","urgent","system","custom").contains(v))throw new JSONException("铃声无效");if("custom".equals(v)&&db.getString("customPath","").isEmpty())throw new JSONException("请先导入音频文件");put(j,"ringtone",v);}
        if(patch.has("windows")){
            JSONArray a=patch.getJSONArray("windows");if(a.length()>32)throw new JSONException("最多支持 32 个时段");
            JSONArray cleaned=new JSONArray();Set<String> ids=new HashSet<>();
            for(int i=0;i<a.length();i++){
                JSONObject w=a.getJSONObject(i);int start=w.getInt("start"),end=w.getInt("end"),days=w.getInt("days");
                String id=w.optString("id",UUID.randomUUID().toString()),name=w.optString("name","自定义时段");
                if(id.length()>80||ids.contains(id))throw new JSONException("时段编号无效");ids.add(id);
                if(name.length()>24)throw new JSONException("时段名称最多 24 个字");
                try{new TimeRules.Window(id,name,start,end,days,w.optBoolean("enabled",true));}catch(Exception e){throw new JSONException(e.getMessage());}
                JSONObject out=new JSONObject();put(out,"id",id);put(out,"name",name);put(out,"start",start);put(out,"end",end);put(out,"days",days);put(out,"enabled",w.optBoolean("enabled",true));cleaned.put(out);
            }
            put(j,"windows",cleaned);
        }
        if(!j.optBoolean("allDay")){
            boolean any=false;JSONArray a=j.optJSONArray("windows");for(int i=0;i<a.length();i++)if(a.optJSONObject(i).optBoolean("enabled"))any=true;
            if(!any)throw new JSONException("请至少启用一个提醒时段，或选择全天提醒");
        }
        db.edit().putString("config",j.toString()).commit();
    }
    private void intSetting(JSONObject j,JSONObject p,String k,int min,int max)throws JSONException{if(p.has(k)){int v=p.getInt(k);if(v<min||v>max)throw new JSONException("数值超出范围");put(j,k,v);}}
    private void choiceInt(JSONObject j,JSONObject p,String k,int[] choices)throws JSONException{if(p.has(k)){int v=p.getInt(k);for(int a:choices)if(a==v){put(j,k,v);return;}throw new JSONException("选项无效");}}
    public List<TimeRules.Window> windows(JSONObject config){
        ArrayList<TimeRules.Window> list=new ArrayList<>();JSONArray a=config.optJSONArray("windows");if(a==null)return list;
        for(int i=0;i<a.length();i++){JSONObject w=a.optJSONObject(i);try{list.add(new TimeRules.Window(w.optString("id"),w.optString("name"),w.optInt("start"),w.optInt("end"),w.optInt("days"),w.optBoolean("enabled")));}catch(Exception ignored){}}
        return list;
    }
    public boolean allowed(long now){JSONObject c=config();return TimeRules.contains(now,c.optBoolean("allDay"),windows(c),TimeRules.zone(c.optString("timezone")));}
    public boolean enabled(){return db.getBoolean("enabled",false);}
    public synchronized void setEnabled(boolean value){
        SharedPreferences.Editor e=db.edit().putBoolean("enabled",value);
        if(value&&!enabled()){e.putLong("armedAt",System.currentTimeMillis());e.putString("gates",freshBaselines().toString());}
        e.commit();
    }

    // ---- anchors: the one place the streamer identity is stored ----

    /** Read path: stored data is repaired rather than trusted, so a bad entry cannot stop the watch. */
    public List<Anchors.Anchor> anchors(){return Anchors.sanitize(readAnchors(db.getString("anchors","")));}
    private static List<Anchors.Anchor> readAnchors(String text){
        ArrayList<Anchors.Anchor> list=new ArrayList<>();
        if(text==null||text.isEmpty())return list;
        try{
            JSONArray a=new JSONArray(text);
            for(int i=0;i<a.length();i++){
                JSONObject o=a.optJSONObject(i);if(o==null)continue;
                list.add(new Anchors.Anchor(o.optString("id"),o.optString("name"),o.optLong("uid"),o.optLong("room"),o.optBoolean("enabled",true),o.optBoolean("alarm",true)));
            }
        }catch(Exception e){return new ArrayList<>();}
        return list;
    }
    public synchronized void saveAnchors(List<Anchors.Anchor> list){
        Anchors.validate(list);
        JSONArray a=new JSONArray();
        for(Anchors.Anchor x:list){JSONObject o=new JSONObject();put(o,"id",x.id);put(o,"name",x.name);put(o,"uid",x.uid);put(o,"room",x.room);put(o,"enabled",x.enabled);put(o,"alarm",x.alarm);a.put(o);}
        db.edit().putString("anchors",a.toString()).commit();
    }
    public Anchors.Anchor anchor(String id){return Anchors.find(anchors(),id);}
    /**
     * The moment this anchor joined the watch. One added while the watch was already running
     * gets its own later moment, so a stream that began before it was added is not a new start.
     */
    public long armedAt(String id){
        // A missing value must not be filled in with "now": every stream that is already running
        // would then look like it began before the watch was armed, and with catch-up off
        // (already_live) the app would stop ringing at all — silently, since this is the one
        // comparison whose failure mode is a missed alarm. Zero means "armed since forever",
        // which can only ever produce an extra alarm, never a missing one.
        long base=db.getLong("armedAt",0);
        long own=id==null?0:db.getLong("armedAt."+id,0);
        return own>base?own:base;
    }
    /** A new anchor, or one repointed at another room, must start from a clean session.
     *  The schedule belongs to the room, so a repointed anchor loses it too. */
    public synchronized void restartAnchor(String id){
        if(id==null)return;
        JSONObject map=gates();map.remove(id);
        db.edit().putString("gates",map.toString()).remove("snapshot."+id)
          .remove("schedule."+id).remove("scheduleText."+id)
          .putLong("armedAt."+id,System.currentTimeMillis()).commit();
    }
    /** A removed anchor must not leave a session behind for a future anchor to inherit. */
    public synchronized void forget(String id){
        JSONObject map=gates();map.remove(id);
        db.edit().putString("gates",map.toString()).remove("snapshot."+id).remove("armedAt."+id)
          .remove("schedule."+id).remove("scheduleText."+id).apply();
    }

    // ---- weekly schedule per anchor (display only; never gates the alarm) ----

    public List<Schedule.Entry> schedule(String id){
        if(id==null)id=Anchors.firstId(anchors());
        return Schedule.sanitize(readSchedule(db.getString("schedule."+id,"")));
    }
    private static List<Schedule.Entry> readSchedule(String text){
        ArrayList<Schedule.Entry> list=new ArrayList<>();
        if(text==null||text.isEmpty())return list;
        try{
            JSONArray a=new JSONArray(text);
            for(int i=0;i<a.length();i++){
                JSONObject o=a.optJSONObject(i);if(o==null)continue;
                list.add(new Schedule.Entry(o.optString("id"),o.optInt("days"),o.optInt("start"),o.optInt("end",-1),o.optString("note","")));
            }
        }catch(Exception e){return new ArrayList<>();}
        return list;
    }
    public synchronized void saveSchedule(String id,List<Schedule.Entry> list){
        Schedule.validate(list);
        JSONArray a=new JSONArray();
        for(Schedule.Entry e:list){JSONObject o=new JSONObject();put(o,"id",e.id);put(o,"days",e.days);put(o,"start",e.start);put(o,"end",e.end);put(o,"note",e.note==null?"":e.note);a.put(o);}
        db.edit().putString("schedule."+id,a.toString()).commit();
    }
    /** The raw text the entries came from, kept so the user can re-edit without re-fetching. */
    public String scheduleText(String id){return db.getString("scheduleText."+id,"");}
    public synchronized void saveScheduleText(String id,String text){
        if(text==null)text="";
        db.edit().putString("scheduleText."+id,text.length()>4096?text.substring(0,4096):text).commit();
    }

    // ---- one dedup state per anchor ----

    private static JSONObject stateJson(LiveGate.State s){
        JSONObject j=new JSONObject();
        put(j,"session",s.session);put(j,"notified",s.notified);put(j,"logged",s.logged);put(j,"started",s.started);
        put(j,"firstSeen",s.firstSeen);put(j,"sourceStart",s.sourceStart);put(j,"offlineSince",s.offlineSince);
        put(j,"offlineSamples",s.offlineSamples);put(j,"baseline",s.baseline);put(j,"unknownBaseline",s.unknownBaseline);
        return j;
    }
    private static LiveGate.State state(JSONObject j){
        LiveGate.State s=new LiveGate.State();
        if(j==null)return s;
        s.session=j.optString("session");s.notified=j.optString("notified");s.logged=j.optString("logged");
        s.started=j.optLong("started");s.firstSeen=j.optLong("firstSeen");s.sourceStart=j.optLong("sourceStart");
        s.offlineSince=j.optLong("offlineSince");s.offlineSamples=j.optInt("offlineSamples");
        s.baseline=j.optBoolean("baseline",true);s.unknownBaseline=j.optBoolean("unknownBaseline");
        return s;
    }
    private JSONObject gates(){
        JSONObject map=obj(db.getString("gates","{}"));
        if(map.length()>0)return map;
        // Upgrades from the single-anchor build kept one dedup state under "gate".
        JSONObject legacy=obj(db.getString("gate","{}"));
        if(legacy.length()==0)return map;
        JSONObject migrated=new JSONObject();
        try{migrated.put(Anchors.firstId(anchors()),legacy);}catch(JSONException ignore){}
        return migrated;
    }
    private JSONObject freshBaselines(){
        JSONObject map=gates();
        for(Anchors.Anchor a:anchors()){
            LiveGate.State s=state(map.optJSONObject(a.id));s.baseline=true;
            try{map.put(a.id,stateJson(s));}catch(JSONException ignore){}
        }
        return map;
    }
    public LiveGate.State gate(String id){return state(gates().optJSONObject(id));}
    public synchronized void saveGate(String id,LiveGate.State s){
        JSONObject map=gates();
        try{map.put(id,stateJson(s));}catch(JSONException e){throw new IllegalStateException(e);}
        db.edit().putString("gates",map.toString()).commit();
    }

    // ---- last observation per anchor ----

    public JSONObject snapshot(String id){
        if(id==null)id=Anchors.firstId(anchors());
        String text=db.getString("snapshot."+id,null);
        if(text==null&&id.equals(Anchors.firstId(anchors())))text=db.getString("snapshot",null);
        return obj(text==null?"{}":text);
    }
    public void saveSnapshot(String id,JSONObject snapshot){db.edit().putString("snapshot."+id,snapshot.toString()).apply();}
    public void clearSnapshot(String id){db.edit().remove("snapshot."+id).apply();}

    // ---- snooze remembers which anchor it belongs to ----

    public void keepSnooze(String anchorId,String session,long at){
        db.edit().putLong("snoozeAt",at).putString("snoozeSession",(anchorId==null?"":anchorId)+"|"+(session==null?"":session)).commit();
    }
    public String snoozeAnchorId(){
        String raw=db.getString("snoozeSession","");
        int cut=raw.indexOf('|');
        // A pre-upgrade value held a bare session and belonged to the only anchor there was.
        if(cut<0)return raw.isEmpty()?"":Anchors.firstId(anchors());
        String id=raw.substring(0,cut);
        return id.isEmpty()?Anchors.firstId(anchors()):id;
    }
    public String snoozeSessionId(){
        String raw=db.getString("snoozeSession","");
        int cut=raw.indexOf('|');
        return cut<0?raw:raw.substring(cut+1);
    }
    public void clearSnoozeKeys(){db.edit().putLong("snoozeAt",0).putString("snoozeSession","").apply();}

    // ---- per-anchor stream history for the statistics shown on the anchor cards ----

    /** A new detected session opens a record; at most the latest 60 are kept per anchor. */
    public synchronized void statSession(String id,long start){
        JSONArray a=array(db.getString("stats."+id,"[]"));
        JSONObject r=new JSONObject();put(r,"s",start);put(r,"e",0);a.put(r);
        JSONArray keep=new JSONArray();
        for(int i=Math.max(0,a.length()-60);i<a.length();i++)keep.put(a.opt(i));
        db.edit().putString("stats."+id,keep.toString()).apply();
    }
    /** Detection of the stream having ended closes the newest open record. */
    public synchronized void closeStatSession(String id){
        JSONArray a=array(db.getString("stats."+id,"[]"));
        for(int i=a.length()-1;i>=0;i--){
            JSONObject r=a.optJSONObject(i);
            if(r!=null&&r.optLong("e",0)==0){put(r,"e",System.currentTimeMillis());db.edit().putString("stats."+id,a.toString()).apply();return;}
        }
    }
    public synchronized void log(String type,String title,String detail){
        JSONArray old=array(db.getString("history","[]")),a=new JSONArray();JSONObject entry=new JSONObject();
        put(entry,"at",System.currentTimeMillis());put(entry,"type",type);put(entry,"title",title);put(entry,"detail",detail);a.put(entry);
        for(int i=0;i<Math.min(199,old.length());i++)a.put(old.opt(i));db.edit().putString("history",a.toString()).apply();
    }
    public JSONArray history(){return array(db.getString("history","[]"));}
    public void clearHistory(){db.edit().putString("history","[]").apply();}
}

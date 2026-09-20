package dev.hazel.livealarm;

import java.util.*;

/**
 * Anchor list model, validation and repair. Pure Java: no Android and no org.json,
 * so it stays inside the core-tests compile set with TimeRules and LiveGate.
 */
public final class Anchors {
    public static final int MAX=16, MAX_NAME=40, MAX_ID=80;
    public static final String DEFAULT_ID="hazel";
    public static final String DEFAULT_NAME="灰泽满 Hazel";
    public static final long DEFAULT_UID=1298779265L, DEFAULT_ROOM=1713546334L;

    /**
     * The uid is carried in two shapes on purpose. {@code uid} is the long that lookups and the
     * storage format have always used; {@code uidText} is the same number as decimal text, which
     * is the only shape the web interface can read back without loss — JavaScript numbers stop
     * being exact at 2^53-1 (9007199254740991) and Bilibili issues 16-digit uids up to
     * 9999999999999999. Both are derived in one place so the two copies cannot disagree.
     */
    public static final class Anchor {
        public final String id, name;
        public final long uid, room;
        /** The uid as decimal text: exact where {@code uid} cannot survive a JSON round trip. */
        public final String uidText;
        public final boolean enabled;
        /** Silencing the bell keeps detection, the timeline and the records running. */
        public final boolean alarm;
        public Anchor(String id, String name, long uid, long room, boolean enabled) { this(id, name, uid, room, enabled, true); }
        public Anchor(String id, String name, long uid, long room, boolean enabled, boolean alarm) {
            this(id, name, uid, room, enabled, alarm, null);
        }
        /** For a caller that holds the uid only as text, which the web interface always does. */
        public Anchor(String id, String name, String uidText, long room, boolean enabled, boolean alarm) {
            this(id, name, parseUid(uidText), room, enabled, alarm, uidText);
        }
        private Anchor(String id, String name, long uid, long room, boolean enabled, boolean alarm, String uidText) {
            this.id=id; this.name=name; this.uid=uid; this.room=room; this.enabled=enabled; this.alarm=alarm;
            // Text like "007" or "+12" would leave the two copies disagreeing, so the long wins
            // whenever it is usable and the raw text is only kept when there is no long at all.
            this.uidText = uid>0 ? Long.toString(uid) : (uidText==null ? "" : uidText.trim());
        }
        public Anchor withEnabled(boolean value) { return new Anchor(id, name, uid, room, value, alarm, uidText); }
        public Anchor withAlarm(boolean value) { return new Anchor(id, name, uid, room, enabled, value, uidText); }
    }

    /**
     * Parse a uid that arrived as text. Blank or non-numeric reads as 0, meaning "not set".
     * A number too large for a long is clamped to the maximum rather than wrapped: a uid must
     * never come out of here as a different account than the one that was typed.
     */
    public static long parseUid(String text) {
        if (text==null) return 0;
        String trimmed=text.trim();
        if (trimmed.isEmpty()) return 0;
        for (int i=0;i<trimmed.length();i++) if (trimmed.charAt(i)<'0'||trimmed.charAt(i)>'9') return 0;
        try { return Long.parseLong(trimmed); } catch (NumberFormatException e) { return Long.MAX_VALUE; }
    }


    private Anchors() {}

    /** The single-anchor install this app shipped before multi-anchor support. */
    public static List<Anchor> defaults() {
        ArrayList<Anchor> list=new ArrayList<Anchor>();
        list.add(new Anchor(DEFAULT_ID, DEFAULT_NAME, DEFAULT_UID, DEFAULT_ROOM, true));
        return list;
    }

    /** Strict rules for writes that came from the user. Throws a message the UI can show. */
    public static List<Anchor> validate(List<Anchor> list) {
        if (list==null||list.isEmpty()) throw new IllegalArgumentException("请至少保留一个主播");
        if (list.size()>MAX) throw new IllegalArgumentException("最多支持 "+MAX+" 个主播");
        Set<String> ids=new HashSet<String>();
        boolean anyEnabled=false;
        for (Anchor a:list) {
            if (a==null||!validId(a.id)||ids.contains(a.id)) throw new IllegalArgumentException("主播编号无效");
            if (a.name==null||a.name.trim().isEmpty()) throw new IllegalArgumentException("请填写主播名称");
            if (a.name.length()>MAX_NAME) throw new IllegalArgumentException("主播名称最多 "+MAX_NAME+" 个字");
            if (a.uid<=0||a.room<=0) throw new IllegalArgumentException("UID 与房间号必须为正数");
            if (a.enabled) anyEnabled=true;
            ids.add(a.id);
        }
        // Mirrors the existing rule for reminder windows: an armed watch needs something to watch.
        if (!anyEnabled) throw new IllegalArgumentException("请至少启用一个主播");
        return list;
    }

    /**
     * Tolerant repair for data read back from storage. Never throws: a single bad entry
     * must not take the watch down, and an unusable list falls back to the default anchor.
     */
    public static List<Anchor> sanitize(List<Anchor> raw) {
        ArrayList<Anchor> out=new ArrayList<Anchor>();
        if (raw!=null) {
            Set<String> ids=new HashSet<String>();
            for (Anchor a:raw) {
                if (a==null||!validId(a.id)||ids.contains(a.id)) continue;
                if (a.name==null||a.name.trim().isEmpty()||a.name.length()>MAX_NAME) continue;
                if (a.uid<=0||a.room<=0) continue;
                ids.add(a.id); out.add(a);
                if (out.size()==MAX) break;
            }
        }
        return out.isEmpty()?defaults():out;
    }

    /** Ids address storage keys and avatar files, so keep them path-safe on purpose. */
    public static boolean validId(String id) {
        if (id==null||id.isEmpty()||id.length()>MAX_ID) return false;
        for (int i=0;i<id.length();i++) {
            char c=id.charAt(i);
            boolean ok=(c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='-'||c=='_';
            if (!ok) return false;
        }
        return true;
    }

    public static Anchor find(List<Anchor> list, String id) {
        int at=findIndex(list, id);
        return at<0?null:list.get(at);
    }

    public static int findIndex(List<Anchor> list, String id) {
        if (list==null||id==null) return -1;
        for (int i=0;i<list.size();i++) {
            Anchor a=list.get(i);
            if (a!=null&&id.equals(a.id)) return i;
        }
        return -1;
    }

    /** The id the single-anchor code paths fall back to when none was supplied. */
    public static String firstId(List<Anchor> list) {
        return list==null||list.isEmpty()?DEFAULT_ID:list.get(0).id;
    }

    /** Shorten a name resolved from Bilibili so it fits the stored limit and the alarm card. */
    public static String fitName(String name) {
        if (name==null) return "";
        String trimmed=name.trim();
        return trimmed.length()<=MAX_NAME?trimmed:trimmed.substring(0,MAX_NAME);
    }
}

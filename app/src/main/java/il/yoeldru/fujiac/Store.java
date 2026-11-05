// נכתב: 2026-11-05 07:54 (שעון ישראל) · עודכן: 2026-11-05 22:46 - כמה מזגנים, קבוצות, יצוא/יבוא הגדרות
// שמירת הגדרות ונתונים מקומית בטלפון (SharedPreferences + JSON).
package il.yoeldru.fujiac;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Store {
    private final SharedPreferences p;

    public static final String FAVORITES = "favorites";
    public static final String LEARNED = "learned";
    public static final String SCHEDULES = "schedules";
    private static final String ACS = "acs";
    public static final String GROUPS = "groups";

    public Store(Context c) {
        p = c.getApplicationContext().getSharedPreferences("fujiac", Context.MODE_PRIVATE);
        if (!p.contains(ACS)) {
            // יצירת המזגן הראשון
            AcConfig a = new AcConfig();
            a.id = nextId();
            a.name = "מזגן 1";
            JSONArray arr = new JSONArray();
            arr.put(a.toJson());
            p.edit().putString(ACS, arr.toString()).putInt("current_ac", a.id).apply();
        }
    }

    // ----- מזגנים -----
    public List<AcConfig> acs() {
        List<AcConfig> out = new ArrayList<>();
        JSONArray a = list(ACS);
        for (int i = 0; i < a.length(); i++) out.add(AcConfig.fromJson(a.optJSONObject(i)));
        return out;
    }

    public AcConfig ac(int id) {
        for (AcConfig a : acs()) if (a.id == id) return a;
        return null;
    }

    public AcConfig current() {
        AcConfig a = ac(p.getInt("current_ac", -1));
        if (a == null) {
            a = acs().get(0);
            setCurrent(a.id);
        }
        return a;
    }

    public void setCurrent(int id) { p.edit().putInt("current_ac", id).apply(); }

    public void saveAc(AcConfig ac) {
        JSONArray a = list(ACS);
        boolean found = false;
        for (int i = 0; i < a.length(); i++) {
            if (a.optJSONObject(i).optInt("id") == ac.id) {
                try { a.put(i, ac.toJson()); } catch (Exception ignored) {}
                found = true;
            }
        }
        if (!found) a.put(ac.toJson());
        saveList(ACS, a);
    }

    public AcConfig addAc() {
        AcConfig a = new AcConfig();
        a.id = nextId();
        a.name = "מזגן " + (acs().size() + 1);
        AcConfig cur = current();
        a.model = cur.model;
        saveAc(a);
        setCurrent(a.id);
        return a;
    }

    /** מוחק מזגן (אם יש יותר מאחד) כולל התזמונים שלו. */
    public boolean deleteAc(int id) {
        JSONArray a = list(ACS);
        if (a.length() <= 1) return false;
        for (int i = a.length() - 1; i >= 0; i--) if (a.optJSONObject(i).optInt("id") == id) a.remove(i);
        saveList(ACS, a);
        if (p.getInt("current_ac", -1) == id) setCurrent(a.optJSONObject(0).optInt("id"));
        return true;
    }

    public void saveState(int acId, AcState s) {
        AcConfig a = ac(acId);
        if (a == null) return;
        a.state = s;
        saveAc(a);
    }

    // ----- קבוצות -----
    // קבוצה: {id, name, acs:[ids]}. המסך הראשי שולט בכל חברי הקבוצה יחד.
    public JSONArray groups() { return list(GROUPS); }

    public JSONObject group(int id) {
        JSONArray g = groups();
        for (int i = 0; i < g.length(); i++) if (g.optJSONObject(i).optInt("id") == id) return g.optJSONObject(i);
        return null;
    }

    /** חברי הקבוצה שעדיין קיימים. */
    public List<AcConfig> members(JSONObject group) {
        List<AcConfig> out = new ArrayList<>();
        if (group == null) return out;
        JSONArray ids = group.optJSONArray("acs");
        if (ids == null) return out;
        for (int i = 0; i < ids.length(); i++) {
            AcConfig a = ac(ids.optInt(i));
            if (a != null) out.add(a);
        }
        return out;
    }

    public void saveGroup(JSONObject g) {
        JSONArray a = groups();
        boolean found = false;
        for (int i = 0; i < a.length(); i++) {
            if (a.optJSONObject(i).optInt("id") == g.optInt("id")) {
                try { a.put(i, g); } catch (Exception ignored) {}
                found = true;
            }
        }
        if (!found) a.put(g);
        saveList(GROUPS, a);
    }

    public void deleteGroup(int id) {
        JSONArray a = groups();
        for (int i = a.length() - 1; i >= 0; i--) if (a.optJSONObject(i).optInt("id") == id) a.remove(i);
        saveList(GROUPS, a);
        if (currentGroup() == id) setCurrentGroup(0);
    }

    /** הקבוצה שנבחרה במסך הראשי (0 = מזגן בודד). */
    public int currentGroup() { return p.getInt("current_group", 0); }
    public void setCurrentGroup(int id) { p.edit().putInt("current_group", id).apply(); }

    // ----- רשימות (מועדפים, כפתורים נלמדים, תזמונים) -----
    public JSONArray list(String key) {
        try { return new JSONArray(p.getString(key, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
    public void saveList(String key, JSONArray a) { p.edit().putString(key, a.toString()).apply(); }

    // ----- יצוא / יבוא -----
    public static final String EXPORT_PREFIX = "FUJIAC1:";

    /** כל ההגדרות כ-JSON: מזגנים (כולל השלט של כל אחד), קבוצות, מועדפים, כפתורים נלמדים ותזמונים. */
    public JSONObject exportAll(boolean withSchedules) {
        JSONObject o = new JSONObject();
        try {
            o.put("app", "fujiac");
            o.put("version", 1);
            o.put("acs", list(ACS));
            o.put("groups", list(GROUPS));
            o.put("favorites", list(FAVORITES));
            o.put("learned", list(LEARNED));
            if (withSchedules) o.put("schedules", list(SCHEDULES));
            o.put("next_id", p.getInt("next_id", 1));
        } catch (Exception ignored) {}
        return o;
    }

    /** קוד טקסט קצר לשליחה בוואטסאפ: קידומת + JSON מכווץ ב-Base64. */
    public String exportCode(boolean withSchedules) {
        try {
            byte[] raw = exportAll(withSchedules).toString().getBytes("UTF-8");
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bos)) { gz.write(raw); }
            return EXPORT_PREFIX + android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    /** מפענח קוד יצוא או JSON רגיל (מקובץ). זורק שגיאה בעברית אם לא תקין. */
    public static JSONObject decode(String text) throws Exception {
        String t = text == null ? "" : text.trim();
        int i = t.indexOf(EXPORT_PREFIX);
        if (i >= 0) {
            String b64 = t.substring(i + EXPORT_PREFIX.length()).split("\\s")[0];
            byte[] gz = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPInputStream in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            t = out.toString("UTF-8");
        }
        JSONObject o;
        try { o = new JSONObject(t); } catch (Exception e) { throw new Exception("זה לא נראה כמו קובץ/קוד הגדרות של האפליקציה"); }
        if (!"fujiac".equals(o.optString("app"))) throw new Exception("זה לא קובץ הגדרות של שלט פוג'יטסו");
        return o;
    }

    /** מחליף את כל ההגדרות בטלפון בהגדרות המיובאות. */
    public void importAll(JSONObject o) {
        SharedPreferences.Editor e = p.edit();
        JSONArray acs = o.optJSONArray("acs");
        if (acs != null && acs.length() > 0) e.putString(ACS, acs.toString());
        for (String k : new String[]{GROUPS, FAVORITES, LEARNED, SCHEDULES}) {
            JSONArray a = o.optJSONArray(k);
            if (a != null) e.putString(k, a.toString());
        }
        e.putInt("next_id", Math.max(p.getInt("next_id", 1), o.optInt("next_id", 1)) + 1);
        e.putInt("current_group", 0);
        if (acs != null && acs.length() > 0) e.putInt("current_ac", acs.optJSONObject(0).optInt("id"));
        e.apply();
    }

    public int nextId() {
        int id = p.getInt("next_id", 1);
        p.edit().putInt("next_id", id + 1).apply();
        return id;
    }
}

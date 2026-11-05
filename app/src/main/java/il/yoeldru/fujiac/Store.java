// נכתב: 2026-11-05 07:54 (שעון ישראל) · עודכן: 2026-11-05 10:30 - תמיכה בכמה מזגנים
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

    // ----- רשימות (מועדפים, כפתורים נלמדים, תזמונים) -----
    public JSONArray list(String key) {
        try { return new JSONArray(p.getString(key, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }
    public void saveList(String key, JSONArray a) { p.edit().putString(key, a.toString()).apply(); }

    public int nextId() {
        int id = p.getInt("next_id", 1);
        p.edit().putInt("next_id", id + 1).apply();
        return id;
    }
}

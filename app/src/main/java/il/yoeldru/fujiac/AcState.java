// נכתב: 2026-11-05 07:54 (שעון ישראל)
// מצב המזגן כפי שהאפליקציה זוכרת אותו (שלט IR הוא חד-כיווני, אין קריאה מהמזגן).
package il.yoeldru.fujiac;

import org.json.JSONObject;

import il.yoeldru.fujiac.core.FujitsuIr;

public class AcState {
    public boolean power = false;
    public int temp = 24;
    public int mode = FujitsuIr.MODE_COOL;
    public int fan = FujitsuIr.FAN_AUTO;
    public int swing = FujitsuIr.SWING_OFF;

    public AcState copy() {
        AcState s = new AcState();
        s.power = power; s.temp = temp; s.mode = mode; s.fan = fan; s.swing = swing;
        return s;
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("power", power); o.put("temp", temp); o.put("mode", mode);
            o.put("fan", fan); o.put("swing", swing);
        } catch (Exception ignored) {}
        return o;
    }

    public static AcState fromJson(JSONObject o) {
        AcState s = new AcState();
        if (o == null) return s;
        s.power = o.optBoolean("power", false);
        s.temp = o.optInt("temp", 24);
        s.mode = o.optInt("mode", FujitsuIr.MODE_COOL);
        s.fan = o.optInt("fan", FujitsuIr.FAN_AUTO);
        s.swing = o.optInt("swing", FujitsuIr.SWING_OFF);
        return s;
    }

    public static final String[] MODE_NAMES = {"אוטומטי", "קירור", "ייבוש", "מאוורר", "חימום"};
    public static final String[] MODE_ICONS = {"🔄", "❄️", "💧", "🌀", "☀️"};
    public static final String[] FAN_NAMES = {"אוטומטי", "חזק", "בינוני", "חלש", "שקט"};
    public static final String[] SWING_NAMES = {"קבוע", "למעלה-למטה", "לצדדים", "הכל"};

    public String summary() {
        if (!power) return "כבוי";
        return temp + "° · " + MODE_NAMES[mode] + " · מאוורר " + FAN_NAMES[fan] + " · נדנוד " + SWING_NAMES[swing];
    }
}

// נכתב: 2026-11-05 07:54 (שעון ישראל) · עודכן: 2026-11-08 00:35 - תזמון אחד עם שעת הדלקה ו/או שעת כיבוי
// ניהול תזמונים: חישוב המועד הבא וקביעת התראה מדויקת (AlarmManager) לכל חלק (הדלקה / כיבוי).
package il.yoeldru.fujiac;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

public final class Scheduler {
    private Scheduler() {}

    // days: ביטמסק, ביט 0 = ראשון ... ביט 6 = שבת. 0 = חד-פעמי.
    public static final String[] DAY_NAMES = {"א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳"};
    public static final String ON = "on", OFF = "off";

    /**
     * מבנה תזמון: onOn/onHour/onMinute (הדלקה), offOn/offHour/offMinute (כיבוי), days, enabled,
     * temp/mode/fan/swing (הגדרות ההדלקה), acId/groupId (יעד), done_on/done_off (לחד-פעמי).
     * ממיר גם תזמונים בפורמט הישן (action + hour/minute).
     */
    public static JSONObject normalize(JSONObject s) {
        if (s == null || s.has("onOn") || s.has("offOn")) return s;
        try {
            boolean off = "off".equals(s.optString("action"));
            s.put(off ? "offOn" : "onOn", true);
            s.put(off ? "onOn" : "offOn", false);
            s.put(off ? "offHour" : "onHour", s.optInt("hour"));
            s.put(off ? "offMinute" : "onMinute", s.optInt("minute"));
            s.remove("action"); s.remove("hour"); s.remove("minute");
        } catch (Exception ignored) {}
        return s;
    }

    public static boolean partOn(JSONObject s, String part) { return s.optBoolean(part + "On", false); }
    public static int hour(JSONObject s, String part) { return s.optInt(part + "Hour", part.equals(ON) ? 7 : 8); }
    public static int minute(JSONObject s, String part) { return s.optInt(part + "Minute", 0); }

    public static long nextTrigger(int hour, int minute, int days, long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        for (int i = 0; i < 8; i++) {
            if (c.getTimeInMillis() > now) {
                int dow = c.get(Calendar.DAY_OF_WEEK) - 1; // 0=ראשון
                if (days == 0 || (days & (1 << dow)) != 0) return c.getTimeInMillis();
            }
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return -1;
    }

    private static PendingIntent pi(Context c, int id, String part) {
        Intent i = new Intent(c, ScheduleReceiver.class);
        i.setAction("il.yoeldru.fujiac.SCHEDULE." + part);
        i.putExtra("id", id);
        i.putExtra("part", part);
        int code = id * 2 + (OFF.equals(part) ? 1 : 0);
        return PendingIntent.getBroadcast(c, code, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** ביטול גם של התראה בפורמט הישן (קוד בקשה = id). */
    private static void cancelLegacy(AlarmManager am, Context c, int id) {
        Intent i = new Intent(c, ScheduleReceiver.class);
        i.setAction("il.yoeldru.fujiac.SCHEDULE");
        i.putExtra("id", id);
        am.cancel(PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    public static void apply(Context c, JSONObject s) {
        normalize(s);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        int id = s.optInt("id");
        cancel(c, id);
        if (!s.optBoolean("enabled", true)) return;
        int days = s.optInt("days");
        for (String part : new String[]{ON, OFF}) {
            if (!partOn(s, part)) continue;
            if (days == 0 && s.optBoolean("done_" + part)) continue;
            long t = nextTrigger(hour(s, part), minute(s, part), days, System.currentTimeMillis());
            if (t < 0) continue;
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(c, id, part));
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(c, id, part));
            }
        }
    }

    public static void cancel(Context c, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi(c, id, ON));
        am.cancel(pi(c, id, OFF));
        cancelLegacy(am, c, id);
    }

    public static void applyAll(Context c) {
        Store st = new Store(c);
        JSONArray a = st.list(Store.SCHEDULES);
        for (int i = 0; i < a.length(); i++) apply(c, a.optJSONObject(i));
        st.saveList(Store.SCHEDULES, a);  // שומר את ההמרה מהפורמט הישן
    }

    public static String time(JSONObject s, String part) {
        return String.format("%02d:%02d", hour(s, part), minute(s, part));
    }

    public static String describe(JSONObject s, String acName) {
        normalize(s);
        int days = s.optInt("days");
        String when;
        if (days == 0) when = "פעם אחת";
        else if (days == 0x7F) when = "כל יום";
        else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 7; i++) if ((days & (1 << i)) != 0) sb.append(DAY_NAMES[i]).append(' ');
            when = sb.toString().trim();
        }
        StringBuilder sb = new StringBuilder();
        if (partOn(s, ON)) sb.append("🟢 ").append(time(s, ON)).append(" הדלקה ").append(s.optInt("temp")).append("° ")
                .append(AcState.MODE_NAMES[Math.max(0, Math.min(4, s.optInt("mode")))]);
        if (partOn(s, OFF)) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("🔴 ").append(time(s, OFF)).append(" כיבוי");
        }
        return (acName == null ? "" : acName + " · ") + when + "\n" + sb;
    }
}

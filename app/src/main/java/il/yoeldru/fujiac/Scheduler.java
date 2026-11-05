// נכתב: 2026-11-05 07:54 (שעון ישראל) · עודכן: 2026-11-05 10:30
// ניהול תזמונים: חישוב המועד הבא וקביעת התראה מדויקת (AlarmManager).
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

    public static long nextTrigger(JSONObject s, long now) {
        int hour = s.optInt("hour"), minute = s.optInt("minute"), days = s.optInt("days");
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

    private static PendingIntent pi(Context c, int id) {
        Intent i = new Intent(c, ScheduleReceiver.class);
        i.setAction("il.yoeldru.fujiac.SCHEDULE");
        i.putExtra("id", id);
        return PendingIntent.getBroadcast(c, id, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void apply(Context c, JSONObject s) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        int id = s.optInt("id");
        am.cancel(pi(c, id));
        if (!s.optBoolean("enabled", true)) return;
        long t = nextTrigger(s, System.currentTimeMillis());
        if (t < 0) return;
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(c, id));
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(c, id));
        }
    }

    public static void cancel(Context c, int id) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pi(c, id));
    }

    public static void applyAll(Context c) {
        JSONArray a = new Store(c).list(Store.SCHEDULES);
        for (int i = 0; i < a.length(); i++) apply(c, a.optJSONObject(i));
    }

    public static String describe(JSONObject s, String acName) {
        String time = String.format("%02d:%02d", s.optInt("hour"), s.optInt("minute"));
        int days = s.optInt("days");
        String when;
        if (days == 0) when = "פעם אחת";
        else if (days == 0x7F) when = "כל יום";
        else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 7; i++) if ((days & (1 << i)) != 0) sb.append(DAY_NAMES[i]).append(' ');
            when = sb.toString().trim();
        }
        String action = s.optString("action").equals("off") ? "כיבוי"
                : "הדלקה " + s.optInt("temp") + "° " + AcState.MODE_NAMES[s.optInt("mode")];
        return time + " · " + when + "\n" + (acName == null ? "" : acName + ": ") + action;
    }
}

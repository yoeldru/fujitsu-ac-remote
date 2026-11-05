// נכתב: 2026-11-05 07:54 (שעון ישראל) · עודכן: 2026-11-05 10:30 - תזמון לכל מזגן
// מופעל בזמן התזמון: שולח את הפקודה למזגן וקובע את המועד הבא.
// גם מופעל אחרי הפעלה מחדש של הטלפון כדי לשחזר את התזמונים.
package il.yoeldru.fujiac;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

public class ScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Context c = ctx.getApplicationContext();
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Scheduler.applyAll(c);
            return;
        }
        int id = intent.getIntExtra("id", -1);
        Store store = new Store(c);
        JSONArray a = store.list(Store.SCHEDULES);
        JSONObject s = null;
        for (int i = 0; i < a.length(); i++)
            if (a.optJSONObject(i).optInt("id") == id) s = a.optJSONObject(i);
        if (s == null || !s.optBoolean("enabled", true)) return;

        AcConfig ac = store.ac(s.optInt("acId"));
        if (ac == null) ac = store.current();
        final AcConfig fAc = ac;
        AcState st = ac.state;
        if (s.optString("action").equals("off")) {
            st.power = false;
        } else {
            st.power = true;
            st.temp = s.optInt("temp", st.temp);
            st.mode = s.optInt("mode", st.mode);
            st.fan = s.optInt("fan", st.fan);
            st.swing = s.optInt("swing", st.swing);
        }
        store.saveState(ac.id, st);
        // בתזמון תמיד שולחים "הדלקה" מפורשת - כך המזגן יידלק גם אם הופעל/כובה בשלט הרגיל
        byte[] ir = Remote.irForState(ac, st, st.power);

        // תזמון חד-פעמי מתבטל; קבוע נקבע מחדש
        try {
            if (s.optInt("days") == 0) s.put("enabled", false);
            store.saveList(Store.SCHEDULES, a);
        } catch (Exception ignored) {}
        Scheduler.apply(c, s);

        PendingResult pr = goAsync();
        new Thread(() -> {
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    Remote.sendBlocking(fAc, ir);
                    break;
                } catch (Exception e) {
                    try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                }
            }
            pr.finish();
        }).start();
    }
}

// נכתב: 2026-11-05 07:54 (שעון ישראל) · עודכן: 2026-11-05 12:42 - תזמון למזגן או לקבוצה
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

        // יעד: קבוצה (כל החברים) או מזגן בודד
        java.util.List<AcConfig> targets = new java.util.ArrayList<>();
        if (s.optInt("groupId") != 0) targets.addAll(store.members(store.group(s.optInt("groupId"))));
        if (targets.isEmpty()) {
            AcConfig one = store.ac(s.optInt("acId"));
            if (one == null) one = store.current();
            targets.add(one);
        }
        java.util.List<byte[]> codes = new java.util.ArrayList<>();
        for (AcConfig ac : targets) {
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
            codes.add(Remote.irForState(ac, st, st.power));
        }

        // תזמון חד-פעמי מתבטל; קבוע נקבע מחדש
        try {
            if (s.optInt("days") == 0) s.put("enabled", false);
            store.saveList(Store.SCHEDULES, a);
        } catch (Exception ignored) {}
        Scheduler.apply(c, s);

        PendingResult pr = goAsync();
        new Thread(() -> {
            for (int k = 0; k < targets.size(); k++) {
                for (int attempt = 0; attempt < 3; attempt++) {
                    try {
                        Remote.sendBlocking(targets.get(k), codes.get(k));
                        break;
                    } catch (Exception e) {
                        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                    }
                }
            }
            pr.finish();
        }).start();
    }
}

// נכתב: 2026-11-05 07:54 (שעון ישראל) · עודכן: 2026-11-08 00:35 - תזמון אחד עם הדלקה ו/או כיבוי
// מסך תזמונים: הדלקה/כיבוי בשעה קבועה בימים נבחרים, וטיימר כיבוי מהיר.
package il.yoeldru.fujiac;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

public class SchedulesActivity extends Activity {
    private Store store;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        LinearLayout col = Ui.screen(this);
        col.addView(Ui.text(this, "תזמונים וטיימר", 24, Ui.TEXT, true));
        Ui.spacer(col, 6);
        col.addView(Ui.text(this, "טיימר הכיבוי פועל על מה שנבחר במסך הראשי. בתזמון חדש בוחרים מזגן או קבוצה. התזמונים פועלים מהטלפון – הוא צריך להיות בבית ומחובר ל-Wi-Fi בשעת התזמון.",
                12.5f, Ui.MUTED, false));
        Ui.spacer(col, 12);

        LinearLayout timer = Ui.card(this);
        timer.addView(Ui.label(this, "טיימר כיבוי מהיר"));
        LinearLayout tr = Ui.row(this);
        int[] mins = {30, 60, 90, 120, 180};
        String[] labels = {"½ שעה", "שעה", "1.5 ש׳", "שעתיים", "3 ש׳"};
        for (int k = 0; k < mins.length; k++) {
            final int m = mins[k];
            Button btn = Ui.soft(this, labels[k]);
            btn.setTextSize(13);
            btn.setOnClickListener(v -> offTimer(m));
            Ui.addWeighted(tr, btn, 3);
        }
        timer.addView(tr);
        col.addView(timer);

        Button add = Ui.primary(this, "＋ תזמון חדש");
        add.setOnClickListener(v -> editDialog());
        col.addView(add, new LinearLayout.LayoutParams(-1, -2));
        Ui.spacer(col, 12);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);
        render();
        checkExactPermission();
    }

    private void checkExactPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (!am.canScheduleExactAlarms()) {
                new AlertDialog.Builder(this).setTitle("הרשאה לתזמון מדויק")
                        .setMessage("כדי שהמזגן יופעל בדיוק בשעה שנקבעה, אשר את ההרשאה 'התראות ותזכורות'.")
                        .setPositiveButton("פתח הגדרות", (d, w) -> startActivity(new Intent(
                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))))
                        .setNegativeButton("לא עכשיו", null).show();
            }
        }
    }

    private void offTimer(int minutes) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MINUTE, minutes);
        try {
            JSONObject s = new JSONObject();
            s.put("id", store.nextId());
            s.put("enabled", true);
            s.put("onOn", false);
            s.put("offOn", true);
            s.put("offHour", c.get(Calendar.HOUR_OF_DAY));
            s.put("offMinute", c.get(Calendar.MINUTE));
            s.put("days", 0);
            s.put("timer", true);
            s.put("acId", store.current().id);
            s.put("groupId", store.currentGroup());
            save(s);
            Toast.makeText(this, String.format(targetName(s) + " יכובה ב-%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)),
                    Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {}
    }

    private String targetName(JSONObject s) {
        JSONObject g = s.optInt("groupId") != 0 ? store.group(s.optInt("groupId")) : null;
        if (g != null) return "👥 " + g.optString("name");
        AcConfig a = store.ac(s.optInt("acId"));
        return a != null ? a.name : "מזגן";
    }

    private void save(JSONObject s) {
        JSONArray a = store.list(Store.SCHEDULES);
        boolean replaced = false;
        for (int i = 0; i < a.length(); i++) {
            if (a.optJSONObject(i).optInt("id") == s.optInt("id")) {
                try { a.put(i, s); } catch (Exception ignored) {}
                replaced = true;
            }
        }
        if (!replaced) a.put(s);
        store.saveList(Store.SCHEDULES, a);
        Scheduler.apply(this, s);
        render();
    }

    private void render() {
        list.removeAllViews();
        JSONArray a = store.list(Store.SCHEDULES);
        if (a.length() == 0) {
            list.addView(Ui.text(this, "אין תזמונים עדיין.", 14, Ui.MUTED, false));
            return;
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i);
            LinearLayout card = Ui.card(this);
            LinearLayout r = Ui.row(this);
            TextView t = Ui.text(this, Scheduler.describe(s, store.acs().size() > 1 ? targetName(s) : null), 16, s.optBoolean("enabled", true) ? Ui.TEXT : Ui.MUTED, false);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            Switch sw = new Switch(this);
            sw.setChecked(s.optBoolean("enabled", true));
            sw.setOnCheckedChangeListener((v, on) -> {
                try { s.put("enabled", on); s.remove("done_on"); s.remove("done_off"); } catch (Exception ignored) {}
                save(s);
            });
            r.addView(sw);
            card.addView(r);
            card.setOnClickListener(v -> { if (!s.optBoolean("timer")) editDialog(s); });
            card.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setTitle("למחוק את התזמון?")
                        .setPositiveButton("מחק", (d, w) -> delete(s.optInt("id")))
                        .setNegativeButton("ביטול", null).show();
                return true;
            });
            list.addView(card);
        }
        list.addView(Ui.text(this, "לחיצה לעריכה · לחיצה ארוכה למחיקה", 12, Ui.MUTED, false));
    }

    private void delete(int id) {
        Scheduler.cancel(this, id);
        JSONArray a = store.list(Store.SCHEDULES);
        for (int i = a.length() - 1; i >= 0; i--) if (a.optJSONObject(i).optInt("id") == id) a.remove(i);
        store.saveList(Store.SCHEDULES, a);
        render();
    }

    private void editDialog() { editDialog(null); }

    private void editDialog(JSONObject existing) {
        AcState cur = store.current().state;
        final JSONObject s;
        try {
            if (existing != null) s = existing;
            else {
                s = new JSONObject();
                s.put("id", store.nextId());
                s.put("enabled", true);
                s.put("onOn", true); s.put("onHour", 7); s.put("onMinute", 0);
                s.put("offOn", false); s.put("offHour", 8); s.put("offMinute", 0);
                s.put("days", 0x7F);
                s.put("acId", store.current().id);
                s.put("temp", cur.temp); s.put("mode", cur.mode); s.put("fan", cur.fan); s.put("swing", cur.swing);
            }
        } catch (Exception e) { return; }
        Scheduler.normalize(s);

        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 16);
        v.setPadding(p, p / 2, p, 0);
        v.setLayoutDirection(LinearLayout.LAYOUT_DIRECTION_RTL);

        // בחירת יעד: מזגן בודד או קבוצה
        java.util.List<AcConfig> all = store.acs();
        org.json.JSONArray groups = store.groups();
        final int[] acId = {s.optInt("acId", store.current().id)};
        final int[] groupId = {s.optInt("groupId", existing == null ? store.currentGroup() : 0)};
        if (all.size() > 1) {
            v.addView(Ui.label(this, "על מה התזמון?"));
            android.widget.RadioGroup rg = new android.widget.RadioGroup(this);
            int rid = 1000;
            for (int i = 0; i < groups.length(); i++) {
                JSONObject g = groups.optJSONObject(i);
                android.widget.RadioButton rb = new android.widget.RadioButton(this);
                rb.setText("👥 " + g.optString("name"));
                rb.setTextSize(16);
                rb.setId(rid++);
                final int gid = g.optInt("id");
                rb.setOnClickListener(x -> groupId[0] = gid);
                rg.addView(rb);
                if (groupId[0] == gid) rb.setChecked(true);
            }
            for (AcConfig a : all) {
                android.widget.RadioButton rb = new android.widget.RadioButton(this);
                rb.setText(a.name);
                rb.setTextSize(16);
                rb.setId(rid++);
                rb.setOnClickListener(x -> { groupId[0] = 0; acId[0] = a.id; });
                rg.addView(rb);
                if (groupId[0] == 0 && acId[0] == a.id) rb.setChecked(true);
            }
            v.addView(rg);
            Ui.spacer(v, 8);
        }

        // שעת הדלקה ושעת כיבוי - כל אחת אפשר לסמן או לא
        final boolean[] onOn = {Scheduler.partOn(s, Scheduler.ON)}, offOn = {Scheduler.partOn(s, Scheduler.OFF)};
        final int[] onT = {Scheduler.hour(s, Scheduler.ON), Scheduler.minute(s, Scheduler.ON)};
        final int[] offT = {Scheduler.hour(s, Scheduler.OFF), Scheduler.minute(s, Scheduler.OFF)};
        LinearLayout onOpts = new LinearLayout(this);
        onOpts.setOrientation(LinearLayout.VERTICAL);

        LinearLayout onRow = Ui.row(this);
        android.widget.CheckBox onCb = new android.widget.CheckBox(this);
        onCb.setText("🟢 הדלקה בשעה");
        onCb.setTextColor(Ui.TEXT);
        onCb.setTextSize(16);
        onCb.setChecked(onOn[0]);
        Button onTime = Ui.soft(this, fmt(onT));
        onTime.setTextSize(18);
        onTime.setOnClickListener(x -> pickTime(onT, onTime));
        onRow.addView(onCb, new LinearLayout.LayoutParams(0, -2, 1f));
        onRow.addView(onTime);
        v.addView(onRow);

        LinearLayout offRow = Ui.row(this);
        android.widget.CheckBox offCb = new android.widget.CheckBox(this);
        offCb.setText("🔴 כיבוי בשעה");
        offCb.setTextColor(Ui.TEXT);
        offCb.setTextSize(16);
        offCb.setChecked(offOn[0]);
        Button offTime = Ui.soft(this, fmt(offT));
        offTime.setTextSize(18);
        offTime.setOnClickListener(x -> pickTime(offT, offTime));
        offRow.addView(offCb, new LinearLayout.LayoutParams(0, -2, 1f));
        offRow.addView(offTime);
        LinearLayout.LayoutParams orl = new LinearLayout.LayoutParams(-1, -2);
        orl.topMargin = Ui.dp(this, 6);
        v.addView(offRow, orl);

        onCb.setOnCheckedChangeListener((b2, c2) -> {
            onOn[0] = c2;
            onTime.setEnabled(c2);
            onTime.setAlpha(c2 ? 1f : 0.4f);
            onOpts.setVisibility(c2 ? android.view.View.VISIBLE : android.view.View.GONE);
        });
        offCb.setOnCheckedChangeListener((b2, c2) -> {
            offOn[0] = c2;
            offTime.setEnabled(c2);
            offTime.setAlpha(c2 ? 1f : 0.4f);
        });
        onTime.setEnabled(onOn[0]); onTime.setAlpha(onOn[0] ? 1f : 0.4f);
        offTime.setEnabled(offOn[0]); offTime.setAlpha(offOn[0] ? 1f : 0.4f);
        Ui.spacer(v, 10);

        v.addView(Ui.label(this, "ימים (בלי בחירה = פעם אחת)"));
        final int[] days = {s.optInt("days")};
        LinearLayout dr = Ui.row(this);
        Button[] db = new Button[7];
        for (int i = 0; i < 7; i++) {
            final int d = i;
            db[i] = Ui.soft(this, Scheduler.DAY_NAMES[i]);
            db[i].setPadding(0, 0, 0, 0);
            db[i].setOnClickListener(x -> { days[0] ^= (1 << d); paintDays(db, days[0]); });
            Ui.addWeighted(dr, db[i], 2);
        }
        paintDays(db, days[0]);
        v.addView(dr);
        Ui.spacer(v, 10);

        onOpts.setVisibility(onOn[0] ? android.view.View.VISIBLE : android.view.View.GONE);
        onOpts.addView(Ui.label(this, "הגדרות ההדלקה"));
        onOpts.addView(Ui.label(this, "טמפרטורה"));
        NumberPicker np = new NumberPicker(this);
        np.setMinValue(16); np.setMaxValue(30);
        np.setValue(s.optInt("temp", 24));
        onOpts.addView(np);
        onOpts.addView(Ui.label(this, "מצב"));
        final int[] mode = {s.optInt("mode", 1)}, fan = {s.optInt("fan", 0)}, swing = {s.optInt("swing", 0)};
        Ui.Segmented ms = new Ui.Segmented(this, AcState.MODE_NAMES, i -> mode[0] = i);
        ms.select(mode[0]);
        onOpts.addView(ms.view);
        Ui.spacer(onOpts, 8);
        onOpts.addView(Ui.label(this, "מאוורר"));
        Ui.Segmented fs = new Ui.Segmented(this, AcState.FAN_NAMES, i -> fan[0] = i);
        fs.select(fan[0]);
        onOpts.addView(fs.view);
        Ui.spacer(onOpts, 8);
        onOpts.addView(Ui.label(this, "נדנוד תריסים"));
        Ui.Segmented ss = new Ui.Segmented(this, AcState.SWING_NAMES, i -> swing[0] = i);
        ss.select(swing[0]);
        onOpts.addView(ss.view);
        v.addView(onOpts);
        Ui.spacer(v, 12);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(v);
        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "תזמון חדש" : "עריכת תזמון")
                .setView(sv)
                .setPositiveButton("שמור", (d, w) -> {
                    if (!onOn[0] && !offOn[0]) {
                        Toast.makeText(this, "סמן הדלקה, כיבוי או שניהם", Toast.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        s.put("onOn", onOn[0]); s.put("onHour", onT[0]); s.put("onMinute", onT[1]);
                        s.put("offOn", offOn[0]); s.put("offHour", offT[0]); s.put("offMinute", offT[1]);
                        s.remove("done_on"); s.remove("done_off");
                        s.put("days", days[0]);
                        s.put("temp", np.getValue());
                        s.put("mode", mode[0]);
                        s.put("fan", fan[0]);
                        s.put("swing", swing[0]);
                        s.put("enabled", true);
                        s.put("acId", acId[0]);
                        s.put("groupId", groupId[0]);
                    } catch (Exception ignored) {}
                    save(s);
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private static String fmt(int[] t) { return String.format("%02d:%02d", t[0], t[1]); }

    private void pickTime(int[] t, Button b) {
        new android.app.TimePickerDialog(this, (tp, h, m) -> { t[0] = h; t[1] = m; b.setText(fmt(t)); },
                t[0], t[1], true).show();
    }

    private void paintDays(Button[] db, int days) {
        for (int i = 0; i < 7; i++) {
            boolean on = (days & (1 << i)) != 0;
            db[i].setBackground(Ui.round(on ? Ui.ACCENT : Ui.ACCENT_SOFT, Ui.dp(this, 10)));
            db[i].setTextColor(on ? 0xFFFFFFFF : Ui.ACCENT);
        }
    }
}

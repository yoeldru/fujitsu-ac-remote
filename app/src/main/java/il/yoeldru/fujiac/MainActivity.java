// נכתב: 2026-11-05 07:54 (שעון ישראל) · עודכן: 2026-11-05 12:06 - בחירה בין מזגנים, התראת עדכון
// המסך הראשי: הדלקה/כיבוי, טמפרטורה, מצב, מאוורר, תריסים, מועדפים.
package il.yoeldru.fujiac;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import il.yoeldru.fujiac.core.FujitsuIr;

public class MainActivity extends Activity {
    private Store store;
    private AcConfig ac;
    private AcState st;
    private LinearLayout acRow, updateCard;
    private Button gear;
    private TextView updateText;
    private HorizontalScrollView acScroll;
    private TextView tempView, statusView, titleView, offHint;
    private Button powerBtn;
    private Ui.Segmented modeSeg, fanSeg, swingSeg;
    private LinearLayout favRow;
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean pending = false;
    private final Runnable pendingSend = () -> { pending = false; sendState(false); };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        load();
        build();
        if (!ac.hasDevice()) startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
        titleView.setText(ac.name);
        renderAcs();
        refresh();
        renderFavorites();
        UpdateUi.quietCheck(info -> {
            boolean has = info != null && info.isNewer();
            updateCard.setVisibility(has ? android.view.View.VISIBLE : android.view.View.GONE);
            gear.setText(has ? "⚙️ הגדרות 🔴" : "⚙️ הגדרות");
            if (has) updateText.setText("🔴 עדכון זמין – גרסה 1.0." + info.build);
        });
    }

    private void build() {
        LinearLayout col = Ui.screen(this);

        // כותרת
        LinearLayout head = Ui.row(this);
        titleView = Ui.text(this, ac.name, 24, Ui.TEXT, true);
        head.addView(titleView, new LinearLayout.LayoutParams(0, -2, 1f));
        gear = Ui.soft(this, "⚙️ הגדרות");
        gear.setTextSize(13);
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        head.addView(gear);
        col.addView(head);
        Ui.spacer(col, 10);

        // כרטיס עדכון (מוצג רק כשיש גרסה חדשה)
        updateCard = Ui.card(this);
        updateCard.setBackground(Ui.round(Ui.ACCENT_SOFT, Ui.dp(this, 18)));
        LinearLayout ur = Ui.row(this);
        updateText = Ui.text(this, "🔴 עדכון זמין", 15, Ui.ACCENT, true);
        ur.addView(updateText, new LinearLayout.LayoutParams(0, -2, 1f));
        Button upd = Ui.primary(this, "עדכן");
        upd.setOnClickListener(v -> { if (UpdateUi.lastInfo != null) UpdateUi.offer(this, UpdateUi.lastInfo); });
        ur.addView(upd);
        updateCard.addView(ur);
        updateCard.setVisibility(android.view.View.GONE);
        col.addView(updateCard);

        // בחירת מזגן (מוצג כשיש יותר ממזגן אחד)
        acScroll = new HorizontalScrollView(this);
        acScroll.setHorizontalScrollBarEnabled(false);
        acRow = Ui.row(this);
        acRow.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        acScroll.addView(acRow);
        col.addView(acScroll);
        Ui.spacer(col, 10);

        // כרטיס ראשי: טמפרטורה + הפעלה
        LinearLayout main = Ui.card(this);
        main.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout tRow = Ui.row(this);
        tRow.setGravity(Gravity.CENTER);
        Button minus = Ui.soft(this, "−");
        minus.setTextSize(28);
        Button plus = Ui.soft(this, "+");
        plus.setTextSize(28);
        tempView = Ui.text(this, "24°", 64, Ui.TEXT, true);
        tempView.setGravity(Gravity.CENTER);
        // ב-RTL: פלוס מימין, מינוס משמאל
        tRow.addView(plus, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        tRow.addView(tempView, new LinearLayout.LayoutParams(0, -2, 1f));
        tRow.addView(minus, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        plus.setOnClickListener(v -> changeTemp(1));
        minus.setOnClickListener(v -> changeTemp(-1));
        main.addView(tRow);
        offHint = Ui.text(this, "", 13, Ui.MUTED, false);
        offHint.setGravity(Gravity.CENTER);
        main.addView(offHint, new LinearLayout.LayoutParams(-1, -2));
        Ui.spacer(main, 10);
        powerBtn = Ui.button(this, "הדלק", Ui.ON, Color.WHITE);
        powerBtn.setTextSize(18);
        powerBtn.setOnClickListener(v -> togglePower());
        main.addView(powerBtn, new LinearLayout.LayoutParams(-1, Ui.dp(this, 56)));
        col.addView(main);

        // מצב
        LinearLayout modeCard = Ui.card(this);
        modeCard.addView(Ui.label(this, "מצב"));
        String[] modes = new String[5];
        for (int i = 0; i < 5; i++) modes[i] = AcState.MODE_ICONS[i] + "\n" + AcState.MODE_NAMES[i];
        modeSeg = new Ui.Segmented(this, modes, i -> { st.mode = i; changed(); });
        modeCard.addView(modeSeg.view);
        Ui.spacer(modeCard, 14);
        modeCard.addView(Ui.label(this, "מהירות מאוורר"));
        fanSeg = new Ui.Segmented(this, AcState.FAN_NAMES, i -> { st.fan = i; changed(); });
        modeCard.addView(fanSeg.view);
        col.addView(modeCard);

        // תריסים
        LinearLayout louver = Ui.card(this);
        louver.addView(Ui.label(this, "תריסים – נדנוד אוטומטי"));
        swingSeg = new Ui.Segmented(this, AcState.SWING_NAMES, i -> { st.swing = i; changed(); });
        louver.addView(swingSeg.view);
        Ui.spacer(louver, 14);
        louver.addView(Ui.label(this, "תריסים – כיוון ידני (כל לחיצה = צעד אחד)"));
        LinearLayout stepRow = Ui.row(this);
        Button vert = Ui.primary(this, "↕️  למעלה / למטה");
        Button horiz = Ui.primary(this, "↔️  ימינה / שמאלה");
        vert.setOnClickListener(v -> sendShort(FujitsuIr.CMD_STEP_VERT, "התריס האנכי זז צעד"));
        horiz.setOnClickListener(v -> sendShort(FujitsuIr.CMD_STEP_HORIZ, "התריס האופקי זז צעד"));
        Ui.addWeighted(stepRow, vert, 4);
        Ui.addWeighted(stepRow, horiz, 4);
        louver.addView(stepRow);
        col.addView(louver);

        // מיוחדים
        LinearLayout extra = Ui.card(this);
        extra.addView(Ui.label(this, "מצבים מיוחדים"));
        LinearLayout exRow = Ui.row(this);
        Button eco = Ui.soft(this, "🍃 אקונומי");
        Button pow = Ui.soft(this, "⚡ עוצמתי");
        eco.setOnClickListener(v -> sendShort(FujitsuIr.CMD_ECONO, "מצב אקונומי הוחלף"));
        pow.setOnClickListener(v -> sendShort(FujitsuIr.CMD_POWERFUL, "מצב עוצמתי הוחלף"));
        Ui.addWeighted(exRow, eco, 4);
        Ui.addWeighted(exRow, pow, 4);
        extra.addView(exRow);
        col.addView(extra);

        // מועדפים
        LinearLayout fav = Ui.card(this);
        LinearLayout favHead = Ui.row(this);
        favHead.addView(Ui.label(this, "מועדפים"), new LinearLayout.LayoutParams(0, -2, 1f));
        Button saveFav = Ui.soft(this, "＋ שמור מצב נוכחי");
        saveFav.setTextSize(12.5f);
        saveFav.setOnClickListener(v -> addFavorite());
        favHead.addView(saveFav);
        fav.addView(favHead);
        Ui.spacer(fav, 6);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        favRow = Ui.row(this);
        favRow.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        hs.addView(favRow);
        fav.addView(hs);
        col.addView(fav);

        // ניווט
        LinearLayout nav = Ui.row(this);
        Button sched = Ui.soft(this, "⏰ תזמונים וטיימר");
        Button learn = Ui.soft(this, "🎓 כפתורים נלמדים");
        sched.setOnClickListener(v -> startActivity(new Intent(this, SchedulesActivity.class)));
        learn.setOnClickListener(v -> startActivity(new Intent(this, LearnActivity.class)));
        Ui.addWeighted(nav, sched, 4);
        Ui.addWeighted(nav, learn, 4);
        col.addView(nav);
        Ui.spacer(col, 14);

        statusView = Ui.text(this, "", 13, Ui.MUTED, false);
        statusView.setGravity(Gravity.CENTER);
        col.addView(statusView, new LinearLayout.LayoutParams(-1, -2));
    }

    private void load() {
        ac = store.current();
        st = ac.state;
    }

    private void renderAcs() {
        acRow.removeAllViews();
        java.util.List<AcConfig> all = store.acs();
        acScroll.setVisibility(all.size() > 1 ? android.view.View.VISIBLE : android.view.View.GONE);
        for (AcConfig a : all) {
            boolean sel = a.id == ac.id;
            Button b = Ui.button(this, (a.state.power ? "🟢 " : "⚪ ") + a.name,
                    sel ? Ui.ACCENT : Ui.ACCENT_SOFT, sel ? Color.WHITE : Ui.ACCENT);
            b.setTextSize(14);
            b.setOnClickListener(v -> switchTo(a.id));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(Ui.dp(this, 8));
            acRow.addView(b, lp);
        }
    }

    private void switchTo(int id) {
        if (id == ac.id) return;
        // שינוי שממתין לשליחה נשלח למזגן הקודם לפני המעבר
        if (pending) { h.removeCallbacks(pendingSend); pending = false; sendState(false); }
        store.setCurrent(id);
        load();
        titleView.setText(ac.name);
        renderAcs();
        refresh();
        status("");
    }

    private void refresh() {
        tempView.setText(st.temp + "°");
        tempView.setTextColor(st.power ? (st.mode == FujitsuIr.MODE_HEAT ? Ui.HEAT : Ui.ACCENT) : Ui.MUTED);
        powerBtn.setText(st.power ? "⏻  כבה" : "⏻  הדלק");
        powerBtn.setBackground(Ui.round(st.power ? Ui.OFF : Ui.ON, Ui.dp(this, 16)));
        offHint.setText(st.power ? AcState.MODE_NAMES[st.mode] : "המזגן כבוי · השינויים יישלחו בהדלקה");
        modeSeg.select(st.mode);
        fanSeg.select(st.fan);
        swingSeg.select(st.swing);
    }

    private void changeTemp(int d) {
        int t = Math.max(16, Math.min(30, st.temp + d));
        if (t == st.temp) return;
        st.temp = t;
        changed();
    }

    /** שינוי במצב: נשמר ונשלח (באיחור קצר כדי לאחד לחיצות רצופות). */
    private void changed() {
        store.saveState(ac.id, st);
        refresh();
        if (!st.power) return;
        h.removeCallbacks(pendingSend);
        pending = true;
        h.postDelayed(pendingSend, 350);
        status("…");
    }

    private void togglePower() {
        h.removeCallbacks(pendingSend);
        pending = false;
        st.power = !st.power;
        store.saveState(ac.id, st);
        refresh();
        renderAcs();
        sendState(st.power);
    }

    private void sendState(boolean turnOn) {
        final String what = st.summary();
        status("שולח…");
        Remote.send(ac, Remote.irForState(ac, st, turnOn), err -> status(err == null ? "✓ נשלח: " + what : "✗ " + err));
    }

    private void sendShort(int cmd, String okMsg) {
        if (!st.power) { status("הדלק קודם את המזגן"); return; }
        status("שולח…");
        Remote.send(ac, Remote.irForShort(ac, cmd), err -> status(err == null ? "✓ " + okMsg : "✗ " + err));
    }

    private void status(String s) { statusView.setText(s); }

    // ----- מועדפים -----
    private void addFavorite() {
        EditText name = new EditText(this);
        name.setHint("למשל: לילה, בוקר, חום");
        new AlertDialog.Builder(this)
                .setTitle("שמירת מועדף")
                .setMessage(st.temp + "° · " + AcState.MODE_NAMES[st.mode] + " · מאוורר " + AcState.FAN_NAMES[st.fan])
                .setView(name)
                .setPositiveButton("שמור", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) n = st.temp + "° " + AcState.MODE_NAMES[st.mode];
                    try {
                        JSONArray a = store.list(Store.FAVORITES);
                        JSONObject o = st.toJson();
                        o.put("name", n);
                        a.put(o);
                        store.saveList(Store.FAVORITES, a);
                    } catch (Exception ignored) {}
                    renderFavorites();
                })
                .setNegativeButton("ביטול", null).show();
    }

    private void renderFavorites() {
        favRow.removeAllViews();
        JSONArray a = store.list(Store.FAVORITES);
        if (a.length() == 0) {
            favRow.addView(Ui.text(this, "עדיין אין מועדפים", 13, Ui.MUTED, false));
            return;
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            final int idx = i;
            AcState f = AcState.fromJson(o);
            Button b = Ui.soft(this, o.optString("name") + "\n" + f.temp + "° " + AcState.MODE_ICONS[f.mode]);
            b.setTextSize(13);
            b.setOnClickListener(v -> {
                boolean wasOn = st.power;
                st = f.copy();
                st.power = true;
                store.saveState(ac.id, st);
                refresh();
                renderAcs();
                sendState(!wasOn);
            });
            b.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setTitle("למחוק את \"" + o.optString("name") + "\"?")
                        .setPositiveButton("מחק", (d, w) -> {
                            JSONArray arr = store.list(Store.FAVORITES);
                            arr.remove(idx);
                            store.saveList(Store.FAVORITES, arr);
                            renderFavorites();
                        }).setNegativeButton("ביטול", null).show();
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(Ui.dp(this, 8));
            favRow.addView(b, lp);
        }
    }
}

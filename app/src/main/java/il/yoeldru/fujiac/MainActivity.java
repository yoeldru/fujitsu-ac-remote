// נכתב: 2026-11-05 07:54 (שעון ישראל) · עודכן: 2026-11-05 23:09 - קבוצות, מזגני Wi-Fi (טורנדו) עם קריאת מצב אמיתי
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import il.yoeldru.fujiac.core.FujitsuIr;

public class MainActivity extends Activity {
    private Store store;
    private AcConfig ac;
    /** המזגנים שהמסך שולט בהם עכשיו: מזגן אחד, או כל חברי הקבוצה. */
    private List<AcConfig> targets;
    private JSONObject group;
    /** מזגנים בקבוצה שהיו כבויים - בשליחה הבאה יקבלו פקודת הדלקה. */
    private final Set<Integer> needOn = new HashSet<>();
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
        titleView.setText(title());
        renderAcs();
        refresh();
        renderFavorites();
        readLive();
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
        titleView = Ui.text(this, title(), 24, Ui.TEXT, true);
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

        // בחירת מזגן או קבוצה (מוצג כשיש יותר ממזגן אחד)
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
        int gid = store.currentGroup();
        group = gid != 0 ? store.group(gid) : null;
        List<AcConfig> m = store.members(group);
        if (group != null && !m.isEmpty()) {
            targets = m;
            ac = m.get(0);
            st = ac.state.copy();
            boolean anyOn = false;
            for (AcConfig t : m) anyOn |= t.state.power;
            st.power = anyOn;
        } else {
            group = null;
            ac = store.current();
            targets = Collections.singletonList(ac);
            st = ac.state.copy();
        }
    }

    private String title() {
        return group != null ? "👥 " + group.optString("name") : ac.name;
    }

    private void renderAcs() {
        acRow.removeAllViews();
        List<AcConfig> all = store.acs();
        acScroll.setVisibility(all.size() > 1 ? android.view.View.VISIBLE : android.view.View.GONE);
        // קבוצות
        JSONArray groups = store.groups();
        for (int i = 0; i < groups.length(); i++) {
            JSONObject g = groups.optJSONObject(i);
            List<AcConfig> m = store.members(g);
            boolean anyOn = false;
            for (AcConfig t : m) anyOn |= t.state.power;
            boolean sel = group != null && group.optInt("id") == g.optInt("id");
            Button b = chip((anyOn ? "🟢 " : "⚪ ") + "👥 " + g.optString("name"), sel);
            b.setOnClickListener(v -> switchToGroup(g.optInt("id")));
            b.setOnLongClickListener(v -> {
                GroupUi.edit(this, g, id -> afterGroupEdit(id));
                return true;
            });
            addChip(b);
        }
        // מזגנים בודדים
        for (AcConfig a : all) {
            boolean sel = group == null && a.id == ac.id;
            Button b = chip((a.state.power ? "🟢 " : "⚪ ") + a.name, sel);
            b.setOnClickListener(v -> switchTo(a.id));
            addChip(b);
        }
        if (all.size() > 1) {
            Button add = chip("＋ קבוצה", false);
            add.setOnClickListener(v -> GroupUi.edit(this, null, id -> afterGroupEdit(id)));
            addChip(add);
        }
    }

    private Button chip(String text, boolean sel) {
        Button b = Ui.button(this, text, sel ? Ui.ACCENT : Ui.ACCENT_SOFT, sel ? Color.WHITE : Ui.ACCENT);
        b.setTextSize(14);
        return b;
    }

    private void addChip(Button b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(Ui.dp(this, 8));
        acRow.addView(b, lp);
    }

    private void afterGroupEdit(int id) {
        if (id != 0) store.setCurrentGroup(id);
        reloadScreen();
    }

    private void flushPending() {
        if (pending) { h.removeCallbacks(pendingSend); pending = false; sendState(false); }
    }

    private void switchTo(int id) {
        if (group == null && id == ac.id) return;
        flushPending();  // שינוי שממתין נשלח למזגן/לקבוצה הקודמים לפני המעבר
        store.setCurrentGroup(0);
        store.setCurrent(id);
        reloadScreen();
    }

    private void switchToGroup(int gid) {
        if (group != null && group.optInt("id") == gid) return;
        flushPending();
        store.setCurrentGroup(gid);
        reloadScreen();
    }

    private void reloadScreen() {
        needOn.clear();
        load();
        titleView.setText(title());
        renderAcs();
        refresh();
        status("");
        readLive();
    }

    /** מזגני Wi-Fi מדווחים את המצב האמיתי שלהם - מעדכנים את המסך (גם אם שינו בשלט הרגיל). */
    private String ambientText = "";

    private void readLive() {
        ambientText = "";
        for (AcConfig t : new ArrayList<>(targets)) {
            if (!t.isWifi()) continue;
            final int acId = t.id;
            Remote.auxRead(t, (stat, err) -> {
                if (isFinishing()) return;
                AcConfig fresh = store.ac(acId);
                if (fresh == null) return;
                if (stat == null) {
                    if (targets.size() == 1) status("✗ " + fresh.name + ": " + err);
                    return;
                }
                fresh.state = Remote.toAppState(stat, fresh.state);
                store.saveAc(fresh);
                boolean stillHere = false;
                for (AcConfig x : targets) if (x.id == acId) stillHere = true;
                if (!stillHere || pending) return;
                if (stat.ambient != null) ambientText = String.format(java.util.Locale.US, "🌡️ בחדר %.1f° · ", stat.ambient);
                if (stat.viaCloud) ambientText = "☁️ " + ambientText;
                if (targets.size() == 1) {
                    load();
                } else if (fresh.state.power) {
                    st.power = true;
                }
                refresh();
                renderAcs();
            });
        }
    }

    /** שומר את המצב המוצג לכל המזגנים שבשליטה. */
    private void saveAll() {
        for (AcConfig t : targets) {
            if (st.power && !t.state.power) needOn.add(t.id);
            t.state = st.copy();
            store.saveState(t.id, t.state);
        }
    }

    private void refresh() {
        tempView.setText(st.temp + "°");
        tempView.setTextColor(st.power ? (st.mode == FujitsuIr.MODE_HEAT ? Ui.HEAT : Ui.ACCENT) : Ui.MUTED);
        powerBtn.setText(st.power ? "⏻  כבה" : "⏻  הדלק");
        powerBtn.setBackground(Ui.round(st.power ? Ui.OFF : Ui.ON, Ui.dp(this, 16)));
        String members = "";
        if (group != null) {
            StringBuilder sb = new StringBuilder();
            for (AcConfig t : targets) { if (sb.length() > 0) sb.append(" + "); sb.append(t.name); }
            members = sb + " · ";
        }
        offHint.setText(ambientText + members + (st.power ? AcState.MODE_NAMES[st.mode]
                : (group != null ? "כבויים" : "המזגן כבוי") + " · השינויים יישלחו בהדלקה"));
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
        saveAll();
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
        saveAll();
        refresh();
        renderAcs();
        sendState(st.power);
    }

    private void sendState(boolean turnOn) {
        final String what = st.summary();
        final AcState snap = st.copy();
        List<AcConfig> ts = new ArrayList<>(targets);
        status("שולח…");
        Multi m = new Multi(ts.size(), "✓ נשלח" + (ts.size() > 1 ? " ל-" + ts.size() + " מזגנים" : "") + ": " + what);
        for (AcConfig t : ts) {
            boolean on = turnOn || needOn.remove(t.id);
            Remote.apply(t, snap, on, err -> m.done(t, err));
        }
    }

    private void sendShort(int cmd, String okMsg) {
        if (!st.power) { status("הדלק קודם את " + (group != null ? "המזגנים" : "המזגן")); return; }
        status("שולח…");
        List<AcConfig> ts = new ArrayList<>(targets);
        Multi m = new Multi(ts.size(), "✓ " + okMsg + (ts.size() > 1 ? " (" + ts.size() + " מזגנים)" : ""));
        for (AcConfig t : ts) Remote.command(t, cmd, err -> m.done(t, err));
    }

    /** מאחד את תוצאות השליחה לכמה מזגנים לשורת סטטוס אחת. */
    private class Multi {
        int left;
        final String ok;
        final List<String> errs = new ArrayList<>();
        Multi(int n, String ok) { left = n; this.ok = ok; }
        void done(AcConfig t, String err) {
            if (err != null) errs.add((targets.size() > 1 ? t.name + ": " : "") + err);
            if (--left == 0) status(errs.isEmpty() ? ok : "✗ " + String.join(" · ", errs));
        }
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
                st = f.copy();
                st.power = true;
                saveAll();
                refresh();
                renderAcs();
                sendState(false);
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

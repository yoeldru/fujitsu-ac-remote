// נכתב: 2026-11-05 07:54 (שעון ישראל) · עודכן: 2026-11-15 01:07 - סורק רשת
// הגדרות: בחירת מזגן, איתור ה-RM4 שלו ברשת, דגם וקוד שלט, בדיקה, הוספה ומחיקה של מזגנים.
package il.yoeldru.fujiac;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import il.yoeldru.fujiac.core.AuxAc;
import il.yoeldru.fujiac.core.BroadlinkDevice;
import il.yoeldru.fujiac.core.FujitsuIr;

public class SettingsActivity extends Activity {
    private Store store;
    private AcConfig ac;
    private LinearLayout root;
    private TextView devInfo, msg;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        root = Ui.screen(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();  // מתעדכן גם אחרי חזרה מאשף הגדרת שלט חדש
    }

    private void render() {
        ac = store.current();
        root.removeAllViews();
        root.addView(Ui.text(this, "הגדרות", 24, Ui.TEXT, true));
        Ui.spacer(root, 12);

        // --- בחירת מזגן ---
        LinearLayout acs = Ui.card(this);
        acs.addView(Ui.label(this, "המזגנים שלי"));
        List<AcConfig> all = store.acs();
        LinearLayout r = null;
        for (int i = 0; i < all.size(); i++) {
            AcConfig a = all.get(i);
            if (i % 3 == 0) {
                r = Ui.row(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.bottomMargin = Ui.dp(this, 6);
                acs.addView(r, lp);
            }
            boolean sel = a.id == ac.id;
            Button btn = Ui.button(this, a.name, sel ? Ui.ACCENT : Ui.ACCENT_SOFT, sel ? 0xFFFFFFFF : Ui.ACCENT);
            btn.setTextSize(13.5f);
            btn.setOnClickListener(v -> { store.setCurrent(a.id); render(); });
            Ui.addWeighted(r, btn, 3);
        }
        if (all.size() % 3 != 0) for (int k = all.size() % 3; k < 3; k++) Ui.addWeighted(r, new TextView(this), 3);
        LinearLayout addRow = Ui.row(this);
        Button add = Ui.soft(this, "＋ מזגן עם שלט (IR)");
        add.setOnClickListener(v -> { store.addAc(); render(); msg("נוסף מזגן חדש – הגדר לו את השלט החכם שלו"); });
        Button addWifi = Ui.soft(this, "＋ מזגן Wi-Fi (טורנדו)");
        addWifi.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("הוספת מזגן Wi-Fi")
                .setItems(new String[]{"🆕 צימוד מזגן (חיבור ראשון / אחרי ניתוק)", "🔍 חפש מזגן שכבר מחובר לרשת",
                        "🖊️ הזן כתובת IP של המזגן (מרשימת המכשירים בנתב)",
                        "🔎 סרוק את כל הרשת וזהה מכשירים"}, (d, w) -> {
                    if (w == 0) {
                        android.content.Intent i = new android.content.Intent(this, SetupDeviceActivity.class);
                        i.putExtra("wifiAc", true);
                        startActivity(i);
                    } else if (w == 1) addWifiAcs();
                    else if (w == 2) askAcIp();
                    else startActivity(new android.content.Intent(this, NetScanActivity.class));
                }).show());
        Ui.addWeighted(addRow, add, 3);
        Ui.addWeighted(addRow, addWifi, 3);
        acs.addView(addRow);
        root.addView(acs);

        // --- קבוצות ---
        if (all.size() > 1) {
            LinearLayout gc = Ui.card(this);
            gc.addView(Ui.label(this, "קבוצות מזגנים (שליטה ותזמון של כמה מזגנים יחד)"));
            org.json.JSONArray groups = store.groups();
            for (int i = 0; i < groups.length(); i++) {
                org.json.JSONObject g = groups.optJSONObject(i);
                StringBuilder names = new StringBuilder();
                for (AcConfig m : store.members(g)) { if (names.length() > 0) names.append(" + "); names.append(m.name); }
                Button gb = Ui.soft(this, "👥 " + g.optString("name") + "  ·  " + names + "  ✏️");
                gb.setOnClickListener(v -> GroupUi.edit(this, g, id -> render()));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.bottomMargin = Ui.dp(this, 6);
                gc.addView(gb, lp);
            }
            Button ng = Ui.primary(this, "＋ קבוצה חדשה");
            ng.setOnClickListener(v -> GroupUi.edit(this, null, id -> render()));
            gc.addView(ng, new LinearLayout.LayoutParams(-1, -2));
            root.addView(gc);
        }

        // --- שם ---
        LinearLayout nameCard = Ui.card(this);
        nameCard.addView(Ui.label(this, "שם המזגן (למשל: סלון, חדר שינה)"));
        LinearLayout nr = Ui.row(this);
        EditText name = new EditText(this);
        name.setText(ac.name);
        name.setTextColor(Ui.TEXT);
        nr.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        Button saveName = Ui.soft(this, "שמור");
        saveName.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) return;
            AcConfig a = store.current();
            a.name = n;
            store.saveAc(a);
            render();
            msg("השם נשמר");
        });
        nr.addView(saveName);
        nameCard.addView(nr);
        root.addView(nameCard);

        // --- השלט החכם ---
        LinearLayout dev = Ui.card(this);
        dev.addView(Ui.label(this, ac.isWifi() ? ac.name + " ברשת (מזגן Wi-Fi)" : "השלט החכם של " + ac.name));
        devInfo = Ui.text(this, "", 15, Ui.TEXT, false);
        dev.addView(devInfo);
        Ui.spacer(dev, 10);
        Button find = Ui.primary(this, "🔍 חפש ברשת הביתית");
        find.setOnClickListener(v -> discover(null));
        dev.addView(find, new LinearLayout.LayoutParams(-1, -2));
        if (!ac.isWifi()) {
            Ui.spacer(dev, 8);
            Button wizard = Ui.soft(this, "🆕 הגדרת שלט חדש / אחרי ריסט (בלי Broadlink)");
            wizard.setOnClickListener(v -> startActivity(new android.content.Intent(this, SetupDeviceActivity.class)));
            dev.addView(wizard, new LinearLayout.LayoutParams(-1, -2));
        }
        Ui.spacer(dev, 8);
        LinearLayout ipRow = Ui.row(this);
        EditText ip = new EditText(this);
        ip.setHint("או הזן IP, למשל 192.168.1.50");
        ip.setInputType(InputType.TYPE_CLASS_PHONE);
        ip.setTextColor(Ui.TEXT);
        ip.setHintTextColor(Ui.MUTED);
        ipRow.addView(ip, new LinearLayout.LayoutParams(0, -2, 1f));
        Button connect = Ui.soft(this, "חבר");
        connect.setOnClickListener(v -> discover(ip.getText().toString().trim()));
        ipRow.addView(connect);
        dev.addView(ipRow);
        Ui.spacer(dev, 8);
        dev.addView(Ui.text(this, ac.isWifi()
                ? "המזגן מחובר ל-Wi-Fi דרך אפליקציית Tornado WIFI 3 – האפליקציה הזו מדברת איתו ישירות ברשת הביתית. "
                        + "מומלץ לקבע לו כתובת IP בנתב."
                : "שלט שחובר דרך אפליקציית Broadlink: ודא שם ש\"נעילת מכשיר\" (Lock device) כבויה. "
                        + "הטלפון צריך להיות על אותה רשת Wi-Fi. מומלץ לקבע לכל שלט כתובת IP בנתב.",
                12.5f, Ui.MUTED, false));
        root.addView(dev);
        showDevice();

        // --- המזגן ---
        LinearLayout acCard = Ui.card(this);
        LinearLayout irOnly = new LinearLayout(this);
        irOnly.setOrientation(LinearLayout.VERTICAL);
        acCard.addView(irOnly);
        if (ac.isWifi()) irOnly.setVisibility(android.view.View.GONE);
        LinearLayout acCardIr = irOnly;
        acCardIr.addView(Ui.label(this, "סוג השלט המקורי (כתוב בגב השלט)"));
        Ui.Segmented modelSeg = new Ui.Segmented(this, new String[]{"AR-RAH2E ודומיו", "AR-REW4E"}, i -> {
            AcConfig a = store.current();
            a.model = i == 0 ? FujitsuIr.MODEL_ARRAH2E : FujitsuIr.MODEL_ARREW4E;
            store.saveAc(a);
            msg("נשמר");
        });
        modelSeg.select(ac.model == FujitsuIr.MODEL_ARREW4E ? 1 : 0);
        acCardIr.addView(modelSeg.view);
        Ui.spacer(acCardIr, 14);

        acCardIr.addView(Ui.label(this, "קוד שלט (Custom code)"));
        Ui.Segmented idSeg = new Ui.Segmented(this, AcConfig.REMOTE_IDS, i -> {
            AcConfig a = store.current();
            a.remoteId = i;
            store.saveAc(a);
            msg("קוד שלט " + AcConfig.REMOTE_IDS[i] + " נשמר");
        });
        idSeg.select(ac.remoteId);
        acCardIr.addView(idSeg.view);
        Ui.spacer(acCardIr, 4);
        acCardIr.addView(Ui.text(this,
                "ברירת המחדל היא A. אם שני מזגנים באותו חדר מגיבים יחד לאותה פקודה – "
                        + "קבע לאחד מהם קוד B (במזגן ובשלט המקורי, לפי חוברת ההוראות) ובחר B גם כאן.",
                12.5f, Ui.MUTED, false));
        Ui.spacer(acCardIr, 12);

        CheckBox pb = new CheckBox(this);
        pb.setText("שלח פקודת הדלקה בכל שינוי (אם משתמשים גם בשלט הרגיל והאפליקציה 'מתבלבלת' אם המזגן דולק)");
        pb.setTextColor(Ui.TEXT);
        pb.setChecked(ac.alwaysPower);
        pb.setOnCheckedChangeListener((v, c) -> {
            AcConfig a = store.current();
            a.alwaysPower = c;
            store.saveAc(a);
        });
        acCardIr.addView(pb);
        Ui.spacer(acCardIr, 12);
        LinearLayout testRow = Ui.row(this);
        Button tOn = Ui.primary(this, "בדיקה: הדלק 24° קירור");
        Button tOff = Ui.soft(this, "בדיקה: כבה");
        tOn.setOnClickListener(v -> test(true));
        tOff.setOnClickListener(v -> test(false));
        Ui.addWeighted(testRow, tOn, 3);
        Ui.addWeighted(testRow, tOff, 3);
        acCard.addView(testRow);
        root.addView(acCard);

        if (all.size() > 1) {
            Button del = Ui.button(this, "🗑 מחק את " + ac.name, Ui.ACCENT_SOFT, Ui.OFF);
            del.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("למחוק את " + ac.name + "?")
                    .setMessage("גם התזמונים והכפתורים הנלמדים שלו יימחקו.")
                    .setPositiveButton("מחק", (d, w) -> deleteCurrent())
                    .setNegativeButton("ביטול", null).show());
            root.addView(del, new LinearLayout.LayoutParams(-1, -2));
            Ui.spacer(root, 10);
        }

        msg = Ui.text(this, "", 14, Ui.ACCENT, true);
        root.addView(msg);
        Ui.spacer(root, 16);
        Button netScan = Ui.soft(this, "🔎 סורק רשת – מה מחובר לרשת הביתית");
        netScan.setOnClickListener(v -> startActivity(new android.content.Intent(this, NetScanActivity.class)));
        root.addView(netScan, new LinearLayout.LayoutParams(-1, -2));
        Ui.spacer(root, 8);

        Button backup = Ui.primary(this, "📦 גיבוי והעברת הגדרות לטלפון אחר");
        backup.setOnClickListener(v -> startActivity(new android.content.Intent(this, BackupActivity.class)));
        root.addView(backup, new LinearLayout.LayoutParams(-1, -2));
        Ui.spacer(root, 12);

        LinearLayout upCard = Ui.card(this);
        upCard.addView(Ui.label(this, "עדכונים"));
        TextView ver = Ui.text(this, "גרסה מותקנת: " + BuildConfig.VERSION_NAME, 14, Ui.TEXT, false);
        upCard.addView(ver);
        Ui.spacer(upCard, 8);
        Button chk = Ui.soft(this, "🔄 בדוק עדכונים");
        chk.setOnClickListener(v -> {
            ver.setText("בודק…");
            Updater.check((info, err) -> {
                if (info == null) { ver.setText("✗ הבדיקה נכשלה: " + err); return; }
                UpdateUi.lastInfo = info;
                if (info.isNewer()) {
                    ver.setText("🔴 יש גרסה חדשה: 1.0." + info.build);
                    UpdateUi.offer(this, info);
                } else ver.setText("✓ מותקנת הגרסה האחרונה (" + BuildConfig.VERSION_NAME + ")");
            });
        });
        upCard.addView(chk, new LinearLayout.LayoutParams(-1, -2));
        root.addView(upCard);
    }

    private void deleteCurrent() {
        int id = ac.id;
        org.json.JSONArray s = store.list(Store.SCHEDULES);
        for (int i = s.length() - 1; i >= 0; i--) {
            if (s.optJSONObject(i).optInt("acId") == id) {
                Scheduler.cancel(this, s.optJSONObject(i).optInt("id"));
                s.remove(i);
            }
        }
        store.saveList(Store.SCHEDULES, s);
        org.json.JSONArray l = store.list(Store.LEARNED);
        for (int i = l.length() - 1; i >= 0; i--) if (l.optJSONObject(i).optInt("acId") == id) l.remove(i);
        store.saveList(Store.LEARNED, l);
        store.deleteAc(id);
        render();
        msg("נמחק");
    }

    private void showDevice() {
        devInfo.setText(!ac.hasDevice() ? "לא הוגדר עדיין"
                : "✓ " + (ac.devName.isEmpty() ? "RM4" : ac.devName) + "\nIP: " + ac.host
                + "\nMAC: " + ac.device().macString() + String.format("\nסוג: 0x%04X", ac.devtype));
    }

    private void msg(String s) { if (msg != null) msg.setText(s); }

    private void discover(String ip) {
        msg("מחפש…");
        Context app = getApplicationContext();
        Remote.NET.execute(() -> {
            List<BroadlinkDevice> found = new ArrayList<>();
            String err = null;
            try {
                if (ip != null && !ip.isEmpty()) {
                    found.addAll(BroadlinkDevice.discover(InetAddress.getByName(ip), 4000));
                } else {
                    InetAddress bc = subnetBroadcast(app);
                    if (bc != null) found.addAll(BroadlinkDevice.discover(bc, 3000));
                    if (found.isEmpty())
                        found.addAll(BroadlinkDevice.discover(InetAddress.getByName("255.255.255.255"), 3000));
                }
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String fErr = err;
            main.post(() -> onFound(found, fErr));
        });
    }

    private void onFound(List<BroadlinkDevice> allFound, String err) {
        // מזגן Wi-Fi מחפש רק מזגנים; מזגן IR מחפש רק שלטים
        List<BroadlinkDevice> found = new ArrayList<>();
        for (BroadlinkDevice d : allFound)
            if ((d.devtype == AuxAc.DEVTYPE) == ac.isWifi()) found.add(d);
        if (found.isEmpty()) {
            msg((ac.isWifi() ? "✗ לא נמצא המזגן" : "✗ לא נמצא שלט") + (err != null ? " (" + err + ")" : "")
                    + ". ודא שהטלפון על אותו Wi-Fi, או הזן IP ידנית (מופיע בנתב או באפליקציית Broadlink).");
            return;
        }
        if (found.size() == 1) { choose(found.get(0)); return; }
        // כמה שלטים ברשת - מציינים איזה כבר משויך למזגן אחר
        List<AcConfig> all = store.acs();
        String[] names = new String[found.size()];
        for (int i = 0; i < names.length; i++) {
            BroadlinkDevice d = found.get(i);
            String owner = "";
            for (AcConfig a : all)
                if (a.id != ac.id && a.mac.equals(FujitsuIr.hex(d.macRaw))) owner = "  (משויך ל" + a.name + ")";
            names[i] = (d.name.isEmpty() ? (ac.isWifi() ? "מזגן" : "RM4") : d.name) + " · " + d.host + owner;
        }
        msg("נמצאו " + found.size() + " שלטים – בחר את זה של " + ac.name);
        new AlertDialog.Builder(this).setTitle("איזה שלט שייך ל" + ac.name + "?")
                .setItems(names, (d, w) -> choose(found.get(w))).show();
    }

    // ---------- הוספת מזגני Wi-Fi ----------

    private void addWifiAcs() {
        msg("מחפש מזגני Wi-Fi ברשת… (כ-10 שניות)");
        Context app = getApplicationContext();
        Remote.NET.execute(() -> {
            List<BroadlinkDevice> found = new ArrayList<>();
            List<InetAddress> targets = new ArrayList<>();
            InetAddress bc = subnetBroadcast(app);
            if (bc != null) targets.add(bc);
            try { targets.add(InetAddress.getByName("255.255.255.255")); } catch (Exception ignored) {}
            for (InetAddress t : targets) {
                try {
                    for (BroadlinkDevice d : BroadlinkDevice.discover(t, 5000, true)) {
                        boolean dup = false;
                        for (BroadlinkDevice x : found) if (x.host.equals(d.host)) dup = true;
                        if (!dup) found.add(d);
                    }
                } catch (Exception ignored) {}
            }
            main.post(() -> onWifiFound(found));
        });
    }

    /** חיבור לפי IP: פנייה ישירה למכשיר (למקרה שהוא לא עונה לחיפוש כללי). */
    private void askAcIp() {
        EditText et = new EditText(this);
        et.setHint("למשל 192.168.150.120");
        et.setInputType(InputType.TYPE_CLASS_PHONE);
        int p = Ui.dp(this, 20);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(p, p / 2, p, 0);
        box.addView(et, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle("כתובת IP של המזגן")
                .setMessage("בנתב (192.168.150.1) ← רשימת המכשירים המחוברים – חפש מכשיר שאינו טלפון/מחשב/RM4 (לפעמים בשם BroadLink, HF-LPB או ללא שם).")
                .setView(box)
                .setPositiveButton("חבר", (d, w) -> {
                    String ip = et.getText().toString().trim();
                    if (ip.isEmpty()) return;
                    msg("פונה אל " + ip + "…");
                    Remote.NET.execute(() -> {
                        List<BroadlinkDevice> found = new ArrayList<>();
                        String err = null;
                        try { found.addAll(BroadlinkDevice.discover(InetAddress.getByName(ip), 6000)); }
                        catch (Exception e) { err = e.getMessage(); }
                        final String fe = err;
                        main.post(() -> {
                            if (found.isEmpty()) {
                                msg("✗ המכשיר ב-" + ip + " לא ענה" + (fe != null ? " (" + fe + ")" : "")
                                        + ". אם זה המזגן – הוא חסום לשליטה מקומית; צריך צימוד מחדש.");
                                return;
                            }
                            onWifiFound(found);
                        });
                    });
                })
                .setNegativeButton("ביטול", null).show();
    }

    private void onWifiFound(List<BroadlinkDevice> found) {
        // מזגנים מסוג מוכר מסומנים מראש; מכשירים חדשים מסוג אחר (שאינם השלטים שכבר משויכים) מוצגים לבחירה
        List<BroadlinkDevice> options = new ArrayList<>();
        List<Boolean> known = new ArrayList<>();
        StringBuilder all = new StringBuilder();
        for (BroadlinkDevice d : found) {
            all.append("\n• ").append(d.name.isEmpty() ? "מכשיר" : d.name).append(" · ").append(d.host)
                    .append(String.format(" · סוג 0x%04X", d.devtype));
            boolean exists = false;
            for (AcConfig a : store.acs()) if (a.mac.equals(FujitsuIr.hex(d.macRaw))) exists = true;
            if (exists) { all.append(" (כבר באפליקציה)"); continue; }
            options.add(d);
            known.add(d.devtype == AuxAc.DEVTYPE);
        }
        if (options.isEmpty()) {
            msg("✗ לא נמצאו מכשירים חדשים ברשת." + (found.isEmpty() ? "" : "\nמה שנמצא:" + all)
                    + "\nהמזגנים לא עונים לחיפוש מקומי – צריך צימוד מחדש דרך \"🆕 צימוד מזגן\".");
            return;
        }
        String[] names = new String[options.size()];
        boolean[] checked = new boolean[options.size()];
        for (int i = 0; i < names.length; i++) {
            BroadlinkDevice d = options.get(i);
            names[i] = (known.get(i) ? "❄️ " : "❓ ") + (d.name.isEmpty() ? "מכשיר" : d.name) + " · " + d.host
                    + (known.get(i) ? "" : String.format(" · סוג 0x%04X", d.devtype));
            checked[i] = known.get(i);
        }
        msg("נמצא ברשת:" + all);
        new AlertDialog.Builder(this).setTitle("סמן את המזגנים (❓ = סוג לא מוכר, ייתכן שזה מזגן)")
                .setMultiChoiceItems(names, checked, (d, w, c) -> checked[w] = c)
                .setPositiveButton("הוסף", (d, w) -> {
                    List<BroadlinkDevice> chosen = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) if (checked[i]) chosen.add(options.get(i));
                    addChosen(chosen);
                })
                .setNegativeButton("ביטול", null).show();
    }

    private void addChosen(List<BroadlinkDevice> chosen) {
        if (chosen.isEmpty()) return;
        msg("מתחבר…");
        Remote.NET.execute(() -> {
            StringBuilder report = new StringBuilder();
            int lastId = 0;
            for (BroadlinkDevice d : chosen) {
                AcConfig a = new AcConfig();
                a.id = store.nextId();
                a.kind = "aux";
                a.name = d.name.isEmpty() ? "מזגן Wi-Fi" : d.name;
                a.setDevice(d);
                String line;
                try {
                    d.auth();
                    AuxAc.Status st = AuxAc.read(d);
                    a.state = Remote.toAppState(st, a.state);
                    line = "✓ " + a.name + " – " + (st.power == 1 ? "דולק, " + (int) st.temp + "°" : "כבוי")
                            + (st.ambient != null ? String.format(java.util.Locale.US, " · בחדר %.1f°", st.ambient) : "");
                } catch (Exception e) {
                    line = "⚠ " + a.name + " – נוסף, אבל לא הגיב: " + e.getMessage();
                }
                store.saveAc(a);
                lastId = a.id;
                report.append(line).append('\n');
            }
            final int fLast = lastId;
            main.post(() -> {
                store.setCurrent(fLast);
                render();
                msg(report.toString().trim());
                new AlertDialog.Builder(this).setTitle("מזגני Wi-Fi")
                        .setMessage(report.toString().trim() + "\n\nאפשר לשנות לכל מזגן את השם כאן בהגדרות.")
                        .setPositiveButton("אישור", null).show();
            });
        });
    }

    private void choose(BroadlinkDevice d) {
        msg("מתחבר ל-" + d.host + "…");
        final int acId = ac.id;
        Remote.NET.execute(() -> {
            String err = null;
            try { d.auth(); } catch (Exception e) { err = e.getMessage(); }
            final String fErr = err;
            main.post(() -> {
                AcConfig a = store.ac(acId);
                if (a == null) return;
                a.setDevice(d);
                store.saveAc(a);
                ac = a;
                showDevice();
                if (fErr == null) msg("✓ מחובר! לחץ \"בדיקה\" כדי לוודא שהמזגן הנכון מגיב.");
                else msg("נשמר, אבל ההתחברות נכשלה: " + fErr
                        + (d.locked ? "\nהמכשיר נעול – בטל את הנעילה באפליקציית Broadlink." : ""));
            });
        });
    }

    private void test(boolean on) {
        AcConfig a = store.current();
        AcState s = new AcState();
        s.power = on; s.temp = 24; s.mode = FujitsuIr.MODE_COOL;
        msg("שולח ל" + a.name + "…");
        Remote.apply(a, s, true,
                err -> msg(err == null ? "✓ נשלח – האם " + a.name + " הגיב?" : "✗ " + err));
    }

    @SuppressWarnings("deprecation")
    private static InetAddress subnetBroadcast(Context c) {
        try {
            WifiManager wm = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);
            DhcpInfo dh = wm.getDhcpInfo();
            if (dh == null || dh.ipAddress == 0) return null;
            int bc = (dh.ipAddress & dh.netmask) | ~dh.netmask;
            if (dh.netmask == 0) bc = (dh.ipAddress & 0x00FFFFFF) | 0xFF000000;
            byte[] q = new byte[4];
            for (int k = 0; k < 4; k++) q[k] = (byte) ((bc >> (k * 8)) & 0xFF);
            return InetAddress.getByAddress(q);
        } catch (Exception e) {
            return null;
        }
    }
}

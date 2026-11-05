// נכתב: 2026-11-05 15:04 (שעון ישראל)
// אשף "הגדרת שלט חדש": מחבר RM4 שעבר ריסט לרשת הביתית ישירות מהאפליקציה – בלי אפליקציית Broadlink ובלי ענן.
// שלבים: ריסט למצב AP ← פרטי הרשת הביתית ← התחברות לרשת של השלט ושליחה ← חיפוש ושיוך למזגן.
package il.yoeldru.fujiac;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiNetworkSpecifier;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PatternMatcher;
import android.provider.Settings;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import il.yoeldru.fujiac.core.BroadlinkDevice;
import il.yoeldru.fujiac.core.FujitsuIr;

public class SetupDeviceActivity extends Activity {
    private Store store;
    private TextView msg;
    private EditText ssid, pass;
    private int security = BroadlinkDevice.SEC_WPA2;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ConnectivityManager.NetworkCallback apCallback;
    private ConnectivityManager cm;
    private boolean askedPermission = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        AcConfig ac = store.current();
        LinearLayout col = Ui.screen(this);
        col.addView(Ui.text(this, "הגדרת שלט חדש", 24, Ui.TEXT, true));
        Ui.spacer(col, 4);
        col.addView(Ui.text(this, "יחובר ישירות לרשת הביתית וישויך ל" + ac.name + " – בלי אפליקציית Broadlink.",
                13, Ui.MUTED, false));
        Ui.spacer(col, 12);

        // שלב 1
        LinearLayout s1 = Ui.card(this);
        s1.addView(Ui.text(this, "1 · ריסט למצב הגדרה", 17, Ui.TEXT, true));
        Ui.spacer(s1, 6);
        s1.addView(Ui.text(this,
                "• החזק את כפתור הריסט (חור קטן) עם סיכה עד שהנורית מהבהבת מהר, ושחרר.\n"
                        + "• החזק שוב עד שהנורית מהבהבת לאט – השלט משדר עכשיו רשת Wi-Fi משלו (בד\"כ BroadlinkProv).",
                14.5f, Ui.TEXT, false));
        col.addView(s1);

        // שלב 2
        LinearLayout s2 = Ui.card(this);
        s2.addView(Ui.text(this, "2 · הרשת הביתית (2.4GHz)", 17, Ui.TEXT, true));
        Ui.spacer(s2, 6);
        ssid = new EditText(this);
        ssid.setHint("שם הרשת (SSID) – בדיוק כמו בנתב");
        ssid.setTextColor(Ui.TEXT);
        ssid.setHintTextColor(Ui.MUTED);
        s2.addView(ssid);
        pass = new EditText(this);
        pass.setHint("סיסמת הרשת");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        pass.setTextColor(Ui.TEXT);
        pass.setHintTextColor(Ui.MUTED);
        s2.addView(pass);
        Ui.spacer(s2, 8);
        s2.addView(Ui.label(this, "סוג אבטחה (אם לא בטוח – השאר WPA2)"));
        int[] modes = {BroadlinkDevice.SEC_WPA2, BroadlinkDevice.SEC_WPA12, BroadlinkDevice.SEC_NONE};
        Ui.Segmented sec = new Ui.Segmented(this, new String[]{"WPA2", "WPA/WPA2", "ללא סיסמה"}, i -> security = modes[i]);
        sec.select(0);
        s2.addView(sec.view);
        Ui.spacer(s2, 6);
        s2.addView(Ui.text(this, "אם לנתב יש רשת 5GHz נפרדת עם שם אחר – הזן את שם רשת ה-2.4.", 12.5f, Ui.MUTED, false));
        col.addView(s2);

        // שלב 3
        LinearLayout s3 = Ui.card(this);
        s3.addView(Ui.text(this, "3 · חיבור השלט", 17, Ui.TEXT, true));
        Ui.spacer(s3, 6);
        Button go = Ui.primary(this, "📡 חבר את השלט לרשת הביתית");
        go.setOnClickListener(v -> start());
        s3.addView(go, new LinearLayout.LayoutParams(-1, -2));
        Ui.spacer(s3, 8);
        s3.addView(Ui.text(this,
                "אנדרואיד יציג חלון עם הרשת של השלט – בחר בה ואשר. אם לא הופיע חלון: חבר ידנית את הטלפון לרשת של השלט "
                        + "בהגדרות ה-Wi-Fi (אם נשאל – \"השאר מחובר\"), חזור לכאן ולחץ:",
                12.5f, Ui.MUTED, false));
        Ui.spacer(s3, 6);
        LinearLayout manual = Ui.row(this);
        Button openWifi = Ui.soft(this, "הגדרות Wi-Fi");
        openWifi.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        Button sendManual = Ui.soft(this, "שלח (חיבור ידני)");
        sendManual.setOnClickListener(v -> sendManual());
        Ui.addWeighted(manual, openWifi, 3);
        Ui.addWeighted(manual, sendManual, 3);
        s3.addView(manual);
        col.addView(s3);

        msg = Ui.text(this, "", 15, Ui.ACCENT, true);
        msg.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        col.addView(msg);

        Button find = Ui.soft(this, "🔍 חפש את השלט ברשת הביתית ושייך");
        find.setOnClickListener(v -> findAndAssign(1));
        col.addView(find, new LinearLayout.LayoutParams(-1, -2));
    }

    @Override
    protected void onDestroy() {
        releaseAp();
        super.onDestroy();
    }

    private byte[] packet() {
        String s = ssid.getText().toString().trim();
        String p = pass.getText().toString();
        if (s.isEmpty()) { msg.setText("✗ הזן את שם הרשת הביתית"); return null; }
        if (security != BroadlinkDevice.SEC_NONE && p.isEmpty()) { msg.setText("✗ הזן סיסמה"); return null; }
        try {
            return BroadlinkDevice.setupPacket(s, p, security);
        } catch (IllegalArgumentException e) {
            msg.setText("✗ " + e.getMessage());
            return null;
        }
    }

    private static final int REQ_PERM = 51;

    /** הרשאה לחיבור לרשת Wi-Fi קרובה (אנדרואיד 13+: מכשירים קרובים; לפני כן: מיקום). */
    private String wifiPermission() {
        return Build.VERSION.SDK_INT >= 33 ? "android.permission.NEARBY_WIFI_DEVICES"
                : "android.permission.ACCESS_FINE_LOCATION";
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_PERM) start();
    }

    /** אנדרואיד 10+: מבקש מהמערכת להתחבר זמנית לרשת של השלט, שולח, ומשחרר - הטלפון חוזר לבד לרשת הביתית. */
    private void start() {
        byte[] pkt = packet();
        if (pkt == null) return;
        if (Build.VERSION.SDK_INT < 29) {
            msg.setText("בגרסת האנדרואיד הזו – חבר ידנית את הטלפון לרשת של השלט ולחץ \"שלח (חיבור ידני)\".");
            return;
        }
        if (checkSelfPermission(wifiPermission()) != android.content.pm.PackageManager.PERMISSION_GRANTED
                && !askedPermission) {
            askedPermission = true;
            requestPermissions(new String[]{wifiPermission()}, REQ_PERM);
            return;
        }
        releaseAp();
        msg.setText("מחפש את הרשת של השלט… בחר בה בחלון שייפתח");
        WifiNetworkSpecifier spec = new WifiNetworkSpecifier.Builder()
                .setSsidPattern(new PatternMatcher(".*[Bb][Rr][Oo][Aa][Dd].*", PatternMatcher.PATTERN_ADVANCED_GLOB))
                .build();
        NetworkRequest req = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(spec)
                .build();
        apCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                main.post(() -> msg.setText("מחובר לשלט – שולח את פרטי הרשת…"));
                sendOn(network, pkt, true);
            }

            @Override
            public void onUnavailable() {
                main.post(() -> msg.setText("✗ לא נמצאה הרשת של השלט. ודא שהנורית מהבהבת לאט, או חבר ידנית."));
            }
        };
        cm.requestNetwork(req, apCallback);
    }

    /** חיבור ידני: הטלפון כבר מחובר לרשת של השלט. */
    private void sendManual() {
        byte[] pkt = packet();
        if (pkt == null) return;
        Network wifi = null;
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities c = cm.getNetworkCapabilities(n);
            if (c != null && c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = n;
        }
        msg.setText("שולח…");
        sendOn(wifi, pkt, false);
    }

    private void sendOn(Network network, byte[] pkt, boolean releaseAfter) {
        Remote.NET.execute(() -> {
            String err = null;
            try (DatagramSocket sock = new DatagramSocket()) {
                // שולחים דרך ה-Wi-Fi של השלט גם אם אין בו אינטרנט (אחרת אנדרואיד שולח בסלולרי)
                if (network != null) network.bindSocket(sock);
                BroadlinkDevice.sendSetup(sock, pkt);
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String fe = err;
            main.post(() -> {
                if (releaseAfter) releaseAp();
                if (fe != null) { msg.setText("✗ השליחה נכשלה: " + fe); return; }
                msg.setText("✓ נשלח! השלט מתחבר לרשת הביתית (הנורית תפסיק להבהב).\n"
                        + (releaseAfter ? "" : "חבר את הטלפון בחזרה לרשת הביתית. ")
                        + "מחפש אותו בעוד כמה שניות…");
                main.postDelayed(() -> findAndAssign(6), 12000);
            });
        });
    }

    private void releaseAp() {
        if (apCallback != null) {
            try { cm.unregisterNetworkCallback(apCallback); } catch (Exception ignored) {}
            apCallback = null;
        }
    }

    /** מחפש את השלט ברשת הביתית (כמה ניסיונות) ומשייך אותו למזגן הנוכחי. */
    private void findAndAssign(int attempts) {
        msg.setText("מחפש את השלט ברשת הביתית…");
        Context app = getApplicationContext();
        Remote.NET.execute(() -> {
            List<BroadlinkDevice> found = new ArrayList<>();
            for (int i = 0; i < attempts && found.isEmpty(); i++) {
                try {
                    found.addAll(BroadlinkDevice.discover(InetAddress.getByName("255.255.255.255"), 4000));
                } catch (Exception ignored) {}
                if (found.isEmpty()) try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            }
            main.post(() -> onFound(found));
        });
    }

    private void onFound(List<BroadlinkDevice> found) {
        if (isFinishing()) return;
        if (found.isEmpty()) {
            msg.setText("✗ השלט לא נמצא עדיין. ודא שהטלפון חזר לרשת הביתית ולחץ \"חפש את השלט\". "
                    + "אם הנורית עדיין מהבהבת – ייתכן ששם הרשת או הסיסמה שגויים, או שהרשת אינה 2.4GHz.");
            return;
        }
        // מעדיפים שלט שעוד לא משויך לאף מזגן
        List<BroadlinkDevice> free = new ArrayList<>();
        for (BroadlinkDevice d : found) {
            boolean used = false;
            for (AcConfig a : store.acs()) if (a.mac.equals(FujitsuIr.hex(d.macRaw))) used = true;
            if (!used) free.add(d);
        }
        List<BroadlinkDevice> options = free.isEmpty() ? found : free;
        if (options.size() == 1) { assign(options.get(0)); return; }
        String[] names = new String[options.size()];
        for (int i = 0; i < names.length; i++)
            names[i] = (options.get(i).name.isEmpty() ? "RM4" : options.get(i).name) + " · " + options.get(i).host
                    + " · " + options.get(i).macString();
        new AlertDialog.Builder(this).setTitle("איזה שלט שייך ל" + store.current().name + "?")
                .setItems(names, (d, w) -> assign(options.get(w))).show();
    }

    private void assign(BroadlinkDevice d) {
        final int acId = store.current().id;
        msg.setText("מתחבר ל-" + d.host + "…");
        Remote.NET.execute(() -> {
            String err = null;
            try { d.auth(); } catch (Exception e) { err = e.getMessage(); }
            final String fe = err;
            main.post(() -> {
                AcConfig a = store.ac(acId);
                if (a == null) return;
                a.setDevice(d);
                store.saveAc(a);
                if (fe == null) {
                    msg.setText("✓ השלט מחובר ומשויך ל" + a.name + "!");
                    new AlertDialog.Builder(this).setTitle("✓ הכל מוכן")
                            .setMessage("השלט (" + d.host + ") משויך ל" + a.name
                                    + ".\nמומלץ לקבע לו כתובת IP בנתב, ולבדוק בהגדרות שהמזגן מגיב.")
                            .setPositiveButton("סיום", (x, y) -> finish()).show();
                } else {
                    msg.setText("נשמר, אבל ההתחברות נכשלה: " + fe);
                }
            });
        });
    }
}

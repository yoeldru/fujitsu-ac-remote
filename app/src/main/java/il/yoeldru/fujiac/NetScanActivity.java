// נכתב: 2026-11-15 01:07 (שעון ישראל)
// סורק רשת: עובר על כל הכתובות ברשת הביתית, מזהה מה מחובר לכל כתובת
// (שם מהנתב, פורטים פתוחים, כותרת דף אינטרנט, תשובת Broadlink) ומאפשר לנסות לחבר מכשיר כמזגן Wi-Fi.
package il.yoeldru.fujiac;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import il.yoeldru.fujiac.core.AuxAc;
import il.yoeldru.fujiac.core.BroadlinkDevice;
import il.yoeldru.fujiac.core.FujitsuIr;

public class NetScanActivity extends Activity {

    /** פורטים לבדיקה ומה הם מרמזים. */
    static final int[] PORTS = {80, 443, 8080, 62078, 445, 139, 9100, 631, 554, 8008, 8009, 5000, 7000, 6668, 1900, 22};

    static class Host {
        String ip, name = "", title = "", server = "";
        final List<Integer> ports = new ArrayList<>();
        BroadlinkDevice broadlink;
        boolean alive;
        int last() { return Integer.parseInt(ip.substring(ip.lastIndexOf('.') + 1)); }
    }

    private Store store;
    private LinearLayout list;
    private TextView status;
    private Button scanBtn;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private ExecutorService pool;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        LinearLayout col = Ui.screen(this);
        col.addView(Ui.text(this, "סורק רשת", 24, Ui.TEXT, true));
        Ui.spacer(col, 4);
        col.addView(Ui.text(this, "עובר על כל הכתובות ברשת הביתית ומנסה לזהות מה מחובר לכל אחת. "
                + "מכשיר ❓ בלי שם ובלי פורטים פתוחים הוא לרוב מכשיר חכם (מזגן, שקע וכו'). לחיצה על מכשיר – אפשרויות.",
                13, Ui.MUTED, false));
        Ui.spacer(col, 10);
        scanBtn = Ui.primary(this, "🔎 סרוק את הרשת");
        scanBtn.setOnClickListener(v -> { if (!running) scan(); });
        col.addView(scanBtn, new LinearLayout.LayoutParams(-1, -2));
        status = Ui.text(this, "", 14, Ui.ACCENT, true);
        status.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        col.addView(status);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);
        scan();
    }

    @Override
    protected void onDestroy() {
        running = false;
        if (pool != null) pool.shutdownNow();
        super.onDestroy();
    }

    // ---------- סריקה ----------

    @SuppressWarnings("deprecation")
    private int[] subnet() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dh = wm.getDhcpInfo();
        if (dh == null || dh.ipAddress == 0) return null;
        int ip = dh.ipAddress; // little-endian
        return new int[]{ip & 0xFF, (ip >> 8) & 0xFF, (ip >> 16) & 0xFF, (ip >> 24) & 0xFF};
    }

    private void scan() {
        int[] me = subnet();
        if (me == null) { status.setText("✗ הטלפון לא מחובר ל-Wi-Fi"); return; }
        running = true;
        scanBtn.setText("סורק…");
        list.removeAllViews();
        String prefix = me[0] + "." + me[1] + "." + me[2] + ".";
        String myIp = prefix + me[3];
        List<Host> hosts = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger done = new AtomicInteger();
        pool = Executors.newFixedThreadPool(48);
        for (int i = 1; i <= 254; i++) {
            final String ip = prefix + i;
            pool.execute(() -> {
                if (!running) return;
                Host h = probe(ip);
                if (h.alive) hosts.add(h);
                int n = done.incrementAndGet();
                if (n % 8 == 0 || n == 254)
                    main.post(() -> status.setText("סורק… " + n + "/254 · נמצאו " + hosts.size()));
            });
        }
        pool.shutdown();
        new Thread(() -> {
            try { pool.awaitTermination(90, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            // תשובות Broadlink: חיפוש כללי + פנייה ישירה לכל כתובת חיה
            try {
                for (BroadlinkDevice d : BroadlinkDevice.discover(InetAddress.getByName("255.255.255.255"), 3000, true))
                    attach(hosts, d);
            } catch (Exception ignored) {}
            List<Host> snapshot;
            synchronized (hosts) { snapshot = new ArrayList<>(hosts); }
            ExecutorService p2 = Executors.newFixedThreadPool(16);
            for (Host h : snapshot) {
                if (h.broadlink != null) continue;
                p2.execute(() -> {
                    try {
                        List<BroadlinkDevice> r = BroadlinkDevice.discover(InetAddress.getByName(h.ip), 1500);
                        if (!r.isEmpty()) h.broadlink = r.get(0);
                    } catch (Exception ignored) {}
                });
            }
            p2.shutdown();
            try { p2.awaitTermination(30, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            synchronized (hosts) { Collections.sort(hosts, (a, c) -> a.last() - c.last()); }
            main.post(() -> {
                running = false;
                scanBtn.setText("🔎 סרוק שוב");
                status.setText("✓ נמצאו " + hosts.size() + " מכשירים ברשת");
                render(hosts, myIp);
            });
        }).start();
    }

    private static void attach(List<Host> hosts, BroadlinkDevice d) {
        synchronized (hosts) {
            for (Host h : hosts) if (h.ip.equals(d.host)) { h.broadlink = d; return; }
            Host h = new Host();
            h.ip = d.host;
            h.alive = true;
            h.broadlink = d;
            hosts.add(h);
        }
    }

    /** בדיקת כתובת אחת: פורטים, שם מהנתב, וכותרת דף אם יש שרת אינטרנט. */
    static Host probe(String ip) {
        Host h = new Host();
        h.ip = ip;
        for (int port : PORTS) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(ip, port), 250);
                h.ports.add(port);
                h.alive = true;
            } catch (java.net.ConnectException e) {
                // "connection refused" = המכשיר קיים אבל הפורט סגור
                String m = String.valueOf(e.getMessage());
                if (m.contains("ECONNREFUSED") || m.contains("refused")) h.alive = true;
            } catch (Exception ignored) {}
        }
        if (!h.alive) {
            try { h.alive = InetAddress.getByName(ip).isReachable(400); } catch (Exception ignored) {}
        }
        if (!h.alive) return h;
        try {
            String n = InetAddress.getByName(ip).getCanonicalHostName();
            if (n != null && !n.equals(ip)) h.name = n.replaceAll("\\.(home|lan|local|localdomain)$", "");
        } catch (Exception ignored) {}
        if (h.ports.contains(80)) httpInfo(h, 80);
        else if (h.ports.contains(8080)) httpInfo(h, 8080);
        return h;
    }

    static void httpInfo(Host h, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(h.ip, port), 600);
            s.setSoTimeout(1200);
            OutputStream o = s.getOutputStream();
            o.write(("GET / HTTP/1.0\r\nHost: " + h.ip + "\r\n\r\n").getBytes());
            InputStream in = s.getInputStream();
            byte[] buf = new byte[4096];
            int total = 0, n;
            while (total < buf.length && (n = in.read(buf, total, buf.length - total)) > 0) total += n;
            String r = new String(buf, 0, total, "ISO-8859-1");
            Matcher m = Pattern.compile("(?i)\\nserver:\\s*([^\\r\\n]+)").matcher(r);
            if (m.find()) h.server = m.group(1).trim();
            m = Pattern.compile("(?is)<title>(.*?)</title>").matcher(r);
            if (m.find()) h.title = m.group(1).trim();
        } catch (Exception ignored) {}
    }

    // ---------- זיהוי ----------

    String guess(Host h, String myIp) {
        if (h.ip.equals(myIp)) return "📱 הטלפון הזה";
        if (h.broadlink != null) {
            if (h.broadlink.devtype == AuxAc.DEVTYPE) return "❄️ מזגן Wi-Fi (Broadlink/AUX)";
            for (AcConfig a : store.acs())
                if (a.mac.equals(FujitsuIr.hex(h.broadlink.macRaw))) return "🎛️ " + a.name + " (שלט/מזגן באפליקציה)";
            return String.format("🎛️ מכשיר Broadlink (סוג 0x%04X)", h.broadlink.devtype);
        }
        if (h.ip.endsWith(".1") || h.title.toLowerCase().contains("d-link") || h.title.toLowerCase().contains("router"))
            return "🌐 נתב";
        String all = (h.name + " " + h.title + " " + h.server).toLowerCase();
        if (h.ports.contains(62078)) return "📱 אייפון / אייפד";
        if (all.contains("android") || all.contains("galaxy") || all.contains("samsung")) return "📱 טלפון / טאבלט";
        if (h.ports.contains(9100) || h.ports.contains(631) || all.contains("printer") || all.contains("epson")
                || all.contains("hp") || all.contains("canon") || all.contains("brother")) return "🖨️ מדפסת";
        if (h.ports.contains(8008) || h.ports.contains(8009)) return "📺 טלוויזיה / Chromecast";
        if (h.ports.contains(554)) return "📷 מצלמה";
        if (h.ports.contains(445) || h.ports.contains(139)) return "💻 מחשב";
        if (all.contains("tv") || all.contains("tizen") || all.contains("webos")) return "📺 טלוויזיה";
        if (h.ports.contains(6668)) return "🔌 מכשיר Tuya (Smart Life)";
        if (all.contains("hf-") || all.contains("lpb") || all.contains("esp") || all.contains("tasmota"))
            return "❓ מכשיר חכם (מודול Wi-Fi)";
        if (h.ports.isEmpty() && h.name.isEmpty()) return "❓ מכשיר לא מזוהה – ייתכן מזגן / מכשיר חכם";
        return "❓ מכשיר לא מזוהה";
    }

    private void render(List<Host> hosts, String myIp) {
        list.removeAllViews();
        for (Host h : hosts) {
            String g = guess(h, myIp);
            LinearLayout card = Ui.card(this);
            card.addView(Ui.text(this, g, 15.5f, Ui.TEXT, true));
            StringBuilder sb = new StringBuilder(h.ip);
            if (!h.name.isEmpty()) sb.append(" · ").append(h.name);
            if (h.broadlink != null && !h.broadlink.name.isEmpty()) sb.append(" · ").append(h.broadlink.name);
            if (!h.title.isEmpty()) sb.append("\nדף: ").append(h.title);
            if (!h.server.isEmpty()) sb.append("\nשרת: ").append(h.server);
            if (!h.ports.isEmpty()) sb.append("\nפורטים: ").append(h.ports.toString().replaceAll("[\\[\\]]", ""));
            if (h.broadlink != null) sb.append("\nMAC: ").append(h.broadlink.macString());
            card.addView(Ui.text(this, sb.toString(), 13, Ui.MUTED, false));
            card.setOnClickListener(v -> options(h));
            list.addView(card);
        }
    }

    private void options(Host h) {
        new AlertDialog.Builder(this).setTitle(h.ip)
                .setItems(new String[]{"❄️ נסה לחבר כמזגן Wi-Fi", "🌐 פתח בדפדפן"}, (d, w) -> {
                    if (w == 0) tryAsAc(h);
                    else startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("http://" + h.ip)));
                }).show();
    }

    private void tryAsAc(Host h) {
        status.setText("פונה אל " + h.ip + "…");
        Remote.NET.execute(() -> {
            BroadlinkDevice dev = h.broadlink;
            String err = null;
            try {
                if (dev == null) {
                    List<BroadlinkDevice> r = BroadlinkDevice.discover(InetAddress.getByName(h.ip), 6000);
                    if (!r.isEmpty()) dev = r.get(0);
                }
                if (dev == null) throw new Exception("המכשיר לא ענה בפרוטוקול של Broadlink – אם זה המזגן, הוא חסום לשליטה מקומית ויש לצמד מחדש");
                dev.auth();
                AuxAc.Status st = AuxAc.read(dev);
                AcConfig a = new AcConfig();
                a.id = store.nextId();
                a.kind = "aux";
                a.name = "מזגן " + (store.acs().size() + 1);
                a.setDevice(dev);
                a.state = Remote.toAppState(st, a.state);
                store.saveAc(a);
                store.setCurrent(a.id);
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String fe = err;
            main.post(() -> {
                if (fe == null) {
                    status.setText("✓ המזגן נוסף לאפליקציה!");
                    new AlertDialog.Builder(this).setTitle("✓ המזגן נוסף")
                            .setMessage("תן לו שם בהגדרות, ומומלץ לקבע לו כתובת IP בנתב.")
                            .setPositiveButton("אישור", null).show();
                } else {
                    status.setText("✗ " + fe);
                }
            });
        });
    }
}

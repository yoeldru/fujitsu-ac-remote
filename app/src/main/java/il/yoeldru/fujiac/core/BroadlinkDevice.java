// נכתב: 2026-11-05 07:54 (שעון ישראל) · עודכן: 2026-11-05 15:04 - הגדרת שלט חדש (מצב AP)
// תקשורת מקומית (UDP, פורט 80) עם Broadlink RM4 mini ברשת הביתית - ללא ענן.
// מימוש הפרוטוקול לפי python-broadlink: גילוי, אימות (AES-128-CBC), שליחת IR ולימוד קודים.
package il.yoeldru.fujiac.core;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class BroadlinkDevice {
    public static final int PORT = 80;
    static final byte[] INIT_KEY = FujitsuIr.unhex("097628343fe99e23765c1513accf8b02");
    static final byte[] IV = FujitsuIr.unhex("562e17996d093d28ddb3ba695a2e6f58");

    public final String host;
    /** כתובת ה-MAC בסדר הבתים שבו היא נשלחת בחבילה (כפי שמגיעה בתשובת הגילוי). */
    public final byte[] macRaw;
    public final int devtype;
    public String name = "";
    public boolean locked = false;

    int count;
    int id = 0;
    byte[] key = INIT_KEY.clone();
    boolean authed = false;
    public int timeoutMs = 4000;

    public BroadlinkDevice(String host, byte[] macRaw, int devtype) {
        this.host = host;
        this.macRaw = macRaw;
        this.devtype = devtype;
        this.count = (int) (Math.random() * 0x7FFF);
    }

    /** MAC בתצוגה רגילה (aa:bb:cc...). */
    public String macString() {
        StringBuilder sb = new StringBuilder();
        for (int i = macRaw.length - 1; i >= 0; i--) {
            sb.append(String.format("%02X", macRaw[i] & 0xFF));
            if (i > 0) sb.append(':');
        }
        return sb.toString();
    }

    public boolean isLegacy() {
        // RM mini 3 ישנים (0x27xx) משתמשים בפורמט פקודה ללא שדה אורך
        return (devtype & 0xFF00) == 0x2700;
    }

    // ---------- גילוי ----------

    static byte[] helloPacket() {
        byte[] p = new byte[0x30];
        Calendar c = Calendar.getInstance();
        int offset = TimeZone.getDefault().getOffset(c.getTimeInMillis()) / 3600000;
        putLE(p, 0x08, offset, 4);
        putLE(p, 0x0C, c.get(Calendar.YEAR), 2);
        p[0x0E] = (byte) c.get(Calendar.MINUTE);
        p[0x0F] = (byte) c.get(Calendar.HOUR_OF_DAY);
        p[0x10] = (byte) (c.get(Calendar.YEAR) % 100);
        int dow = c.get(Calendar.DAY_OF_WEEK); // 1=ראשון
        p[0x11] = (byte) (dow == Calendar.SUNDAY ? 7 : dow - 1);
        p[0x12] = (byte) c.get(Calendar.DAY_OF_MONTH);
        p[0x13] = (byte) (c.get(Calendar.MONTH) + 1);
        p[0x26] = 6;
        int cs = checksum(p, 0, p.length);
        putLE(p, 0x20, cs, 2);
        return p;
    }

    /** שולח הודעת hello לכתובת (broadcast או IP ספציפי) ומחזיר את המכשירים שענו. */
    public static List<BroadlinkDevice> discover(InetAddress target, int timeoutMs) throws IOException {
        List<BroadlinkDevice> found = new ArrayList<>();
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.setBroadcast(true);
            sock.setSoTimeout(500);
            byte[] hello = helloPacket();
            long end = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < end) {
                sock.send(new DatagramPacket(hello, hello.length, target, PORT));
                long roundEnd = Math.min(end, System.currentTimeMillis() + 1000);
                while (System.currentTimeMillis() < roundEnd) {
                    byte[] buf = new byte[1024];
                    DatagramPacket dp = new DatagramPacket(buf, buf.length);
                    try {
                        sock.receive(dp);
                    } catch (SocketTimeoutException e) {
                        continue;
                    }
                    byte[] r = Arrays.copyOf(buf, dp.getLength());
                    if (r.length < 0x40) continue;
                    int devtype = (r[0x34] & 0xFF) | ((r[0x35] & 0xFF) << 8);
                    byte[] mac = Arrays.copyOfRange(r, 0x3A, 0x40);
                    String ip = dp.getAddress().getHostAddress();
                    boolean dup = false;
                    for (BroadlinkDevice d : found) if (d.host.equals(ip)) dup = true;
                    if (dup) continue;
                    BroadlinkDevice d = new BroadlinkDevice(ip, mac, devtype);
                    int n = 0x40;
                    while (n < r.length && r[n] != 0) n++;
                    d.name = new String(r, 0x40, n - 0x40, "UTF-8");
                    d.locked = r.length > 0x7F && r[0x7F] != 0;
                    found.add(d);
                }
                if (!found.isEmpty()) break;
            }
        }
        return found;
    }

    // ---------- הגדרה ראשונית (מצב AP, אחרי ריסט) ----------

    public static final int SEC_NONE = 0, SEC_WEP = 1, SEC_WPA1 = 2, SEC_WPA2 = 3, SEC_WPA12 = 4;

    /** חבילת ההגדרה: שם רשת + סיסמה + סוג אבטחה (כמו python-broadlink setup). */
    public static byte[] setupPacket(String ssid, String password, int security) {
        byte[] p = new byte[0x88];
        p[0x26] = 0x14;
        byte[] s = ssid.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] w = password.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (s.length > 32 || w.length > 32) throw new IllegalArgumentException("שם רשת או סיסמה ארוכים מ-32 תווים");
        System.arraycopy(s, 0, p, 0x44, s.length);
        System.arraycopy(w, 0, p, 0x64, w.length);
        p[0x84] = (byte) s.length;
        p[0x85] = (byte) w.length;
        p[0x86] = (byte) security;
        putLE(p, 0x20, checksum(p, 0, p.length), 2);
        return p;
    }

    /** שולח את חבילת ההגדרה כ-broadcast דרך socket נתון (שכבר קשור לרשת של השלט). */
    public static void sendSetup(DatagramSocket sock, byte[] packet) throws IOException {
        sock.setBroadcast(true);
        InetAddress bc = InetAddress.getByName("255.255.255.255");
        for (int i = 0; i < 3; i++) {
            sock.send(new DatagramPacket(packet, packet.length, bc, PORT));
            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
        }
    }

    // ---------- הצפנה ----------

    byte[] crypt(int mode, byte[] data) {
        try {
            Cipher c = Cipher.getInstance("AES/CBC/NoPadding");
            c.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(IV));
            return c.doFinal(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- חבילות ----------

    byte[] buildPacket(int packetType, byte[] payload) {
        count = ((count + 1) | 0x8000) & 0xFFFF;
        byte[] header = new byte[0x38];
        byte[] magic = {0x5a, (byte) 0xa5, (byte) 0xaa, 0x55, 0x5a, (byte) 0xa5, (byte) 0xaa, 0x55};
        System.arraycopy(magic, 0, header, 0, 8);
        putLE(header, 0x24, devtype, 2);
        putLE(header, 0x26, packetType, 2);
        putLE(header, 0x28, count, 2);
        System.arraycopy(macRaw, 0, header, 0x2A, 6);
        putLE(header, 0x30, id, 4);
        putLE(header, 0x34, checksum(payload, 0, payload.length), 2);

        int pad = (16 - payload.length % 16) % 16;
        byte[] padded = Arrays.copyOf(payload, payload.length + pad);
        byte[] enc = crypt(Cipher.ENCRYPT_MODE, padded);

        byte[] packet = new byte[0x38 + enc.length];
        System.arraycopy(header, 0, packet, 0, 0x38);
        System.arraycopy(enc, 0, packet, 0x38, enc.length);
        putLE(packet, 0x20, checksum(packet, 0, packet.length), 2);
        return packet;
    }

    synchronized byte[] sendPacket(int packetType, byte[] payload) throws IOException {
        byte[] packet = buildPacket(packetType, payload);
        InetAddress addr = InetAddress.getByName(host);
        try (DatagramSocket sock = new DatagramSocket()) {
            sock.setSoTimeout(1000);
            long end = System.currentTimeMillis() + timeoutMs;
            while (true) {
                sock.send(new DatagramPacket(packet, packet.length, addr, PORT));
                byte[] buf = new byte[2048];
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                try {
                    sock.receive(dp);
                    byte[] resp = Arrays.copyOf(buf, dp.getLength());
                    if (resp.length < 0x30) throw new BroadlinkException(-4007, "תשובה קצרה מדי מהמכשיר");
                    int err = (short) ((resp[0x22] & 0xFF) | ((resp[0x23] & 0xFF) << 8));
                    if (err != 0) throw new BroadlinkException(err, describe(err));
                    return resp;
                } catch (SocketTimeoutException e) {
                    if (System.currentTimeMillis() > end)
                        throw new BroadlinkException(-4000, "אין תשובה מהשלט החכם (" + host + ")");
                }
            }
        }
    }

    byte[] decryptPayload(byte[] resp) {
        if (resp.length <= 0x38) return new byte[0];
        byte[] enc = Arrays.copyOfRange(resp, 0x38, resp.length);
        if (enc.length % 16 != 0) enc = Arrays.copyOf(enc, enc.length - enc.length % 16);
        return crypt(Cipher.DECRYPT_MODE, enc);
    }

    public synchronized void auth() throws IOException {
        id = 0;
        key = INIT_KEY.clone();
        byte[] p = new byte[0x50];
        for (int i = 0x04; i < 0x14; i++) p[i] = 0x31;
        p[0x1E] = 0x01;
        p[0x2D] = 0x01;
        byte[] name = "Test 1".getBytes();
        System.arraycopy(name, 0, p, 0x30, name.length);
        byte[] resp = sendPacket(0x65, p);
        byte[] payload = decryptPayload(resp);
        if (payload.length < 0x14) throw new BroadlinkException(-1, "אימות נכשל");
        id = (int) getLE(payload, 0, 4);
        key = Arrays.copyOfRange(payload, 4, 20);
        authed = true;
    }

    /** שולח פקודת שלט (0x6A) ומחזיר את נתוני התשובה. */
    synchronized byte[] command(int cmd, byte[] data) throws IOException {
        if (!authed) auth();
        try {
            return commandOnce(cmd, data);
        } catch (BroadlinkException e) {
            // ייתכן שהמפתח פג - מתחברים מחדש פעם אחת
            if (e.code == -7|| e.code == -2 || e.code == -4012 || e.code == -1) {
                auth();
                return commandOnce(cmd, data);
            }
            throw e;
        }
    }

    byte[] commandOnce(int cmd, byte[] data) throws IOException {
        byte[] packet;
        if (isLegacy()) {
            packet = new byte[4 + data.length];
            putLE(packet, 0, cmd, 4);
            System.arraycopy(data, 0, packet, 4, data.length);
        } else {
            packet = new byte[6 + data.length];
            putLE(packet, 0, data.length + 4, 2);
            putLE(packet, 2, cmd, 4);
            System.arraycopy(data, 0, packet, 6, data.length);
        }
        byte[] resp = sendPacket(0x6A, packet);
        byte[] payload = decryptPayload(resp);
        if (isLegacy()) return payload.length > 4 ? Arrays.copyOfRange(payload, 4, payload.length) : new byte[0];
        if (payload.length < 6) return new byte[0];
        int pLen = (int) getLE(payload, 0, 2);
        int end = Math.min(payload.length, pLen + 2);
        return end > 6 ? Arrays.copyOfRange(payload, 6, end) : new byte[0];
    }

    public void sendIr(byte[] broadlinkIr) throws IOException {
        command(0x02, broadlinkIr);
    }

    public void enterLearning() throws IOException {
        command(0x03, new byte[0]);
    }

    /** מחזיר קוד שנלמד, או null אם עדיין לא נקלט. */
    public byte[] checkLearned() throws IOException {
        try {
            byte[] d = command(0x04, new byte[0]);
            return (d.length > 4) ? d : null;
        } catch (BroadlinkException e) {
            if (e.code == -4000) throw e;
            return null;
        }
    }

    // ---------- עזר ----------

    static int checksum(byte[] b, int from, int to) {
        int s = 0xBEAF;
        for (int i = from; i < to; i++) s += b[i] & 0xFF;
        return s & 0xFFFF;
    }

    static void putLE(byte[] b, int off, long v, int n) {
        for (int i = 0; i < n; i++) b[off + i] = (byte) ((v >> (8 * i)) & 0xFF);
    }

    static long getLE(byte[] b, int off, int n) {
        long v = 0;
        for (int i = 0; i < n; i++) v |= (long) (b[off + i] & 0xFF) << (8 * i);
        return v;
    }

    static String describe(int code) {
        switch (code) {
            case -1: return "האימות נכשל - בדוק שהמכשיר לא 'נעול' באפליקציית Broadlink";
            case -2: return "החיבור נותק";
            case -3: return "המכשיר לא מחובר";
            case -4: return "הפקודה לא נתמכת במכשיר";
            case -7: return "מפתח השליטה פג תוקף";
            case -8: return "שגיאת שליחה";
            case -10: return "אין קוד שנלמד";
            default: return "שגיאה מהמכשיר (" + code + ")";
        }
    }

    public static class BroadlinkException extends IOException {
        public final int code;
        public BroadlinkException(int code, String msg) {
            super(msg);
            this.code = code;
        }
    }
}

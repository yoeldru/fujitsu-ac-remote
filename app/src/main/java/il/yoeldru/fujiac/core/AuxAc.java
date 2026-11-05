// נכתב: 2026-11-05 23:09 (שעון ישראל)
// שליטה מקומית במזגני Wi-Fi מבוססי Broadlink (AUX / טורנדו / AC Freedom) - בלי ענן.
// מבנה החבילות לפי broadlink_ac_mqtt ו-broadlinkac2mqtt (devtype 0x4E2A).
package il.yoeldru.fujiac.core;

import java.io.IOException;

public final class AuxAc {
    private AuxAc() {}

    public static final int DEVTYPE = 0x4E2A;

    // ערכי מצב בפרוטוקול
    public static final int MODE_AUTO = 0, MODE_COOL = 1, MODE_DRY = 2, MODE_HEAT = 4, MODE_FAN = 6;
    // מהירות מאוורר
    public static final int FAN_HIGH = 1, FAN_MED = 2, FAN_LOW = 3, FAN_AUTO = 5;
    // תריס אנכי: 1=למעלה ... 5=למטה, 6=נדנוד, 7=אוטומטי
    public static final int V_TOP = 1, V_BOTTOM = 5, V_SWING = 6, V_AUTO = 7;
    // תריס אופקי (ניסיוני): 0=נדנוד, 7=קבוע
    public static final int H_SWING = 0, H_FIXED = 7;

    static final byte[] GET_STATES = FujitsuIr.unhex("0C00BB0006800000020011012B7E0000");
    static final byte[] GET_INFO = FujitsuIr.unhex("0C00BB0006800000020021011B7E0000");

    /** המצב הגולמי של המזגן. */
    public static class Status {
        public double temp = 24;
        public int power, vertical = V_AUTO, horizontal = H_FIXED, mode = MODE_COOL, fan = FAN_AUTO;
        public int sleep, display, mildew, health, mute, turbo, clean;
        public Double ambient;   // טמפרטורת החדר, אם ידועה

        public Status copy() {
            Status s = new Status();
            s.temp = temp; s.power = power; s.vertical = vertical; s.horizontal = horizontal; s.mode = mode;
            s.fan = fan; s.sleep = sleep; s.display = display; s.mildew = mildew; s.health = health;
            s.mute = mute; s.turbo = turbo; s.clean = clean; s.ambient = ambient;
            return s;
        }
    }

    /** קריאת המצב האמיתי מהמזגן. */
    public static Status read(BroadlinkDevice d) throws IOException {
        byte[] r = d.rawCommand(GET_STATES);
        if (r.length < 2 + 23 || (r[4] & 0xFF) != 0x07 || (r[0] & 0xFF) != 0x19)
            throw new IOException("תשובה לא צפויה מהמזגן");
        int[] p = new int[r.length - 2];
        for (int i = 0; i < p.length; i++) p[i] = r[i + 2] & 0xFF;
        Status s = new Status();
        s.temp = 8 + (p[10] >> 3) + 0.5 * (p[12] >> 7);
        s.vertical = p[10] & 0x07;
        s.horizontal = (p[11] >> 5) & 0x07;
        s.fan = (p[13] >> 5) & 0x07;
        s.mute = (p[14] >> 7) & 1;
        s.turbo = (p[14] >> 6) & 1;
        s.mode = (p[15] >> 5) & 0x07;
        s.sleep = (p[15] >> 2) & 1;
        s.power = (p[18] >> 5) & 1;
        s.health = (p[18] >> 1) & 1;
        s.clean = (p[18] >> 2) & 1;
        s.display = (p[20] >> 4) & 1;
        s.mildew = (p[20] >> 3) & 1;
        if (s.temp < 16) throw new IOException("קריאת טמפרטורה לא תקינה");
        try {
            byte[] a = d.rawCommand(GET_INFO);
            if (a.length >= 2 + 32) {
                int x15 = a[2 + 15] & 0xFF, x31 = a[2 + 31] & 0xFF;
                double amb = (x15 - 32) + x31 / 10.0;
                if (amb > -10 && amb < 60) s.ambient = amb;
            }
        } catch (IOException ignored) {}
        return s;
    }

    /** בניית פקודת "קבע מצב" (23 בתים + אורך + checksum). */
    public static byte[] buildSet(Status s) {
        double t = Math.max(16, Math.min(32, s.temp));
        int temp = (int) Math.floor(t) - 8;
        int half = (t - Math.floor(t)) >= 0.5 ? 1 : 0;
        int[] p = new int[23];
        p[0] = 0xbb; p[1] = 0x00; p[2] = 0x06; p[3] = 0x80; p[4] = 0x00; p[5] = 0x00;
        p[6] = 0x0f; p[7] = 0x00; p[8] = 0x01; p[9] = 0x01;
        p[10] = ((temp << 3) | (s.vertical & 0x07)) & 0xFF;
        p[11] = ((s.horizontal & 0x07) << 5) & 0xFF;
        p[12] = (0x0F | (half << 7)) & 0xFF;
        p[13] = ((s.fan & 0x07) << 5) & 0xFF;
        p[14] = ((s.turbo << 6) | (s.mute << 7)) & 0xFF;
        p[15] = ((s.mode << 5) | (s.sleep << 2)) & 0xFF;
        p[16] = 0; p[17] = 0;
        p[18] = ((s.power << 5) | (s.health << 1) | (s.clean << 2)) & 0xFF;
        p[19] = 0;
        p[20] = ((s.display << 4) | (s.mildew << 3)) & 0xFF;
        p[21] = 0; p[22] = 0;

        int sum = 0;
        for (int i = 0; i < p.length; i += 2) sum += (p[i] << 8) + (i + 1 < p.length ? p[i + 1] : 0);
        sum = (sum >> 16) + (sum & 0xFFFF);
        sum = ~sum & 0xFFFF;

        byte[] out = new byte[32];
        out[0] = (byte) (p.length + 2);
        for (int i = 0; i < p.length; i++) out[2 + i] = (byte) p[i];
        out[p.length + 2] = (byte) ((sum >> 8) & 0xFF);
        out[p.length + 3] = (byte) (sum & 0xFF);
        return out;
    }

    public static void write(BroadlinkDevice d, Status s) throws IOException {
        d.rawCommand(buildSet(s));
    }
}

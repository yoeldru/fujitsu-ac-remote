// נכתב: 2026-11-05 07:54 (שעון ישראל) · עודכן: 2026-11-05 10:30 - קוד שלט A-D
// מקודד קודי IR של מזגני פוג'יטסו (שלטים AR-RAH2E / AR-REB1E / AR-RY4 / AR-REW4E)
// והמרתם לפורמט החבילה של Broadlink. מבוסס על תיעוד הפרוטוקול של IRremoteESP8266.
package il.yoeldru.fujiac.core;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class FujitsuIr {
    private FujitsuIr() {}

    // מצבים
    public static final int MODE_AUTO = 0, MODE_COOL = 1, MODE_DRY = 2, MODE_FAN = 3, MODE_HEAT = 4;
    // מאוורר
    public static final int FAN_AUTO = 0, FAN_HIGH = 1, FAN_MED = 2, FAN_LOW = 3, FAN_QUIET = 4;
    // נדנוד תריסים
    public static final int SWING_OFF = 0, SWING_VERT = 1, SWING_HORIZ = 2, SWING_BOTH = 3;

    // פקודות קצרות
    public static final int CMD_TURN_OFF = 0x02;
    public static final int CMD_ECONO = 0x09;
    public static final int CMD_POWERFUL = 0x39;
    public static final int CMD_STEP_VERT = 0x6C;          // הזזת התריס האנכי צעד אחד (למעלה/למטה)
    public static final int CMD_TOGGLE_SWING_VERT = 0x6D;
    public static final int CMD_STEP_HORIZ = 0x79;         // הזזת התריס האופקי צעד אחד (ימינה/שמאלה)
    public static final int CMD_TOGGLE_SWING_HORIZ = 0x7A;

    // דגמי שלט
    public static final int MODEL_ARRAH2E = 0;  // ברירת מחדל - רוב דגמי ASYG/ASYA
    public static final int MODEL_ARREW4E = 1;  // טמפרטורה בחצאי מעלות, פרוטוקול 0x31

    // תזמוני IR במיקרו-שניות
    static final int HDR_MARK = 3324, HDR_SPACE = 1574, BIT_MARK = 448,
            ONE_SPACE = 1182, ZERO_SPACE = 390, GAP = 8100;

    /** בונה קוד מלא (16 בתים) שמכיל את כל מצב המזגן. */
    public static byte[] buildLong(int model, int remoteId, boolean turnOn, double tempC, int mode, int fan, int swing) {
        int[] s = new int[16];
        s[0] = 0x14; s[1] = 0x63;
        s[2] = (remoteId & 0x03) << 4;  // קוד שלט A/B/C/D
        s[3] = 0x10; s[4] = 0x10;
        s[5] = 0xFE;           // קוד ארוך
        s[6] = 16 - 7;         // מספר הבתים שאחרי בית זה
        s[7] = (model == MODEL_ARREW4E) ? 0x31 : 0x30;

        double t = Math.max(16, Math.min(30, tempC));
        int tempField;
        if (model == MODEL_ARREW4E) tempField = (int) Math.round((t - 8) * 2);
        else tempField = (int) Math.round(t - 16) * 4;
        s[8] = (turnOn ? 1 : 0) | ((tempField & 0x3F) << 2);
        s[9] = mode & 0x07;
        s[10] = (fan & 0x07) | ((swing & 0x03) << 4);
        s[11] = 0; s[12] = 0; s[13] = 0;   // ללא טיימרים של המזגן עצמו
        s[14] = (model == MODEL_ARREW4E) ? 0x00 : 0x20;
        int sum = 0;
        for (int i = 7; i < 15; i++) sum += s[i];
        s[15] = (0 - sum) & 0xFF;
        return toBytes(s);
    }

    /** בונה קוד קצר (7 בתים) לפקודה בודדת: כיבוי, הזזת תריס, אקונומי וכו'. */
    public static byte[] buildShort(int remoteId, int cmd) {
        int[] s = {0x14, 0x63, (remoteId & 0x03) << 4, 0x10, 0x10, cmd & 0xFF, (~cmd) & 0xFF};
        return toBytes(s);
    }

    /** ממיר בתים לרצף פולסים (סימן/רווח) במיקרו-שניות, LSB first. */
    public static List<Integer> toPulses(byte[] data) {
        List<Integer> p = new ArrayList<>();
        p.add(HDR_MARK);
        p.add(HDR_SPACE);
        for (byte b : data) {
            int v = b & 0xFF;
            for (int i = 0; i < 8; i++) {
                p.add(BIT_MARK);
                p.add(((v >> i) & 1) == 1 ? ONE_SPACE : ZERO_SPACE);
            }
        }
        p.add(BIT_MARK);
        p.add(GAP);
        return p;
    }

    /** ממיר פולסים לחבילת IR בפורמט Broadlink (0x26 ...). */
    public static byte[] pulsesToBroadlink(List<Integer> pulses) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (int us : pulses) {
            int ticks = (int) Math.floor(us / 32.84);
            int div = ticks / 256, mod = ticks % 256;
            if (div != 0) {
                body.write(0);
                body.write(div);
            }
            body.write(mod);
        }
        body.write(0x0D);
        body.write(0x05);
        byte[] b = body.toByteArray();
        byte[] out = new byte[4 + b.length];
        out[0] = 0x26;
        out[1] = 0x00;                 // ללא חזרות
        out[2] = (byte) (b.length & 0xFF);
        out[3] = (byte) ((b.length >> 8) & 0xFF);
        System.arraycopy(b, 0, out, 4, b.length);
        return out;
    }

    public static byte[] toBroadlink(byte[] fujitsuCode) {
        return pulsesToBroadlink(toPulses(fujitsuCode));
    }

    private static byte[] toBytes(int[] s) {
        byte[] b = new byte[s.length];
        for (int i = 0; i < s.length; i++) b[i] = (byte) s[i];
        return b;
    }

    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString();
    }

    public static byte[] unhex(String s) {
        s = s.replaceAll("[^0-9a-fA-F]", "");
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++)
            b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return b;
    }
}

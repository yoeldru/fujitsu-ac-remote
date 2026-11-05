// נכתב: 2026-11-05 07:54 (שעון ישראל) · עודכן: 2026-11-05 23:09 - מזגני IR (RM4) ומזגני Wi-Fi (טורנדו/AUX)
// שכבת השליחה: מצב/פקודה → קוד IR דרך ה-RM4, או פקודה ישירה למזגן Wi-Fi. הכל ברקע.
package il.yoeldru.fujiac;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import il.yoeldru.fujiac.core.AuxAc;
import il.yoeldru.fujiac.core.BroadlinkDevice;
import il.yoeldru.fujiac.core.FujitsuIr;

public final class Remote {
    private Remote() {}

    public static final ExecutorService NET = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** חיבורים מאומתים לפי כתובת+MAC, כדי לא להתחבר מחדש בכל לחיצה. */
    private static final Map<String, BroadlinkDevice> CACHE = new HashMap<>();
    /** המצב האחרון שנקרא ממזגני Wi-Fi (שומר ביטים שהאפליקציה לא מנהלת). */
    private static final Map<Integer, AuxAc.Status> AUX_LAST = new HashMap<>();

    public interface Callback { void done(String error); }
    public interface StatusCallback { void done(AuxAc.Status status, String error); }

    public static synchronized BroadlinkDevice device(AcConfig ac) {
        if (ac == null || !ac.hasDevice()) return null;
        String key = ac.host + "|" + ac.mac + "|" + ac.devtype;
        BroadlinkDevice d = CACHE.get(key);
        if (d == null) {
            d = ac.device();
            CACHE.put(key, d);
        }
        return d;
    }

    // ---------- IR ----------

    public static byte[] irForState(AcConfig ac, AcState s, boolean turnOn) {
        if (!s.power) return irForShort(ac, FujitsuIr.CMD_TURN_OFF);
        boolean bit = turnOn || ac.alwaysPower;
        return FujitsuIr.toBroadlink(FujitsuIr.buildLong(ac.model, ac.remoteId, bit, s.temp, s.mode, s.fan, s.swing));
    }

    public static byte[] irForShort(AcConfig ac, int cmd) {
        return FujitsuIr.toBroadlink(FujitsuIr.buildShort(ac.remoteId, cmd));
    }

    public static void sendBlocking(AcConfig ac, byte[] ir) throws IOException {
        BroadlinkDevice d = device(ac);
        if (d == null) throw new IOException("ל" + ac.name + " לא הוגדר שלט חכם - היכנס להגדרות");
        d.sendIr(ir);
    }

    // ---------- Wi-Fi (טורנדו / AUX) ----------

    static final int[] AUX_MODE = {AuxAc.MODE_AUTO, AuxAc.MODE_COOL, AuxAc.MODE_DRY, AuxAc.MODE_FAN, AuxAc.MODE_HEAT};
    static final int[] AUX_FAN = {AuxAc.FAN_AUTO, AuxAc.FAN_HIGH, AuxAc.FAN_MED, AuxAc.FAN_LOW, AuxAc.FAN_LOW};

    private static AuxAc.Status auxBase(AcConfig ac, BroadlinkDevice d) {
        try {
            AuxAc.Status s = AuxAc.read(d);
            AUX_LAST.put(ac.id, s);
            return s.copy();
        } catch (IOException e) {
            AuxAc.Status last = AUX_LAST.get(ac.id);
            return last != null ? last.copy() : new AuxAc.Status();
        }
    }

    /** ממיר את מצב האפליקציה למצב המזגן (שומר את שאר ההגדרות שהמזגן מדווח). */
    static void applyTo(AuxAc.Status a, AcState s) {
        a.power = s.power ? 1 : 0;
        a.temp = s.temp;
        a.mode = AUX_MODE[Math.max(0, Math.min(4, s.mode))];
        a.fan = AUX_FAN[Math.max(0, Math.min(4, s.fan))];
        a.mute = s.fan == 4 ? 1 : 0;
        boolean v = s.swing == 1 || s.swing == 3, h = s.swing == 2 || s.swing == 3;
        if (v) a.vertical = AuxAc.V_SWING;
        else if (a.vertical == AuxAc.V_SWING || a.vertical == 0) a.vertical = 3;  // עצירה במרכז
        a.horizontal = h ? AuxAc.H_SWING : AuxAc.H_FIXED;
    }

    public static AuxAc.Status auxReadBlocking(AcConfig ac) throws IOException {
        BroadlinkDevice d = device(ac);
        if (d == null) throw new IOException(ac.name + " לא מחובר – היכנס להגדרות");
        AuxAc.Status s = AuxAc.read(d);
        AUX_LAST.put(ac.id, s);
        return s;
    }

    // ---------- ממשק אחיד ----------

    /** שולח מצב מלא למזגן (IR או Wi-Fi). */
    public static void applyBlocking(AcConfig ac, AcState s, boolean turnOn) throws IOException {
        if (ac.isWifi()) {
            BroadlinkDevice d = device(ac);
            if (d == null) throw new IOException(ac.name + " לא מחובר – היכנס להגדרות");
            AuxAc.Status a = auxBase(ac, d);
            applyTo(a, s);
            a.turbo = 0;
            AuxAc.write(d, a);
            AUX_LAST.put(ac.id, a);
        } else {
            sendBlocking(ac, irForState(ac, s, turnOn));
        }
    }

    /** פקודה קצרה: הזזת תריס, אקונומי, עוצמתי. מחזיר הודעת שגיאה אם לא נתמך. */
    public static void commandBlocking(AcConfig ac, int cmd) throws IOException {
        if (!ac.isWifi()) { sendBlocking(ac, irForShort(ac, cmd)); return; }
        BroadlinkDevice d = device(ac);
        if (d == null) throw new IOException(ac.name + " לא מחובר – היכנס להגדרות");
        AuxAc.Status a = auxBase(ac, d);
        switch (cmd) {
            case FujitsuIr.CMD_STEP_VERT:
                // מיקומים 1 (למעלה) עד 5 (למטה) במחזוריות
                a.vertical = (a.vertical >= AuxAc.V_TOP && a.vertical < AuxAc.V_BOTTOM) ? a.vertical + 1 : AuxAc.V_TOP;
                break;
            case FujitsuIr.CMD_STEP_HORIZ:
                a.horizontal = a.horizontal == AuxAc.H_SWING ? AuxAc.H_FIXED : AuxAc.H_SWING;
                break;
            case FujitsuIr.CMD_POWERFUL:
                a.turbo = a.turbo == 1 ? 0 : 1;
                if (a.turbo == 1) a.mute = 0;
                break;
            case FujitsuIr.CMD_ECONO:
                a.sleep = a.sleep == 1 ? 0 : 1;   // במזגני AUX - מצב שינה/חיסכון
                break;
            default:
                return;
        }
        AuxAc.write(d, a);
        AUX_LAST.put(ac.id, a);
    }

    public static void apply(AcConfig ac, AcState s, boolean turnOn, Callback cb) {
        run(() -> applyBlocking(ac, s, turnOn), cb);
    }

    public static void command(AcConfig ac, int cmd, Callback cb) {
        run(() -> commandBlocking(ac, cmd), cb);
    }

    public static void auxRead(AcConfig ac, StatusCallback cb) {
        NET.execute(() -> {
            AuxAc.Status s = null;
            String err = null;
            try { s = auxReadBlocking(ac); } catch (IOException e) { err = msg(e); }
            final AuxAc.Status fs = s;
            final String fe = err;
            MAIN.post(() -> cb.done(fs, fe));
        });
    }

    /** שליחה אסינכרונית של קוד IR גולמי (כפתורים נלמדים). */
    public static void send(AcConfig ac, byte[] ir, Callback cb) {
        run(() -> sendBlocking(ac, ir), cb);
    }

    interface Job { void run() throws IOException; }

    private static void run(Job job, Callback cb) {
        NET.execute(() -> {
            String err = null;
            try { job.run(); } catch (IOException e) { err = msg(e); }
            final String fErr = err;
            if (cb != null) MAIN.post(() -> cb.done(fErr));
        });
    }

    private static String msg(Exception e) { return e.getMessage() == null ? e.toString() : e.getMessage(); }

    /** ממיר מצב שנקרא ממזגן Wi-Fi למצב האפליקציה. */
    public static AcState toAppState(AuxAc.Status a, AcState base) {
        AcState s = base.copy();
        s.power = a.power == 1;
        s.temp = (int) Math.round(a.temp);
        for (int i = 0; i < AUX_MODE.length; i++) if (AUX_MODE[i] == a.mode) s.mode = i;
        if (a.mute == 1) s.fan = 4;
        else for (int i = 0; i < 4; i++) if (AUX_FAN[i] == a.fan) s.fan = i;
        boolean v = a.vertical == AuxAc.V_SWING, h = a.horizontal == AuxAc.H_SWING;
        s.swing = v && h ? 3 : v ? 1 : h ? 2 : 0;
        return s;
    }
}

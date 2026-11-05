// נכתב: 2026-11-05 07:54 (שעון ישראל) · עודכן: 2026-11-05 23:22 - IR (RM4), Wi-Fi מקומי, וענן (טורנדו/AUX)
// שכבת השליחה: מצב/פקודה → קוד IR דרך ה-RM4, או פקודה ישירה למזגן Wi-Fi. הכל ברקע.
package il.yoeldru.fujiac;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import il.yoeldru.fujiac.core.AuxAc;
import il.yoeldru.fujiac.core.AuxCloud;
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

    static Context appContext;
    private static AuxCloud cloud;
    private static String cloudKey = "";

    public static final AuxCloud.Profile[] PROFILES = {AuxCloud.AC_FREEDOM};

    /** לקוח הענן לפי החשבון השמור (נוצר מחדש אם החשבון השתנה). */
    public static synchronized AuxCloud cloud() throws IOException {
        if (appContext == null) throw new IOException("אין הקשר אפליקציה");
        Store st = new Store(appContext);
        if (!st.hasCloudAccount()) throw new IOException("לא הוגדר חשבון ענן – היכנס להגדרות ← חשבון ענן");
        String key = st.cloudUser() + "|" + st.cloudPass() + "|" + st.cloudProfile();
        if (cloud == null || !key.equals(cloudKey)) {
            AuxCloud.Profile prof = PROFILES[Math.max(0, Math.min(PROFILES.length - 1, st.cloudProfile()))];
            cloud = new AuxCloud(prof, st.cloudServer(), st.cloudUser(), st.cloudPass());
            cloudKey = key;
        }
        return cloud;
    }

    public static synchronized void resetCloud() { cloud = null; cloudKey = ""; }

    public interface Callback { void done(String error); }
    public interface StatusCallback { void done(AuxAc.Status status, String error); }

    public static synchronized BroadlinkDevice device(AcConfig ac) {
        if (ac == null || !ac.hasDevice()) return null;
        String key = ac.host + "|" + ac.mac + "|" + ac.devtype;
        BroadlinkDevice d = CACHE.get(key);
        if (d == null) {
            d = ac.device();
            if (ac.isWifi() && ac.hasCloud()) d.timeoutMs = 2000;  // יש ענן לגיבוי - לא מחכים הרבה לחיבור המקומי
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
        IOException localErr = null;
        BroadlinkDevice d = device(ac);
        if (d != null && !(ac.hasCloud() && recentlyFailed(ac))) {
            try {
                AuxAc.Status s = AuxAc.read(d);
                AUX_LAST.put(ac.id, s);
                LOCAL_FAIL.remove(ac.id);
                return s;
            } catch (IOException e) {
                localErr = e;
                markFailed(ac);
            }
        }
        if (ac.hasCloud()) {
            AuxAc.Status s = fromCloud(cloud().get(ac.cloudDid));
            s.viaCloud = true;
            return s;
        }
        throw localErr != null ? localErr : new IOException(ac.name + " לא מחובר – היכנס להגדרות");
    }

    /** חיבור מקומי שנכשל לאחרונה (מחוץ לבית) - דילוג ישר לענן למשך דקה. */
    private static final Map<Integer, Long> LOCAL_FAIL = new HashMap<>();

    private static synchronized boolean recentlyFailed(AcConfig ac) {
        Long t = LOCAL_FAIL.get(ac.id);
        return ac.hasCloud() && t != null && System.currentTimeMillis() - t < 60000;
    }

    private static synchronized void markFailed(AcConfig ac) { LOCAL_FAIL.put(ac.id, System.currentTimeMillis()); }

    private static boolean localAlive(AcConfig ac, BroadlinkDevice d) {
        if (recentlyFailed(ac)) return false;
        try {
            AUX_LAST.put(ac.id, AuxAc.read(d));
            return true;
        } catch (IOException e) {
            markFailed(ac);
            return false;
        }
    }

    // ---------- ענן ----------
    // ענן: ac_mode 0 קירור,1 חימום,2 ייבוש,3 מאוורר,4 אוטו; ac_mark 0 אוטו,1 נמוך,2 בינוני,3 גבוה,4 טורבו,5 שקט
    static final int[] CLOUD_MODE = {4, 0, 2, 3, 1};      // לפי אינדקס מצב באפליקציה
    static final int[] CLOUD_FAN = {0, 3, 2, 1, 5};       // לפי אינדקס מאוורר באפליקציה

    static void cloudApply(AcConfig ac, AcState s) throws IOException {
        java.util.Map<String, Integer> p = new java.util.LinkedHashMap<>();
        p.put("pwr", s.power ? 1 : 0);
        if (s.power) {
            p.put("ac_mode", CLOUD_MODE[Math.max(0, Math.min(4, s.mode))]);
            p.put("temp", s.temp * 10);
            p.put("ac_mark", CLOUD_FAN[Math.max(0, Math.min(4, s.fan))]);
            p.put("ac_vdir", (s.swing == 1 || s.swing == 3) ? 1 : 0);
            p.put("ac_hdir", (s.swing == 2 || s.swing == 3) ? 1 : 0);
        }
        cloud().set(ac.cloudDid, p);
    }

    static void cloudCommand(AcConfig ac, int cmd) throws IOException {
        java.util.Map<String, Integer> p = new java.util.HashMap<>();
        if (cmd == FujitsuIr.CMD_POWERFUL) p.put("ac_mark", 4);
        else if (cmd == FujitsuIr.CMD_STEP_VERT) {
            java.util.Map<String, Integer> cur = cloud().get(ac.cloudDid);
            p.put("ac_vdir", cur.getOrDefault("ac_vdir", 0) == 1 ? 0 : 1);
        } else if (cmd == FujitsuIr.CMD_STEP_HORIZ) {
            java.util.Map<String, Integer> cur = cloud().get(ac.cloudDid);
            p.put("ac_hdir", cur.getOrDefault("ac_hdir", 0) == 1 ? 0 : 1);
        } else throw new IOException("הפעולה הזו לא זמינה בחיבור דרך הענן");
        cloud().set(ac.cloudDid, p);
    }

    static AuxAc.Status fromCloud(java.util.Map<String, Integer> m) {
        AuxAc.Status s = new AuxAc.Status();
        int pwr = m.containsKey("pwr") ? m.get("pwr") : m.getOrDefault("ac_pwr", 0);
        s.power = pwr == 1 ? 1 : 0;
        int t = m.containsKey("temp") ? m.get("temp") : m.getOrDefault("ac_temp", 240);
        s.temp = t / 10.0;
        int[] modeMap = {AuxAc.MODE_COOL, AuxAc.MODE_HEAT, AuxAc.MODE_DRY, AuxAc.MODE_FAN, AuxAc.MODE_AUTO};
        int cm = m.getOrDefault("ac_mode", 4);
        s.mode = (cm >= 0 && cm < modeMap.length) ? modeMap[cm] : AuxAc.MODE_AUTO;
        int f = m.getOrDefault("ac_mark", 0);
        s.fan = f == 1 ? AuxAc.FAN_LOW : f == 2 ? AuxAc.FAN_MED : f == 3 ? AuxAc.FAN_HIGH : AuxAc.FAN_AUTO;
        s.turbo = f == 4 ? 1 : 0;
        s.mute = f == 5 ? 1 : 0;
        s.vertical = m.getOrDefault("ac_vdir", 0) == 1 ? AuxAc.V_SWING : 3;
        s.horizontal = m.getOrDefault("ac_hdir", 0) == 1 ? AuxAc.H_SWING : AuxAc.H_FIXED;
        if (m.containsKey("envtemp")) s.ambient = m.get("envtemp") / 10.0;
        return s;
    }

    // ---------- ממשק אחיד ----------

    /** שולח מצב מלא למזגן (IR או Wi-Fi). */
    public static void applyBlocking(AcConfig ac, AcState s, boolean turnOn) throws IOException {
        if (ac.isWifi()) {
            IOException localErr = null;
            if (ac.hasLocal() && !recentlyFailed(ac)) {
                try {
                    BroadlinkDevice d = device(ac);
                    AuxAc.Status a = auxBase(ac, d);
                    applyTo(a, s);
                    a.turbo = 0;
                    AuxAc.write(d, a);
                    AUX_LAST.put(ac.id, a);
                    return;
                } catch (IOException e) {
                    localErr = e;
                    markFailed(ac);
                }
            }
            if (ac.hasCloud()) { cloudApply(ac, s); return; }
            throw localErr != null ? localErr : new IOException(ac.name + " לא מחובר – היכנס להגדרות");
        } else {
            sendBlocking(ac, irForState(ac, s, turnOn));
        }
    }

    /** פקודה קצרה: הזזת תריס, אקונומי, עוצמתי. מחזיר הודעת שגיאה אם לא נתמך. */
    public static void commandBlocking(AcConfig ac, int cmd) throws IOException {
        if (!ac.isWifi()) { sendBlocking(ac, irForShort(ac, cmd)); return; }
        BroadlinkDevice d = device(ac);
        if (d == null || !localAlive(ac, d)) {
            if (ac.hasCloud()) { cloudCommand(ac, cmd); return; }
            throw new IOException(ac.name + " לא מחובר – היכנס להגדרות");
        }
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

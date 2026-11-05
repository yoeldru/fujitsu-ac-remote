// נכתב: 2026-11-05 07:54 (שעון ישראל) · עודכן: 2026-11-05 10:30 - כל מזגן עם ה-RM4 שלו
// שכבת השליחה: הופכת מצב/פקודה לקוד IR ושולחת דרך ה-RM4 של המזגן ברקע.
package il.yoeldru.fujiac;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import il.yoeldru.fujiac.core.BroadlinkDevice;
import il.yoeldru.fujiac.core.FujitsuIr;

public final class Remote {
    private Remote() {}

    public static final ExecutorService NET = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** חיבורים מאומתים לפי כתובת+MAC, כדי לא להתחבר מחדש בכל לחיצה. */
    private static final Map<String, BroadlinkDevice> CACHE = new HashMap<>();

    public interface Callback { void done(String error); }

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

    /** קוד IR מלא לפי מצב. */
    public static byte[] irForState(AcConfig ac, AcState s, boolean turnOn) {
        if (!s.power) return irForShort(ac, FujitsuIr.CMD_TURN_OFF);
        boolean bit = turnOn || ac.alwaysPower;
        return FujitsuIr.toBroadlink(FujitsuIr.buildLong(ac.model, ac.remoteId, bit, s.temp, s.mode, s.fan, s.swing));
    }

    public static byte[] irForShort(AcConfig ac, int cmd) {
        return FujitsuIr.toBroadlink(FujitsuIr.buildShort(ac.remoteId, cmd));
    }

    /** שליחה סינכרונית (לשימוש מתוך thread רקע). */
    public static void sendBlocking(AcConfig ac, byte[] ir) throws IOException {
        BroadlinkDevice d = device(ac);
        if (d == null) throw new IOException("ל" + ac.name + " לא הוגדר שלט חכם - היכנס להגדרות");
        d.sendIr(ir);
    }

    /** שליחה אסינכרונית, התשובה חוזרת ל-UI. */
    public static void send(AcConfig ac, byte[] ir, Callback cb) {
        NET.execute(() -> {
            String err = null;
            try {
                sendBlocking(ac, ir);
            } catch (IOException e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final String fErr = err;
            if (cb != null) MAIN.post(() -> cb.done(fErr));
        });
    }
}

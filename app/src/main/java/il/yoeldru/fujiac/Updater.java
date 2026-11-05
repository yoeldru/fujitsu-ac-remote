// נכתב: 2026-11-05 12:06 (שעון ישראל)
// עדכונים מתוך האפליקציה: בודק את ה-Release "latest" בגיטהאב, מוריד את ה-APK ומתקין אותו
// דרך PackageInstaller - בלי להוריד את הקובץ ידנית מהדפדפן.
package il.yoeldru.fujiac;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Updater {
    private Updater() {}

    static final String API = "https://api.github.com/repos/yoeldru/fujitsu-ac-remote/releases/tags/latest";
    public static final String APK_URL =
            "https://github.com/yoeldru/fujitsu-ac-remote/releases/download/latest/fujitsu-remote.apk";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static class Info {
        public int build;
        public String notes = "";
        public String apkUrl = APK_URL;
        public boolean isNewer() { return build > BuildConfig.VERSION_CODE; }
    }

    public interface OnInfo { void done(Info info, String error); }
    public interface OnProgress { void progress(int percent); }
    public interface OnDone { void done(String error); }

    /** בדיקת גרסה (ברקע, התשובה חוזרת ל-UI). */
    public static void check(OnInfo cb) {
        new Thread(() -> {
            Info info = null;
            String err = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "fujitsu-ac-remote");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                if (c.getResponseCode() != 200) throw new Exception("שרת העדכונים החזיר " + c.getResponseCode());
                JSONObject o = new JSONObject(readAll(c.getInputStream()));
                info = new Info();
                String body = o.optString("body", "");
                Matcher m = Pattern.compile("build #(\\d+)").matcher(body);
                if (m.find()) info.build = Integer.parseInt(m.group(1));
                info.notes = body.replaceFirst("build #\\d+\\s*", "").trim();
                JSONArray assets = o.optJSONArray("assets");
                if (assets != null) for (int i = 0; i < assets.length(); i++) {
                    JSONObject a = assets.optJSONObject(i);
                    if (a.optString("name").endsWith(".apk")) info.apkUrl = a.optString("browser_download_url", APK_URL);
                }
            } catch (Exception e) {
                err = e.getMessage();
            }
            final Info fi = info;
            final String fe = err;
            MAIN.post(() -> cb.done(fi, fe));
        }).start();
    }

    /** האם מותר לאפליקציה להתקין עדכונים (הרשאה חד-פעמית). */
    public static boolean canInstall(Context c) {
        return c.getPackageManager().canRequestPackageInstalls();
    }

    public static void openInstallPermission(Activity a) {
        a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + a.getPackageName())));
    }

    /** מוריד ומתקין. בסוף אנדרואיד מציג "לעדכן?" ולוחצים עדכון. */
    public static void downloadAndInstall(Context ctx, Info info, OnProgress prog, OnDone done) {
        Context c = ctx.getApplicationContext();
        new Thread(() -> {
            String err = null;
            PackageInstaller.Session session = null;
            try {
                HttpURLConnection conn = open(info.apkUrl);
                long total = conn.getContentLengthLong();
                PackageInstaller pi = c.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params =
                        new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(c.getPackageName());
                if (Build.VERSION.SDK_INT >= 31)
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
                int id = pi.createSession(params);
                session = pi.openSession(id);
                try (InputStream in = conn.getInputStream();
                     OutputStream out = session.openWrite("app.apk", 0, total > 0 ? total : -1)) {
                    byte[] buf = new byte[64 * 1024];
                    long done2 = 0;
                    int n, last = -1;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        done2 += n;
                        if (total > 0) {
                            int p = (int) (done2 * 100 / total);
                            if (p != last) { last = p; final int fp = p; MAIN.post(() -> prog.progress(fp)); }
                        }
                    }
                    session.fsync(out);
                }
                Intent i = new Intent(c, InstallReceiver.class);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                PendingIntent pend = PendingIntent.getBroadcast(c, 77, i, flags);
                session.commit(pend.getIntentSender());
                session.close();
                session = null;
            } catch (Exception e) {
                err = e.getMessage() == null ? e.toString() : e.getMessage();
                if (session != null) session.abandon();
            }
            final String fe = err;
            MAIN.post(() -> done.done(fe));
        }).start();
    }

    private static HttpURLConnection open(String url) throws Exception {
        // גיטהאב מפנה לשרת הקבצים - עוקבים אחרי ההפניות ידנית
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "fujitsu-ac-remote");
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                url = c.getHeaderField("Location");
                c.disconnect();
                continue;
            }
            if (code != 200) throw new Exception("ההורדה נכשלה (" + code + ")");
            return c;
        }
        throw new Exception("יותר מדי הפניות בהורדה");
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }
}

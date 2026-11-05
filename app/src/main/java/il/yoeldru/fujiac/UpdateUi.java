// נכתב: 2026-11-05 12:06 (שעון ישראל)
// חלונות העדכון: "עדכון זמין" → הרשאה (פעם אחת) → הורדה עם התקדמות → התקנה.
package il.yoeldru.fujiac;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.TextView;

public final class UpdateUi {
    private UpdateUi() {}

    /** תוצאת הבדיקה האחרונה בהפעלה הנוכחית (כדי לא לבדוק שוב בכל מעבר מסך). */
    public static Updater.Info lastInfo;
    private static long lastCheck = 0;

    public interface OnResult { void result(Updater.Info info); }

    /** בדיקה שקטה - לכל היותר פעם ב-30 דקות. */
    public static void quietCheck(OnResult r) {
        if (lastInfo != null && System.currentTimeMillis() - lastCheck < 30 * 60 * 1000L) {
            r.result(lastInfo);
            return;
        }
        Updater.check((info, err) -> {
            if (info != null) { lastInfo = info; lastCheck = System.currentTimeMillis(); }
            r.result(info);
        });
    }

    public static void offer(Activity a, Updater.Info info) {
        String notes = info.notes == null || info.notes.isEmpty() ? "" : "\n\nמה חדש:\n" + info.notes;
        new AlertDialog.Builder(a)
                .setTitle("🔴 עדכון זמין")
                .setMessage("גרסה 1.0." + info.build + " (מותקנת: " + BuildConfig.VERSION_NAME + ")" + notes)
                .setPositiveButton("עדכן עכשיו", (d, w) -> start(a, info))
                .setNegativeButton("אחר כך", null)
                .show();
    }

    public static void start(Activity a, Updater.Info info) {
        if (!Updater.canInstall(a)) {
            new AlertDialog.Builder(a)
                    .setTitle("הרשאה חד-פעמית")
                    .setMessage("כדי שהאפליקציה תוכל לעדכן את עצמה, הפעל \"אפשר מהמקור הזה\" במסך שייפתח, "
                            + "חזור לכאן ולחץ שוב \"עדכן\".")
                    .setPositiveButton("פתח הגדרות", (d, w) -> Updater.openInstallPermission(a))
                    .setNegativeButton("ביטול", null)
                    .show();
            return;
        }
        TextView t = Ui.text(a, "מוריד… 0%", 16, Ui.TEXT, false);
        int p = Ui.dp(a, 24);
        t.setPadding(p, p, p, p);
        AlertDialog dlg = new AlertDialog.Builder(a).setTitle("מעדכן").setView(t).setCancelable(false).show();
        Updater.downloadAndInstall(a, info,
                pct -> t.setText("מוריד… " + pct + "%"),
                err -> {
                    if (err == null) {
                        t.setText("מתקין… אם אנדרואיד שואל \"לעדכן?\" – לחץ עדכון.");
                        t.postDelayed(() -> { try { dlg.dismiss(); } catch (Exception ignored) {} }, 4000);
                    } else {
                        t.setText("✗ " + err);
                        dlg.setCancelable(true);
                    }
                });
    }
}

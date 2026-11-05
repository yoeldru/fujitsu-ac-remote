// נכתב: 2026-11-05 22:46 (שעון ישראל)
// גיבוי והעברת הגדרות: שליחה בוואטסאפ (קוד טקסט), שמירה/פתיחה של קובץ, הדבקת קוד.
// מקבל גם קוד/קובץ ששותף אל האפליקציה (Share → שלט פוג'יטסו).
package il.yoeldru.fujiac;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BackupActivity extends Activity {
    private static final int REQ_SAVE = 11, REQ_OPEN = 12;
    private Store store;
    private CheckBox withSched;
    private TextView msg;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        LinearLayout col = Ui.screen(this);
        col.addView(Ui.text(this, "גיבוי והעברת הגדרות", 24, Ui.TEXT, true));
        Ui.spacer(col, 4);
        col.addView(Ui.text(this, "כולל את כל המזגנים והשלטים שלהם, קבוצות, מועדפים וכפתורים נלמדים. "
                + "ההגדרות יעבדו בטלפון אחר כל עוד לא עשו ריסט לשלטים.", 13, Ui.MUTED, false));
        Ui.spacer(col, 12);

        // --- יצוא ---
        LinearLayout ex = Ui.card(this);
        ex.addView(Ui.label(this, "יצוא"));
        withSched = new CheckBox(this);
        withSched.setText("כולל תזמונים (אם שני טלפונים מתזמנים – המזגן יקבל את הפקודה פעמיים, זה לא מזיק)");
        withSched.setTextColor(Ui.TEXT);
        withSched.setChecked(false);
        ex.addView(withSched);
        Ui.spacer(ex, 8);
        Button share = Ui.primary(this, "📤 שלח לטלפון אחר (וואטסאפ וכו')");
        share.setOnClickListener(v -> share());
        ex.addView(share, new LinearLayout.LayoutParams(-1, -2));
        Ui.spacer(ex, 8);
        LinearLayout r = Ui.row(this);
        Button save = Ui.soft(this, "💾 שמור לקובץ");
        save.setOnClickListener(v -> saveFile());
        Button copy = Ui.soft(this, "📋 העתק קוד");
        copy.setOnClickListener(v -> copyCode());
        Ui.addWeighted(r, save, 3);
        Ui.addWeighted(r, copy, 3);
        ex.addView(r);
        col.addView(ex);

        // --- יבוא ---
        LinearLayout im = Ui.card(this);
        im.addView(Ui.label(this, "יבוא (מחליף את ההגדרות בטלפון הזה)"));
        LinearLayout r2 = Ui.row(this);
        Button open = Ui.soft(this, "📂 פתח קובץ");
        open.setOnClickListener(v -> openFile());
        Button paste = Ui.soft(this, "📋 הדבק קוד");
        paste.setOnClickListener(v -> pasteDialog());
        Ui.addWeighted(r2, open, 3);
        Ui.addWeighted(r2, paste, 3);
        im.addView(r2);
        Ui.spacer(im, 6);
        im.addView(Ui.text(this, "דרך קלה: בטלפון השני לחץ לחיצה ארוכה על ההודעה בוואטסאפ ← העתק, "
                + "ואז \"הדבק קוד\". או: בחר את ההודעה ← שתף ← שלט פוג'יטסו.", 12.5f, Ui.MUTED, false));
        col.addView(im);

        msg = Ui.text(this, "", 14, Ui.ACCENT, true);
        col.addView(msg);
        Ui.spacer(col, 10);
        col.addView(Ui.text(this, "גיבוי אוטומטי: אם הגיבוי של גוגל פעיל בטלפון, ההגדרות משוחזרות לבד "
                + "בהתקנה מחדש או בטלפון חדש.", 12, Ui.MUTED, false));

        handleIncoming(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncoming(intent);
    }

    // ---------- יצוא ----------

    private String code() { return store.exportCode(withSched.isChecked()); }

    private void share() {
        String text = "הגדרות לאפליקציית \"שלט פוג'יטסו\" 🌬️\n"
                + "1. התקן: " + Updater.APK_URL + "\n"
                + "2. העתק את כל ההודעה הזו ← באפליקציה: הגדרות ← גיבוי והעברה ← הדבק קוד\n\n" + code();
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, "שליחת הגדרות"));
    }

    private void copyCode() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("fujiac", code()));
        msg.setText("✓ הקוד הועתק");
    }

    private void saveFile() {
        String name = "fujitsu-settings-" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".json";
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, REQ_SAVE);
    }

    // ---------- יבוא ----------

    private void openFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_OPEN);
    }

    private void pasteDialog() {
        EditText et = new EditText(this);
        et.setHint("הדבק כאן את הקוד (מתחיל ב-FUJIAC1:)");
        et.setMinLines(3);
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence t = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (t != null && t.toString().contains(Store.EXPORT_PREFIX)) et.setText(t);
        }
        int p = Ui.dp(this, 20);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(p, p / 2, p, 0);
        box.addView(et, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle("יבוא מקוד").setView(box)
                .setPositiveButton("יבא", (d, w) -> tryImport(et.getText().toString()))
                .setNegativeButton("ביטול", null).show();
    }

    private void handleIncoming(Intent in) {
        if (in == null) return;
        String text = null;
        if (Intent.ACTION_SEND.equals(in.getAction())) {
            text = in.getStringExtra(Intent.EXTRA_TEXT);
            if (text == null) {
                Uri u = in.getParcelableExtra(Intent.EXTRA_STREAM);
                if (u != null) text = readUri(u);
            }
        } else if (Intent.ACTION_VIEW.equals(in.getAction()) && in.getData() != null) {
            text = readUri(in.getData());
        }
        if (text != null) tryImport(text);
    }

    private void tryImport(String text) {
        JSONObject o;
        try {
            o = Store.decode(text);
        } catch (Exception e) {
            msg.setText("✗ " + e.getMessage());
            return;
        }
        JSONArray acs = o.optJSONArray("acs");
        StringBuilder names = new StringBuilder();
        if (acs != null) for (int i = 0; i < acs.length(); i++) {
            if (names.length() > 0) names.append(", ");
            names.append(acs.optJSONObject(i).optString("name"));
        }
        JSONArray sched = o.optJSONArray("schedules");
        JSONArray groups = o.optJSONArray("groups");
        new AlertDialog.Builder(this).setTitle("ליבא את ההגדרות?")
                .setMessage("מזגנים: " + names
                        + (groups != null && groups.length() > 0 ? "\nקבוצות: " + groups.length() : "")
                        + "\nתזמונים: " + (sched == null ? "לא כלולים" : sched.length())
                        + "\n\nההגדרות הנוכחיות בטלפון הזה יוחלפו.")
                .setPositiveButton("יבא", (d, w) -> {
                    // מבטלים תזמונים קיימים לפני ההחלפה
                    JSONArray old = store.list(Store.SCHEDULES);
                    for (int i = 0; i < old.length(); i++) Scheduler.cancel(this, old.optJSONObject(i).optInt("id"));
                    store.importAll(o);
                    Scheduler.applyAll(this);
                    msg.setText("✓ ההגדרות יובאו! חזור למסך הראשי.");
                    new AlertDialog.Builder(this).setTitle("✓ יובא בהצלחה")
                            .setMessage("המזגנים: " + names + "\nבדוק שהטלפון מחובר לרשת הביתית ונסה להדליק.")
                            .setPositiveButton("למסך הראשי", (x, y) -> {
                                Intent m = new Intent(this, MainActivity.class);
                                m.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(m);
                                finish();
                            }).show();
                })
                .setNegativeButton("ביטול", null).show();
    }

    // ---------- קבצים ----------

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        if (req == REQ_SAVE) {
            try (OutputStream out = getContentResolver().openOutputStream(u)) {
                out.write(store.exportAll(withSched.isChecked()).toString(2).getBytes("UTF-8"));
                msg.setText("✓ נשמר לקובץ");
            } catch (Exception e) {
                msg.setText("✗ השמירה נכשלה: " + e.getMessage());
            }
        } else if (req == REQ_OPEN) {
            String t = readUri(u);
            if (t == null) msg.setText("✗ לא הצלחתי לקרוא את הקובץ");
            else tryImport(t);
        }
    }

    private String readUri(Uri u) {
        try (InputStream in = getContentResolver().openInputStream(u)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}

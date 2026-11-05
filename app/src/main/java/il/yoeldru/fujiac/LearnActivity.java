// נכתב: 2026-11-05 07:54 (שעון ישראל) · עודכן: 2026-11-05 10:30 - כפתורים לכל שלט חכם
// כפתורים נלמדים: לימוד כל כפתור מהשלט המקורי דרך ה-RM4 ושליחתו בלחיצה.
// גיבוי למקרה שקוד כלשהו מהשלט לא מכוסה ע"י המקודד המובנה.
package il.yoeldru.fujiac;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import il.yoeldru.fujiac.core.BroadlinkDevice;
import il.yoeldru.fujiac.core.FujitsuIr;

public class LearnActivity extends Activity {
    private Store store;
    private LinearLayout list;
    private TextView msg;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean learning = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);
        LinearLayout col = Ui.screen(this);
        col.addView(Ui.text(this, "כפתורים נלמדים – " + store.current().name, 24, Ui.TEXT, true));
        Ui.spacer(col, 6);
        col.addView(Ui.text(this,
                "לחץ \"למד כפתור\", כוון את השלט המקורי אל ה-RM4 מקרוב ולחץ על הכפתור הרצוי. "
                        + "שים לב: בשלט מזגן כל לחיצה שולחת גם את הטמפרטורה והמצב שמוצגים על השלט באותו רגע.",
                12.5f, Ui.MUTED, false));
        Ui.spacer(col, 12);
        Button learn = Ui.primary(this, "🎓 למד כפתור חדש");
        learn.setOnClickListener(v -> startLearning());
        col.addView(learn, new LinearLayout.LayoutParams(-1, -2));
        msg = Ui.text(this, "", 14, Ui.ACCENT, true);
        msg.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        col.addView(msg);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        col.addView(list);
        render();
    }

    @Override
    protected void onDestroy() {
        learning = false;
        super.onDestroy();
    }

    private void startLearning() {
        if (learning) return;
        learning = true;
        msg.setText("מכין את השלט החכם…");
        Remote.NET.execute(() -> {
            byte[] code = null;
            String err = null;
            try {
                BroadlinkDevice d = Remote.device(store.current());
                if (d == null) throw new Exception("ל" + store.current().name + " לא הוגדר שלט חכם");
                d.enterLearning();
                main.post(() -> msg.setText("🔴 לחץ עכשיו על הכפתור בשלט המקורי (30 שניות)…"));
                long end = System.currentTimeMillis() + 30000;
                while (learning && System.currentTimeMillis() < end && code == null) {
                    Thread.sleep(1000);
                    code = d.checkLearned();
                }
                if (code == null && learning) err = "לא נקלט קוד. נסה שוב, קרוב יותר לשלט החכם.";
            } catch (Exception e) {
                err = e.getMessage();
            }
            learning = false;
            final byte[] fCode = code;
            final String fErr = err;
            main.post(() -> {
                if (isFinishing()) return;
                if (fCode != null) askName(fCode);
                else msg.setText(fErr == null ? "" : "✗ " + fErr);
            });
        });
    }

    private void askName(byte[] code) {
        msg.setText("✓ נקלט קוד (" + code.length + " בתים)");
        EditText name = new EditText(this);
        name.setHint("למשל: תריס שמאלה");
        new AlertDialog.Builder(this).setTitle("שם לכפתור").setView(name)
                .setPositiveButton("שמור", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) n = "כפתור " + (store.list(Store.LEARNED).length() + 1);
                    try {
                        JSONArray a = store.list(Store.LEARNED);
                        JSONObject o = new JSONObject();
                        o.put("name", n);
                        o.put("code", FujitsuIr.hex(code));
                        o.put("acId", store.current().id);
                        a.put(o);
                        store.saveList(Store.LEARNED, a);
                    } catch (Exception ignored) {}
                    render();
                })
                .setNegativeButton("בטל", null).show();
    }

    private void render() {
        list.removeAllViews();
        AcConfig cur = store.current();
        JSONArray all = store.list(Store.LEARNED);
        // מציגים רק את הכפתורים שנלמדו דרך השלט החכם של המזגן הנוכחי
        JSONArray a = new JSONArray();
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o.optInt("acId", cur.id) == cur.id) a.put(o);
        }
        if (a.length() == 0) {
            list.addView(Ui.text(this, "אין כפתורים נלמדים.", 14, Ui.MUTED, false));
            return;
        }
        LinearLayout row = null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            final int idx = i;
            if (i % 2 == 0) {
                row = Ui.row(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.bottomMargin = Ui.dp(this, 8);
                list.addView(row, lp);
            }
            Button b = Ui.soft(this, o.optString("name"));
            b.setOnClickListener(v -> {
                msg.setText("שולח…");
                Remote.send(cur, FujitsuIr.unhex(o.optString("code")),
                        err -> msg.setText(err == null ? "✓ נשלח: " + o.optString("name") : "✗ " + err));
            });
            b.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this).setTitle("למחוק את \"" + o.optString("name") + "\"?")
                        .setPositiveButton("מחק", (d, w) -> {
                            JSONArray arr = store.list(Store.LEARNED);
                            for (int k = arr.length() - 1; k >= 0; k--) {
                                JSONObject x = arr.optJSONObject(k);
                                if (x.optString("code").equals(o.optString("code"))
                                        && x.optString("name").equals(o.optString("name"))) { arr.remove(k); break; }
                            }
                            store.saveList(Store.LEARNED, arr);
                            render();
                        }).setNegativeButton("ביטול", null).show();
                return true;
            });
            Ui.addWeighted(row, b, 4);
        }
        if (a.length() % 2 == 1) Ui.addWeighted(row, new TextView(this), 4);
        list.addView(Ui.text(this, "לחיצה ארוכה למחיקה", 12, Ui.MUTED, false));
    }
}

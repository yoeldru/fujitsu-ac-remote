// נכתב: 2026-11-05 12:42 (שעון ישראל)
// חלון יצירה/עריכה של קבוצת מזגנים (למשל "סלון ומטבח").
package il.yoeldru.fujiac;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class GroupUi {
    private GroupUi() {}

    public interface OnSaved { void saved(int groupId); }

    /** existing = null ליצירת קבוצה חדשה. */
    public static void edit(Activity a, JSONObject existing, OnSaved onSaved) {
        Store store = new Store(a);
        List<AcConfig> all = store.acs();
        if (all.size() < 2) {
            Toast.makeText(a, "צריך לפחות שני מזגנים כדי ליצור קבוצה – הוסף מזגן בהגדרות", Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout v = new LinearLayout(a);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setLayoutDirection(LinearLayout.LAYOUT_DIRECTION_RTL);
        int p = Ui.dp(a, 20);
        v.setPadding(p, p / 2, p, 0);

        EditText name = new EditText(a);
        name.setHint("שם הקבוצה, למשל: סלון ומטבח");
        if (existing != null) name.setText(existing.optString("name"));
        v.addView(name);
        Ui.spacer(v, 8);
        v.addView(Ui.label(a, "מזגנים בקבוצה"));

        List<Integer> chosen = new ArrayList<>();
        if (existing != null) {
            JSONArray ids = existing.optJSONArray("acs");
            if (ids != null) for (int i = 0; i < ids.length(); i++) chosen.add(ids.optInt(i));
        }
        List<CheckBox> boxes = new ArrayList<>();
        for (AcConfig ac : all) {
            CheckBox cb = new CheckBox(a);
            cb.setText(ac.name);
            cb.setTextSize(16);
            cb.setChecked(existing == null || chosen.contains(ac.id));
            boxes.add(cb);
            v.addView(cb);
        }

        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(existing == null ? "קבוצה חדשה" : "עריכת קבוצה")
                .setView(v)
                .setPositiveButton("שמור", (d, w) -> {
                    JSONArray ids = new JSONArray();
                    for (int i = 0; i < all.size(); i++) if (boxes.get(i).isChecked()) ids.put(all.get(i).id);
                    if (ids.length() < 2) {
                        Toast.makeText(a, "בחר לפחות שני מזגנים", Toast.LENGTH_LONG).show();
                        return;
                    }
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < all.size(); i++) if (boxes.get(i).isChecked()) {
                            if (sb.length() > 0) sb.append(" + ");
                            sb.append(all.get(i).name);
                        }
                        n = sb.toString();
                    }
                    try {
                        JSONObject g = existing != null ? existing : new JSONObject();
                        if (existing == null) g.put("id", store.nextId());
                        g.put("name", n);
                        g.put("acs", ids);
                        store.saveGroup(g);
                        if (onSaved != null) onSaved.saved(g.optInt("id"));
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("ביטול", null);
        if (existing != null) {
            b.setNeutralButton("מחק קבוצה", (d, w) -> new AlertDialog.Builder(a)
                    .setTitle("למחוק את \"" + existing.optString("name") + "\"?")
                    .setMessage("המזגנים עצמם לא יימחקו. תזמונים של הקבוצה יימחקו.")
                    .setPositiveButton("מחק", (d2, w2) -> {
                        int gid = existing.optInt("id");
                        JSONArray s = store.list(Store.SCHEDULES);
                        for (int i = s.length() - 1; i >= 0; i--) {
                            if (s.optJSONObject(i).optInt("groupId") == gid) {
                                Scheduler.cancel(a, s.optJSONObject(i).optInt("id"));
                                s.remove(i);
                            }
                        }
                        store.saveList(Store.SCHEDULES, s);
                        store.deleteGroup(gid);
                        if (onSaved != null) onSaved.saved(0);
                    })
                    .setNegativeButton("ביטול", null).show());
        }
        b.show();
    }
}

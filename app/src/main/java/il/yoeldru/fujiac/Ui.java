// נכתב: 2026-11-05 07:54 (שעון ישראל)
// רכיבי ממשק משותפים (נבנים בקוד, בלי ספריות חיצוניות) - כולל מצב כהה.
package il.yoeldru.fujiac;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class Ui {
    private Ui() {}

    public static int BG, CARD, TEXT, MUTED, ACCENT, ACCENT_SOFT, ON, OFF, HEAT, LINE;

    public static void init(Context c) {
        boolean dark = (c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            BG = 0xFF0F1419; CARD = 0xFF1A2129; TEXT = 0xFFE8EDF2; MUTED = 0xFF8B98A5;
            ACCENT = 0xFF4DA3FF; ACCENT_SOFT = 0xFF1E3A5C; LINE = 0xFF2A333D;
        } else {
            BG = 0xFFF2F5F8; CARD = 0xFFFFFFFF; TEXT = 0xFF14202B; MUTED = 0xFF66737F;
            ACCENT = 0xFF1A73E8; ACCENT_SOFT = 0xFFE3EEFD; LINE = 0xFFE1E6EB;
        }
        ON = 0xFF1E9E5A; OFF = 0xFFD93F3F; HEAT = 0xFFE8710A;
    }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable round(int color, int radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    /** מסך בסיסי: גלילה + עמודה, RTL. מחזיר את העמודה. */
    public static LinearLayout screen(Activity a) {
        init(a);
        ScrollView sv = new ScrollView(a);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(a, 16);
        col.setPadding(pad, dp(a, 20), pad, dp(a, 32));
        sv.addView(col);
        a.setContentView(sv);
        a.getWindow().setStatusBarColor(BG);
        a.getWindow().setNavigationBarColor(BG);
        boolean dark = BG != 0xFFF2F5F8;
        if (!dark) a.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        return col;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(round(CARD, dp(c, 18)));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        l.setElevation(dp(c, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(c, 12);
        l.setLayoutParams(lp);
        return l;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextDirection(View.TEXT_DIRECTION_RTL);
        return t;
    }

    public static TextView label(Context c, String s) {
        TextView t = text(c, s, 13, MUTED, true);
        t.setPadding(0, 0, 0, dp(c, 8));
        return t;
    }

    public static Button button(Context c, String s, int bg, int fg) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(fg);
        b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33000000), round(bg, dp(c, 14)), null));
        b.setPadding(dp(c, 10), dp(c, 12), dp(c, 10), dp(c, 12));
        b.setMinHeight(dp(c, 48));
        return b;
    }

    public static Button primary(Context c, String s) { return button(c, s, ACCENT, Color.WHITE); }
    public static Button soft(Context c, String s) { return button(c, s, ACCENT_SOFT, ACCENT); }

    public static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    /** מוסיף ילד לשורה במשקל שווה. */
    public static void addWeighted(LinearLayout row, View v, int marginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(dp(row.getContext(), marginDp));
        lp.setMarginEnd(dp(row.getContext(), marginDp));
        row.addView(v, lp);
    }

    public static void spacer(LinearLayout col, int dpH) {
        View v = new View(col.getContext());
        col.addView(v, new LinearLayout.LayoutParams(1, dp(col.getContext(), dpH)));
    }

    /** כפתורי בחירה (segmented). */
    public static class Segmented {
        public final LinearLayout view;
        final Button[] buttons;
        public int selected = -1;
        public interface OnPick { void pick(int i); }

        public Segmented(Context c, String[] labels, OnPick onPick) {
            view = row(c);
            buttons = new Button[labels.length];
            for (int i = 0; i < labels.length; i++) {
                final int idx = i;
                Button b = button(c, labels[i], ACCENT_SOFT, ACCENT);
                b.setTextSize(labels.length > 4 ? 12.5f : 14);
                b.setPadding(dp(c, 2), dp(c, 10), dp(c, 2), dp(c, 10));
                b.setOnClickListener(v -> { select(idx); onPick.pick(idx); });
                buttons[i] = b;
                addWeighted(view, b, 3);
            }
        }

        public void select(int i) {
            selected = i;
            Context c = view.getContext();
            for (int j = 0; j < buttons.length; j++) {
                boolean on = j == i;
                buttons[j].setBackground(new RippleDrawable(ColorStateList.valueOf(0x33000000),
                        round(on ? ACCENT : ACCENT_SOFT, dp(c, 12)), null));
                buttons[j].setTextColor(on ? Color.WHITE : ACCENT);
            }
        }
    }
}

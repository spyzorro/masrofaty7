package com.mohamed.expenseguard;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.content.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;

public class GoldActivity extends Activity {
    private ExpenseDbHelper db;
    private LinearLayout list;
    private TextView summary, status;
    private final int[] karats = {24, 21, 18, 14};
    private final double[] buy = new double[4], sell = new double[4];
    private double goldPoundPrice = 0;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        db = new ExpenseDbHelper(this);
        if (new FirebaseSyncManager(this, db).currentUser() == null) {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
            return;
        }
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        loadCache();
        show();
        refreshPrices(false);
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private TextView tv(String s, int z, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(z);
        v.setTextColor(Color.rgb(25, 39, 45));
        v.setGravity(Gravity.RIGHT);
        v.setPadding(dp(6), dp(5), dp(6), dp(5));
        if (bold) v.setTypeface(null, 1);
        return v;
    }

    private String money(double v) { return String.format(Locale.US, "%,.2f ج.م", v); }

    private void show() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(10), dp(16), 0);
        page.setBackgroundColor(Color.rgb(255, 250, 238));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(12), dp(8), dp(12), dp(8));
        head.setBackgroundColor(Color.WHITE);
        summary = tv("جاري الحساب...", 16, true);
        head.addView(summary);
        page.addView(head, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sc = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        sc.addView(content);

        content.addView(tv("🪙 مدخراتي من الذهب", 24, true));
        content.addView(tv("سجّل الوزن والعيار، والجنيه الذهب يتحسب بسعر الجنيه المنشور في الموقع نفسه.", 13, false));
        status = tv("", 12, false);
        content.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        Button add = new Button(this);
        add.setText("＋ إضافة ذهب");
        add.setOnClickListener(v -> edit(null));
        Button update = new Button(this);
        update.setText("تحديث السعر");
        update.setOnClickListener(v -> refreshPrices(true));
        buttons.addView(add, new LinearLayout.LayoutParams(0, -2, 1));
        buttons.addView(update, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(buttons);

        content.addView(tv("اختيارات سريعة", 14, true));
        LinearLayout presets = new LinearLayout(this);
        addPresetButton(presets, "＋ جنيه ذهب\n8 جم عيار 21", "جنيه ذهب", 21, 8.0);
        addPresetButton(presets, "＋ سبيكة 10 جم\nعيار 24", "سبيكة 10 جرام عيار 24", 24, 10.0);
        content.addView(presets);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);
        page.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
        page.addView(bottomNav(), new LinearLayout.LayoutParams(-1, dp(70)));
        setContentView(page);
        render();
    }

    private void addPresetButton(LinearLayout parent, String text, String label, int karat, double grams) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(12);
        b.setOnClickListener(v -> {
            db.saveGoldHolding(null, label, karat, grams, 0);
            Toast.makeText(this, "تمت إضافة " + label, Toast.LENGTH_SHORT).show();
            render();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        parent.addView(b, lp);
    }

    private LinearLayout bottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(4), dp(6), dp(4), dp(6));
        nav.setBackgroundColor(Color.WHITE);
        addNavButton(nav, "🧾\nمصروفاتي", v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        });
        addNavButton(nav, "✅\nالمهام", v -> { startActivity(new Intent(this, TaskActivity.class)); finish(); });
        addNavButton(nav, "🪙\nالذهب", v -> Toast.makeText(this, "أنت في مدخرات الذهب", Toast.LENGTH_SHORT).show());
        return nav;
    }

    private void addNavButton(LinearLayout nav, String label, View.OnClickListener click) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextSize(12);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        nav.addView(b, lp);
    }

    private void loadCache() {
        for (int i = 0; i < karats.length; i++) {
            sell[i] = db.getDoubleSetting("gold_sell_" + karats[i], 0);
            buy[i] = db.getDoubleSetting("gold_buy_" + karats[i], 0);
        }
        goldPoundPrice = db.getDoubleSetting("gold_pound_price", 0);
    }

    private int idx(int k) {
        for (int i = 0; i < karats.length; i++) if (karats[i] == k) return i;
        return 1;
    }

    private void render() {
        if (list == null) return;
        list.removeAllViews();
        double total = 0, cashoutTotal = 0, grams = 0, cost = 0;
        List<ExpenseDbHelper.GoldHolding> hs = db.getGoldHoldings();
        for (ExpenseDbHelper.GoldHolding g : hs) {
            double value = valueFor(g, true);
            double cashout = valueFor(g, false);
            total += value;
            cashoutTotal += cashout;
            grams += g.grams;
            cost += g.purchasePrice;
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(dp(12), dp(9), dp(12), dp(9));
            c.setBackgroundColor(Color.WHITE);
            c.addView(tv((g.label == null || g.label.isEmpty() ? "ذهب" : g.label) + " • عيار " + g.karat, 17, true));
            String valueLabel = isGoldPound(g) && goldPoundPrice > 0 ? "القيمة حسب سعر الجنيه الذهب بالموقع " : "القيمة حسب بيع الموقع ";
            c.addView(tv(String.format(Locale.US, "%.3f جرام  •  %s%s", g.grams, valueLabel, money(value)), 14, false));
            if (cashout > 0 && Math.abs(cashout - value) > 0.01) c.addView(tv("لو هتبيع للتاجر حسب شراء الموقع: " + money(cashout), 12, false));
            if (g.purchasePrice > 0) c.addView(tv("سعر الشراء المسجل: " + money(g.purchasePrice) + "  •  الفرق: " + money(value - g.purchasePrice), 12, false));
            LinearLayout a = new LinearLayout(this);
            Button e = new Button(this);
            e.setText("تعديل");
            e.setOnClickListener(v -> edit(g));
            Button d = new Button(this);
            d.setText("حذف");
            d.setOnClickListener(v -> new AlertDialog.Builder(this).setMessage("حذف البند؟").setPositiveButton("حذف", (x, w) -> { db.deleteGoldHolding(g.id); render(); }).setNegativeButton("إلغاء", null).show());
            a.addView(e, new LinearLayout.LayoutParams(0, -2, 1));
            a.addView(d, new LinearLayout.LayoutParams(0, -2, 1));
            c.addView(a);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(6), 0, dp(6));
            list.addView(c, lp);
        }
        summary.setText("الإجمالي: " + money(total)
                + "\nبيع للتاجر: " + money(cashoutTotal)
                + "\nالوزن: " + String.format(Locale.US, "%.3f جرام", grams));
        if (hs.isEmpty()) list.addView(tv("أضف السبائك أو الجنيهات أو المشغولات اللي محوشها.", 15, false));
    }

    private boolean isGoldPound(ExpenseDbHelper.GoldHolding g) {
        String label = g.label == null ? "" : g.label;
        return g.karat == 21 && Math.abs(g.grams - 8.0) < 0.001 && label.contains("جنيه");
    }

    private double valueFor(ExpenseDbHelper.GoldHolding g, boolean siteSellPrice) {
        if (isGoldPound(g) && goldPoundPrice > 0) return goldPoundPrice;
        int i = idx(g.karat);
        double gramPrice = siteSellPrice ? sell[i] : buy[i];
        return g.grams * gramPrice;
    }

    private void edit(ExpenseDbHelper.GoldHolding old) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        EditText label = new EditText(this);
        label.setHint("الوصف (مثال: سبيكة 10 جرام)");
        Spinner k = new Spinner(this);
        String[] ks = {"عيار 24", "عيار 21", "عيار 18", "عيار 14"};
        k.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ks));
        EditText grams = new EditText(this);
        grams.setHint("الوزن بالجرام *");
        grams.setInputType(2 | 8192);
        EditText paid = new EditText(this);
        paid.setHint("إجمالي سعر الشراء (اختياري)");
        paid.setInputType(2 | 8192);

        LinearLayout presetRow = new LinearLayout(this);
        Button pound = new Button(this);
        pound.setAllCaps(false);
        pound.setText("جنيه ذهب");
        pound.setOnClickListener(v -> { label.setText("جنيه ذهب"); k.setSelection(idx(21)); grams.setText("8"); });
        Button bar10 = new Button(this);
        bar10.setAllCaps(false);
        bar10.setText("سبيكة 10جم 24");
        bar10.setOnClickListener(v -> { label.setText("سبيكة 10 جرام عيار 24"); k.setSelection(idx(24)); grams.setText("10"); });
        presetRow.addView(pound, new LinearLayout.LayoutParams(0, -2, 1));
        presetRow.addView(bar10, new LinearLayout.LayoutParams(0, -2, 1));

        if (old != null) {
            label.setText(old.label);
            k.setSelection(idx(old.karat));
            grams.setText(String.valueOf(old.grams));
            if (old.purchasePrice > 0) paid.setText(String.valueOf(old.purchasePrice));
        }

        box.addView(tv("اختيارات جاهزة", 13, true));
        box.addView(presetRow);
        box.addView(label);
        box.addView(k);
        box.addView(grams);
        box.addView(paid);

        new AlertDialog.Builder(this)
                .setTitle(old == null ? "إضافة ذهب" : "تعديل الذهب")
                .setView(box)
                .setPositiveButton("حفظ", (d, w) -> {
                    try {
                        double g = Double.parseDouble(grams.getText().toString());
                        double p = paid.getText().toString().trim().isEmpty() ? 0 : Double.parseDouble(paid.getText().toString());
                        if (g <= 0) throw new Exception();
                        db.saveGoldHolding(old == null ? null : old.id, label.getText().toString().trim(), karats[k.getSelectedItemPosition()], g, p);
                        render();
                    } catch (Exception e) {
                        Toast.makeText(this, "اكتب وزن صحيح أكبر من صفر", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void refreshPrices(boolean force) {
        if (!force && cacheIsFromToday() && hasAnyPrice()) {
            status.setText(priceLine() + "\nآخر تحديث محفوظ اليوم • المصدر: edahabapp.com");
            render();
            return;
        }
        status.setText("جاري تحديث أسعار eDahab...");
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://edahabapp.com/").openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(12000);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 Masrofaty/2.33");
                StringBuilder html = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = r.readLine()) != null) html.append(line).append(' ');
                }
                String text = normalizeNumbers(html.toString().replaceAll("<[^>]+>", " ").replace("&nbsp;", " ").replaceAll("\\s+", " "));
                for (int i = 0; i < karats.length; i++) {
                    Pattern p = Pattern.compile("الذهب\\s*عيار\\s*" + karats[i] + "\\s*:?\\s*بيع\\s*:?\\s*([0-9,.]+)\\s*جنيه\\s*شراء\\s*:?\\s*([0-9,.]+)", Pattern.DOTALL);
                    Matcher m = p.matcher(text);
                    boolean found = m.find();
                    if (!found) {
                        p = Pattern.compile("عيار\\s*" + karats[i] + ".*?بيع\\s*:?\\s*([0-9,.]+).*?شراء\\s*:?\\s*([0-9,.]+)", Pattern.DOTALL);
                        m = p.matcher(text);
                        found = m.find();
                    }
                    if (!found) throw new IOException("تعذر قراءة عيار " + karats[i]);
                    sell[i] = Double.parseDouble(m.group(1).replace(",", ""));
                    buy[i] = Double.parseDouble(m.group(2).replace(",", ""));
                    db.setSetting("gold_sell_" + karats[i], String.valueOf(sell[i]));
                    db.setSetting("gold_buy_" + karats[i], String.valueOf(buy[i]));
                }
                Matcher pound = Pattern.compile("سعر\\s*الجنيه\\s*الذهب\\s*:?\\s*([0-9,.]+)").matcher(text);
                if (pound.find()) {
                    goldPoundPrice = Double.parseDouble(pound.group(1).replace(",", ""));
                    db.setSetting("gold_pound_price", String.valueOf(goldPoundPrice));
                }
                db.setSetting("gold_updated_at", String.valueOf(System.currentTimeMillis()));
                db.setSetting("gold_updated_day", dayKey(System.currentTimeMillis()));
                runOnUiThread(() -> {
                    status.setText(priceLine() + "\nآخر تحديث: " + new SimpleDateFormat("dd/MM/yyyy hh:mm a", new Locale("ar")).format(new Date()) + " • المصدر: edahabapp.com");
                    render();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    String last = db.getSetting("gold_updated_at", "");
                    status.setText("تعذر تحديث السعر الآن. معروض آخر سعر محفوظ" + (last.isEmpty() ? " (لا يوجد سعر محفوظ بعد)" : " بتاريخ " + new SimpleDateFormat("dd/MM/yyyy", new Locale("ar")).format(new Date(Long.parseLong(last)))) + ".");
                    render();
                });
            }
        }).start();
    }

    private String priceLine() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < karats.length; i++) {
            if (i > 0) s.append("  |  ");
            s.append(karats[i]).append(": بيع ").append(String.format(Locale.US, "%.0f", sell[i])).append(" / شراء ").append(String.format(Locale.US, "%.0f", buy[i]));
        }
        if (goldPoundPrice > 0) s.append("  |  الجنيه الذهب: ").append(String.format(Locale.US, "%.0f", goldPoundPrice));
        return "أسعار الموقع: " + s;
    }

    private boolean hasAnyPrice() {
        for (double v : sell) if (v > 0) return true;
        return goldPoundPrice > 0;
    }

    private boolean cacheIsFromToday() {
        String saved = db.getSetting("gold_updated_day", "");
        return dayKey(System.currentTimeMillis()).equals(saved);
    }

    private String dayKey(long time) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(time));
    }

    private String normalizeNumbers(String value) {
        if (value == null) return "";
        return value
                .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3').replace('٤', '4')
                .replace('٥', '5').replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9')
                .replace('٬', ',').replace('٫', '.');
    }
}

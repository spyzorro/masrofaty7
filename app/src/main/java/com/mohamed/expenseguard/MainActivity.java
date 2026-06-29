package com.mohamed.expenseguard;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.KeyguardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.DocumentsContract;
import android.speech.RecognizerIntent;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Date;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;

import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends Activity {
    private static final int REQ_VOICE = 44;
    private static final int REQ_GOOGLE_SIGN_IN = 77;
    private static final int REQ_DEVICE_LOCK = 88;
    private static final int REQ_CORE_PERMS = 10;
    private static final int REQ_EXPORT_BACKUP_FILE = 120;
    private static final int REQ_IMPORT_BACKUP_FILE = 121;

    private final int BG = Color.rgb(246, 250, 248);
    private final int DARK = Color.rgb(15, 31, 42);
    private final int MUTED = Color.rgb(108, 121, 130);
    private final int PRIMARY = Color.rgb(15, 166, 122);
    private final int PRIMARY_DARK = Color.rgb(8, 110, 100);
    private final int ORANGE = Color.rgb(239, 145, 38);
    private final int BLUE = Color.rgb(51, 122, 255);
    private final int RED = Color.rgb(229, 75, 75);
    private final int PURPLE = Color.rgb(121, 91, 219);

    private ExpenseDbHelper db;
    private FirebaseSyncManager syncManager;
    private LinearLayout root;
    private boolean appUnlocked = false;
    private long lastAutoBackupAttempt = 0L;
    private boolean awaitingDeviceCredential = false;
    private boolean lockDialogShowing = false;

    private boolean isEn() { return db != null && "en".equals(db.getSetting("language", "ar")); }
    private String L(String ar, String en) { return isEn() ? en : ar; }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        db = new ExpenseDbHelper(this);
        boolean restoredLocal = db.restoreLocalBackupIfEmpty();
        db.autoArchiveIfNewMonth();
        syncManager = new FirebaseSyncManager(this, db);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        styleSystemBars();
        requestNeededPermissions();
        showHome();
        if (restoredLocal) toast("تم استرجاع نسخة محلية تلقائيًا");
        else maybePromptBackupRestoreOnFreshInstall();
        maybeShowAppLock();
        autoCheckForUpdatesSilent();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!awaitingDeviceCredential && "1".equals(db.getSetting("app_lock_enabled", "0")) && !appUnlocked) {
            maybeShowAppLock();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if ("1".equals(db.getSetting("app_lock_enabled", "0"))) appUnlocked = false;
        autoCloudBackup();
    }

    private void styleSystemBars() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(PRIMARY_DARK);
            w.setNavigationBarColor(BG);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private void requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            List<String> perms = new ArrayList<>();
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.POST_NOTIFICATIONS);
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.RECORD_AUDIO);
            if (checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.READ_SMS);
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) perms.add(Manifest.permission.RECEIVE_SMS);
            if (!perms.isEmpty()) requestPermissions(perms.toArray(new String[0]), REQ_CORE_PERMS);
        }
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void setup(String title) {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(BG);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        root.setGravity(Gravity.RIGHT);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sv.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setBackgroundColor(BG);
        main.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        main.addView(bottomNav(), new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(70)));
        setContentView(main);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setGravity(Gravity.RIGHT);
        top.setPadding(0, dp(4), 0, dp(10));
        root.addView(top, matchWrap());

        LinearLayout titleRow = row();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = text(title, 26, true, DARK);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        titleRow.addView(t, new LinearLayout.LayoutParams(0, dp(54), 1));
        top.addView(titleRow, matchWrap());

        TextView sub = text("تحكم في ميزانيتك ومصاريفك من مكان واحد", 13, false, MUTED);
        sub.setGravity(Gravity.RIGHT);
        if ("مصروفاتي".equals(title) || "Masrofaty".equals(title)) top.addView(text(L("تحكم في ميزانيتك ومصاريفك من مكان واحد", "Control your budget, expenses, debts, and subscriptions"), 13, false, MUTED), matchWrap());
    }


    private LinearLayout bottomNav() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(6), dp(6), dp(6), dp(6));
        nav.setBackground(strokeBg(Color.WHITE, Color.rgb(222, 234, 230), 0, 1));
        nav.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        addBottomButton(nav, "➕\nإضافة", PRIMARY, v -> openAddMenu());
        addBottomButton(nav, "📋\nالسجل/مراجعة", BLUE, v -> openRecordsMenu());
        addBottomButton(nav, "🏠\nالرئيسية", PRIMARY_DARK, v -> showHome());
        addBottomButton(nav, "💰\nفلوس/كاش", PURPLE, v -> openMoneyMenu());
        addBottomButton(nav, "⚙️\nالمزيد", MUTED, v -> openMainMenu());
        return nav;
    }

    private void addBottomButton(LinearLayout nav, String label, int color, View.OnClickListener click) {
        TextView b = text(label, 11, true, color);
        b.setGravity(Gravity.CENTER);
        b.setTextDirection(View.TEXT_DIRECTION_RTL);
        b.setClickable(true);
        b.setOnClickListener(click);
        b.setBackground(bg(pale(color), 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(dp(3), 0, dp(3), 0);
        nav.addView(b, lp);
    }

    private void openAddMenu() {
        LinearLayout box = menuBox();
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("إضافة سريعة").setView(box).create();
        addMenuButton(box, "إضافة مصروف / دخل كتابة", BLUE, v -> { dialog.dismiss(); manualDialog(); });
        addMenuButton(box, "إضافة بالفويس", PRIMARY, v -> { dialog.dismiss(); startVoice(); });
        addMenuButton(box, "دخل إضافي منفصل", PRIMARY_DARK, v -> { dialog.dismiss(); extraIncomeDialog(); });
        addMenuButton(box, "اشتراك شهري", PURPLE, v -> { dialog.dismiss(); addSubscriptionDialog(); });
        addMenuButton(box, "تعليم/تحليل رسالة بنك", ORANGE, v -> { dialog.dismiss(); bankMessageTrainerDialog(); });
        dialog.show();
    }

    private void openRecordsMenu() {
        LinearLayout box = menuBox();
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("السجل والمراجعة").setView(box).create();
        addMenuButton(box, "سجل العمليات", BLUE, v -> { dialog.dismiss(); showLog(); });
        addMenuButton(box, "عمليات للمراجعة", ORANGE, v -> { dialog.dismiss(); showPending(); });
        addMenuButton(box, "تعليم/تحليل رسالة بنك", PURPLE, v -> { dialog.dismiss(); bankMessageTrainerDialog(); });
        addMenuButton(box, "أرشيف الشهور", PRIMARY_DARK, v -> { dialog.dismiss(); showMonthArchive(); });
        dialog.show();
    }

    private void openMoneyMenu() {
        LinearLayout box = menuBox();
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("الفلوس والديون").setView(box).create();
        addMenuButton(box, "الديون والمواعيد", PURPLE, v -> { dialog.dismiss(); showDebts(); });
        addMenuButton(box, "محفظة الكاش", PRIMARY_DARK, v -> { dialog.dismiss(); showCashWallet(); });
        addMenuButton(box, "الاشتراكات الشهرية", BLUE, v -> { dialog.dismiss(); showSubscriptions(); });
        addMenuButton(box, "مراجعة الأونلاين والوارد", ORANGE, v -> { dialog.dismiss(); showPending(); });
        addMenuButton(box, "تعديل الميزانية والمصروف", PRIMARY, v -> { dialog.dismiss(); budgetAndSpentDialog(); });
        dialog.show();
    }

    private LinearLayout menuBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        return box;
    }

    private void addMenuButton(LinearLayout box, String title, int color, View.OnClickListener click) {
        Button b = softBtn(title, color);
        b.setOnClickListener(click);
        box.addView(b);
    }

    private boolean privacyMode() { return "1".equals(db.getSetting("privacy_mode", "0")); }
    private String money(double v) { return privacyMode() ? "••••" : db.money(v); }
    private String moneyCurrency(double v, String currency) {
        if (privacyMode()) return "••••";
        String sym = "EGP".equalsIgnoreCase(currency) ? "ج.م" : "ر.س";
        return String.format(Locale.US, "%.2f %s", v, sym);
    }

    private String debtSummary(String direction) {
        if (privacyMode()) return "••••";
        double sar = db.getDebtTotal(direction, "SAR");
        double egp = db.getDebtTotal(direction, "EGP");
        if (sar > 0 && egp > 0) return moneyCurrency(sar, "SAR") + " | " + moneyCurrency(egp, "EGP");
        if (egp > 0) return moneyCurrency(egp, "EGP");
        return moneyCurrency(sar, "SAR");
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(12));
        return lp;
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView tv = new TextView(this);
        tv.setText(s == null ? "" : s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.RIGHT);
        tv.setTextDirection(View.TEXT_DIRECTION_RTL);
        tv.setIncludeFontPadding(true);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView tv = this.text(text, sp, bold, bold ? DARK : MUTED);
        tv.setPadding(dp(2), dp(4), dp(2), dp(4));
        return tv;
    }

    private GradientDrawable bg(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable strokeBg(int color, int strokeColor, float radiusDp, int strokeDp) {
        GradientDrawable g = bg(color, radiusDp);
        g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private GradientDrawable gradient(int start, int end, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private LinearLayout card() { return card(Color.WHITE); }

    private LinearLayout card(int color) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(bg(color, 22));
        c.setLayoutParams(cardLp());
        if (Build.VERSION.SDK_INT >= 21) c.setElevation(dp(2));
        return c;
    }

    private Button btn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(48));
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setBackground(gradient(PRIMARY, PRIMARY_DARK, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        lp.setMargins(0, dp(6), 0, dp(6));
        b.setLayoutParams(lp);
        return b;
    }

    private Button softBtn(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14);
        b.setTextColor(color);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(46));
        int pale = pale(color);
        b.setBackground(strokeBg(pale, lighten(color), 15, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        lp.setMargins(0, dp(5), 0, dp(5));
        b.setLayoutParams(lp);
        return b;
    }

    private int pale(int color) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return Color.rgb((int)(r * 0.12 + 255 * 0.88), (int)(g * 0.12 + 255 * 0.88), (int)(b * 0.12 + 255 * 0.88));
    }

    private int lighten(int color) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return Color.rgb((int)(r * 0.28 + 255 * 0.72), (int)(g * 0.28 + 255 * 0.72), (int)(b * 0.28 + 255 * 0.72));
    }

    private TextView pill(String text, int color) {
        TextView p = this.text(text, 12, true, color);
        p.setGravity(Gravity.CENTER);
        p.setPadding(dp(10), dp(4), dp(10), dp(4));
        p.setBackground(bg(pale(color), 50));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(4), dp(4), 0, dp(4));
        p.setLayoutParams(lp);
        return p;
    }

    private void addHomeButton() {
        Button home = softBtn("← رجوع للرئيسية", PRIMARY_DARK);
        home.setOnClickListener(v -> showHome());
        root.addView(home);
    }

    private void openMainMenu() {
        ScrollView sc = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(10), dp(14), dp(10));
        sc.addView(box, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("المزيد").setView(sc).create();

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(14), dp(12), dp(14), dp(12));
        head.setBackground(gradient(PRIMARY, PRIMARY_DARK, 22));
        head.addView(text("إعدادات وأدوات مصروفاتي", 18, true, Color.WHITE), matchWrap());
        head.addView(text("الحاجات الأساسية موجودة في الأزرار اللي تحت، وهنا الأدوات والإعدادات بس.", 12, false, Color.rgb(230, 255, 248)), matchWrap());
        box.addView(head, cardLp());

        addMoreTile(box, "مزامنة Google والنسخ الاحتياطي", "تسجيل الدخول، حفظ واسترجاع الداتا، واستيراد ملف", PRIMARY, v -> { dialog.dismiss(); showGoogleSyncCenter(); });
        addMoreTile(box, "التحديثات", "فحص سريع للتحديثات من GitHub", BLUE, v -> { dialog.dismiss(); showUpdateCenter(); });
        addMoreTile(box, "الإعدادات والخصوصية", "القفل، وضع الخصوصية، التذكيرات، والتنبيهات", ORANGE, v -> { dialog.dismiss(); showSecurityAndReminderSettings(); });
        addMoreTile(box, "اللغة والعملة", "عربي / English واختيار ريال أو جنيه", PRIMARY_DARK, v -> { dialog.dismiss(); openLanguageCurrencyMenu(); });

        dialog.show();
    }

    private void addMoreTile(LinearLayout box, String title, String subtitle, int color, View.OnClickListener click) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(14), dp(12), dp(14), dp(12));
        tile.setBackground(strokeBg(pale(color), lighten(color), 20, 1));
        tile.setClickable(true);
        tile.setOnClickListener(click);
        if (Build.VERSION.SDK_INT >= 21) tile.setElevation(dp(1));
        tile.addView(text(title, 16, true, color), matchWrap());
        tile.addView(text(subtitle, 12, false, DARK), matchWrap());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(6), 0, dp(8));
        box.addView(tile, lp);
    }

    private void openLanguageCurrencyMenu() {
        LinearLayout box = menuBox();
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("اللغة والعملة").setView(box).create();
        addMenuButton(box, L("اختيار العملة", "Currency"), PRIMARY_DARK, v -> { dialog.dismiss(); currencyDialog(); });
        addMenuButton(box, L("اللغة / Language", "Language / اللغة"), BLUE, v -> { dialog.dismiss(); languageDialog(); });
        dialog.show();
    }


    private void showGoogleSyncCenter() {
        setup("مزامنة Google");
        addHomeButton();

        FirebaseUser user = syncManager.currentUser();
        LinearLayout info = card();
        info.addView(text("حفظ واسترجاع الداتا", 22, true, DARK), matchWrap());
        info.addView(text("سجل دخول بحساب Google، وبعدها التطبيق يحفظ نسخة احتياطية تلقائيًا ويسترجعها تلقائيًا لو غيرت الموبايل أو ثبت التطبيق من جديد.", 13, false, MUTED), matchWrap());
        info.addView(pill(user == null ? "غير متصل" : "متصل: " + user.getEmail(), user == null ? ORANGE : PRIMARY), matchWrap());
        root.addView(info);

        LinearLayout actions = card();
        if (!syncManager.hasConfig()) {
            actions.addView(text("مالك التطبيق لم يفعّل إعدادات Google بعد", 18, true, ORANGE), matchWrap());
            actions.addView(text("بعد ما تضيف قيم Firebase في ملف strings.xml، المستخدمين هيشوفوا زر تسجيل الدخول فقط بدون أي إعدادات معقدة.", 13, false, MUTED), matchWrap());
        }

        Button login = btn(user == null ? "تسجيل الدخول بحساب Google" : "تغيير حساب Google");
        login.setOnClickListener(v -> {
            try { startActivityForResult(syncManager.signInIntent(), REQ_GOOGLE_SIGN_IN); }
            catch (Exception e) { new AlertDialog.Builder(this).setTitle("المزامنة غير جاهزة").setMessage(e.getMessage()).setPositiveButton("تمام", null).show(); }
        });
        actions.addView(login);

        CheckBox autoBackup = new CheckBox(this);
        autoBackup.setText("حفظ تلقائي بعد أي تعديل");
        autoBackup.setChecked("1".equals(db.getSetting("google_auto_backup", "1")));
        autoBackup.setGravity(Gravity.RIGHT); autoBackup.setTextDirection(View.TEXT_DIRECTION_RTL);
        actions.addView(autoBackup);

        CheckBox autoRestore = new CheckBox(this);
        autoRestore.setText("استرجاع تلقائي بعد تسجيل الدخول");
        autoRestore.setChecked("1".equals(db.getSetting("google_auto_restore", "1")));
        autoRestore.setGravity(Gravity.RIGHT); autoRestore.setTextDirection(View.TEXT_DIRECTION_RTL);
        actions.addView(autoRestore);

        Button saveAuto = softBtn("حفظ الإعدادات", PRIMARY_DARK);
        saveAuto.setOnClickListener(v -> {
            db.setSetting("google_auto_backup", autoBackup.isChecked() ? "1" : "0");
            db.setSetting("google_auto_restore", autoRestore.isChecked() ? "1" : "0");
            autoCloudBackup();
            toast("تم الحفظ");
        });
        actions.addView(saveAuto);

        Button upload = softBtn("حفظ نسخة الآن", BLUE);
        upload.setOnClickListener(v -> syncManager.uploadBackup(new FirebaseSyncManager.Callback() {
            @Override public void ok(String message) { runOnUiThread(() -> toast(message)); }
            @Override public void fail(String message) { runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this).setTitle("تعذر الحفظ").setMessage(message).setPositiveButton("تمام", null).show()); }
        }));
        actions.addView(upload);

        Button restore = softBtn("استرجاع النسخة", ORANGE);
        restore.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("استرجاع الداتا")
                .setMessage("هيتم استبدال الداتا الموجودة بالنسخة المحفوظة على حسابك. تكمل؟")
                .setPositiveButton("استرجاع", (d, w) -> syncManager.restoreBackup(new FirebaseSyncManager.Callback() {
                    @Override public void ok(String message) { runOnUiThread(() -> { toast(message); showHome(); }); }
                    @Override public void fail(String message) { runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this).setTitle("تعذر الاسترجاع").setMessage(message).setPositiveButton("تمام", null).show()); }
                }))
                .setNegativeButton("إلغاء", null).show());
        actions.addView(restore);

        if (user != null) {
            Button logout = softBtn("تسجيل الخروج", RED);
            logout.setOnClickListener(v -> syncManager.signOut(this, new FirebaseSyncManager.Callback() {
                @Override public void ok(String message) { runOnUiThread(() -> { toast(message); showGoogleSyncCenter(); }); }
                @Override public void fail(String message) { runOnUiThread(() -> toast(message)); }
            }));
            actions.addView(logout);
        }
        root.addView(actions);

        LinearLayout local = card(pale(PRIMARY));
        local.setBackground(strokeBg(pale(PRIMARY), lighten(PRIMARY), 22, 1));
        local.addView(text("نسخة احتياطية على الهاتف", 18, true, PRIMARY_DARK), matchWrap());
        local.addView(text("مهم: أندرويد بيمسح داتا التطبيق الداخلية وقت الحذف. عشان الداتا تفضل بعد Uninstall لازم تحفظ ملف نسخة احتياطية في Downloads/Documents أو تستخدم Google Sync.", 13, false, DARK), matchWrap());
        String last = db.getSetting("last_local_backup", "");
        try { if (last.length() > 0) local.addView(text("آخر حفظ محلي: " + ExpenseDbHelper.date(Long.parseLong(last)), 12, false, MUTED), matchWrap()); } catch (Exception ignored) {}
        String uriSaved = db.getSetting("phone_backup_uri", "");
        if (uriSaved.length() > 0) local.addView(pill("ملف نسخ احتياطي مختار", PRIMARY), matchWrap());

        Button chooseBackupFile = softBtn("اختيار / إنشاء ملف حفظ دائم", BLUE);
        chooseBackupFile.setOnClickListener(v -> chooseBackupFileForAutoSave());
        local.addView(chooseBackupFile);

        Button savePhone = softBtn("حفظ نسخة على الهاتف الآن", PRIMARY_DARK);
        savePhone.setOnClickListener(v -> {
            db.saveLocalBackup();
            boolean ok = saveBackupToChosenUri(false);
            toast(ok ? "تم حفظ النسخة على ملف الهاتف" : "تم الحفظ الداخلي. اختار ملف حفظ دائم لو عايزها ترجع بعد حذف التطبيق");
            showGoogleSyncCenter();
        });
        local.addView(savePhone);

        Button importFile = softBtn("استيراد نسخة من ملف", ORANGE);
        importFile.setOnClickListener(v -> chooseBackupFileForImport());
        local.addView(importFile);

        Button restorePhone = softBtn("محاولة استرجاع تلقائي من Downloads", MUTED);
        restorePhone.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("استرجاع من الهاتف")
                .setMessage("هيتم استبدال الداتا الحالية لو التطبيق قدر يقرأ النسخة الموجودة في Downloads/Masrofaty. لو ملقاش حاجة استخدم زر استيراد نسخة من ملف.")
                .setPositiveButton("استرجاع", (d,w) -> { boolean ok = db.restoreLocalBackupForce(); toast(ok ? "تم الاسترجاع من ملف الهاتف" : "ملقتش نسخة. استخدم استيراد نسخة من ملف"); showHome(); })
                .setNegativeButton("إلغاء", null).show());
        local.addView(restorePhone);
        root.addView(local);
    }

    private String defaultUpdateUrl() {
        String saved = db.getSetting("update_json_url", "").trim();
        if (isValidUpdateUrl(saved)) return saved;

        try {
            int id = getResources().getIdentifier("update_json_url", "string", getPackageName());
            String v = id == 0 ? "" : getString(id).trim();
            if (isValidUpdateUrl(v)) return v;
        } catch (Exception ignored) {}

        try {
            String repo = BuildConfig.GITHUB_REPOSITORY == null ? "" : BuildConfig.GITHUB_REPOSITORY.trim();
            String branch = BuildConfig.GITHUB_BRANCH == null ? "main" : BuildConfig.GITHUB_BRANCH.trim();
            if (repo.length() > 0 && repo.contains("/")) {
                if (branch.length() == 0) branch = "main";
                return "https://raw.githubusercontent.com/" + repo + "/" + branch + "/update.json";
            }
        } catch (Exception ignored) {}

        return "";
    }

    private boolean isValidUpdateUrl(String url) {
        if (url == null) return false;
        String u = url.trim();
        if (u.length() == 0) return false;
        if (!u.startsWith("http://") && !u.startsWith("https://")) return false;
        if (u.contains("USERNAME") || u.contains("REPO") || u.contains("رابط")) return false;
        return true;
    }

    private void showUpdateCenter() {
        setup("التحديثات");
        addHomeButton();

        LinearLayout info = card();
        info.addView(text("تحديث التطبيق", 22, true, DARK), matchWrap());
        info.addView(text("الإصدار الحالي: " + BuildConfig.VERSION_NAME, 14, true, PRIMARY_DARK), matchWrap());
        info.addView(text("التطبيق بيفحص التحديث تلقائيًا عند الفتح. الزر ده للفحص اليدوي فقط.", 13, false, MUTED), matchWrap());
        Button check = btn("فحص التحديث الآن");
        check.setOnClickListener(v -> checkForUpdates(defaultUpdateUrl()));
        info.addView(check);
        root.addView(info);

        String url = defaultUpdateUrl();
        if (url.length() == 0) {
            LinearLayout warn = card(pale(ORANGE));
            warn.setBackground(strokeBg(pale(ORANGE), lighten(ORANGE), 22, 1));
            warn.addView(text("رابط التحديث غير مضاف", 18, true, ORANGE), matchWrap());
            warn.addView(text("مالك التطبيق يضيف رابط update.json مرة واحدة داخل strings.xml عشان المستخدم العادي ميشوفش إعدادات معقدة.", 13, false, DARK), matchWrap());
            root.addView(warn);
        }
    }

    private void autoCheckForUpdatesSilent() {
        String url = defaultUpdateUrl();
        if (url.length() == 0) return;
        long now = System.currentTimeMillis();
        long last = 0;
        try { last = Long.parseLong(db.getSetting("last_update_check", "0")); } catch (Exception ignored) {}
        if (now - last < 6L * 60L * 60L * 1000L) return;
        db.setSetting("last_update_check", String.valueOf(now));
        new Thread(() -> {
            try {
                String jsonText = downloadText(url);
                JSONObject obj = new JSONObject(jsonText);
                int latestCode = obj.optInt("latestVersionCode", obj.optInt("versionCode", BuildConfig.VERSION_CODE));
                if (latestCode <= BuildConfig.VERSION_CODE) return;
                String latestName = obj.optString("latestVersionName", obj.optString("versionName", ""));
                String apkUrl = obj.optString("apkUrl", "");
                String notes = obj.optString("notes", "");
                String dataVersion = obj.optString("dataVersion", "");
                String msg = obj.optString("message", "");
                runOnUiThread(() -> showUpdateResult(latestCode, latestName, apkUrl, notes, dataVersion, msg));
            } catch (Exception ignored) {}
        }).start();
    }

    private void checkForUpdates(String updateUrl) {
        if (updateUrl == null || updateUrl.trim().isEmpty()) {
            toast("رابط التحديث غير جاهز. ابني النسخة من GitHub Actions أو أضف update.json");
            return;
        }
        toast("جاري فحص التحديث...");
        new Thread(() -> {
            try {
                String jsonText = downloadText(updateUrl);
                JSONObject obj = new JSONObject(jsonText);
                int latestCode = obj.optInt("latestVersionCode", obj.optInt("versionCode", BuildConfig.VERSION_CODE));
                String latestName = obj.optString("latestVersionName", obj.optString("versionName", ""));
                String apkUrl = obj.optString("apkUrl", "");
                String notes = obj.optString("notes", "");
                String dataVersion = obj.optString("dataVersion", "");
                String msg = obj.optString("message", "");
                if (dataVersion.length() > 0) db.setSetting("remote_data_version", dataVersion);
                if (msg.length() > 0) db.setSetting("remote_update_message", msg);
                db.setSetting("last_update_json", jsonText);
                runOnUiThread(() -> showUpdateResult(latestCode, latestName, apkUrl, notes, dataVersion, msg));
            } catch (Exception e) {
                runOnUiThread(() -> new AlertDialog.Builder(this)
                        .setTitle("فشل فحص التحديث")
                        .setMessage("" + e.getMessage())
                        .setPositiveButton("تمام", null).show());
            }
        }).start();
    }

    private String downloadText(String urlText) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(urlText).openConnection();
        con.setConnectTimeout(12000);
        con.setReadTimeout(12000);
        con.setRequestMethod("GET");
        con.setRequestProperty("Accept", "application/json,text/plain,*/*");
        int code = con.getResponseCode();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private void showUpdateResult(int latestCode, String latestName, String apkUrl, String notes, String dataVersion, String msg) {
        boolean hasAppUpdate = latestCode > BuildConfig.VERSION_CODE && apkUrl != null && apkUrl.trim().length() > 0;
        StringBuilder m = new StringBuilder();
        if (hasAppUpdate) {
            m.append("إصدار جديد متاح");
            if (latestName != null && latestName.length() > 0) m.append(": ").append(latestName);
            if (notes != null && notes.length() > 0) m.append("\n\n").append(notes);
            if (msg != null && msg.length() > 0) m.append("\n\n").append(msg);
        } else {
            m.append("أنت على آخر إصدار حاليًا");
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(hasAppUpdate ? "تحديث جديد" : "لا يوجد تحديث")
                .setMessage(m.toString())
                .setNegativeButton("إغلاق", null);
        if (hasAppUpdate) {
            b.setPositiveButton("تحميل", (d, w) -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl))); }
                catch (Exception e) { toast("مش قادر أفتح رابط التحميل"); }
            });
        }
        b.show();
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return r;
    }

    private void addWeighted(LinearLayout row, View child, float weight, int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight);
        lp.setMargins(dp(margin), dp(4), dp(margin), dp(4));
        row.addView(child, lp);
    }

    private LinearLayout statCard(String icon, String title, String value, int color) {
        return statCard(icon, title, value, color, null);
    }

    private LinearLayout statCard(String icon, String title, String value, int color, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(10), dp(12), dp(10));
        c.setGravity(Gravity.RIGHT);
        c.setBackground(bg(Color.WHITE, 20));
        if (click != null) { c.setClickable(true); c.setOnClickListener(click); }
        if (Build.VERSION.SDK_INT >= 21) c.setElevation(dp(1));
        LinearLayout top = row();
        TextView ic = text(icon, 18, true, color);
        top.addView(ic, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        Space sp = new Space(this); top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));
        TextView tt = text(title, 12, false, MUTED); top.addView(tt);
        c.addView(top, matchWrap());
        c.addView(text(value, 16, true, DARK), matchWrap());
        return c;
    }

    private LinearLayout actionCard(String icon, String title, String subtitle, int color, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.setGravity(Gravity.RIGHT);
        c.setClickable(true);
        c.setBackground(strokeBg(Color.WHITE, lighten(color), 20, 1));
        if (Build.VERSION.SDK_INT >= 21) c.setElevation(dp(1));
        c.setOnClickListener(click);
        TextView ic = text(icon, 23, true, color); ic.setGravity(Gravity.RIGHT); c.addView(ic, matchWrap());
        c.addView(text(title, 14, true, DARK), matchWrap());
        c.addView(text(subtitle, 11, false, MUTED), matchWrap());
        return c;
    }

    private String statusArabic(String status) {
        if ("CONFIRMED".equals(status)) return "تم الخصم";
        if ("PENDING_ONLINE".equals(status)) return "أونلاين للمراجعة";
        if ("PENDING_INCOMING".equals(status)) return "وارد للمراجعة";
        if ("SAVED_ONLY".equals(status)) return "حفظ فقط";
        if ("PENDING_REVIEW".equals(status)) return "تحتاج مراجعة";
        return status;
    }

    private int statusColor(String status) {
        if ("CONFIRMED".equals(status)) return PRIMARY;
        if ("PENDING_ONLINE".equals(status)) return ORANGE;
        if ("PENDING_INCOMING".equals(status)) return BLUE;
        if ("SAVED_ONLY".equals(status)) return MUTED;
        if ("PENDING_REVIEW".equals(status)) return PURPLE;
        return PURPLE;
    }

    private String debtStatusArabic(String status) {
        if ("PAID".equals(status)) return "تم السداد";
        if ("PARTIAL".equals(status)) return "سداد جزئي";
        return "لم يسدد";
    }

    private int debtStatusColor(String status) {
        if ("PAID".equals(status)) return PRIMARY;
        if ("PARTIAL".equals(status)) return ORANGE;
        return RED;
    }

    private void showHome() {
        setup(L("مصروفاتي", "Masrofaty"));
        double budget = db.getBudget();
        double spent = db.getMonthlySpent();
        double remaining = budget - spent;
        double extraIncome = db.getExtraIncome();
        int pending = db.getPendingCount();
        double pendingTotal = db.getPendingTotal();
        double owedToMe = db.getDebtTotal("OWED_TO_ME");
        double oweToOthers = db.getDebtTotal("OWE_TO_OTHERS");
        int upcomingDues = db.getUpcomingDebtCount(System.currentTimeMillis() + 3L * 24L * 60L * 60L * 1000L);
        double subscriptionsTotal = db.getActiveSubscriptionsTotal();
        double cashBalance = db.getCashBalance();
        double today = db.getTodaySpent();
        int count = db.getMonthlyExpenseCount();
        double progress = budget <= 0 ? 0 : spent / budget;

        LinearLayout hero = card();
        hero.setBackground(gradient(PRIMARY, PRIMARY_DARK, 26));
        hero.setPadding(dp(18), dp(18), dp(18), dp(18));
        hero.setClickable(true);
        hero.setOnClickListener(v -> showLog());
        hero.addView(text("المتبقي من ميزانية الشهر", 14, false, Color.rgb(222, 255, 246)), matchWrap());
        TextView rem = text(money(remaining), 32, true, Color.WHITE);
        rem.setGravity(Gravity.RIGHT);
        hero.addView(rem, matchWrap());
        TextView spendLine = text("صرفت " + money(spent) + " من " + money(budget), 13, false, Color.rgb(220, 250, 242));
        hero.addView(spendLine, matchWrap());
        BudgetProgressView pv = new BudgetProgressView(this, progress, Color.argb(75, 255, 255, 255), Color.WHITE);
        LinearLayout.LayoutParams pvlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10));
        pvlp.setMargins(0, dp(12), 0, dp(4));
        hero.addView(pv, pvlp);
        TextView hint = text(budget <= 0 ? "حدد ميزانية الشهر عشان يبدأ الحساب" : "استهلاك الميزانية: " + Math.round(Math.min(progress, 1) * 100) + "%", 12, false, Color.rgb(220, 250, 242));
        hero.addView(hint, matchWrap());
        TextView editHint = text("اضغط هنا لفتح سجل العمليات وتعديل أي عملية", 12, true, Color.WHITE);
        editHint.setPadding(0, dp(8), 0, 0);
        hero.addView(editHint, matchWrap());
        root.addView(hero);

        LinearLayout actions = card();
        actions.addView(text("إجراءات سريعة", 18, true, DARK), matchWrap());
        actions.addView(text("ضيف مصروف، زود الميزانية، أو راجع عمليات البنك بسرعة", 12, false, MUTED), matchWrap());
        LinearLayout ar1 = row();
        addWeighted(ar1, actionCard("✍️", "إضافة كتابة", "مصروف أو دخل أو تعديل", BLUE, v -> manualDialog()), 1, 4);
        addWeighted(ar1, actionCard("🎙️", "إضافة بالفويس", "قول: صرفت 20 " + db.currencySymbol(), PRIMARY, v -> startVoice()), 1, 4);
        actions.addView(ar1, matchWrap());
        LinearLayout ar2 = row();
        addWeighted(ar2, actionCard("➕", "زود الميزانية", "إضافة مبلغ للشهر", PRIMARY_DARK, v -> budgetDialog("زود الميزانية بمبلغ", 1)), 1, 4);
        addWeighted(ar2, actionCard("➖", "انقص الميزانية", "تقليل ميزانية الشهر", RED, v -> budgetDialog("انقص الميزانية بمبلغ", -1)), 1, 4);
        actions.addView(ar2, matchWrap());
        LinearLayout ar3 = row();
        addWeighted(ar3, actionCard("💵", L("محفظة الكاش", "Cash wallet"), L("إضافة أو صرف كاش", "Add or spend cash"), PRIMARY_DARK, v -> showCashWallet()), 1, 4);
        addWeighted(ar3, actionCard("💰", "دخل إضافي", "إضافة دخل للشهر", PRIMARY, v -> extraIncomeDialog()), 1, 4);
        actions.addView(ar3, matchWrap());
        LinearLayout ar4 = row();
        addWeighted(ar4, actionCard("🔁", "اشتراك شهري", "Google / Netflix وغيره", BLUE, v -> addSubscriptionDialog()), 1, 4);
        addWeighted(ar4, actionCard("🔐", "قفل التطبيق", "PIN أو قفل الجهاز", PRIMARY_DARK, v -> showSecurityAndReminderSettings()), 1, 4);
        actions.addView(ar4, matchWrap());
        root.addView(actions);

        LinearLayout stats1 = row();
        addWeighted(stats1, statCard("📌", "عمليات للمراجعة", String.valueOf(pending), ORANGE, v -> showPending()), 1, 4);
        addWeighted(stats1, statCard("💳", "مصروف اليوم", money(today), BLUE, v -> showLog()), 1, 4);
        root.addView(stats1, matchWrap());

        LinearLayout stats2 = row();
        addWeighted(stats2, statCard("💰", "دخل إضافي منفصل", money(extraIncome), PRIMARY, v -> extraIncomeDialog()), 1, 4);
        addWeighted(stats2, statCard("🤝", "ليك عند الناس", debtSummary("OWED_TO_ME"), PURPLE, v -> showDebts()), 1, 4);
        root.addView(stats2, matchWrap());

        LinearLayout stats3 = row();
        addWeighted(stats3, statCard("📤", "عليك للناس", debtSummary("OWE_TO_OTHERS"), RED, v -> showDebts()), 1, 4);
        addWeighted(stats3, statCard("⏰", "مواعيد قريبة", String.valueOf(upcomingDues), ORANGE), 1, 4);
        root.addView(stats3, matchWrap());

        LinearLayout stats4 = row();
        addWeighted(stats4, statCard("🔁", L("اشتراكات شهرية", "Monthly subs"), money(subscriptionsTotal), BLUE, v -> showSubscriptions()), 1, 4);
        addWeighted(stats4, statCard("💵", L("محفظة الكاش", "Cash wallet"), money(cashBalance), PRIMARY_DARK, v -> showCashWallet()), 1, 4);
        root.addView(stats4, matchWrap());

        if (pending > 0) {
            LinearLayout pendingCard = card(pale(ORANGE));
            pendingCard.setBackground(strokeBg(pale(ORANGE), lighten(ORANGE), 22, 1));
            pendingCard.addView(text("عندك " + pending + " عملية محتاجة مراجعة", 18, true, ORANGE), matchWrap());
            pendingCard.addView(text("إجمالي تقريبي: " + money(pendingTotal) + " — الأونلاين والوارد مش بيتخصموا غير بموافقتك", 12, false, DARK), matchWrap());
            Button review = btn("راجع العمليات الآن"); review.setOnClickListener(v -> showPending()); pendingCard.addView(review);
            root.addView(pendingCard);
        }

        LinearLayout chart = card();
        chart.addView(text("توزيع مصاريف الشهر", 18, true, DARK), matchWrap());
        chart.addView(text(count == 0 ? "لسه مفيش مصاريف مؤكدة الشهر ده" : count + " عملية مؤكدة خلال الشهر", 12, false, MUTED), matchWrap());
        CategoryChartView cv = new CategoryChartView(this, db.getSpendingByCategory(5));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(150));
        clp.setMargins(0, dp(10), 0, dp(4));
        chart.addView(cv, clp);
        root.addView(chart);


        root.addView(text("شراء PoS والحوالة الصادرة يتخصموا تلقائيًا. شراء الإنترنت والوارد يدخلوا مراجعة فقط.", 12, false, MUTED));
    }

    private void languageDialog() {
        String[] items = new String[]{"العربية", "English"};
        int checked = "en".equals(db.getSetting("language", "ar")) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("Language / اللغة")
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    db.setSetting("language", which == 1 ? "en" : "ar");
                    autoCloudBackup();
                    dialog.dismiss();
                    toast(which == 1 ? "English enabled" : "تم تفعيل العربية");
                    showHome();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void currencyDialog() {
        String[] items = new String[]{"ريال سعودي", "جنيه مصري"};
        int checked = "EGP".equals(db.getCurrency()) ? 1 : 0;
        new AlertDialog.Builder(this)
                .setTitle("اختيار العملة")
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    db.setCurrency(which == 1 ? "EGP" : "SAR");
                    autoCloudBackup();
                    dialog.dismiss();
                    toast("تم تغيير العملة إلى " + db.currencyName());
                    showHome();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void budgetAndSpentDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(5), dp(20), dp(5));

        EditText budgetField = field("الميزانية الشهرية", String.valueOf(db.getBudget()));
        budgetField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText spentField = field("إجمالي اللي اتصرف الشهر ده", String.valueOf(db.getMonthlySpent()));
        spentField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView note = text("استخدم الخانة دي لو الفويس أو رسالة بنك سجلت مبلغ غلط. التطبيق هيعمل عملية تصحيح عشان إجمالي المصروف يبقى الرقم اللي كتبته.", 12, false, MUTED);
        note.setPadding(0, dp(8), 0, dp(8));

        box.addView(label("الميزانية الشهرية", 13, true));
        box.addView(budgetField);
        box.addView(label("إجمالي المصروف الحالي", 13, true));
        box.addView(spentField);
        box.addView(note);

        new AlertDialog.Builder(this)
                .setTitle("تعديل الميزانية والمصروف")
                .setView(box)
                .setPositiveButton("حفظ", (d, w) -> {
                    double newBudget = parseAmount(budgetField.getText().toString());
                    double newSpent = parseAmount(spentField.getText().toString());
                    db.setBudget(newBudget);
                    db.adjustMonthlySpentTo(newSpent);
                    toast("تم التعديل");
                    autoCloudBackup();
                    showHome();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void budgetDialog(String title, int mode) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("اكتب المبلغ بـ " + db.currencyName());
        input.setGravity(Gravity.RIGHT);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton("حفظ", (d, w) -> {
                    double amount = parseAmount(input.getText().toString());
                    if (amount <= 0) return;
                    if (mode == 0) db.setBudget(amount);
                    else if (mode == 1) db.addToBudget(amount);
                    else db.addToBudget(-amount);
                    String raw = title + " " + amount;
                    db.insertManual(mode == -1 ? "BUDGET_DECREASE" : mode == 1 ? "BUDGET_INCREASE" : "BUDGET_SET", "CONFIRMED", amount, title, 0, raw);
                    autoCloudBackup();
                    showHome();
                }).setNegativeButton("إلغاء", null).show();
    }

    private void manualDialog() {
        final EditText input = new EditText(this);
        input.setMinLines(3);
        input.setGravity(Gravity.RIGHT);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setHint("مثال: صرفت 25 " + db.currencySymbol() + " قهوة\nأو: زود الميزانية 500\nأو: دخل 200 سداد شغل");
        new AlertDialog.Builder(this)
                .setTitle("إضافة كتابة")
                .setView(input)
                .setPositiveButton("إضافة", (d, w) -> handleManualInput(input.getText().toString()))
                .setNegativeButton("إلغاء", null).show();
    }

    private void handleManualInput(String text) {
        MessageParser.ParsedTransaction tx = MessageParser.parseManualText(text, db.getBudget());
        if (tx == null) { toast("مش قادر أفهم المبلغ"); return; }
        tx.currency = db.getCurrency();
        if ("BUDGET_INCREASE".equals(tx.type)) db.addToBudget(tx.amount);
        else if ("BUDGET_DECREASE".equals(tx.type)) db.addToBudget(-tx.amount);
        db.insertParsed(tx);
        autoCloudBackup();
        toast("تم التسجيل");
        showHome();
    }


    private void extraIncomeDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(5), dp(20), dp(5));
        EditText amount = field("المبلغ", "");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText title = field("مصدر الدخل: راتب / شغل / هدية", "دخل إضافي");
        box.addView(label("مبلغ الدخل", 13, true));
        box.addView(amount);
        box.addView(label("الوصف", 13, true));
        box.addView(title);
        new AlertDialog.Builder(this)
                .setTitle("إضافة دخل إضافي منفصل")
                .setView(box)
                .setPositiveButton("حفظ", (d, w) -> {
                    double a = parseAmount(amount.getText().toString());
                    if (a <= 0) return;
                    String t = title.getText().toString().trim();
                    if (t.length() == 0) t = "دخل إضافي";
                    db.insertManualCurrencyCategory("EXTRA_INCOME", "CONFIRMED", a, t, 0, "دخل إضافي يدوي: " + t, db.getCurrency(), "دخل إضافي");
                    autoCloudBackup();
                    toast("تم إضافة الدخل");
                    showHome();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void startVoice() {
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_CORE_PERMS);
            toast("اسمح بالمايك وبعدها جرب الفويس تاني");
            return;
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA");
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "قول العملية: صرفت 20 " + db.currencySymbol() + " قهوة");
        try { startActivityForResult(i, REQ_VOICE); }
        catch (Exception e) { toast("محتاج تطبيق يدعم تحويل الصوت لنص على الجهاز"); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> res = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (res != null && !res.isEmpty()) handleManualInput(res.get(0));
        } else if (requestCode == REQ_DEVICE_LOCK) {
            awaitingDeviceCredential = false;
            if (resultCode == RESULT_OK) { appUnlocked = true; toast("تم فتح القفل"); }
            else maybeShowAppLock();
        } else if (requestCode == REQ_EXPORT_BACKUP_FILE && resultCode == RESULT_OK && data != null) {
            handleBackupFileChosen(data);
        } else if (requestCode == REQ_IMPORT_BACKUP_FILE && resultCode == RESULT_OK && data != null) {
            importBackupFromUri(data.getData(), data.getFlags());
        } else if (requestCode == REQ_GOOGLE_SIGN_IN && data != null) {
            syncManager.handleSignInResult(data, new FirebaseSyncManager.Callback() {
                @Override public void ok(String message) { runOnUiThread(() -> {
                    toast(message);
                    if ("1".equals(db.getSetting("google_auto_restore", "1"))) {
                        syncManager.restoreBackup(new FirebaseSyncManager.Callback() {
                            @Override public void ok(String msg) { runOnUiThread(() -> { toast("تم استرجاع الداتا تلقائيًا"); showHome(); }); }
                            @Override public void fail(String msg) { runOnUiThread(() -> { autoCloudBackup(); showGoogleSyncCenter(); }); }
                        });
                    } else {
                        autoCloudBackup();
                        showGoogleSyncCenter();
                    }
                }); }
                @Override public void fail(String message) { runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this).setTitle("مشكلة تسجيل الدخول").setMessage(message).setPositiveButton("تمام", null).show()); }
            });
        }
    }



    private void maybePromptBackupRestoreOnFreshInstall() {
        try {
            if (db.hasUserData()) return;
            String seen = db.getSetting("fresh_restore_prompt_seen", "");
            if ("1".equals(seen)) return;
            db.setSetting("fresh_restore_prompt_seen", "1");
            root.postDelayed(() -> new AlertDialog.Builder(this)
                    .setTitle("استرجاع الداتا")
                    .setMessage("لو كنت حذفت التطبيق قبل كده، أندرويد بيمسح الداتا الداخلية. استرجعها من ملف النسخة الاحتياطية أو سجل دخول Google.")
                    .setPositiveButton("استيراد من ملف", (d,w) -> chooseBackupFileForImport())
                    .setNegativeButton("Google Sync", (d,w) -> showGoogleSyncCenter())
                    .setNeutralButton("لاحقًا", null)
                    .show(), 700);
        } catch (Exception ignored) { }
    }

    private void chooseBackupFileForAutoSave() {
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "masrofaty_backup.json");
            startActivityForResult(i, REQ_EXPORT_BACKUP_FILE);
        } catch (Exception e) {
            toast("الجهاز لا يدعم اختيار ملف الحفظ");
        }
    }

    private void chooseBackupFileForImport() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_IMPORT_BACKUP_FILE);
        } catch (Exception e) {
            toast("الجهاز لا يدعم اختيار ملف الاستيراد");
        }
    }

    private void handleBackupFileChosen(Intent data) {
        Uri uri = data == null ? null : data.getData();
        if (uri == null) { toast("لم يتم اختيار ملف"); return; }
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            if (flags != 0) getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) { }
        db.setSetting("phone_backup_uri", uri.toString());
        boolean ok = saveBackupToChosenUri(true);
        toast(ok ? "تم ربط ملف الحفظ وحفظ النسخة" : "تم اختيار الملف لكن تعذر الحفظ");
        showGoogleSyncCenter();
    }

    private boolean saveBackupToChosenUri(boolean showError) {
        String saved = db.getSetting("phone_backup_uri", "");
        if (saved == null || saved.trim().isEmpty()) return false;
        OutputStream out = null;
        try {
            Uri uri = Uri.parse(saved);
            out = getContentResolver().openOutputStream(uri, "wt");
            if (out == null) return false;
            String json = db.exportBackupJson();
            out.write(json.getBytes("UTF-8"));
            out.flush();
            db.setSetting("last_local_backup", String.valueOf(System.currentTimeMillis()));
            return true;
        } catch (Exception e) {
            if (showError) new AlertDialog.Builder(this).setTitle("تعذر حفظ النسخة").setMessage(e.getMessage()).setPositiveButton("تمام", null).show();
            return false;
        } finally {
            try { if (out != null) out.close(); } catch (Exception ignored) {}
        }
    }

    private void importBackupFromUri(Uri uri, int intentFlags) {
        if (uri == null) { toast("لم يتم اختيار ملف"); return; }
        try {
            try {
                int flags = intentFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
                if (flags != 0) getContentResolver().takePersistableUriPermission(uri, flags);
            } catch (Exception ignored) {}
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) { toast("تعذر فتح الملف"); return; }
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            db.importBackupJson(sb.toString());
            db.saveLocalBackup();
            toast("تم استرجاع الداتا من الملف");
            showHome();
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("تعذر الاستيراد").setMessage(e.getMessage()).setPositiveButton("تمام", null).show();
        }
    }

    private void autoCloudBackup() {
        try { db.saveLocalBackup(); } catch (Exception ignored) {}
        try { saveBackupToChosenUri(false); } catch (Exception ignored) {}
        if (!"1".equals(db.getSetting("google_auto_backup", "1"))) return;
        long now = System.currentTimeMillis();
        if (now - lastAutoBackupAttempt < 30000L) return;
        lastAutoBackupAttempt = now;
        try {
            if (syncManager.currentUser() == null) return;
            syncManager.uploadBackup(new FirebaseSyncManager.Callback() {
                @Override public void ok(String message) { }
                @Override public void fail(String message) { }
            });
        } catch (Exception ignored) { }
    }

    private void testMessageDialog() {
        bankMessageTrainerDialog();
    }

    private void bankMessageTrainerDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(14), dp(8), dp(14), dp(8));

        TextView hint = text("الصق رسالة بنك جديدة، واختار هل هي خصم أو إضافة. بعد كده أي رسالة بنفس الشكل هتتسجل تلقائيًا.", 13, false, MUTED);
        box.addView(hint, matchWrap());
        box.addView(pill("القواعد المحفوظة: " + db.learnedBankRuleCount(), PURPLE), matchWrap());

        final EditText input = new EditText(this);
        input.setMinLines(7);
        input.setGravity(Gravity.RIGHT);
        input.setTextDirection(View.TEXT_DIRECTION_RTL);
        input.setHint("الصق رسالة البنك هنا");
        box.addView(input, matchWrap());

        final String[] selectedCategory = new String[]{"عام"};
        Button category = softBtn("الفئة: " + selectedCategory[0], PURPLE);
        category.setOnClickListener(v -> categoryPickerDialog(selectedCategory[0], cat -> {
            selectedCategory[0] = cat;
            category.setText("الفئة: " + cat);
        }));
        box.addView(category);

        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("تعليم رسائل البنك").setView(box).setNegativeButton("إغلاق", null).create();

        Button analyze = btn("تحليل وحفظ مرة واحدة");
        analyze.setOnClickListener(v -> {
            String raw = input.getText().toString();
            if (raw.trim().isEmpty()) { toast("الصق الرسالة الأول"); return; }
            MessageParser.ParsedTransaction tx = db.parseBankMessageSmart(raw);
            if (tx == null) { toast("مش قادر أفهم الرسالة، علمهالي كخصم أو إضافة"); return; }
            long id = db.insertParsed(tx);
            if (id == -1 || id == -2) toast("العملية مكررة وتم تجاهلها"); else toast("تم حفظ: " + tx.title);
            autoCloudBackup();
            dialog.dismiss();
            showHome();
        });
        box.addView(analyze);

        LinearLayout grid1 = row();
        Button exp = softBtn("تعليمها كخصم", ORANGE);
        exp.setOnClickListener(v -> learnBankRuleAndSave(input.getText().toString(), "EXPENSE", selectedCategory[0], dialog));
        Button inc = softBtn("تعليمها كإضافة", PRIMARY);
        inc.setOnClickListener(v -> learnBankRuleAndSave(input.getText().toString(), "INCOME", selectedCategory[0], dialog));
        grid1.addView(exp, new LinearLayout.LayoutParams(0, dp(54), 1));
        grid1.addView(inc, new LinearLayout.LayoutParams(0, dp(54), 1));
        box.addView(grid1, matchWrap());

        LinearLayout grid2 = row();
        Button online = softBtn("أونلاين للمراجعة", BLUE);
        online.setOnClickListener(v -> learnBankRuleAndSave(input.getText().toString(), "ONLINE", selectedCategory[0], dialog));
        Button saved = softBtn("حفظ فقط", MUTED);
        saved.setOnClickListener(v -> learnBankRuleAndSave(input.getText().toString(), "SAVE_ONLY", selectedCategory[0], dialog));
        grid2.addView(online, new LinearLayout.LayoutParams(0, dp(54), 1));
        grid2.addView(saved, new LinearLayout.LayoutParams(0, dp(54), 1));
        box.addView(grid2, matchWrap());

        Button clear = softBtn("مسح كل قواعد رسائل البنك المتعلمة", RED);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("تأكيد المسح")
                .setMessage("هيتم مسح القواعد المتعلمة فقط، مش السجل.")
                .setPositiveButton("مسح", (d, w) -> { db.clearLearnedBankRules(); toast("تم مسح القواعد"); dialog.dismiss(); })
                .setNegativeButton("إلغاء", null).show());
        box.addView(clear);

        dialog.show();
    }

    private void learnBankRuleAndSave(String raw, String kind, String category, AlertDialog dialog) {
        if (raw == null || raw.trim().isEmpty()) { toast("الصق رسالة البنك الأول"); return; }
        db.learnBankMessage(raw, kind, category);
        MessageParser.ParsedTransaction tx = db.parseBankMessageSmart(raw);
        long id = -1;
        if (tx != null) id = db.insertParsed(tx);
        autoCloudBackup();
        String kindName = "EXPENSE".equals(kind) ? "خصم" : ("INCOME".equals(kind) ? "إضافة" : ("ONLINE".equals(kind) ? "أونلاين للمراجعة" : "حفظ فقط"));
        if (tx == null) toast("اتعلم شكل الرسالة كـ " + kindName + "، لكن لم يتم حفظ العملية لعدم وجود مبلغ واضح");
        else if (id == -1 || id == -2) toast("اتعلم شكل الرسالة كـ " + kindName + "، والعملية الحالية مكررة");
        else toast("اتعلم شكل الرسالة واتحفظت كـ " + kindName);
        if (dialog != null) dialog.dismiss();
        showHome();
    }

    private void showPending() {
        setup("مراجعة العمليات"); addHomeButton();
        LinearLayout intro = card(pale(ORANGE));
        intro.setBackground(strokeBg(pale(ORANGE), lighten(ORANGE), 22, 1));
        intro.addView(text("الأونلاين والوارد مش بيتحسبوا غير بعد موافقتك", 16, true, ORANGE), matchWrap());
        intro.addView(text("اختار خصم، حفظ فقط، دخل إضافي، أو سداد دين حسب كل عملية.", 12, false, DARK), matchWrap());
        root.addView(intro);

        List<ExpenseDbHelper.Tx> list = db.getPending();
        if (list.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("✅ مفيش عمليات معلقة حاليًا", 18, true, PRIMARY), matchWrap());
            empty.addView(text("أي شراء أونلاين أو حوالة واردة هتظهر هنا للمراجعة.", 13, false, MUTED), matchWrap());
            root.addView(empty);
        }
        for (ExpenseDbHelper.Tx tx : list) {
            LinearLayout c = card();
            LinearLayout top = row();
            top.addView(pill(statusArabic(tx.status), statusColor(tx.status)));
            Space sp = new Space(this); top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));
            top.addView(text(money(tx.amount), 18, true, DARK));
            c.addView(top, matchWrap());
            c.addView(text(tx.title, 20, true, DARK), matchWrap());
            c.addView(text("التاريخ: " + ExpenseDbHelper.date(tx.dateMillis), 13, false, MUTED), matchWrap());
            if (tx.card != null && tx.card.length() > 0) c.addView(text("البطاقة / الحساب: " + tx.card, 13, false, MUTED), matchWrap());
            if (tx.extra != null && tx.extra.length() > 0) c.addView(text(tx.extra, 13, false, MUTED), matchWrap());
            if ("PENDING_ONLINE".equals(tx.status)) {
                Button approve = btn("خصم من الميزانية"); approve.setOnClickListener(v -> { db.approveOnline(tx.id); toast("تم الخصم"); showPending(); }); c.addView(approve);
                Button edit = softBtn("تعديل المبلغ ثم خصم", BLUE); edit.setOnClickListener(v -> editAmountThenApprove(tx)); c.addView(edit);
                Button save = softBtn("تجاهل / حفظ فقط", MUTED); save.setOnClickListener(v -> { db.saveOnly(tx.id); showPending(); }); c.addView(save);
            } else if ("PENDING_INCOMING".equals(tx.status)) {
                Button income = btn("تسجيل كدخل إضافي منفصل"); income.setOnClickListener(v -> { db.markExtraIncome(tx.id); toast("اتسجل دخل إضافي"); showPending(); }); c.addView(income);
                Button debt = softBtn("تسجيل كسداد دين", PURPLE); debt.setOnClickListener(v -> chooseDebtForPayment(tx)); c.addView(debt);
                Button save = softBtn("تجاهل / حفظ فقط", MUTED); save.setOnClickListener(v -> { db.saveOnly(tx.id); showPending(); }); c.addView(save);
            } else if ("PENDING_REVIEW".equals(tx.status)) {
                Button approve = btn("اعتبارها مصروف وخصمها"); approve.setOnClickListener(v -> { db.approveOnline(tx.id); toast("تم الخصم"); showPending(); }); c.addView(approve);
                Button edit = softBtn("تعديل المبلغ ثم خصم", BLUE); edit.setOnClickListener(v -> editAmountThenApprove(tx)); c.addView(edit);
                Button save = softBtn("حفظ فقط بدون خصم", MUTED); save.setOnClickListener(v -> { db.saveOnly(tx.id); showPending(); }); c.addView(save);
            }
            root.addView(c);
        }
    }

    private void editAmountThenApprove(ExpenseDbHelper.Tx tx) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.valueOf(tx.amount));
        input.setGravity(Gravity.RIGHT);
        new AlertDialog.Builder(this).setTitle("تعديل المبلغ")
                .setView(input)
                .setPositiveButton("خصم", (d, w) -> {
                    double a = parseAmount(input.getText().toString());
                    if (a > 0) { db.updateTransactionAmount(tx.id, a); db.approveOnline(tx.id); showPending(); }
                }).setNegativeButton("إلغاء", null).show();
    }

    private void chooseDebtForPayment(ExpenseDbHelper.Tx tx) {
        List<ExpenseDbHelper.Debt> debts = db.getDebtsByDirection("OWED_TO_ME");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(6), dp(10), dp(6));
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("اختار الشخص اللي سدد").setView(box).setNegativeButton("إلغاء", null).create();
        for (ExpenseDbHelper.Debt d : debts) {
            if ("PAID".equals(d.status)) continue;
            Button b = softBtn(d.name + " - المتبقي " + debtMoney(d, d.amount - d.paid), PURPLE);
            b.setOnClickListener(v -> {
                db.addDebtPayment(d.id, tx.amount, "سداد من حوالة واردة", tx.id);
                db.markDebtPayment(tx.id);
                toast("تم تسجيل السداد");
                dialog.dismiss();
                showPending();
            });
            box.addView(b);
        }
        Button addNew = btn("إضافة شخص جديد بهذا المبلغ");
        addNew.setOnClickListener(v -> { dialog.dismiss(); addDebtDialog(tx.merchant, tx.amount); });
        box.addView(addNew);
        dialog.show();
    }

    private void showDebts() {
        setup("الديون والمواعيد"); addHomeButton();
        double owedToMe = db.getDebtTotal("OWED_TO_ME");
        double oweToOthers = db.getDebtTotal("OWE_TO_OTHERS");

        LinearLayout hero = card();
        hero.setBackground(gradient(PURPLE, Color.rgb(92, 74, 168), 24));
        hero.addView(text("متابعة اللي ليك واللي عليك", 14, false, Color.rgb(235, 231, 255)), matchWrap());
        hero.addView(text("ليك: " + debtSummary("OWED_TO_ME"), 25, true, Color.WHITE), matchWrap());
        hero.addView(text("عليك: " + debtSummary("OWE_TO_OTHERS"), 21, true, Color.rgb(255, 236, 236)), matchWrap());
        hero.addView(text("حدد تاريخ ووقت للسداد أو التحصيل وهيجيلك إشعار بالاسم والمبلغ", 12, false, Color.rgb(235, 231, 255)), matchWrap());
        root.addView(hero);

        LinearLayout actions = row();
        addWeighted(actions, actionCard("📥", "فلوس عند الناس", "أضف شخص هتاخد منه", PURPLE, v -> addDebtDialog("", 0, "OWED_TO_ME")), 1, 4);
        addWeighted(actions, actionCard("📤", "ناس ليها عندي", "أضف شخص هتسدده", RED, v -> addDebtDialog("", 0, "OWE_TO_OTHERS")), 1, 4);
        root.addView(actions, matchWrap());

        Button pay = softBtn("تسجيل دفعة / سداد", PRIMARY); pay.setOnClickListener(v -> manualDebtPaymentDialog()); root.addView(pay);
        Button settings = softBtn("إعدادات تذكيرات الديون", ORANGE); settings.setOnClickListener(v -> showSecurityAndReminderSettings()); root.addView(settings);

        addDebtSection("📥 فلوس ليا عند الناس", "OWED_TO_ME", "هنا الأشخاص اللي أنت مستني تاخد منهم فلوس");
        addDebtSection("📤 ناس ليها فلوس عندي", "OWE_TO_OTHERS", "هنا الأشخاص اللي عليك تسدد لهم فلوس");
    }

    private void addDebtSection(String title, String direction, String emptyMsg) {
        List<ExpenseDbHelper.Debt> list = db.getDebtsByDirection(direction);
        LinearLayout section = card(Color.WHITE);
        section.setBackground(strokeBg(Color.WHITE, Color.rgb(224, 235, 231), 22, 1));
        section.addView(text(title, 18, true, DARK), matchWrap());
        section.addView(text(emptyMsg, 12, false, MUTED), matchWrap());
        if (list.isEmpty()) {
            section.addView(text("لا يوجد بيانات في هذا القسم", 13, false, MUTED), matchWrap());
            root.addView(section);
            return;
        }
        root.addView(section);
        for (ExpenseDbHelper.Debt d : list) addDebtCard(d);
    }

    private void addDebtCard(ExpenseDbHelper.Debt d) {
        LinearLayout c = card();
        double remaining = Math.max(0, d.amount - d.paid);
        boolean iOwe = "OWE_TO_OTHERS".equals(d.direction);
        int mainColor = iOwe ? RED : PURPLE;
        LinearLayout top = row();
        TextView avatar = text(initials(d.name), 16, true, Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(bg(mainColor, 50));
        LinearLayout.LayoutParams avlp = new LinearLayout.LayoutParams(dp(42), dp(42));
        avlp.setMargins(dp(6), dp(2), 0, dp(2));
        top.addView(avatar, avlp);
        LinearLayout names = new LinearLayout(this); names.setOrientation(LinearLayout.VERTICAL); names.setGravity(Gravity.RIGHT);
        names.addView(text(d.name, 19, true, DARK), matchWrap());
        names.addView(text((iOwe ? "متبقي عليك: " : "متبقي ليك: ") + debtMoney(d, remaining), 13, false, MUTED), matchWrap());
        top.addView(names, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        top.addView(pill(debtStatusArabic(d.status), debtStatusColor(d.status)));
        c.addView(top, matchWrap());
        DebtProgressView dpv = new DebtProgressView(this, d.amount <= 0 ? 0 : d.paid / d.amount, pale(debtStatusColor(d.status)), debtStatusColor(d.status));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(9));
        dlp.setMargins(0, dp(10), 0, dp(6)); c.addView(dpv, dlp);
        c.addView(text("الأصل: " + debtMoney(d, d.amount) + " | المدفوع: " + debtMoney(d, d.paid), 13, false, MUTED), matchWrap());
        c.addView(text(debtDueText(d), 13, true, d.dueDateMillis > 0 && d.dueDateMillis < System.currentTimeMillis() && !"PAID".equals(d.status) ? RED : MUTED), matchWrap());
        if (d.notes != null && d.notes.length() > 0) c.addView(text("ملاحظات: " + d.notes, 13, false, MUTED), matchWrap());
        if (d.whatsapp != null && d.whatsapp.trim().length() > 0) {
            Button w = softBtn("فتح واتساب", PRIMARY); w.setOnClickListener(v -> openUrl("https://wa.me/" + cleanPhone(d.whatsapp))); c.addView(w);
            Button msg = softBtn("رسالة واتساب جاهزة بالمبلغ", PRIMARY_DARK); msg.setOnClickListener(v -> openDebtWhatsappMessage(d)); c.addView(msg);
        }
        if (d.facebook != null && d.facebook.trim().length() > 0) {
            Button f = softBtn("فتح فيسبوك", BLUE); f.setOnClickListener(v -> openUrl(d.facebook)); c.addView(f);
        }
        Button payment = softBtn(iOwe ? "سجل إنك سددت جزء" : "سجل إنه دفع جزء", ORANGE);
        payment.setOnClickListener(v -> debtPaymentAmountDialog(d));
        c.addView(payment);
        Button reminder = softBtn("تعديل ميعاد التذكير", mainColor);
        reminder.setOnClickListener(v -> debtDueDateDialog(d));
        c.addView(reminder);
        root.addView(c);
    }

    private String debtMoney(ExpenseDbHelper.Debt d, double amount) {
        return moneyCurrency(amount, d.currency == null || d.currency.trim().isEmpty() ? db.getCurrency() : d.currency);
    }

    private String debtDueText(ExpenseDbHelper.Debt d) {
        if (d.dueDateMillis <= 0) return "لا يوجد ميعاد تذكير";
        String prefix = "OWE_TO_OTHERS".equals(d.direction) ? "ميعاد السداد: " : "ميعاد التحصيل: ";
        long now = System.currentTimeMillis();
        String tail = d.dueDateMillis < now && !"PAID".equals(d.status) ? " — متأخر" : "";
        return prefix + ExpenseDbHelper.date(d.dueDateMillis) + tail;
    }

    private void addDebtDialog(String defaultName, double defaultAmount) { addDebtDialog(defaultName, defaultAmount, "OWED_TO_ME"); }

    private void addDebtDialog(String defaultName, double defaultAmount, String direction) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(5), dp(20), dp(5));
        EditText name = field("الاسم", defaultName);
        EditText amount = field("المبلغ", defaultAmount > 0 ? String.valueOf(defaultAmount) : ""); amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final String[] selectedCurrency = new String[]{db.getCurrency()};
        Button currency = softBtn("العملة: " + safeCurrency(selectedCurrency[0]), PRIMARY_DARK);
        currency.setOnClickListener(v -> {
            String[] items = new String[]{"ريال سعودي", "جنيه مصري"};
            int checked = "EGP".equals(selectedCurrency[0]) ? 1 : 0;
            new AlertDialog.Builder(this).setTitle("اختار العملة").setSingleChoiceItems(items, checked, (dd, which) -> {
                selectedCurrency[0] = which == 1 ? "EGP" : "SAR";
                currency.setText("العملة: " + safeCurrency(selectedCurrency[0]));
                dd.dismiss();
            }).setNegativeButton("إلغاء", null).show();
        });
        EditText whatsapp = field("رقم واتساب اختياري", "");
        EditText facebook = field("رابط فيسبوك اختياري", "");
        EditText due = field("ميعاد التذكير اختياري: 27/06/2026 20:30", "");
        EditText notes = field("ملاحظات", "");
        box.addView(name); box.addView(amount); box.addView(currency); box.addView(whatsapp); box.addView(facebook); box.addView(due); box.addView(notes);
        String title = "OWE_TO_OTHERS".equals(direction) ? "إضافة شخص ليه فلوس عندي" : "إضافة شخص ليا عنده فلوس";
        new AlertDialog.Builder(this).setTitle(title).setView(box)
                .setPositiveButton("حفظ", (d, w) -> {
                    double a = parseAmount(amount.getText().toString());
                    long dueMs = parseDateTime(due.getText().toString());
                    if (a > 0 && name.getText().toString().trim().length() > 0) {
                        long id = db.addDebt(name.getText().toString().trim(), a, whatsapp.getText().toString(), facebook.getText().toString(), notes.getText().toString(), direction, dueMs, selectedCurrency[0]);
                        if (dueMs > 0) scheduleDebtReminder(id, name.getText().toString().trim(), a, direction, dueMs);
                        toast("تم الحفظ" + (dueMs > 0 ? " وتم ضبط التذكير" : "")); showDebts();
                    }
                }).setNegativeButton("إلغاء", null).show();
    }

    private void debtDueDateDialog(ExpenseDbHelper.Debt debt) {
        EditText due = new EditText(this);
        due.setHint("27/06/2026 20:30");
        due.setText(debt.dueDateMillis > 0 ? ExpenseDbHelper.date(debt.dueDateMillis) : "");
        due.setGravity(Gravity.RIGHT); due.setTextDirection(View.TEXT_DIRECTION_RTL);
        new AlertDialog.Builder(this).setTitle("تعديل ميعاد " + debt.name).setView(due)
                .setPositiveButton("حفظ", (d,w) -> {
                    long dueMs = parseDateTime(due.getText().toString());
                    db.updateDebtDueDate(debt.id, dueMs);
                    if (dueMs > 0) scheduleDebtReminder(debt.id, debt.name, debt.amount - debt.paid, debt.direction, dueMs);
                    toast("تم تحديث الميعاد"); showDebts();
                }).setNegativeButton("إلغاء", null).show();
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "؟";
        String[] parts = name.trim().split("\\s+");
        String s = parts[0].substring(0, 1);
        if (parts.length > 1) s += parts[1].substring(0, 1);
        return s;
    }

    private void manualDebtPaymentDialog() {
        List<ExpenseDbHelper.Debt> debts = db.getDebts();
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(10), dp(6), dp(10), dp(6));
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("اختار الشخص").setView(box).setNegativeButton("إلغاء", null).create();
        for (ExpenseDbHelper.Debt debt : debts) {
            if ("PAID".equals(debt.status)) continue;
            Button b = softBtn(debt.name + " - المتبقي " + money(debt.amount - debt.paid), PURPLE);
            b.setOnClickListener(v -> { dialog.dismiss(); debtPaymentAmountDialog(debt); });
            box.addView(b);
        }
        dialog.show();
    }

    private void debtPaymentAmountDialog(ExpenseDbHelper.Debt debt) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("المبلغ المدفوع");
        input.setGravity(Gravity.RIGHT);
        new AlertDialog.Builder(this).setTitle("دفعة من " + debt.name).setView(input)
                .setPositiveButton("حفظ", (d, w) -> {
                    double a = parseAmount(input.getText().toString());
                    if (a > 0) { db.addDebtPayment(debt.id, a, "دفعة يدوية", -1); showDebts(); }
                }).setNegativeButton("إلغاء", null).show();
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setText(value); e.setGravity(Gravity.RIGHT); e.setTextDirection(View.TEXT_DIRECTION_RTL);
        e.setSingleLine(false);
        return e;
    }

    private void showLog() { showLog("ALL", "ALL"); }

    private void showLog(String currencyFilter, String categoryFilter) {
        setup("سجل العمليات"); addHomeButton();

        List<ExpenseDbHelper.Tx> txs = db.getRecentFiltered("ALL", "ALL", 250);
        if (txs.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("مفيش عمليات مسجلة", 18, true, DARK), matchWrap());
            empty.addView(text("أي عملية تضيفها هتظهر هنا ومعاها العملة والتصنيف.", 13, false, MUTED), matchWrap());
            root.addView(empty);
        }
        for (ExpenseDbHelper.Tx tx : txs) {
            LinearLayout c = card();
            c.setPadding(dp(12), dp(10), dp(12), dp(10));
            c.setClickable(true);
            c.setOnClickListener(v -> editTransactionDialog(tx));
            LinearLayout top = row();
            top.addView(text(tx.title == null || tx.title.length() == 0 ? "عملية" : tx.title, 14, true, DARK), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            top.addView(text(txMoney(tx), 15, true, tx.affectsBudget == 1 ? DARK : PRIMARY_DARK));
            c.addView(top, matchWrap());
            String meta = (tx.category == null ? "عام" : tx.category) + " • " + safeCurrency(tx.currency) + " • " + ExpenseDbHelper.date(tx.dateMillis);
            c.addView(text(meta, 11, false, MUTED), matchWrap());
            root.addView(c);
        }
    }

    private void categoryFilterDialog(String currencyFilter) {
        List<String> cats = db.getCategories();
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(10), dp(6), dp(10), dp(6));
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("اختار التصنيف").setView(box).setNegativeButton("إلغاء", null).create();
        Button all = softBtn("كل التصنيفات", MUTED); all.setOnClickListener(v -> { dialog.dismiss(); showLog(currencyFilter, "ALL"); }); box.addView(all);
        for (String cat : cats) {
            Button b = softBtn(cat, BLUE);
            b.setOnClickListener(v -> { dialog.dismiss(); showLog(currencyFilter, cat); });
            box.addView(b);
        }
        dialog.show();
    }

    private String safeCurrency(String c) {
        if ("EGP".equalsIgnoreCase(c)) return "جنيه مصري";
        if ("SAR".equalsIgnoreCase(c)) return "ريال سعودي";
        return c == null || c.length() == 0 ? db.currencyName() : c;
    }

    private String txMoney(ExpenseDbHelper.Tx tx) {
        String cur = tx.currency == null ? db.getCurrency() : tx.currency;
        return moneyCurrency(tx.amount, cur);
    }


    private interface CategoryPickCallback { void onPick(String category); }

    private void categoryPickerDialog(String current, CategoryPickCallback callback) {
        List<String> cats = db.getAllCategoryNames();
        if (cats.isEmpty()) cats = db.getCategories();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(10), dp(6), dp(10), dp(6));
        ScrollView sc = new ScrollView(this);
        sc.addView(box, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("اختار الفئة").setView(sc).setNegativeButton("إلغاء", null).create();
        for (String cat : cats) {
            Button b = softBtn((cat.equals(current) ? "✓ " : "") + cat, cat.equals(current) ? PRIMARY : BLUE);
            b.setOnClickListener(v -> { dialog.dismiss(); callback.onPick(cat); });
            box.addView(b);
        }
        dialog.show();
    }

    private void editTransactionDialog(ExpenseDbHelper.Tx tx) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(5), dp(20), dp(5));

        EditText title = field("اسم العملية", tx.title == null ? "" : tx.title);
        EditText amount = field("المبلغ", String.valueOf(tx.amount));
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        final String[] selectedCategory = new String[]{tx.category == null || tx.category.trim().isEmpty() ? "عام" : tx.category};
        Button category = softBtn("التصنيف: " + selectedCategory[0], PURPLE);
        category.setOnClickListener(v -> categoryPickerDialog(selectedCategory[0], cat -> {
            selectedCategory[0] = cat;
            category.setText("التصنيف: " + cat);
        }));
        CheckBox affects = new CheckBox(this);
        affects.setText("تؤثر على الميزانية الشهرية");
        affects.setTextSize(14);
        affects.setTextColor(DARK);
        affects.setGravity(Gravity.RIGHT);
        affects.setTextDirection(View.TEXT_DIRECTION_RTL);
        affects.setChecked(tx.affectsBudget == 1);

        box.addView(label("اسم العملية", 13, true));
        box.addView(title);
        box.addView(label("المبلغ", 13, true));
        box.addView(amount);
        box.addView(label("التصنيف", 13, true));
        box.addView(category);
        box.addView(affects);

        new AlertDialog.Builder(this)
                .setTitle("تعديل العملية")
                .setView(box)
                .setPositiveButton("حفظ", (d, w) -> {
                    double a = parseAmount(amount.getText().toString());
                    db.updateTransaction(tx.id, a, title.getText().toString(), selectedCategory[0], affects.isChecked() ? 1 : 0);
                    autoCloudBackup();
                    toast("تم تعديل العملية");
                    showLog();
                })
                .setNeutralButton("حذف", (d, w) -> {
                    db.deleteTransaction(tx.id);
                    autoCloudBackup();
                    toast("تم حذف العملية");
                    showLog();
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }


    private void showSecurityAndReminderSettings() {
        setup("إعدادات التذكير والقفل"); addHomeButton();
        LinearLayout info = card();
        info.addView(text("تذكيرات الديون", 20, true, DARK), matchWrap());
        info.addView(text("اختار هل يوصلك تنبيه قبل الميعاد بيوم، قبل الميعاد بساعتين، وهل يتكرر لو السداد/التحصيل اتأخر.", 13, false, MUTED), matchWrap());
        CheckBox beforeDay = new CheckBox(this); beforeDay.setText("تذكير قبل الميعاد بيوم"); beforeDay.setChecked("1".equals(db.getSetting("debt_remind_day", "1"))); beforeDay.setGravity(Gravity.RIGHT); beforeDay.setTextDirection(View.TEXT_DIRECTION_RTL);
        CheckBox before2h = new CheckBox(this); before2h.setText("تذكير قبل الميعاد بساعتين"); before2h.setChecked("1".equals(db.getSetting("debt_remind_2h", "1"))); before2h.setGravity(Gravity.RIGHT); before2h.setTextDirection(View.TEXT_DIRECTION_RTL);
        CheckBox repeat = new CheckBox(this); repeat.setText("تكرار التذكير لو متأخر"); repeat.setChecked("1".equals(db.getSetting("debt_repeat_overdue", "1"))); repeat.setGravity(Gravity.RIGHT); repeat.setTextDirection(View.TEXT_DIRECTION_RTL);
        CheckBox sub3 = new CheckBox(this); sub3.setText("تذكير الاشتراك قبل الخصم بـ 3 أيام"); sub3.setChecked("1".equals(db.getSetting("subscription_remind_3d", "1"))); sub3.setGravity(Gravity.RIGHT); sub3.setTextDirection(View.TEXT_DIRECTION_RTL);
        CheckBox sub1 = new CheckBox(this); sub1.setText("تذكير الاشتراك قبل الخصم بيوم"); sub1.setChecked("1".equals(db.getSetting("subscription_remind_1d", "1"))); sub1.setGravity(Gravity.RIGHT); sub1.setTextDirection(View.TEXT_DIRECTION_RTL);
        EditText abnormal = field("حد المصروف الكبير غير المعتاد - 0 تلقائي", db.getSetting("abnormal_expense_threshold", "0"));
        abnormal.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        info.addView(beforeDay); info.addView(before2h); info.addView(repeat); info.addView(sub3); info.addView(sub1); info.addView(label("تنبيه المصاريف غير الطبيعية", 13, true)); info.addView(abnormal);
        Button saveRem = btn("حفظ إعدادات التذكير");
        saveRem.setOnClickListener(v -> {
            db.setSetting("debt_remind_day", beforeDay.isChecked() ? "1" : "0");
            db.setSetting("debt_remind_2h", before2h.isChecked() ? "1" : "0");
            db.setSetting("debt_repeat_overdue", repeat.isChecked() ? "1" : "0");
            db.setSetting("subscription_remind_3d", sub3.isChecked() ? "1" : "0");
            db.setSetting("subscription_remind_1d", sub1.isChecked() ? "1" : "0");
            db.setSetting("abnormal_expense_threshold", String.valueOf(parseAmount(abnormal.getText().toString())));
            for (ExpenseDbHelper.Debt d : db.getDebts()) {
                if (!"PAID".equals(d.status) && d.dueDateMillis > 0) scheduleDebtReminder(d.id, d.name, Math.max(0, d.amount - d.paid), d.direction, d.dueDateMillis);
            }
            for (ExpenseDbHelper.Subscription sub : db.getSubscriptions()) if (sub.active == 1 && sub.nextDateMillis > 0) scheduleSubscriptionReminder(sub.id, sub.name, sub.amount, sub.currency, sub.nextDateMillis);
            toast("تم حفظ إعدادات التذكير وإعادة ضبط المواعيد");
            autoCloudBackup();
        });
        info.addView(saveRem);
        root.addView(info);

        LinearLayout lock = card();
        lock.addView(text("قفل التطبيق", 20, true, DARK), matchWrap());
        lock.addView(text("لو فعلته، التطبيق يطلب PIN عند الفتح. وتقدر تفتحه كمان ببصمة/قفل الجهاز لو الجهاز بيدعم ده.", 13, false, MUTED), matchWrap());
        CheckBox privacy = new CheckBox(this); privacy.setText("تفعيل وضع الخصوصية وإخفاء الأرقام"); privacy.setChecked("1".equals(db.getSetting("privacy_mode", "0"))); privacy.setGravity(Gravity.RIGHT); privacy.setTextDirection(View.TEXT_DIRECTION_RTL);
        CheckBox enabled = new CheckBox(this); enabled.setText("تفعيل قفل التطبيق"); enabled.setChecked("1".equals(db.getSetting("app_lock_enabled", "0"))); enabled.setGravity(Gravity.RIGHT); enabled.setTextDirection(View.TEXT_DIRECTION_RTL);
        EditText pin = field("PIN جديد 4 أرقام أو أكثر", db.getSetting("app_lock_pin", ""));
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        lock.addView(privacy); lock.addView(enabled); lock.addView(pin);
        Button saveLock = btn("حفظ القفل");
        saveLock.setOnClickListener(v -> {
            String p = pin.getText().toString().trim();
            if (enabled.isChecked() && p.length() < 4) { toast("اكتب PIN من 4 أرقام على الأقل"); return; }
            db.setSetting("privacy_mode", privacy.isChecked() ? "1" : "0");
            db.setSetting("app_lock_enabled", enabled.isChecked() ? "1" : "0");
            db.setSetting("app_lock_pin", p);
            appUnlocked = !enabled.isChecked();
            toast(enabled.isChecked() ? "تم حفظ القفل والخصوصية" : "تم حفظ الإعدادات");
            autoCloudBackup();
            showHome();
        });
        lock.addView(saveLock);
        Button testDevice = softBtn("اختبار فتح ببصمة / قفل الجهاز", PRIMARY_DARK);
        testDevice.setOnClickListener(v -> openDeviceCredential());
        lock.addView(testDevice);
        root.addView(lock);
    }

    private void maybeShowAppLock() {
        if (appUnlocked || lockDialogShowing) return;
        if (!"1".equals(db.getSetting("app_lock_enabled", "0"))) return;
        lockDialogShowing = true;
        final EditText pin = new EditText(this);
        pin.setHint("اكتب PIN");
        pin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin.setGravity(Gravity.CENTER);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("مصروفاتي مقفول")
                .setMessage("افتح التطبيق بالـ PIN أو ببصمة/قفل الجهاز")
                .setView(pin)
                .setPositiveButton("فتح", null)
                .setNeutralButton("بصمة / قفل الجهاز", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (pin.getText().toString().trim().equals(db.getSetting("app_lock_pin", ""))) {
                    appUnlocked = true; lockDialogShowing = false; dialog.dismiss(); toast("تم فتح القفل");
                } else toast("PIN غير صحيح");
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                awaitingDeviceCredential = true;
                dialog.dismiss();
                openDeviceCredential();
            });
        });
        dialog.setOnDismissListener(d -> lockDialogShowing = false);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private void openDeviceCredential() {
        if (Build.VERSION.SDK_INT < 21) { toast("جهازك لا يدعم فتح القفل من هنا"); return; }
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km == null || !km.isKeyguardSecure()) { toast("فعل بصمة أو PIN للجهاز الأول"); return; }
        Intent intent = km.createConfirmDeviceCredentialIntent("مصروفاتي", "افتح التطبيق بقفل الجهاز");
        if (intent != null) { awaitingDeviceCredential = true; startActivityForResult(intent, REQ_DEVICE_LOCK); }
    }

    private void openDebtWhatsappMessage(ExpenseDbHelper.Debt d) {
        if (d.whatsapp == null || d.whatsapp.trim().isEmpty()) { toast("مفيش رقم واتساب"); return; }
        double remaining = Math.max(0, d.amount - d.paid);
        boolean iOwe = "OWE_TO_OTHERS".equals(d.direction);
        String message = iOwe
                ? "السلام عليكم، للتذكير عليا ليك مبلغ " + money(remaining) + " وهسدده في أقرب وقت."
                : "السلام عليكم، للتذكير ليا عندك مبلغ " + money(remaining) + " يا ريت تراجعني في ميعاد السداد. شكرًا";
        openUrl("https://wa.me/" + cleanPhone(d.whatsapp) + "?text=" + Uri.encode(message));
    }

    private void showSubscriptions() {
        setup("الاشتراكات الشهرية"); addHomeButton();
        LinearLayout hero = card();
        hero.setBackground(gradient(BLUE, Color.rgb(36, 84, 180), 24));
        hero.addView(text("تابع الاشتراكات المتكررة", 14, false, Color.rgb(230, 238, 255)), matchWrap());
        hero.addView(text("الإجمالي النشط: " + money(db.getActiveSubscriptionsTotal()), 24, true, Color.WHITE), matchWrap());
        hero.addView(text("أمثلة: Google، Netflix، Apple، شاهد، برامج شهرية", 12, false, Color.rgb(230, 238, 255)), matchWrap());
        root.addView(hero);
        Button add = btn("إضافة اشتراك شهري"); add.setOnClickListener(v -> addSubscriptionDialog()); root.addView(add);

        List<ExpenseDbHelper.Subscription> list = db.getSubscriptions();
        if (list.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("لسه مفيش اشتراكات", 18, true, DARK), matchWrap());
            empty.addView(text("ضيف Google أو Netflix أو أي اشتراك شهري عشان يظهرلك ميعاده ويتسجل كمصروف.", 13, false, MUTED), matchWrap());
            root.addView(empty);
        }
        for (ExpenseDbHelper.Subscription sub : list) addSubscriptionCard(sub);
    }

    private void addSubscriptionCard(ExpenseDbHelper.Subscription sub) {
        LinearLayout c = card();
        LinearLayout top = row();
        top.addView(pill(sub.active == 1 ? "نشط" : "متوقف", sub.active == 1 ? PRIMARY : MUTED));
        Space sp = new Space(this); top.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));
        String sym = "EGP".equalsIgnoreCase(sub.currency) ? "ج.م" : "ر.س";
        top.addView(text(String.format(Locale.US, "%.2f %s", sub.amount, sym), 17, true, DARK));
        c.addView(top, matchWrap());
        c.addView(text(sub.name, 19, true, DARK), matchWrap());
        c.addView(text("التصنيف: " + sub.category + " | القادم: " + (sub.nextDateMillis > 0 ? ExpenseDbHelper.date(sub.nextDateMillis) : "غير محدد"), 13, false, MUTED), matchWrap());
        if (sub.notes != null && sub.notes.length() > 0) c.addView(text("ملاحظات: " + sub.notes, 12, false, MUTED), matchWrap());
        Button charge = softBtn("سجل خصم هذا الشهر", PRIMARY);
        charge.setOnClickListener(v -> { db.chargeSubscription(sub.id); ExpenseDbHelper.Subscription updated = db.getSubscriptionById(sub.id); if (updated != null) scheduleSubscriptionReminder(updated.id, updated.name, updated.amount, updated.currency, updated.nextDateMillis); toast("تم تسجيل الاشتراك كمصروف وتحديث الشهر القادم"); showSubscriptions(); });
        c.addView(charge);
        Button toggle = softBtn(sub.active == 1 ? "إيقاف الاشتراك" : "تفعيل الاشتراك", sub.active == 1 ? RED : PRIMARY);
        toggle.setOnClickListener(v -> { db.toggleSubscription(sub.id, sub.active != 1); showSubscriptions(); });
        c.addView(toggle);
        root.addView(c);
    }

    private void addSubscriptionDialog() {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(5), dp(20), dp(5));
        EditText name = field("اسم الاشتراك: Google / Netflix", "");
        EditText amount = field("المبلغ", ""); amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText merchant = field("اسم التاجر اختياري", "");
        EditText category = field("التصنيف", "اشتراكات/أونلاين");
        EditText next = field("ميعاد الخصم القادم: 27/06/2026 20:30", "");
        EditText notes = field("ملاحظات", "");
        CheckBox egp = new CheckBox(this); egp.setText("جنيه مصري بدل ريال سعودي"); egp.setGravity(Gravity.RIGHT); egp.setTextDirection(View.TEXT_DIRECTION_RTL);
        box.addView(name); box.addView(amount); box.addView(merchant); box.addView(category); box.addView(next); box.addView(notes); box.addView(egp);
        new AlertDialog.Builder(this).setTitle("إضافة اشتراك شهري").setView(box)
                .setPositiveButton("حفظ", (d,w) -> {
                    double a = parseAmount(amount.getText().toString()); long nextMs = parseDateTime(next.getText().toString());
                    if (a <= 0 || name.getText().toString().trim().isEmpty()) { toast("اكتب الاسم والمبلغ"); return; }
                    String cur = egp.isChecked() ? "EGP" : "SAR";
                    long id = db.addSubscription(name.getText().toString(), a, cur, merchant.getText().toString(), category.getText().toString(), nextMs, notes.getText().toString());
                    if (nextMs > 0) scheduleSubscriptionReminder(id, name.getText().toString(), a, cur, nextMs);
                    toast("تم حفظ الاشتراك"); showSubscriptions();
                }).setNegativeButton("إلغاء", null).show();
    }

    private void openUrl(String url) {
        if (url == null || url.trim().isEmpty()) return;
        if (!url.startsWith("http") && !url.startsWith("whatsapp")) url = "https://" + url;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { toast("مش قادر أفتح الرابط"); }
    }

    private String cleanPhone(String p) {
        String s = p.replaceAll("[^0-9]", "");
        if (s.startsWith("0")) s = "966" + s.substring(1);
        return s;
    }

    private double parseAmount(String s) {
        try { return Double.parseDouble(s.trim().replace(",", ".")); } catch (Exception e) { return 0; }
    }

    private long parseDateTime(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        String v = value.trim().replace("-", "/");
        String[] patterns = {"dd/MM/yyyy HH:mm", "d/M/yyyy HH:mm", "dd/MM/yy HH:mm", "d/M/yy HH:mm", "dd/MM/yyyy", "d/M/yyyy"};
        for (String pattern : patterns) {
            try {
                SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.US);
                f.setLenient(false);
                Date date = f.parse(v);
                return date == null ? 0 : date.getTime();
            } catch (Exception ignored) {}
        }
        toast("صيغة التاريخ غير واضحة. استخدم مثال: 27/06/2026 20:30");
        return 0;
    }

    private void scheduleDebtReminder(long debtId, String name, double amount, String direction, long dueDateMillis) {
        if (dueDateMillis <= 0) return;
        if ("1".equals(db.getSetting("debt_remind_day", "1"))) scheduleReminderAlarm(debtId, name, amount, direction, dueDateMillis - 24L * 60L * 60L * 1000L, "BEFORE_DAY", 11);
        if ("1".equals(db.getSetting("debt_remind_2h", "1"))) scheduleReminderAlarm(debtId, name, amount, direction, dueDateMillis - 2L * 60L * 60L * 1000L, "BEFORE_2H", 22);
        scheduleReminderAlarm(debtId, name, amount, direction, dueDateMillis, "DUE", 33);
        if ("1".equals(db.getSetting("debt_repeat_overdue", "1"))) {
            for (int i = 1; i <= 5; i++) scheduleReminderAlarm(debtId, name, amount, direction, dueDateMillis + i * 24L * 60L * 60L * 1000L, "OVERDUE", 100 + i);
        }
    }

    private void scheduleReminderAlarm(long debtId, String name, double amount, String direction, long atMillis, String kind, int salt) {
        if (atMillis <= System.currentTimeMillis()) return;
        try {
            Intent intent = new Intent(this, DebtReminderReceiver.class);
            intent.putExtra("debtId", debtId);
            intent.putExtra("name", name);
            intent.putExtra("amount", money(amount));
            intent.putExtra("direction", direction);
            intent.putExtra("kind", kind);
            int req = (int)((debtId % 100000) * 10 + salt);
            PendingIntent pi = PendingIntent.getBroadcast(this, req, intent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am == null) return;
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
            else am.set(AlarmManager.RTC_WAKEUP, atMillis, pi);
        } catch (Exception ignored) {}
    }

    private void scheduleSubscriptionReminder(long subId, String name, double amount, String currency, long nextDateMillis) {
        if (nextDateMillis <= 0) return;
        if ("1".equals(db.getSetting("subscription_remind_3d", "1"))) scheduleSubscriptionAlarm(subId, name, amount, currency, nextDateMillis - 3L * 24L * 60L * 60L * 1000L, "SUBSCRIPTION_3D", 3);
        if ("1".equals(db.getSetting("subscription_remind_1d", "1"))) scheduleSubscriptionAlarm(subId, name, amount, currency, nextDateMillis - 24L * 60L * 60L * 1000L, "SUBSCRIPTION_1D", 1);
        scheduleSubscriptionAlarm(subId, name, amount, currency, nextDateMillis, "SUBSCRIPTION", 0);
    }

    private void scheduleSubscriptionAlarm(long subId, String name, double amount, String currency, long atMillis, String kind, int salt) {
        if (atMillis <= System.currentTimeMillis()) return;
        try {
            Intent intent = new Intent(this, DebtReminderReceiver.class);
            intent.putExtra("name", name);
            String sym = "EGP".equalsIgnoreCase(currency) ? "ج.م" : "ر.س";
            intent.putExtra("amount", String.format(Locale.US, "%.2f %s", amount, sym));
            intent.putExtra("direction", "SUBSCRIPTION");
            intent.putExtra("kind", kind);
            PendingIntent pi = PendingIntent.getBroadcast(this, (int)(700000 + (subId % 100000) * 10 + salt), intent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
            if (am == null) return;
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi);
            else am.set(AlarmManager.RTC_WAKEUP, atMillis, pi);
        } catch (Exception ignored) {}
    }

    private void showCategoryBudgets() {
        setup(L("ميزانيات الفئات", "Category budgets")); addHomeButton();
        LinearLayout hero = card();
        hero.setBackground(gradient(PURPLE, Color.rgb(92, 74, 168), 24));
        hero.addView(text(L("قسم ميزانيتك حسب الفئات", "Split your budget by category"), 18, true, Color.WHITE), matchWrap());
        hero.addView(text(L("وأنت بتضيف مصروف تقدر تختار الفئة، والتطبيق ينبهك لو فئة عدت حدها.", "Choose the category when adding expenses and track each limit."), 12, false, Color.rgb(235,231,255)), matchWrap());
        root.addView(hero);
        Button add = btn(L("إضافة / تعديل فئة", "Add / edit category")); add.setOnClickListener(v -> categoryBudgetDialog(null)); root.addView(add);
        for (ExpenseDbHelper.BudgetCategory bc : db.getBudgetCategories()) {
            LinearLayout c = card();
            double spent = db.getCategorySpent(bc.name);
            int col = bc.monthlyLimit > 0 && spent > bc.monthlyLimit ? RED : PRIMARY;
            c.addView(text(bc.name, 20, true, DARK), matchWrap());
            c.addView(text(L("المصروف: ", "Spent: ") + money(spent) + " / " + (bc.monthlyLimit <= 0 ? L("بدون حد", "No limit") : money(bc.monthlyLimit)), 13, true, col), matchWrap());
            BudgetProgressView bp = new BudgetProgressView(this, bc.monthlyLimit <= 0 ? 0 : spent / bc.monthlyLimit, pale(col), col);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(10)); lp.setMargins(0, dp(8), 0, dp(8)); c.addView(bp, lp);
            if (bc.monthlyLimit > 0) c.addView(text(L("المتبقي: ", "Remaining: ") + money(bc.monthlyLimit - spent), 13, false, MUTED), matchWrap());
            Button edit = softBtn(L("تعديل الفئة", "Edit category"), PURPLE); edit.setOnClickListener(v -> categoryBudgetDialog(bc)); c.addView(edit);
            root.addView(c);
        }
    }

    private void categoryBudgetDialog(ExpenseDbHelper.BudgetCategory bc) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(5), dp(20), dp(5));
        EditText name = field(L("اسم الفئة", "Category name"), bc == null ? "" : bc.name);
        EditText limit = field(L("ميزانية الفئة الشهرية", "Monthly category limit"), bc == null ? "" : String.valueOf(bc.monthlyLimit));
        limit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(name); box.addView(limit);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(L("فئة الميزانية", "Budget category")).setView(box)
                .setPositiveButton(L("حفظ", "Save"), (d,w) -> { db.addOrUpdateBudgetCategory(name.getText().toString(), parseAmount(limit.getText().toString())); toast(L("تم الحفظ", "Saved")); showCategoryBudgets(); })
                .setNegativeButton(L("إلغاء", "Cancel"), null);
        if (bc != null) builder.setNeutralButton(L("حذف", "Delete"), (d,w) -> { db.deleteBudgetCategory(bc.id); showCategoryBudgets(); });
        builder.show();
    }

    private void showMonthArchive() {
        setup(L("أرشيف الشهور", "Monthly archive")); addHomeButton();
        LinearLayout info = card(pale(ORANGE)); info.setBackground(strokeBg(pale(ORANGE), lighten(ORANGE), 22, 1));
        info.addView(text(L("كل شهر بيتقفل لوحده عند بداية شهر جديد، وتقدر تقفله يدويًا هنا.", "Each month is archived when a new month starts, and you can close it manually here."), 13, false, DARK), matchWrap());
        Button close = btn(L("قفل الشهر الحالي الآن", "Close current month now")); close.setOnClickListener(v -> { db.closeCurrentMonth(); toast(L("تم حفظ أرشيف الشهر", "Month archived")); showMonthArchive(); }); info.addView(close);
        root.addView(info);
        List<ExpenseDbHelper.MonthArchive> list = db.getMonthArchives();
        if (list.isEmpty()) { LinearLayout e = card(); e.addView(text(L("لسه مفيش شهور متقفلة", "No archived months yet"), 18, true, MUTED), matchWrap()); root.addView(e); return; }
        for (ExpenseDbHelper.MonthArchive m : list) {
            LinearLayout c = card();
            c.addView(text(m.monthKey, 22, true, DARK), matchWrap());
            c.addView(text(L("الميزانية: ", "Budget: ") + money(m.budget), 13, false, MUTED), matchWrap());
            c.addView(text(L("المصروف: ", "Spent: ") + money(m.spent), 13, true, m.spent > m.budget && m.budget > 0 ? RED : PRIMARY), matchWrap());
            c.addView(text(L("دخل إضافي: ", "Extra income: ") + money(m.extraIncome) + " | " + L("كاش: ", "Cash: ") + money(m.cashBalance), 13, false, MUTED), matchWrap());
            c.addView(text(L("اتقفل: ", "Closed: ") + ExpenseDbHelper.date(m.closedAt), 12, false, MUTED), matchWrap());
            root.addView(c);
        }
    }

    private void showCashWallet() {
        setup(L("محفظة الكاش", "Cash wallet")); addHomeButton();
        LinearLayout hero = card(); hero.setBackground(gradient(PRIMARY_DARK, PRIMARY, 24));
        hero.addView(text(L("الكاش المتاح", "Cash available"), 14, false, Color.rgb(220,250,242)), matchWrap());
        hero.addView(text(money(db.getCashBalance()), 32, true, Color.WHITE), matchWrap());
        hero.addView(text(L("سجل الكاش اللي معاك وصرف الكاش كعملية ضمن الميزانية.", "Track cash on hand and cash spending in your budget."), 12, false, Color.rgb(220,250,242)), matchWrap());
        root.addView(hero);
        Button add = btn(L("إضافة كاش", "Add cash")); add.setOnClickListener(v -> cashDialog(true)); root.addView(add);
        Button spend = softBtn(L("صرف كاش", "Spend cash"), ORANGE); spend.setOnClickListener(v -> cashDialog(false)); root.addView(spend);
        Button set = softBtn(L("تعديل رصيد الكاش يدويًا", "Set cash balance"), BLUE); set.setOnClickListener(v -> setCashDialog()); root.addView(set);
    }

    private void cashDialog(boolean addMode) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(5), dp(20), dp(5));
        EditText amount = field(L("المبلغ", "Amount"), ""); amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText title = field(L("ملاحظة / اسم المصروف", "Note / expense name"), addMode ? L("إضافة كاش", "Add cash") : L("مصروف كاش", "Cash expense"));
        EditText category = field(L("التصنيف", "Category"), "كاش");
        box.addView(amount); box.addView(title); if (!addMode) box.addView(category);
        new AlertDialog.Builder(this).setTitle(addMode ? L("إضافة كاش", "Add cash") : L("صرف كاش", "Spend cash")).setView(box)
                .setPositiveButton(L("حفظ", "Save"), (d,w) -> { double a = parseAmount(amount.getText().toString()); if (addMode) db.addCash(a, title.getText().toString()); else db.spendCash(a, title.getText().toString(), category.getText().toString()); showCashWallet(); })
                .setNegativeButton(L("إلغاء", "Cancel"), null).show();
    }

    private void setCashDialog() {
        EditText amount = new EditText(this); amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL); amount.setText(String.valueOf(db.getCashBalance())); amount.setGravity(Gravity.RIGHT);
        new AlertDialog.Builder(this).setTitle(L("تعديل رصيد الكاش", "Set cash balance")).setView(amount)
                .setPositiveButton(L("حفظ", "Save"), (d,w) -> { db.setCashBalance(parseAmount(amount.getText().toString())); showCashWallet(); })
                .setNegativeButton(L("إلغاء", "Cancel"), null).show();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    @Override public void onBackPressed() { showHome(); }

    public static class BudgetProgressView extends View {
        private final double progress;
        private final int bgColor;
        private final int fgColor;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        public BudgetProgressView(Activity ctx, double progress, int bgColor, int fgColor) {
            super(ctx); this.progress = Math.max(0, Math.min(1, progress)); this.bgColor = bgColor; this.fgColor = fgColor;
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float r = getHeight() / 2f;
            paint.setColor(bgColor);
            RectF full = new RectF(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(full, r, r, paint);
            paint.setColor(fgColor);
            RectF done = new RectF(0, 0, (float)(getWidth() * progress), getHeight());
            canvas.drawRoundRect(done, r, r, paint);
        }
    }

    public static class DebtProgressView extends BudgetProgressView {
        public DebtProgressView(Activity ctx, double progress, int bgColor, int fgColor) { super(ctx, progress, bgColor, fgColor); }
    }

    public class CategoryChartView extends View {
        private final List<ExpenseDbHelper.CatTotal> data;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] colors = new int[]{PRIMARY, BLUE, ORANGE, PURPLE, RED};
        public CategoryChartView(Activity ctx, List<ExpenseDbHelper.CatTotal> data) { super(ctx); this.data = data; }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (data == null || data.isEmpty()) {
                paint.setColor(Color.rgb(232, 239, 236));
                canvas.drawRoundRect(new RectF(0, dp(20), w, h - dp(20)), dp(18), dp(18), paint);
                paint.setColor(MUTED);
                paint.setTextSize(dp(13));
                paint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("ابدأ تسجيل مصاريفك عشان يظهر الرسم", w / 2f, h / 2f, paint);
                return;
            }
            double max = 1;
            for (ExpenseDbHelper.CatTotal c : data) if (c.total > max) max = c.total;
            float itemH = h / Math.max(1, data.size());
            paint.setTextSize(dp(11));
            for (int i = 0; i < data.size(); i++) {
                ExpenseDbHelper.CatTotal ct = data.get(i);
                float y = i * itemH + dp(6);
                paint.setColor(MUTED);
                paint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(ct.category, w - dp(4), y + dp(13), paint);
                paint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(String.format(Locale.US, "%.0f", ct.total), dp(4), y + dp(13), paint);
                float barTop = y + dp(22);
                float barH = dp(10);
                paint.setColor(Color.rgb(232, 239, 236));
                canvas.drawRoundRect(new RectF(0, barTop, w, barTop + barH), dp(8), dp(8), paint);
                paint.setColor(colors[i % colors.length]);
                float bw = (float)(w * Math.min(1, ct.total / max));
                canvas.drawRoundRect(new RectF(w - bw, barTop, w, barTop + barH), dp(8), dp(8), paint);
            }
        }
    }
}

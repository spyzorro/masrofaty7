package com.mohamed.expenseguard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class FirebaseSyncManager {
    public interface Callback {
        void ok(String message);
        void fail(String message);
    }

    private final Context context;
    private final ExpenseDbHelper db;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private FirebaseApp app;
    private static long lastAutoBackupAttempt = 0L;
    private static boolean autoBackupRunning = false;

    private String resString(String name) {
        try {
            int id = context.getResources().getIdentifier(name, "string", context.getPackageName());
            if (id == 0) return "";
            String v = context.getString(id);
            return v == null ? "" : v.trim();
        } catch (Exception e) { return ""; }
    }

    private String packagedWebClientId() {
        String v = resString("default_web_client_id");
        if (v.length() > 0) return v;
        v = resString("google_web_client_id");
        return v == null ? "" : v.trim();
    }

    private String packagedFirebaseValue(String key, String resName) {
        String v = resString(resName);
        if (v.length() > 0) return v;
        if ("firebase_api_key".equals(key)) return resString("google_api_key");
        if ("firebase_app_id".equals(key)) return resString("google_app_id");
        if ("firebase_project_id".equals(key)) return resString("project_id");
        return "";
    }

    private String cfg(String key, String resName) {
        if ("google_web_client_id".equals(key)) {
            String packaged = packagedWebClientId();
            if (packaged.length() > 0) return packaged;
            String saved = db.getSetting(key, "").trim();
            return saved;
        }
        String v = db.getSetting(key, "").trim();
        if (v.length() > 0) return v;
        v = packagedFirebaseValue(key, resName);
        if (v.length() > 0) return v;
        return "";
    }

    public boolean hasDeveloperDefaults() {
        return hasConfig();
    }

    public FirebaseSyncManager(Context context, ExpenseDbHelper db) {
        this.context = context.getApplicationContext();
        this.db = db;
    }

    public boolean hasConfig() {
        if (packagedWebClientId().length() > 0) return true;
        if (hasDefaultFirebaseApp()) return true;
        return cfg("firebase_api_key", "firebase_api_key").length() > 0
                && cfg("firebase_app_id", "firebase_app_id").length() > 0
                && cfg("firebase_project_id", "firebase_project_id").length() > 0
                && cfg("google_web_client_id", "google_web_client_id").length() > 0;
    }

    public void saveConfig(String apiKey, String appId, String projectId, String webClientId) {
        db.setSetting("firebase_api_key", apiKey.trim());
        db.setSetting("firebase_app_id", appId.trim());
        db.setSetting("firebase_project_id", projectId.trim());
        db.setSetting("google_web_client_id", webClientId.trim());
    }

    public void init() throws Exception {
        String webClientId = cfg("google_web_client_id", "google_web_client_id");
        if (webClientId.length() == 0) throw new Exception("مزامنة Google محتاجة Web Client ID من Firebase");
        try { app = FirebaseApp.getInstance(); } catch (Exception ignored) {}
        if (app == null) {
            try { app = FirebaseApp.initializeApp(context); } catch (Exception ignored) {}
        }
        if (app == null) {
            if (cfg("firebase_api_key", "firebase_api_key").length() == 0
                    || cfg("firebase_app_id", "firebase_app_id").length() == 0
                    || cfg("firebase_project_id", "firebase_project_id").length() == 0) {
                throw new Exception("أضف google-services.json داخل مجلد app أو أدخل إعدادات Firebase من شاشة المزامنة");
            }
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApiKey(cfg("firebase_api_key", "firebase_api_key"))
                    .setApplicationId(cfg("firebase_app_id", "firebase_app_id"))
                    .setProjectId(cfg("firebase_project_id", "firebase_project_id"))
                    .build();
            app = FirebaseApp.initializeApp(context, options, "masrofaty");
        }
        auth = FirebaseAuth.getInstance(app);
        firestore = FirebaseFirestore.getInstance(app);
    }

    private boolean hasDefaultFirebaseApp() {
        try {
            FirebaseApp existing = FirebaseApp.getInstance();
            return existing != null && cfg("google_web_client_id", "google_web_client_id").length() > 0;
        } catch (Exception ignored) {
            try {
                FirebaseApp initialized = FirebaseApp.initializeApp(context);
                return initialized != null && cfg("google_web_client_id", "google_web_client_id").length() > 0;
            } catch (Exception e) {
                return false;
            }
        }
    }

    public FirebaseUser currentUser() {
        try {
            init();
            return auth.getCurrentUser();
        } catch (Exception e) {
            return null;
        }
    }

    public Intent signInIntent() throws Exception {
        init();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(cfg("google_web_client_id", "google_web_client_id"))
                .requestEmail()
                .build();
        GoogleSignInClient client = GoogleSignIn.getClient(context, gso);
        return client.getSignInIntent();
    }

    public void handleSignInResult(Intent data, Callback cb) {
        try {
            init();
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null || account.getIdToken() == null) {
                cb.fail("تسجيل الدخول فشل: مفيش Token من جوجل");
                return;
            }
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            auth.signInWithCredential(credential)
                    .addOnSuccessListener(r -> cb.ok("تم تسجيل الدخول: " + (r.getUser() == null ? "" : r.getUser().getEmail())))
                    .addOnFailureListener(e -> cb.fail("فشل تسجيل الدخول: " + e.getMessage()));
        } catch (Exception e) {
            cb.fail("فشل تسجيل الدخول: " + e.getMessage());
        }
    }

    public void signOut(Activity activity, Callback cb) {
        try {
            init();
            auth.signOut();
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(cfg("google_web_client_id", "google_web_client_id"))
                    .requestEmail()
                    .build();
            GoogleSignIn.getClient(activity, gso).signOut()
                    .addOnCompleteListener(t -> cb.ok("تم تسجيل الخروج"));
        } catch (Exception e) {
            cb.fail(e.getMessage());
        }
    }

    public void uploadBackup(Callback cb) {
        try {
            init();
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) { cb.fail("سجل دخول بجوجل الأول"); return; }
            if (!db.hasUserData()) { cb.fail("مفيش داتا محلية تتحفظ. ضيف بيانات أو استرجع نسخة الأول"); return; }
            String backup = db.exportBackupJson();
            Map<String, Object> map = new HashMap<>();
            map.put("backupJson", backup);
            map.put("updatedAt", Timestamp.now());
            map.put("appName", "مصروفاتي");
            map.put("version", "2.36");
            map.put("recordsCount", db.userDataCount());
            firestore.collection("users").document(user.getUid()).collection("backups").document("current")
                    .set(map)
                    .addOnSuccessListener(v -> { db.setSetting("last_cloud_sync", String.valueOf(System.currentTimeMillis())); cb.ok("تم حفظ نسخة Google (" + db.userDataCount() + " سجل)"); })
                    .addOnFailureListener(e -> cb.fail("فشل الرفع: " + e.getMessage()));
        } catch (Exception e) {
            cb.fail(e.getMessage());
        }
    }

    public void restoreBackup(Callback cb) {
        try {
            init();
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) { cb.fail("سجل دخول بجوجل الأول"); return; }
            firestore.collection("users").document(user.getUid()).collection("backups").document("current")
                    .get()
                    .addOnSuccessListener(doc -> {
                        try {
                            if (!doc.exists()) { cb.fail("مفيش نسخة محفوظة على السحابة"); return; }
                            String backup = doc.getString("backupJson");
                            if (backup == null || backup.trim().isEmpty()) { cb.fail("النسخة السحابية فاضية"); return; }
                            if (!db.backupHasData(backup)) { cb.fail("النسخة السحابية لا تحتوي بيانات محفوظة"); return; }
                            int before = db.userDataCount();
                            int cloud = db.backupRecordCount(backup);
                            db.importBackupJson(backup);
                            db.setSetting("last_cloud_restore", String.valueOf(System.currentTimeMillis()));
                            cb.ok("تم استرجاع Google: السحابة " + cloud + " سجل، قبل " + before + "، بعد " + db.userDataCount());
                        } catch (Exception e) {
                            cb.fail("فشل الاسترجاع: " + e.getMessage());
                        }
                    })
                    .addOnFailureListener(e -> cb.fail("فشل التحميل: " + e.getMessage()));
        } catch (Exception e) {
            cb.fail(e.getMessage());
        }
    }

    public static void scheduleAutoBackup(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> runAutoBackup(appContext), 1500L);
    }

    private static void runAutoBackup(Context context) {
        synchronized (FirebaseSyncManager.class) {
            long now = System.currentTimeMillis();
            if (autoBackupRunning) return;
            if (now - lastAutoBackupAttempt < 10000L) return;
            autoBackupRunning = true;
            lastAutoBackupAttempt = now;
        }
        boolean asyncStarted = false;
        try {
            ExpenseDbHelper helper = new ExpenseDbHelper(context);
            if (!"1".equals(helper.getSetting("google_auto_backup", "1"))) return;
            if (!helper.hasUserData()) return;
            FirebaseSyncManager manager = new FirebaseSyncManager(context, helper);
            if (manager.currentUser() == null) return;
            asyncStarted = true;
            manager.uploadBackup(new Callback() {
                @Override public void ok(String message) { synchronized (FirebaseSyncManager.class) { autoBackupRunning = false; } }
                @Override public void fail(String message) { synchronized (FirebaseSyncManager.class) { autoBackupRunning = false; } }
            });
            return;
        } catch (Exception ignored) {
        } finally {
            if (!asyncStarted) {
                synchronized (FirebaseSyncManager.class) {
                    autoBackupRunning = false;
                }
            }
        }
    }
}

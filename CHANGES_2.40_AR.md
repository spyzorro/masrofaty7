# تغييرات Masrofaty v2.40

- إصلاح خطأ GitHub Actions:
  `Plugin with id 'com.google.gms.google-services' not found`
- إضافة تعريف Google Services plugin داخل `build.gradle` الجذر حتى يعمل `app/build.gradle` مع ملف `google-services.json`.
- استمرار استخدام `compileSdk 35` داخل `app/build.gradle`.
- رفع الإصدار إلى `versionName 2.40` و `versionCode 51`.

## الملفات المهمة في هذا الإصلاح

- `build.gradle`
- `app/build.gradle`
- `app/src/main/java/com/mohamed/expenseguard/MainActivity.java`
- `update.json`
- `CHANGELOG.txt`

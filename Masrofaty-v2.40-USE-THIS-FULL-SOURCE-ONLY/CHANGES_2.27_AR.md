# تعديلات Masrofaty v2.27

تم تقوية GitHub Actions لحل خطأ:

`Directory ... does not contain a Gradle build`

الجديد:

- ملف `android.yml` القديم أصبح يبني نفس النسخة النهائية بدل ما يفشل.
- ملف `masrofaty-final-one-apk.yml` أصبح أقوى.
- لو المشروع مرفوع داخل فولدر فرعي، يتم اكتشافه تلقائيًا.
- لو رفعت ملف ZIP نفسه داخل الريبو، الـ Workflow يحاول يفك الـ ZIP ويبحث داخله عن `settings.gradle`.
- البناء يستخدم `gradle -p "$PROJECT_DIR"` بدل `cd "$PROJECT_DIR"` لتقليل مشاكل المسارات.

الناتج الصحيح:

- Artifact: `FINAL-Masrofaty-ONE-APK`
- داخله APK واحد فقط:
  `Masrofaty-latest.apk`

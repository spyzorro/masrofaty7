# تعديلات Masrofaty v2.25

تم حل مشكلة ملف GitHub الذي يحتوي على 4 ملفات APK قديمة.

السبب كان أن GitHub ما زال يشغل Workflow قديم اسمه `Build Android APK` وينتج Artifact اسمه `Masrofaty-apk`.

الحل في هذه النسخة:

- إضافة Workflow جديد باسم:
  `Build Final Masrofaty One APK`
- تعطيل Workflow القديم داخل:
  `.github/workflows/android.yml`
- الناتج الصحيح أصبح Artifact باسم:
  `FINAL-Masrofaty-ONE-APK`
- داخل الـ Artifact يوجد ملف APK واحد فقط:
  `Masrofaty-latest.apk`
- رفع الإصدار إلى:
  `2.25`

أي ملف باسم `Masrofaty-apk_7.zip` أو `Masrofaty-apk` يعتبر ناتج قديم وليس النسخة الصحيحة.

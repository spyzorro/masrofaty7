# تعديلات Masrofaty v2.23

تم إصلاح الاسترجاع وتسهيل مزامنة Google:

- الاسترجاع من Google أو من ملف محلي أصبح "دمج آمن" بدل مسح الداتا الحالية.
- التطبيق يمنع رفع نسخة فاضية على Google عشان مايحصلش مسح للسحابة بعد إعادة التثبيت.
- قبل أي استرجاع، التطبيق يحفظ Snapshot باسم `masrofaty_before_restore.json` داخل ملفات التطبيق الخارجية.
- شاشة "مزامنة Google" فيها زر "إعداد Google Sync الآن" لإدخال قيم Firebase من داخل التطبيق.
- لو وضعت ملف `google-services.json` داخل مجلد `app/` في GitHub، Gradle يفعّل إعدادات Firebase تلقائيًا أثناء البناء.

مهم للتفعيل الحقيقي:

لا أقدر أضع إعدادات Firebase الخاصة بك من غير مشروع Firebase الخاص بك. للتفعيل الكامل لازم:

1. تنشئ مشروع Firebase.
2. تضيف Android app باسم الحزمة:
   `com.mohamed.expenseguard`
3. تفعّل Google Authentication.
4. تفعّل Firestore Database.
5. تنزل ملف `google-services.json` وتحطه داخل مجلد `app/` قبل رفع المشروع على GitHub.

بعدها GitHub Actions هيطلع APK واحد كامل باسم `Masrofaty-latest.apk`.

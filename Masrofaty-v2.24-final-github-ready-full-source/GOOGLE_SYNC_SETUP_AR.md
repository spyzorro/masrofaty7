# تفعيل مزامنة Google في Masrofaty

الكود جاهز للمزامنة، لكن لازم تربطه بمشروع Firebase الخاص بك.

## الطريقة الأسهل

1. افتح Firebase Console واعمل مشروع جديد.
2. أضف Android app بالبيانات دي:
   - Package name: `com.mohamed.expenseguard`
3. فعّل Authentication > Sign-in method > Google.
4. فعّل Firestore Database.
5. نزّل ملف `google-services.json`.
6. ضع الملف داخل مجلد:
   `app/google-services.json`
7. ارفع المشروع على GitHub وشغّل GitHub Actions.

بعد البناء، حمّل ملف واحد فقط:

`downloads/Masrofaty-latest.apk`

## بديل بدون تعديل ملفات

افتح التطبيق ثم:

المزيد > مزامنة Google والنسخ الاحتياطي > إعداد Google Sync الآن

واكتب القيم:

- Firebase API Key
- Firebase App ID
- Firebase Project ID
- Google Web Client ID

## ملاحظات مهمة

- التطبيق لن يرفع نسخة فاضية على Google.
- الاسترجاع أصبح دمج آمن، وليس مسح واستبدال مباشر.
- قبل الاسترجاع يتم حفظ Snapshot محلي باسم:
  `masrofaty_before_restore.json`

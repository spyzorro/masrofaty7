# تعديلات Masrofaty v2.24

هذه نسخة نهائية لحل مشكلتين:

- التطبيق كان يظهر/يتحمل كأنه 4 ملفات APK بسبب ملفات قديمة في مجلد `downloads`.
- التحديث داخل التطبيق لم يكن يصل دائمًا للملف الصحيح.

ما تم:

- تم وضع ملف Firebase الحالي في:
  `app/google-services.json`
- GitHub Actions يبني APK واحد فقط.
- الـ Artifact النهائي اسمه:
  `Masrofaty-latest-apk`
- داخله ملف واحد فقط:
  `Masrofaty-latest.apk`
- GitHub Release يتم إنشاؤه/تحديثه تلقائيًا وفيه:
  `Masrofaty-latest.apk`
  `update.json`
- التطبيق يفحص التحديث من GitHub Release أولًا.

مهم:

لو عندك ملفات APK قديمة موجودة في GitHub داخل `downloads`، الـ Action الجديد سيحذفها من المستودع ويترك `Masrofaty-latest.apk` فقط.

لو مزامنة Google لم تسجل الدخول بعد تثبيت APK، افتح Firebase وفعل:

- Authentication > Sign-in method > Google
- Firestore Database

ثم نزّل `google-services.json` مرة أخرى من Firebase وضعه في `app/`.

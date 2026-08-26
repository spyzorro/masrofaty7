# تغييرات Masrofaty v2.32

## حل نهائي لشاشة إعدادات Google اليدوية

- تم حذف ظهور رسالة:

```text
مالك التطبيق لم يفعّل إعدادات Google بعد
```

- تم حذف زر:

```text
إعداد Google Sync الآن
```

- المستخدم لن يدخل أي بيانات Firebase من داخل التطبيق.
- التطبيق يعتمد على `app/google-services.json` المدمج في APK.

## إصلاح سبب المشكلة

السبب كان أن التطبيق يبحث عن أسماء موارد مثل:

```text
firebase_api_key
firebase_app_id
firebase_project_id
```

بينما Firebase/Google plugin يولّدها بأسماء:

```text
google_api_key
google_app_id
project_id
```

تم إضافة دعم للأسماء الصحيحة، وتم جعل وجود Web Client ID المدمج كافيًا لاعتبار Google Sync جاهز.

## السلوك الجديد

- عند فتح التطبيق بدون حساب Google، تظهر شاشة تسجيل الدخول فقط.
- لا تظهر شاشة إدخال إعدادات Firebase.
- بعد تسجيل الدخول يتم الحفظ والاسترجاع تلقائيًا.

# تعديلات Masrofaty v2.28

تم تعديل شاشة الذهب:

- إضافة زر سريع: `جنيه ذهب`.
  - الوزن الافتراضي: `8 جرام`.
  - العيار الافتراضي: `21`.
- إضافة زر سريع: `سبيكة 10 جرام عيار 24`.
  - الوزن الافتراضي: `10 جرام`.
  - العيار الافتراضي: `24`.
- نفس الاختيارات موجودة داخل نافذة إضافة أو تعديل الذهب، بحيث تقدر تختار ثم تعدل السعر أو الوصف قبل الحفظ.

ملاحظات Google Sync:

- للحصول على القيم من Firebase:
  - Firebase API Key من ملف `google-services.json` داخل `client[0].api_key[0].current_key`.
  - Firebase App ID من `client[0].client_info.mobilesdk_app_id`.
  - Firebase Project ID من `project_info.project_id`.
  - Google Web Client ID يظهر بعد تفعيل Authentication > Google، ثم تنزيل `google-services.json` مرة ثانية.

# تعديلات Masrofaty v2.29

تم حل خطأ:

`resource xml/data_extraction_rules not found`

و:

`resource drawable/app_logo not found`

السبب أن GitHub كان يبني من فولدر:

`Masrofaty-v2.28-gold-presets-modified-files`

وهذا فولدر ملفات معدلة فقط، وليس مشروع Android كامل.

الحل:

- GitHub Actions أصبح يتجاهل أي فولدر اسمه `modified-files` أو `patch-only`.
- GitHub Actions يتحقق أن المشروع فيه:
  - `app/src/main/res/drawable/app_logo.png`
  - `app/src/main/res/xml/backup_rules.xml`
  - `app/src/main/res/xml/data_extraction_rules.xml`
  - `app/src/main/res/values/styles.xml`
  - ملفات Java الأساسية
- تم تجهيز ملف تحميل واضح باسم:
  `USE-THIS-FULL-SOURCE-ONLY`

مهم جدًا:

ارفع الملف الكامل فقط على GitHub. لا ترفع ملف `modified-files` للبناء.

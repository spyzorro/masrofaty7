# حل مشكلة بناء GitHub من modified-files

لو ظهر الخطأ:

`resource xml/data_extraction_rules not found`

أو:

`resource drawable/app_logo not found`

فهذا يعني أن GitHub يبني من ملف أو فولدر `modified-files`، وهذا ليس مشروع Android كامل.

## استخدم هذا فقط للبناء

ارفع محتويات ملف:

`Masrofaty-v2.29-USE-THIS-FULL-SOURCE-ONLY.zip`

ولا ترفع أي ملف اسمه:

- `modified-files`
- `patch-only`
- `Masrofaty-v*-modified-files.zip`

## الناتج الصحيح

بعد تشغيل GitHub Actions حمّل Artifact:

`FINAL-Masrofaty-ONE-APK`

وداخله ملف واحد فقط:

`Masrofaty-latest.apk`

## ماذا تم إصلاحه؟

ملف GitHub Actions أصبح يتجاهل أي فولدر ناقص مثل `modified-files`، ولا يبني إلا من مشروع كامل يحتوي على:

- `app/src/main/res/drawable/app_logo.png`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/main/res/values/styles.xml`
- ملفات Java الأساسية

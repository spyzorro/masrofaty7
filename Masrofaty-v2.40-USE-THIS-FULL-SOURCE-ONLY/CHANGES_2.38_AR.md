# Masrofaty v2.38

## الجديد
- مصدر سعر الريال مقابل الجنيه أصبح بنك مصر بدل بنك القاهرة.
- التطبيق يستخدم الرابط العربي الرسمي لبنك مصر أولًا:
  https://www.banquemisr.com/Home/CAPITAL%20MARKETS/Exchange%20rates%20and%20currencies?sc_lang=ar-EG
- لو الرابط العربي لم يقرأ لأي سبب، يحاول رابط بنك مصر الإنجليزي كاحتياطي من نفس البنك.
- لو كان محفوظ قبل كده سعر من بنك القاهرة، التطبيق يحدثه تلقائيًا إلى بنك مصر عند فتح الديون.
- الدين يظل محفوظًا ومعروضًا بالريال، وتحت المبلغ يظهر فقط المقابل التقريبي بالجنيه المصري.
- رسائل الديون تستخدم عملة الشخص نفسه.

## الملفات المعدلة
- app/src/main/java/com/mohamed/expenseguard/MainActivity.java
- app/src/main/java/com/mohamed/expenseguard/FirebaseSyncManager.java
- app/build.gradle
- update.json
- CHANGELOG.txt

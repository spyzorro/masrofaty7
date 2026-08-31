# Masrofaty v2.37

## الجديد
- ربط الديون بالريال السعودي التي لك عند الناس بقيمة تقريبية بالجنيه المصري.
- سعر التحويل يعتمد على سعر شراء بنك القاهرة من صفحة أسعار العملات الرسمية.
- في حالة فشل بنك القاهرة يحاول التطبيق قراءة سعر بنك مصر كمصدر احتياطي.
- يظهر المقابل بالجنيه في ملخص الديون وفي كارت كل دين ريالي مستحق لك.
- يوجد زر "تحديث سعر الريال مقابل الجنيه" داخل شاشة الديون، مع تحديث يومي تلقائي.
- الجنيه الذهب في شاشة الذهب أصبح يعتمد على سعر الجنيه الذهب المنشور في eDahab مباشرة، وليس على ضرب 8 جرام في سعر عيار 21.

## الملفات المعدلة
- app/src/main/java/com/mohamed/expenseguard/MainActivity.java
- app/src/main/java/com/mohamed/expenseguard/GoldActivity.java
- app/src/main/java/com/mohamed/expenseguard/FirebaseSyncManager.java
- app/build.gradle
- update.json
- CHANGELOG.txt

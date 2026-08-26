# تعديلات Masrofaty v2.26

تم حل خطأ GitHub Actions:

`Directory ... does not contain a Gradle build`

السبب أن ملفات Gradle لم تكن في جذر الريبو الذي شغّل منه GitHub الأمر.

الحل:

- الـ Workflow الجديد يبحث تلقائيًا عن `settings.gradle` أو `settings.gradle.kts`.
- لو المشروع مرفوع داخل فولدر فرعي، سيستخدم هذا الفولدر تلقائيًا.
- لو لم يجد ملفات Gradle، سيطبع الملفات الموجودة ويوضح أن المطلوب رفع محتويات المشروع وليس ملف ZIP فقط.

الناتج الصحيح ما زال:

- Workflow: `Build Final Masrofaty One APK`
- Artifact: `FINAL-Masrofaty-ONE-APK`
- APK واحد: `Masrofaty-latest.apk`

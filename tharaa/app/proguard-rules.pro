# الكيانات بتتسلسل لـ JSON للنسخ الاحتياطي: نحافظ عليها كما هي
-keep class com.tharaa.app.data.** { *; }
-keep class com.tharaa.app.domain.Commitment { *; }
-keep class com.tharaa.app.domain.Commitment$$serializer { *; }
-keepclassmembers class com.tharaa.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.tharaa.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class com.soaringscoring.xcsoaringscoring.api.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp / Okio use some reflection internally; the usual quiet-down rules
-dontwarn okhttp3.**
-dontwarn okio.**

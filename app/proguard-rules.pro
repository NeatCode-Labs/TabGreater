# kotlinx.serialization — keep serializers for our models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.neatcode.tabgreater.**$$serializer { *; }
-keepclassmembers class com.neatcode.tabgreater.** { *** Companion; }
-keepclasseswithmembers class com.neatcode.tabgreater.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# WebView JS bridge (chart) — keep names used from JavaScript.
-keepclassmembers class com.neatcode.tabgreater.feature.chart.** {
    @android.webkit.JavascriptInterface <methods>;
}

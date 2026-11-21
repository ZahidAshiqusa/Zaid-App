# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.zaidashiq.chat.**$$serializer { *; }
-keepclassmembers class com.zaidashiq.chat.** {
    *** Companion;
}
-keepclasseswithmembers class com.zaidashiq.chat.** {
    kotlinx.serialization.KSerializer serializer(...);
}

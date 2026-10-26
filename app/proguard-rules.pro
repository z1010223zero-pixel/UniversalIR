# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.zero.universalir.**$$serializer { *; }
-keepclassmembers class com.zero.universalir.** { *** Companion; }
-keepclasseswithmembers class com.zero.universalir.** { kotlinx.serialization.KSerializer serializer(...); }

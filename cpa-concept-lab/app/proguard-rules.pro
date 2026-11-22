# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.cpaconceptlab.study.**$$serializer { *; }
-keepclassmembers class com.cpaconceptlab.study.** { *** Companion; }
-keepclasseswithmembers class com.cpaconceptlab.study.** { kotlinx.serialization.KSerializer serializer(...); }

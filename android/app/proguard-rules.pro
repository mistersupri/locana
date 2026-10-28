# kotlinx.serialization: keep generated serializers for DTOs
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.locana.data.api.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.locana.data.api.**$$serializer { *; }
# Retrofit service interfaces are reflected on
-keep,allowobfuscation interface app.locana.data.api.LocanaApi
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

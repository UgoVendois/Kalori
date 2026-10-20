# Règles R8 spécifiques au projet.
# Les bibliothèques (Hilt, Room, Compose, kotlinx.serialization) fournissent leurs propres règles consumer.

# Retire tous les appels android.util.Log des builds release (aucune donnée en log).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}

# Les routes de navigation sont sérialisées par kotlinx.serialization.
-keep @kotlinx.serialization.Serializable class fr.kalori.app.navigation.** { *; }

# SQLCipher : classes appelées depuis le code natif (JNI).
-keep class net.zetetic.database.** { *; }
-keepclassmembers class net.zetetic.database.** { native <methods>; }

# SDK Anthropic (variantes IA) : sérialisation Jackson par réflexion, y compris Kotlin.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-dontwarn com.fasterxml.jackson.**
-dontwarn com.github.victools.**
-dontwarn io.swagger.**
-dontwarn org.slf4j.**
-dontwarn java.beans.**
-dontwarn com.standardwebhooks.**

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
-keep @kotlinx.serialization.Serializable class fr.nutrisport.app.navigation.** { *; }

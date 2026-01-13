# MobileCLI Production - ProGuard Rules
#
# NOTE: Minification is DISABLED for Termux compatibility.
# These rules are here for documentation and future use.

# Keep all terminal-related classes
-keep class com.termux.terminal.** { *; }
-keep class com.termux.view.** { *; }

# Keep Stripe SDK classes
-keep class com.stripe.** { *; }

# Keep Supabase-related models
-keep class com.termux.auth.** { *; }
-keep class com.termux.payment.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep R classes
-keepclassmembers class **.R$* {
    public static <fields>;
}

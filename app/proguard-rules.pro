# R8 full mode is on. Release builds must be smoke-tested separately from debug,
# because shrinking problems only surface in release.

# --- Room -------------------------------------------------------------------
# Room generates implementations reflectively referenced by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# --- Kotlin coroutines ------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# --- Hilt / Dagger ----------------------------------------------------------
# Hilt ships its own consumer rules; these cover generated component lookups.
-keep class dagger.hilt.internal.aggregatedroot.codegen.** { *; }
-keep class hilt_aggregated_deps.** { *; }

# --- Enums stored by name in Room -------------------------------------------
# Enum names are persisted, so obfuscating valueOf/name would corrupt reads.
-keepclassmembers enum com.shelfie.core.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Keep model classes intact ----------------------------------------------
-keep class com.shelfie.core.model.** { *; }

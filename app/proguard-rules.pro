# R8 rules for release builds.
#
# Most of what ForgeLog uses ships its own consumer rules — Room, Hilt, Compose and AndroidX all do,
# and those are applied automatically. What is here covers the one thing that does not survive
# shrinking on its own: kotlinx-serialization, which finds a class's serializer reflectively and so
# looks like dead code to R8. A backup that cannot be written or read is the worst possible way to
# discover a missing keep, which is why these are explicit rather than trusted to defaults.

# --- kotlinx-serialization ---
# Serializers are generated as a nested `$$serializer` class and reached through a static
# `Companion.serializer()`. Neither is called from Kotlin source R8 can see.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault

-keepclassmembers class dev.happyc0der.forgelog.** {
    *** Companion;
}
-keepclasseswithmembers class dev.happyc0der.forgelog.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class dev.happyc0der.forgelog.**
-keepclassmembers class dev.happyc0der.forgelog.<1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class dev.happyc0der.forgelog.** {
    static **$* *;
}
-keepclassmembers class dev.happyc0der.forgelog.<1>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# The backup DTOs are the payload of every export and import. What actually fixes the JSON keys is
# not this rule: the serialization plugin bakes each key into the generated descriptor as a string
# literal, so a renamed field keeps its key even when R8 obfuscates it. Verified on the release dex,
# where every descriptor still spells out every key in source order.
#
# This rule earns its place by keeping the classes out of R8's optimiser instead. Two data classes
# of the same shape are candidates for horizontal merging, and a merged class with someone else's
# generated serializer attached would write a file nothing can read back.
-keep,allowobfuscation,allowshrinking class dev.happyc0der.forgelog.data.backup.** { *; }

# --- Navigation Compose typed routes ---
# Routes are @Serializable data classes resolved by type at runtime.
-keep class dev.happyc0der.forgelog.ui.navigation.** { *; }

# --- Room ---
# Entities are constructed reflectively by generated code; the generated classes themselves are
# covered by Room's own consumer rules.
-keep class dev.happyc0der.forgelog.data.local.entity.** { *; }
-keep class dev.happyc0der.forgelog.data.local.relation.** { *; }

# --- Enums ---
# Enum constants are looked up by name in the type converters and in the backup format, so their
# names are data, not just identifiers.
-keepclassmembers enum dev.happyc0der.forgelog.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep line numbers so a crash report from a release build can be read, while still obfuscating.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

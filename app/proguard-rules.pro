-dontobfuscate

-renamesourcefileattribute SourceFile
-keepattributes Exceptions, SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

-keepattributes Signature, InnerClasses, EnclosingMethod

-keep class ac.mdiq.podcini**
-keepclassmembers class ac.mdiq.podcini** {*;}

-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern
-dontwarn java.beans.**
-dontwarn org.mozilla.javascript.**

-allowaccessmodification
-dontskipnonpubliclibraryclassmembers


# -dontwarn org.jspecify.annotations.NullMarked

# -keepclasseswithmembers class * {
#     @retrofit2.http.* <methods>;
# }
#
# Moshi
# -keep class com.squareup.moshi.** { *; }
# -keep interface com.squareup.moshi.** { *; }
####

# -dontwarn org.slf4j.impl.StaticLoggerBinder

-optimizations !class/merging/vertical,!class/merging/horizontal
-optimizations !method/propagation/parameter,!method/marking/private

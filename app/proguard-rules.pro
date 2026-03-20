-dontobfuscate

-renamesourcefileattribute SourceFile
-keepattributes Exceptions, SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

-keepattributes Signature, InnerClasses, EnclosingMethod

-keep class ac.mdiq.podcini**
-keepclassmembers class ac.mdiq.podcini** {*;}

# -keep class org.mozilla.javascript.** { *; }
# -keep class org.mozilla.classfile.ClassFileWriter
# -dontwarn org.mozilla.javascript.tools.**
# -keep class java.beans.**
# -dontwarn java.beans.**

-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern
-dontwarn java.beans.**
-dontwarn org.mozilla.javascript.**

-allowaccessmodification
-dontskipnonpubliclibraryclassmembers


# for okhttp
# -dontwarn okhttp3.**
# -dontwarn okio.**

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

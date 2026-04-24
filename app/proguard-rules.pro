-dontobfuscate

# -renamesourcefileattribute SourceFile

# -keepattributes Exceptions

-keepattributes SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes Signature, InnerClasses, EnclosingMethod

# -keep class ac.mdiq.podcini.** { *; }
# -keepclassmembers class ac.mdiq.podcini** {*;}

-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern
-dontwarn java.beans.**
-dontwarn org.mozilla.javascript.**

# -optimizations !code/simplification/inline,!code/merging/*,!class/merging/*,!method/propagation/parameter

-dontoptimize

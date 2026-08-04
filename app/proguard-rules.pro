# Release builds use R8. Keep only metadata needed for useful stack traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve structural metadata used by AndroidX, coroutines, and annotation processors.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

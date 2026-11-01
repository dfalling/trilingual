# The Anthropic SDK (de)serializes its models with Jackson via reflection.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn com.fasterxml.jackson.databind.ext.**
-dontwarn org.slf4j.**
-dontwarn io.swagger.**
-dontwarn com.google.errorprone.**
-dontwarn java.beans.**
-dontwarn javax.annotation.**
# Only reached by the SDK's class-derived structured outputs and OpenTelemetry,
# neither of which the app uses.
-dontwarn com.google.auto.value.AutoValue
-dontwarn java.lang.reflect.AnnotatedParameterizedType
-dontwarn java.lang.reflect.AnnotatedType

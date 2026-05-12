# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
 class:
-keepclassmembers class fqcn.of.javascript.interface.for.webview {
   public *;
}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Mantém a biblioteca de extração e os modelos de dados [cite: 1, 7]
-keep class com.maxrave.kotlinyoutubeextractor.** { *; }
-keep class com.evgenii.jsevaluator.** { *; }

# Mantém a biblioteca de extração e os modelos de dados 
-keep class com.maxrave.kotlinyoutubeextractor.** { *; }
-keep class com.evgenii.jsevaluator.** { *; }

# Garante que as interfaces de JavaScript do WebView não sejam renomeadas
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Impede que o R8 remova metadados das Data Classes (VideoMeta, YtFile, Format) 
-keepclassmembers class com.maxrave.kotlinyoutubeextractor.** {
    <fields>;
    <methods>;
}

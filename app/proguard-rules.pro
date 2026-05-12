# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
-keepclassmembers class fqcn.of.javascript.interface.for.webview {
   public *;


# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# 1. Configurações Padrão (Descomente para ajudar no Debug de erros)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 2. Mantém a biblioteca de extração e os modelos de dados
# Isso evita que o R8 renomeie as classes que você acabou de atualizar
-keep class com.maxrave.kotlinyoutubeextractor.** { *; }

# 3. Mantém a biblioteca JsEvaluator
# Essencial para rodar o código de decifração de assinatura (cipher) do YouTube
-keep class com.evgenii.jsevaluator.** { *; }

# 4. Garante que as interfaces de JavaScript não sejam removidas
# Sem isso, o motor do YouTube não consegue devolver o link decifrado para o app
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 5. Preservar campos das Data Classes (VideoMeta, YtFile, Format)
# Impede que o otimizador remova variáveis que parecem não estar em uso mas são lidas via reflexão
-keepclassmembers class com.maxrave.kotlinyoutubeextractor.** {
    <fields>;
    <methods>;
}

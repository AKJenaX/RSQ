# Razorpay ProGuard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.razorpay.** {*;}
-dontwarn com.razorpay.**

# If your project uses WebView with JS
-keepclassmembers class fqcn.of.javascript.interface.for.webview {
   public *;
}

-keepattributes SourceFile,LineNumberTable

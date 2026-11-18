# 保留 OkHttp 的可选平台类（缺失时 OkHttp 会走降级路径，但 R8 需要知道它们是被反射引用的）
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Coil 通过反射构建 ImageLoader / Decoder
-keep class coil.** { *; }
-dontwarn coil.**

# 我们自己的数据模型走 org.json 手工解析，不依赖反射，
# 但 Attachment / Session 的 data class 会被 Coil 的 model 解析间接引用，保一下更稳。
-keep class com.yg.dschat.Attachment { *; }
-keep class com.yg.dschat.Session { *; }

# WebView 的 JS 桥接方法必须保留
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

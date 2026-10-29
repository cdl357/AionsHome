# Add project specific ProGuard rules here.

# ==================== 通用规则 ====================

# 保留行号信息，便于调试崩溃日志
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 保留注解
-keepattributes *Annotation*

# 保留泛型签名
-keepattributes Signature

# ==================== OkHttp ====================
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn org.codehaus.mojo.animal_sniffer.*

# ==================== JSON 解析类 ====================
# 保留所有数据类（Kotlin data class）
-keep class com.aion.chat.compose.data.** { *; }

# 保留 org.json 类
-keep class org.json.** { *; }

# ==================== BouncyCastle ====================
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ==================== Jetpack Compose ====================
-keep class androidx.compose.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# ==================== WebView ====================
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ==================== 保留 BuildConfig ====================
-keep class com.aion.chat.BuildConfig { *; }

# ==================== 反射调用的类 ====================
# 如果有使用反射的类，在此添加
# -keep class your.package.ReflectedClass { *; }

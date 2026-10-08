# kotlinx.serialization keeps its generated serializers via the library's own
# consumer rules. BouncyCastle is used only through the lightweight X25519 API.
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.math.ec.rfc7748.** { *; }

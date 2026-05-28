package cn.toside.music.mobile.source.js

import android.util.Base64
import cn.toside.music.mobile.crypto.AES
import cn.toside.music.mobile.crypto.RSA
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Native crypto/util callbacks the v4 preload expects, ported 1:1 from `userApi/QuickJS.java`.
 * Reuses the kept [AES] / [RSA] Java helpers so behaviour matches the old RN app exactly.
 */
object CryptoBridge {

    fun str2b64(s: String): String =
        try {
            String(Base64.encode(s.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP))
        } catch (e: Exception) {
            ""
        }

    /** base64 → JSON array string of signed bytes (preload calls JSON.parse on the result). */
    fun b642buf(b64: String): String =
        try {
            val bytes = Base64.decode(b64.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            buildString {
                append('[')
                for (i in bytes.indices) {
                    append(bytes[i].toInt())
                    if (i < bytes.size - 1) append(',')
                }
                append(']')
            }
        } catch (e: Exception) {
            "[]"
        }

    fun str2md5(s: String): String =
        try {
            val str = URLDecoder.decode(s, "UTF-8")
            val md = MessageDigest.getInstance("MD5")
            md.digest(str.toByteArray(StandardCharsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }

    fun aesEncrypt(dataB64: String, keyB64: String, ivB64: String, mode: String): String =
        try {
            AES.encrypt(dataB64, keyB64, ivB64, mode) ?: ""
        } catch (e: Exception) {
            ""
        }

    fun rsaEncrypt(dataB64: String, key: String, padding: String): String =
        try {
            RSA.encryptRSAToString(dataB64, key, padding) ?: ""
        } catch (e: Exception) {
            ""
        }
}

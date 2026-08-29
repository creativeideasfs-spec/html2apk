package com.html2apk.builder.engine

import android.content.Context
import android.util.Log
import com.android.apksig.ApkSigner
import com.html2apk.builder.R
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.Enumeration

/**
 * apksig 签名器：使用内置默认密钥签名（v2/v3）。
 * 密钥从打包进 App 的 raw 资源读取。
 */
object ApkSignerRunner {

    private const val TAG = "HTML2APK.Sign"

    // 内置默认密钥（资源：raw/release.keystore）
    private const val STORE_PASS = "html2apk2026"
    private const val KEY_PASS = "html2apk2026"

    /** 签名；返回 null=成功，否则错误信息 */
    fun sign(ctx: Context, input: File, output: File): String? {
        return try {
            val ksRes = ctx.resources.openRawResource(R.raw.release)
            val ksData = ksRes.use { it.readBytes() }

            val ks = KeyStore.getInstance("PKCS12")
            ks.load(ByteArrayInputStream(ksData), STORE_PASS.toCharArray())

            val alias = findFirstKeyAlias(ks) ?: return "密钥库中无可用条目"
            val privKey = ks.getKey(alias, KEY_PASS.toCharArray()) as PrivateKey
            val cert = ks.getCertificate(alias) as X509Certificate

            val signerConfig = ApkSigner.SignerConfig.Builder(alias, privKey, Collections.singletonList(cert))
                .build()

            val builder = ApkSigner.Builder(Collections.singletonList(signerConfig))
                .setInputApk(input)
                .setOutputApk(output)
            // 强制 v2/v3 签名（Android 7+ 兼容，v1 非必须）
            builder.setV1SigningEnabled(false)
            builder.setV2SigningEnabled(true)
            builder.setV3SigningEnabled(true)

            builder.build().sign()
            Log.i(TAG, "signed OK: ${output.name}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "sign failed", e)
            e.message ?: "签名异常"
        }
    }

    private fun findFirstKeyAlias(ks: KeyStore): String? {
        val aliases: Enumeration<String> = ks.aliases()
        while (aliases.hasMoreElements()) {
            val a = aliases.nextElement()
            if (ks.isKeyEntry(a)) return a
        }
        return null
    }
}
package com.html2apk.builder.engine

import android.content.Context
import android.util.Log
import com.android.apksig.ApkSigner
import com.html2apk.builder.R
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Collections

/**
 * apksig 签名器：使用内置默认密钥签名（v2/v3）。
 *
 * 密钥以 PKCS8 DER 私钥 + X.509 DER 证书直接打包（raw/release_key、raw/release_cert），
 * 运行时不走 KeyStore 的 "PKCS12" 解析——部分 Android 系统/ROM 裁剪了内置
 * BouncyCastle 的 PBE 算法，PKCS12 的 MAC 校验会抛
 * "No installed provider supports this key: com.android.org.bouncycastle.jcajce.PKCS12Key"。
 * RSA/X.509 由 Conscrypt 保证支持，所有设备均可稳定签名。
 */
object ApkSignerRunner {

    private const val TAG = "HTML2APK.Sign"

    /** 签名；返回 null=成功，否则错误信息 */
    fun sign(ctx: Context, input: File, output: File): String? {
        return try {
            val keyData = ctx.resources.openRawResource(R.raw.release_key).use { it.readBytes() }
            val certData = ctx.resources.openRawResource(R.raw.release_cert).use { it.readBytes() }

            // 直接从 DER 构建密钥/证书，不依赖 BouncyCastle PBE（PKCS12）支持
            val privKey = KeyFactory.getInstance("RSA")
                .generatePrivate(PKCS8EncodedKeySpec(keyData))
            val cert = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(certData)) as X509Certificate

            val signerConfig = ApkSigner.SignerConfig.Builder("html2apk", privKey, Collections.singletonList(cert))
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
}
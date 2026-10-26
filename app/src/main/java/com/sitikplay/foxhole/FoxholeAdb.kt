package com.sitikplay.foxhole

import android.content.Context
import android.os.Build
import android.util.Base64
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date

class FoxholeAdb private constructor(ctx: Context) : AbsAdbConnectionManager() {

    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)

        val prefs = ctx.getSharedPreferences("rb_adb", Context.MODE_PRIVATE)
        val storedKey = prefs.getString("key", null)
        val storedCert = prefs.getString("cert", null)

        val loaded = if (storedKey != null && storedCert != null) {
            try {
                KeyFactory.getInstance("RSA").generatePrivate(
                    PKCS8EncodedKeySpec(Base64.decode(storedKey, Base64.NO_WRAP))
                ) to CertificateFactory.getInstance("X.509").generateCertificate(
                    ByteArrayInputStream(Base64.decode(storedCert, Base64.NO_WRAP))
                )
            } catch (e: Exception) {
                prefs.edit().remove("key").remove("cert").apply()
                null
            }
        } else null

        val (key, cert) = loaded ?: generate().also { (k, c) ->
            prefs.edit()
                .putString("key", Base64.encodeToString(k.encoded, Base64.NO_WRAP))
                .putString("cert", Base64.encodeToString(c.encoded, Base64.NO_WRAP))
                .apply()
        }
        privateKey = key
        certificate = cert
    }

    override fun getPrivateKey(): PrivateKey = privateKey

    override fun getCertificate(): Certificate = certificate

    override fun getDeviceName(): String = "Foxhole"

    companion object {
        @Volatile
        private var instance: FoxholeAdb? = null

        fun get(ctx: Context): FoxholeAdb =
            instance ?: synchronized(this) {
                instance ?: FoxholeAdb(ctx.applicationContext).also { instance = it }
            }

        fun forget(ctx: Context) {
            synchronized(this) {
                instance = null
                ctx.getSharedPreferences("rb_adb", Context.MODE_PRIVATE).edit().clear().apply()
            }
        }

        fun hasKey(ctx: Context): Boolean {
            val prefs = ctx.getSharedPreferences("rb_adb", Context.MODE_PRIVATE)
            return prefs.getString("key", null) != null &&
                prefs.getString("cert", null) != null &&
                prefs.getBoolean("paired", false)
        }

        fun markPaired(ctx: Context) {
            ctx.getSharedPreferences("rb_adb", Context.MODE_PRIVATE)
                .edit().putBoolean("paired", true).apply()
        }

        private fun generate(): Pair<PrivateKey, Certificate> {
            val gen = KeyPairGenerator.getInstance("RSA")
            gen.initialize(2048, SecureRandom())
            val pair = gen.generateKeyPair()
            val publicKey = pair.public
            val priv = pair.private

            val algorithm = "SHA512withRSA"
            val notBefore = Date()
            val notAfter = Date(System.currentTimeMillis() + 3650L * 24 * 60 * 60 * 1000)

            val extensions = CertificateExtensions()
            extensions.set(
                "SubjectKeyIdentifier",
                SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier)
            )
            val name = X500Name("CN=Foxhole")
            extensions.set("PrivateKeyUsage", PrivateKeyUsageExtension(notBefore, notAfter))

            val info = X509CertInfo()
            info.set("version", CertificateVersion(2))
            info.set("serialNumber", CertificateSerialNumber(SecureRandom().nextInt() and Int.MAX_VALUE))
            info.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(algorithm)))
            info.set("subject", CertificateSubjectName(name))
            info.set("key", CertificateX509Key(publicKey))
            info.set("validity", CertificateValidity(notBefore, notAfter))
            info.set("issuer", CertificateIssuerName(name))
            info.set("extensions", extensions)

            val cert = X509CertImpl(info)
            cert.sign(priv, algorithm)
            return Pair(priv, cert)
        }
    }
}


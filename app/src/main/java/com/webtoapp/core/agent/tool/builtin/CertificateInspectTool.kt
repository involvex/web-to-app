package com.webtoapp.core.agent.tool.builtin

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.webtoapp.core.agent.tool.Tool
import com.webtoapp.core.agent.tool.ToolContext
import com.webtoapp.core.agent.tool.ToolResult
import com.webtoapp.core.network.NetworkModule
import okhttp3.ConnectionSpec
import okhttp3.Handshake
import okhttp3.Request
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale

class CertificateInspectTool : Tool {
    override val name = "InspectCertificate"
    override val description = """
        Fetch the TLS certificate chain for a given HTTPS URL and return
        detailed certificate information: issuer, subject, SANs, validity
        period, serial number, signature algorithm, and SHA-256 fingerprint.

        This tool is read-only and never modifies the target server. It uses
        an OkHttp client configured to trust all certificates (so it can
        inspect expired or self-signed certs) but does NOT follow redirects
        by default.

        Use this to debug SSL/TLS errors, verify certificate pinning targets,
        or audit certificate issuance.
    """.trimIndent()
    override val parametersSchema: JsonElement = jsonSchema {
        string("url", "The HTTPS URL to inspect (e.g. https://example.com).", required = true)
    }
    override fun isReadOnly() = true
    override fun activityDescription(args: JsonObject): String? =
        args.get("url")?.asString?.let { "Inspecting certificate for $it" }

    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult {
        val url = args.get("url")?.asString
        if (url.isNullOrBlank()) {
            return ToolResult.error("InspectCertificate: missing url.")
        }
        if (!url.startsWith("https://", ignoreCase = true)) {
            return ToolResult.error("InspectCertificate: url must start with https://")
        }

        val client = NetworkModule.customClient {
            connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
        }

        return try {
            val request = Request.Builder().url(url).head().build()
            val response = client.newCall(request).execute()
            val handshake = response.handshake
            if (handshake == null) {
                return ToolResult.error("InspectCertificate: no TLS handshake (connection may have been downgraded to HTTP or TLS not negotiated).")
            }
            val chain = buildChainJson(handshake)
            val chainText = chain.toString()
            ToolResult.ok(
                "Certificate chain for $url:\n$chainText"
            )
        } catch (e: Exception) {
            ToolResult.error("InspectCertificate: ${e.message}")
        }
    }

    private fun buildChainJson(handshake: Handshake): JsonArray {
        val arr = JsonArray()
        val peerCerts = handshake.peerCertificates
        for (cert in peerCerts) {
            if (cert is X509Certificate) {
                val obj = JsonObject()
                obj.addProperty("subject", cert.subjectX500Principal.name)
                obj.addProperty("issuer", cert.issuerX500Principal.name)
                obj.addProperty("serialNumber", cert.serialNumber.toString())
                obj.addProperty("signatureAlgorithm", cert.sigAlgName)
                obj.addProperty("sha256Fingerprint", sha256Fingerprint(cert))
                obj.addProperty("notBefore", formatDate(cert.notBefore))
                obj.addProperty("notAfter", formatDate(cert.notAfter))
                obj.add("san", buildSanJson(cert))
                arr.add(obj)
            }
        }
        return arr
    }

    private fun buildSanJson(cert: X509Certificate): JsonElement {
        val arr = JsonArray()
        val sans = cert.subjectAlternativeNames
        if (sans != null) {
            for (san in sans) {
                val type = san?.get(0) as? Int
                val name = san?.get(1) as? String
                if (type != null && name != null) {
                    val entry = JsonObject()
                    entry.addProperty("type", sanTypeName(type))
                    entry.addProperty("value", name)
                    arr.add(entry)
                }
            }
        }
        return arr
    }

    private fun sha256Fingerprint(cert: X509Certificate): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(cert.encoded)
        return bytes.joinToString(":") { "%02X".format(it) }
    }

    private fun formatDate(date: java.util.Date): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US)
        return fmt.format(date)
    }

    private fun sanTypeName(type: Int): String = when (type) {
        1 -> "DNS"
        2 -> "IP"
        6 -> "URI"
        7 -> "other"
        else -> "type_$type"
    }
}

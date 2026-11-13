package com.kutlusoy.tvsig.update

import com.google.gson.JsonParser

/** The newest release of the app and the files that belong to it. */
class ReleaseInfo(
    val version: String,
    val versionCode: Long,
    val apkName: String?,
    val apkUrl: String?,
    val apkSize: Long,
    val checksumsUrl: String?
)

object ReleaseParser {
    private val APK_NAME = Regex("^TVSig-.+\\.apk$")
    private const val CHECKSUMS_NAME = "SHA256SUMS.txt"

    /** Reads the answer of the GitHub API for one release. Returns null for a draft, a pre-release or a bad answer. */
    fun parse(json: String): ReleaseInfo? {
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            return null
        }
        if (root.get("draft")?.asBoolean == true || root.get("prerelease")?.asBoolean == true) return null
        val tag = root.get("tag_name")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        val version = tag.removePrefix("v")
        val code = Versions.code(version) ?: return null

        var apkName: String? = null
        var apkUrl: String? = null
        var apkSize = -1L
        var checksumsUrl: String? = null
        root.getAsJsonArray("assets")?.forEach { element ->
            val asset = element.asJsonObject
            val name = asset.get("name")?.asString ?: return@forEach
            val url = asset.get("browser_download_url")?.asString ?: return@forEach
            when {
                name == CHECKSUMS_NAME -> checksumsUrl = url
                apkName == null && APK_NAME.matches(name) -> {
                    apkName = name
                    apkUrl = url
                    apkSize = asset.get("size")?.asLong ?: -1L
                }
            }
        }
        return ReleaseInfo(version, code, apkName, apkUrl, apkSize, checksumsUrl)
    }
}

object Versions {
    private val PATTERN = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(-[0-9A-Za-z.]+)?$")

    /** The version code of a version, the same scheme as in app/version.txt and tools/version.py. Null for anything else. */
    fun code(version: String): Long? {
        val match = PATTERN.matchEntire(version) ?: return null
        val (major, minor, patch) = match.destructured
        if (minor.length > 3 || patch.length > 3 || major.length > 4) return null
        return major.toLong() * 1_000_000 + minor.toLong() * 1_000 + patch.toLong()
    }
}

object Checksums {
    private val HASH = Regex("^[0-9a-fA-F]{64}$")

    /** Finds the SHA-256 of a file in the text of SHA256SUMS.txt (lines of the form "hash  ./name"). */
    fun find(text: String, fileName: String): String? =
        text.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val name = parts[1].removePrefix("*").removePrefix("./")
            if (name == fileName && HASH.matches(parts[0])) parts[0].lowercase() else null
        }.firstOrNull()
}

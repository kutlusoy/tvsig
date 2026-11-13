package com.kutlusoy.tvsig.update

import android.content.Context
import com.kutlusoy.tvsig.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Looks for a newer release on GitHub and installs it. Nothing happens without a press of the button. */
class UpdateManager(private val context: Context) {
    private val downloader = UpdateDownloader()
    private val folder = File(context.cacheDir, "update")

    /** The newest release, or null when it is not newer than the installed version. */
    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        val json = downloader.fetchText(LATEST_RELEASE_URL, mapOf("Accept" to "application/vnd.github+json"))
        val release = ReleaseParser.parse(json) ?: throw UpdateException(UpdateException.Code.NOT_FOUND)
        if (release.versionCode > BuildConfig.VERSION_CODE) CheckResult.Available(release) else CheckResult.UpToDate(release.version)
    }

    sealed class CheckResult {
        class Available(val release: ReleaseInfo) : CheckResult()
        class UpToDate(val version: String) : CheckResult()
    }

    enum class Phase { DOWNLOADING, VERIFYING, INSTALLING }

    suspend fun downloadAndInstall(release: ReleaseInfo, onPhase: (Phase, Int) -> Unit) = withContext(Dispatchers.IO) {
        val apkUrl = release.apkUrl
        val apkName = release.apkName
        if (apkUrl == null || apkName == null) throw UpdateException(UpdateException.Code.NO_APK)
        val checksumsUrl = release.checksumsUrl ?: throw UpdateException(UpdateException.Code.NO_CHECKSUM)

        folder.deleteRecursively()
        folder.mkdirs()
        if (release.apkSize > 0 && folder.usableSpace < release.apkSize * 2) throw UpdateException(UpdateException.Code.STORAGE)

        onPhase(Phase.VERIFYING, 0)
        val checksum = Checksums.find(downloader.fetchText(checksumsUrl), apkName)
            ?: throw UpdateException(UpdateException.Code.NO_CHECKSUM)

        val target = File(folder, apkName)
        onPhase(Phase.DOWNLOADING, 0)
        downloader.download(apkUrl, target, checksum, MAX_APK_BYTES) { percent -> onPhase(Phase.DOWNLOADING, percent) }

        onPhase(Phase.INSTALLING, 100)
        try {
            UpdateInstaller.install(context, target)
        } catch (e: Exception) {
            throw UpdateException(UpdateException.Code.INSTALL_FAILED, e.message)
        }
    }

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/kutlusoy/tvsig/releases/latest"
        private const val MAX_APK_BYTES = 300L * 1024 * 1024
    }
}

package com.kutlusoy.tvsig.update

class UpdateException(val code: Code, detail: String? = null) : Exception(detail ?: code.name) {
    enum class Code { NETWORK, NOT_FOUND, NO_APK, NO_CHECKSUM, CHECKSUM_MISMATCH, INSECURE_URL, TOO_LARGE, STORAGE, INSTALL_FAILED }
}

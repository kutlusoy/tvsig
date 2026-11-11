package com.kutlusoy.tvsig.models

sealed class PresentationState {
    object Loading : PresentationState()
    data class Error(val message: String) : PresentationState()
    data class Success(
        val files: List<MediaFile>,
        val currentIndex: Int = 0
    ) : PresentationState()
}

data class CurrentMedia(
    val file: MediaFile,
    val path: String,
    val token: Long
)

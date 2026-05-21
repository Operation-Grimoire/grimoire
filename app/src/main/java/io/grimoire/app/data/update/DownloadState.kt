package io.grimoire.app.data.update

import java.io.File

sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val bytesRead: Long, val totalBytes: Long) : DownloadState()
    data class Completed(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

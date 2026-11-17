package io.grimoire.app.data.reading

internal enum class CompletionSaveNotice {
    SAVE_FAILED,
    WORD_COUNT_FAILED,
    CHANGED,
    PARTIAL_SAVE_FAILED,
    PARTIAL_WORD_COUNT_FAILED,
    PARTIAL_CHANGED,
}

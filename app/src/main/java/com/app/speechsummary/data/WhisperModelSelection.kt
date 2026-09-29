package com.app.speechsummary.data

/** The previous base Q5_1 model remains the default for existing installs. */
class WhisperModelSelection(
    private val read: () -> String?,
    private val write: (String) -> Unit
) {
    fun load(): WhisperModel = WhisperModel.entries.firstOrNull { it.name == read() }
        ?: WhisperModel.BASE

    fun save(model: WhisperModel) = write(model.name)
}

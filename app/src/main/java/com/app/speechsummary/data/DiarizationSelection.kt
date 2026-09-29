package com.app.speechsummary.data

/** Store the enum name so preferences survive app restarts and unknown older values default safely. */
class DiarizationSelection(
    private val read: () -> String?,
    private val write: (String) -> Unit
) {
    fun load(): DiarizationEngine = DiarizationEngine.entries.firstOrNull { it.name == read() }
        ?: DiarizationEngine.SHERPA

    fun save(engine: DiarizationEngine) = write(engine.name)
}

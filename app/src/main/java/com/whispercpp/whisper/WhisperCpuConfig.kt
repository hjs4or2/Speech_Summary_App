package com.whispercpp.whisper

import android.util.Log
import java.io.BufferedReader
import java.io.FileReader

object WhisperCpuConfig {
    val preferredThreadCount: Int
        get() = selectThreadCount(
            Runtime.getRuntime().availableProcessors(),
            CpuInfo.readMaxFrequencies()
        )

    internal fun selectThreadCount(availableProcessors: Int, maxFrequencies: List<Int>?): Int {
        // Match whisper_full_default_params' conservative four-thread budget. This
        // also bounds contention when multiple files are transcribed concurrently.
        val limit = availableProcessors.coerceIn(1, 4)
        if (maxFrequencies.isNullOrEmpty() ||
            maxFrequencies.size < availableProcessors ||
            maxFrequencies.any { it <= 0 }
        ) return limit

        val minimum = maxFrequencies.min()
        val fasterCpus = maxFrequencies.count { it > minimum }
        // Only a complete frequency list can describe the CPU topology. Equal
        // frequencies describe a homogeneous CPU, not zero usable cores.
        if (fasterCpus == 0) return limit

        // Keep the existing preference for faster clusters when frequency data is
        // available. Never request more workers than the runtime can actually use.
        return fasterCpus.coerceIn(minOf(2, limit), limit)
    }
}

private object CpuInfo {
    private const val LOG_TAG = "WhisperCpuConfig"

    fun readMaxFrequencies(): List<Int>? = try {
        BufferedReader(FileReader("/proc/cpuinfo")).useLines { lines ->
            lines.filter { it.substringBefore(':').trim() == "processor" }
                .map { getMaxCpuFrequency(it.substringAfter(':').trim().toInt()) }
                .toList()
        }
    } catch (e: Exception) {
        // CPU variant is a hardware revision, not a performance ranking. Sysfs can
        // be unavailable on emulators or restricted devices; use the bounded
        // runtime processor count instead of subtracting an assumed four cores.
        Log.d(LOG_TAG, "CPU frequencies unavailable; using runtime CPU count", e)
        null
    }

    private fun getMaxCpuFrequency(cpuIndex: Int): Int {
        val path = "/sys/devices/system/cpu/cpu${cpuIndex}/cpufreq/cpuinfo_max_freq"
        return BufferedReader(FileReader(path)).use { it.readLine().trim().toInt() }
    }
}

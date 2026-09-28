package com.whispercpp.whisper

import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperCpuConfigTest {
    @Test fun homogeneousQuadCoreUsesFourWorkersInsteadOfTwo() {
        assertEquals(4, WhisperCpuConfig.selectThreadCount(4, List(4) { 2_400_000 }))
    }

    @Test fun missingSysfsUsesBoundedRuntimeCount() {
        assertEquals(4, WhisperCpuConfig.selectThreadCount(4, null))
        assertEquals(4, WhisperCpuConfig.selectThreadCount(8, emptyList()))
    }

    @Test fun invalidFrequencyDataUsesRuntimeFallback() {
        assertEquals(4, WhisperCpuConfig.selectThreadCount(4, listOf(0, 2_400_000)))
        assertEquals(4, WhisperCpuConfig.selectThreadCount(4, listOf(-1)))
    }

    @Test fun incompleteFrequencyDataUsesRuntimeFallback() {
        assertEquals(4, WhisperCpuConfig.selectThreadCount(4, listOf(2_400_000)))
        assertEquals(4, WhisperCpuConfig.selectThreadCount(8,
            listOf(1_800_000, 2_400_000)))
    }

    @Test fun homogeneousSmallDevicesUseEveryAvailableWorker() {
        assertEquals(2, WhisperCpuConfig.selectThreadCount(2, List(2) { 2_400_000 }))
        assertEquals(3, WhisperCpuConfig.selectThreadCount(3, List(3) { 2_400_000 }))
    }

    @Test fun preservesFasterClusterPreference() {
        assertEquals(4, WhisperCpuConfig.selectThreadCount(8,
            List(4) { 1_800_000 } + List(3) { 2_400_000 } + 3_000_000))
        assertEquals(2, WhisperCpuConfig.selectThreadCount(8,
            List(6) { 1_800_000 } + List(2) { 2_400_000 }))
    }

    @Test fun onePrimeCoreRetainsTwoWorkerMinimumWhenAvailable() {
        assertEquals(2, WhisperCpuConfig.selectThreadCount(8,
            List(7) { 1_800_000 } + 3_000_000))
    }

    @Test fun neverExceedsRuntimeAvailability() {
        assertEquals(2, WhisperCpuConfig.selectThreadCount(2,
            List(4) { 1_800_000 } + List(4) { 2_400_000 }))
        assertEquals(1, WhisperCpuConfig.selectThreadCount(1, List(8) { 2_400_000 }))
        assertEquals(1, WhisperCpuConfig.selectThreadCount(1, null))
    }

    @Test fun capsManyCoreDevicesToUpstreamDefaultBudget() {
        assertEquals(4, WhisperCpuConfig.selectThreadCount(16, List(16) { 2_400_000 }))
        assertEquals(4, WhisperCpuConfig.selectThreadCount(12,
            List(4) { 1_800_000 } + List(8) { 2_400_000 }))
    }

    @Test fun invalidRuntimeCountStillProvidesOneWorker() {
        assertEquals(1, WhisperCpuConfig.selectThreadCount(0, null))
        assertEquals(1, WhisperCpuConfig.selectThreadCount(-1, listOf(2_400_000)))
    }
}

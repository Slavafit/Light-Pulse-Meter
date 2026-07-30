package com.slavafit.lightflicker.measurement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SignalProcessorTest {
    @Test
    fun detectsSyntheticTenHertzSignal() {
        val fps = 60.0
        val samples = (0 until 240).map { index ->
            val seconds = index / fps
            FrameSample(
                timestampNs = (seconds * 1e9).toLong(),
                brightness = 120.0 + 24.0 * sin(2.0 * PI * 10.0 * seconds),
                saturatedRatio = 0.0,
                motion = 0.2,
                rowContrast = 5.0,
            )
        }

        val result = SignalProcessor.process(samples)

        assertNotNull(result.frequencyHz)
        assertEquals(10.0, result.frequencyHz!!, 0.3)
        assertTrue(result.flickerPercent!! > 15.0)
        assertEquals(ResultZone.RED, result.zone)
    }

    @Test
    fun rejectsInsufficientData() {
        val result = SignalProcessor.process(emptyList())
        assertEquals(ResultZone.GRAY, result.zone)
        assertEquals(null, result.frequencyHz)
    }
}

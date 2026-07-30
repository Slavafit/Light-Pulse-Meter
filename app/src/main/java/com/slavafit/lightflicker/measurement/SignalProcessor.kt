package com.slavafit.lightflicker.measurement

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

enum class Confidence { HIGH, MEDIUM, LOW }
enum class ResultZone { GREEN, YELLOW, RED, GRAY }

data class FrameSample(
    val timestampNs: Long,
    val brightness: Double,
    val saturatedRatio: Double,
    val motion: Double,
    val rowContrast: Double,
)

data class MeasurementResult(
    val frequencyHz: Double?,
    val flickerPercent: Double?,
    val confidence: Confidence,
    val zone: ResultZone,
)

object SignalProcessor {
    fun process(samples: List<FrameSample>): MeasurementResult {
        if (samples.size < 45) return unreliable()
        val duration = (samples.last().timestampNs - samples.first().timestampNs) / 1e9
        if (duration < 2.5) return unreliable()

        val values = samples.map { it.brightness }
        val mean = values.average()
        if (mean < 25.0 || mean > 245.0) return unreliable()
        val sorted = values.sorted()
        val low = percentile(sorted, 0.05)
        val high = percentile(sorted, 0.95)
        val flicker = ((high - low) / (2.0 * mean) * 100.0).coerceIn(0.0, 100.0)

        val fps = (samples.size - 1) / duration
        val centered = values.mapIndexed { index, value ->
            val trendRadius = 4
            val from = max(0, index - trendRadius)
            val to = minOf(values.lastIndex, index + trendRadius)
            value - values.subList(from, to + 1).average()
        }
        val (frequency, peakRatio) = dominantFrequency(centered, fps)
        val motion = samples.map { it.motion }.average()
        val saturation = samples.map { it.saturatedRatio }.average()
        val rowEvidence = samples.map { it.rowContrast }.average()

        val quality = peakRatio * 0.55 +
            (1.0 - (motion / 18.0).coerceIn(0.0, 1.0)) * 0.25 +
            (1.0 - (saturation / 0.12).coerceIn(0.0, 1.0)) * 0.10 +
            (rowEvidence / 18.0).coerceIn(0.0, 1.0) * 0.10

        if (frequency == null || peakRatio < 0.18 || motion > 28.0 || saturation > 0.25) {
            return unreliable()
        }

        val confidence = when {
            quality >= 0.66 && samples.size >= 90 -> Confidence.HIGH
            quality >= 0.42 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        if (confidence == Confidence.LOW) return unreliable()

        // Prototype thresholds: configurable after calibration against a reference meter.
        val zone = when {
            flicker < 5.0 -> ResultZone.GREEN
            flicker < 15.0 -> ResultZone.YELLOW
            else -> ResultZone.RED
        }
        return MeasurementResult(frequency, flicker, confidence, zone)
    }

    private fun dominantFrequency(values: List<Double>, sampleRate: Double): Pair<Double?, Double> {
        if (sampleRate <= 1.0) return null to 0.0
        val n = values.size
        val powers = mutableListOf<Pair<Int, Double>>()
        for (k in 1 until n / 2) {
            val hz = k * sampleRate / n
            if (hz < 2.0) continue
            var real = 0.0
            var imaginary = 0.0
            for (i in values.indices) {
                val window = 0.5 - 0.5 * cos(2.0 * PI * i / (n - 1))
                val angle = 2.0 * PI * k * i / n
                real += values[i] * window * cos(angle)
                imaginary -= values[i] * window * sin(angle)
            }
            powers += k to hypot(real, imaginary)
        }
        val peak = powers.maxByOrNull { it.second } ?: return null to 0.0
        val rms = sqrt(powers.map { it.second * it.second }.average()).coerceAtLeast(1e-6)
        val ratio = (peak.second / (rms * 4.0)).coerceIn(0.0, 1.0)
        return (peak.first * sampleRate / n) to ratio
    }

    private fun percentile(sorted: List<Double>, q: Double): Double {
        val position = q * (sorted.size - 1)
        val lower = position.toInt()
        val fraction = position - lower
        return sorted[lower] * (1.0 - fraction) + sorted[minOf(lower + 1, sorted.lastIndex)] * fraction
    }

    private fun unreliable() = MeasurementResult(null, null, Confidence.LOW, ResultZone.GRAY)
}

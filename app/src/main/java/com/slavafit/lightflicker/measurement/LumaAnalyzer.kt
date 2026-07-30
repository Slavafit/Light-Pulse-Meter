package com.slavafit.lightflicker.measurement

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.sqrt

class LumaAnalyzer(private val onSample: (FrameSample) -> Unit) : ImageAnalysis.Analyzer {
    private var previousGrid: DoubleArray? = null

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val width = image.width
            val height = image.height
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val left = width / 4
            val right = width * 3 / 4
            val top = height / 4
            val bottom = height * 3 / 4
            val step = 4
            var sum = 0.0
            var count = 0
            var saturated = 0
            val rowMeans = mutableListOf<Double>()
            val grid = DoubleArray(64)
            val gridCounts = IntArray(64)

            var y = top
            while (y < bottom) {
                var rowSum = 0.0
                var rowCount = 0
                var x = left
                while (x < right) {
                    val index = y * rowStride + x * pixelStride
                    if (index < buffer.limit()) {
                        val value = buffer.get(index).toInt() and 0xff
                        sum += value
                        rowSum += value
                        count++
                        rowCount++
                        if (value >= 250) saturated++
                        val gx = ((x - left) * 8 / (right - left)).coerceIn(0, 7)
                        val gy = ((y - top) * 8 / (bottom - top)).coerceIn(0, 7)
                        val gi = gy * 8 + gx
                        grid[gi] += value
                        gridCounts[gi]++
                    }
                    x += step
                }
                if (rowCount > 0) rowMeans += rowSum / rowCount
                y += step
            }
            if (count == 0) return
            for (i in grid.indices) if (gridCounts[i] > 0) grid[i] /= gridCounts[i]
            val previous = previousGrid
            val motion = if (previous == null) 0.0 else grid.indices.map { abs(grid[it] - previous[it]) }.average()
            previousGrid = grid
            val rowAverage = rowMeans.average()
            val rowContrast = sqrt(rowMeans.map { (it - rowAverage) * (it - rowAverage) }.average())
            onSample(
                FrameSample(
                    timestampNs = image.imageInfo.timestamp,
                    brightness = sum / count,
                    saturatedRatio = saturated.toDouble() / count,
                    motion = motion,
                    rowContrast = rowContrast,
                )
            )
        } finally {
            image.close()
        }
    }
}

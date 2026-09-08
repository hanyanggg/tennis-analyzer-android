package com.yang.tennisanalyzer

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class CourtDetectionResult(
    val points: List<Offset>,
    val confidence: Float,
    val message: String
)

/**
 * First on-device court detector.
 *
 * It searches the selected video frame for the four dominant singles-court
 * boundary lines and validates the resulting perspective trapezoid. The API is
 * intentionally model-shaped: a LiteRT keypoint model can replace the image
 * analyser later without changing the measurement screen.
 */
internal object ClassicalCourtDetector {
    suspend fun detect(
        context: Context,
        videoUri: Uri,
        timeMs: Int
    ): CourtDetectionResult = withContext(Dispatchers.Default) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val source = retriever.getFrameAtTime(
                timeMs.coerceAtLeast(0) * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: return@withContext CourtDetectionResult(
                emptyList(),
                0f,
                "无法读取当前视频画面"
            )

            val width = 360
            val height = (source.height * width.toFloat() / source.width)
                .roundToInt()
                .coerceIn(180, 640)
            val bitmap = Bitmap.createScaledBitmap(source, width, height, true)
            if (bitmap !== source) source.recycle()
            try {
                analyse(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (_: Exception) {
            CourtDetectionResult(emptyList(), 0f, "自动识别失败，请换到球场线更清晰的一帧")
        } finally {
            retriever.release()
        }
    }

    private data class LineCandidate(
        val center: Float,
        val slope: Float,
        val score: Float
    )

    private data class SideCandidate(
        val farX: Float,
        val nearX: Float,
        val score: Float
    )

    private fun analyse(bitmap: Bitmap): CourtDetectionResult {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val lineMap = buildLineMap(pixels, width, height)

        val farBase = findHorizontalLine(
            lineMap, width, height,
            minY = 0.14f, maxY = 0.50f
        )
        val nearBase = findHorizontalLine(
            lineMap, width, height,
            minY = max(0.53f, farBase.center + 0.24f), maxY = 0.94f
        )

        if (nearBase.center - farBase.center < 0.22f) {
            return CourtDetectionResult(emptyList(), 0f, "没有找到完整的近、远端底线")
        }

        val left = findSideLine(
            lineMap, width, height, farBase, nearBase, isLeft = true
        )
        val right = findSideLine(
            lineMap, width, height, farBase, nearBase, isLeft = false
        )

        val farWidth = right.farX - left.farX
        val nearWidth = right.nearX - left.nearX
        val geometryScore = when {
            farWidth !in 0.16f..0.75f -> 0f
            nearWidth !in 0.34f..0.98f -> 0f
            nearWidth < farWidth * 1.08f -> 0.25f
            left.nearX >= left.farX || right.nearX <= right.farX -> 0.35f
            else -> 1f
        }

        val evidence = (
            farBase.score + nearBase.score + left.score + right.score
            ) / 4f
        val confidence = (evidence * geometryScore * 1.45f).coerceIn(0f, 0.96f)

        if (confidence < 0.30f) {
            return CourtDetectionResult(
                emptyList(),
                confidence,
                "球场线不够清晰，建议暂停到无遮挡画面后重试"
            )
        }

        fun baselineY(line: LineCandidate, x: Float): Float =
            line.center + line.slope * (x - 0.5f)

        val points = listOf(
            Offset(left.nearX, baselineY(nearBase, left.nearX)),
            Offset(right.nearX, baselineY(nearBase, right.nearX)),
            Offset(right.farX, baselineY(farBase, right.farX)),
            Offset(left.farX, baselineY(farBase, left.farX))
        ).map { Offset(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }

        val label = when {
            confidence >= 0.70f -> "识别完成，可信度较高"
            confidence >= 0.48f -> "识别完成，建议检查四个角点"
            else -> "识别完成，建议逐点修正"
        }
        return CourtDetectionResult(points, confidence, label)
    }

    private fun buildLineMap(
        pixels: IntArray,
        width: Int,
        height: Int
    ): FloatArray {
        val luminance = FloatArray(pixels.size)
        val whiteness = FloatArray(pixels.size)
        for (index in pixels.indices) {
            val color = pixels[index]
            val red = (color shr 16) and 0xff
            val green = (color shr 8) and 0xff
            val blue = color and 0xff
            val high = max(red, max(green, blue)).toFloat()
            val low = min(red, min(green, blue)).toFloat()
            val light = 0.299f * red + 0.587f * green + 0.114f * blue
            val saturation = if (high < 1f) 0f else (high - low) / high
            luminance[index] = light
            val brightScore = ((light - 115f) / 115f).coerceIn(0f, 1f)
            val neutralScore = ((0.58f - saturation) / 0.45f).coerceIn(0f, 1f)
            whiteness[index] = brightScore * neutralScore
        }

        val result = FloatArray(pixels.size)
        for (y in 2 until height - 2) {
            for (x in 2 until width - 2) {
                val index = y * width + x
                val local = (
                    abs(luminance[index] - luminance[index - 2]) +
                        abs(luminance[index] - luminance[index + 2]) +
                        abs(luminance[index] - luminance[index - width * 2]) +
                        abs(luminance[index] - luminance[index + width * 2])
                    ) / 4f
                val contrastScore = (local / 42f).coerceIn(0f, 1f)
                result[index] = (whiteness[index] * 0.72f + contrastScore * 0.28f)
                    .coerceIn(0f, 1f)
            }
        }
        return result
    }

    private fun findHorizontalLine(
        map: FloatArray,
        width: Int,
        height: Int,
        minY: Float,
        maxY: Float
    ): LineCandidate {
        var best = LineCandidate((minY + maxY) / 2f, 0f, 0f)
        var center = minY
        while (center <= maxY) {
            var slope = -0.10f
            while (slope <= 0.10f) {
                var score = 0f
                var samples = 0
                var x = 0.10f
                while (x <= 0.90f) {
                    val y = center + slope * (x - 0.5f)
                    score += sampleThick(map, width, height, x, y)
                    samples++
                    x += 0.006f
                }
                val average = score / samples.coerceAtLeast(1)
                if (average > best.score) best = LineCandidate(center, slope, average)
                slope += 0.01f
            }
            center += 0.006f
        }
        return best
    }

    private fun findSideLine(
        map: FloatArray,
        width: Int,
        height: Int,
        farBase: LineCandidate,
        nearBase: LineCandidate,
        isLeft: Boolean
    ): SideCandidate {
        val farRange = if (isLeft) 0.16f..0.48f else 0.52f..0.84f
        val nearRange = if (isLeft) 0.01f..0.43f else 0.57f..0.99f
        var best = SideCandidate(
            if (isLeft) 0.36f else 0.64f,
            if (isLeft) 0.14f else 0.86f,
            0f
        )
        var farX = farRange.start
        while (farX <= farRange.endInclusive) {
            var nearX = nearRange.start
            while (nearX <= nearRange.endInclusive) {
                val perspectiveIsValid = if (isLeft) nearX < farX - 0.025f
                else nearX > farX + 0.025f
                if (perspectiveIsValid) {
                    var score = 0f
                    var samples = 0
                    var progress = 0.03f
                    while (progress <= 0.97f) {
                        val x = farX + (nearX - farX) * progress
                        val y = farBase.center +
                            (nearBase.center - farBase.center) * progress
                        score += sampleThick(map, width, height, x, y)
                        samples++
                        progress += 0.012f
                    }
                    val average = score / samples.coerceAtLeast(1)
                    if (average > best.score) best = SideCandidate(farX, nearX, average)
                }
                nearX += 0.012f
            }
            farX += 0.012f
        }
        return best
    }

    private fun sampleThick(
        map: FloatArray,
        width: Int,
        height: Int,
        normalizedX: Float,
        normalizedY: Float
    ): Float {
        val x = (normalizedX * (width - 1)).roundToInt().coerceIn(2, width - 3)
        val y = (normalizedY * (height - 1)).roundToInt().coerceIn(2, height - 3)
        var best = 0f
        for (dy in -2..2) {
            for (dx in -2..2) {
                best = max(best, map[(y + dy) * width + x + dx])
            }
        }
        return best
    }
}

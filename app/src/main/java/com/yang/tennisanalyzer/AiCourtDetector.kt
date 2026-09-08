package com.yang.tennisanalyzer

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.min

/**
 * Runs the trained 14-keypoint court model first and falls back to the original
 * line detector when the frame or the model result is not suitable.
 */
enum class CourtModelMode(
    val assetName: String,
    val displayName: String,
    val guidance: String,
    val minKeypointConfidence: Float
) {
    GENERAL(
        assetName = "court_keypoints_fp32.tflite",
        displayName = "通用模型",
        guidance = "适合电视转播和较高机位",
        minKeypointConfidence = 0.45f
    ),
    LOW_CAMERA(
        assetName = "court_keypoints_low_camera_v1_fp32.tflite",
        displayName = "低机位模型",
        guidance = "适合底线后方的手机或三脚架",
        minKeypointConfidence = 0.30f
    )
}

object CourtAutoDetector {
    private const val INPUT_WIDTH = 640
    private const val INPUT_HEIGHT = 360
    private const val OUTPUT_WIDTH = 160
    private const val OUTPUT_HEIGHT = 90
    private const val KEYPOINT_COUNT = 14
    private const val EXPECTED_ASPECT = 16f / 9f
    private const val MAX_ASPECT_ERROR = 0.06f

    // Order required by the speed screen: near-left, near-right, far-right, far-left.
    private val calibrationChannels = intArrayOf(5, 7, 6, 4)

    suspend fun detect(
        context: Context,
        videoUri: Uri,
        timeMs: Int,
        modelMode: CourtModelMode = CourtModelMode.GENERAL
    ): CourtDetectionResult = withContext(Dispatchers.Default) {
        val aiResult = runCatching {
            detectWithModel(context, videoUri, timeMs, modelMode)
        }.getOrNull()

        if (aiResult != null && aiResult.points.size == 4) {
            return@withContext aiResult
        }

        val fallback = ClassicalCourtDetector.detect(context, videoUri, timeMs)
        if (fallback.points.size == 4) {
            val reason = aiResult?.message ?: "AI 模型暂时无法处理这一帧"
            fallback.copy(message = "$reason，已改用传统线条识别")
        } else {
            aiResult ?: fallback.copy(
                message = "AI 与传统识别都未找到完整球场，请换到无遮挡的 16:9 画面"
            )
        }
    }

    private fun detectWithModel(
        context: Context,
        videoUri: Uri,
        timeMs: Int,
        modelMode: CourtModelMode
    ): CourtDetectionResult {
        val retriever = MediaMetadataRetriever()
        var source: Bitmap? = null
        var resized: Bitmap? = null
        try {
            retriever.setDataSource(context, videoUri)
            source = retriever.getFrameAtTime(
                timeMs.coerceAtLeast(0) * 1_000L,
                MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: return failure("AI 无法读取当前视频画面")

            val aspect = source.width.toFloat() / source.height.coerceAtLeast(1)
            val aspectError = kotlin.math.abs(aspect - EXPECTED_ASPECT) / EXPECTED_ASPECT
            if (aspectError > MAX_ASPECT_ERROR) {
                return failure("AI 当前要求 16:9 横屏画面")
            }

            resized = Bitmap.createScaledBitmap(
                source,
                INPUT_WIDTH,
                INPUT_HEIGHT,
                true
            )
            val input = bitmapToInput(resized)
            val output = ByteBuffer.allocateDirect(
                OUTPUT_WIDTH * OUTPUT_HEIGHT * KEYPOINT_COUNT * Float.SIZE_BYTES
            ).order(ByteOrder.nativeOrder())
            val model = loadModel(context, modelMode.assetName)
            val startMs = SystemClock.elapsedRealtime()
            val options = Interpreter.Options().apply {
                setNumThreads(min(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1)))
            }
            Interpreter(model, options).use { interpreter ->
                interpreter.run(input, output)
            }
            val elapsedMs = SystemClock.elapsedRealtime() - startMs
            val decoded = decodeOutput(output)
            val selected = calibrationChannels.map { decoded[it] }
            val minimumConfidence = selected.minOf { it.confidence }
            val averageConfidence = selected.map { it.confidence }.average().toFloat()
            val points = selected.map { it.point }

            if (minimumConfidence < modelMode.minKeypointConfidence) {
                return failure(
                    "AI 关键点可信度偏低（${(minimumConfidence * 100).toInt()}%）"
                )
            }
            if (!validCourtGeometry(points)) {
                return failure("AI 识别到的球场形状不完整")
            }

            return CourtDetectionResult(
                points = points,
                confidence = averageConfidence.coerceIn(0f, 0.99f),
                message = "${modelMode.displayName}识别完成 · ${elapsedMs}ms · 请检查四个角点"
            )
        } finally {
            if (resized != null && resized !== source && !resized.isRecycled) resized.recycle()
            if (source != null && !source.isRecycled) source.recycle()
            retriever.release()
        }
    }

    private fun loadModel(context: Context, assetName: String): ByteBuffer {
        val bytes = context.assets.open(assetName).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(bytes)
                rewind()
            }
    }

    private fun bitmapToInput(bitmap: Bitmap): ByteBuffer {
        val pixels = IntArray(INPUT_WIDTH * INPUT_HEIGHT)
        bitmap.getPixels(pixels, 0, INPUT_WIDTH, 0, 0, INPUT_WIDTH, INPUT_HEIGHT)
        return ByteBuffer.allocateDirect(pixels.size * 3 * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .apply {
                for (color in pixels) {
                    putFloat(((color shr 16) and 0xff) / 255f)
                    putFloat(((color shr 8) and 0xff) / 255f)
                    putFloat((color and 0xff) / 255f)
                }
                rewind()
            }
    }

    private data class DecodedKeypoint(
        val point: Offset,
        val confidence: Float
    )

    private fun decodeOutput(output: ByteBuffer): List<DecodedKeypoint> {
        output.rewind()
        val bestLogits = FloatArray(KEYPOINT_COUNT) { Float.NEGATIVE_INFINITY }
        val bestRows = IntArray(KEYPOINT_COUNT)
        val bestColumns = IntArray(KEYPOINT_COUNT)

        for (row in 0 until OUTPUT_HEIGHT) {
            for (column in 0 until OUTPUT_WIDTH) {
                for (channel in 0 until KEYPOINT_COUNT) {
                    val logit = output.float
                    if (logit > bestLogits[channel]) {
                        bestLogits[channel] = logit
                        bestRows[channel] = row
                        bestColumns[channel] = column
                    }
                }
            }
        }

        return List(KEYPOINT_COUNT) { channel ->
            DecodedKeypoint(
                point = Offset(
                    x = (bestColumns[channel] + 0.5f) / OUTPUT_WIDTH,
                    y = (bestRows[channel] + 0.5f) / OUTPUT_HEIGHT
                ),
                confidence = sigmoid(bestLogits[channel])
            )
        }
    }

    private fun validCourtGeometry(points: List<Offset>): Boolean {
        if (points.size != 4) return false
        val nearLeft = points[0]
        val nearRight = points[1]
        val farRight = points[2]
        val farLeft = points[3]
        val nearWidth = nearRight.x - nearLeft.x
        val farWidth = farRight.x - farLeft.x
        val nearY = (nearLeft.y + nearRight.y) / 2f
        val farY = (farLeft.y + farRight.y) / 2f
        return nearWidth > 0.20f &&
            farWidth > 0.10f &&
            nearY - farY > 0.12f &&
            nearWidth >= farWidth * 0.85f &&
            points.all { it.x in 0f..1f && it.y in 0f..1f }
    }

    private fun sigmoid(value: Float): Float {
        return if (value >= 0f) {
            (1.0 / (1.0 + exp(-value.toDouble()))).toFloat()
        } else {
            val exponential = exp(value.toDouble())
            (exponential / (1.0 + exponential)).toFloat()
        }
    }

    private fun failure(message: String) = CourtDetectionResult(
        points = emptyList(),
        confidence = 0f,
        message = message
    )
}

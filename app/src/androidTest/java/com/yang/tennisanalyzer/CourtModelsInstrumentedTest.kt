package com.yang.tennisanalyzer

import android.net.Uri
import android.util.Log
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.exp

@RunWith(AndroidJUnit4::class)
class CourtModelsInstrumentedTest {
    private val targetContext
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun bothBundledModelsAreIntactAndLoadable() {
        val expected = mapOf(
            CourtModelMode.GENERAL to ModelExpectation(
                size = 4_175_516,
                sha256 = "c5b02aa6aca016c41db5368044e1cfed743666b4244d55bcdaabc7c6c1ac1c07"
            ),
            CourtModelMode.LOW_CAMERA to ModelExpectation(
                size = 4_182_836,
                sha256 = "e07cc656535db5d947a5627efcfa7d686170d422795da944f313b777151aeeeb"
            )
        )

        expected.forEach { (mode, expectation) ->
            val bytes = targetContext.assets.open(mode.assetName).use { it.readBytes() }
            assertEquals(expectation.size, bytes.size)
            assertEquals(expectation.sha256, bytes.sha256())

            val model = ByteBuffer.allocateDirect(bytes.size)
                .order(ByteOrder.nativeOrder())
                .apply {
                    put(bytes)
                    rewind()
                }
            Interpreter(model).use { interpreter ->
                assertArrayEquals(
                    intArrayOf(1, 360, 640, 3),
                    interpreter.getInputTensor(0).shape()
                )
                assertArrayEquals(
                    intArrayOf(1, 90, 160, 14),
                    interpreter.getOutputTensor(0).shape()
                )
            }
        }
    }

    @Test
    fun lowCameraModelRecognizesUnseenBackViewVideo() = runBlocking {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val videoFile = File(targetContext.cacheDir, "video9_low_camera.mp4")
        testContext.assets.open("video9_low_camera.mp4").use { input ->
            videoFile.outputStream().use { output -> input.copyTo(output) }
        }

        val results = listOf(0, 2_000, 4_000, 6_000, 8_000).map { timeMs ->
            timeMs to CourtAutoDetector.detect(
                context = targetContext,
                videoUri = Uri.fromFile(videoFile),
                timeMs = timeMs,
                modelMode = CourtModelMode.LOW_CAMERA
            ).also { result ->
                Log.i("CourtModelTest", "$timeMs ms: ${result.message}")
            }
        }
        val successful = results.filter { (_, result) ->
            result.points.size == 4 && result.message.contains("低机位模型识别完成")
        }
        val summary = results.joinToString(separator = " | ") { (timeMs, result) ->
            "$timeMs ms: ${result.message}"
        }

        assertTrue(summary, successful.size >= 4)
        assertTrue(
            summary,
            successful.all { (_, result) ->
                result.confidence >= CourtModelMode.LOW_CAMERA.minKeypointConfidence
            }
        )
    }

    @Test
    fun exportedLowCameraFrameIsAccurateOnDevice() {
        val prediction = runModelOnTestImage(
            modelAsset = CourtModelMode.LOW_CAMERA.assetName,
            imageAsset = "video9_frame_000000.jpg"
        )
        val appChannels = intArrayOf(5, 7, 6, 4)
        val appConfidences = appChannels.map { prediction.confidences[it] }
        val appPoints = appChannels.map { prediction.points[it] }
        Log.i(
            "CourtModelTest",
            "extracted frame APP confidences: $appConfidences; points: $appPoints"
        )
        val expected = listOf(
            0.164637f to 0.784378f,
            0.845312f to 0.796187f,
            0.638168f to 0.417710f,
            0.375979f to 0.412847f
        )
        val errors = appPoints.zip(expected).map { (actual, truth) ->
            kotlin.math.hypot(actual.first - truth.first, actual.second - truth.second)
        }
        assertTrue("errors=$errors", errors.average() <= 0.03)
    }

    private fun runModelOnTestImage(modelAsset: String, imageAsset: String): ModelPrediction {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val bitmap = testContext.assets.open(imageAsset).use(BitmapFactory::decodeStream)
        val resized = Bitmap.createScaledBitmap(bitmap, 640, 360, true)
        val pixels = IntArray(640 * 360)
        resized.getPixels(pixels, 0, 640, 0, 0, 640, 360)
        val input = ByteBuffer.allocateDirect(pixels.size * 3 * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .apply {
                pixels.forEach { color ->
                    putFloat(((color shr 16) and 0xff) / 255f)
                    putFloat(((color shr 8) and 0xff) / 255f)
                    putFloat((color and 0xff) / 255f)
                }
                rewind()
            }
        val output = ByteBuffer.allocateDirect(160 * 90 * 14 * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        val modelBytes = targetContext.assets.open(modelAsset).use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(modelBytes.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(modelBytes)
                rewind()
            }
        Interpreter(model).use { interpreter -> interpreter.run(input, output) }
        output.rewind()
        val maxLogits = FloatArray(14) { Float.NEGATIVE_INFINITY }
        val bestRows = IntArray(14)
        val bestColumns = IntArray(14)
        repeat(90) { row ->
            repeat(160) { column ->
                repeat(14) { channel ->
                val logit = output.float
                    if (logit > maxLogits[channel]) {
                        maxLogits[channel] = logit
                        bestRows[channel] = row
                        bestColumns[channel] = column
                    }
                }
            }
        }
        if (resized !== bitmap) resized.recycle()
        bitmap.recycle()
        return ModelPrediction(
            confidences = FloatArray(14) { channel ->
                (1.0 / (1.0 + exp(-maxLogits[channel].toDouble()))).toFloat()
            },
            points = List(14) { channel ->
                (bestColumns[channel] + 0.5f) / 160f to
                    (bestRows[channel] + 0.5f) / 90f
            }
        )
    }

    private fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }

    private data class ModelExpectation(
        val size: Int,
        val sha256: String
    )

    private data class ModelPrediction(
        val confidences: FloatArray,
        val points: List<Pair<Float, Float>>
    )
}

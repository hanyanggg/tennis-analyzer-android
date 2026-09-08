package com.yang.tennisanalyzer

import android.net.Uri
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

private data class BallFrameSample(
    val position: Offset,
    val timeMs: Int
)

private data class SpeedEstimate(
    val speedKmh: Float,
    val distanceMeters: Float,
    val elapsedMs: Int,
    val confidence: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BallSpeedMeasurementScreen(
    videoUri: Uri,
    onBack: () -> Unit,
    onSpeedMeasured: (Float) -> Unit
) {
    val context = LocalContext.current
    var videoView by remember(videoUri) { mutableStateOf<VideoView?>(null) }
    var currentPosition by remember(videoUri) { mutableIntStateOf(0) }
    var duration by remember(videoUri) { mutableIntStateOf(0) }
    var isPlaying by remember(videoUri) { mutableStateOf(false) }
    var videoAspectRatio by remember(videoUri) { mutableFloatStateOf(16f / 9f) }
    var courtPoints by remember(videoUri) { mutableStateOf(listOf<Offset>()) }
    var ballSamples by remember(videoUri) {
        mutableStateOf(listOf<BallFrameSample>())
    }
    var estimate by remember(videoUri) { mutableStateOf<SpeedEstimate?>(null) }
    var isAutoDetecting by remember(videoUri) { mutableStateOf(false) }
    var autoDetection by remember(videoUri) {
        mutableStateOf<CourtDetectionResult?>(null)
    }
    var courtModelMode by remember(videoUri) {
        mutableStateOf(CourtModelMode.LOW_CAMERA)
    }
    var correctionIndex by remember(videoUri) { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(videoView) {
        while (true) {
            videoView?.let { view ->
                currentPosition = view.currentPosition.coerceAtLeast(0)
                if (view.duration > 0) duration = view.duration
                isPlaying = view.isPlaying
            }
            delay(120)
        }
    }

    DisposableEffect(videoUri) {
        onDispose { videoView?.stopPlayback() }
    }

    fun stepFrame(deltaMs: Int) {
        val view = videoView ?: return
        view.pause()
        val target = (view.currentPosition + deltaMs)
            .coerceIn(0, duration.coerceAtLeast(0))
        view.seekTo(target)
        currentPosition = target
        isPlaying = false
    }

    val calibrationLabels = listOf(
        "近端左侧单打底线角",
        "近端右侧单打底线角",
        "远端右侧单打底线角",
        "远端左侧单打底线角"
    )
    val instruction = when {
        correctionIndex != null -> {
            "修正 ${correctionIndex!! + 1}/4：点击${calibrationLabels[correctionIndex!!]}"
        }
        courtPoints.size < 4 -> {
            "标定 ${courtPoints.size + 1}/4：点击${calibrationLabels[courtPoints.size]}"
        }
        ballSamples.isEmpty() -> "暂停在网球清晰的一帧，点击网球中心作为起点"
        ballSamples.size == 1 -> "向前移动若干帧，再点击网球中心作为终点"
        else -> "测算完成，可使用结果或清除后重新测量"
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("球速测算", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "固定机位 · 球场标定 · 地面投影估算",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← 返回", color = CourtGreen, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFFEAF1FC)
                ) {
                    Column(modifier = Modifier.padding(15.dp)) {
                        Text(
                            text = instruction,
                            color = Color(0xFF234F91),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "机位应位于底线后方中央，画面包含完整单打场地，拍摄过程中不要移动或变焦。",
                            color = Muted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "识别模型",
                            color = Color(0xFF234F91),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CourtModelMode.values().forEach { mode ->
                                FilterChip(
                                    selected = courtModelMode == mode,
                                    onClick = {
                                        courtModelMode = mode
                                        autoDetection = null
                                        courtPoints = emptyList()
                                        ballSamples = emptyList()
                                        estimate = null
                                        correctionIndex = null
                                    },
                                    enabled = !isAutoDetecting,
                                    label = {
                                        Text(
                                            mode.displayName,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                )
                            }
                        }
                        Text(
                            text = courtModelMode.guidance,
                            color = Muted,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = {
                                videoView?.pause()
                                isPlaying = false
                                isAutoDetecting = true
                                correctionIndex = null
                                val frameTime = videoView?.currentPosition
                                    ?: currentPosition
                                scope.launch {
                                    val result = CourtAutoDetector.detect(
                                        context.applicationContext,
                                        videoUri,
                                        frameTime,
                                        modelMode = courtModelMode
                                    )
                                    autoDetection = result
                                    isAutoDetecting = false
                                    if (result.points.size == 4) {
                                        courtPoints = result.points
                                        ballSamples = emptyList()
                                        estimate = null
                                    } else {
                                        Toast.makeText(
                                            context,
                                            result.message,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            },
                            enabled = !isAutoDetecting,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CourtGreen
                            )
                        ) {
                            if (isAutoDetecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = BallGreen,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("正在识别球场…")
                            } else {
                                Text(
                                    if (courtPoints.size == 4) "重新自动识别"
                                    else "用${courtModelMode.displayName}识别当前画面",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        autoDetection?.let { result ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = if (result.points.size == 4) {
                                    "${result.message} · ${
                                        (result.confidence * 100).toInt()
                                    }%"
                                } else result.message,
                                color = if (result.points.size == 4) CourtGreen
                                else Color(0xFFB26A00),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Black
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(videoAspectRatio)
                    ) {
                        AndroidView(
                            factory = { viewContext ->
                                VideoView(viewContext).apply {
                                    setVideoURI(videoUri)
                                    setOnPreparedListener { player ->
                                        duration = player.duration.coerceAtLeast(0)
                                        if (player.videoWidth > 0 && player.videoHeight > 0) {
                                            videoAspectRatio = (
                                                player.videoWidth.toFloat() /
                                                    player.videoHeight.toFloat()
                                                ).coerceIn(0.56f, 2.4f)
                                        }
                                        player.isLooping = false
                                        start()
                                        isPlaying = true
                                    }
                                    setOnErrorListener { _, _, _ ->
                                        Toast.makeText(
                                            context,
                                            "视频暂时无法播放",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        true
                                    }
                                    videoView = this
                                }
                            },
                            update = { videoView = it },
                            modifier = Modifier.matchParentSize()
                        )

                        MeasurementOverlay(
                            courtPoints = courtPoints,
                            ballSamples = ballSamples,
                            onTap = { normalizedPoint ->
                                videoView?.pause()
                                isPlaying = false
                                val activeCorrection = correctionIndex
                                when {
                                    activeCorrection != null -> {
                                        courtPoints = courtPoints.toMutableList().also {
                                            it[activeCorrection] = normalizedPoint
                                        }
                                        correctionIndex = if (activeCorrection < 3) {
                                            activeCorrection + 1
                                        } else null
                                        ballSamples = emptyList()
                                        estimate = null
                                    }
                                    courtPoints.size < 4 -> {
                                        courtPoints = courtPoints + normalizedPoint
                                    }
                                    ballSamples.size < 2 -> {
                                        val newSamples = ballSamples + BallFrameSample(
                                            position = normalizedPoint,
                                            timeMs = videoView?.currentPosition
                                                ?: currentPosition
                                        )
                                        ballSamples = newSamples
                                        if (newSamples.size == 2) {
                                            estimate = estimateBallSpeed(
                                                courtPoints,
                                                newSamples
                                            )
                                            if (estimate == null) {
                                                Toast.makeText(
                                                    context,
                                                    "两个取点时间太接近或标定无效，请重新测量",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CardWhite
                ) {
                    Column(modifier = Modifier.padding(15.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatSpeedTime(currentPosition),
                                color = Ink,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "标定点 ${courtPoints.size}/4 · 球点 ${ballSamples.size}/2",
                                color = Muted,
                                fontSize = 11.sp
                            )
                        }
                        Slider(
                            value = currentPosition
                                .coerceIn(0, duration.coerceAtLeast(0))
                                .toFloat(),
                            onValueChange = {
                                currentPosition = it.toInt()
                                videoView?.seekTo(currentPosition)
                            },
                            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                            enabled = duration > 0,
                            colors = SliderDefaults.colors(
                                thumbColor = BallGreen,
                                activeTrackColor = CourtGreen
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { stepFrame(-33) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(13.dp)
                            ) {
                                Text("−1 帧")
                            }
                            Button(
                                onClick = {
                                    if (videoView?.isPlaying == true) {
                                        videoView?.pause()
                                        isPlaying = false
                                    } else {
                                        videoView?.start()
                                        isPlaying = true
                                    }
                                },
                                modifier = Modifier.weight(1.2f).height(44.dp),
                                shape = RoundedCornerShape(13.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CourtGreen
                                )
                            ) {
                                Text(if (isPlaying) "暂停" else "播放")
                            }
                            OutlinedButton(
                                onClick = { stepFrame(33) },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(13.dp)
                            ) {
                                Text("+1 帧")
                            }
                        }
                    }
                }
            }

            estimate?.let { result ->
                item {
                    SpeedResultCard(
                        result = result,
                        onUse = { onSpeedMeasured(result.speedKmh) }
                    )
                }
            }

            item {
                if (courtPoints.size == 4) {
                    OutlinedButton(
                        onClick = {
                            correctionIndex = if (correctionIndex == null) 0 else null
                            ballSamples = emptyList()
                            estimate = null
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            if (correctionIndex == null) "逐点修正 AI 标定"
                            else "取消点位修正"
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            ballSamples = emptyList()
                            estimate = null
                        },
                        enabled = ballSamples.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("重选网球")
                    }
                    OutlinedButton(
                        onClick = {
                            courtPoints = emptyList()
                            ballSamples = emptyList()
                            estimate = null
                            autoDetection = null
                            correctionIndex = null
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("重新标定")
                    }
                }
            }

            item {
                Text(
                    text = "说明：当前为单摄像头地面投影估算，空中高度、运动模糊和机位偏差都会影响结果，精度低于雷达测速。",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MeasurementOverlay(
    courtPoints: List<Offset>,
    ballSamples: List<BallFrameSample>,
    onTap: (Offset) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(courtPoints.size, ballSamples.size) {
                detectTapGestures { point ->
                    if (size.width > 0 && size.height > 0) {
                        onTap(
                            Offset(
                                x = point.x / size.width.toFloat(),
                                y = point.y / size.height.toFloat()
                            )
                        )
                    }
                }
            }
    ) {
        val displayCourt = courtPoints.map {
            Offset(it.x * size.width, it.y * size.height)
        }
        if (displayCourt.size >= 2) {
            for (index in 0 until displayCourt.lastIndex) {
                drawLine(
                    color = BallGreen,
                    start = displayCourt[index],
                    end = displayCourt[index + 1],
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
            }
        }
        if (displayCourt.size == 4) {
            drawLine(
                color = BallGreen,
                start = displayCourt[3],
                end = displayCourt[0],
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }
        displayCourt.forEachIndexed { index, point ->
            drawCircle(Color.White, radius = 12f, center = point)
            drawCircle(
                color = if (index < 2) BallGreen else Color(0xFFFFB74D),
                radius = 8f,
                center = point
            )
        }

        val displayBall = ballSamples.map {
            Offset(it.position.x * size.width, it.position.y * size.height)
        }
        if (displayBall.size == 2) {
            drawLine(
                color = Color(0xFFFF7043),
                start = displayBall[0],
                end = displayBall[1],
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
        }
        displayBall.forEachIndexed { index, point ->
            drawCircle(Color.White, radius = 13f, center = point)
            drawCircle(
                color = if (index == 0) Color(0xFF42A5F5) else Color(0xFFFF7043),
                radius = 9f,
                center = point
            )
        }
    }
}

@Composable
private fun SpeedResultCard(result: SpeedEstimate, onUse: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = CourtGreen
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "地面投影估算球速",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp
            )
            Text(
                text = "%.1f km/h".format(result.speedKmh),
                color = BallGreen,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 34.sp
            )
            Text(
                text = "距离 %.2f m · 时间 %d ms · 可信度%s"
                    .format(
                        result.distanceMeters,
                        result.elapsedMs,
                        result.confidence
                    ),
                color = Color.White,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onUse,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BallGreen,
                    contentColor = CourtGreen
                )
            ) {
                Text("使用这个结果", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun estimateBallSpeed(
    courtPoints: List<Offset>,
    samples: List<BallFrameSample>
): SpeedEstimate? {
    if (courtPoints.size != 4 || samples.size != 2) return null
    val transform = calculateCourtTransform(courtPoints) ?: return null
    val first = transformPoint(transform, samples[0].position) ?: return null
    val second = transformPoint(transform, samples[1].position) ?: return null
    val elapsedMs = abs(samples[1].timeMs - samples[0].timeMs)
    if (elapsedMs < 20) return null

    val distance = hypot(second.first - first.first, second.second - first.second)
    val speedKmh = distance / (elapsedMs / 1000.0) * 3.6
    if (!speedKmh.isFinite() || speedKmh <= 0.0) return null
    val confidence = when {
        elapsedMs >= 200 && speedKmh in 10.0..300.0 -> "较高"
        elapsedMs >= 80 && speedKmh in 10.0..320.0 -> "中"
        else -> "低"
    }
    return SpeedEstimate(
        speedKmh = speedKmh.toFloat(),
        distanceMeters = distance.toFloat(),
        elapsedMs = elapsedMs,
        confidence = confidence
    )
}

private fun calculateCourtTransform(source: List<Offset>): DoubleArray? {
    val destination = listOf(
        0.0 to 0.0,
        8.23 to 0.0,
        8.23 to 23.77,
        0.0 to 23.77
    )
    val matrix = Array(8) { DoubleArray(9) }
    source.forEachIndexed { index, point ->
        val x = point.x.toDouble()
        val y = point.y.toDouble()
        val targetX = destination[index].first
        val targetY = destination[index].second
        val firstRow = index * 2
        val secondRow = firstRow + 1

        matrix[firstRow][0] = x
        matrix[firstRow][1] = y
        matrix[firstRow][2] = 1.0
        matrix[firstRow][6] = -targetX * x
        matrix[firstRow][7] = -targetX * y
        matrix[firstRow][8] = targetX

        matrix[secondRow][3] = x
        matrix[secondRow][4] = y
        matrix[secondRow][5] = 1.0
        matrix[secondRow][6] = -targetY * x
        matrix[secondRow][7] = -targetY * y
        matrix[secondRow][8] = targetY
    }

    for (column in 0 until 8) {
        var pivotRow = column
        for (row in column + 1 until 8) {
            if (abs(matrix[row][column]) > abs(matrix[pivotRow][column])) {
                pivotRow = row
            }
        }
        if (abs(matrix[pivotRow][column]) < 1e-10) return null
        val temporary = matrix[column]
        matrix[column] = matrix[pivotRow]
        matrix[pivotRow] = temporary

        val pivot = matrix[column][column]
        for (entry in column until 9) matrix[column][entry] /= pivot

        for (row in 0 until 8) {
            if (row == column) continue
            val factor = matrix[row][column]
            for (entry in column until 9) {
                matrix[row][entry] -= factor * matrix[column][entry]
            }
        }
    }
    return DoubleArray(8) { matrix[it][8] }
}

private fun transformPoint(
    transform: DoubleArray,
    point: Offset
): Pair<Double, Double>? {
    val x = point.x.toDouble()
    val y = point.y.toDouble()
    val denominator = transform[6] * x + transform[7] * y + 1.0
    if (abs(denominator) < 1e-10) return null
    val courtX = (transform[0] * x + transform[1] * y + transform[2]) /
        denominator
    val courtY = (transform[3] * x + transform[4] * y + transform[5]) /
        denominator
    return courtX to courtY
}

private fun formatSpeedTime(positionMs: Int): String {
    val totalSeconds = positionMs.coerceAtLeast(0) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val milliseconds = positionMs.coerceAtLeast(0) % 1000
    return "%02d:%02d.%03d".format(minutes, seconds, milliseconds)
}

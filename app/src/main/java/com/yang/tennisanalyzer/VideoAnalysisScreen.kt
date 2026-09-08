package com.yang.tennisanalyzer

import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoAnalysisScreen(
    videoUri: Uri,
    onBack: () -> Unit,
    onPickAnother: () -> Unit,
    initialMarkedFrames: List<Int> = emptyList(),
    onSaveTraining: (String, String, String, List<Int>, Int, Float?) -> Unit
) {
    val context = LocalContext.current
    var videoView by remember(videoUri) { mutableStateOf<VideoView?>(null) }
    var mediaPlayer by remember(videoUri) { mutableStateOf<MediaPlayer?>(null) }
    var currentPosition by remember(videoUri) { mutableIntStateOf(0) }
    var duration by remember(videoUri) { mutableIntStateOf(0) }
    var isPlaying by remember(videoUri) { mutableStateOf(false) }
    var playbackSpeed by remember(videoUri) { mutableFloatStateOf(1f) }
    var markedFrames by remember(videoUri, initialMarkedFrames) {
        mutableStateOf(initialMarkedFrames)
    }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showSpeedMeasurement by remember { mutableStateOf(false) }
    var measuredSpeedKmh by remember(videoUri) { mutableStateOf<Float?>(null) }

    if (showSpeedMeasurement) {
        BallSpeedMeasurementScreen(
            videoUri = videoUri,
            onBack = { showSpeedMeasurement = false },
            onSpeedMeasured = { speed ->
                measuredSpeedKmh = speed
                showSpeedMeasurement = false
                Toast.makeText(
                    context,
                    "球速结果已带回本次训练",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
        return
    }

    LaunchedEffect(videoView) {
        while (true) {
            videoView?.let { view ->
                currentPosition = view.currentPosition.coerceAtLeast(0)
                if (view.duration > 0) duration = view.duration
                isPlaying = view.isPlaying
            }
            delay(150)
        }
    }

    DisposableEffect(videoUri) {
        onDispose { videoView?.stopPlayback() }
    }

    fun changeSpeed(speed: Float) {
        playbackSpeed = speed
        applyPlaybackSpeed(mediaPlayer, speed)
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

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "视频分析",
                            color = Ink,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "慢放、逐帧查看并收藏关键动作",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = "← 返回",
                            color = CourtGreen,
                            fontWeight = FontWeight.Bold
                        )
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black,
                    shadowElevation = 2.dp
                ) {
                    AndroidView(
                        factory = { viewContext ->
                            VideoView(viewContext).apply {
                                setVideoURI(videoUri)
                                setOnPreparedListener { player ->
                                    mediaPlayer = player
                                    duration = player.duration.coerceAtLeast(0)
                                    player.isLooping = false
                                    applyPlaybackSpeed(player, playbackSpeed)
                                    start()
                                    isPlaying = true
                                }
                                setOnCompletionListener {
                                    currentPosition = duration
                                    isPlaying = false
                                }
                                setOnErrorListener { _, _, _ ->
                                    Toast.makeText(
                                        context,
                                        "这个视频暂时无法播放，请换一个试试",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    true
                                }
                                tag = videoUri.toString()
                                videoView = this
                            }
                        },
                        update = { view ->
                            if (view.tag != videoUri.toString()) {
                                view.setVideoURI(videoUri)
                                view.tag = videoUri.toString()
                            }
                            videoView = view
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                    )
                }
            }

            item {
                PlaybackControlCard(
                    currentPosition = currentPosition,
                    duration = duration,
                    isPlaying = isPlaying,
                    playbackSpeed = playbackSpeed,
                    onSeek = { target ->
                        currentPosition = target
                        videoView?.seekTo(target)
                    },
                    onStepBack = { stepFrame(-33) },
                    onTogglePlayback = {
                        val view = videoView
                        if (view?.isPlaying == true) {
                            view.pause()
                            isPlaying = false
                        } else {
                            if (currentPosition >= duration - 100) view?.seekTo(0)
                            applyPlaybackSpeed(mediaPlayer, playbackSpeed)
                            view?.start()
                            isPlaying = true
                        }
                    },
                    onStepForward = { stepFrame(33) },
                    onSpeedSelected = ::changeSpeed
                )
            }

            item {
                BallSpeedEntryCard(
                    measuredSpeedKmh = measuredSpeedKmh,
                    onStart = {
                        videoView?.pause()
                        isPlaying = false
                        showSpeedMeasurement = true
                    }
                )
            }

            item {
                KeyFramesCard(
                    markedFrames = markedFrames,
                    onAdd = {
                        val position = videoView?.currentPosition ?: currentPosition
                        if (markedFrames.none { abs(it - position) < 150 }) {
                            markedFrames = (markedFrames + position).sorted()
                        } else {
                            Toast.makeText(
                                context,
                                "这个位置已经收藏",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onView = { position ->
                        videoView?.pause()
                        videoView?.seekTo(position)
                        currentPosition = position
                        isPlaying = false
                    },
                    onDelete = { index ->
                        markedFrames = markedFrames.filterIndexed { itemIndex, _ ->
                            itemIndex != index
                        }
                    }
                )
            }

            item {
                Button(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CourtGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text("保存本次训练", fontWeight = FontWeight.ExtraBold)
                }
            }

            item {
                OutlinedButton(
                    onClick = onPickAnother,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CourtGreen
                    )
                ) {
                    Text("换一个视频", fontWeight = FontWeight.Bold)
                }
            }

            item { SupportedFeaturesCard() }
        }
    }

    if (showSaveDialog) {
        SaveTrainingDialog(
            onDismiss = { showSaveDialog = false },
            onSave = { title, strokeType, notes ->
                onSaveTraining(
                    title,
                    strokeType,
                    notes,
                    markedFrames,
                    duration,
                    measuredSpeedKmh
                )
                showSaveDialog = false
            }
        )
    }
}

@Composable
private fun BallSpeedEntryCard(
    measuredSpeedKmh: Float?,
    onStart: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFEAF1FC)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "球速测算",
                    color = Ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Text(
                    text = measuredSpeedKmh?.let {
                        "当前结果：%.1f km/h".format(it)
                    } ?: "固定机位、球场标定与跨帧取点",
                    color = if (measuredSpeedKmh != null) {
                        Color(0xFF2C64B7)
                    } else {
                        Muted
                    },
                    fontWeight = if (measuredSpeedKmh != null) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    },
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2C64B7),
                    contentColor = Color.White
                )
            ) {
                Text(
                    if (measuredSpeedKmh == null) "开始测算" else "重新测算",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun SaveTrainingDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var strokeType by remember { mutableStateOf("发球") }
    val strokeTypes = listOf("发球", "正手", "反手", "截击")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存训练", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("训练标题") },
                    placeholder = { Text("例如：周三发球练习") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("动作类型", color = Ink, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    strokeTypes.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { type ->
                                FilterChip(
                                    selected = strokeType == type,
                                    onClick = { strokeType = type },
                                    label = { Text(type) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("训练备注") },
                    placeholder = { Text("记录动作感受和改进重点") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = title.trim().ifEmpty {
                        "$strokeType 训练"
                    }
                    onSave(finalTitle, strokeType, notes.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun PlaybackControlCard(
    currentPosition: Int,
    duration: Int,
    isPlaying: Boolean,
    playbackSpeed: Float,
    onSeek: (Int) -> Unit,
    onStepBack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onStepForward: () -> Unit,
    onSpeedSelected: (Float) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = CardWhite,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatVideoTime(currentPosition) +
                        " / " + formatVideoTime(duration),
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = String.format("%.2f×", playbackSpeed),
                    color = FreshGreen,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }

            Slider(
                value = currentPosition
                    .coerceIn(0, duration.coerceAtLeast(0))
                    .toFloat(),
                onValueChange = { onSeek(it.toInt()) },
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                enabled = duration > 0,
                colors = SliderDefaults.colors(
                    thumbColor = BallGreen,
                    activeTrackColor = CourtGreen,
                    inactiveTrackColor = Color(0xFFDDE6DD)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onStepBack,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("−1 帧", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onTogglePlayback,
                    modifier = Modifier.weight(1.15f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CourtGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        if (isPlaying) "暂停" else "播放",
                        fontWeight = FontWeight.Bold
                    )
                }
                OutlinedButton(
                    onClick = onStepForward,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("+1 帧", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "播放速度",
                color = Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlaybackSpeedButton(
                    label = "0.25×",
                    selected = playbackSpeed == 0.25f,
                    onClick = { onSpeedSelected(0.25f) },
                    modifier = Modifier.weight(1f)
                )
                PlaybackSpeedButton(
                    label = "0.5×",
                    selected = playbackSpeed == 0.5f,
                    onClick = { onSpeedSelected(0.5f) },
                    modifier = Modifier.weight(1f)
                )
                PlaybackSpeedButton(
                    label = "1.0×",
                    selected = playbackSpeed == 1f,
                    onClick = { onSpeedSelected(1f) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "逐帧按钮每次移动约 1/30 秒，适合检查击球前后的动作变化。",
                color = Muted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun PlaybackSpeedButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(42.dp),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FreshGreen,
                contentColor = Color.White
            )
        ) {
            Text(label, fontWeight = FontWeight.ExtraBold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(42.dp),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CourtGreen)
        ) {
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun KeyFramesCard(
    markedFrames: List<Int>,
    onAdd: () -> Unit,
    onView: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = CardWhite
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "关键帧",
                        color = Ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "收藏击球、引拍或随挥瞬间",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
                Button(
                    onClick = onAdd,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BallGreen,
                        contentColor = CourtGreen
                    )
                ) {
                    Text("+ 收藏当前帧", fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(Modifier.height(12.dp))
            if (markedFrames.isEmpty()) {
                Text(
                    text = "暂停在关键动作处，然后点击“收藏当前帧”。",
                    color = Muted,
                    fontSize = 12.sp
                )
            } else {
                markedFrames.forEachIndexed { index, position ->
                    if (index > 0) HorizontalDivider(color = Color(0xFFE5EAE5))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "关键帧 ${index + 1}",
                            color = Ink,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatVideoTime(position),
                            color = FreshGreen,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { onView(position) }) { Text("查看") }
                        TextButton(onClick = { onDelete(index) }) {
                            Text("删除", color = Color(0xFFB54B45))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SupportedFeaturesCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFE7EFDF)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "本页已支持",
                color = Ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            AnalysisFeatureRow("0.25× / 0.5× 慢速播放")
            AnalysisFeatureRow("前进和后退一帧")
            AnalysisFeatureRow("收藏并跳转到关键帧")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "下一步：画线与三点关节角度",
                color = CourtGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun AnalysisFeatureRow(text: String) {
    Text(
        text = "•  $text",
        modifier = Modifier.padding(vertical = 4.dp),
        color = Muted,
        fontSize = 13.sp
    )
}

private fun applyPlaybackSpeed(player: MediaPlayer?, speed: Float): Boolean {
    if (player == null) return false
    return try {
        player.playbackParams = player.playbackParams.setSpeed(speed)
        true
    } catch (_: Exception) {
        false
    }
}

private fun formatVideoTime(positionMs: Int): String {
    val totalSeconds = positionMs.coerceAtLeast(0) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

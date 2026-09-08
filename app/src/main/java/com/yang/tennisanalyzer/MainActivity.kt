package com.yang.tennisanalyzer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.yang.tennisanalyzer.ui.theme.TennisAnalyzerTheme

internal val AppBackground = Color(0xFFF4F7F1)
internal val CourtGreen = Color(0xFF123D2C)
internal val FreshGreen = Color(0xFF2E7D52)
internal val BallGreen = Color(0xFFC8F34A)
internal val Ink = Color(0xFF17211B)
internal val Muted = Color(0xFF68736C)
internal val CardWhite = Color(0xFFFFFFFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TennisAnalyzerTheme {
                TennisAnalyzerApp()
            }
        }
    }
}

@Composable
fun TennisAnalyzerApp() {
    val context = LocalContext.current
    val repository = remember(context) { LocalDataRepository(context) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedTraining by remember { mutableStateOf<TrainingRecord?>(null) }
    var showMatchTracker by remember { mutableStateOf(false) }
    var trainingRecords by remember {
        mutableStateOf(repository.loadTrainingRecords())
    }
    var matchRecords by remember {
        mutableStateOf(repository.loadMatchRecords())
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedTraining = null
            selectedVideoUri = uri
        }
    }

    val openVideoPicker = {
        selectedTraining = null
        videoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    val showComingSoon: (String) -> Unit = { feature ->
        Toast.makeText(context, feature + "将在下一阶段接入", Toast.LENGTH_SHORT).show()
    }

    if (selectedVideoUri != null) {
        VideoAnalysisScreen(
            videoUri = selectedVideoUri!!,
            onBack = {
                selectedVideoUri = null
                selectedTraining = null
            },
            onPickAnother = openVideoPicker,
            initialMarkedFrames = selectedTraining?.keyFrames ?: emptyList(),
            onSaveTraining = {
                    title,
                    strokeType,
                    notes,
                    keyFrames,
                    duration,
                    estimatedSpeedKmh ->
                val record = TrainingRecord(
                    id = System.currentTimeMillis().toString(),
                    createdAt = System.currentTimeMillis(),
                    title = title,
                    strokeType = strokeType,
                    notes = notes,
                    videoUri = selectedVideoUri.toString(),
                    keyFrames = keyFrames,
                    durationMs = duration,
                    estimatedSpeedKmh = estimatedSpeedKmh
                )
                trainingRecords = repository.addTrainingRecord(record)
                Toast.makeText(context, "训练已保存", Toast.LENGTH_SHORT).show()
            }
        )
    } else if (showMatchTracker) {
        MatchTrackerScreen(
            onBack = { showMatchTracker = false },
            onSaveMatch = { record ->
                matchRecords = repository.addMatchRecord(record)
                showMatchTracker = false
                selectedTab = 2
            }
        )
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = AppBackground,
            bottomBar = {
                AppBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { index ->
                        when (index) {
                            1 -> {
                                selectedTab = 0
                                openVideoPicker()
                            }
                            3 -> showComingSoon("我的页面")
                            else -> selectedTab = index
                        }
                    }
                )
            }
        ) { innerPadding ->
            when (selectedTab) {
                2 -> RecordsScreen(
                    trainingRecords = trainingRecords,
                    matchRecords = matchRecords,
                    onOpenTraining = { record ->
                        selectedTraining = record
                        selectedVideoUri = Uri.parse(record.videoUri)
                    },
                    modifier = Modifier.padding(innerPadding)
                )
                else -> HomeScreen(
                    modifier = Modifier.padding(innerPadding),
                    trainingCount = trainingRecords.size,
                    trainingMinutes = trainingRecords.sumOf { it.durationMs } / 60_000,
                    onStartAnalysis = openVideoPicker,
                    onFeatureClick = { feature ->
                        when (feature) {
                            "视频动作分析" -> openVideoPicker()
                            "比赛数据记录" -> showMatchTracker = true
                            "历史训练" -> selectedTab = 2
                            else -> showComingSoon(feature)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyVideoAnalysisScreen(
    videoUri: Uri,
    onBack: () -> Unit,
    onPickAnother: () -> Unit,
    onStartMarking: () -> Unit
) {
    val context = LocalContext.current

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
                            text = "播放、查看并准备标注",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text(
                            text = "‹ 返回",
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
                                val controller = MediaController(viewContext)
                                controller.setAnchorView(this)
                                setMediaController(controller)
                                setVideoURI(videoUri)
                                setOnPreparedListener { player ->
                                    player.isLooping = false
                                    start()
                                    controller.show(3000)
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
                            }
                        },
                        update = { videoView ->
                            if (videoView.tag != videoUri.toString()) {
                                videoView.setVideoURI(videoUri)
                                videoView.tag = videoUri.toString()
                                videoView.start()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 10f)
                    )
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CardWhite
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = CircleShape,
                            color = BallGreen.copy(alpha = 0.45f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "▶",
                                    color = CourtGreen,
                                    fontSize = 16.sp
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "播放控制已就绪",
                                color = Ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "点击画面可显示播放、暂停和进度拖动",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickAnother,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = CourtGreen
                        )
                    ) {
                        Text("换一个视频", fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onStartMarking,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CourtGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Text("开始标注", fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFFE7EFDF)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "接下来会加入",
                            color = Ink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        NextStepRow("0.25× / 0.5× 慢速播放")
                        NextStepRow("前进和后退一帧")
                        NextStepRow("画线与三点关节角度")
                        NextStepRow("收藏击球关键帧")
                    }
                }
            }
        }
    }
}

@Composable
private fun NextStepRow(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(FreshGreen)
        )
        Spacer(Modifier.width(10.dp))
        Text(text = text, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    trainingCount: Int,
    trainingMinutes: Int,
    onStartAnalysis: () -> Unit,
    onFeatureClick: (String) -> Unit
) {
    val features = listOf(
        FeatureItem(
            icon = "影",
            title = "视频动作分析",
            description = "慢放、逐帧与关键帧收藏",
            accent = FreshGreen
        ),
        FeatureItem(
            icon = "赛",
            title = "比赛数据记录",
            description = "比分、回合与非受迫失误统计",
            accent = Color(0xFF2C64B7)
        ),
        FeatureItem(
            icon = "史",
            title = "历史训练",
            description = "回看关键帧、备注与改进计划",
            accent = Color(0xFFD17A22)
        )
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { WelcomeHeader() }
        item { AnalysisHero(onStartAnalysis) }
        item {
            Text(
                text = "本周概览",
                color = Ink,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(trainingCount.toString(), "训练次数", Modifier.weight(1f))
                StatCard(trainingCount.toString(), "已分析", Modifier.weight(1f))
                StatCard("$trainingMinutes 分钟", "训练时长", Modifier.weight(1f))
            }
        }
        item {
            Text(
                text = "分析工具",
                color = Ink,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                features.forEach { feature ->
                    FeatureCard(
                        item = feature,
                        onClick = { onFeatureClick(feature.title) }
                    )
                }
            }
        }
        item {
            PracticeTip()
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun WelcomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "你好，网球爱好者",
                color = Ink,
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "今天也来练出一组好球",
                color = Muted,
                fontSize = 14.sp
            )
        }
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = CourtGreen
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Y",
                    color = BallGreen,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AnalysisHero(onStartAnalysis: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(CourtGreen, Color(0xFF216B49))
                )
            )
            .padding(22.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TennisBallMark()
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "开始一次训练分析",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "导入或拍摄视频，找到动作提升点",
                        color = Color.White.copy(alpha = 0.76f),
                        fontSize = 13.sp
                    )
                }
                Surface(
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "真机版",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onStartAnalysis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BallGreen,
                    contentColor = CourtGreen
                )
            ) {
                Text(
                    text = "导入或拍摄视频",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun TennisBallMark() {
    Canvas(modifier = Modifier.size(48.dp)) {
        drawCircle(color = BallGreen)
        val lineStyle = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
        val leftSeam = Path().apply {
            moveTo(size.width * 0.32f, 0f)
            quadraticBezierTo(
                size.width * 0.05f,
                size.height * 0.5f,
                size.width * 0.32f,
                size.height
            )
        }
        val rightSeam = Path().apply {
            moveTo(size.width * 0.68f, 0f)
            quadraticBezierTo(
                size.width * 0.95f,
                size.height * 0.5f,
                size.width * 0.68f,
                size.height
            )
        }
        drawPath(leftSeam, Color.White, style = lineStyle)
        drawPath(rightSeam, Color.White, style = lineStyle)
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = CardWhite,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color = CourtGreen,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(3.dp))
            Text(text = label, color = Muted, fontSize = 12.sp)
        }
    }
}

private data class FeatureItem(
    val icon: String,
    val title: String,
    val description: String,
    val accent: Color
)

@Composable
private fun FeatureCard(item: FeatureItem, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = CardWhite,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = item.accent.copy(alpha = 0.13f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = item.icon,
                        color = item.accent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = item.description,
                    color = Muted,
                    fontSize = 12.sp
                )
            }
            Text(text = "›", color = Muted, fontSize = 28.sp)
        }
    }
}

@Composable
private fun PracticeTip() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFE7EFDF)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(text = "TIP", color = FreshGreen, fontWeight = FontWeight.Black, fontSize = 12.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "拍摄小提示",
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "手机尽量固定在身体侧前方，确保全身和球拍都在画面中。",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private data class NavItem(val label: String, val symbol: String)

@Composable
private fun AppBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val items = listOf(
        NavItem("首页", "⌂"),
        NavItem("分析", "◎"),
        NavItem("记录", "▤"),
        NavItem("我的", "○")
    )

    NavigationBar(
        containerColor = CardWhite,
        tonalElevation = 0.dp
    ) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = {
                    Text(
                        text = item.symbol,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                label = { Text(item.label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CourtGreen,
                    selectedTextColor = CourtGreen,
                    indicatorColor = BallGreen.copy(alpha = 0.42f),
                    unselectedIconColor = Muted,
                    unselectedTextColor = Muted
                )
            )
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun TennisAnalyzerPreview() {
    TennisAnalyzerTheme {
        TennisAnalyzerApp()
    }
}

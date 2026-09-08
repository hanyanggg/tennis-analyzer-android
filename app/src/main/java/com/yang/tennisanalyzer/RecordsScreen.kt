package com.yang.tennisanalyzer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordsScreen(
    trainingRecords: List<TrainingRecord>,
    matchRecords: List<MatchRecord>,
    onOpenTraining: (TrainingRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text(
            text = "记录中心",
            color = Ink,
            fontSize = 25.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "持续积累，才能看到真正的进步",
            color = Muted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RecordSectionButton(
                label = "训练记录 ${trainingRecords.size}",
                selected = selectedSection == 0,
                onClick = { selectedSection = 0 },
                modifier = Modifier.weight(1f)
            )
            RecordSectionButton(
                label = "比赛记录 ${matchRecords.size}",
                selected = selectedSection == 1,
                onClick = { selectedSection = 1 },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(14.dp))

        if (selectedSection == 0) {
            if (trainingRecords.isEmpty()) {
                EmptyRecordsCard(
                    title = "还没有训练记录",
                    description = "选择一段网球视频，分析后点击“保存本次训练”。"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 18.dp)
                ) {
                    items(trainingRecords, key = { it.id }) { record ->
                        TrainingRecordCard(record, onOpenTraining)
                    }
                }
            }
        } else {
            if (matchRecords.isEmpty()) {
                EmptyRecordsCard(
                    title = "还没有比赛记录",
                    description = "从首页进入比赛数据记录，完成后保存比赛。"
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 18.dp)
                ) {
                    items(matchRecords, key = { it.id }) { record ->
                        MatchRecordCard(record)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordSectionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(44.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CourtGreen,
                contentColor = Color.White
            )
        ) {
            Text(label, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(44.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = CourtGreen)
        ) {
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TrainingRecordCard(
    record: TrainingRecord,
    onOpenTraining: (TrainingRecord) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardWhite,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = BallGreen.copy(alpha = 0.42f)
                ) {
                    Text(
                        text = record.strokeType,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = CourtGreen,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.title,
                        color = Ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = formatRecordDate(record.createdAt),
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "视频 ${formatRecordDuration(record.durationMs)} · " +
                    "关键帧 ${record.keyFrames.size} 个",
                color = FreshGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            record.estimatedSpeedKmh?.let { speed ->
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "估算球速 %.1f km/h".format(speed),
                    color = Color(0xFF2C64B7),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
            if (record.notes.isNotBlank()) {
                Spacer(Modifier.height(7.dp))
                Text(record.notes, color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onOpenTraining(record) },
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CourtGreen)
            ) {
                Text("重新打开分析", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MatchRecordCard(record: MatchRecord) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardWhite,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "我  ${record.playerGames} : ${record.opponentGames}  对手",
                        color = Ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = formatRecordDate(record.createdAt),
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
                Text(
                    text = "总得分 ${record.playerPointsWon}-${record.opponentPointsWon}",
                    color = FreshGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            MatchRecordStatRow("Ace", record.playerAces, record.opponentAces)
            MatchRecordStatRow("制胜分", record.playerWinners, record.opponentWinners)
            MatchRecordStatRow("双误", record.playerDoubleFaults, record.opponentDoubleFaults)
            MatchRecordStatRow(
                "非受迫失误",
                record.playerUnforcedErrors,
                record.opponentUnforcedErrors
            )
        }
    }
}

@Composable
private fun MatchRecordStatRow(label: String, player: Int, opponent: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(player.toString(), color = CourtGreen, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 12.sp)
        Text(opponent.toString(), color = Ink, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyRecordsCard(title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = CardWhite
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎾", fontSize = 34.sp)
            Spacer(Modifier.height(10.dp))
            Text(title, color = Ink, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(5.dp))
            Text(description, color = Muted, fontSize = 12.sp)
        }
    }
}

private fun formatRecordDate(timestamp: Long): String =
    SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(timestamp))

private fun formatRecordDuration(durationMs: Int): String {
    val totalSeconds = durationMs.coerceAtLeast(0) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return if (minutes > 0) "${minutes}分${seconds}秒" else "${seconds}秒"
}

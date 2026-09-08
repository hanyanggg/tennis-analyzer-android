package com.yang.tennisanalyzer

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private data class MatchScoreState(
    val playerGames: Int = 0,
    val opponentGames: Int = 0,
    val playerGamePoints: Int = 0,
    val opponentGamePoints: Int = 0,
    val playerPointsWon: Int = 0,
    val opponentPointsWon: Int = 0,
    val playerAces: Int = 0,
    val opponentAces: Int = 0,
    val playerWinners: Int = 0,
    val opponentWinners: Int = 0,
    val playerDoubleFaults: Int = 0,
    val opponentDoubleFaults: Int = 0,
    val playerUnforcedErrors: Int = 0,
    val opponentUnforcedErrors: Int = 0,
    val isComplete: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchTrackerScreen(
    onBack: () -> Unit,
    onSaveMatch: (MatchRecord) -> Unit
) {
    val context = LocalContext.current
    var score by remember { mutableStateOf(MatchScoreState()) }
    var history by remember { mutableStateOf(listOf<MatchScoreState>()) }
    var selectedEvent by remember { mutableStateOf("普通得分") }
    val eventTypes = listOf("普通得分", "Ace", "制胜分", "双误", "非受迫失误")

    fun addPoint(playerWon: Boolean) {
        if (score.isComplete) return
        history = history + score
        score = recordMatchPoint(score, playerWon, selectedEvent)
        selectedEvent = "普通得分"
    }

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("比赛数据记录", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "单盘计分与关键数据统计",
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
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { MatchScoreBoard(score) }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = CardWhite
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "本分类型",
                            color = Ink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "先选择类型，再点击本分获胜方",
                            color = Muted,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        eventTypes.chunked(3).forEach { rowEvents ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                rowEvents.forEach { event ->
                                    FilterChip(
                                        selected = selectedEvent == event,
                                        onClick = { selectedEvent = event },
                                        label = {
                                            Text(event, fontSize = 11.sp)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - rowEvents.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { addPoint(true) },
                        enabled = !score.isComplete,
                        modifier = Modifier.weight(1f).height(62.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CourtGreen,
                            contentColor = Color.White
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("我得分", fontWeight = FontWeight.ExtraBold)
                            Text(selectedEvent, fontSize = 10.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = { addPoint(false) },
                        enabled = !score.isComplete,
                        modifier = Modifier.weight(1f).height(62.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF2C64B7)
                        )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("对手得分", fontWeight = FontWeight.ExtraBold)
                            Text(selectedEvent, fontSize = 10.sp)
                        }
                    }
                }
            }

            item { MatchStatisticsCard(score) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (history.isNotEmpty()) {
                                score = history.last()
                                history = history.dropLast(1)
                            }
                        },
                        enabled = history.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("撤销上一分")
                    }
                    OutlinedButton(
                        onClick = {
                            score = MatchScoreState()
                            history = emptyList()
                            selectedEvent = "普通得分"
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("重新开始", color = Color(0xFFB54B45))
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        onSaveMatch(score.toMatchRecord())
                        Toast.makeText(context, "比赛已保存", Toast.LENGTH_SHORT).show()
                    },
                    enabled = score.playerPointsWon + score.opponentPointsWon > 0,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BallGreen,
                        contentColor = CourtGreen
                    )
                ) {
                    Text("保存比赛记录", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun MatchScoreBoard(score: MatchScoreState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = CourtGreen
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = if (score.isComplete) "本盘已结束" else "当前比分",
                color = BallGreen,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(12.dp))
            ScoreBoardRow(
                name = "我",
                games = score.playerGames,
                point = tennisPointLabel(
                    score.playerGamePoints,
                    score.opponentGamePoints
                ),
                highlight = true
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
            ScoreBoardRow(
                name = "对手",
                games = score.opponentGames,
                point = tennisPointLabel(
                    score.opponentGamePoints,
                    score.playerGamePoints
                ),
                highlight = false
            )
            if (score.isComplete) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (score.playerGames > score.opponentGames) {
                        "你赢下了这一盘 🎾"
                    } else {
                        "对手赢下了这一盘，继续加油"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ScoreBoardRow(
    name: String,
    games: Int,
    point: String,
    highlight: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontWeight = if (highlight) FontWeight.ExtraBold else FontWeight.Bold,
            fontSize = 18.sp
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("局", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
            Text(
                games.toString(),
                color = BallGreen,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp
            )
        }
        Spacer(Modifier.width(34.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("分", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
            Text(
                point,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp
            )
        }
    }
}

@Composable
private fun MatchStatisticsCard(score: MatchScoreState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardWhite
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "比赛统计",
                color = Ink,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(10.dp))
            MatchStatRow("总得分", score.playerPointsWon, score.opponentPointsWon)
            MatchStatRow("Ace", score.playerAces, score.opponentAces)
            MatchStatRow("制胜分", score.playerWinners, score.opponentWinners)
            MatchStatRow("双误", score.playerDoubleFaults, score.opponentDoubleFaults)
            MatchStatRow(
                "非受迫失误",
                score.playerUnforcedErrors,
                score.opponentUnforcedErrors
            )
        }
    }
}

@Composable
private fun MatchStatRow(label: String, player: Int, opponent: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            player.toString(),
            modifier = Modifier.width(42.dp),
            color = CourtGreen,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = Muted,
            fontSize = 12.sp
        )
        Text(
            opponent.toString(),
            modifier = Modifier.width(42.dp),
            color = Ink,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp
        )
    }
}

private fun recordMatchPoint(
    state: MatchScoreState,
    playerWon: Boolean,
    eventType: String
): MatchScoreState {
    var updated = if (playerWon) {
        state.copy(playerPointsWon = state.playerPointsWon + 1)
    } else {
        state.copy(opponentPointsWon = state.opponentPointsWon + 1)
    }

    updated = when (eventType) {
        "Ace" -> if (playerWon) {
            updated.copy(playerAces = updated.playerAces + 1)
        } else {
            updated.copy(opponentAces = updated.opponentAces + 1)
        }
        "制胜分" -> if (playerWon) {
            updated.copy(playerWinners = updated.playerWinners + 1)
        } else {
            updated.copy(opponentWinners = updated.opponentWinners + 1)
        }
        "双误" -> if (playerWon) {
            updated.copy(opponentDoubleFaults = updated.opponentDoubleFaults + 1)
        } else {
            updated.copy(playerDoubleFaults = updated.playerDoubleFaults + 1)
        }
        "非受迫失误" -> if (playerWon) {
            updated.copy(opponentUnforcedErrors = updated.opponentUnforcedErrors + 1)
        } else {
            updated.copy(playerUnforcedErrors = updated.playerUnforcedErrors + 1)
        }
        else -> updated
    }

    val nextPlayerPoints = updated.playerGamePoints + if (playerWon) 1 else 0
    val nextOpponentPoints = updated.opponentGamePoints + if (playerWon) 0 else 1

    if (nextPlayerPoints >= 4 && nextPlayerPoints - nextOpponentPoints >= 2) {
        val nextGames = updated.playerGames + 1
        return updated.copy(
            playerGames = nextGames,
            playerGamePoints = 0,
            opponentGamePoints = 0,
            isComplete = nextGames >= 6 &&
                nextGames - updated.opponentGames >= 2
        )
    }

    if (nextOpponentPoints >= 4 && nextOpponentPoints - nextPlayerPoints >= 2) {
        val nextGames = updated.opponentGames + 1
        return updated.copy(
            opponentGames = nextGames,
            playerGamePoints = 0,
            opponentGamePoints = 0,
            isComplete = nextGames >= 6 &&
                nextGames - updated.playerGames >= 2
        )
    }

    return updated.copy(
        playerGamePoints = nextPlayerPoints,
        opponentGamePoints = nextOpponentPoints
    )
}

private fun tennisPointLabel(ownPoints: Int, otherPoints: Int): String {
    if (ownPoints >= 3 && otherPoints >= 3) {
        return when {
            ownPoints == otherPoints -> "40"
            ownPoints == otherPoints + 1 -> "AD"
            else -> "40"
        }
    }
    return listOf("0", "15", "30", "40")[ownPoints.coerceIn(0, 3)]
}

private fun MatchScoreState.toMatchRecord(): MatchRecord = MatchRecord(
    id = System.currentTimeMillis().toString(),
    createdAt = System.currentTimeMillis(),
    playerGames = playerGames,
    opponentGames = opponentGames,
    playerPointsWon = playerPointsWon,
    opponentPointsWon = opponentPointsWon,
    playerAces = playerAces,
    opponentAces = opponentAces,
    playerWinners = playerWinners,
    opponentWinners = opponentWinners,
    playerDoubleFaults = playerDoubleFaults,
    opponentDoubleFaults = opponentDoubleFaults,
    playerUnforcedErrors = playerUnforcedErrors,
    opponentUnforcedErrors = opponentUnforcedErrors
)

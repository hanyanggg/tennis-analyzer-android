package com.yang.tennisanalyzer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TrainingRecord(
    val id: String,
    val createdAt: Long,
    val title: String,
    val strokeType: String,
    val notes: String,
    val videoUri: String,
    val keyFrames: List<Int>,
    val durationMs: Int,
    val estimatedSpeedKmh: Float? = null
)

data class MatchRecord(
    val id: String,
    val createdAt: Long,
    val playerGames: Int,
    val opponentGames: Int,
    val playerPointsWon: Int,
    val opponentPointsWon: Int,
    val playerAces: Int,
    val opponentAces: Int,
    val playerWinners: Int,
    val opponentWinners: Int,
    val playerDoubleFaults: Int,
    val opponentDoubleFaults: Int,
    val playerUnforcedErrors: Int,
    val opponentUnforcedErrors: Int
)

class LocalDataRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "tennis_analyzer_records",
        Context.MODE_PRIVATE
    )

    fun loadTrainingRecords(): List<TrainingRecord> = runCatching {
        val array = JSONArray(preferences.getString(KEY_TRAININGS, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val framesJson = item.optJSONArray("keyFrames") ?: JSONArray()
                val frames = buildList {
                    for (frameIndex in 0 until framesJson.length()) {
                        add(framesJson.optInt(frameIndex))
                    }
                }
                add(
                    TrainingRecord(
                        id = item.optString("id"),
                        createdAt = item.optLong("createdAt"),
                        title = item.optString("title"),
                        strokeType = item.optString("strokeType"),
                        notes = item.optString("notes"),
                        videoUri = item.optString("videoUri"),
                        keyFrames = frames,
                        durationMs = item.optInt("durationMs"),
                        estimatedSpeedKmh = if (
                            item.has("estimatedSpeedKmh") &&
                            !item.isNull("estimatedSpeedKmh")
                        ) {
                            item.optDouble("estimatedSpeedKmh").toFloat()
                        } else {
                            null
                        }
                    )
                )
            }
        }.sortedByDescending { it.createdAt }
    }.getOrDefault(emptyList())

    fun addTrainingRecord(record: TrainingRecord): List<TrainingRecord> {
        val updated = (listOf(record) + loadTrainingRecords())
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
        val array = JSONArray()
        updated.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("createdAt", item.createdAt)
                    put("title", item.title)
                    put("strokeType", item.strokeType)
                    put("notes", item.notes)
                    put("videoUri", item.videoUri)
                    put("durationMs", item.durationMs)
                    put("estimatedSpeedKmh", item.estimatedSpeedKmh)
                    put("keyFrames", JSONArray(item.keyFrames))
                }
            )
        }
        preferences.edit().putString(KEY_TRAININGS, array.toString()).apply()
        return updated
    }

    fun loadMatchRecords(): List<MatchRecord> = runCatching {
        val array = JSONArray(preferences.getString(KEY_MATCHES, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    MatchRecord(
                        id = item.optString("id"),
                        createdAt = item.optLong("createdAt"),
                        playerGames = item.optInt("playerGames"),
                        opponentGames = item.optInt("opponentGames"),
                        playerPointsWon = item.optInt("playerPointsWon"),
                        opponentPointsWon = item.optInt("opponentPointsWon"),
                        playerAces = item.optInt("playerAces"),
                        opponentAces = item.optInt("opponentAces"),
                        playerWinners = item.optInt("playerWinners"),
                        opponentWinners = item.optInt("opponentWinners"),
                        playerDoubleFaults = item.optInt("playerDoubleFaults"),
                        opponentDoubleFaults = item.optInt("opponentDoubleFaults"),
                        playerUnforcedErrors = item.optInt("playerUnforcedErrors"),
                        opponentUnforcedErrors = item.optInt("opponentUnforcedErrors")
                    )
                )
            }
        }.sortedByDescending { it.createdAt }
    }.getOrDefault(emptyList())

    fun addMatchRecord(record: MatchRecord): List<MatchRecord> {
        val updated = (listOf(record) + loadMatchRecords())
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
        val array = JSONArray()
        updated.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("createdAt", item.createdAt)
                    put("playerGames", item.playerGames)
                    put("opponentGames", item.opponentGames)
                    put("playerPointsWon", item.playerPointsWon)
                    put("opponentPointsWon", item.opponentPointsWon)
                    put("playerAces", item.playerAces)
                    put("opponentAces", item.opponentAces)
                    put("playerWinners", item.playerWinners)
                    put("opponentWinners", item.opponentWinners)
                    put("playerDoubleFaults", item.playerDoubleFaults)
                    put("opponentDoubleFaults", item.opponentDoubleFaults)
                    put("playerUnforcedErrors", item.playerUnforcedErrors)
                    put("opponentUnforcedErrors", item.opponentUnforcedErrors)
                }
            )
        }
        preferences.edit().putString(KEY_MATCHES, array.toString()).apply()
        return updated
    }

    private companion object {
        const val KEY_TRAININGS = "training_records"
        const val KEY_MATCHES = "match_records"
    }
}

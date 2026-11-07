package com.snakepuzzle3d.gameplay.domain

import android.content.Context
import android.content.SharedPreferences

object CampaignProgressManager {
    private const val PREFS_NAME = "snake_puzzle_3d_campaign_progress"
    private const val KEY_HIGHEST_UNLOCKED = "highest_unlocked_level"
    private const val KEY_COMPLETED_COUNT = "total_completed_levels"

    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    var highestUnlockedLevel: Int
        get() = prefs?.getInt(KEY_HIGHEST_UNLOCKED, 1) ?: 1
        private set(value) {
            prefs?.edit()?.putInt(KEY_HIGHEST_UNLOCKED, value)?.apply()
        }

    fun isLevelUnlocked(levelId: Int): Boolean {
        if (levelId <= 1) return true
        return levelId <= highestUnlockedLevel
    }

    fun getLevelStars(levelId: Int): Int {
        return prefs?.getInt("level_${levelId}_stars", 0) ?: 0
    }

    fun getLevelBestMoves(levelId: Int): Int {
        return prefs?.getInt("level_${levelId}_best_moves", 0) ?: 0
    }

    fun recordLevelCompletion(levelId: Int, moves: Int, parMoves: Int): Int {
        val stars = when {
            moves <= parMoves -> 3
            moves <= (parMoves * 1.5).toInt() -> 2
            else -> 1
        }

        val p = prefs ?: return stars
        val oldStars = getLevelStars(levelId)
        val oldBest = getLevelBestMoves(levelId)

        val editor = p.edit()
        if (stars > oldStars) {
            editor.putInt("level_${levelId}_stars", stars)
        }
        if (oldBest == 0 || moves < oldBest) {
            editor.putInt("level_${levelId}_best_moves", moves)
        }

        if (levelId >= highestUnlockedLevel && highestUnlockedLevel < 20) {
            highestUnlockedLevel = levelId + 1
        }

        editor.apply()
        return stars
    }

    fun resetProgress() {
        prefs?.edit()?.clear()?.apply()
    }
}

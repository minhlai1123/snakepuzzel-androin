package com.snakepuzzle3d.gameplay.domain

import kotlinx.serialization.Serializable

@Serializable
data class LevelDefinition(
    val schemaVersion: Int,
    val levelId: Int,
    val difficulty: String,
    val gridWidth: Int,
    val gridHeight: Int,
    val exit: ExitDefinition,
    val obstacles: List<ObstacleDefinition> = emptyList(),
    val snakes: List<SnakeDefinition>,
    val parMoves: Int,
    val validationMetadata: ValidationMetadata
) {
    val timeLimitSeconds: Int
        get() = when (difficulty.uppercase()) {
            "EASY" -> 60
            "MEDIUM" -> 50
            "HARD" -> 40
            "EXPERT" -> 35
            else -> 60
        }
}

@Serializable
data class ExitDefinition(
    val x: Int,
    val y: Int,
    val direction: String
) {
    val gridDirection: GridDirection?
        get() = try {
            GridDirection.valueOf(direction)
        } catch (e: Exception) {
            null
        }
}

@Serializable
data class ObstacleDefinition(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val shape: String
)

@Serializable
data class SnakeDefinition(
    val id: String,
    val colorPalette: String,
    val facingDirection: String,
    val segments: List<GridPosition>
) {
    val gridDirection: GridDirection?
        get() = try {
            GridDirection.valueOf(facingDirection)
        } catch (e: Exception) {
            null
        }
}

@Serializable
data class ValidationMetadata(
    val isSolvable: Boolean,
    val minimumMoves: Int,
    val checksumSHA256: String
)

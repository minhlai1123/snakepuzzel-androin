package com.snakepuzzle3d.gameplay.movement

import com.snakepuzzle3d.gameplay.domain.BoardState
import com.snakepuzzle3d.gameplay.domain.GameplayResult
import com.snakepuzzle3d.gameplay.domain.GridDirection

interface MovementStrategy {
    val strategyIdentifier: String
    fun executeMove(state: BoardState, snakeId: String, direction: GridDirection): GameplayResult
}

class SwipeToSteerStrategy : MovementStrategy {
    override val strategyIdentifier: String = "SWIPE_TO_STEER"

    override fun executeMove(state: BoardState, snakeId: String, direction: GridDirection): GameplayResult {
        val targetSnake = state.snakes[snakeId] ?: return GameplayResult.UnknownSnake(snakeId)
        if (targetSnake.isEscaped) return GameplayResult.UnknownSnake(snakeId)

        val currentHead = targetSnake.head
        val targetCell = currentHead + direction

        // 1. Exit Gate Traversal Check
        if (currentHead == state.exit.position && direction == state.exit.direction) {
            targetSnake.isEscaped = true
            state.escapedSnakeIds.add(snakeId)
            state.movesCount++
            return if (state.isLevelWon) GameplayResult.LevelWon else GameplayResult.Escaped(snakeId)
        }

        // 2. Wall / Board Bounds Check
        if (!state.size.contains(targetCell)) {
            return GameplayResult.BlockedWall(snakeId)
        }

        // 3. Static Obstacle Check
        for (obstacle in state.obstacles) {
            if (obstacle.occupies(targetCell)) {
                return GameplayResult.BlockedObstacle(snakeId)
            }
        }

        // 4. Other Snake Collision Check
        for (otherSnake in state.snakes.values) {
            if (otherSnake.isEscaped || otherSnake.id == snakeId) continue
            if (otherSnake.segments.contains(targetCell)) {
                return GameplayResult.BlockedSnake(snakeId, otherSnake.id)
            }
        }

        // 5. Self Collision Check (all segments except current tail which vacates)
        val nonTailSegments = targetSnake.segments.dropLast(1)
        if (nonTailSegments.contains(targetCell)) {
            return GameplayResult.BlockedSelf(snakeId)
        }

        // 6. Valid Move Execution
        val freedTail = targetSnake.advance(direction)
        state.movesCount++

        return GameplayResult.Success(snakeId, targetSnake.head, freedTail)
    }
}

class TapToSlitherStrategy : MovementStrategy {
    override val strategyIdentifier: String = "TAP_TO_SLITHER"

    private val swipeDelegate = SwipeToSteerStrategy()

    override fun executeMove(state: BoardState, snakeId: String, direction: GridDirection): GameplayResult {
        val targetSnake = state.snakes[snakeId] ?: return GameplayResult.UnknownSnake(snakeId)
        return swipeDelegate.executeMove(state, snakeId, targetSnake.facingDirection)
    }
}

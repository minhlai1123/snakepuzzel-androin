package com.snakepuzzle3d

import com.snakepuzzle3d.gameplay.domain.GridDirection
import com.snakepuzzle3d.gameplay.domain.GridPosition
import com.snakepuzzle3d.gameplay.domain.Snake
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class SnakeDomainTest {

    @Test
    fun testGridPositionArithmeticAndManhattanDistance() {
        val p1 = GridPosition(2, 3)
        val p2 = GridPosition(5, 7)
        assertEquals(abs(2 - 5) + abs(3 - 7), p1.manhattanDistance(p2))

        val movedNorth = p1 + GridDirection.NORTH
        assertEquals(GridPosition(2, 2), movedNorth)

        val movedEast = p1 + GridDirection.EAST
        assertEquals(GridPosition(3, 3), movedEast)
    }

    @Test
    fun testSnakeAdvanceAndTailVacation() {
        val snake = Snake(
            id = "snake_test",
            colorPalette = "RED_STRIPED",
            facingDirection = GridDirection.EAST,
            segments = listOf(GridPosition(2, 0), GridPosition(1, 0), GridPosition(0, 0))
        )

        assertEquals(GridPosition(2, 0), snake.head)
        assertEquals(GridPosition(0, 0), snake.tail)

        val vacatedTail = snake.advance(GridDirection.SOUTH)
        assertEquals(GridPosition(0, 0), vacatedTail)
        assertEquals(GridPosition(2, 1), snake.head)
        assertEquals(listOf(GridPosition(2, 1), GridPosition(2, 0), GridPosition(1, 0)), snake.segments)
    }

    @Test
    fun testLevelTimeLimitsPerDifficulty() {
        val easy = com.snakepuzzle3d.gameplay.domain.LevelDefinition(
            schemaVersion = 1, levelId = 1, difficulty = "EASY", gridWidth = 4, gridHeight = 6,
            exit = com.snakepuzzle3d.gameplay.domain.ExitDefinition(1, 5, "SOUTH"), snakes = emptyList(),
            parMoves = 10, validationMetadata = com.snakepuzzle3d.gameplay.domain.ValidationMetadata(true, 10, "dummy")
        )
        assertEquals(60, easy.timeLimitSeconds)

        val medium = easy.copy(levelId = 9, difficulty = "MEDIUM")
        assertEquals(50, medium.timeLimitSeconds)

        val hard = easy.copy(levelId = 13, difficulty = "HARD")
        assertEquals(40, hard.timeLimitSeconds)

        val expert = easy.copy(levelId = 17, difficulty = "EXPERT")
        assertEquals(35, expert.timeLimitSeconds)
    }
}

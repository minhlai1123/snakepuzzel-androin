package com.snakepuzzle3d

import com.snakepuzzle3d.gameplay.domain.GridPosition
import com.snakepuzzle3d.gameplay.domain.LevelDefinition
import com.snakepuzzle3d.gameplay.validation.LevelValidator
import com.snakepuzzle3d.gameplay.validation.ValidationException
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossPlatformParityTest {

    @Test
    fun testValidLevelStructure() {
        val validLevel = LevelDefinition(
            schemaVersion = 1,
            levelId = 1,
            difficulty = "EASY",
            gridWidth = 4,
            gridHeight = 6,
            exit = com.snakepuzzle3d.gameplay.domain.ExitDefinition(1, 5, "SOUTH"),
            obstacles = listOf(com.snakepuzzle3d.gameplay.domain.ObstacleDefinition(1, 3, 1, 1, "BLOCK_1X1")),
            snakes = listOf(
                com.snakepuzzle3d.gameplay.domain.SnakeDefinition("snake_red", "RED_STRIPED", "EAST", listOf(
                    GridPosition(2, 0), GridPosition(1, 0), GridPosition(0, 0),
                    GridPosition(0, 1), GridPosition(0, 2), GridPosition(1, 2)
                )),
                com.snakepuzzle3d.gameplay.domain.SnakeDefinition("snake_yellow", "YELLOW_STRIPED", "NORTH", listOf(
                    GridPosition(3, 0), GridPosition(3, 1), GridPosition(3, 2)
                ))
            ),
            parMoves = 15,
            validationMetadata = com.snakepuzzle3d.gameplay.domain.ValidationMetadata(true, 15, "1d2d25044a1321576bccc90d19f53bc50a85f58876c16b76de73a62197199878")
        )

        // Must validate without throwing
        LevelValidator.validate(validLevel)
        assertTrue(true)
    }

    @Test
    fun testRejectAdjacencyViolation() {
        val invalidLevel = LevelDefinition(
            schemaVersion = 1,
            levelId = 903,
            difficulty = "EASY",
            gridWidth = 4,
            gridHeight = 6,
            exit = com.snakepuzzle3d.gameplay.domain.ExitDefinition(1, 5, "SOUTH"),
            obstacles = emptyList(),
            snakes = listOf(
                com.snakepuzzle3d.gameplay.domain.SnakeDefinition("snake_red", "RED_STRIPED", "SOUTH", listOf(
                    GridPosition(0, 0), GridPosition(0, 2) // Distance = 2
                ))
            ),
            parMoves = 5,
            validationMetadata = com.snakepuzzle3d.gameplay.domain.ValidationMetadata(false, 5, "dummy")
        )

        assertThrows(ValidationException.SnakeNonAdjacent::class.java) {
            LevelValidator.validate(invalidLevel)
        }
    }

    @Test
    fun testRejectOverlapViolation() {
        val invalidLevel = LevelDefinition(
            schemaVersion = 1,
            levelId = 901,
            difficulty = "EASY",
            gridWidth = 4,
            gridHeight = 6,
            exit = com.snakepuzzle3d.gameplay.domain.ExitDefinition(1, 5, "SOUTH"),
            obstacles = emptyList(),
            snakes = listOf(
                com.snakepuzzle3d.gameplay.domain.SnakeDefinition("s1", "RED_STRIPED", "EAST", listOf(
                    GridPosition(1, 0), GridPosition(1, 1)
                )),
                com.snakepuzzle3d.gameplay.domain.SnakeDefinition("s2", "YELLOW_STRIPED", "SOUTH", listOf(
                    GridPosition(1, 1), GridPosition(2, 1)
                ))
            ),
            parMoves = 5,
            validationMetadata = com.snakepuzzle3d.gameplay.domain.ValidationMetadata(false, 5, "dummy")
        )

        assertThrows(ValidationException.SnakeInterOverlap::class.java) {
            LevelValidator.validate(invalidLevel)
        }
    }
}

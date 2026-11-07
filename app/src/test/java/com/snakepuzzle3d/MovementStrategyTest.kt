package com.snakepuzzle3d

import com.snakepuzzle3d.gameplay.domain.*
import com.snakepuzzle3d.gameplay.movement.SwipeToSteerStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementStrategyTest {

    @Test
    fun testSwipeToSteerLegalAndBlockedMoves() {
        val size = BoardSize(4, 6)
        val exit = ExitGate(1, 5, GridDirection.SOUTH)
        val obstacles = listOf(Obstacle(1, 3, 1, 1, "BLOCK_1X1"))
        val snakes = mutableMapOf(
            "snake_red" to Snake("snake_red", "RED_STRIPED", GridDirection.EAST, listOf(
                GridPosition(2, 0), GridPosition(1, 0), GridPosition(0, 0),
                GridPosition(0, 1), GridPosition(0, 2), GridPosition(1, 2)
            )),
            "snake_yellow" to Snake("snake_yellow", "YELLOW_STRIPED", GridDirection.NORTH, listOf(
                GridPosition(3, 0), GridPosition(3, 1), GridPosition(3, 2)
            ))
        )

        val state = BoardState(size, exit, obstacles, snakes)
        val strategy = SwipeToSteerStrategy()

        // 1. Move North from (2,0) -> Blocked by wall
        val resNorth = strategy.executeMove(state, "snake_red", GridDirection.NORTH)
        assertEquals(GameplayResult.BlockedWall("snake_red"), resNorth)

        // 2. Move East from (2,0) into (3,0) -> Blocked by yellow snake
        val resEast = strategy.executeMove(state, "snake_red", GridDirection.EAST)
        assertEquals(GameplayResult.BlockedSnake("snake_red", "snake_yellow"), resEast)

        // 3. Move West from (2,0) into (1,0) -> Blocked by self body
        val resWest = strategy.executeMove(state, "snake_red", GridDirection.WEST)
        assertEquals(GameplayResult.BlockedSelf("snake_red"), resWest)

        // 4. Move South from (2,0) into (2,1) -> Legal!
        val resSouth = strategy.executeMove(state, "snake_red", GridDirection.SOUTH)
        assertEquals(GameplayResult.Success("snake_red", GridPosition(2, 1), GridPosition(1, 2)), resSouth)
        assertEquals(1, state.movesCount)
    }

    @Test
    fun testExitGateEscape() {
        val size = BoardSize(4, 6)
        val exit = ExitGate(1, 5, GridDirection.SOUTH)
        val snakes = mutableMapOf(
            "snake_red" to Snake("snake_red", "RED_STRIPED", GridDirection.SOUTH, listOf(
                GridPosition(1, 5), GridPosition(0, 5)
            ))
        )
        val state = BoardState(size, exit, emptyList(), snakes)
        val strategy = SwipeToSteerStrategy()

        val res = strategy.executeMove(state, "snake_red", GridDirection.SOUTH)
        assertEquals(GameplayResult.LevelWon, res)
        assertTrue(state.isLevelWon)
    }
}

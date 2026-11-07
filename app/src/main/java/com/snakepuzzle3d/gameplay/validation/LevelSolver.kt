package com.snakepuzzle3d.gameplay.validation

import com.snakepuzzle3d.gameplay.domain.BoardSize
import com.snakepuzzle3d.gameplay.domain.BoardState
import com.snakepuzzle3d.gameplay.domain.ExitGate
import com.snakepuzzle3d.gameplay.domain.GridDirection
import com.snakepuzzle3d.gameplay.domain.LevelDefinition
import com.snakepuzzle3d.gameplay.domain.Obstacle
import com.snakepuzzle3d.gameplay.domain.Snake
import com.snakepuzzle3d.gameplay.domain.GameplayResult
import com.snakepuzzle3d.gameplay.movement.SwipeToSteerStrategy
import java.util.ArrayDeque

sealed interface SolverStatus {
    data class Solvable(val minimumMoves: Int, val solutionMoves: List<String>) : SolverStatus
    data object Unsolvable : SolverStatus
    data object Unknown : SolverStatus
    data class SearchLimitExceeded(val exploredStates: Int) : SolverStatus
}

interface LevelSolver {
    fun solve(level: LevelDefinition, maxStates: Int = 50000): SolverStatus
}

class StateSpaceLevelSolver : LevelSolver {

    override fun solve(level: LevelDefinition, maxStates: Int): SolverStatus {
        val initialSnakes = level.snakes.map { sDef ->
            Snake(
                id = sDef.id,
                colorPalette = sDef.colorPalette,
                facingDirection = sDef.gridDirection ?: GridDirection.NORTH,
                segments = sDef.segments
            )
        }

        val exitGate = ExitGate(level.exit.x, level.exit.y, level.exit.gridDirection ?: GridDirection.SOUTH)
        val obstacles = level.obstacles.map { Obstacle(it.x, it.y, it.width, it.height, it.shape) }
        val initialBoard = BoardState(
            size = BoardSize(level.gridWidth, level.gridHeight),
            exit = exitGate,
            obstacles = obstacles,
            snakes = initialSnakes.associateBy { it.id }.toMutableMap()
        )

        fun stateKey(state: BoardState): String {
            return state.snakes.entries.sortedBy { it.key }.joinToString("|") { (id, s) ->
                if (s.isEscaped) "$id:ESC" else "$id:" + s.segments.joinToString(";") { "${it.x},${it.y}" }
            }
        }

        val queue = ArrayDeque<Pair<BoardState, List<String>>>()
        val visited = HashSet<String>()
        val initialKey = stateKey(initialBoard)
        visited.add(initialKey)
        queue.add(initialBoard to emptyList())

        val strategy = SwipeToSteerStrategy()
        var statesExplored = 0

        while (queue.isNotEmpty()) {
            val (currentState, moves) = queue.removeFirst()
            statesExplored++

            if (currentState.isLevelWon) {
                return SolverStatus.Solvable(moves.size, moves)
            }

            if (statesExplored >= maxStates) {
                return SolverStatus.SearchLimitExceeded(statesExplored)
            }

            for (sId in currentState.snakes.keys.sorted()) {
                val snake = currentState.snakes[sId] ?: continue
                if (snake.isEscaped) continue

                for (dir in GridDirection.entries) {
                    val nextSnakes = currentState.snakes.mapValues {
                        Snake(it.value.id, it.value.colorPalette, it.value.facingDirection, it.value.segments, it.value.isEscaped)
                    }.toMutableMap()

                    val nextState = BoardState(
                        currentState.size,
                        currentState.exit,
                        currentState.obstacles,
                        nextSnakes,
                        currentState.escapedSnakeIds.toMutableSet(),
                        currentState.movesCount
                    )

                    val res = strategy.executeMove(nextState, sId, dir)
                    when (res) {
                        is GameplayResult.Success, is GameplayResult.Escaped, is GameplayResult.LevelWon -> {
                            val key = stateKey(nextState)
                            if (!visited.contains(key)) {
                                visited.add(key)
                                queue.add(nextState to (moves + "$sId:${dir.name}"))
                            }
                        }
                        else -> { /* Blocked */ }
                    }
                }
            }
        }

        return SolverStatus.Unsolvable
    }
}

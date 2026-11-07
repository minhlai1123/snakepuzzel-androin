package com.snakepuzzle3d.gameplay.domain

sealed interface CellOccupant {
    data object Empty : CellOccupant
    data object Wall : CellOccupant
    data class ObstacleBlock(val shape: String) : CellOccupant
    data class SnakeHead(val snakeId: String) : CellOccupant
    data class SnakeBody(val snakeId: String, val segmentIndex: Int) : CellOccupant
    data class Exit(val direction: GridDirection) : CellOccupant
}

data class BoardState(
    val size: BoardSize,
    val exit: ExitGate,
    val obstacles: List<Obstacle>,
    val snakes: MutableMap<String, Snake>,
    val escapedSnakeIds: MutableSet<String> = mutableSetOf(),
    var movesCount: Int = 0
) {
    val isLevelWon: Boolean
        get() = snakes.values.all { it.isEscaped }

    fun occupantAt(pos: GridPosition): CellOccupant {
        if (!size.contains(pos)) {
            return CellOccupant.Wall
        }
        if (pos == exit.position) {
            return CellOccupant.Exit(exit.direction)
        }
        for (obs in obstacles) {
            if (obs.occupies(pos)) {
                return CellOccupant.ObstacleBlock(obs.shape)
            }
        }
        for (s in snakes.values) {
            if (s.isEscaped) continue
            s.segments.forEachIndexed { idx, seg ->
                if (seg == pos) {
                    return if (idx == 0) {
                        CellOccupant.SnakeHead(s.id)
                    } else {
                        CellOccupant.SnakeBody(s.id, idx)
                    }
                }
            }
        }
        return CellOccupant.Empty
    }
}

sealed interface GameplayResult {
    data class Success(val snakeId: String, val newHead: GridPosition, val freedTail: GridPosition) : GameplayResult
    data class Escaped(val snakeId: String) : GameplayResult
    data object LevelWon : GameplayResult
    data class BlockedWall(val snakeId: String) : GameplayResult
    data class BlockedObstacle(val snakeId: String) : GameplayResult
    data class BlockedSnake(val snakeId: String, val blockingSnakeId: String) : GameplayResult
    data class BlockedSelf(val snakeId: String) : GameplayResult
    data class UnknownSnake(val snakeId: String) : GameplayResult
}

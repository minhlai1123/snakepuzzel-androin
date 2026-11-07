package com.snakepuzzle3d.gameplay.domain

import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class GridPosition(
    val x: Int,
    val y: Int
) {
    fun manhattanDistance(other: GridPosition): Int {
        return abs(this.x - other.x) + abs(this.y - other.y)
    }

    fun translated(direction: GridDirection): GridPosition {
        return GridPosition(x = this.x + direction.dx, y = this.y + direction.dy)
    }

    operator fun plus(direction: GridDirection): GridPosition {
        return translated(direction)
    }
}

@Serializable
enum class GridDirection(val dx: Int, val dy: Int) {
    NORTH(dx = 0, dy = -1),
    SOUTH(dx = 0, dy = 1),
    EAST(dx = 1, dy = 0),
    WEST(dx = -1, dy = 0);

    val opposite: GridDirection
        get() = when (this) {
            NORTH -> SOUTH
            SOUTH -> NORTH
            EAST -> WEST
            WEST -> EAST
        }
}

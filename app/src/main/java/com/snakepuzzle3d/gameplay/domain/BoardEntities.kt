package com.snakepuzzle3d.gameplay.domain

import kotlinx.serialization.Serializable

@Serializable
data class BoardSize(
    val width: Int,
    val height: Int
) {
    fun contains(pos: GridPosition): Boolean {
        return pos.x in 0 until width && pos.y in 0 until height
    }
}

@Serializable
data class Obstacle(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val shape: String
) {
    fun occupies(pos: GridPosition): Boolean {
        return pos.x in x until (x + width) && pos.y in y until (y + height)
    }

    val allOccupiedPositions: List<GridPosition>
        get() {
            val list = mutableListOf<GridPosition>()
            for (ox in x until (x + width)) {
                for (oy in y until (y + height)) {
                    list.add(GridPosition(ox, oy))
                }
            }
            return list
        }
}

@Serializable
data class ExitGate(
    val x: Int,
    val y: Int,
    val direction: GridDirection
) {
    val position: GridPosition
        get() = GridPosition(x, y)
}

@Serializable
data class Snake(
    val id: String,
    val colorPalette: String,
    var facingDirection: GridDirection,
    var segments: List<GridPosition>,
    var isEscaped: Boolean = false
) {
    val head: GridPosition
        get() = segments.first()

    val tail: GridPosition
        get() = segments.last()

    val length: Int
        get() = segments.size

    /**
     * Advances the snake 1 step in the specified direction.
     * Returns the vacated old tail position.
     */
    fun advance(direction: GridDirection): GridPosition {
        val newHead = head + direction
        val oldTail = tail
        val newSegments = ArrayList<GridPosition>(segments.size)
        newSegments.add(newHead)
        for (i in 0 until segments.size - 1) {
            newSegments.add(segments[i])
        }
        this.segments = newSegments
        this.facingDirection = direction
        return oldTail
    }
}

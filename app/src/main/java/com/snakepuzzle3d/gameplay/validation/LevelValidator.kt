package com.snakepuzzle3d.gameplay.validation

import com.snakepuzzle3d.gameplay.domain.GridPosition
import com.snakepuzzle3d.gameplay.domain.LevelDefinition
import java.security.MessageDigest

sealed class ValidationException(message: String) : Exception(message) {
    class GridOutOfBounds(w: Int, h: Int) : ValidationException("Grid dimensions ($w x $h) out of bounds")
    class ExitOutOfBounds(x: Int, y: Int) : ValidationException("Exit coordinates ($x, $y) out of bounds")
    class ObstacleOutOfBounds(x: Int, y: Int) : ValidationException("Obstacle at ($x, $y) out of bounds")
    class ObstacleOverlap(x: Int, y: Int) : ValidationException("Obstacle overlapping at ($x, $y)")
    class SnakeTooShort(id: String, len: Int) : ValidationException("Snake $id has fewer than 2 segments: $len")
    class SnakeSegmentOutOfBounds(id: String, x: Int, y: Int) : ValidationException("Snake $id segment at ($x, $y) out of bounds")
    class SnakeNonAdjacent(id: String, idx1: Int, idx2: Int, dist: Int) : ValidationException("Snake $id segments $idx1 and $idx2 not adjacent (dist=$dist)")
    class SnakeSelfOverlap(id: String, x: Int, y: Int) : ValidationException("Snake $id self-overlaps at ($x, $y)")
    class SnakeInterOverlap(s1: String, s2: String, x: Int, y: Int) : ValidationException("Snake overlap between $s1 and $s2 at ($x, $y)")
    class SnakeObstacleOverlap(id: String, x: Int, y: Int) : ValidationException("Snake $id overlaps obstacle at ($x, $y)")
    class ChecksumMismatch(val claimed: String, val calculated: String) : ValidationException("Checksum mismatch: claimed $claimed != calculated $calculated")
}

object LevelValidator {

    @Throws(ValidationException::class)
    fun validate(level: LevelDefinition) {
        val w = level.gridWidth
        val h = level.gridHeight

        // 1. Grid bounds
        if (w !in 3..12 || h !in 3..16) {
            throw ValidationException.GridOutOfBounds(w, h)
        }

        // 2. Exit bounds
        if (level.exit.x !in 0 until w || level.exit.y !in 0 until h) {
            throw ValidationException.ExitOutOfBounds(level.exit.x, level.exit.y)
        }

        // 3. Obstacles
        val obstacleCells = mutableSetOf<GridPosition>()
        for (obs in level.obstacles) {
            for (ox in obs.x until (obs.x + obs.width)) {
                for (oy in obs.y until (obs.y + obs.height)) {
                    val p = GridPosition(ox, oy)
                    if (p.x !in 0 until w || p.y !in 0 until h) {
                        throw ValidationException.ObstacleOutOfBounds(p.x, p.y)
                    }
                    if (obstacleCells.contains(p)) {
                        throw ValidationException.ObstacleOverlap(p.x, p.y)
                    }
                    obstacleCells.add(p)
                }
            }
        }

        // 4. Snakes
        val occupiedCells = mutableMapOf<GridPosition, String>()
        for (snake in level.snakes) {
            if (snake.segments.size < 2) {
                throw ValidationException.SnakeTooShort(snake.id, snake.segments.size)
            }

            val internalCells = mutableSetOf<GridPosition>()
            snake.segments.forEachIndexed { idx, seg ->
                if (seg.x !in 0 until w || seg.y !in 0 until h) {
                    throw ValidationException.SnakeSegmentOutOfBounds(snake.id, seg.x, seg.y)
                }
                if (obstacleCells.contains(seg)) {
                    throw ValidationException.SnakeObstacleOverlap(snake.id, seg.x, seg.y)
                }
                if (internalCells.contains(seg)) {
                    throw ValidationException.SnakeSelfOverlap(snake.id, seg.x, seg.y)
                }
                if (occupiedCells.containsKey(seg)) {
                    throw ValidationException.SnakeInterOverlap(snake.id, occupiedCells[seg]!!, seg.x, seg.y)
                }

                internalCells.add(seg)
                occupiedCells[seg] = snake.id

                if (idx > 0) {
                    val prev = snake.segments[idx - 1]
                    val dist = seg.manhattanDistance(prev)
                    if (dist != 1) {
                        throw ValidationException.SnakeNonAdjacent(snake.id, idx - 1, idx, dist)
                    }
                }
            }
        }
    }

    fun computeCanonicalSha256(canonicalJsonString: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(canonicalJsonString.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

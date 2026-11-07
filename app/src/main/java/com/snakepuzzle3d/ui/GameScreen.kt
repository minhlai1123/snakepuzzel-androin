package com.snakepuzzle3d.ui

import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.LifecycleOwner
import com.snakepuzzle3d.gameplay.domain.*
import com.snakepuzzle3d.gameplay.movement.SwipeToSteerStrategy
import com.snakepuzzle3d.gameplay.validation.LevelValidator
import com.snakepuzzle3d.rendering.FilamentLifecycleManager
import com.snakepuzzle3d.rendering.FilamentRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.random.Random

enum class AppScreen {
    START,
    GAMEPLAY
}

fun LevelDefinition.toBoardState(): BoardState {
    val size = BoardSize(gridWidth, gridHeight)
    val exitGate = ExitGate(exit.x, exit.y, exit.gridDirection ?: GridDirection.SOUTH)
    val obstacleList = obstacles.map { Obstacle(it.x, it.y, it.width, it.height, it.shape) }
    val snakeMap = snakes.associate { s ->
        s.id to Snake(
            id = s.id,
            colorPalette = s.colorPalette,
            facingDirection = s.gridDirection ?: GridDirection.SOUTH,
            segments = s.segments
        )
    }.toMutableMap()
    return BoardState(size, exitGate, obstacleList, snakeMap)
}

// Confetti Particle model
data class ConfettiParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val size: Float,
    val color: Color,
    var rotation: Float,
    var vRot: Float,
    var alpha: Float = 1.0f
)

@Composable
fun GameScreen(lifecycleOwner: LifecycleOwner) {
    val context = LocalContext.current
    val currentView = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    var appScreen by remember { mutableStateOf(AppScreen.GAMEPLAY) }
    var currentLevelIndex by remember { mutableIntStateOf(1) }
    var filamentRenderer by remember { mutableStateOf<FilamentRenderer?>(null) }
    var currentLevel by remember { mutableStateOf<LevelDefinition?>(null) }
    var boardState by remember { mutableStateOf<BoardState?>(null) }
    var selectedSnakeId by remember { mutableStateOf("snake_red") }
    var movesCount by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf("Swipe to slither!") }
    var isVictory by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var showLevelSelect by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(true) }
    var musicEnabled by remember { mutableStateOf(true) }
    var timeRemaining by remember { mutableIntStateOf(60) }
    var isTimeUp by remember { mutableStateOf(false) }

    // Confetti particles list
    val confettiParticles = remember { mutableStateListOf<ConfettiParticle>() }

    fun triggerConfetti(count: Int = 90) {
        val colors = listOf(
            Color(0xFFFF3826), Color(0xFFFFC00D), Color(0xFF29CC47),
            Color(0xFF0DCCF2), Color(0xFFFF2E8C), Color(0xFFFFD700), Color(0xFF8B5CF6)
        )
        confettiParticles.clear()
        for (i in 0 until count) {
            val p = ConfettiParticle(
                x = Random.nextFloat() * 1000f,
                y = Random.nextFloat() * 200f + 200f,
                vx = (Random.nextFloat() - 0.5f) * 18f,
                vy = -Random.nextFloat() * 20f - 10f,
                size = Random.nextFloat() * 16f + 8f,
                color = colors[Random.nextInt(colors.size)],
                rotation = Random.nextFloat() * 360f,
                vRot = (Random.nextFloat() - 0.5f) * 24f
            )
            confettiParticles.add(p)
        }
    }

    // Confetti physics tick
    LaunchedEffect(confettiParticles.size) {
        if (confettiParticles.isNotEmpty()) {
            while (confettiParticles.isNotEmpty()) {
                val iter = confettiParticles.iterator()
                while (iter.hasNext()) {
                    val p = iter.next()
                    p.x += p.vx
                    p.y += p.vy
                    p.vy += 0.85f // gravity
                    p.vx *= 0.98f // air drag
                    p.rotation += p.vRot
                    p.alpha -= 0.012f
                    if (p.alpha <= 0f || p.y > 2200f) {
                        iter.remove()
                    }
                }
                delay(16)
            }
        }
    }

    fun loadLevel(levelIdx: Int) {
        try {
            val safeIdx = levelIdx.coerceIn(1, 20)
            val fileName = String.format("levels/level_%03d.json", safeIdx)
            Log.d("GameScreen", "Loading $fileName")
            val jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val jsonParser = Json { ignoreUnknownKeys = true }
            val level = jsonParser.decodeFromString<LevelDefinition>(jsonString)
            LevelValidator.validate(level)

            currentLevelIndex = safeIdx
            currentLevel = level
            boardState = level.toBoardState()
            movesCount = 0
            isVictory = false
            isPaused = false
            isTimeUp = false
            timeRemaining = level.timeLimitSeconds
            showLevelSelect = false
            statusMessage = "Level $safeIdx (${level.difficulty}): Time limit ${level.timeLimitSeconds}s!"
            val firstSnake = level.snakes.firstOrNull()?.id ?: "snake_red"
            selectedSnakeId = firstSnake

            filamentRenderer?.let { rend ->
                rend.setSelectedSnake(firstSnake)
                rend.buildScene(level)
            }
        } catch (e: Exception) {
            Log.e("GameScreen", "Error loading level $levelIdx: ${e.message}", e)
            statusMessage = "Error: ${e.message}"
        }
    }

    // Initial persistence, audio, and level loading
    LaunchedEffect(Unit) {
        CampaignProgressManager.initialize(context)
        SoundManager.initialize(context)
        SoundManager.startBgm(context)
        val startLvl = CampaignProgressManager.highestUnlockedLevel.coerceIn(1, 20)
        loadLevel(startLvl)
    }

    // Countdown Timer per level difficulty
    LaunchedEffect(currentLevelIndex, isTimeUp, isPaused, isVictory, showLevelSelect) {
        if (!isTimeUp && !isPaused && !isVictory && !showLevelSelect) {
            while (timeRemaining > 0 && !isTimeUp && !isPaused && !isVictory && !showLevelSelect) {
                delay(1000L)
                timeRemaining--
                if (timeRemaining in 1..5) {
                    SoundManager.playTick(currentView)
                }
                if (timeRemaining <= 0) {
                    isTimeUp = true
                    SoundManager.playTimeUp(currentView)
                    statusMessage = "Time's up! Level failed."
                }
            }
        }
    }

    // Ensure 3D scene builds as soon as renderer becomes available
    LaunchedEffect(filamentRenderer, currentLevel) {
        val rend = filamentRenderer
        val level = currentLevel
        if (rend != null && level != null) {
            rend.setSelectedSnake(selectedSnakeId)
            rend.buildScene(level)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Fullscreen Filament 3D Surface View
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    Log.d("GameScreen", "Creating SurfaceView and FilamentRenderer...")
                    val rend = FilamentRenderer(this)
                    rend.initialize()
                    filamentRenderer = rend

                    val lifecycleManager = FilamentLifecycleManager(rend)
                    lifecycleOwner.lifecycle.addObserver(lifecycleManager)

                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            if (width > 0 && height > 0) {
                                rend.onSurfaceAvailable(holder.surface, width, height)
                                currentLevel?.let { rend.buildScene(it) }
                            }
                        }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            if (width > 0 && height > 0) {
                                rend.onSurfaceResized(holder.surface, width, height)
                                currentLevel?.let { rend.buildScene(it) }
                            }
                        }
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            rend.onSurfaceDestroyed()
                        }
                    })
                }
            }
        )

        // 2. Gameplay Gestures Layer
        if (appScreen == AppScreen.GAMEPLAY && !isPaused && !isVictory && !showLevelSelect && !isTimeUp) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selectedSnakeId, boardState) {
                        detectTapGestures(
                            onTap = { _ ->
                                val state = boardState ?: return@detectTapGestures
                                val activeSnakes = state.snakes.values.filter { !it.isEscaped }
                                if (activeSnakes.size > 1) {
                                    val currentIdx = activeSnakes.indexOfFirst { it.id == selectedSnakeId }
                                    val nextSnake = activeSnakes[(currentIdx + 1) % activeSnakes.size]
                                    selectedSnakeId = nextSnake.id
                                    filamentRenderer?.setSelectedSnake(nextSnake.id)
                                    SoundManager.playTap(currentView)
                                }
                            }
                        )
                    }
                    .pointerInput(selectedSnakeId, boardState) {
                        var totalDragX = 0f
                        var totalDragY = 0f
                        detectDragGestures(
                            onDragStart = {
                                totalDragX = 0f
                                totalDragY = 0f
                            },
                            onDragEnd = {
                                val state = boardState ?: return@detectDragGestures
                                val snake = state.snakes[selectedSnakeId] ?: return@detectDragGestures
                                if (snake.isEscaped) return@detectDragGestures

                                val absX = kotlin.math.abs(totalDragX)
                                val absY = kotlin.math.abs(totalDragY)
                                val threshold = 30f

                                val direction = when {
                                    absX > absY && absX > threshold -> {
                                        if (totalDragX > 0) GridDirection.EAST else GridDirection.WEST
                                    }
                                    absY >= absX && absY > threshold -> {
                                        if (totalDragY > 0) GridDirection.SOUTH else GridDirection.NORTH
                                    }
                                    else -> null
                                }

                                if (direction != null) {
                                    val oldSegments = snake.segments.toList()
                                    val headPos = oldSegments.firstOrNull() ?: GridPosition(0, 0)
                                    val strategy = SwipeToSteerStrategy()
                                    val result = strategy.executeMove(state, selectedSnakeId, direction)

                                    when (result) {
                                        is GameplayResult.Success -> {
                                            movesCount = state.movesCount
                                            SoundManager.playMove(currentView)
                                            val newSegments = state.snakes[selectedSnakeId]?.segments ?: emptyList()
                                            filamentRenderer?.animateMove(selectedSnakeId, oldSegments, newSegments, direction)
                                            statusMessage = "Slithered ${direction.name}!"
                                        }
                                        is GameplayResult.Escaped -> {
                                            movesCount = state.movesCount
                                            SoundManager.playEscape(currentView)
                                            triggerConfetti(60)
                                            filamentRenderer?.animateEscape(selectedSnakeId, oldSegments, direction) {
                                                selectedSnakeId = state.snakes.values.firstOrNull { !it.isEscaped }?.id ?: selectedSnakeId
                                                filamentRenderer?.setSelectedSnake(selectedSnakeId)
                                            }
                                            statusMessage = "Snake escaped into portal! 🎉"
                                        }
                                        is GameplayResult.LevelWon -> {
                                            movesCount = state.movesCount
                                            val par = currentLevel?.parMoves ?: 15
                                            CampaignProgressManager.recordLevelCompletion(currentLevelIndex, movesCount, par)
                                            SoundManager.playVictory(coroutineScope, currentView)
                                            triggerConfetti(120)
                                            filamentRenderer?.animateEscape(selectedSnakeId, oldSegments, direction) {
                                                isVictory = true
                                            }
                                            statusMessage = "SUBLIME! Level cleared!"
                                        }
                                        is GameplayResult.BlockedWall,
                                        is GameplayResult.BlockedObstacle,
                                        is GameplayResult.BlockedSnake,
                                        is GameplayResult.BlockedSelf -> {
                                            SoundManager.playBlocked(currentView)
                                            filamentRenderer?.animateBlocked(selectedSnakeId, headPos, direction)
                                            statusMessage = "Blocked!"
                                        }
                                        is GameplayResult.UnknownSnake -> {}
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDragX += dragAmount.x
                                totalDragY += dragAmount.y
                            }
                        )
                    }
            )
        }

        // 3. Canvas Confetti Shower
        if (confettiParticles.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (p in confettiParticles) {
                    drawRect(
                        color = p.color.copy(alpha = p.alpha.coerceIn(0f, 1f)),
                        topLeft = Offset(p.x, p.y),
                        size = Size(p.size, p.size * 0.7f)
                    )
                }
            }
        }

        // 4. Gameplay Top HUD Bar (Level, Moves, Level Select, Sound, Restart)
        if (appScreen == AppScreen.GAMEPLAY) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Level Badge & Countdown Timer
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Level Badge Capsule (Clickable -> Opens Level Select)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF0F172A),
                            shadowElevation = 6.dp,
                            modifier = Modifier.clickable {
                                SoundManager.playTap(currentView)
                                showLevelSelect = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "★ L$currentLevelIndex",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "▾",
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Countdown Timer Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = when {
                                timeRemaining <= 5 -> Color(0xFFEF4444)
                                timeRemaining <= 15 -> Color(0xFFF59E0B)
                                else -> Color.White
                            },
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⏱ ",
                                    fontSize = 12.sp
                                )
                                val m = timeRemaining / 60
                                val s = timeRemaining % 60
                                Text(
                                    text = String.format("%02d:%02d", m, s),
                                    color = if (timeRemaining <= 15) Color.White else Color(0xFF0F172A),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Move Counter
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.White,
                        shadowElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "M: ",
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "$movesCount",
                                color = if (movesCount <= (currentLevel?.parMoves ?: 15)) Color(0xFF10B981) else Color(0xFFF59E0B),
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "/${currentLevel?.parMoves ?: 15}",
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Action Buttons Row: Level Select, Music, Sound, Restart
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Level Select Button
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable {
                                    SoundManager.playTap(currentView)
                                    showLevelSelect = true
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "▦",
                                    color = Color(0xFF0F172A),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        // Music Toggle
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable {
                                    musicEnabled = SoundManager.toggleMusic(context)
                                    SoundManager.playTap(currentView)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (musicEnabled) "🎵" else "🔇",
                                    fontSize = 15.sp
                                )
                            }
                        }

                        // Sound Toggle
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable {
                                    soundEnabled = !soundEnabled
                                    SoundManager.isSoundEnabled = soundEnabled
                                    SoundManager.playTap(currentView)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (soundEnabled) "🔊" else "🔈",
                                    fontSize = 15.sp
                                )
                            }
                        }

                        // Restart Button
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .size(36.dp)
                                .clickable {
                                    SoundManager.playTap(currentView)
                                    loadLevel(currentLevelIndex)
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "↺",
                                    color = Color(0xFF0F172A),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }

            // 5. Floating Sleek Snake Switcher (Bottom Center)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                boardState?.let { state ->
                    val activeSnakes = state.snakes.values.filter { !it.isEscaped }
                    if (activeSnakes.size > 1) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color.White.copy(alpha = 0.92f),
                            shadowElevation = 10.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                state.snakes.values.forEach { snake ->
                                    val isSelected = snake.id == selectedSnakeId
                                    val chipColor = when (snake.colorPalette) {
                                        "YELLOW_STRIPED" -> Color(0xFFFFB800)
                                        "RED_STRIPED" -> Color(0xFFFF3826)
                                        "CYAN_STRIPED" -> Color(0xFF0DCCF2)
                                        "PINK_STRIPED" -> Color(0xFFFF2E8C)
                                        else -> Color(0xFF29CC47)
                                    }
                                    val snakeName = when (snake.colorPalette) {
                                        "RED_STRIPED" -> "Red"
                                        "YELLOW_STRIPED" -> "Yellow"
                                        "CYAN_STRIPED" -> "Cyan"
                                        "PINK_STRIPED" -> "Pink"
                                        else -> "Green"
                                    }

                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) chipColor.copy(alpha = 0.20f) else Color.Transparent)
                                            .clickable {
                                                if (!snake.isEscaped) {
                                                    selectedSnakeId = snake.id
                                                    filamentRenderer?.setSelectedSnake(snake.id)
                                                    SoundManager.playTap(currentView)
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(if (isSelected) 14.dp else 10.dp)
                                                .background(if (snake.isEscaped) Color(0xFFCBD5E1) else chipColor, CircleShape)
                                        )
                                        if (isSelected && !snake.isEscaped) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = snakeName,
                                                color = Color(0xFF0F172A),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Level Select Modal
        if (showLevelSelect) {
            Dialog(onDismissRequest = { showLevelSelect = false }) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "SELECT LEVEL",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = Color(0xFF0F172A),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "20 Progressive Campaign Levels",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        val highestUnlocked = CampaignProgressManager.highestUnlockedLevel

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.height(340.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(20) { idx ->
                                val lvlId = idx + 1
                                val isUnlocked = lvlId <= highestUnlocked
                                val isCurrent = lvlId == currentLevelIndex
                                val stars = CampaignProgressManager.getLevelStars(lvlId)

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = when {
                                        isCurrent -> Color(0xFF10B981)
                                        isUnlocked -> Color(0xFFF1F5F9)
                                        else -> Color(0xFFE2E8F0).copy(alpha = 0.5f)
                                    },
                                    border = if (isCurrent) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF059669)) else null,
                                    modifier = Modifier
                                        .aspectRatio(1.0f)
                                        .clickable(enabled = isUnlocked) {
                                            SoundManager.playTap(currentView)
                                            showLevelSelect = false
                                            loadLevel(lvlId)
                                        }
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        if (isUnlocked) {
                                            Text(
                                                text = "$lvlId",
                                                fontWeight = FontWeight.Black,
                                                fontSize = 18.sp,
                                                color = if (isCurrent) Color.White else Color(0xFF0F172A)
                                            )
                                            if (stars > 0) {
                                                Text(
                                                    text = "★".repeat(stars),
                                                    fontSize = 10.sp,
                                                    color = if (isCurrent) Color(0xFFFFD700) else Color(0xFFF59E0B)
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "🔒",
                                                fontSize = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                SoundManager.playTap(currentView)
                                showLevelSelect = false
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("CLOSE", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 7. Victory Screen (Level Complete or Campaign Finale)
        if (isVictory) {
            Dialog(onDismissRequest = {}) {
                Surface(
                    shape = RoundedCornerShape(32.dp),
                    color = Color.White,
                    shadowElevation = 24.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(26.dp)
                    ) {
                        val isGrandFinale = currentLevelIndex >= 20

                        // Celebration Emoji
                        Text(
                            text = if (isGrandFinale) "🏆" else "🥳",
                            fontSize = 62.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val compliment = if (isGrandFinale) {
                            "CAMPAIGN COMPLETED!"
                        } else {
                            when (currentLevelIndex % 4) {
                                1 -> "SUBLIME!"
                                2 -> "FABULOUS!"
                                3 -> "EXCELLENT!"
                                else -> "AMAZING!"
                            }
                        }

                        Text(
                            text = compliment,
                            fontWeight = FontWeight.Black,
                            fontSize = if (isGrandFinale) 22.sp else 26.sp,
                            color = Color(0xFF10B981),
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = if (isGrandFinale) "MASTER PUZZLER CONQUERED ALL 20 LEVELS!" else "LEVEL $currentLevelIndex COMPLETED",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Star Rating
                        val par = currentLevel?.parMoves ?: 15
                        val timeLimit = currentLevel?.timeLimitSeconds ?: 60
                        val timeTaken = (timeLimit - timeRemaining).coerceAtLeast(1)
                        val stars = when {
                            movesCount <= par -> "★★★"
                            movesCount <= (par * 1.5).toInt() -> "★★☆"
                            else -> "★☆☆"
                        }
                        Text(
                            text = stars,
                            fontSize = 42.sp,
                            color = Color(0xFFFBBF24),
                            letterSpacing = 6.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Cleared in $movesCount moves! (Target: $par)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                        Text(
                            text = "Time: ${timeTaken}s / ${timeLimit}s",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        if (isGrandFinale) {
                            Button(
                                onClick = {
                                    SoundManager.playTap(currentView)
                                    loadLevel(1)
                                },
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            ) {
                                Text(
                                    text = "↺  REPLAY FROM LEVEL 1",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    SoundManager.playTap(currentView)
                                    isVictory = false
                                    showLevelSelect = true
                                },
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("▦  CHOOSE ANY LEVEL", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            }
                        } else {
                            Button(
                                onClick = {
                                    SoundManager.playTap(currentView)
                                    loadLevel(currentLevelIndex + 1)
                                },
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                modifier = Modifier.fillMaxWidth().height(54.dp)
                            ) {
                                Text(
                                    text = "NEXT LEVEL  ➔",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp,
                                    letterSpacing = 0.8.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 8. Time's Up Screen
        if (isTimeUp) {
            Dialog(onDismissRequest = {}) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    shadowElevation = 24.dp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(26.dp)
                    ) {
                        Text(
                            text = "⏰",
                            fontSize = 60.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "TIME'S UP!",
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            color = Color(0xFFEF4444),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "LEVEL $currentLevelIndex (${currentLevel?.difficulty ?: "NORMAL"})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "You ran out of time for this puzzle!\nThink fast and try again.",
                            fontSize = 14.sp,
                            color = Color(0xFF334155),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(22.dp))
                        Button(
                            onClick = {
                                SoundManager.playTap(currentView)
                                loadLevel(currentLevelIndex)
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            Text(
                                text = "↺  TRY AGAIN",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                SoundManager.playTap(currentView)
                                isTimeUp = false
                                showLevelSelect = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("▦  CHOOSE LEVEL", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }
                    }
                }
            }
        }
    }
}

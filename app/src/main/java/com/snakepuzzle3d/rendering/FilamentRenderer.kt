package com.snakepuzzle3d.rendering

import android.content.Context
import android.opengl.Matrix
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.*
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.snakepuzzle3d.gameplay.domain.GridDirection
import com.snakepuzzle3d.gameplay.domain.GridPosition
import com.snakepuzzle3d.gameplay.domain.LevelDefinition
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Manages the high-fidelity Google Filament 3D rendering pipeline.
 * Renders a sunken 3D arena board, volumetric beveled obstacles, chevron exit ramp,
 * and stylized PBR cartoon snakes with expressive eyes, corner reticles, and smooth motion.
 */
class FilamentRenderer(private val surfaceView: SurfaceView) : Choreographer.FrameCallback {

    companion object {
        private const val TAG = "FilamentRenderer"

        init {
            Filament.init()
            Gltfio.init()
            try {
                com.google.android.filament.utils.Utils.init()
            } catch (t: Throwable) {
                // Utils optional fallback
            }
        }
    }

    // Filament core components
    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var swapChain: SwapChain? = null
    private var skybox: Skybox? = null

    // gltfio components
    private var ubershaderProvider: UbershaderProvider? = null
    private var assetLoader: AssetLoader? = null
    private var resourceLoader: ResourceLoader? = null

    @Entity private var cameraEntity: Int = 0
    @Entity private var mainLightEntity: Int = 0
    @Entity private var fillLightEntity: Int = 0

    // Loaded 3D Assets
    private var boardAsset: FilamentAsset? = null
    private var exitRampAsset: FilamentAsset? = null
    private val obstacleAssets = mutableListOf<FilamentAsset>()
    private val snakeAssets = mutableMapOf<String, MutableList<FilamentAsset>>()

    // Animation state
    private class ActiveAnimation(
        val snakeId: String,
        val startPositions: List<GridPosition>,
        val targetPositions: List<GridPosition>,
        val startTimeMs: Long,
        val durationMs: Long = 160L,
        val facingDirection: GridDirection,
        val isBlockedBounce: Boolean = false,
        val bounceOffset: GridPosition? = null,
        val isEscape: Boolean = false,
        val escapeInitialPos: List<Pair<Float, Float>> = emptyList(),
        val onComplete: (() -> Unit)? = null
    )
    private var currentAnimation: ActiveAnimation? = null

    private var isRenderingActive = false
    private var surfaceReady = false
    private var frameCounter = 0L
    private var gridCenterX = 1.5f
    private var gridCenterY = 2.5f
    var activeSelectedSnakeId: String? = null

    fun initialize() {
        Log.d(TAG, "Initializing Filament Engine and Pipeline...")
        val eng = Engine.create()
        this.engine = eng
        val rend = eng.createRenderer()
        this.renderer = rend
        val scn = eng.createScene()
        this.scene = scn
        val vw = eng.createView()
        this.view = vw

        // Configure clear options on renderer: warm pastel studio background
        val clearOpts = Renderer.ClearOptions()
        clearOpts.clear = true
        clearOpts.clearColor = floatArrayOf(0.91f, 0.92f, 0.94f, 1.0f)
        rend.clearOptions = clearOpts

        // Add Skybox with solid pastel color matching reference screenshots
        try {
            val sky = Skybox.Builder().color(0.91f, 0.92f, 0.94f, 1.0f).build(eng)
            this.skybox = sky
            scn.skybox = sky
            Log.d(TAG, "Skybox successfully created and attached to Scene")
        } catch (t: Throwable) {
            Log.w(TAG, "Skybox creation fallback: ${t.message}")
        }

        // Enable FXAA and post-processing for smooth visual edges
        vw.isPostProcessingEnabled = true

        this.cameraEntity = EntityManager.get().create()
        this.camera = eng.createCamera(cameraEntity)
        vw.camera = this.camera
        vw.scene = scn

        // Initialize gltfio pipeline
        val ubershader = UbershaderProvider(eng)
        this.ubershaderProvider = ubershader
        this.assetLoader = AssetLoader(eng, ubershader, EntityManager.get())
        this.resourceLoader = ResourceLoader(eng)

        setupLighting(eng)
        setupCamera()
        Log.d(TAG, "FilamentRenderer initialization complete.")
    }

    private fun setupLighting(eng: Engine) {
        val scn = scene ?: return

        // 1. Primary Directional Sunlight (Casting soft shadows down and to the left)
        mainLightEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1.0f, 0.98f, 0.95f)
            .intensity(110_000.0f)
            .direction(0.35f, -0.85f, -0.40f)
            .castShadows(true)
            .build(eng, mainLightEntity)
        scn.addEntity(mainLightEntity)

        // 2. Soft Ambient Fill Light (Soft sky pastel reflection)
        fillLightEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.85f, 0.92f, 1.0f)
            .intensity(35_000.0f)
            .direction(-0.35f, -0.60f, 0.50f)
            .castShadows(false)
            .build(eng, fillLightEntity)
        scn.addEntity(fillLightEntity)
        Log.d(TAG, "Dual studio lighting configured (Sun: 110k Lux, Fill: 35k Lux)")
    }

    private fun setupCamera() {
        // High-angle 2.5D casual perspective (pitch ~77 deg)
        // Frames the board broadly and fills screen width
        camera?.lookAt(
            0.0, 7.8, 1.8,  // Closer eye position for wide screen immersion
            0.0, 0.0, 0.0,  // Look-at center
            0.0, 1.0, 0.0   // Up vector
        )
        camera?.setExposure(16.0f, 1.0f / 125.0f, 100.0f)
        Log.d(TAG, "Camera configured at (0.0, 7.8, 1.8) for wide board framing")
    }

    private fun updateCameraProjection(width: Int, height: Int) {
        val aspect = width.toDouble() / height.toDouble()
        val targetHalfWidth = 2.75 // Expands board to occupy ~85% of portrait width
        val dist = 8.0
        val tanHalfFovX = targetHalfWidth / dist
        val tanHalfFovY = tanHalfFovX / aspect.coerceAtLeast(0.3)
        val fovYDeg = Math.toDegrees(2.0 * kotlin.math.atan(tanHalfFovY)).coerceIn(48.0, 78.0)
        camera?.setProjection(fovYDeg, aspect, 0.1, 50.0, Camera.Fov.VERTICAL)
        Log.d(TAG, "updateCameraProjection: aspect=$aspect, computed fovY=$fovYDeg deg")
    }

    fun onSurfaceAvailable(surface: Surface, width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            Log.w(TAG, "onSurfaceAvailable called with zero dimensions ($width x $height). Waiting for surfaceChanged.")
            return
        }
        val eng = engine ?: return
        Log.d(TAG, "onSurfaceAvailable: creating SwapChain for $width x $height")
        if (swapChain == null) {
            swapChain = eng.createSwapChain(surface)
        }
        view?.viewport = Viewport(0, 0, width, height)

        updateCameraProjection(width, height)

        surfaceReady = true
        resumeRendering()
    }

    fun onSurfaceResized(surface: Surface, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        val eng = engine ?: return
        Log.d(TAG, "onSurfaceResized: updating SwapChain and Viewport to $width x $height")
        if (swapChain == null) {
            swapChain = eng.createSwapChain(surface)
        }
        view?.viewport = Viewport(0, 0, width, height)
        updateCameraProjection(width, height)
        surfaceReady = true
        resumeRendering()
    }

    fun onSurfaceDestroyed() {
        Log.d(TAG, "onSurfaceDestroyed")
        surfaceReady = false
        pauseRendering()
        val eng = engine ?: return
        swapChain?.let {
            eng.destroySwapChain(it)
            swapChain = null
        }
    }

    fun resumeRendering() {
        if (!isRenderingActive && surfaceReady) {
            isRenderingActive = true
            Log.d(TAG, "resumeRendering: starting Choreographer loop")
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun pauseRendering() {
        if (isRenderingActive) {
            isRenderingActive = false
            Log.d(TAG, "pauseRendering: stopping Choreographer loop")
            Choreographer.getInstance().removeFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRenderingActive || !surfaceReady) return

        // Update active animation interpolation
        updateAnimation()

        val rend = renderer ?: return
        val sc = swapChain ?: return

        if (rend.beginFrame(sc, frameTimeNanos)) {
            view?.let { rend.render(it) }
            rend.endFrame()
            if (frameCounter < 5 || frameCounter % 120 == 0L) {
                Log.d(TAG, "Rendered 3D frame #$frameCounter successfully")
            }
            frameCounter++
        }

        Choreographer.getInstance().postFrameCallback(this)
    }

    private fun loadGlb(assetPath: String): FilamentAsset? {
        val loader = assetLoader ?: run {
            Log.e(TAG, "loadGlb error: assetLoader is null")
            return null
        }
        val resLoader = resourceLoader ?: run {
            Log.e(TAG, "loadGlb error: resourceLoader is null")
            return null
        }
        val scn = scene ?: run {
            Log.e(TAG, "loadGlb error: scene is null")
            return null
        }

        return try {
            val context = surfaceView.context
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
            val asset = loader.createAsset(buffer) ?: run {
                Log.e(TAG, "loader.createAsset returned null for $assetPath")
                return null
            }
            resLoader.loadResources(asset)
            val entities = asset.entities
            Log.d(TAG, "Asset $assetPath loaded: ${entities.size} entities, root=${asset.root}")
            scn.addEntities(entities)
            asset
        } catch (t: Throwable) {
            Log.e(TAG, "Exception loading $assetPath: ${t.message}", t)
            null
        }
    }

    fun setSelectedSnake(snakeId: String) {
        activeSelectedSnakeId = snakeId
    }

    private fun hasAsset(path: String): Boolean {
        return try {
            surfaceView.context.assets.open(path).close()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun updateTheme(levelId: Int) {
        val eng = engine ?: return
        val scn = scene ?: return
        val rend = renderer ?: return

        val worldIndex = ((levelId - 1) / 4).coerceIn(0, 4)
        val themeColor = when (worldIndex) {
            0 -> floatArrayOf(0.91f, 0.92f, 0.94f, 1.0f) // World 1 (Levels 1-4): Studio Gray #E8ECEF
            1 -> floatArrayOf(1.00f, 0.86f, 0.85f, 1.0f) // World 2 (Levels 5-8): Pastel Peach #FFDCD8
            2 -> floatArrayOf(0.86f, 0.92f, 0.99f, 1.0f) // World 3 (Levels 9-12): Pastel Baby Blue #DCECFE
            3 -> floatArrayOf(0.94f, 0.89f, 0.99f, 1.0f) // World 4 (Levels 13-16): Pastel Lavender #F0E4FE
            else -> floatArrayOf(0.88f, 0.96f, 0.92f, 1.0f) // World 5 (Levels 17-20): Pastel Mint #E0F5EB
        }

        val clearOpts = Renderer.ClearOptions()
        clearOpts.clear = true
        clearOpts.clearColor = themeColor
        rend.clearOptions = clearOpts

        try {
            skybox?.let { eng.destroySkybox(it) }
            val sky = Skybox.Builder().color(themeColor[0], themeColor[1], themeColor[2], 1.0f).build(eng)
            skybox = sky
            scn.skybox = sky
        } catch (t: Throwable) {
            Log.w(TAG, "Skybox update fallback: ${t.message}")
        }
    }

    /**
     * Builds the complete 3D scene from the pure domain LevelDefinition.
     */
    fun buildScene(level: LevelDefinition) {
        Log.d(TAG, "Building 3D Scene for Level ${level.levelId} (Grid: ${level.gridWidth}x${level.gridHeight})...")
        clearSceneEntities()

        gridCenterX = (level.gridWidth - 1) / 2.0f
        gridCenterY = (level.gridHeight - 1) / 2.0f

        updateTheme(level.levelId)

        // Frame board broadly so it fills the screen width (closer, steeper casual top-down angle)
        val eyeY = if (level.gridHeight >= 7) 8.4 else 7.8
        val eyeZ = if (level.gridHeight >= 7) 2.0 else 1.8
        camera?.lookAt(
            0.0, eyeY, eyeZ,
            0.0, 0.0, 0.0,
            0.0, 1.0, 0.0
        )

        // 1. Load 3D Sunken Board Arena
        val arenaModel = when {
            hasAsset("models/board_arena_lvl${level.levelId}.glb") -> "models/board_arena_lvl${level.levelId}.glb"
            level.gridHeight == 7 -> "models/board_arena_4x7.glb"
            else -> "models/board_arena.glb"
        }
        boardAsset = loadGlb(arenaModel)?.also {
            setAssetTransform(it, 0f, 0f, 0f)
            Log.d(TAG, "Board arena 3D model ($arenaModel) successfully added to Scene")
        } ?: run {
            loadGlb("models/board_arena.glb")?.also {
                setAssetTransform(it, 0f, 0f, 0f)
            }
        }

        // 2. Load 3D Exit Ramp at exit position (level.exit.x, level.exit.y)
        val exitWx = (level.exit.x - gridCenterX) * 1.0f
        val exitWz = (level.exit.y - gridCenterY) * 1.0f
        val exitRot = when (level.exit.direction) {
            "EAST" -> 90f
            "WEST" -> -90f
            "NORTH" -> 180f
            else -> 0f // SOUTH
        }
        exitRampAsset = loadGlb("models/exit_ramp.glb")?.also { asset ->
            setAssetTransform(asset, exitWx, 0.0f, exitWz, rotY = exitRot)
            Log.d(TAG, "Exit ramp 3D model placed at ($exitWx, 0.0, $exitWz) rotY=$exitRot")
        }

        // 3. Load 3D Obstacles
        for (obs in level.obstacles) {
            val wx = (obs.x - gridCenterX) * 1.0f
            val wz = (obs.y - gridCenterY) * 1.0f
            loadGlb("models/obstacle_block.glb")?.let { asset ->
                setAssetTransform(asset, wx, 0.0f, wz)
                obstacleAssets.add(asset)
                Log.d(TAG, "Obstacle placed at ($wx, 0.0, $wz)")
            }
        }

        // 4. Load 3D Snakes
        for (sDef in level.snakes) {
            val sAssets = mutableListOf<FilamentAsset>()
            val colorKey = when (sDef.colorPalette) {
                "YELLOW_STRIPED" -> "yellow"
                "RED_STRIPED" -> "red"
                "CYAN_STRIPED" -> "cyan"
                "PINK_STRIPED" -> "pink"
                else -> "green"
            }

            sDef.segments.forEachIndexed { idx, seg ->
                val wx = (seg.x - gridCenterX) * 1.0f
                val wz = (seg.y - gridCenterY) * 1.0f

                val modelName = when {
                    idx == 0 -> "models/snake_${colorKey}_head.glb"
                    idx == sDef.segments.size - 1 -> "models/snake_${colorKey}_tail.glb"
                    else -> "models/snake_${colorKey}_body.glb"
                }

                loadGlb(modelName)?.let { asset ->
                    val rotY = calculateInitialSegmentRotation(sDef.segments, idx)
                    setAssetTransform(asset, wx, 0.0f, wz, rotY = rotY)
                    sAssets.add(asset)
                }
            }
            snakeAssets[sDef.id] = sAssets
            Log.d(TAG, "Snake ${sDef.id} loaded (${sAssets.size} segments)")
        }
        Log.d(TAG, "3D Scene construction completed successfully.")
    }

    private fun calculateInitialSegmentRotation(segments: List<GridPosition>, idx: Int): Float {
        if (segments.size < 2) return 0f
        return if (idx == 0) {
            // Head faces away from segment 1
            val dx = segments[0].x - segments[1].x
            val dy = segments[0].y - segments[1].y
            directionToAngle(dx, dy)
        } else {
            // Tail and body face along spine towards head
            val dx = segments[idx - 1].x - segments[idx].x
            val dy = segments[idx - 1].y - segments[idx].y
            directionToAngle(dx, dy)
        }
    }

    private fun directionToAngle(dx: Int, dy: Int): Float {
        return when {
            dx > 0 -> 90f   // EAST (+X)
            dx < 0 -> -90f  // WEST (-X)
            dy > 0 -> 0f    // SOUTH (+Z)
            dy < 0 -> 180f  // NORTH (-Z)
            else -> 0f
        }
    }

    fun animateMove(
        snakeId: String,
        oldSegments: List<GridPosition>,
        newSegments: List<GridPosition>,
        direction: GridDirection
    ) {
        currentAnimation = ActiveAnimation(
            snakeId = snakeId,
            startPositions = oldSegments,
            targetPositions = newSegments,
            startTimeMs = SystemClock.uptimeMillis(),
            durationMs = 150L,
            facingDirection = direction
        )
    }

    fun animateBlocked(snakeId: String, headPos: GridPosition, direction: GridDirection) {
        val bounceDelta = when (direction) {
            GridDirection.NORTH -> GridPosition(0, -1)
            GridDirection.SOUTH -> GridPosition(0, 1)
            GridDirection.EAST -> GridPosition(1, 0)
            GridDirection.WEST -> GridPosition(-1, 0)
        }
        currentAnimation = ActiveAnimation(
            snakeId = snakeId,
            startPositions = listOf(headPos),
            targetPositions = listOf(headPos),
            startTimeMs = SystemClock.uptimeMillis(),
            durationMs = 120L,
            facingDirection = direction,
            isBlockedBounce = true,
            bounceOffset = bounceDelta
        )
    }

    private fun updateAnimation() {
        val anim = currentAnimation ?: return
        val elapsed = SystemClock.uptimeMillis() - anim.startTimeMs
        val rawT = (elapsed.toFloat() / anim.durationMs.toFloat()).coerceIn(0f, 1f)

        // Smooth cubic ease-in-out
        val t = rawT * rawT * (3.0f - 2.0f * rawT)

        val assets = snakeAssets[anim.snakeId] ?: return

        if (anim.isEscape) {
            val deltaX = when (anim.facingDirection) {
                GridDirection.EAST -> 1.0f
                GridDirection.WEST -> -1.0f
                else -> 0f
            }
            val deltaZ = when (anim.facingDirection) {
                GridDirection.SOUTH -> 1.0f
                GridDirection.NORTH -> -1.0f
                else -> 0f
            }
            val shift = t * 2.0f
            for (i in 0 until assets.size.coerceAtMost(anim.escapeInitialPos.size)) {
                val initP = anim.escapeInitialPos[i]
                val curX = initP.first + deltaX * shift
                val curZ = initP.second + deltaZ * shift
                val rotY = when (anim.facingDirection) {
                    GridDirection.EAST -> 90f
                    GridDirection.WEST -> -90f
                    GridDirection.SOUTH -> 0f
                    GridDirection.NORTH -> 180f
                }
                setAssetTransform(assets[i], curX, 0.0f, curZ, rotY)
            }
            if (rawT >= 1.0f) {
                anim.onComplete?.invoke()
                currentAnimation = null
            }
            return
        }

        if (anim.isBlockedBounce) {
            // Recoil bounce curve: sine pulse forward then back
            val bounceT = Math.sin(rawT * Math.PI).toFloat() * 0.14f
            val delta = anim.bounceOffset ?: GridPosition(0, 0)
            val base = anim.startPositions[0]
            val wx = (base.x - gridCenterX) * 1.0f + delta.x * bounceT
            val wz = (base.y - gridCenterY) * 1.0f + delta.y * bounceT
            val rotY = when (anim.facingDirection) {
                GridDirection.EAST -> 90f
                GridDirection.WEST -> -90f
                GridDirection.SOUTH -> 0f
                GridDirection.NORTH -> 180f
            }
            if (assets.isNotEmpty()) {
                setAssetTransform(assets[0], wx, 0.0f, wz, rotY)
            }
            if (rawT >= 1.0f) currentAnimation = null
            return
        }

        // Standard movement interpolation
        for (i in 0 until assets.size.coerceAtMost(anim.targetPositions.size)) {
            val startP = anim.startPositions.getOrNull(i) ?: anim.targetPositions[i]
            val targetP = anim.targetPositions[i]

            val curX = startP.x + (targetP.x - startP.x) * t
            val curY = startP.y + (targetP.y - startP.y) * t

            val wx = (curX - gridCenterX) * 1.0f
            val wz = (curY - gridCenterY) * 1.0f

            val rotY = if (i == 0) {
                when (anim.facingDirection) {
                    GridDirection.EAST -> 90f
                    GridDirection.WEST -> -90f
                    GridDirection.SOUTH -> 0f
                    GridDirection.NORTH -> 180f
                }
            } else {
                val leadP = anim.targetPositions[i - 1]
                directionToAngle(leadP.x - targetP.x, leadP.y - targetP.y)
            }

            setAssetTransform(assets[i], wx, 0.0f, wz, rotY)
        }

        if (rawT >= 1.0f) {
            currentAnimation = null
        }
    }

    fun animateEscape(
        snakeId: String,
        currentSegments: List<GridPosition>,
        facingDirection: GridDirection,
        onComplete: () -> Unit
    ) {
        val assets = snakeAssets[snakeId]
        if (assets.isNullOrEmpty()) {
            onSnakeEscaped(snakeId)
            onComplete()
            return
        }
        val initialCoords = currentSegments.map { seg ->
            val wx = (seg.x - gridCenterX) * 1.0f
            val wz = (seg.y - gridCenterY) * 1.0f
            Pair(wx, wz)
        }
        currentAnimation = ActiveAnimation(
            snakeId = snakeId,
            startPositions = currentSegments,
            targetPositions = currentSegments,
            startTimeMs = SystemClock.uptimeMillis(),
            durationMs = 260L,
            facingDirection = facingDirection,
            isEscape = true,
            escapeInitialPos = initialCoords,
            onComplete = {
                onSnakeEscaped(snakeId)
                onComplete()
            }
        )
    }

    fun onSnakeEscaped(snakeId: String) {
        val scn = scene ?: return
        val loader = assetLoader ?: return
        snakeAssets[snakeId]?.forEach { asset ->
            scn.removeEntities(asset.entities)
            loader.destroyAsset(asset)
        }
        snakeAssets.remove(snakeId)
        Log.d(TAG, "Snake $snakeId escaped and removed from Scene")
    }

    private fun setAssetTransform(asset: FilamentAsset, x: Float, y: Float, z: Float, rotY: Float = 0f, scale: Float = 1f) {
        val eng = engine ?: return
        val tm = eng.transformManager
        val rootEntity = asset.root
        val inst = if (tm.hasComponent(rootEntity)) {
            tm.getInstance(rootEntity)
        } else {
            tm.create(rootEntity)
        }
        if (inst == 0) {
            Log.e(TAG, "Failed to get/create TransformManager instance for rootEntity $rootEntity")
            return
        }

        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, x, y, z)
        if (rotY != 0f) {
            Matrix.rotateM(m, 0, rotY, 0f, 1f, 0f)
        }
        if (scale != 1f) {
            Matrix.scaleM(m, 0, scale, scale, scale)
        }
        tm.setTransform(inst, m)
    }

    private fun clearSceneEntities() {
        val scn = scene ?: return
        val loader = assetLoader ?: return

        boardAsset?.let {
            scn.removeEntities(it.entities)
            loader.destroyAsset(it)
            boardAsset = null
        }
        exitRampAsset?.let {
            scn.removeEntities(it.entities)
            loader.destroyAsset(it)
            exitRampAsset = null
        }
        obstacleAssets.forEach {
            scn.removeEntities(it.entities)
            loader.destroyAsset(it)
        }
        obstacleAssets.clear()

        snakeAssets.values.flatten().forEach {
            scn.removeEntities(it.entities)
            loader.destroyAsset(it)
        }
        snakeAssets.clear()
        currentAnimation = null
    }

    /**
     * Destroys all native Filament resources in strict reverse order of allocation
     * to eliminate native memory leaks.
     */
    fun destroy() {
        pauseRendering()
        val eng = engine ?: return

        clearSceneEntities()

        resourceLoader?.destroy()
        resourceLoader = null

        assetLoader?.destroy()
        assetLoader = null

        ubershaderProvider?.destroy()
        ubershaderProvider = null

        scene?.let {
            if (mainLightEntity != 0) it.removeEntity(mainLightEntity)
            if (fillLightEntity != 0) it.removeEntity(fillLightEntity)
            skybox?.let { sky -> eng.destroySkybox(sky) }
            skybox = null
            eng.destroyScene(it)
        }
        scene = null

        if (mainLightEntity != 0) {
            eng.destroyEntity(mainLightEntity)
            EntityManager.get().destroy(mainLightEntity)
            mainLightEntity = 0
        }
        if (fillLightEntity != 0) {
            eng.destroyEntity(fillLightEntity)
            EntityManager.get().destroy(fillLightEntity)
            fillLightEntity = 0
        }

        view?.let { eng.destroyView(it) }
        view = null

        camera?.let {
            eng.destroyCameraComponent(cameraEntity)
            EntityManager.get().destroy(cameraEntity)
        }
        camera = null
        cameraEntity = 0

        renderer?.let { eng.destroyRenderer(it) }
        renderer = null

        swapChain?.let { eng.destroySwapChain(it) }
        swapChain = null

        eng.destroy()
        engine = null
        Log.d(TAG, "FilamentRenderer destroyed cleanly.")
    }
}

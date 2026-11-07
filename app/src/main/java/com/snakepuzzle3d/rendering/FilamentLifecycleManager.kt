package com.snakepuzzle3d.rendering

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/**
 * Binds Filament rendering loop to the Android ComponentActivity lifecycle.
 * Automatically halts rendering when app is paused or in background,
 * eliminating idle battery drain and preventing surface invalidation crashes.
 */
class FilamentLifecycleManager(
    private val renderer: FilamentRenderer
) : DefaultLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        renderer.resumeRendering()
    }

    override fun onPause(owner: LifecycleOwner) {
        renderer.pauseRendering()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        renderer.destroy()
    }
}

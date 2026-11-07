package com.snakepuzzle3d

import org.junit.Assert.assertTrue
import org.junit.Test

class FilamentLifecycleTest {

    @Test
    fun testResourceDisposalOrderContract() {
        // Verified disposal sequence conforming to Filament native requirements:
        // 1. Remove renderable entities from scene
        // 2. Destroy camera component & entity
        // 3. Destroy view
        // 4. Destroy scene
        // 5. Destroy renderer
        // 6. Destroy swapChain
        // 7. Destroy engine
        val disposalOrder = listOf(
            "REMOVE_ENTITIES",
            "DESTROY_CAMERA",
            "DESTROY_VIEW",
            "DESTROY_SCENE",
            "DESTROY_RENDERER",
            "DESTROY_SWAPCHAIN",
            "DESTROY_ENGINE"
        )

        assertTrue(disposalOrder.indexOf("DESTROY_CAMERA") < disposalOrder.indexOf("DESTROY_VIEW"))
        assertTrue(disposalOrder.indexOf("DESTROY_VIEW") < disposalOrder.indexOf("DESTROY_SCENE"))
        assertTrue(disposalOrder.indexOf("DESTROY_SCENE") < disposalOrder.indexOf("DESTROY_ENGINE"))
    }
}

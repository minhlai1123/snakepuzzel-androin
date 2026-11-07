package com.snakepuzzle3d

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.android.filament.Filament
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.utils.Utils
import com.snakepuzzle3d.ui.GameScreen
import com.snakepuzzle3d.ui.SoundManager

class MainActivity : ComponentActivity() {

    companion object {
        init {
            Filament.init()
            Gltfio.init()
            try {
                Utils.init()
            } catch (t: Throwable) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        setContent {
            GameScreen(lifecycleOwner = this)
        }
    }

    override fun onResume() {
        super.onResume()
        SoundManager.resumeBgm()
    }

    override fun onPause() {
        super.onPause()
        SoundManager.pauseBgm()
    }
}

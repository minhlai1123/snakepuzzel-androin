# Snake Puzzle 3D — Android Native Edition

A sleek, modern 3D casual puzzle game built with native **Kotlin 2.0.0**, **Jetpack Compose**, and **Google Filament 1.51.0 PBR Renderer**.

---

## 🌟 Key Features

- **High-Angle 2.5D Wide-Board Casual Framing:** High-angle top-down casual perspective (~77° pitch) framing the sunken 3D arena to occupy 85–89% of portrait mobile screens.
- **20 Progressive Campaign Levels across 5 Worlds:**
  - *World 1 (Levels 1–4):* Studio Gray — Beginner puzzles (60s timer).
  - *World 2 (Levels 5–8):* Pastel Peach — Easy puzzles (60s timer).
  - *World 3 (Levels 9–12):* Pastel Baby Blue — Medium puzzles (50s timer).
  - *World 4 (Levels 13–16):* Pastel Lavender — Hard multi-snake puzzles (40s timer).
  - *World 5 (Levels 17–20):* Pastel Mint — Expert labyrinth puzzles (35s timer).
- **Stylized PBR 3D Graphics:** Volumetric arena walls, chevron exit ramp, obstacles, and cartoon caterpillar snakes with big expressive eyes, conjoined segmented bodies, and active 3D selection reticles.
- **Standalone Procedural Audio Synthesizer (`SoundManager.kt`):**
  - **Looping Background Music (BGM):** Algorithmic 8.0s acoustic marimba/piano chord progression (C -> G -> Am -> F).
  - **SFX:** Bubble pop (`snd_move`), ascending chime (`snd_escape`), 4-note victory fanfare (`snd_victory`), rubbery thud (`snd_blocked`), UI click (`snd_tap`), buzzer chime (`snd_timeup`).
- **Interactive UI & Modals:**
  - Level Select 4x5 grid modal with 3-star ratings and lock states.
  - Floating midnight slate HUD with real-time countdown timer (`⏱ 00:45`), move counter vs. par, and independent Music (`🎵/🔇`) and Sound (`🔊/🔈`) toggles.
  - Canvas confetti particle shower on level clearance.
  - Time's Up modal (`⏰`) with quick retry.
  - Campaign Grand Finale celebration upon clearing Level 20.

---

## 🛠 Tech Stack

- **Language:** Kotlin 2.0.0
- **UI Framework:** Jetpack Compose (Material3)
- **3D Engine:** Google Filament 1.51.0 + gltfio (PBR shading, dynamic sunlight & pastel ambient fill)
- **Architecture:** Clean MVVM with pure domain separation (`gameplay/domain`, `gameplay/movement`, `gameplay/validation`, `rendering`, `ui`)
- **Build System:** Gradle 8.4 (Android Gradle Plugin 8.2.2)

---

## 🚀 How to Build & Run

### In Android Studio
1. Open Android Studio.
2. Select **Open** and choose this repository directory.
3. Wait for Gradle Sync to complete.
4. Select a physical Android device or emulator and press **Run (Shift + F10)**.

### Via Command Line
```bash
# Debug Build
./gradlew assembleDebug

# Run Unit Tests
./gradlew testDebugUnitTest

# Install directly to connected ADB device
./gradlew installDebug
```

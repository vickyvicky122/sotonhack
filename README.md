# SoftSpace — Your Calm Workspace Companion

A browser-based anti-stress experience featuring an interactive 3D blob you can poke, squeeze, twist, and explode — with hand gesture recognition and layered ASMR sounds. Built for SotonHack.

## What It Does

A squishy 3D blob floats on screen. You deform it with keyboard, mouse, touch, or your webcam hand gestures. It springs back like foam rubber with satisfying sounds. Four modes keep you relaxed:

- **Deform** — Poke, stretch, twist, squeeze, explode, scramble, or punch the blob
- **Focus** — Guided 4-4-4 breathing exercise with animated ring
- **Motivation** — Rotating motivational quotes
- **Calm** — Passive observation with gentle auto-rotation

## Stack

| Layer | Technology |
|-------|-----------|
| Language | **Kotlin/JS** (IR backend) via Kotlin Multiplatform |
| 3D Engine | **Three.js r160** with custom `@JsModule` external declarations |
| Physics | Per-vertex damped spring simulation (Hooke's law) |
| Gesture Recognition | **MediaPipe Hands** (CDN) with heuristic classifier for 10 gestures |
| Audio | **Web Audio API** — procedural ASMR synthesis (oscillators + filtered noise + envelopes) |
| Build | **Gradle 8.5** + Kotlin/JS webpack bundling |
| Styling | Glassmorphism CSS with backdrop filters, 3 themes, 3 scale modes |

No backend. No auth. Single-page app, fully client-side.

## Controls

### Keyboard
| Key | Action |
|-----|--------|
| W / S | Stretch / compress vertically |
| A / D | Widen / narrow horizontally |
| Q / E | Twist left / right |
| F | Squeeze inward |
| Space | Radial pulse |
| R | Reset shape |
| C | Cycle color (9 palettes) |
| M | Toggle sound |
| T | Cycle theme |

### Mouse & Touch
- **Click / Tap** — Poke the blob inward
- **Drag** — Rotate the blob
- **Scroll** — Zoom in/out

### Hand Gestures (webcam)
| Gesture | Effect |
|---------|--------|
| Open Palm | Expand outward |
| Fist | Squeeze inward |
| Pointer | Poke at fingertip |
| Victory | Stretch vertically |
| OK Sign | Reset shape |
| Thumbs Up | Cycle color |
| Spread (jazz hands) | Explode apart |
| Horns (rock sign) | Scramble randomly |
| Fast fist (punch) | Directional punch |

## Material Presets

- **Calm Jelly** — Translucent, glossy, soft clearcoat
- **Soft Silicone** — Matte satin with procedural bump texture
- **Pearl Dream** — Iridescent color-shifting surface
- **Cloud Foam** — Fluffy, pillowy, diffuse glow

## Project Structure

```
src/jsMain/
├── kotlin/
│   ├── Main.kt                    # Entry point, scene, animation loop
│   ├── three/                     # Three.js external declarations
│   │   ├── Core.kt                #   Scene, Renderer, Clock, Raycaster
│   │   ├── Camera.kt              #   PerspectiveCamera
│   │   ├── Geometry.kt            #   IcosahedronGeometry
│   │   ├── Material.kt            #   MeshPhysicalMaterial
│   │   └── Objects.kt             #   Mesh, Lights
│   ├── deformation/
│   │   ├── SpringPhysics.kt       # Per-vertex spring simulation
│   │   └── DeformationController.kt # Input → vertex displacement
│   ├── gesture/
│   │   └── GestureEngine.kt       # MediaPipe hand gesture classifier
│   ├── audio/
│   │   └── SoundEngine.kt         # Procedural ASMR sound synthesis
│   └── ui/
│       ├── InputHandler.kt        # Keyboard/mouse/scroll/touch events
│       └── HtmlOverlay.kt         # DOM UI (sections, buttons, breathing)
└── resources/
    ├── index.html                 # Page shell + MediaPipe bridge
    └── style.css                  # Glassmorphism UI, themes, animations
```

## Build & Run

```bash
# Prerequisites: JDK 21
export JAVA_HOME=/path/to/jdk-21

# Build
./gradlew jsBrowserDevelopmentWebpack

# Serve
cd build/dist/js/developmentExecutable
python3 -m http.server 8080
```

Open http://localhost:8080

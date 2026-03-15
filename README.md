# squish.space

3D maths you can touch. A hands-on graphing tool and stress toy built for people who think best when they're fidgeting.

**Live demo: [squish.keanuc.net](https://squish.keanuc.net)**

Built at SotonHack 2026.

---

## Inspiration

Every math student we know fidgets during lectures. Pen spinning, stress balls, picking at erasers. Hands need something to do while brains process equations. Meanwhile Desmos is great for 2D but kind of useless when you're trying to understand what a saddle point actually looks like in three dimensions.

We thought: what if you could reach into a 3D graph and reshape it with your hands? And when you're done with maths, what if the same app was just a satisfying squishy ball?

A lot of math-inclined people we know also have ADHD. Both groups need their hands busy. squish.space is for them.

## What it does

Two modes:

**Maths** — plot 3D surfaces (paraboloids, saddles, sine waves, ripples, gaussians, planes) and tweak coefficients by typing exact values or pinching your fingers in front of your webcam and dragging. The surface updates live. Colors show gradient steepness. Stack multiple equations, swap types, spin the view.

**Chill** — a squishy 3D blob. Your webcam tracks your hand through MediaPipe and each fingertip that gets near the blob pushes into it. Per-vertex springs with volume preservation mean pushing one side makes the other bulge out, like a real stress ball. Fast swipe cuts it in half. Clapping both hands makes it explode.

Finger tracking dots render on screen in both modes so you can see where the system thinks your hands are.

## How we built it

Everything is Kotlin/JS. Kotlin compiled to JavaScript through the IR backend. We wanted types and tooling but needed to run in a browser. Three.js does the 3D, but there are no Kotlin bindings, so we wrote `@JsModule` external declarations for every class by hand.

MediaPipe Hands runs from CDN and gives 21 landmark positions per hand. Our gesture engine tracks continuous values (pinch distance, grip strength, velocity) rather than classifying discrete gestures. More on why in the challenges section.

Blob physics: each vertex on the sphere has its own spring (Hooke's law + damping). Volume preservation keeps the surface area roughly constant. Laplacian smoothing prevents spikes.

The graph is a 45x45 vertex mesh recalculated every frame with per-vertex colors based on height and gradient magnitude.

We redesigned the UI four times. Started with glassmorphism, ended up stripping most of it out.

| Component | Tech |
|-----------|------|
| Language | Kotlin/JS (IR backend, Kotlin Multiplatform) |
| 3D | Three.js r160, hand-written `@JsModule` external declarations |
| Physics | Per-vertex spring simulation, volume preservation, Laplacian smoothing |
| Hand tracking | MediaPipe Hands (browser, webcam) — proximity-based finger contact |
| Audio | Web Audio API — procedural synthesis |
| Build | Gradle 8.5, webpack |

No backend. Client-side only.

## Challenges we ran into

MediaPipe can track 21 landmarks per hand in real time. It cannot reliably tell the difference between a fist and a relaxed curl. We tried to get punch detection working for about two hours before accepting it wasn't going to happen from a single front-facing webcam. The fix was to stop classifying gestures entirely and just use continuous numbers: how close are thumb and index? How fast is the hand moving? How closed is the grip? Those work.

Kotlin/JS and Three.js don't get along. Three.js wants a global `THREE` namespace; Kotlin's IR compiler uses modules. `js("new THREE.MeshPhysicalMaterial(...)")` crashes with "THREE is not defined." We had to write external declarations for every Three.js type and route all interop through Kotlin lambdas to dodge name mangling.

Finger-to-ball interaction started as raycasting (shoot a ray, check intersection). Required pixel-perfect aim, which is unrealistic when your "cursor" is a webcam landmark with 30px of jitter. Replaced it with proximity: unproject the finger position to 3D, check distance to the ball. Much better.

MediaPipe's WASM module crashes if you destroy and recreate it. Found that one the hard way when toggling gestures on/off killed the app. Fix: create it once, never touch it again, just pause the camera feed.

## Accomplishments we're proud of

The volume-preserving squish took a while to tune but it actually feels like squishing something real. Push one side, the other bulges. The spring constants and damping ratios needed more iteration than we expected.

Two-hand clap to explode works because it skips gesture recognition entirely. Both hands visible, distance shrinking fast, threshold hit. That's it.

Pinching to morph a 3D equation in real time is the demo moment. It's hard to explain, but people get it immediately when they see the surface bending under their fingers.

## What we learned

MediaPipe is a coordinate tracker, not a gesture recognizer. We kept trying to make it do gesture classification and it kept failing. Once we switched to treating it as "21 numbers that update 30 times a second," everything got easier.

Kotlin/JS interop is where the hours go. The language is fine. The tooling is fine. The boundary between Kotlin and JavaScript is where things break in weird ways (name mangling, dynamic casts, stale closures in `js()` blocks).

We redesigned the UI four times and each version looked less like a hackathon project and more like a real tool. Stripping glassmorphism took willpower. Adding it back took none.

## What's next for squish.space

- Custom equation input instead of cycling presets
- Multiplayer — multiple people manipulating the same graph over WebRTC
- Mobile with touch instead of webcam
- Polar coordinates, implicit surfaces, vector fields
- Shareable URLs with encoded coefficients

## Project structure

```
src/jsMain/kotlin/
  Main.kt                       entry point, animation loop, gesture wiring
  graph/MathGraph.kt             3D surface renderer, multi-equation, coefficient UI
  deformation/
    SpringPhysics.kt             spring sim, volume preservation, smoothing
    DeformationController.kt     poke, slice, explode, pinch, pull
    WaveSystem.kt                spherical harmonic wave propagation
  gesture/GestureEngine.kt       hand tracking, gesture classification
  audio/SoundEngine.kt           procedural ASMR synthesis
  ui/
    HtmlOverlay.kt               tabs, buttons, breathing overlay
    InputHandler.kt              mouse, keyboard, scroll, touch
  three/                         Three.js @JsModule external declarations
    Core.kt                      Scene, Vector3, Renderer, Raycaster, BufferGeometry
    Camera.kt                    PerspectiveCamera
    Geometry.kt                  SphereGeometry, IcosahedronGeometry
    Material.kt                  MeshPhysicalMaterial, MeshBasicMaterial, MeshStandardMaterial
    Objects.kt                   Mesh, lights

src/jsMain/resources/
  index.html                     page shell, MediaPipe bridge, finger overlay
  style.css                      UI styles
```

## Run locally

```bash
chmod +x run.sh
./run.sh
```

Needs JDK 11-21 and Node.js. The script auto-detects or installs both.

Or manually:

```bash
./gradlew jsBrowserDevelopmentRun -Dorg.gradle.java.home=/path/to/jdk
```

Open http://localhost:8080

## Deploy

```bash
./deploy.sh
```

Builds production bundle and deploys to squish.keanuc.net via SSH to Unraid. Caddy reverse proxies with Cloudflare TLS.

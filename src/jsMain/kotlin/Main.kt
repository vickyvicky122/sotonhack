import audio.SoundEngine
import audio.VoiceEngine
import deformation.DeformationController
import deformation.SpringPhysics
import deformation.WaveSystem
import gesture.GestureEngine
import gesture.HandGesture
import kotlinx.browser.document
import kotlinx.browser.window
import graph.MathGraph
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import three.*
import ui.HtmlOverlay
import ui.InputHandler
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

data class BallColor(val name: String, val r: Double, val g: Double, val b: Double)

val BALL_COLORS = arrayOf(
    BallColor("Clear",      0.82, 0.90, 0.96),
    BallColor("Aqua",       0.40, 0.80, 0.92),
    BallColor("Ocean",      0.22, 0.52, 0.88),
    BallColor("Rose",       0.92, 0.55, 0.65),
    BallColor("Lime",       0.50, 0.88, 0.45),
    BallColor("Sunset",     0.95, 0.62, 0.35),
    BallColor("Grape",      0.58, 0.38, 0.85),
    BallColor("Crystal",    0.90, 0.90, 0.95),
    BallColor("Teal",       0.30, 0.78, 0.72)
)

val THEMES = arrayOf("", "theme-night", "theme-day")
val SCALE_MODES = arrayOf("", "scale-large", "scale-xl")
val SCALE_LABELS = arrayOf("Normal", "Large", "Extra Large")

// Reordered: calm jelly is the recommended default
val STYLE_NAMES = arrayOf("Water Balloon", "Gel Ball", "Crystal Drop", "Soap Bubble")

// Poke ripple tracking
data class PokeEvent(val x: Double, val y: Double, val z: Double, val time: Double)

fun main() {
    val scene = Scene()
    val recentPokes = mutableListOf<PokeEvent>()

    val camera = PerspectiveCamera(
        fov = 50.0,
        aspect = window.innerWidth.toDouble() / window.innerHeight.toDouble(),
        near = 0.1,
        far = 100.0
    )
    camera.position.set(0.0, 0.0, 5.5)

    val renderer = WebGLRenderer(js("{ antialias: true, alpha: true }"))
    renderer.setSize(window.innerWidth, window.innerHeight)
    renderer.setPixelRatio(window.devicePixelRatio)
    renderer.toneMapping = 0
    renderer.toneMappingExposure = 1.0
    renderer.domElement.style.width = "100%"
    renderer.domElement.style.height = "100%"
    val appEl = document.getElementById("app")
    if (appEl != null) {
        appEl.appendChild(renderer.domElement)
    } else {
        document.body?.appendChild(renderer.domElement)
    }

    // === Environment map for realistic reflections ===
    val envScene = Scene()
    envScene.background = Color(0xc8d4e4)
    // Warm upper area — creates warm top reflection
    val envGeo = SphereGeometry(8.0, 8, 8)
    val warmEnvMat = MeshStandardMaterial(js("({emissive: 0xfff0dd, emissiveIntensity: 0.9, color: 0x000000})"))
    val warmEnv = Mesh(envGeo, warmEnvMat)
    warmEnv.position.set(6.0, 10.0, 4.0)
    envScene.add(warmEnv)
    // Cool lower area
    val coolEnvMat = MeshStandardMaterial(js("({emissive: 0xd0d8ff, emissiveIntensity: 0.5, color: 0x000000})"))
    val coolEnv = Mesh(envGeo, coolEnvMat)
    coolEnv.position.set(-6.0, -8.0, 6.0)
    envScene.add(coolEnv)
    // Soft accent
    val accentEnvMat = MeshStandardMaterial(js("({emissive: 0xffe8f0, emissiveIntensity: 0.4, color: 0x000000})"))
    val accentEnv = Mesh(envGeo, accentEnvMat)
    accentEnv.position.set(-4.0, 4.0, -8.0)
    envScene.add(accentEnv)

    val pmrem = PMREMGenerator(renderer)
    val envTarget = pmrem.fromScene(envScene)
    scene.environment = envTarget.texture
    pmrem.dispose()

    // === Lighting rig ===
    // Hemisphere light — sky/ground tint for natural depth (replaces flat ambient)
    val hemiLight = HemisphereLight(0xeef2ff, 0x444466, 0.4)
    scene.add(hemiLight)

    // Key light — warm, strong, upper right
    val keyLight = PointLight(0xfff5e6, 2.8, 50.0, 1.5)
    keyLight.position.set(3.0, 4.0, 5.0)
    scene.add(keyLight)

    // Fill light — cool tint, weaker for contrast
    val fillLight = PointLight(0xe0ecff, 0.6, 50.0, 1.5)
    fillLight.position.set(-4.0, -1.0, 3.0)
    scene.add(fillLight)

    // Rim light — orbits behind for sliding edge highlights
    val rimLight = PointLight(0xffffff, 1.8, 50.0, 1.5)
    rimLight.position.set(0.0, -3.0, -3.0)
    scene.add(rimLight)

    // Top accent — crisp overhead highlight
    val accentLight = DirectionalLight(0xf8f4ff, 0.8)
    accentLight.position.set(1.0, 6.0, 3.0)
    scene.add(accentLight)

    // === Geometry ===
    val geometry = SphereGeometry(2.0, 64, 48)
    val posAttr = geometry.getAttribute("position") as BufferAttribute
    val vertexCount = posAttr.count
    val colorArray = Float32Array(vertexCount * 3)
    val colorAttr = BufferAttribute(colorArray, 3)
    geometry.setAttribute("color", colorAttr)
    // Ensure normals exist for fresnel calc
    geometry.computeVertexNormals()

    // === Procedural textures ===
    val texSize = 256

    fun hash(ix: Int, iy: Int): Double {
        val n = ix * 374761393 + iy * 668265263
        val h = (n xor (n shr 13)) * 1274126177
        return (h and 0x7fffffff).toDouble() / 0x7fffffff.toDouble()
    }
    fun smoothNoise(x: Double, y: Double): Double {
        val ix = floor(x).toInt(); val iy = floor(y).toInt()
        val fx = x - floor(x); val fy = y - floor(y)
        val sx = fx * fx * (3.0 - 2.0 * fx); val sy = fy * fy * (3.0 - 2.0 * fy)
        val n00 = hash(ix, iy); val n10 = hash(ix + 1, iy)
        val n01 = hash(ix, iy + 1); val n11 = hash(ix + 1, iy + 1)
        return (n00 + (n10 - n00) * sx) + ((n01 + (n11 - n01) * sx) - (n00 + (n10 - n00) * sx)) * sy
    }

    // Bump map
    val bumpCanvas = (document.createElement("canvas") as HTMLCanvasElement).apply {
        width = texSize; height = texSize
    }
    val bumpCtx = bumpCanvas.getContext("2d") as CanvasRenderingContext2D
    val imageData = bumpCtx.createImageData(texSize.toDouble(), texSize.toDouble())
    val pixels = imageData.data.asDynamic()
    for (py in 0 until texSize) {
        for (px in 0 until texSize) {
            val nx = px.toDouble(); val ny = py.toDouble()
            var v = smoothNoise(nx * 0.03, ny * 0.03) * 0.4
            v += smoothNoise(nx * 0.08, ny * 0.08) * 0.3
            v += smoothNoise(nx * 0.2, ny * 0.2) * 0.2
            v += smoothNoise(nx * 0.5, ny * 0.5) * 0.1
            val b = (v * 255.0).toInt().coerceIn(0, 255)
            val idx = (py * texSize + px) * 4
            pixels[idx] = b; pixels[idx + 1] = b; pixels[idx + 2] = b; pixels[idx + 3] = 255
        }
    }
    bumpCtx.putImageData(imageData, 0.0, 0.0)
    val foamBumpTex = CanvasTexture(bumpCanvas)
    foamBumpTex.wrapS = RepeatWrapping; foamBumpTex.wrapT = RepeatWrapping
    foamBumpTex.repeat.set(3.0, 3.0)

    // Roughness map
    val roughCanvas = (document.createElement("canvas") as HTMLCanvasElement).apply {
        width = texSize; height = texSize
    }
    val roughCtx = roughCanvas.getContext("2d") as CanvasRenderingContext2D
    val roughData = roughCtx.createImageData(texSize.toDouble(), texSize.toDouble())
    val rPx = roughData.data.asDynamic()
    for (py in 0 until texSize) {
        for (px in 0 until texSize) {
            var v = 0.5 + (smoothNoise(px + 100.0, py + 100.0) - 0.5) * 0.3
            v += (smoothNoise(px * 0.15 + 200.0, py * 0.15 + 200.0) - 0.5) * 0.15
            val b = (v * 255.0).toInt().coerceIn(0, 255)
            val idx = (py * texSize + px) * 4
            rPx[idx] = b; rPx[idx + 1] = b; rPx[idx + 2] = b; rPx[idx + 3] = 255
        }
    }
    roughCtx.putImageData(roughData, 0.0, 0.0)
    val foamRoughTex = CanvasTexture(roughCanvas)
    foamRoughTex.wrapS = RepeatWrapping; foamRoughTex.wrapT = RepeatWrapping
    foamRoughTex.repeat.set(3.0, 3.0)

    // === Material — starts as Water Balloon (the recommended default) ===
    val material = MeshPhysicalMaterial(js("""({
        color: 0xffffff,
        vertexColors: true,
        roughness: 0.02,
        metalness: 0.0,
        clearcoat: 1.0,
        clearcoatRoughness: 0.01,
        sheen: 0.0,
        sheenRoughness: 0.0,
        sheenColor: 0xffffff,
        iridescence: 0.15,
        iridescenceIOR: 1.33,
        transmission: 0.3,
        thickness: 2.5,
        ior: 1.33,
        transparent: true,
        opacity: 1.0,
        envMapIntensity: 2.0
    })"""))
    val blob = Mesh(geometry, material)
    scene.add(blob)

    // Soft shadow
    val shadowGeo = SphereGeometry(2.4, 16, 12)
    val shadowMat = MeshStandardMaterial(js("""({
        color: 0x000000, transparent: true, opacity: 0.08, roughness: 1.0, metalness: 0.0
    })"""))
    val shadow = Mesh(shadowGeo, shadowMat)
    shadow.position.set(0.0, -2.2, 0.0)
    shadow.scale.set(1.0, 0.02, 1.0)
    scene.add(shadow)

    // Physics + deformation
    val springPhysics = SpringPhysics(geometry)
    val deformController = DeformationController(springPhysics)
    val waveSystem = WaveSystem()

    // Sound
    val sound = SoundEngine()
    val voice = VoiceEngine()
    var soundEnabled = true

    // Gesture recognition
    val gestureEngine = GestureEngine()
    var gesturePokeCooldown = 0.0

    // 3D Math graph
    val mathGraph = graph.MathGraph(scene)
    mathGraph.setup()

    // Input
    val inputHandler = InputHandler()
    inputHandler.setup()

    // (mouse-based coefficient editing removed — use input fields or gestures)

    // === State ===
    var currentColorIndex = 0
    var currentColor = BALL_COLORS[0]
    var currentThemeIndex = 0
    var currentScaleIndex = 0
    var currentStyleIndex = 0

    fun applyBallColor() { currentColor = BALL_COLORS[currentColorIndex] }

    fun applyTheme() {
        val body = document.body ?: return
        THEMES.forEach { if (it.isNotEmpty()) body.classList.remove(it) }
        val theme = THEMES[currentThemeIndex]
        if (theme.isNotEmpty()) body.classList.add(theme)
    }

    fun applyScale() {
        val body = document.body ?: return
        SCALE_MODES.forEach { if (it.isNotEmpty()) body.classList.remove(it) }
        val scale = SCALE_MODES[currentScaleIndex]
        if (scale.isNotEmpty()) body.classList.add(scale)
    }

    fun applyStyle() {
        when (currentStyleIndex) {
            0 -> { // Water Balloon — refractive membrane, liquid interior
                material.roughness = 0.02
                material.metalness = 0.0
                material.clearcoat = 1.0
                material.clearcoatRoughness = 0.01
                material.sheen = 0.0
                material.sheenRoughness = 0.0
                material.iridescence = 0.15
                material.iridescenceIOR = 1.33
                material.transmission = 0.3
                material.thickness = 2.5
                material.ior = 1.33
                material.transparent = true
                material.opacity = 1.0
                material.bumpMap = null
                material.bumpScale = 0.0
                material.roughnessMap = null
                material.envMapIntensity = 2.0
            }
            1 -> { // Gel Ball — thick viscous gel, translucent
                material.roughness = 0.15
                material.metalness = 0.0
                material.clearcoat = 0.8
                material.clearcoatRoughness = 0.1
                material.sheen = 0.2
                material.sheenRoughness = 0.4
                material.iridescence = 0.0
                material.iridescenceIOR = 1.5
                material.transmission = 0.2
                material.thickness = 3.0
                material.ior = 1.45
                material.transparent = true
                material.opacity = 1.0
                material.bumpMap = foamBumpTex
                material.bumpScale = 0.04
                material.roughnessMap = null
                material.envMapIntensity = 1.2
            }
            2 -> { // Crystal Drop — glass-clear, prismatic
                material.roughness = 0.0
                material.metalness = 0.0
                material.clearcoat = 1.0
                material.clearcoatRoughness = 0.0
                material.sheen = 0.0
                material.sheenRoughness = 0.0
                material.iridescence = 0.8
                material.iridescenceIOR = 2.0
                material.transmission = 0.45
                material.thickness = 1.5
                material.ior = 1.8
                material.transparent = true
                material.opacity = 1.0
                material.bumpMap = null
                material.bumpScale = 0.0
                material.roughnessMap = null
                material.envMapIntensity = 2.5
            }
            3 -> { // Soap Bubble — ultra-thin, iridescent film
                material.roughness = 0.0
                material.metalness = 0.0
                material.clearcoat = 1.0
                material.clearcoatRoughness = 0.0
                material.sheen = 0.0
                material.sheenRoughness = 0.0
                material.iridescence = 1.0
                material.iridescenceIOR = 1.8
                material.transmission = 0.55
                material.thickness = 0.5
                material.ior = 1.3
                material.transparent = true
                material.opacity = 0.7
                material.bumpMap = null
                material.bumpScale = 0.0
                material.roughnessMap = null
                material.envMapIntensity = 3.0
            }
        }
        material.needsUpdate = true
    }

    // === UI Overlay ===
    var overlayRef: HtmlOverlay? = null
    val overlay = HtmlOverlay(
        onReset = {
            deformController.reset()
            waveSystem.reset()
            if (soundEnabled) sound.playReset()
        },
        onToggleSound = {
            soundEnabled = !soundEnabled
            if (soundEnabled) sound.ensureResumed()
            overlayRef?.updateSoundLabel(soundEnabled)
        },
        onCycleTheme = {
            currentThemeIndex = (currentThemeIndex + 1) % THEMES.size
            applyTheme()
        },
        onCycleScale = {
            currentScaleIndex = (currentScaleIndex + 1) % SCALE_MODES.size
            applyScale()
            overlayRef?.updateScaleLabel(SCALE_LABELS[currentScaleIndex])
        },
        onCycleStyle = {
            currentStyleIndex = (currentStyleIndex + 1) % STYLE_NAMES.size
            applyStyle()
            overlayRef?.updateStyleLabel(STYLE_NAMES[currentStyleIndex])
        },
        onToggleGesture = {
            val on = gestureEngine.toggle()
            overlayRef?.updateGestureLabel(on)
        },
        onResetStrings = {
            mathGraph.resetSelected()
            if (soundEnabled) sound.playReset()
        },
        onCycleEquation = {
            mathGraph.cycleSelectedType()
            if (soundEnabled) sound.playClick()
        },
        onSectionChanged = { section ->
            when (section) {
                else -> voice.stop()
            }
        }
    )
    overlayRef = overlay
    overlay.setup()

    val raycaster = Raycaster()
    val mouseVec = Vector2()
    val clock = Clock()

    var squishSoundCooldown = 0.0
    var quoteTimer = 0.0
    val quoteInterval = 180.0
    val quoteDisplayDuration = 10.0
    var quoteDisplayTimer = 0.0
    var lastWasIdle = true

    // Resize
    fun handleResize() {
        val w = window.innerWidth; val h = window.innerHeight
        camera.aspect = w.toDouble() / h.toDouble()
        camera.updateProjectionMatrix()
        renderer.setSize(w, h)
        camera.position.z = if (kotlin.math.min(w, h).toDouble() < 500) 7.5 else 5.5
    }
    window.addEventListener("resize", { handleResize() })
    handleResize()

    document.addEventListener("click", { sound.ensureResumed() }, js("{once: true}"))
    document.addEventListener("keydown", { sound.ensureResumed() }, js("{once: true}"))

    // Touch poke — also records ripple event
    document.addEventListener("touchstart", { _ ->
        sound.ensureResumed()
        val touch = js("event.touches[0]")
        if (touch != null) {
            val tx = (touch.clientX as Double / window.innerWidth) * 2.0 - 1.0
            val ty = -(touch.clientY as Double / window.innerHeight) * 2.0 + 1.0
            mouseVec.set(tx, ty)
            raycaster.setFromCamera(mouseVec, camera)
            val intersects = raycaster.intersectObject(blob)
            if (intersects.isNotEmpty()) {
                val pt = intersects[0].point as Vector3
                deformController.applyPoke(pt)
                waveSystem.excite(-pt.x * 0.15, -pt.y * 0.15, -pt.z * 0.15, 0.6)
                recentPokes.add(PokeEvent(pt.x, pt.y, pt.z, clock.getElapsedTime()))
                if (recentPokes.size > 8) recentPokes.removeAt(0)
                if (soundEnabled) sound.playPoke()
            }
        }
    })

    applyBallColor()
    applyTheme()
    applyScale()
    applyStyle()

    // === Animation loop ===
    fun animate() {
        window.requestAnimationFrame { animate() }

        val dt = clock.getDelta()
        val elapsed = clock.getElapsedTime()
        overlay.updateBreathing(dt)
        val currentSection = overlay.getCurrentSection()

        // Toggle visibility: blob vs graph
        val graphActive = currentSection == "strings"
        blob.visible = !graphActive
        shadow.visible = !graphActive
        mathGraph.setVisible(graphActive)

        // Expire old poke ripples
        recentPokes.removeAll { elapsed - it.time > 1.5 }

        // --- Dynamic rim light orbit ---
        rimLight.position.set(
            sin(elapsed * 0.25) * 3.5,
            -2.5 + sin(elapsed * 0.4) * 1.0,
            -2.5 + cos(elapsed * 0.25) * 2.0
        )

        // --- Quote system ---
        if (currentSection == "deform") {
            quoteTimer += dt
            val qt = inputHandler.timeSinceLastInteraction()
            val idle = qt > 5.0
            if (quoteTimer > quoteInterval || (idle && !lastWasIdle && qt > 3.0)) {
                overlay.showQuote(); quoteDisplayTimer = 0.0; quoteTimer = 0.0
            }
            lastWasIdle = idle
            if (overlay.isQuoteVisible()) {
                quoteDisplayTimer += dt
                if (quoteDisplayTimer > quoteDisplayDuration) overlay.hideQuote()
            }
        }

        // --- Input (deform section only) ---
        if (currentSection == "deform") {
            if (" " in inputHandler.pressedKeys) {
                deformController.applyPulse()
                waveSystem.excite(0.0, 0.5, 0.0, 1.0)
                if (soundEnabled) sound.playPulse()
                inputHandler.pressedKeys.remove(" ")
            }
            if ("r" in inputHandler.pressedKeys || "R" in inputHandler.pressedKeys) {
                deformController.reset()
                waveSystem.reset()
                if (soundEnabled) sound.playReset()
                inputHandler.pressedKeys.remove("r"); inputHandler.pressedKeys.remove("R")
            }
            if ("c" in inputHandler.pressedKeys || "C" in inputHandler.pressedKeys) {
                currentColorIndex = (currentColorIndex + 1) % BALL_COLORS.size
                applyBallColor()
                if (soundEnabled) sound.playClick()
                inputHandler.pressedKeys.remove("c"); inputHandler.pressedKeys.remove("C")
            }
            deformController.applyKeyboardDeformation(inputHandler.pressedKeys, dt)
            squishSoundCooldown -= dt
            if (soundEnabled && deformController.isDeforming() && squishSoundCooldown <= 0.0) {
                sound.playSquish(springPhysics.totalEnergy.coerceAtMost(1.0))
                squishSoundCooldown = 0.15
            }
            if (!deformController.isDeforming()) {
                // Slower decay when gesture fingers are actively touching the ball
                val decayRate = if (gestureEngine.enabled && gestureEngine.handDetected) 0.15 else 1.0
                springPhysics.decayTargets(decayRate, dt)
            }
        } else {
            val decayRate = if (gestureEngine.enabled && gestureEngine.handDetected) 0.15 else 1.0
            springPhysics.decayTargets(decayRate, dt)
        }

        // === Gesture input ===
        gestureEngine.update(dt)
        if (gestureEngine.enabled && gestureEngine.handDetected) {
            if (currentSection == "deform") {
                gesturePokeCooldown = (gesturePokeCooldown - dt).coerceAtLeast(0.0)

                // === ALWAYS: proximity-based finger + palm contact ===
                // Generous contact zone — you just need to be roughly near the ball
                val ballRadius = 2.0
                val contactThreshold = 4.5  // very forgiving — wide interaction zone
                var fingerContactCount = 0

                // Helper: unproject NDC to closest-point-on-ray to ball center
                fun ndcToContact(nx: Double, ny: Double): Triple<Double, Double, Double>? {
                    val nearPt = Vector3(nx, ny, 0.0).unproject(camera)
                    val farPt = Vector3(nx, ny, 1.0).unproject(camera)
                    val dx = farPt.x - nearPt.x
                    val dy = farPt.y - nearPt.y
                    val dz = farPt.z - nearPt.z
                    val dl = sqrt(dx * dx + dy * dy + dz * dz)
                    if (dl < 0.001) return null
                    val ndx = dx / dl; val ndy = dy / dl; val ndz = dz / dl
                    val t = -(nearPt.x * ndx + nearPt.y * ndy + nearPt.z * ndz)
                    val cx = nearPt.x + ndx * t
                    val cy = nearPt.y + ndy * t
                    val cz = nearPt.z + ndz * t
                    val dist = sqrt(cx * cx + cy * cy + cz * cz)
                    return if (dist < contactThreshold) Triple(cx, cy, cz) else null
                }

                // Each fingertip pushes into the ball
                val fingerNDCs = gestureEngine.getAllFingerNDC()
                for ((fndcX, fndcY) in fingerNDCs) {
                    val contact = ndcToContact(fndcX, fndcY) ?: continue
                    val (cx, cy, cz) = contact
                    val dist = sqrt(cx * cx + cy * cy + cz * cz)
                    val penetration = ((contactThreshold - dist) / (contactThreshold - ballRadius)).coerceIn(0.0, 1.0)
                    val contactPt = Vector3(cx, cy, cz)
                    deformController.applyPoke(contactPt, radius = 1.4, strength = -0.7 * penetration * dt * 60.0)
                    fingerContactCount++
                }

                // Palm also pushes — broad, soft pressure
                val (palmNx, palmNy) = gestureEngine.getPalmNDC()
                val palmContact = ndcToContact(palmNx, palmNy)
                if (palmContact != null) {
                    val (px, py, pz) = palmContact
                    val palmDist = sqrt(px * px + py * py + pz * pz)
                    val palmPen = ((contactThreshold - palmDist) / (contactThreshold - ballRadius)).coerceIn(0.0, 1.0)
                    val palmPt = Vector3(px, py, pz)
                    deformController.applyPoke(palmPt, radius = 2.0, strength = -0.4 * palmPen * dt * 60.0)
                }

                // Hand movement always drags the surface (for pulling/shaping)
                if (gestureEngine.handVelocity > 0.003 && palmContact != null) {
                    val (px, py, pz) = palmContact
                    val palmPt = Vector3(px, py, pz)
                    deformController.applyPull(
                        palmPt,
                        -gestureEngine.handDeltaX * 20.0,
                        -gestureEngine.handDeltaY * 20.0,
                        strength = 1.5
                    )
                }

                if (fingerContactCount > 0 || palmContact != null) {
                    waveSystem.excite(
                        -gestureEngine.handDeltaX * 0.2,
                        -gestureEngine.handDeltaY * 0.2,
                        0.0, 0.05 * (fingerContactCount + 1)
                    )
                    if (soundEnabled && squishSoundCooldown <= 0.0) {
                        sound.playSquish(0.2 + fingerContactCount * 0.08)
                        squishSoundCooldown = 0.12
                    }
                }

                // === Gesture-specific actions (on top of always-on finger contact) ===
                when (gestureEngine.currentGesture) {
                    // OPEN + CLOSE: core interaction is handled by finger+palm contact above
                    // These just add sound variation
                    HandGesture.OPEN -> {}
                    HandGesture.CLOSE -> {
                        // Continuous grip squeeze — proportional to how closed the fist is
                        deformController.applyGrip(gestureEngine.gripAmount, dt)
                        if (soundEnabled && squishSoundCooldown <= 0.0 && gestureEngine.gripAmount > 0.2) {
                            sound.playSqueeze()
                            squishSoundCooldown = 0.12
                        }
                    }
                    // POINTER: finger contact handles the poke, just add sound
                    HandGesture.POINTER -> {
                        if (soundEnabled && fingerContactCount > 0 && squishSoundCooldown <= 0.0) {
                            sound.playPoke()
                            squishSoundCooldown = 0.08
                        }
                    }
                    HandGesture.VICTORY -> {
                        // Stretch vertically
                        val gestureKeys = mutableSetOf("w")
                        deformController.applyKeyboardDeformation(gestureKeys, dt)
                        if (soundEnabled && squishSoundCooldown <= 0.0) {
                            sound.playStretch()
                            squishSoundCooldown = 0.15
                        }
                    }
                    HandGesture.OK -> {
                        if (gestureEngine.shouldReset()) {
                            deformController.reset()
                            waveSystem.reset()
                            if (soundEnabled) sound.playReset()
                        }
                    }
                    HandGesture.THUMBS_UP -> {
                        if (gestureEngine.shouldCycleColor()) {
                            currentColorIndex = (currentColorIndex + 1) % BALL_COLORS.size
                            applyBallColor()
                            if (soundEnabled) sound.playBubble()
                        }
                    }
                    HandGesture.SPREAD -> {
                        if (gestureEngine.shouldExplode()) {
                            deformController.applyExplode()
                            waveSystem.excite(0.5, 0.8, 0.3, 3.0)
                            if (soundEnabled) sound.playExplode()
                        }
                    }
                    HandGesture.HORNS -> {
                        if (gestureEngine.shouldScramble()) {
                            deformController.applyScramble()
                            if (soundEnabled) sound.playScramble()
                        }
                    }
                    HandGesture.PUNCH -> {
                        if (gestureEngine.shouldPunch()) {
                            deformController.applyExplode()
                            waveSystem.excite(0.5, 0.8, 0.3, 3.0)
                            if (soundEnabled) sound.playExplode()
                        }
                    }
                    HandGesture.PINCH -> {
                        // Continuous pinch deformation — thumb+index pinching the ball
                        if (gesturePokeCooldown <= 0.0 && gestureEngine.pinchAmount > 0.2) {
                            val (ndcX, ndcY) = gestureEngine.getFingerNDC()
                            mouseVec.set(ndcX, ndcY)
                            raycaster.setFromCamera(mouseVec, camera)
                            val intersects = raycaster.intersectObject(blob)
                            if (intersects.isNotEmpty()) {
                                val pt = intersects[0].point as Vector3
                                deformController.applyPinch(pt, gestureEngine.pinchAmount, radius = 0.8)
                                recentPokes.add(PokeEvent(pt.x, pt.y, pt.z, elapsed))
                                if (recentPokes.size > 8) recentPokes.removeAt(0)
                            }
                            if (soundEnabled && squishSoundCooldown <= 0.0) {
                                sound.playPinch(gestureEngine.pinchAmount)
                                squishSoundCooldown = 0.08
                            }
                            gesturePokeCooldown = 0.05
                        }
                    }
                    HandGesture.PULL -> {
                        // Pull — pinching and pulling outward stretches the ball
                        if (gestureEngine.shouldPull()) {
                            val (ndcX, ndcY) = gestureEngine.getFingerNDC()
                            mouseVec.set(ndcX, ndcY)
                            raycaster.setFromCamera(mouseVec, camera)
                            val intersects = raycaster.intersectObject(blob)
                            if (intersects.isNotEmpty()) {
                                val pt = intersects[0].point as Vector3
                                deformController.applyPull(
                                    pt,
                                    -gestureEngine.handDeltaX * 15.0,
                                    -gestureEngine.handDeltaY * 15.0
                                )
                                recentPokes.add(PokeEvent(pt.x, pt.y, pt.z, elapsed))
                                if (recentPokes.size > 8) recentPokes.removeAt(0)
                            }
                            if (soundEnabled) sound.playPull()
                        }
                    }
                    HandGesture.SLAP -> {
                        // Open-hand slap — broad impact with wobble
                        if (gestureEngine.shouldSlap()) {
                            deformController.applySlap(
                                -gestureEngine.handDeltaX * 15.0,
                                -gestureEngine.handDeltaY * 15.0
                            )
                            waveSystem.excite(-gestureEngine.handDeltaX, -gestureEngine.handDeltaY, -0.3, 1.5)
                            if (soundEnabled) sound.playSlap()
                        }
                    }
                    HandGesture.SLICE -> {
                        // Karate chop slice — cuts the ball in two along the hand's movement direction
                        if (gestureEngine.shouldSlice()) {
                            // Slice plane normal is perpendicular to hand movement direction
                            // Hand moves in X/Y, so the slice plane normal is rotated 90 degrees
                            val dx = -gestureEngine.handDeltaX
                            val dy = -gestureEngine.handDeltaY
                            val dLen = sqrt(dx * dx + dy * dy)
                            // Normal perpendicular to movement: rotate 90 degrees
                            val nx = if (dLen > 0.001) -dy / dLen else 1.0
                            val ny = if (dLen > 0.001) dx / dLen else 0.0
                            val nz = 0.0
                            deformController.applySlice(nx, ny, nz)
                            waveSystem.excite(nx * 0.5, ny * 0.5, 0.0, 2.0)
                            if (soundEnabled) sound.playSlice()
                        }
                    }
                    HandGesture.KNEAD -> {
                        // Continuous kneading — dough-like manipulation
                        deformController.applyKnead(
                            gestureEngine.kneadIntensity * kotlin.math.PI * 2.0,
                            0.8,
                            dt
                        )
                        if (soundEnabled && squishSoundCooldown <= 0.0) {
                            sound.playKnead()
                            squishSoundCooldown = 0.3
                        }
                    }
                    HandGesture.TWO_HAND_RESIZE -> {
                        // Two-hand resize — spread hands apart to grow, bring together to shrink
                        val resizeDelta = gestureEngine.twoHandResizeDelta
                        if (kotlin.math.abs(resizeDelta) > 0.001) {
                            deformController.applyResize(resizeDelta * 8.0, dt)
                            if (soundEnabled && squishSoundCooldown <= 0.0) {
                                sound.playResize(resizeDelta > 0)
                                squishSoundCooldown = 0.15
                            }
                        }
                    }
                    HandGesture.NONE -> {}
                }
            }
        }

        // === 3D Graph section ===
        if (graphActive) {
            mathGraph.update(elapsed)

            // Keyboard: cycle equation with E, reset with R
            if ("e" in inputHandler.pressedKeys || "E" in inputHandler.pressedKeys) {
                mathGraph.cycleSelectedType()
                inputHandler.pressedKeys.remove("e"); inputHandler.pressedKeys.remove("E")
            }
            if ("r" in inputHandler.pressedKeys || "R" in inputHandler.pressedKeys) {
                mathGraph.resetSelected()
                inputHandler.pressedKeys.remove("r"); inputHandler.pressedKeys.remove("R")
            }

            // Arrow keys: up/down → c, left/right → translate graph
            // (only when no input field is focused)
            val inputFocused: Boolean = js("document.activeElement && document.activeElement.tagName === 'INPUT'") as Boolean
            if (!inputFocused) {
                if ("ArrowUp" in inputHandler.pressedKeys) {
                    mathGraph.adjustC(dt * 0.8)
                }
                if ("ArrowDown" in inputHandler.pressedKeys) {
                    mathGraph.adjustC(-dt * 0.8)
                }
                if ("ArrowLeft" in inputHandler.pressedKeys) {
                    mathGraph.translate(-dt * 2.0, 0.0)
                }
                if ("ArrowRight" in inputHandler.pressedKeys) {
                    mathGraph.translate(dt * 2.0, 0.0)
                }
            }

            // Gesture interaction for maths tab
            if (gestureEngine.enabled && gestureEngine.handDetected) {
                if (gestureEngine.pinchAmount > 0.7) {
                    // Pinching → adjust coefficients a, b
                    mathGraph.adjustAB(
                        -gestureEngine.handDeltaX,
                        gestureEngine.handDeltaY
                    )
                } else {
                    // Not pinching → hand position gently orbits the camera
                    if (mathGraph.isGrabbing) mathGraph.endGrab()
                    val handCenterX = gestureEngine.handX - 0.5  // -0.5 to 0.5
                    val handCenterY = gestureEngine.handY - 0.5
                    // Smoothly orbit: hand left = rotate left, hand up = tilt up
                    mathGraph.rotate(
                        -handCenterY * dt * 0.8,
                        handCenterX * dt * 0.8
                    )
                }
            } else {
                if (mathGraph.isGrabbing) mathGraph.endGrab()
            }
        }

        // Global shortcuts
        if ("m" in inputHandler.pressedKeys || "M" in inputHandler.pressedKeys) {
            soundEnabled = !soundEnabled
            if (soundEnabled) sound.ensureResumed()
            overlay.updateSoundLabel(soundEnabled)
            inputHandler.pressedKeys.remove("m"); inputHandler.pressedKeys.remove("M")
        }
        if ("t" in inputHandler.pressedKeys || "T" in inputHandler.pressedKeys) {
            currentThemeIndex = (currentThemeIndex + 1) % THEMES.size; applyTheme()
            inputHandler.pressedKeys.remove("t"); inputHandler.pressedKeys.remove("T")
        }

        // Rotation (mouse drag + inertia) — applies to whichever model is active
        val handActive = gestureEngine.enabled && gestureEngine.handDetected
        val (rotX, rotY) = inputHandler.updateRotationInertia(dt)
        if (graphActive) {
            // Graph rotation
            if (rotX != 0.0 || rotY != 0.0) {
                mathGraph.rotate(rotX, rotY)
            } else if (!inputHandler.isMouseDown) {
                mathGraph.rotate(0.0, 0.0003)  // gentle idle spin
            }
        } else {
            // Blob rotation
            if (rotX != 0.0 || rotY != 0.0) {
                blob.rotation.x += rotX; blob.rotation.y += rotY
            } else if (!handActive && !inputHandler.isMouseDown) {
                blob.rotation.y += 0.0015
            }
        }

        // Idle breathing — subtle scale pulse, always alive
        val breathScale = 1.0 + sin(elapsed * 0.5) * 0.015
        blob.scale.set(breathScale, breathScale, breathScale)

        // Idle floating (gesture activity counts as interaction)
        val idleTime = inputHandler.timeSinceLastInteraction()
        val isIdle = idleTime > 5.0 && !(gestureEngine.enabled && gestureEngine.handDetected)
        if (isIdle) {
            blob.position.y = sin(elapsed * 0.8) * 0.04
        } else {
            blob.position.y += (0.0 - blob.position.y) * 0.05
        }

        // Scroll zoom
        val scroll = inputHandler.consumeScrollDelta()
        if (scroll != 0.0) camera.position.z = (camera.position.z + scroll * 0.003).coerceIn(3.0, 10.0)

        // Click poke + ripple tracking
        if (currentSection == "deform") {
            val click = inputHandler.consumeClick()
            if (click != null) {
                mouseVec.set(click.first, click.second)
                raycaster.setFromCamera(mouseVec, camera)
                val intersects = raycaster.intersectObject(blob)
                if (intersects.isNotEmpty()) {
                    val pt = intersects[0].point as Vector3
                    deformController.applyPoke(pt)
                    waveSystem.excite(-pt.x * 0.15, -pt.y * 0.15, -pt.z * 0.15, 0.6)
                    recentPokes.add(PokeEvent(pt.x, pt.y, pt.z, elapsed))
                    if (recentPokes.size > 8) recentPokes.removeAt(0)
                    if (soundEnabled) sound.playPoke()
                }
            }
        } else {
            inputHandler.consumeClick()
        }

        // Physics
        springPhysics.update(dt)

        // Wave overlay — coherent sloshing across the water balloon surface
        waveSystem.update(dt)
        if (waveSystem.totalEnergy > 0.001) {
            for (v in 0 until vertexCount) {
                val wox = springPhysics.getOriginalX(v)
                val woy = springPhysics.getOriginalY(v)
                val woz = springPhysics.getOriginalZ(v)
                val wlen = sqrt(wox * wox + woy * woy + woz * woz)
                if (wlen > 0.001) {
                    val wnx = wox / wlen; val wny = woy / wlen; val wnz = woz / wlen
                    val wr = waveSystem.getRadialOffset(wnx, wny, wnz)
                    posAttr.array[v * 3] = posAttr.array[v * 3] + (wnx * wr).toFloat()
                    posAttr.array[v * 3 + 1] = posAttr.array[v * 3 + 1] + (wny * wr).toFloat()
                    posAttr.array[v * 3 + 2] = posAttr.array[v * 3 + 2] + (wnz * wr).toFloat()
                }
            }
            posAttr.needsUpdate = true
            geometry.computeVertexNormals()
        }

        // Gradually restore boosted limits after dramatic effects (explode/scramble/punch)
        if (springPhysics.maxOffset > 2.0) {
            springPhysics.maxOffset = (springPhysics.maxOffset - dt * 1.5).coerceAtLeast(2.0)
        }
        if (springPhysics.maxVelocity > 10.0) {
            springPhysics.maxVelocity = (springPhysics.maxVelocity - dt * 7.0).coerceAtLeast(10.0)
        }
        if (springPhysics.volumePreservation < 0.7) {
            springPhysics.volumePreservation = (springPhysics.volumePreservation + dt * 0.3).coerceAtMost(0.7)
        }


        // ============================================
        // === Per-vertex shader-like coloring pass ===
        // ============================================

        // Per-style effect strengths
        val fresnelStr: Double
        val subsurfaceStr: Double
        val rippleStr: Double
        val shimmerAmp: Double
        val compressStr: Double
        val stretchStr: Double
        val driftAmp: Double
        val causticStr: Double
        // Fresnel tint (r, g, b) per style
        var frTintR: Double; var frTintG: Double; var frTintB: Double
        val pearlescent: Boolean

        when (currentStyleIndex) {
            0 -> { // Water Balloon
                fresnelStr = 0.5; subsurfaceStr = 0.06; rippleStr = 0.4
                shimmerAmp = 0.03; compressStr = 0.2; stretchStr = 0.3; driftAmp = 0.04
                causticStr = 0.25
                frTintR = 0.7; frTintG = 0.85; frTintB = 1.0; pearlescent = false
            }
            1 -> { // Gel Ball
                fresnelStr = 0.2; subsurfaceStr = 0.15; rippleStr = 0.25
                shimmerAmp = 0.04; compressStr = 0.25; stretchStr = 0.15; driftAmp = 0.05
                causticStr = 0.12
                frTintR = 0.85; frTintG = 0.9; frTintB = 0.8; pearlescent = false
            }
            2 -> { // Crystal Drop
                fresnelStr = 0.6; subsurfaceStr = 0.04; rippleStr = 0.5
                shimmerAmp = 0.06; compressStr = 0.1; stretchStr = 0.25; driftAmp = 0.05
                causticStr = 0.35
                frTintR = 0.8; frTintG = 0.75; frTintB = 1.0; pearlescent = true
            }
            else -> { // Soap Bubble
                fresnelStr = 0.7; subsurfaceStr = 0.02; rippleStr = 0.3
                shimmerAmp = 0.08; compressStr = 0.05; stretchStr = 0.15; driftAmp = 0.06
                causticStr = 0.15
                frTintR = 0.9; frTintG = 0.85; frTintB = 1.0; pearlescent = true
            }
        }

        val colors = colorAttr.array
        val baseR = currentColor.r; val baseG = currentColor.g; val baseB = currentColor.b
        val normalAttr = geometry.getAttribute("normal") as BufferAttribute
        val camZ = camera.position.z

        for (v in 0 until vertexCount) {
            // Current deformed position (for fresnel + ripple distance)
            val px = posAttr.array[v * 3].toDouble()
            val py = posAttr.array[v * 3 + 1].toDouble()
            val pz = posAttr.array[v * 3 + 2].toDouble()

            // Vertex normal (recomputed after physics)
            val nx = normalAttr.getX(v)
            val ny = normalAttr.getY(v)
            val nz = normalAttr.getZ(v)

            // View direction (camera to vertex, unnormalized)
            val vdx = -px; val vdy = -py; val vdz = camZ - pz
            val vdLen = sqrt(vdx * vdx + vdy * vdy + vdz * vdz)
            val dotNV = if (vdLen > 0.001) (nx * vdx + ny * vdy + nz * vdz) / vdLen else 1.0

            // === Fresnel rim glow ===
            val fresnelRaw = (1.0 - abs(dotNV)).coerceIn(0.0, 1.0)
            val fresnel = fresnelRaw * fresnelRaw * fresnelStr

            // For pearlescent: iridescent color-shifting fresnel
            var fgR: Double; var fgG: Double; var fgB: Double
            if (pearlescent) {
                val ox = springPhysics.getOriginalX(v)
                val hueAngle = fresnelRaw * 3.0 + sin(elapsed * 0.2 + sqrt(ox * ox + py * py) * 2.0)
                fgR = (sin(hueAngle) * 0.5 + 0.5) * fresnel
                fgG = (sin(hueAngle + 2.094) * 0.5 + 0.5) * fresnel
                fgB = (sin(hueAngle + 4.189) * 0.5 + 0.5) * fresnel
            } else {
                fgR = fresnel * frTintR
                fgG = fresnel * frTintG
                fgB = fresnel * frTintB
            }

            // === Subsurface glow fake ===
            // Light passing through: center-facing areas get warm glow
            val sss = abs(dotNV).coerceIn(0.0, 1.0) * subsurfaceStr
            val sssR = sss * 1.1  // warm tint
            val sssG = sss * 0.7
            val sssB = sss * 0.4

            // === Spatial effects from original position ===
            val ox = springPhysics.getOriginalX(v)
            val oy = springPhysics.getOriginalY(v)
            val oz = springPhysics.getOriginalZ(v)
            val dist = sqrt(ox * ox + oy * oy + oz * oz)

            // Radial gradient — center brighter, edges deeper (stronger for 3D depth)
            val radial = 1.0 - (dist / 2.2).coerceIn(0.0, 1.0) * 0.25

            // Vertical gradient — top lighter, bottom richer
            val vertical = 1.0 + (oy / 2.5).coerceIn(-1.0, 1.0) * 0.14

            // === Animated gradient drift ===
            val drift1 = sin(elapsed * 0.25 + dist * 1.2 + oy * 0.8) * driftAmp
            val drift2 = cos(elapsed * 0.18 + ox * 1.5) * driftAmp * 0.7

            // === Shimmer — slow noise-based sparkle ===
            val shimX = ox * 2.0 + elapsed * 0.12
            val shimY = oz * 2.0 + elapsed * 0.1
            val shimmer = (smoothNoise(shimX, shimY) - 0.5) * shimmerAmp

            // === Caustic highlights — refracted light patterns like water ===
            val caustScale = 3.0
            val c1 = smoothNoise(ox * caustScale + elapsed * 0.3, oz * caustScale + elapsed * 0.2)
            val c2 = smoothNoise(oy * caustScale * 1.5 - elapsed * 0.25, oz * caustScale * 1.5 + elapsed * 0.15)
            val caustic = ((c1 + c2 - 0.8) * 3.0).coerceIn(0.0, 1.0) * causticStr

            // === Deformation response ===
            val deform = springPhysics.deformationMagnitudes[v].toDouble()
            val stretchGlow = (deform / 1.0).coerceIn(0.0, 1.0) * stretchStr
            val compressFade = 1.0 - (deform / 1.5).coerceIn(0.0, 1.0) * compressStr

            // === Poke ripple highlights ===
            var rippleGlow = 0.0
            for (poke in recentPokes) {
                val age = elapsed - poke.time
                if (age < 0.0 || age > 1.5) continue
                val dx = px - poke.x; val dy = py - poke.y; val dz2 = pz - poke.z
                val pokeDist = sqrt(dx * dx + dy * dy + dz2 * dz2)
                val ringRadius = age * 2.5
                val ringDist = abs(pokeDist - ringRadius)
                val ringFalloff = (1.0 - ringDist / 0.4).coerceIn(0.0, 1.0)
                val fade = (1.0 - age / 1.5).coerceIn(0.0, 1.0)
                rippleGlow += ringFalloff * fade * rippleStr
            }
            rippleGlow = rippleGlow.coerceAtMost(0.5)

            // === Combine all layers ===
            val baseFactor = radial * vertical * compressFade

            var r = baseR * baseFactor + drift1 + shimmer
            var g = baseG * baseFactor + drift2 + shimmer * 0.7
            var b = baseB * baseFactor - drift1 * 0.3 + shimmer * 0.5

            // Add stretch brightening
            r += stretchGlow * 0.25
            g += stretchGlow * 0.15
            b += stretchGlow * 0.1

            // Add fresnel edge glow
            r += fgR; g += fgG; b += fgB

            // Add subsurface warmth
            r += sssR; g += sssG; b += sssB

            // Add poke ripple (white-ish glow)
            r += rippleGlow * 0.9
            g += rippleGlow * 0.92
            b += rippleGlow * 0.95

            // Add caustic highlights (blue-white shimmer)
            r += caustic * 0.85
            g += caustic * 0.92
            b += caustic * 1.0

            colors[v * 3] = r.coerceIn(0.0, 1.0).toFloat()
            colors[v * 3 + 1] = g.coerceIn(0.0, 1.0).toFloat()
            colors[v * 3 + 2] = b.coerceIn(0.0, 1.0).toFloat()
        }
        colorAttr.needsUpdate = true

        // Shadow
        val energy = springPhysics.totalEnergy
        shadow.scale.set(1.0 + energy * 0.08, 0.02, 1.0 + energy * 0.08)

        renderer.render(scene, camera)
    }

    animate()
}

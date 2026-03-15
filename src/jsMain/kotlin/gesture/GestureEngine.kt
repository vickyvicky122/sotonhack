package gesture

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

enum class HandGesture(val label: String) {
    NONE("No hand"),
    OPEN("Open Palm"),
    CLOSE("Fist"),
    POINTER("Pointer"),
    OK("OK"),
    VICTORY("Victory"),
    THUMBS_UP("Thumbs Up"),
    SPREAD("Explode!"),
    HORNS("Scramble!"),
    PUNCH("Punch!"),
    PINCH("Pinch"),
    PULL("Pull"),
    SLAP("Slap!"),
    SLICE("Slice!"),
    KNEAD("Knead"),
    CLAP("Clap!"),
    TWO_HAND_RESIZE("Zoom"),
    MIDDLE_FINGER("—")
}

/**
 * 3D landmark data for a single fingertip, with estimated depth.
 * x/y are normalized webcam coords (0-1), z is MediaPipe's relative depth.
 */
data class Finger3D(
    val x: Double, val y: Double, val z: Double,
    val ndcX: Double, val ndcY: Double
)

/**
 * Full 3D hand state derived from MediaPipe landmarks.
 */
data class Hand3DState(
    val wristX: Double, val wristY: Double, val wristZ: Double,
    val palmCenterX: Double, val palmCenterY: Double, val palmCenterZ: Double,
    val fingers: List<Finger3D>,  // 5 fingertips: thumb(4), index(8), middle(12), ring(16), pinky(20)
    val palmSpan: Double,         // distance from wrist(0) to middle MCP(9) — proxy for depth
    val handScale: Double,        // overall hand size in normalized coords — depth proxy
    val estimatedDepth: Double,   // estimated Z distance from camera (higher = closer)
    val palmNormalX: Double,
    val palmNormalY: Double,
    val palmNormalZ: Double,
    val palmFacingCamera: Double,
    val handRoll: Double,
    val handPitch: Double,
    val fingerForwardness: List<Double>
)

data class ContactPointNDC(
    val ndcX: Double,
    val ndcY: Double,
    val pressure: Double
)

data class MotionSample(
    val x: Double,
    val y: Double,
    val z: Double,
    val time: Double
)

enum class GestureTrajectory {
    IDLE,
    LATERAL,
    PUSH,
    PULL,
    CIRCULAR
}

data class TwoHandSpatialState(
    val vectorX: Double,
    val vectorY: Double,
    val vectorZ: Double,
    val distance: Double,
    val distanceDelta: Double,
    val tilt: Double
)

class GestureEngine {
    var enabled = false
        private set
    var currentGesture = HandGesture.NONE
        private set
    var handDetected = false
        private set

    // Second hand state
    var hand2Detected = false
        private set

    // Normalized hand position (0-1, webcam coords) — EMA smoothed
    var handX = 0.5
        private set
    var handY = 0.5
        private set

    // Index fingertip position for pointer mode — EMA smoothed
    var fingerTipX = 0.5
        private set
    var fingerTipY = 0.5
        private set

    // Grip strength (0 = open hand, 1 = fully closed fist) — continuous
    var gripAmount = 0.0
        private set
    var prevGripAmount = 0.0
        private set

    // Frame-to-frame hand movement delta
    var handDeltaX = 0.0
        private set
    var handDeltaY = 0.0
        private set

    // 3D hand state
    var hand3D: Hand3DState? = null
        private set
    var hand2_3D: Hand3DState? = null
        private set

    // Pinch state tracking
    var pinchAmount = 0.0   // 0 = open, 1 = fully pinched
        private set
    var prevPinchAmount = 0.0
        private set

    // Two-hand distance for resize
    var twoHandDistance = 0.0
        private set
    var prevTwoHandDistance = 0.0
        private set
    var twoHandResizeDelta = 0.0  // positive = spreading apart, negative = pushing together
        private set
    // Center point between two hands (for interaction position)
    var twoHandCenterX = 0.5
        private set
    var twoHandCenterY = 0.5
        private set

    // Hand velocity (magnitude per frame)
    var handVelocity = 0.0
        private set
    var handVelocityX = 0.0
        private set
    var handVelocityY = 0.0
        private set
    var handVelocityZ = 0.0
        private set
    var handAcceleration = 0.0
        private set
    var trajectory = GestureTrajectory.IDLE
        private set
    var twoHandSpatial: TwoHandSpatialState? = null
        private set

    // Depth change rate (moving toward/away from camera)
    var depthDelta = 0.0
        private set
    private var prevDepth = 0.0
    var palmFacingCamera = 0.0
        private set
    var handRoll = 0.0
        private set
    var handPitch = 0.0
        private set

    // Knead state — alternating squeeze cycles
    private var kneadPhase = 0.0
    var kneadIntensity = 0.0
        private set

    private var prevHandX = 0.5
    private var prevHandY = 0.5

    // EMA smoothing for hand positions (filters MediaPipe jitter)
    private val smoothAlpha = 0.35  // 0 = maximum smoothing, 1 = no smoothing
    private var smoothHandX = 0.5
    private var smoothHandY = 0.5
    private var smoothFingerTipX = 0.5
    private var smoothFingerTipY = 0.5
    private var smoothPalmCenterX = 0.5
    private var smoothPalmCenterY = 0.5
    // Smoothed fingertip positions (5 fingertips x 2 coords)
    private val smoothFingerX = DoubleArray(5) { 0.5 }
    private val smoothFingerY = DoubleArray(5) { 0.5 }

    // Swipe tracking for slice detection
    private var swipeStartX = 0.5
    private var swipeAccumX = 0.0
    private var swipeFrames = 0
    private val swipeMaxFrames = 20  // ~0.33s at 60fps to complete a swipe

    // Gesture smoothing
    private var rawGesture = HandGesture.NONE
    private var gestureFrames = 0
    private val confirmFrames = 4  // filters single-frame gesture flickering (~67ms at 60fps)

    // Clap detection — two hands closing fast then hitting minimum distance
    private var clapCooldown = 0.0
    private var clapClosingSpeed = 0.0  // how fast hands are coming together (positive = closing)

    // One-shot action debounce
    private var resetCooldown = 0.0
    private var colorCooldown = 0.0
    private var explodeCooldown = 0.0
    private var scrambleCooldown = 0.0
    private var punchCooldown = 0.0
    private var slapCooldown = 0.0
    private var sliceCooldown = 0.0
    private var pullCooldown = 0.0

    // History for velocity smoothing
    private val velocityHistory = DoubleArray(5)
    private var velocityIdx = 0
    private val landmarkHistory = Array(21) { ArrayDeque<MotionSample>() }
    private val landmarkVelocity = Array(21) { DoubleArray(3) }
    private val depthScaleHistory = ArrayDeque<Double>()
    private var depthBaselineScale = 0.0
    private var depthCalibrationFrames = 0
    private val palmTrajectory = ArrayDeque<MotionSample>()
    private var timeline = 0.0
    private var prevVelocityX = 0.0
    private var prevVelocityY = 0.0
    private var prevVelocityZ = 0.0

    fun toggle(): Boolean {
        if (enabled) stop() else start()
        return enabled
    }

    private fun start() {
        val available: Boolean = js("typeof window.Hands !== 'undefined'") as Boolean
        if (!available) {
            println("MediaPipe Hands not loaded from CDN")
            return
        }
        enabled = true
        js("window.initGesture()")
    }

    private fun stop() {
        enabled = false
        js("window.stopGesture()")
        currentGesture = HandGesture.NONE
        handDetected = false
        hand2Detected = false
        handDeltaX = 0.0
        handDeltaY = 0.0
        hand3D = null
        hand2_3D = null
        pinchAmount = 0.0
        twoHandDistance = 0.0
        twoHandResizeDelta = 0.0
        handVelocity = 0.0
        handVelocityX = 0.0
        handVelocityY = 0.0
        handVelocityZ = 0.0
        handAcceleration = 0.0
        depthDelta = 0.0
        prevDepth = 0.0
        palmFacingCamera = 0.0
        handRoll = 0.0
        handPitch = 0.0
        trajectory = GestureTrajectory.IDLE
        twoHandSpatial = null
        timeline = 0.0
        depthScaleHistory.clear()
        palmTrajectory.clear()
        for (history in landmarkHistory) history.clear()
    }

    /** Call each frame from animation loop */
    fun update(dt: Double) {
        if (!enabled) return

        timeline += dt

        resetCooldown = (resetCooldown - dt).coerceAtLeast(0.0)
        colorCooldown = (colorCooldown - dt).coerceAtLeast(0.0)
        explodeCooldown = (explodeCooldown - dt).coerceAtLeast(0.0)
        scrambleCooldown = (scrambleCooldown - dt).coerceAtLeast(0.0)
        punchCooldown = (punchCooldown - dt).coerceAtLeast(0.0)
        slapCooldown = (slapCooldown - dt).coerceAtLeast(0.0)
        sliceCooldown = (sliceCooldown - dt).coerceAtLeast(0.0)
        clapCooldown = (clapCooldown - dt).coerceAtLeast(0.0)
        pullCooldown = (pullCooldown - dt).coerceAtLeast(0.0)

        val detected: Boolean = js("window._handDetected === true") as Boolean
        if (!detected) {
            handDetected = false
            hand2Detected = false
            currentGesture = HandGesture.NONE
            handDeltaX = 0.0
            handDeltaY = 0.0
            hand3D = null
            hand2_3D = null
            depthDelta = 0.0
            handVelocity = 0.0
            handVelocityX = 0.0
            handVelocityY = 0.0
            handVelocityZ = 0.0
            handAcceleration = 0.0
            trajectory = GestureTrajectory.IDLE
            twoHandSpatial = null
            palmTrajectory.clear()
            for (history in landmarkHistory) history.clear()
            val label: dynamic = js("document.getElementById('gestureLabel')")
            if (label != null) {
                label.textContent = "No hand"
            }
            return
        }

        handDetected = true
        val lm: dynamic = js("window._handLandmarks")
        val worldLmRaw: dynamic = js("window._handWorldLandmarks")
        if (lm == null) return
        // Guard: landmarks might be an empty array or have missing entries
        val lmValid: Boolean = js("lm != null && lm.length >= 21 && lm[0] != null") as Boolean
        if (!lmValid) return
        val worldLmValid: Boolean = if (worldLmRaw != null) js("worldLmRaw != null && worldLmRaw.length >= 21 && worldLmRaw[0] != null") as Boolean else false
        val worldLm: dynamic = if (worldLmValid) worldLmRaw else null

        // Read second hand (with guard)
        val h2detected: Boolean = js("window._hand2Detected === true") as Boolean
        val lm2Raw: dynamic = if (h2detected) js("window._hand2Landmarks") else null
        val worldLm2Raw: dynamic = if (h2detected) js("window._hand2WorldLandmarks") else null
        val lm2Valid: Boolean = if (lm2Raw != null) js("lm2Raw != null && lm2Raw.length >= 21 && lm2Raw[0] != null") as Boolean else false
        val worldLm2Valid: Boolean = if (worldLm2Raw != null) js("worldLm2Raw != null && worldLm2Raw.length >= 21 && worldLm2Raw[0] != null") as Boolean else false
        hand2Detected = h2detected && lm2Valid
        val lm2: dynamic = if (lm2Valid) lm2Raw else null
        val worldLm2: dynamic = if (worldLm2Valid) worldLm2Raw else null

        // Update hand position (wrist = landmark 0)
        // Use raw values for delta computation, then EMA-smooth the exposed positions
        val rawHandX = lm[0].x as Double
        val rawHandY = lm[0].y as Double
        prevHandX = handX
        prevHandY = handY
        smoothHandX = smoothAlpha * rawHandX + (1.0 - smoothAlpha) * smoothHandX
        smoothHandY = smoothAlpha * rawHandY + (1.0 - smoothAlpha) * smoothHandY
        handX = smoothHandX
        handY = smoothHandY
        handDeltaX = handX - prevHandX
        handDeltaY = handY - prevHandY

        // Index fingertip (landmark 8) for pointer mode — EMA smoothed
        val rawFingerTipX = lm[8].x as Double
        val rawFingerTipY = lm[8].y as Double
        smoothFingerTipX = smoothAlpha * rawFingerTipX + (1.0 - smoothAlpha) * smoothFingerTipX
        smoothFingerTipY = smoothAlpha * rawFingerTipY + (1.0 - smoothAlpha) * smoothFingerTipY
        fingerTipX = smoothFingerTipX
        fingerTipY = smoothFingerTipY

        // Build 3D hand state
        hand3D = build3DState(lm, worldLm)
        hand2_3D = if (lm2 != null) build3DState(lm2, worldLm2) else null

        // EMA-smooth 3D palm center for contact deformation
        val h3d = hand3D
        if (h3d != null) {
            smoothPalmCenterX = smoothAlpha * h3d.palmCenterX + (1.0 - smoothAlpha) * smoothPalmCenterX
            smoothPalmCenterY = smoothAlpha * h3d.palmCenterY + (1.0 - smoothAlpha) * smoothPalmCenterY
            palmFacingCamera = h3d.palmFacingCamera
            handRoll = h3d.handRoll
            handPitch = h3d.handPitch
            // Smooth each fingertip position
            for (fi in h3d.fingers.indices) {
                smoothFingerX[fi] = smoothAlpha * h3d.fingers[fi].x + (1.0 - smoothAlpha) * smoothFingerX[fi]
                smoothFingerY[fi] = smoothAlpha * h3d.fingers[fi].y + (1.0 - smoothAlpha) * smoothFingerY[fi]
            }
        }

        updateTemporalTracking(lm, hand3D!!)

        // Compute grip amount: how closed is the fist (0=open, 1=fully closed)
        // Measured by average fingertip curl — ratio of tip-to-wrist vs MCP-to-wrist distance
        prevGripAmount = gripAmount
        val wristX = lm[0].x as Double
        val wristY = lm[0].y as Double
        val tipIndices = intArrayOf(8, 12, 16, 20)  // index, middle, ring, pinky tips
        val mcpIndices = intArrayOf(5, 9, 13, 17)   // corresponding MCPs
        var curlSum = 0.0
        for (fi in tipIndices.indices) {
            val tipX = lm[tipIndices[fi]].x as Double
            val tipY = lm[tipIndices[fi]].y as Double
            val mcpX = lm[mcpIndices[fi]].x as Double
            val mcpY = lm[mcpIndices[fi]].y as Double
            val tipDist = hypot(tipX - wristX, tipY - wristY)
            val mcpDist = hypot(mcpX - wristX, mcpY - wristY)
            // When finger is extended: tipDist > mcpDist (ratio > 1)
            // When curled: tipDist < mcpDist (ratio < 1)
            val curlRatio = if (mcpDist > 0.001) (1.0 - tipDist / mcpDist).coerceIn(0.0, 1.0) else 0.0
            curlSum += curlRatio
        }
        val rawGrip = (curlSum / tipIndices.size).coerceIn(0.0, 1.0)
        // Smooth grip amount
        gripAmount = smoothAlpha * rawGrip + (1.0 - smoothAlpha) * gripAmount

        // Track depth changes
        val currentDepth = hand3D?.estimatedDepth ?: 0.0
        depthDelta = currentDepth - prevDepth
        prevDepth = currentDepth

        // Compute hand velocity (smoothed)
        val rawVel = sqrt(handVelocityX * handVelocityX + handVelocityY * handVelocityY + handVelocityZ * handVelocityZ) * dt
        velocityHistory[velocityIdx % 5] = rawVel
        velocityIdx++
        handVelocity = velocityHistory.average()

        // Compute pinch amount (thumb tip to index tip distance)
        val thumbTipX = lm[4].x as Double
        val thumbTipY = lm[4].y as Double
        val thumbTipZ = (lm[4].z as? Double) ?: 0.0
        val indexTipX = lm[8].x as Double
        val indexTipY = lm[8].y as Double
        val indexTipZ = (lm[8].z as? Double) ?: 0.0
        val pinchDist = sqrt(
            (thumbTipX - indexTipX) * (thumbTipX - indexTipX) +
            (thumbTipY - indexTipY) * (thumbTipY - indexTipY) +
            (thumbTipZ - indexTipZ) * (thumbTipZ - indexTipZ)
        )
        prevPinchAmount = pinchAmount
        // Map distance to 0-1 range — generous range for easier pinch detection
        // ~0.20 = fully open, ~0.04 = pinched tight
        pinchAmount = (1.0 - ((pinchDist - 0.04) / 0.16).coerceIn(0.0, 1.0))

        // Two-hand tracking (resize + clap detection)
        if (hand2Detected && hand2_3D != null) {
            prevTwoHandDistance = twoHandDistance
            val h1 = hand3D!!
            val h2 = hand2_3D!!
            val vectorX = h2.palmCenterX - h1.palmCenterX
            val vectorY = h2.palmCenterY - h1.palmCenterY
            val vectorZ = h2.estimatedDepth - h1.estimatedDepth
            twoHandDistance = sqrt(vectorX * vectorX + vectorY * vectorY + vectorZ * vectorZ)
            twoHandResizeDelta = twoHandDistance - prevTwoHandDistance
            twoHandCenterX = (h1.palmCenterX + h2.palmCenterX) / 2.0
            twoHandCenterY = (h1.palmCenterY + h2.palmCenterY) / 2.0
            twoHandSpatial = TwoHandSpatialState(
                vectorX = vectorX,
                vectorY = vectorY,
                vectorZ = vectorZ,
                distance = twoHandDistance,
                distanceDelta = twoHandResizeDelta,
                tilt = atan2(vectorZ, hypot(vectorX, vectorY).coerceAtLeast(0.0001))
            )

            // Clap detection: hands closing fast (negative delta = getting closer)
            // Smooth the closing speed to avoid single-frame spikes
            clapClosingSpeed = clapClosingSpeed * 0.6 + (-twoHandResizeDelta) * 0.4
            // Clap triggers when: hands are close AND were closing fast
            if (twoHandDistance < 0.34 && clapClosingSpeed > 0.01 && clapCooldown <= 0.0) {
                currentGesture = HandGesture.CLAP
                clapCooldown = 2.0
                clapClosingSpeed = 0.0
            }
        } else {
            twoHandResizeDelta = 0.0
            clapClosingSpeed = 0.0
            twoHandSpatial = null
        }

        // Knead tracking (continuous squeeze-release cycling)
        if (currentGesture == HandGesture.KNEAD) {
            kneadPhase += dt * 3.0
            kneadIntensity = (kotlin.math.sin(kneadPhase) + 1.0) / 2.0  // 0-1 oscillation
        } else {
            kneadPhase = 0.0
            kneadIntensity = 0.0
        }

        // Classify gesture
        val detectedGesture = classifyGesture(lm, lm2)

        // Smoothing: require same gesture for N consecutive frames
        if (detectedGesture == rawGesture) {
            gestureFrames++
        } else {
            rawGesture = detectedGesture
            gestureFrames = 1
        }

        if (gestureFrames >= confirmFrames) {
            currentGesture = rawGesture
        }

        // Upgrade fist → punch when hand is moving fast
        // Also trigger from any gesture with high grip + velocity (more forgiving)
        if (currentGesture == HandGesture.CLOSE && (handVelocity > 0.018 || (trajectory == GestureTrajectory.PUSH && handAcceleration > 1.0))) {
            currentGesture = HandGesture.PUNCH
        } else if (gripAmount > 0.6 && (handVelocity > 0.03 || trajectory == GestureTrajectory.PUSH)) {
            currentGesture = HandGesture.PUNCH
        }

        // Track swipes for slice detection:
        // A slice = hand swipes across a large horizontal distance quickly
        // while passing through the center area (where the ball is)
        if (handVelocity > 0.015) {
            // Hand is moving — accumulate horizontal travel
            swipeAccumX += abs(handDeltaX)
            swipeFrames++

            // Check if we've swiped far enough, fast enough, through the center
            val handNearCenter = handX > 0.25 && handX < 0.75 && handY > 0.2 && handY < 0.8
            if (swipeAccumX > 0.18 && swipeFrames <= swipeMaxFrames && handNearCenter && sliceCooldown <= 0.0) {
                currentGesture = HandGesture.SLICE
                swipeAccumX = 0.0
                swipeFrames = 0
            }

            // Reset if taking too long (not a fast swipe)
            if (swipeFrames > swipeMaxFrames) {
                swipeAccumX = 0.0
                swipeFrames = 0
            }
        } else {
            // Hand stopped or moving slowly — reset swipe tracking
            swipeAccumX = 0.0
            swipeFrames = 0
        }

        // Upgrade open palm → slap when hand is moving very fast sideways (only if not already SLICE)
        if (currentGesture == HandGesture.OPEN && currentGesture != HandGesture.SLICE) {
            if (handVelocity > 0.04 || (trajectory == GestureTrajectory.LATERAL && handAcceleration > 1.0)) {
                currentGesture = HandGesture.SLAP
            }
        }

        // Update gesture label in DOM
        val label: dynamic = js("document.getElementById('gestureLabel')")
        if (label != null) {
            label.textContent = currentGesture.label
        }
    }

    /** Build a 3D hand state from MediaPipe landmarks */
    private fun build3DState(lm: dynamic, worldLm: dynamic): Hand3DState {
        val wristX = lm[0].x as Double
        val wristY = lm[0].y as Double
        val wristZ = (lm[0].z as? Double) ?: 0.0

        // Palm center = average of wrist(0), index MCP(5), middle MCP(9), ring MCP(13), pinky MCP(17)
        val palmIndices = intArrayOf(0, 5, 9, 13, 17)
        var pcx = 0.0; var pcy = 0.0; var pcz = 0.0
        for (idx in palmIndices) {
            pcx += lm[idx].x as Double
            pcy += lm[idx].y as Double
            pcz += (lm[idx].z as? Double) ?: 0.0
        }
        pcx /= palmIndices.size
        pcy /= palmIndices.size
        pcz /= palmIndices.size

        // Fingertip positions (landmarks 4,8,12,16,20)
        val tipIndices = intArrayOf(4, 8, 12, 16, 20)
        val fingers = tipIndices.map { idx ->
            val fx = lm[idx].x as Double
            val fy = lm[idx].y as Double
            val fz = (lm[idx].z as? Double) ?: 0.0
            // Convert to NDC (mirrored for webcam)
            val ndcX = 1.0 - 2.0 * fx
            val ndcY = 1.0 - 2.0 * fy
            Finger3D(fx, fy, fz, ndcX, ndcY)
        }

        // Palm span: wrist to middle MCP — larger = hand closer to camera
        val midMcpX = lm[9].x as Double
        val midMcpY = lm[9].y as Double
        val palmSpan = hypot(wristX - midMcpX, wristY - midMcpY)

        // Hand scale: bounding box of all key landmarks — depth proxy
        var minX = 1.0; var maxX = 0.0; var minY = 1.0; var maxY = 0.0
        val allIndices = intArrayOf(0, 4, 8, 12, 16, 20, 5, 9, 13, 17)
        for (idx in allIndices) {
            val lx = lm[idx].x as Double
            val ly = lm[idx].y as Double
            if (lx < minX) minX = lx
            if (lx > maxX) maxX = lx
            if (ly < minY) minY = ly
            if (ly > maxY) maxY = ly
        }
        val handScale = hypot(maxX - minX, maxY - minY)

        depthScaleHistory.addLast(handScale)
        while (depthScaleHistory.size > 8) depthScaleHistory.removeFirst()
        val smoothedScale = depthScaleHistory.average().coerceAtLeast(0.0001)
        if (depthCalibrationFrames < 20) {
            depthBaselineScale = if (depthCalibrationFrames == 0) smoothedScale else depthBaselineScale * 0.85 + smoothedScale * 0.15
            depthCalibrationFrames++
        }
        if (depthBaselineScale <= 0.0001) depthBaselineScale = smoothedScale
        val estimatedDepth = (smoothedScale / depthBaselineScale).coerceIn(0.45, 2.4)

        fun wx(index: Int): Double = if (worldLm != null) (worldLm[index].x as Double) else lm[index].x as Double
        fun wy(index: Int): Double = if (worldLm != null) (worldLm[index].y as Double) else lm[index].y as Double
        fun wz(index: Int): Double = if (worldLm != null) (worldLm[index].z as Double) else ((lm[index].z as? Double) ?: 0.0)

        val indexMcpX = wx(5) - wx(0)
        val indexMcpY = wy(5) - wy(0)
        val indexMcpZ = wz(5) - wz(0)
        val pinkyMcpX = wx(17) - wx(0)
        val pinkyMcpY = wy(17) - wy(0)
        val pinkyMcpZ = wz(17) - wz(0)

        val palmNormalXRaw = indexMcpY * pinkyMcpZ - indexMcpZ * pinkyMcpY
        val palmNormalYRaw = indexMcpZ * pinkyMcpX - indexMcpX * pinkyMcpZ
        val palmNormalZRaw = indexMcpX * pinkyMcpY - indexMcpY * pinkyMcpX
        val palmNormalLen = sqrt(
            palmNormalXRaw * palmNormalXRaw +
                palmNormalYRaw * palmNormalYRaw +
                palmNormalZRaw * palmNormalZRaw
        ).coerceAtLeast(0.0001)
        val palmNormalX = palmNormalXRaw / palmNormalLen
        val palmNormalY = palmNormalYRaw / palmNormalLen
        val palmNormalZ = palmNormalZRaw / palmNormalLen
        val palmFacingCamera = (-palmNormalZ).coerceIn(-1.0, 1.0)
        val handRoll = atan2((lm[5].y as Double) - (lm[17].y as Double), (lm[5].x as Double) - (lm[17].x as Double))
        val handPitch = atan2(palmFacingCamera, hypot(palmNormalX, palmNormalY).coerceAtLeast(0.0001))
        val palmWorldZ = (wz(0) + wz(5) + wz(9) + wz(13) + wz(17)) / 5.0
        val fingerForwardness = tipIndices.map { idx ->
            (palmWorldZ - wz(idx)).coerceIn(-0.25, 0.25)
        }

        return Hand3DState(
            wristX, wristY, wristZ,
            pcx, pcy, pcz,
            fingers, palmSpan, handScale, estimatedDepth,
            palmNormalX, palmNormalY, palmNormalZ,
            palmFacingCamera,
            handRoll,
            handPitch,
            fingerForwardness
        )
    }

    private fun updateTemporalTracking(lm: dynamic, state: Hand3DState) {
        val inferredDepthOffset = (state.estimatedDepth - 1.0) * 0.35
        for (index in 0 until 21) {
            val sample = MotionSample(
                x = lm[index].x as Double,
                y = lm[index].y as Double,
                z = ((lm[index].z as? Double) ?: 0.0) + inferredDepthOffset,
                time = timeline
            )
            val history = landmarkHistory[index]
            history.addLast(sample)
            while (history.size > 8) history.removeFirst()
            if (history.size >= 2) {
                val prev = history[history.size - 2]
                val dt = (sample.time - prev.time).coerceAtLeast(0.0001)
                landmarkVelocity[index][0] = (sample.x - prev.x) / dt
                landmarkVelocity[index][1] = (sample.y - prev.y) / dt
                landmarkVelocity[index][2] = (sample.z - prev.z) / dt
            }
        }

        val palmSample = MotionSample(
            x = state.palmCenterX,
            y = state.palmCenterY,
            z = state.estimatedDepth,
            time = timeline
        )
        palmTrajectory.addLast(palmSample)
        while (palmTrajectory.isNotEmpty() && timeline - palmTrajectory.first().time > 0.5) {
            palmTrajectory.removeFirst()
        }

        if (palmTrajectory.size >= 2) {
            val prev = palmTrajectory[palmTrajectory.size - 2]
            val dt = (palmSample.time - prev.time).coerceAtLeast(0.0001)
            handVelocityX = (palmSample.x - prev.x) / dt
            handVelocityY = (palmSample.y - prev.y) / dt
            handVelocityZ = (palmSample.z - prev.z) / dt
            val accX = (handVelocityX - prevVelocityX) / dt
            val accY = (handVelocityY - prevVelocityY) / dt
            val accZ = (handVelocityZ - prevVelocityZ) / dt
            handAcceleration = sqrt(accX * accX + accY * accY + accZ * accZ)
            prevVelocityX = handVelocityX
            prevVelocityY = handVelocityY
            prevVelocityZ = handVelocityZ
        }

        trajectory = classifyTrajectory()
    }

    private fun classifyTrajectory(): GestureTrajectory {
        if (palmTrajectory.size < 4) return GestureTrajectory.IDLE

        val first = palmTrajectory.first()
        val last = palmTrajectory.last()
        val totalDx = last.x - first.x
        val totalDy = last.y - first.y
        val totalDz = last.z - first.z
        val spanX = palmTrajectory.maxOf { it.x } - palmTrajectory.minOf { it.x }
        val spanY = palmTrajectory.maxOf { it.y } - palmTrajectory.minOf { it.y }
        var pathLength = 0.0
        var signChangesX = 0
        var signChangesY = 0
        var prevStepX = 0.0
        var prevStepY = 0.0

        for (i in 1 until palmTrajectory.size) {
            val prev = palmTrajectory[i - 1]
            val current = palmTrajectory[i]
            val stepX = current.x - prev.x
            val stepY = current.y - prev.y
            val stepZ = current.z - prev.z
            pathLength += sqrt(stepX * stepX + stepY * stepY + stepZ * stepZ)
            if (i > 1 && stepX * prevStepX < 0.0) signChangesX++
            if (i > 1 && stepY * prevStepY < 0.0) signChangesY++
            prevStepX = stepX
            prevStepY = stepY
        }

        val directDistance = sqrt(totalDx * totalDx + totalDy * totalDy + totalDz * totalDz).coerceAtLeast(0.0001)
        return when {
            totalDz > 0.12 -> GestureTrajectory.PUSH
            totalDz < -0.08 -> GestureTrajectory.PULL
            spanX > 0.05 && spanY > 0.05 && pathLength > directDistance * 1.7 && signChangesX >= 2 && signChangesY >= 2 -> GestureTrajectory.CIRCULAR
            abs(totalDx) > 0.08 && abs(totalDx) > abs(totalDy) * 1.4 -> GestureTrajectory.LATERAL
            else -> GestureTrajectory.IDLE
        }
    }

    /** Check if reset action should fire (debounced) */
    fun shouldReset(): Boolean {
        if (currentGesture == HandGesture.OK && resetCooldown <= 0.0) {
            resetCooldown = 1.5
            return true
        }
        return false
    }

    /** Check if color cycle should fire (debounced) */
    fun shouldCycleColor(): Boolean {
        if (currentGesture == HandGesture.THUMBS_UP && colorCooldown <= 0.0) {
            colorCooldown = 1.0
            return true
        }
        return false
    }

    fun shouldExplode(): Boolean {
        if (currentGesture == HandGesture.SPREAD && explodeCooldown <= 0.0) {
            explodeCooldown = 1.5
            return true
        }
        return false
    }

    fun shouldScramble(): Boolean {
        if (currentGesture == HandGesture.HORNS && scrambleCooldown <= 0.0) {
            scrambleCooldown = 1.2
            return true
        }
        return false
    }

    fun shouldPunch(): Boolean {
        if (currentGesture == HandGesture.PUNCH && punchCooldown <= 0.0) {
            punchCooldown = 0.6
            return true
        }
        return false
    }

    fun shouldSlap(): Boolean {
        if (currentGesture == HandGesture.SLAP && slapCooldown <= 0.0) {
            slapCooldown = 0.5
            return true
        }
        return false
    }

    fun shouldSlice(): Boolean {
        if (currentGesture == HandGesture.SLICE && sliceCooldown <= 0.0) {
            sliceCooldown = 2.0
            return true
        }
        return false
    }

    fun shouldClap(): Boolean {
        return currentGesture == HandGesture.CLAP
    }

    fun shouldPull(): Boolean {
        if (currentGesture == HandGesture.PULL && pullCooldown <= 0.0) {
            pullCooldown = 0.3
            return true
        }
        return false
    }

    /** Convert fingertip position to NDC for raycaster (accounts for webcam mirror) */
    fun getFingerNDC(): Pair<Double, Double> {
        val ndcX = 1.0 - 2.0 * fingerTipX
        val ndcY = 1.0 - 2.0 * fingerTipY
        return Pair(ndcX, ndcY)
    }

    /** Convert palm center to NDC for contact-based deformation (EMA smoothed) */
    fun getPalmNDC(): Pair<Double, Double> {
        if (hand3D == null) return Pair(0.0, 0.0)
        val ndcX = 1.0 - 2.0 * smoothPalmCenterX
        val ndcY = 1.0 - 2.0 * smoothPalmCenterY
        return Pair(ndcX, ndcY)
    }

    /** Get all fingertip NDC positions for multi-point collision (EMA smoothed) */
    fun getAllFingerNDC(): List<Pair<Double, Double>> {
        if (hand3D == null) return emptyList()
        return (0 until 5).map {
            Pair(1.0 - 2.0 * smoothFingerX[it], 1.0 - 2.0 * smoothFingerY[it])
        }
    }

    /** Richer contact receptors across the whole hand for dough interactions. */
    fun getContactPointsNDC(): List<ContactPointNDC> {
        val state = hand3D ?: return emptyList()
        val indices = intArrayOf(0, 5, 9, 13, 17, 4, 8, 12, 16, 20)
        val contacts = mutableListOf<ContactPointNDC>()
        for (index in indices) {
            val history = landmarkHistory[index]
            if (history.isEmpty()) continue
            val sample = history.last()
            val velocityMag = sqrt(
                landmarkVelocity[index][0] * landmarkVelocity[index][0] +
                    landmarkVelocity[index][1] * landmarkVelocity[index][1] +
                    landmarkVelocity[index][2] * landmarkVelocity[index][2]
            )
            val fingerForward = when (index) {
                4 -> state.fingerForwardness.getOrElse(0) { 0.0 }
                8 -> state.fingerForwardness.getOrElse(1) { 0.0 }
                12 -> state.fingerForwardness.getOrElse(2) { 0.0 }
                16 -> state.fingerForwardness.getOrElse(3) { 0.0 }
                20 -> state.fingerForwardness.getOrElse(4) { 0.0 }
                else -> state.palmFacingCamera * 0.08
            }
            val palmBias = if (index == 0 || index == 5 || index == 9 || index == 13 || index == 17) {
                0.25 + state.palmFacingCamera.coerceAtLeast(0.0) * 0.35
            } else {
                0.08
            }
            val pressure = (
                0.35 +
                    palmBias +
                    fingerForward.coerceAtLeast(0.0) * 3.0 +
                    (state.estimatedDepth - 1.0).coerceAtLeast(0.0) * 0.45 +
                    velocityMag * 0.015
                ).coerceIn(0.2, 1.5)
            contacts.add(
                ContactPointNDC(
                    ndcX = 1.0 - 2.0 * sample.x,
                    ndcY = 1.0 - 2.0 * sample.y,
                    pressure = pressure
                )
            )
        }
        return contacts
    }

    /** Convert hand movement to rotation values (mirrored for natural feel) */
    fun getRotationDelta(): Pair<Double, Double> {
        if (!handDetected) return Pair(0.0, 0.0)
        return Pair(handDeltaY * 4.0, -handDeltaX * 4.0)
    }

    private fun classifyGesture(lm: dynamic, lm2: dynamic): HandGesture {
        val state = hand3D ?: return HandGesture.NONE
        val thumbUp = isThumbExtended(lm)
        val indexUp = isFingerExtended(lm, 6, 8)
        val middleUp = isFingerExtended(lm, 10, 12)
        val ringUp = isFingerExtended(lm, 14, 16)
        val pinkyUp = isFingerExtended(lm, 18, 20)
        val palmTowardCamera = state.palmFacingCamera > 0.12
        val handEdgeOn = abs(state.palmFacingCamera) < 0.18
        val indexForward = state.fingerForwardness.getOrElse(1) { 0.0 }

        // OK: thumb tip and index tip very close together
        val thumbTipX = lm[4].x as Double
        val thumbTipY = lm[4].y as Double
        val indexTipX = lm[8].x as Double
        val indexTipY = lm[8].y as Double
        val okDist = hypot(thumbTipX - indexTipX, thumbTipY - indexTipY)

        // Also check thumb-to-middle pinch (some people pinch with middle finger)
        val middleTipX = lm[12].x as Double
        val middleTipY = lm[12].y as Double
        val thumbMiddleDist = hypot(thumbTipX - middleTipX, thumbTipY - middleTipY)
        val anyPinchClose = okDist < 0.10 || thumbMiddleDist < 0.08

        // Two-hand zoom takes priority only when both hands are making a deliberate pinch.
        if (lm2 != null && hand2Detected) {
            val thumbTipX2 = lm2[4].x as Double
            val thumbTipY2 = lm2[4].y as Double
            val indexTipX2 = lm2[8].x as Double
            val indexTipY2 = lm2[8].y as Double
            val middleTipX2 = lm2[12].x as Double
            val middleTipY2 = lm2[12].y as Double
            val okDist2 = hypot(thumbTipX2 - indexTipX2, thumbTipY2 - indexTipY2)
            val thumbMiddleDist2 = hypot(thumbTipX2 - middleTipX2, thumbTipY2 - middleTipY2)
            val secondHandPinching = okDist2 < 0.085 || thumbMiddleDist2 < 0.07
            val bothHandsPinching = anyPinchClose && secondHandPinching
            if (bothHandsPinching) return HandGesture.TWO_HAND_RESIZE
        }

        return when {
            // Pinch: thumb+index OR thumb+middle close — very forgiving, any finger state
            anyPinchClose && okDist < 0.10 && !middleUp && !ringUp -> HandGesture.PINCH
            // Also pinch if just thumb+index are close regardless of other fingers (but not middle finger)
            okDist < 0.07 && !(!indexUp && middleUp && !ringUp && !pinkyUp) -> HandGesture.PINCH
            // Pull: pinch + hand moving away from camera
            anyPinchClose && (trajectory == GestureTrajectory.PULL || depthDelta < -0.003) -> HandGesture.PULL
            // OK sign: thumb+index touching AND at least 2 other fingers clearly up
            okDist < 0.06 && middleUp && (ringUp || pinkyUp) -> HandGesture.OK
            // Knead: looping trajectory with the palm presented toward the camera
            trajectory == GestureTrajectory.CIRCULAR && palmTowardCamera && gripAmount > 0.15 -> HandGesture.KNEAD
            // Spread / jazz hands: ALL 5 fingers including thumb AND spread apart → explode
            thumbUp && indexUp && middleUp && ringUp && pinkyUp && areFingersSpread(lm) -> HandGesture.SPREAD
            // (middle-only gesture removed)
            // Horns / rock sign: index + pinky only → scramble
            indexUp && !middleUp && !ringUp && pinkyUp -> HandGesture.HORNS
            // Victory: index + middle extended, ring + pinky closed
            indexUp && middleUp && !ringUp && !pinkyUp -> HandGesture.VICTORY
            // Thumbs up: only thumb extended
            thumbUp && !indexUp && !middleUp && !ringUp && !pinkyUp -> HandGesture.THUMBS_UP
            // Pointer: only index extended (no pinky — distinguishes from horns)
            indexUp && !middleUp && !ringUp && !pinkyUp && indexForward > -0.02 -> HandGesture.POINTER
            // Edge-on lateral wipe becomes a slice candidate before the explicit swipe override kicks in
            handEdgeOn && trajectory == GestureTrajectory.LATERAL && handVelocity > 0.02 -> HandGesture.SLICE
            // Open palm: 4+ fingers extended (thumb optional if not spread)
            indexUp && middleUp && ringUp && pinkyUp -> HandGesture.OPEN
            // Closed fist: no fingers extended (upgraded to PUNCH in update if moving fast)
            !thumbUp && !indexUp && !middleUp && !ringUp && !pinkyUp -> HandGesture.CLOSE
            else -> HandGesture.NONE
        }
    }

    private fun isThumbExtended(lm: dynamic): Boolean {
        val tipX = lm[4].x as Double
        val ipX = lm[3].x as Double
        val mcpX = lm[2].x as Double
        return abs(tipX - mcpX) > abs(ipX - mcpX) * 1.1
    }

    private fun isFingerExtended(lm: dynamic, pip: Int, tip: Int): Boolean {
        val tipY = lm[tip].y as Double
        val pipY = lm[pip].y as Double
        // Also check against MCP (pip - 1) for more robust detection
        val mcpY = lm[pip - 1].y as Double
        // Finger is extended if tip is clearly above PIP joint
        // Use relative threshold based on hand size for scale independence
        return tipY < pipY - 0.015 && tipY < mcpY
    }

    /** Check if fingertips are spread apart (not just extended) — prevents accidental explosion */
    private fun areFingersSpread(lm: dynamic): Boolean {
        val tips = intArrayOf(4, 8, 12, 16, 20)
        var totalDist = 0.0
        var count = 0
        for (i in tips.indices) {
            for (j in i + 1 until tips.size) {
                val x1 = lm[tips[i]].x as Double
                val y1 = lm[tips[i]].y as Double
                val x2 = lm[tips[j]].x as Double
                val y2 = lm[tips[j]].y as Double
                totalDist += hypot(x1 - x2, y1 - y2)
                count++
            }
        }
        return totalDist / count > 0.12
    }
}

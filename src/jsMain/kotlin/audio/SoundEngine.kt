package audio

class SoundEngine {

    private val ctx: dynamic = js("new (window.AudioContext || window.webkitAudioContext)()")
    private var droneOsc: dynamic = null
    private var droneGain: dynamic = null
    private var isDroneActive = false

    fun ensureResumed() {
        if (ctx.state == "suspended") {
            ctx.resume()
        }
    }

    /** Soft foam thud on poke — muffled low-frequency impact */
    fun playPoke() {
        ensureResumed()
        val now = ctx.currentTime as Double
        val osc = ctx.createOscillator()
        val gain = ctx.createGain()
        val filter = ctx.createBiquadFilter()

        osc.type = "sine"
        osc.frequency.setValueAtTime(180, now)
        osc.frequency.exponentialRampToValueAtTime(60, now + 0.2)

        filter.type = "lowpass"
        filter.frequency.setValueAtTime(600, now)
        filter.frequency.exponentialRampToValueAtTime(100, now + 0.25)

        gain.gain.setValueAtTime(0.3, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.3)

        osc.connect(filter)
        filter.connect(gain)
        gain.connect(ctx.destination)

        osc.start(now)
        osc.stop(now + 0.35)
    }

    /** Soft puff on pulse — foam expanding */
    fun playPulse() {
        ensureResumed()
        val now = ctx.currentTime as Double

        // Filtered noise puff
        val bufferSize = (ctx.sampleRate as Double * 0.25).toInt()
        val buffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate)
        val data = buffer.getChannelData(0)
        for (i in 0 until bufferSize) {
            data[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0) * 0.2
        }
        val noise = ctx.createBufferSource()
        noise.buffer = buffer

        val filter = ctx.createBiquadFilter()
        filter.type = "lowpass"
        filter.frequency.setValueAtTime(800, now)
        filter.frequency.exponentialRampToValueAtTime(150, now + 0.2)

        val gain = ctx.createGain()
        gain.gain.setValueAtTime(0.2, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.25)

        // Low tone underneath
        val osc = ctx.createOscillator()
        osc.type = "sine"
        osc.frequency.setValueAtTime(120, now)
        osc.frequency.exponentialRampToValueAtTime(80, now + 0.2)
        val oscGain = ctx.createGain()
        oscGain.gain.setValueAtTime(0.15, now)
        oscGain.gain.exponentialRampToValueAtTime(0.001, now + 0.25)

        noise.connect(filter)
        filter.connect(gain)
        gain.connect(ctx.destination)
        osc.connect(oscGain)
        oscGain.connect(ctx.destination)

        noise.start(now)
        noise.stop(now + 0.25)
        osc.start(now)
        osc.stop(now + 0.28)
    }

    /** Soft exhale on reset — foam puffing back to shape */
    fun playReset() {
        ensureResumed()
        val now = ctx.currentTime as Double

        val bufferSize = (ctx.sampleRate as Double * 0.5).toInt()
        val buffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate)
        val data = buffer.getChannelData(0)
        for (i in 0 until bufferSize) {
            data[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0) * 0.15
        }

        val noise = ctx.createBufferSource()
        noise.buffer = buffer

        val filter = ctx.createBiquadFilter()
        filter.type = "bandpass"
        filter.frequency.setValueAtTime(400, now)
        filter.frequency.exponentialRampToValueAtTime(150, now + 0.4)
        filter.Q.setValueAtTime(1, now)

        val gain = ctx.createGain()
        gain.gain.setValueAtTime(0.0, now)
        gain.gain.linearRampToValueAtTime(0.12, now + 0.05)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.45)

        noise.connect(filter)
        filter.connect(gain)
        gain.connect(ctx.destination)

        noise.start(now)
        noise.stop(now + 0.5)
    }

    /** Low rumble drone tied to deformation — foam under stress */
    fun updateDrone(energy: Double) {
        ensureResumed()
        if (!isDroneActive) {
            startDrone()
        }
        val now = ctx.currentTime as Double
        val freq = 50.0 + energy * 80.0
        val vol = (energy * 0.07).coerceAtMost(0.07)
        droneOsc.frequency.setTargetAtTime(freq, now, 0.15)
        droneGain.gain.setTargetAtTime(vol, now, 0.08)
    }

    private fun startDrone() {
        val now = ctx.currentTime as Double
        droneOsc = ctx.createOscillator()
        droneGain = ctx.createGain()

        val filter = ctx.createBiquadFilter()
        filter.type = "lowpass"
        filter.frequency.setValueAtTime(200, now)

        droneOsc.type = "sine"
        droneOsc.frequency.setValueAtTime(50, now)
        droneGain.gain.setValueAtTime(0.0, now)

        droneOsc.connect(filter)
        filter.connect(droneGain)
        droneGain.connect(ctx.destination)

        droneOsc.start(now)
        isDroneActive = true
    }

    /** Soft squish sound for squeeze/stretch — muffled foam compression */
    fun playSquish(intensity: Double) {
        ensureResumed()
        val now = ctx.currentTime as Double

        val bufferSize = (ctx.sampleRate as Double * 0.1).toInt()
        val buffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate)
        val data = buffer.getChannelData(0)
        for (i in 0 until bufferSize) {
            data[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0) * 0.15
        }
        val noise = ctx.createBufferSource()
        noise.buffer = buffer

        val filter = ctx.createBiquadFilter()
        filter.type = "lowpass"
        val cutoff = 300.0 + intensity * 400.0
        filter.frequency.setValueAtTime(cutoff, now)
        filter.frequency.exponentialRampToValueAtTime(80, now + 0.1)

        val gain = ctx.createGain()
        val vol = (intensity * 0.06).coerceIn(0.01, 0.06)
        gain.gain.setValueAtTime(vol, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.1)

        noise.connect(filter)
        filter.connect(gain)
        gain.connect(ctx.destination)

        noise.start(now)
        noise.stop(now + 0.12)
    }

    /** Click sound for color change */
    fun playClick() {
        ensureResumed()
        val now = ctx.currentTime as Double
        val osc = ctx.createOscillator()
        val gain = ctx.createGain()

        osc.type = "sine"
        osc.frequency.setValueAtTime(1200, now)
        osc.frequency.exponentialRampToValueAtTime(800, now + 0.03)

        gain.gain.setValueAtTime(0.08, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.05)

        osc.connect(gain)
        gain.connect(ctx.destination)

        osc.start(now)
        osc.stop(now + 0.06)
    }

    // ========== ASMR gesture sounds ==========

    /** Explode — sparkly burst: bright pop + shimmering scatter + sub-bass thump */
    fun playExplode() {
        ensureResumed()
        val now = ctx.currentTime as Double
        val sr = ctx.sampleRate as Double

        // Layer 1: bright pop (band-passed noise burst)
        val popSize = (sr * 0.08).toInt()
        val popBuf = ctx.createBuffer(1, popSize, ctx.sampleRate)
        val popData = popBuf.getChannelData(0)
        for (i in 0 until popSize) {
            popData[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0) * 0.4
        }
        val pop = ctx.createBufferSource()
        pop.buffer = popBuf
        val popFilter = ctx.createBiquadFilter()
        popFilter.type = "bandpass"
        popFilter.frequency.setValueAtTime(2500, now)
        popFilter.frequency.exponentialRampToValueAtTime(500, now + 0.07)
        popFilter.Q.setValueAtTime(2, now)
        val popGain = ctx.createGain()
        popGain.gain.setValueAtTime(0.3, now)
        popGain.gain.exponentialRampToValueAtTime(0.001, now + 0.1)
        pop.connect(popFilter)
        popFilter.connect(popGain)
        popGain.connect(ctx.destination)
        pop.start(now)
        pop.stop(now + 0.12)

        // Layer 2: sparkle scatter (granular high-frequency shimmer)
        val sparkleSize = (sr * 0.8).toInt()
        val sparkleBuf = ctx.createBuffer(1, sparkleSize, ctx.sampleRate)
        val sparkleData = sparkleBuf.getChannelData(0)
        for (i in 0 until sparkleSize) {
            val grain = if (kotlin.random.Random.nextDouble() > 0.7) 1.0 else 0.08
            sparkleData[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0) * 0.12 * grain
        }
        val sparkle = ctx.createBufferSource()
        sparkle.buffer = sparkleBuf
        val sparkleFilter = ctx.createBiquadFilter()
        sparkleFilter.type = "highpass"
        sparkleFilter.frequency.setValueAtTime(3000, now)
        sparkleFilter.frequency.exponentialRampToValueAtTime(800, now + 0.6)
        val sparkleGain = ctx.createGain()
        sparkleGain.gain.setValueAtTime(0.0, now)
        sparkleGain.gain.linearRampToValueAtTime(0.14, now + 0.04)
        sparkleGain.gain.exponentialRampToValueAtTime(0.001, now + 0.7)
        sparkle.connect(sparkleFilter)
        sparkleFilter.connect(sparkleGain)
        sparkleGain.connect(ctx.destination)
        sparkle.start(now)
        sparkle.stop(now + 0.8)

        // Layer 3: sub-bass thump (felt more than heard)
        val bass = ctx.createOscillator()
        bass.type = "sine"
        bass.frequency.setValueAtTime(100, now)
        bass.frequency.exponentialRampToValueAtTime(35, now + 0.2)
        val bassGain = ctx.createGain()
        bassGain.gain.setValueAtTime(0.25, now)
        bassGain.gain.exponentialRampToValueAtTime(0.001, now + 0.3)
        bass.connect(bassGain)
        bassGain.connect(ctx.destination)
        bass.start(now)
        bass.stop(now + 0.35)
    }

    /** Scramble — crinkly rain stick: granular noise + wobbling filter + warble */
    fun playScramble() {
        ensureResumed()
        val now = ctx.currentTime as Double
        val sr = ctx.sampleRate as Double

        // Layer 1: crinkle texture (granular band-passed noise)
        val crinkleSize = (sr * 0.7).toInt()
        val crinkleBuf = ctx.createBuffer(1, crinkleSize, ctx.sampleRate)
        val crinkleData = crinkleBuf.getChannelData(0)
        for (i in 0 until crinkleSize) {
            val grain = if (kotlin.random.Random.nextDouble() > 0.6) 1.0 else 0.04
            crinkleData[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0) * 0.18 * grain
        }
        val crinkle = ctx.createBufferSource()
        crinkle.buffer = crinkleBuf
        val crinkleFilter = ctx.createBiquadFilter()
        crinkleFilter.type = "bandpass"
        crinkleFilter.frequency.setValueAtTime(2500, now)
        crinkleFilter.frequency.setTargetAtTime(800.0, now, 0.2)
        crinkleFilter.Q.setValueAtTime(3, now)
        crinkleFilter.Q.setTargetAtTime(1.0, now + 0.3, 0.1)
        val crinkleGain = ctx.createGain()
        crinkleGain.gain.setValueAtTime(0.0, now)
        crinkleGain.gain.linearRampToValueAtTime(0.18, now + 0.03)
        crinkleGain.gain.setTargetAtTime(0.1, now + 0.1, 0.15)
        crinkleGain.gain.exponentialRampToValueAtTime(0.001, now + 0.6)
        crinkle.connect(crinkleFilter)
        crinkleFilter.connect(crinkleGain)
        crinkleGain.connect(ctx.destination)
        crinkle.start(now)
        crinkle.stop(now + 0.65)

        // Layer 2: warble underneath (wobbling triangle wave)
        val warble = ctx.createOscillator()
        warble.type = "triangle"
        warble.frequency.setValueAtTime(200, now)
        warble.frequency.setTargetAtTime(140.0, now + 0.1, 0.08)
        warble.frequency.setTargetAtTime(260.0, now + 0.25, 0.06)
        warble.frequency.setTargetAtTime(110.0, now + 0.4, 0.08)
        val warbleGain = ctx.createGain()
        warbleGain.gain.setValueAtTime(0.06, now)
        warbleGain.gain.exponentialRampToValueAtTime(0.001, now + 0.5)
        warble.connect(warbleGain)
        warbleGain.connect(ctx.destination)
        warble.start(now)
        warble.stop(now + 0.55)

        // Layer 3: soft high shimmer (adds "scatter" feel)
        val shimmer = ctx.createOscillator()
        shimmer.type = "sine"
        shimmer.frequency.setValueAtTime(1800, now)
        shimmer.frequency.exponentialRampToValueAtTime(600, now + 0.4)
        val shimGain = ctx.createGain()
        shimGain.gain.setValueAtTime(0.03, now)
        shimGain.gain.exponentialRampToValueAtTime(0.001, now + 0.35)
        shimmer.connect(shimGain)
        shimGain.connect(ctx.destination)
        shimmer.start(now)
        shimmer.stop(now + 0.4)
    }

    /** Punch — deep pillow thwack: transient thud + smack texture + boing tail */
    fun playPunch() {
        ensureResumed()
        val now = ctx.currentTime as Double
        val sr = ctx.sampleRate as Double

        // Layer 1: deep transient thud
        val thud = ctx.createOscillator()
        thud.type = "sine"
        thud.frequency.setValueAtTime(120, now)
        thud.frequency.exponentialRampToValueAtTime(28, now + 0.15)
        val thudGain = ctx.createGain()
        thudGain.gain.setValueAtTime(0.4, now)
        thudGain.gain.exponentialRampToValueAtTime(0.001, now + 0.25)
        thud.connect(thudGain)
        thudGain.connect(ctx.destination)
        thud.start(now)
        thud.stop(now + 0.3)

        // Layer 2: impact smack (filtered noise burst)
        val smackSize = (sr * 0.12).toInt()
        val smackBuf = ctx.createBuffer(1, smackSize, ctx.sampleRate)
        val smackData = smackBuf.getChannelData(0)
        for (i in 0 until smackSize) {
            smackData[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0) * 0.3
        }
        val smack = ctx.createBufferSource()
        smack.buffer = smackBuf
        val smackFilter = ctx.createBiquadFilter()
        smackFilter.type = "lowpass"
        smackFilter.frequency.setValueAtTime(1400, now)
        smackFilter.frequency.exponentialRampToValueAtTime(80, now + 0.1)
        val smackGain = ctx.createGain()
        smackGain.gain.setValueAtTime(0.22, now)
        smackGain.gain.exponentialRampToValueAtTime(0.001, now + 0.12)
        smack.connect(smackFilter)
        smackFilter.connect(smackGain)
        smackGain.connect(ctx.destination)
        smack.start(now)
        smack.stop(now + 0.15)

        // Layer 3: soft boing resonance tail (satisfying wobble)
        val boing = ctx.createOscillator()
        boing.type = "sine"
        boing.frequency.setValueAtTime(220, now + 0.04)
        boing.frequency.exponentialRampToValueAtTime(70, now + 0.45)
        val boingGain = ctx.createGain()
        boingGain.gain.setValueAtTime(0.0, now)
        boingGain.gain.linearRampToValueAtTime(0.08, now + 0.05)
        boingGain.gain.exponentialRampToValueAtTime(0.001, now + 0.5)
        boing.connect(boingGain)
        boingGain.connect(ctx.destination)
        boing.start(now)
        boing.stop(now + 0.55)
    }

    /** Expand — gentle whoosh: rising breathy noise + warm pad */
    fun playExpand() {
        ensureResumed()
        val now = ctx.currentTime as Double
        val sr = ctx.sampleRate as Double

        // Layer 1: rising whoosh (band-passed noise sweep up then down)
        val whooshSize = (sr * 0.5).toInt()
        val whooshBuf = ctx.createBuffer(1, whooshSize, ctx.sampleRate)
        val whooshData = whooshBuf.getChannelData(0)
        for (i in 0 until whooshSize) {
            whooshData[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0) * 0.1
        }
        val whoosh = ctx.createBufferSource()
        whoosh.buffer = whooshBuf
        val whooshFilter = ctx.createBiquadFilter()
        whooshFilter.type = "bandpass"
        whooshFilter.frequency.setValueAtTime(200, now)
        whooshFilter.frequency.linearRampToValueAtTime(1200, now + 0.18)
        whooshFilter.frequency.exponentialRampToValueAtTime(300, now + 0.4)
        whooshFilter.Q.setValueAtTime(1.5, now)
        val whooshGain = ctx.createGain()
        whooshGain.gain.setValueAtTime(0.0, now)
        whooshGain.gain.linearRampToValueAtTime(0.15, now + 0.07)
        whooshGain.gain.exponentialRampToValueAtTime(0.001, now + 0.45)
        whoosh.connect(whooshFilter)
        whooshFilter.connect(whooshGain)
        whooshGain.connect(ctx.destination)
        whoosh.start(now)
        whoosh.stop(now + 0.5)

        // Layer 2: warm triangle pad
        val pad = ctx.createOscillator()
        pad.type = "triangle"
        pad.frequency.setValueAtTime(80, now)
        pad.frequency.linearRampToValueAtTime(130, now + 0.3)
        val padGain = ctx.createGain()
        padGain.gain.setValueAtTime(0.06, now)
        padGain.gain.exponentialRampToValueAtTime(0.001, now + 0.4)
        pad.connect(padGain)
        padGain.connect(ctx.destination)
        pad.start(now)
        pad.stop(now + 0.45)
    }

    /** Squeeze — foam compression: descending tone + lowpass squish texture */
    fun playSqueeze() {
        ensureResumed()
        val now = ctx.currentTime as Double
        val sr = ctx.sampleRate as Double

        // Layer 1: descending sine (compressed air)
        val osc = ctx.createOscillator()
        osc.type = "sine"
        osc.frequency.setValueAtTime(260, now)
        osc.frequency.exponentialRampToValueAtTime(55, now + 0.3)
        val oscGain = ctx.createGain()
        oscGain.gain.setValueAtTime(0.12, now)
        oscGain.gain.exponentialRampToValueAtTime(0.001, now + 0.35)
        osc.connect(oscGain)
        oscGain.connect(ctx.destination)
        osc.start(now)
        osc.stop(now + 0.38)

        // Layer 2: low-passed squish noise
        val sqSize = (sr * 0.3).toInt()
        val sqBuf = ctx.createBuffer(1, sqSize, ctx.sampleRate)
        val sqData = sqBuf.getChannelData(0)
        for (i in 0 until sqSize) {
            sqData[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0) * 0.12
        }
        val sqNoise = ctx.createBufferSource()
        sqNoise.buffer = sqBuf
        val sqFilter = ctx.createBiquadFilter()
        sqFilter.type = "lowpass"
        sqFilter.frequency.setValueAtTime(500, now)
        sqFilter.frequency.exponentialRampToValueAtTime(70, now + 0.25)
        val sqGain = ctx.createGain()
        sqGain.gain.setValueAtTime(0.1, now)
        sqGain.gain.exponentialRampToValueAtTime(0.001, now + 0.28)
        sqNoise.connect(sqFilter)
        sqFilter.connect(sqGain)
        sqGain.connect(ctx.destination)
        sqNoise.start(now)
        sqNoise.stop(now + 0.3)
    }

    /** Stretch — taffy pull: smooth rising tone + breathy sweep */
    fun playStretch() {
        ensureResumed()
        val now = ctx.currentTime as Double
        val sr = ctx.sampleRate as Double

        // Layer 1: smooth rising sine (stretchy feel)
        val osc = ctx.createOscillator()
        osc.type = "sine"
        osc.frequency.setValueAtTime(100, now)
        osc.frequency.linearRampToValueAtTime(300, now + 0.3)
        val oscGain = ctx.createGain()
        oscGain.gain.setValueAtTime(0.08, now)
        oscGain.gain.exponentialRampToValueAtTime(0.001, now + 0.35)
        osc.connect(oscGain)
        oscGain.connect(ctx.destination)
        osc.start(now)
        osc.stop(now + 0.38)

        // Layer 2: breathy noise sweep
        val brSize = (sr * 0.35).toInt()
        val brBuf = ctx.createBuffer(1, brSize, ctx.sampleRate)
        val brData = brBuf.getChannelData(0)
        for (i in 0 until brSize) {
            brData[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0) * 0.06
        }
        val br = ctx.createBufferSource()
        br.buffer = brBuf
        val brFilter = ctx.createBiquadFilter()
        brFilter.type = "bandpass"
        brFilter.frequency.setValueAtTime(300, now)
        brFilter.frequency.linearRampToValueAtTime(700, now + 0.3)
        brFilter.Q.setValueAtTime(2, now)
        val brGain = ctx.createGain()
        brGain.gain.setValueAtTime(0.1, now)
        brGain.gain.exponentialRampToValueAtTime(0.001, now + 0.3)
        br.connect(brFilter)
        brFilter.connect(brGain)
        brGain.connect(ctx.destination)
        br.start(now)
        br.stop(now + 0.35)
    }

    /** Bubble pop — soft, satisfying pop for color change */
    fun playBubble() {
        ensureResumed()
        val now = ctx.currentTime as Double
        val sr = ctx.sampleRate as Double

        // Layer 1: bright sine ping
        val ping = ctx.createOscillator()
        ping.type = "sine"
        ping.frequency.setValueAtTime(900, now)
        ping.frequency.exponentialRampToValueAtTime(500, now + 0.08)
        val pingGain = ctx.createGain()
        pingGain.gain.setValueAtTime(0.1, now)
        pingGain.gain.exponentialRampToValueAtTime(0.001, now + 0.12)
        ping.connect(pingGain)
        pingGain.connect(ctx.destination)
        ping.start(now)
        ping.stop(now + 0.15)

        // Layer 2: tiny air pop (noise burst)
        val popSize = (sr * 0.04).toInt()
        val popBuf = ctx.createBuffer(1, popSize, ctx.sampleRate)
        val popData = popBuf.getChannelData(0)
        for (i in 0 until popSize) {
            popData[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0) * 0.15
        }
        val pop = ctx.createBufferSource()
        pop.buffer = popBuf
        val popFilter = ctx.createBiquadFilter()
        popFilter.type = "highpass"
        popFilter.frequency.setValueAtTime(2000, now)
        val popGain = ctx.createGain()
        popGain.gain.setValueAtTime(0.12, now)
        popGain.gain.exponentialRampToValueAtTime(0.001, now + 0.05)
        pop.connect(popFilter)
        popFilter.connect(popGain)
        popGain.connect(ctx.destination)
        pop.start(now)
        pop.stop(now + 0.06)
    }

    /** Pinch — tight, high-frequency squish like pinching putty */
    fun playPinch(intensity: Double) {
        ensureResumed()
        val now = ctx.currentTime as Double
        val freq = 300.0 + intensity * 400.0

        val osc = ctx.createOscillator()
        osc.type = "sine"
        osc.frequency.setValueAtTime(freq, now)
        osc.frequency.exponentialRampToValueAtTime(100, now + 0.15)
        val gain = ctx.createGain()
        gain.gain.setValueAtTime(0.08 * intensity, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.2)
        val filter = ctx.createBiquadFilter()
        filter.type = "lowpass"
        filter.frequency.setValueAtTime(800, now)
        osc.connect(filter)
        filter.connect(gain)
        gain.connect(ctx.destination)
        osc.start(now)
        osc.stop(now + 0.25)
    }

    /** Slap — sharp, bright impact with a ringing tail */
    fun playSlap() {
        ensureResumed()
        val now = ctx.currentTime as Double
        val sr = ctx.sampleRate as Double

        // Layer 1: sharp transient crack
        val crackSize = (sr * 0.02).toInt()
        val crackBuf = ctx.createBuffer(1, crackSize, ctx.sampleRate)
        val crackData = crackBuf.getChannelData(0)
        for (i in 0 until crackSize) {
            crackData[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0) * 0.4
        }
        val crack = ctx.createBufferSource()
        crack.buffer = crackBuf
        val crackFilter = ctx.createBiquadFilter()
        crackFilter.type = "bandpass"
        crackFilter.frequency.setValueAtTime(2500, now)
        crackFilter.Q.setValueAtTime(2.0, now)
        val crackGain = ctx.createGain()
        crackGain.gain.setValueAtTime(0.2, now)
        crackGain.gain.exponentialRampToValueAtTime(0.001, now + 0.06)
        crack.connect(crackFilter)
        crackFilter.connect(crackGain)
        crackGain.connect(ctx.destination)
        crack.start(now)
        crack.stop(now + 0.08)

        // Layer 2: ringing tone
        val ring = ctx.createOscillator()
        ring.type = "sine"
        ring.frequency.setValueAtTime(400, now)
        ring.frequency.exponentialRampToValueAtTime(200, now + 0.2)
        val ringGain = ctx.createGain()
        ringGain.gain.setValueAtTime(0.06, now)
        ringGain.gain.exponentialRampToValueAtTime(0.001, now + 0.25)
        ring.connect(ringGain)
        ringGain.connect(ctx.destination)
        ring.start(now)
        ring.stop(now + 0.3)
    }

    /** Knead — continuous low rumble with pulsing character */
    fun playKnead() {
        ensureResumed()
        val now = ctx.currentTime as Double

        val osc = ctx.createOscillator()
        osc.type = "sine"
        osc.frequency.setValueAtTime(80, now)
        osc.frequency.linearRampToValueAtTime(120, now + 0.15)
        osc.frequency.linearRampToValueAtTime(80, now + 0.3)
        val gain = ctx.createGain()
        gain.gain.setValueAtTime(0.06, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.35)
        val filter = ctx.createBiquadFilter()
        filter.type = "lowpass"
        filter.frequency.setValueAtTime(200, now)
        osc.connect(filter)
        filter.connect(gain)
        gain.connect(ctx.destination)
        osc.start(now)
        osc.stop(now + 0.4)
    }

    /** Resize — gentle whoosh that rises or falls based on direction */
    fun playResize(expanding: Boolean) {
        ensureResumed()
        val now = ctx.currentTime as Double
        val sr = ctx.sampleRate as Double

        val noiseSize = (sr * 0.15).toInt()
        val noiseBuf = ctx.createBuffer(1, noiseSize, ctx.sampleRate)
        val noiseData = noiseBuf.getChannelData(0)
        for (i in 0 until noiseSize) {
            noiseData[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0) * 0.1
        }
        val noise = ctx.createBufferSource()
        noise.buffer = noiseBuf
        val filter = ctx.createBiquadFilter()
        filter.type = "bandpass"
        if (expanding) {
            filter.frequency.setValueAtTime(400, now)
            filter.frequency.linearRampToValueAtTime(1200, now + 0.15)
        } else {
            filter.frequency.setValueAtTime(1200, now)
            filter.frequency.linearRampToValueAtTime(400, now + 0.15)
        }
        filter.Q.setValueAtTime(1.5, now)
        val gain = ctx.createGain()
        gain.gain.setValueAtTime(0.08, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.2)
        noise.connect(filter)
        filter.connect(gain)
        gain.connect(ctx.destination)
        noise.start(now)
        noise.stop(now + 0.25)
    }

    /** Slice — sharp whoosh + clean separation: high-pass noise sweep + descending tone */
    fun playSlice() {
        ensureResumed()
        val now = ctx.currentTime as Double
        val sr = ctx.sampleRate as Double

        // Layer 1: sharp whoosh (high-pass noise sweep — the cutting air sound)
        val whooshSize = (sr * 0.35).toInt()
        val whooshBuf = ctx.createBuffer(1, whooshSize, ctx.sampleRate)
        val whooshData = whooshBuf.getChannelData(0)
        for (i in 0 until whooshSize) {
            whooshData[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0) * 0.3
        }
        val whoosh = ctx.createBufferSource()
        whoosh.buffer = whooshBuf
        val whooshFilter = ctx.createBiquadFilter()
        whooshFilter.type = "highpass"
        whooshFilter.frequency.setValueAtTime(1500, now)
        whooshFilter.frequency.exponentialRampToValueAtTime(300, now + 0.3)
        whooshFilter.Q.setValueAtTime(1.5, now)
        val whooshGain = ctx.createGain()
        whooshGain.gain.setValueAtTime(0.0, now)
        whooshGain.gain.linearRampToValueAtTime(0.25, now + 0.02)
        whooshGain.gain.exponentialRampToValueAtTime(0.001, now + 0.3)
        whoosh.connect(whooshFilter)
        whooshFilter.connect(whooshGain)
        whooshGain.connect(ctx.destination)
        whoosh.start(now)
        whoosh.stop(now + 0.35)

        // Layer 2: descending separation tone (the "split" feeling)
        val tone = ctx.createOscillator()
        tone.type = "sine"
        tone.frequency.setValueAtTime(600, now)
        tone.frequency.exponentialRampToValueAtTime(80, now + 0.4)
        val toneGain = ctx.createGain()
        toneGain.gain.setValueAtTime(0.15, now)
        toneGain.gain.exponentialRampToValueAtTime(0.001, now + 0.45)
        tone.connect(toneGain)
        toneGain.connect(ctx.destination)
        tone.start(now)
        tone.stop(now + 0.5)

        // Layer 3: bright transient crack at the moment of slice
        val crackSize = (sr * 0.03).toInt()
        val crackBuf = ctx.createBuffer(1, crackSize, ctx.sampleRate)
        val crackData = crackBuf.getChannelData(0)
        for (i in 0 until crackSize) {
            crackData[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0) * 0.35
        }
        val crack = ctx.createBufferSource()
        crack.buffer = crackBuf
        val crackFilter = ctx.createBiquadFilter()
        crackFilter.type = "bandpass"
        crackFilter.frequency.setValueAtTime(3500, now)
        crackFilter.Q.setValueAtTime(2.0, now)
        val crackGain = ctx.createGain()
        crackGain.gain.setValueAtTime(0.2, now)
        crackGain.gain.exponentialRampToValueAtTime(0.001, now + 0.05)
        crack.connect(crackFilter)
        crackFilter.connect(crackGain)
        crackGain.connect(ctx.destination)
        crack.start(now)
        crack.stop(now + 0.08)
    }

    /** Pull — stretchy taffy-pull sound */
    fun playPull() {
        ensureResumed()
        val now = ctx.currentTime as Double

        val osc = ctx.createOscillator()
        osc.type = "sine"
        osc.frequency.setValueAtTime(150, now)
        osc.frequency.exponentialRampToValueAtTime(300, now + 0.2)
        osc.frequency.exponentialRampToValueAtTime(100, now + 0.4)
        val gain = ctx.createGain()
        gain.gain.setValueAtTime(0.07, now)
        gain.gain.linearRampToValueAtTime(0.1, now + 0.15)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.45)
        osc.connect(gain)
        gain.connect(ctx.destination)
        osc.start(now)
        osc.stop(now + 0.5)
    }
}

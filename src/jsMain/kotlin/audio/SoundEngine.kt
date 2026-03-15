package audio

class SoundEngine {

    private val ctx: dynamic = js("new (window.AudioContext || window.webkitAudioContext)()")

    // Overlap prevention
    private var activeSoundCount = 0
    private val maxSimultaneous = 2
    private val lastPlayTime = mutableMapOf<String, Double>()
    private val globalMinInterval = 0.8 // seconds between ANY sound

    private var lastGlobalPlay = 0.0

    private fun canPlay(soundId: String): Boolean {
        val now = ctx.currentTime as Double
        if (activeSoundCount >= maxSimultaneous) return false
        if (now - lastGlobalPlay < globalMinInterval) return false
        val last = lastPlayTime[soundId] ?: 0.0
        if (now - last < 1.2) return false // same sound needs 1.2s gap
        lastPlayTime[soundId] = now
        lastGlobalPlay = now
        return true
    }

    private fun trackSound(durationSec: Double) {
        activeSoundCount++
        val ms = (durationSec * 1000).toInt()
        js("setTimeout")(fun() { activeSoundCount = (activeSoundCount - 1).coerceAtLeast(0) }, ms)
    }

    fun ensureResumed() {
        if (ctx.state == "suspended") {
            ctx.resume()
        }
    }

    /** Gentle low thump on poke */
    fun playPoke() {
        if (!canPlay("poke")) return
        trackSound(0.3)
        ensureResumed()
        val now = ctx.currentTime as Double
        val osc = ctx.createOscillator()
        val gain = ctx.createGain()
        val filter = ctx.createBiquadFilter()
        osc.type = "sine"
        osc.frequency.setValueAtTime(140, now)
        osc.frequency.exponentialRampToValueAtTime(50, now + 0.2)
        filter.type = "lowpass"
        filter.frequency.setValueAtTime(400, now)
        filter.frequency.exponentialRampToValueAtTime(80, now + 0.25)
        gain.gain.setValueAtTime(0.1, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.25)
        osc.connect(filter)
        filter.connect(gain)
        gain.connect(ctx.destination)
        osc.start(now)
        osc.stop(now + 0.3)
    }

    /** Soft airy puff on pulse */
    fun playPulse() {
        if (!canPlay("pulse")) return
        trackSound(0.25)
        ensureResumed()
        val now = ctx.currentTime as Double
        // Low tone
        val osc = ctx.createOscillator()
        val gain = ctx.createGain()
        osc.type = "sine"
        osc.frequency.setValueAtTime(100, now)
        osc.frequency.exponentialRampToValueAtTime(55, now + 0.18)
        gain.gain.setValueAtTime(0.08, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.2)
        osc.connect(gain)
        gain.connect(ctx.destination)
        osc.start(now)
        osc.stop(now + 0.25)
        // Soft filtered noise layer
        val sr = ctx.sampleRate as Double
        val bufSize = (sr * 0.15).toInt()
        val buf = ctx.createBuffer(1, bufSize, ctx.sampleRate)
        val data = buf.getChannelData(0)
        for (i in 0 until bufSize) {
            data[i] = (kotlin.random.Random.nextDouble() * 2.0 - 1.0) * 0.08
        }
        val noise = ctx.createBufferSource()
        noise.buffer = buf
        val nf = ctx.createBiquadFilter()
        nf.type = "lowpass"
        nf.frequency.setValueAtTime(300, now)
        nf.frequency.exponentialRampToValueAtTime(80, now + 0.12)
        val ng = ctx.createGain()
        ng.gain.setValueAtTime(0.06, now)
        ng.gain.exponentialRampToValueAtTime(0.001, now + 0.15)
        noise.connect(nf)
        nf.connect(ng)
        ng.connect(ctx.destination)
        noise.start(now)
        noise.stop(now + 0.18)
    }

    /** Gentle exhale on reset */
    fun playReset() {
        if (!canPlay("reset")) return
        trackSound(0.35)
        ensureResumed()
        val now = ctx.currentTime as Double
        val osc = ctx.createOscillator()
        val gain = ctx.createGain()
        osc.type = "triangle"
        osc.frequency.setValueAtTime(150, now)
        osc.frequency.exponentialRampToValueAtTime(60, now + 0.25)
        gain.gain.setValueAtTime(0.08, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.3)
        osc.connect(gain)
        gain.connect(ctx.destination)
        osc.start(now)
        osc.stop(now + 0.35)
    }

    /** Soft squish — warm filtered tone */
    fun playSquish(intensity: Double) {
        if (!canPlay("squish")) return
        trackSound(0.2)
        ensureResumed()
        val now = ctx.currentTime as Double
        val freq = 80.0 + intensity * 60.0
        val vol = (0.05 + intensity * 0.05).coerceAtMost(0.1)
        val osc = ctx.createOscillator()
        val gain = ctx.createGain()
        val filter = ctx.createBiquadFilter()
        osc.type = "sine"
        osc.frequency.setValueAtTime(freq, now)
        osc.frequency.exponentialRampToValueAtTime(45, now + 0.15)
        filter.type = "lowpass"
        filter.frequency.setValueAtTime(250, now)
        filter.frequency.exponentialRampToValueAtTime(60, now + 0.15)
        gain.gain.setValueAtTime(vol, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.18)
        osc.connect(filter)
        filter.connect(gain)
        gain.connect(ctx.destination)
        osc.start(now)
        osc.stop(now + 0.2)
    }

    /** Squeeze — descending compressed tone */
    fun playSqueeze() {
        if (!canPlay("squeeze")) return
        trackSound(0.25)
        ensureResumed()
        val now = ctx.currentTime as Double
        val osc = ctx.createOscillator()
        val gain = ctx.createGain()
        osc.type = "sine"
        osc.frequency.setValueAtTime(160, now)
        osc.frequency.exponentialRampToValueAtTime(40, now + 0.2)
        gain.gain.setValueAtTime(0.08, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.22)
        osc.connect(gain)
        gain.connect(ctx.destination)
        osc.start(now)
        osc.stop(now + 0.25)
    }

    /** Stretch — gentle rising tone */
    fun playStretch() {
        if (!canPlay("stretch")) return
        trackSound(0.25)
        ensureResumed()
        val now = ctx.currentTime as Double
        val osc = ctx.createOscillator()
        val gain = ctx.createGain()
        osc.type = "triangle"
        osc.frequency.setValueAtTime(70, now)
        osc.frequency.linearRampToValueAtTime(150, now + 0.18)
        gain.gain.setValueAtTime(0.07, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.22)
        osc.connect(gain)
        gain.connect(ctx.destination)
        osc.start(now)
        osc.stop(now + 0.25)
    }

    /** Pinch — soft descending blip */
    fun playPinch(intensity: Double) {
        if (!canPlay("pinch")) return
        trackSound(0.2)
        ensureResumed()
        val now = ctx.currentTime as Double
        val freq = 100.0 + intensity * 80.0
        val osc = ctx.createOscillator()
        val gain = ctx.createGain()
        osc.type = "sine"
        osc.frequency.setValueAtTime(freq, now)
        osc.frequency.exponentialRampToValueAtTime(50, now + 0.12)
        gain.gain.setValueAtTime(0.06, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.15)
        osc.connect(gain)
        gain.connect(ctx.destination)
        osc.start(now)
        osc.stop(now + 0.2)
    }

    /** Knead — gentle wobble tone */
    fun playKnead() {
        if (!canPlay("knead")) return
        trackSound(0.3)
        ensureResumed()
        val now = ctx.currentTime as Double
        val osc = ctx.createOscillator()
        val gain = ctx.createGain()
        osc.type = "sine"
        osc.frequency.setValueAtTime(70, now)
        osc.frequency.linearRampToValueAtTime(110, now + 0.1)
        osc.frequency.linearRampToValueAtTime(65, now + 0.2)
        gain.gain.setValueAtTime(0.06, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.25)
        osc.connect(gain)
        gain.connect(ctx.destination)
        osc.start(now)
        osc.stop(now + 0.3)
    }

    /** Resize — directional sweep */
    fun playResize(expanding: Boolean) {
        if (!canPlay("resize")) return
        trackSound(0.2)
        ensureResumed()
        val now = ctx.currentTime as Double
        val osc = ctx.createOscillator()
        val gain = ctx.createGain()
        osc.type = "triangle"
        if (expanding) {
            osc.frequency.setValueAtTime(70, now)
            osc.frequency.linearRampToValueAtTime(130, now + 0.12)
        } else {
            osc.frequency.setValueAtTime(130, now)
            osc.frequency.linearRampToValueAtTime(70, now + 0.12)
        }
        gain.gain.setValueAtTime(0.06, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.15)
        osc.connect(gain)
        gain.connect(ctx.destination)
        osc.start(now)
        osc.stop(now + 0.2)
    }

    /** Pull — rising stretch tone */
    fun playPull() {
        if (!canPlay("pull")) return
        trackSound(0.25)
        ensureResumed()
        val now = ctx.currentTime as Double
        val osc = ctx.createOscillator()
        val gain = ctx.createGain()
        osc.type = "sine"
        osc.frequency.setValueAtTime(80, now)
        osc.frequency.exponentialRampToValueAtTime(160, now + 0.15)
        osc.frequency.exponentialRampToValueAtTime(70, now + 0.22)
        gain.gain.setValueAtTime(0.07, now)
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.22)
        osc.connect(gain)
        gain.connect(ctx.destination)
        osc.start(now)
        osc.stop(now + 0.25)
    }

    // Stubs for unused methods still referenced elsewhere
    fun playClick() {}
    fun playBubble() {}
    fun playExplode() {}
    fun playScramble() {}
    fun playPunch() {}
    fun playSlap() {}
    fun playSlice() {}
    fun playExpand() {}
    fun playPluck(frequency: Double = 330.0, intensity: Double = 0.8) {}
    fun playStrum(baseFreq: Double = 164.81) {}
    fun playStringStretch(tension: Double) {}
}

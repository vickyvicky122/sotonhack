package ui

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement

class HtmlOverlay(
    private val onReset: () -> Unit,
    private val onToggleSound: () -> Unit,
    private val onCycleTheme: () -> Unit,
    private val onCycleScale: () -> Unit = {},
    private val onCycleStyle: () -> Unit = {},
    private val onToggleGesture: () -> Unit = {},
    private val onResetStrings: () -> Unit = {},
    private val onCycleEquation: () -> Unit = {},
    private val onSectionChanged: (String) -> Unit = {}
) {
    private var quoteEl: HTMLElement? = null
    private var soundBtn: HTMLElement? = null
    private var currentSection = "strings"

    // Section elements — two main modes
    private var sectionChill: HTMLElement? = null
    private var sectionMaths: HTMLElement? = null

    // Breathing state
    private var breathingActive = false
    private var breathingPhase = "idle"
    private var breathingTimer = 0.0
    private var breathingCircle: HTMLElement? = null
    private var breathingLabel: HTMLElement? = null
    private var breathingBtn: HTMLElement? = null
    private var gestureGuide: HTMLElement? = null
    private var gestureGuideVisible = true
    private var okayPopup: HTMLElement? = null
    private var okayPopupTimer = 0.0

    private val quotes = arrayOf(
        "Take a breath. You're doing well.",
        "Small progress is still progress.",
        "You don't have to rush.",
        "You've solved harder problems before.",
        "Be kind to yourself today.",
        "This moment is yours.",
        "Let go of what you can't control.",
        "One step at a time.",
        "You're exactly where you need to be.",
        "Rest is productive too.",
        "Breathe in calm, breathe out tension.",
        "Your best is enough.",
        "It's okay to take a break.",
        "You are more capable than you think.",
        "Slow down. There's no hurry.",
        "Trust the process.",
        "You're making it happen.",
        "Consistency beats intensity.",
        "Give yourself permission to pause.",
        "Every expert was once a beginner."
    )

    private var quoteVisible = false

    fun setup() {
        // -- Top quote --
        quoteEl = (document.createElement("div") as HTMLDivElement).apply {
            id = "quote"
            textContent = quotes[0]
        }
        document.body?.appendChild(quoteEl!!)

        // -- Section: Chill (blob + breathing) --
        sectionChill = createSection("section-chill", """
            <div class="section-hint">
                drag to rotate · scroll to zoom · squish with your hands
            </div>
            <div class="deform-controls">
                <button class="pill-btn" id="btnReset">Reset</button>
                <button class="pill-btn" id="btnSound">Sound ON</button>
                <button class="pill-btn" id="btnTheme">Theme</button>
                <button class="pill-btn" id="btnColor">Color</button>
                <button class="pill-btn" id="btnStyle">Calm Jelly</button>
                <button class="pill-btn" id="btnBreath">Breathe</button>
                <button class="pill-btn" id="btnGestureGuide">Gestures</button>
            </div>
        """.trimIndent())
        sectionChill?.style?.display = "none"
        document.body?.appendChild(sectionChill!!)

        // Wire chill buttons
        document.getElementById("btnReset")?.addEventListener("click", { onReset() })
        soundBtn = document.getElementById("btnSound") as? HTMLElement
        soundBtn?.addEventListener("click", { onToggleSound() })
        document.getElementById("btnTheme")?.addEventListener("click", { onCycleTheme() })
        document.getElementById("btnColor")?.addEventListener("click", {
            document.dispatchEvent(js("new KeyboardEvent('keydown', {key: 'c'})"))
        })
        document.getElementById("btnStyle")?.addEventListener("click", { onCycleStyle() })
        breathingBtn = document.getElementById("btnBreath") as? HTMLElement
        breathingBtn?.addEventListener("click", { toggleBreathing() })
        document.getElementById("btnGestureGuide")?.addEventListener("click", { toggleGestureGuide() })

        // -- Global gesture toggle (always visible, top-right) --
        val gestureBtn = (document.createElement("button") as HTMLElement).apply {
            id = "btnGesture"
            className = "pill-btn gesture-global-btn"
            textContent = "Turn On The Camera"
        }
        document.body?.appendChild(gestureBtn)
        gestureBtn.addEventListener("click", { onToggleGesture() })

        // -- Breathing overlay (hidden, centered, floats over blob) --
        val breathOverlay = (document.createElement("div") as HTMLDivElement).apply {
            id = "breathOverlay"
            className = "breath-overlay"
            innerHTML = """
                <div class="breathing-ring" id="breathRing">
                    <div class="breathing-circle" id="breathCircle"></div>
                </div>
                <div class="breathing-label" id="breathLabel"></div>
            """.trimIndent()
            style.display = "none"
        }
        document.body?.appendChild(breathOverlay)
        breathingCircle = document.getElementById("breathCircle") as? HTMLElement
        breathingLabel = document.getElementById("breathLabel") as? HTMLElement

        // -- Gesture guide overlay (hidden by default) --
        gestureGuide = (document.createElement("div") as HTMLDivElement).apply {
            id = "gestureGuide"
            className = "gesture-guide"
            innerHTML = """
                <div class="gesture-guide-title">Hand Gestures</div>
                <div class="gesture-guide-grid">
                    <div class="gesture-item"><span class="gesture-emoji">✋</span><span class="gesture-name">Open Palm</span><span class="gesture-desc">Push surface</span></div>
                    <div class="gesture-item"><span class="gesture-emoji">✊</span><span class="gesture-name">Fist</span><span class="gesture-desc">Squeeze</span></div>
                    <div class="gesture-item"><span class="gesture-emoji">☝️</span><span class="gesture-name">Pointer</span><span class="gesture-desc">Poke</span></div>
                    <div class="gesture-item"><span class="gesture-emoji">✌️</span><span class="gesture-name">Victory</span><span class="gesture-desc">Stretch up</span></div>
                    <div class="gesture-item"><span class="gesture-emoji">👌</span><span class="gesture-name">OK Sign</span><span class="gesture-desc">Reset shape</span></div>
                    <div class="gesture-item"><span class="gesture-emoji">👍</span><span class="gesture-name">Thumbs Up</span><span class="gesture-desc">Cycle color</span></div>
                    <div class="gesture-item"><span class="gesture-emoji">🖐️</span><span class="gesture-name">Jazz Hands</span><span class="gesture-desc">Explode</span></div>
                    <div class="gesture-item"><span class="gesture-emoji">🤘</span><span class="gesture-name">Horns</span><span class="gesture-desc">Scramble</span></div>
                    <div class="gesture-item"><span class="gesture-emoji">👊</span><span class="gesture-name">Punch</span><span class="gesture-desc">Impact</span></div>
                    <div class="gesture-item"><span class="gesture-emoji">🤏</span><span class="gesture-name">Pinch</span><span class="gesture-desc">Dent inward</span></div>
                    <div class="gesture-item"><span class="gesture-emoji">🤌</span><span class="gesture-name">Pull</span><span class="gesture-desc">Stretch out</span></div>
                    <div class="gesture-item"><span class="gesture-emoji">✋💨</span><span class="gesture-name">Slap</span><span class="gesture-desc">Broad hit</span></div>
                    <div class="gesture-item"><span class="gesture-emoji">🔪</span><span class="gesture-name">Slice</span><span class="gesture-desc">Split blob</span></div>
                    <div class="gesture-item"><span class="gesture-emoji">🫳</span><span class="gesture-name">Knead</span><span class="gesture-desc">Dough squeeze</span></div>
                    <div class="gesture-item"><span class="gesture-emoji">🙌</span><span class="gesture-name">Two Hands</span><span class="gesture-desc">Resize</span></div>
                </div>
            """.trimIndent()
        }
        document.body?.appendChild(gestureGuide!!)

        // -- "IT WILL BE OKAY" popup (hidden by default) --
        okayPopup = (document.createElement("div") as HTMLDivElement).apply {
            id = "okayPopup"
            className = "okay-popup"
            textContent = "IT WILL BE OKAY"
            style.display = "none"
        }
        document.body?.appendChild(okayPopup!!)

        // -- Section: Maths (3D graph — sidebar handles UI) --
        sectionMaths = createSection("section-maths", """
            <div class="strings-content">
                <div class="strings-subtext">drag to rotate · scroll to shift · pinch to tweak</div>
            </div>
        """.trimIndent())
        document.body?.appendChild(sectionMaths!!)

        // -- Bottom Navigation — two tabs --
        val nav = (document.createElement("nav") as HTMLElement).apply {
            id = "nav"
            innerHTML = """
                <button class="nav-btn" data-section="deform">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="8"/><path d="M12 8c-2 0-3.5 1.5-3.5 4s1.5 4 3.5 4 3.5-1.5 3.5-4-1.5-4-3.5-4z"/></svg>
                    <span>Chill</span>
                </button>
                <button class="nav-btn active" data-section="strings">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M4 20 L8 4 L12 16 L16 8 L20 12"/><line x1="2" y1="20" x2="22" y2="20" opacity="0.3"/></svg>
                    <span>Maths</span>
                </button>
            """.trimIndent()
        }
        document.body?.appendChild(nav)

        // Nav click handlers
        nav.querySelectorAll(".nav-btn").asDynamic().forEach { btn: dynamic ->
            (btn as HTMLElement).addEventListener("click", {
                val section = btn.getAttribute("data-section") as String
                switchSection(section)
            })
        }

        // Don't show quote on startup (Maths is default)
    }

    private fun createSection(id: String, html: String): HTMLElement {
        return (document.createElement("div") as HTMLDivElement).apply {
            this.id = id
            className = "section"
            innerHTML = html
        }
    }

    fun switchSection(name: String) {
        currentSection = name
        // "deform" = chill mode, "strings" = maths mode
        sectionChill?.style?.display = if (name == "deform") "" else "none"
        sectionMaths?.style?.display = if (name == "strings") "" else "none"

        // Update nav active state
        document.querySelectorAll(".nav-btn").asDynamic().forEach { btn: dynamic ->
            val el = btn as HTMLElement
            if (el.getAttribute("data-section") == name) {
                el.classList.add("active")
            } else {
                el.classList.remove("active")
            }
        }

        // Quote shows on chill mode
        quoteEl?.style?.display = if (name == "deform") "" else "none"

        // Stop breathing and hide gesture guide when leaving chill
        if (name != "deform" && breathingActive) {
            stopBreathing()
        }
        if (name != "deform" && gestureGuideVisible) {
            hideGestureGuide()
        }

        onSectionChanged(name)
    }

    fun getCurrentSection(): String = currentSection

    // -- Quotes --
    fun showQuote() {
        quoteEl?.let { el ->
            el.textContent = quotes[(kotlin.random.Random.nextInt(quotes.size))]
            el.classList.add("visible")
            quoteVisible = true
        }
    }

    fun hideQuote() {
        quoteEl?.classList?.remove("visible")
        quoteVisible = false
    }

    fun isQuoteVisible(): Boolean = quoteVisible

    fun updateSoundLabel(on: Boolean) {
        soundBtn?.textContent = if (on) "Sound ON" else "Sound OFF"
    }

    fun updateScaleLabel(label: String) {
        (document.getElementById("btnScale") as? HTMLElement)?.textContent = label
    }

    fun updateStyleLabel(label: String) {
        (document.getElementById("btnStyle") as? HTMLElement)?.textContent = label
    }

    fun updateGestureLabel(on: Boolean) {
        (document.getElementById("btnGesture") as? HTMLElement)?.textContent =
            if (on) "Turn Off The Camera" else "Turn On The Camera"
        // Auto-toggle camera preview with gesture
        if (on) {
            js("window._cameraPreviewOn = true")
            js("if(!document.getElementById('cameraPreview')){var c=document.createElement('canvas');c.id='cameraPreview';c.className='camera-preview';c.width=320;c.height=240;document.body.appendChild(c);}")
            js("document.getElementById('cameraPreview').style.display=''")
        } else {
            js("window._cameraPreviewOn = false")
            js("var cp=document.getElementById('cameraPreview');if(cp)cp.style.display='none'")
        }
    }

    // -- Breathing (integrated into chill mode) --
    private fun toggleBreathing() {
        if (breathingActive) stopBreathing() else startBreathing()
    }

    private fun startBreathing() {
        breathingActive = true
        breathingPhase = "inhale"
        breathingTimer = 0.0
        breathingBtn?.textContent = "Stop Breathing"
        breathingCircle?.classList?.add("inhale")
        breathingLabel?.textContent = "Breathe in..."
        (document.getElementById("breathOverlay") as? HTMLElement)?.style?.display = ""
    }

    private fun stopBreathing() {
        breathingActive = false
        breathingPhase = "idle"
        breathingTimer = 0.0
        breathingBtn?.textContent = "Breathe"
        breathingCircle?.classList?.remove("inhale", "hold", "exhale")
        breathingLabel?.textContent = ""
        (document.getElementById("breathOverlay") as? HTMLElement)?.style?.display = "none"
    }

    fun updateBreathing(dt: Double) {
        if (!breathingActive) return
        breathingTimer += dt

        when (breathingPhase) {
            "inhale" -> if (breathingTimer >= 4.0) {
                breathingPhase = "hold"
                breathingTimer = 0.0
                breathingCircle?.classList?.remove("inhale")
                breathingCircle?.classList?.add("hold")
                breathingLabel?.textContent = "Hold..."
            }
            "hold" -> if (breathingTimer >= 4.0) {
                breathingPhase = "exhale"
                breathingTimer = 0.0
                breathingCircle?.classList?.remove("hold")
                breathingCircle?.classList?.add("exhale")
                breathingLabel?.textContent = "Breathe out..."
            }
            "exhale" -> if (breathingTimer >= 4.0) {
                breathingPhase = "inhale"
                breathingTimer = 0.0
                breathingCircle?.classList?.remove("exhale")
                breathingCircle?.classList?.add("inhale")
                breathingLabel?.textContent = "Breathe in..."
            }
        }
    }

    fun isBreathingActive(): Boolean = breathingActive

    // -- "IT WILL BE OKAY" popup --
    fun showOkayPopup() {
        if (okayPopupTimer > 0.0) return // already showing
        okayPopup?.style?.display = ""
        okayPopup?.classList?.add("visible")
        okayPopupTimer = 3.0
    }

    fun updateOkayPopup(dt: Double) {
        if (okayPopupTimer > 0.0) {
            okayPopupTimer -= dt
            if (okayPopupTimer <= 0.0) {
                okayPopup?.classList?.remove("visible")
                okayPopup?.style?.display = "none"
            }
        }
    }

    // -- Gesture Guide --
    private fun toggleGestureGuide() {
        if (gestureGuideVisible) hideGestureGuide() else showGestureGuide()
    }

    private fun showGestureGuide() {
        gestureGuide?.style?.display = ""
        gestureGuideVisible = true
    }

    private fun hideGestureGuide() {
        gestureGuide?.style?.display = "none"
        gestureGuideVisible = false
    }
}

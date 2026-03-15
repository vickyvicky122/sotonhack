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
    private val onSectionChanged: (String) -> Unit = {}
) {
    private var quoteEl: HTMLElement? = null
    private var soundBtn: HTMLElement? = null
    private var currentSection = "deform"

    // Section elements
    private var sectionDeform: HTMLElement? = null
    private var sectionFocus: HTMLElement? = null
    private var sectionMotivation: HTMLElement? = null
    private var sectionCalm: HTMLElement? = null
    private var sectionStrings: HTMLElement? = null

    // Breathing state
    private var breathingActive = false
    private var breathingPhase = "idle"
    private var breathingTimer = 0.0
    private var breathingCircle: HTMLElement? = null
    private var breathingLabel: HTMLElement? = null
    private var breathingBtn: HTMLElement? = null

    // Motivation
    private var motivationQuoteEl: HTMLElement? = null
    private var motivationIndex = 0

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
        // -- Top quote (shows on deform/home) --
        quoteEl = (document.createElement("div") as HTMLDivElement).apply {
            id = "quote"
            textContent = quotes[0]
        }
        document.body?.appendChild(quoteEl!!)

        // -- Section: Deform --
        sectionDeform = createSection("section-deform", """
            <div class="section-hint">
                drag to rotate · scroll to zoom · keys to deform
            </div>
            <div class="deform-controls">
                <button class="pill-btn" id="btnReset">Reset</button>
                <button class="pill-btn" id="btnSound">Sound ON</button>
                <button class="pill-btn" id="btnTheme">Theme</button>
                <button class="pill-btn" id="btnColor">Color</button>
                <button class="pill-btn" id="btnStyle">Calm Jelly</button>
                <button class="pill-btn" id="btnScale">Normal</button>
                <button class="pill-btn" id="btnGesture">Gesture ON</button>
            </div>
        """.trimIndent())
        document.body?.appendChild(sectionDeform!!)

        // Wire deform buttons
        document.getElementById("btnReset")?.addEventListener("click", { onReset() })
        soundBtn = document.getElementById("btnSound") as? HTMLElement
        soundBtn?.addEventListener("click", { onToggleSound() })
        document.getElementById("btnTheme")?.addEventListener("click", { onCycleTheme() })
        document.getElementById("btnColor")?.addEventListener("click", {
            document.dispatchEvent(js("new KeyboardEvent('keydown', {key: 'c'})"))
        })
        document.getElementById("btnStyle")?.addEventListener("click", { onCycleStyle() })
        document.getElementById("btnScale")?.addEventListener("click", { onCycleScale() })
        document.getElementById("btnGesture")?.addEventListener("click", { onToggleGesture() })

        // -- Section: Focus (Breathing) --
        sectionFocus = createSection("section-focus", """
            <div class="focus-content">
                <div class="breathing-ring" id="breathRing">
                    <div class="breathing-circle" id="breathCircle"></div>
                </div>
                <div class="breathing-label" id="breathLabel">Ready</div>
                <button class="pill-btn breath-btn" id="btnBreath">Start Breathing</button>
                <div class="focus-desc">4 seconds inhale · 4 seconds hold · 4 seconds exhale</div>
            </div>
        """.trimIndent())
        sectionFocus?.style?.display = "none"
        document.body?.appendChild(sectionFocus!!)

        breathingCircle = document.getElementById("breathCircle") as? HTMLElement
        breathingLabel = document.getElementById("breathLabel") as? HTMLElement
        breathingBtn = document.getElementById("btnBreath") as? HTMLElement
        breathingBtn?.addEventListener("click", { toggleBreathing() })

        // -- Section: Motivation --
        sectionMotivation = createSection("section-motivation", """
            <div class="motivation-content">
                <div class="motivation-quote" id="motivQuote">"Small progress is still progress."</div>
                <div class="motivation-actions">
                    <button class="pill-btn" id="btnNextQuote">Next Quote</button>
                </div>
            </div>
        """.trimIndent())
        sectionMotivation?.style?.display = "none"
        document.body?.appendChild(sectionMotivation!!)

        motivationQuoteEl = document.getElementById("motivQuote") as? HTMLElement
        document.getElementById("btnNextQuote")?.addEventListener("click", { nextMotivationQuote() })

        // -- Section: Calm --
        sectionCalm = createSection("section-calm", """
            <div class="calm-content">
                <div class="calm-text">Let your mind drift.</div>
                <div class="calm-subtext">The blob floats gently. Just watch.</div>
            </div>
        """.trimIndent())
        sectionCalm?.style?.display = "none"
        document.body?.appendChild(sectionCalm!!)

        // -- Section: Strings --
        sectionStrings = createSection("section-strings", """
            <div class="strings-content">
                <div class="strings-text">Pull and release.</div>
                <div class="strings-subtext">Click &amp; drag to deform the graph · Space to pluck · R to reset</div>
                <div class="strings-controls">
                    <button class="pill-btn" id="btnStringsReset">Reset</button>
                </div>
            </div>
        """.trimIndent())
        sectionStrings?.style?.display = "none"
        document.body?.appendChild(sectionStrings!!)
        document.getElementById("btnStringsReset")?.addEventListener("click", { onResetStrings() })

        // -- Bottom Navigation --
        val nav = (document.createElement("nav") as HTMLElement).apply {
            id = "nav"
            innerHTML = """
                <button class="nav-btn active" data-section="deform">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="8"/><path d="M12 8c-2 0-3.5 1.5-3.5 4s1.5 4 3.5 4 3.5-1.5 3.5-4-1.5-4-3.5-4z"/></svg>
                    <span>Deform</span>
                </button>
                <button class="nav-btn" data-section="strings">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><line x1="4" y1="4" x2="4" y2="20"/><line x1="20" y1="4" x2="20" y2="20"/><path d="M4 8 Q12 12 20 8"/><path d="M4 14 Q12 18 20 14"/></svg>
                    <span>Strings</span>
                </button>
                <button class="nav-btn" data-section="focus">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg>
                    <span>Focus</span>
                </button>
                <button class="nav-btn" data-section="motivation">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M20.84 4.61a5.5 5.5 0 00-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 00-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 000-7.78z"/></svg>
                    <span>Motivation</span>
                </button>
                <button class="nav-btn" data-section="calm">
                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M17.5 19H9a7 7 0 110-14h.5"/><path d="M17.5 19a4.5 4.5 0 100-9h-1.8"/></svg>
                    <span>Calm</span>
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

        showQuote()
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
        sectionDeform?.style?.display = if (name == "deform") "" else "none"
        sectionFocus?.style?.display = if (name == "focus") "" else "none"
        sectionMotivation?.style?.display = if (name == "motivation") "" else "none"
        sectionCalm?.style?.display = if (name == "calm") "" else "none"
        sectionStrings?.style?.display = if (name == "strings") "" else "none"

        // Update nav active state
        document.querySelectorAll(".nav-btn").asDynamic().forEach { btn: dynamic ->
            val el = btn as HTMLElement
            if (el.getAttribute("data-section") == name) {
                el.classList.add("active")
            } else {
                el.classList.remove("active")
            }
        }

        // Show/hide top quote
        if (name == "deform" || name == "calm") {
            quoteEl?.style?.display = ""
        } else {
            quoteEl?.style?.display = "none"
        }

        // Stop breathing when leaving focus
        if (name != "focus" && breathingActive) {
            stopBreathing()
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
            if (on) "Gesture ON" else "Gesture OFF"
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

    // -- Motivation --
    private fun nextMotivationQuote() {
        motivationIndex = (motivationIndex + 1) % quotes.size
        motivationQuoteEl?.let { el ->
            el.classList.remove("fade-in")
            // Force reflow then re-add
            window.requestAnimationFrame {
                el.textContent = "\"${quotes[motivationIndex]}\""
                el.classList.add("fade-in")
            }
        }
    }

    // -- Breathing --
    private fun toggleBreathing() {
        if (breathingActive) stopBreathing() else startBreathing()
    }

    private fun startBreathing() {
        breathingActive = true
        breathingPhase = "inhale"
        breathingTimer = 0.0
        breathingBtn?.textContent = "Stop"
        breathingCircle?.classList?.add("inhale")
        breathingLabel?.textContent = "Breathe in..."
    }

    private fun stopBreathing() {
        breathingActive = false
        breathingPhase = "idle"
        breathingTimer = 0.0
        breathingBtn?.textContent = "Start Breathing"
        breathingCircle?.classList?.remove("inhale", "hold", "exhale")
        breathingLabel?.textContent = "Ready"
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
}

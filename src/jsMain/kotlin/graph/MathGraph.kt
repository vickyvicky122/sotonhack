package graph

import kotlinx.browser.document
import org.khronos.webgl.Float32Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.dom.HTMLElement
import three.*
import kotlin.math.*

data class GraphEquation(
    val name: String,
    val formula: String,
    var a: Double = 1.0,
    var b: Double = 1.0,
    var c: Double = 0.0,
    val eval: (x: Double, y: Double, a: Double, b: Double, c: Double) -> Double
) {
    fun displayString(): String {
        fun fmt(v: Double): String = ((v * 100).toInt() / 100.0).toString()
        return formula.replace("{a}", fmt(a)).replace("{b}", fmt(b)).replace("{c}", fmt(c))
    }
}

fun makeEquation(name: String, formula: String, a: Double, b: Double, c: Double,
                 eval: (Double, Double, Double, Double, Double) -> Double): GraphEquation {
    return GraphEquation(name, formula, a, b, c, eval)
}

val EQUATION_TEMPLATES = arrayOf(
    { makeEquation("Paraboloid", "z = {a}x² + {b}y² + {c}", 0.3, 0.3, 0.0,
        { x, y, a, b, c -> a * x * x + b * y * y + c }) },
    { makeEquation("Saddle", "z = {a}x² − {b}y² + {c}", 0.3, 0.3, 0.0,
        { x, y, a, b, c -> a * x * x - b * y * y + c }) },
    { makeEquation("Wave", "z = {a}sin({b}x)cos({b}y) + {c}", 1.0, 1.0, 0.0,
        { x, y, a, b, c -> a * sin(b * x) * cos(b * y) + c }) },
    { makeEquation("Ripple", "z = {a}sin({b}√(x²+y²)) + {c}", 1.0, 2.0, 0.0,
        { x, y, a, b, c -> a * sin(b * sqrt(x * x + y * y + 0.01)) + c }) },
    { makeEquation("Gaussian", "z = {a}e^(−{b}(x²+y²)) + {c}", 2.0, 0.5, 0.0,
        { x, y, a, b, c -> a * exp(-b * (x * x + y * y)) + c }) },
    { makeEquation("Plane", "z = {a}x + {b}y + {c}", 0.3, 0.2, 0.0,
        { x, y, a, b, c -> a * x + b * y + c }) }
)

// Surface colors — distinct, not pastel
val SURFACE_COLORS = arrayOf(
    Triple(0.18, 0.44, 0.70),  // desmos blue
    Triple(0.77, 0.20, 0.30),  // warm red
    Triple(0.16, 0.60, 0.38),  // forest green
    Triple(0.60, 0.35, 0.70),  // purple
    Triple(0.85, 0.52, 0.15),  // amber
)

data class SurfaceEntry(
    val equation: GraphEquation,
    val geo: BufferGeometry,
    val solidMesh: Mesh,
    val wireMesh: Mesh,
    val colorBase: Triple<Double, Double, Double>
)

class MathGraph(private val scene: Scene) {

    private val gridSize = 45
    private val rangeX = 4.0
    private val rangeY = 4.0
    private val graphGroup = Group()
    private val gridGroup = Group()

    private val surfaces = mutableListOf<SurfaceEntry>()
    var selectedIndex = 0
        private set

    var isGrabbing = false
        private set
    var locked = false
        private set

    private var panelEl: HTMLElement? = null
    private var eqDisplayEl: HTMLElement? = null

    fun setup() {
        buildGrid()
        graphGroup.add(gridGroup)
        graphGroup.visible = false
        scene.add(graphGroup)

        // Equation display at top center
        eqDisplayEl = (document.createElement("div") as HTMLElement).apply {
            id = "equationDisplay"
            className = "eq-top-bar"
            innerHTML = """
                <div id="eqFormula" class="eq-formula"></div>
                <div class="eq-inputs">
                    <label>a<input id="gInputA" type="number" step="0.1"></label>
                    <label>b<input id="gInputB" type="number" step="0.1"></label>
                    <label>c<input id="gInputC" type="number" step="0.1"></label>
                </div>
            """.trimIndent()
        }
        document.body?.appendChild(eqDisplayEl!!)

        // Start with one equation
        addSurface(0)

        // Build the sidebar panel
        buildPanel()
    }

    fun addSurface(templateIndex: Int) {
        val eq = EQUATION_TEMPLATES[templateIndex % EQUATION_TEMPLATES.size]()
        val colorIdx = surfaces.size % SURFACE_COLORS.size
        val color = SURFACE_COLORS[colorIdx]

        val vertCount = gridSize * gridSize
        val positions = Float32Array(vertCount * 3)
        val colors = Float32Array(vertCount * 3)

        val geo = BufferGeometry()
        geo.setAttribute("position", BufferAttribute(positions, 3))
        geo.setAttribute("color", BufferAttribute(colors, 3))

        // Index buffer
        val geoD = geo.asDynamic()
        val jsIndices = js("[]")
        for (iy in 0 until gridSize - 1) {
            for (ix in 0 until gridSize - 1) {
                val a = iy * gridSize + ix
                val b = iy * gridSize + ix + 1
                val c = (iy + 1) * gridSize + ix
                val d = (iy + 1) * gridSize + ix + 1
                jsIndices.push(a); jsIndices.push(c); jsIndices.push(b)
                jsIndices.push(b); jsIndices.push(c); jsIndices.push(d)
            }
        }
        geoD.setIndex(jsIndices)

        val solidMesh = Mesh(geo, MeshPhysicalMaterial(js("""({
            vertexColors: true, roughness: 0.35, metalness: 0.0,
            clearcoat: 0.2, transparent: true, opacity: 0.8,
            side: 2, flatShading: false
        })""")))

        val wireMesh = Mesh(geo, MeshBasicMaterial(js("""({
            color: 0x333333, wireframe: true, transparent: true, opacity: 0.08
        })""")))

        graphGroup.add(solidMesh)
        graphGroup.add(wireMesh)

        val entry = SurfaceEntry(eq, geo, solidMesh, wireMesh, color)
        surfaces.add(entry)
        selectedIndex = surfaces.size - 1

        updateSurface(entry)
        rebuildPanel()
    }

    fun removeSurface(index: Int) {
        if (surfaces.size <= 1) return
        val entry = surfaces.removeAt(index)
        graphGroup.remove(entry.solidMesh)
        graphGroup.remove(entry.wireMesh)
        entry.geo.dispose()
        if (selectedIndex >= surfaces.size) selectedIndex = surfaces.size - 1
        rebuildPanel()
    }

    fun selectSurface(index: Int) {
        selectedIndex = index.coerceIn(0, surfaces.size - 1)
        rebuildPanel()
    }

    val selectedEquation: GraphEquation? get() = surfaces.getOrNull(selectedIndex)?.equation

    fun rotate(dx: Double, dy: Double) {
        graphGroup.rotation.x += dx
        graphGroup.rotation.y += dy
    }

    fun translate(dx: Double, dz: Double) {
        graphGroup.position.x += dx
        graphGroup.position.z += dz
    }

    fun setVisible(visible: Boolean) {
        graphGroup.visible = visible
        panelEl?.style?.display = if (visible) "" else "none"
        eqDisplayEl?.style?.display = if (visible) "flex" else "none"
    }

    fun cycleSelectedType() {
        val entry = surfaces.getOrNull(selectedIndex) ?: return
        // Find current template index and cycle
        val currentName = entry.equation.name
        var tIdx = EQUATION_TEMPLATES.indexOfFirst { it().name == currentName }
        tIdx = (tIdx + 1) % EQUATION_TEMPLATES.size
        val newEq = EQUATION_TEMPLATES[tIdx]()
        // Replace equation in place
        val newEntry = entry.copy(equation = newEq)
        surfaces[selectedIndex] = newEntry
        updateSurface(newEntry)
        rebuildPanel()
    }

    fun resetSelected() {
        val entry = surfaces.getOrNull(selectedIndex) ?: return
        val tIdx = EQUATION_TEMPLATES.indexOfFirst { it().name == entry.equation.name }
        if (tIdx >= 0) {
            val fresh = EQUATION_TEMPLATES[tIdx]()
            entry.equation.a = fresh.a
            entry.equation.b = fresh.b
            entry.equation.c = fresh.c
        }
        rebuildPanel()
    }

    fun update(elapsed: Double) {
        for (entry in surfaces) updateSurface(entry)
        syncInputs()
        // Update top equation display
        (document.getElementById("eqFormula") as? HTMLElement)?.textContent = selectedEquation?.displayString() ?: ""
    }

    fun toggleLock() {
        locked = !locked
        rebuildPanel()
    }

    fun adjustAB(deltaX: Double, deltaY: Double) {
        if (locked) return
        isGrabbing = true
        val eq = selectedEquation ?: return
        eq.a = (eq.a + deltaX * 5.0).coerceIn(-5.0, 5.0)
        eq.b = (eq.b + deltaY * 5.0).coerceIn(-5.0, 5.0)
    }

    fun adjustC(deltaY: Double) {
        if (locked) return
        isGrabbing = true
        val eq = selectedEquation ?: return
        eq.c = (eq.c + deltaY * 4.0).coerceIn(-10.0, 10.0)
    }

    fun endGrab() { isGrabbing = false }

    private fun updateSurface(entry: SurfaceEntry) {
        val posAttr = entry.geo.getAttribute("position") as BufferAttribute
        val colAttr = entry.geo.getAttribute("color") as BufferAttribute
        val eq = entry.equation
        val (baseR, baseG, baseB) = entry.colorBase

        var minZ = 99.0; var maxZ = -99.0
        for (iy in 0 until gridSize) {
            for (ix in 0 until gridSize) {
                val x = -rangeX + 2.0 * rangeX * ix / (gridSize - 1)
                val y = -rangeY + 2.0 * rangeY * iy / (gridSize - 1)
                val z = eq.eval(x, y, eq.a, eq.b, eq.c).coerceIn(-6.0, 6.0)
                val vi = iy * gridSize + ix
                posAttr.array[vi * 3] = x.toFloat()
                posAttr.array[vi * 3 + 1] = z.toFloat()
                posAttr.array[vi * 3 + 2] = y.toFloat()
                if (z < minZ) minZ = z; if (z > maxZ) maxZ = z
            }
        }

        val zRange = (maxZ - minZ).coerceAtLeast(0.1)
        for (iy in 0 until gridSize) {
            for (ix in 0 until gridSize) {
                val x = -rangeX + 2.0 * rangeX * ix / (gridSize - 1)
                val y = -rangeY + 2.0 * rangeY * iy / (gridSize - 1)
                val z = eq.eval(x, y, eq.a, eq.b, eq.c).coerceIn(-6.0, 6.0)
                val t = ((z - minZ) / zRange).coerceIn(0.0, 1.0)

                // Gradient magnitude for brightness boost
                val h = 0.05
                val dfdx = (eq.eval(x + h, y, eq.a, eq.b, eq.c) - eq.eval(x - h, y, eq.a, eq.b, eq.c)) / (2.0 * h)
                val dfdy = (eq.eval(x, y + h, eq.a, eq.b, eq.c) - eq.eval(x, y - h, eq.a, eq.b, eq.c)) / (2.0 * h)
                val gradMag = sqrt(dfdx * dfdx + dfdy * dfdy)
                val gradBoost = (gradMag / 4.0).coerceIn(0.0, 1.0) * 0.2

                // Rich height-based palette: blue → teal → green → yellow → red
                // Tinted toward this surface's base color
                val r: Double; val g: Double; val b2: Double
                when {
                    t < 0.25 -> { val s = t / 0.25
                        r = 0.12 + s * 0.03; g = 0.22 + s * 0.35; b2 = 0.65 - s * 0.15 }
                    t < 0.5 -> { val s = (t - 0.25) / 0.25
                        r = 0.15 + s * 0.15; g = 0.57 + s * 0.23; b2 = 0.50 - s * 0.28 }
                    t < 0.75 -> { val s = (t - 0.5) / 0.25
                        r = 0.30 + s * 0.50; g = 0.80 - s * 0.10; b2 = 0.22 - s * 0.12 }
                    else -> { val s = (t - 0.75) / 0.25
                        r = 0.80 + s * 0.15; g = 0.70 - s * 0.40; b2 = 0.10 - s * 0.05 }
                }

                // Blend with surface base color (30% tint) + gradient brightness
                val vi = iy * gridSize + ix
                colAttr.array[vi * 3] = (r * 0.7 + baseR * 0.3 + gradBoost).coerceIn(0.0, 1.0).toFloat()
                colAttr.array[vi * 3 + 1] = (g * 0.7 + baseG * 0.3 + gradBoost).coerceIn(0.0, 1.0).toFloat()
                colAttr.array[vi * 3 + 2] = (b2 * 0.7 + baseB * 0.3 + gradBoost * 0.5).coerceIn(0.0, 1.0).toFloat()
            }
        }
        posAttr.needsUpdate = true; colAttr.needsUpdate = true
        entry.geo.computeVertexNormals()
    }

    private fun buildGrid() {
        val gridMat = LineBasicMaterial(js("({color:0x555555,transparent:true,opacity:0.15})"))
        var v = -rangeX; while (v <= rangeX) {
            if (abs(v) > 0.01) {
                gridGroup.add(Line(BufferGeometry().setFromPoints(arrayOf(
                    Vector3(v, -0.02, -rangeY), Vector3(v, -0.02, rangeY))), gridMat))
                gridGroup.add(Line(BufferGeometry().setFromPoints(arrayOf(
                    Vector3(-rangeX, -0.02, v), Vector3(rangeX, -0.02, v))), gridMat))
            }; v += 1.0
        }
        // Colored axes
        gridGroup.add(Line(BufferGeometry().setFromPoints(arrayOf(
            Vector3(-rangeX, 0.0, 0.0), Vector3(rangeX, 0.0, 0.0))),
            LineBasicMaterial(js("({color:0xbb3333,transparent:true,opacity:0.4})"))))
        gridGroup.add(Line(BufferGeometry().setFromPoints(arrayOf(
            Vector3(0.0, -3.0, 0.0), Vector3(0.0, 5.0, 0.0))),
            LineBasicMaterial(js("({color:0x33bb33,transparent:true,opacity:0.4})"))))
        gridGroup.add(Line(BufferGeometry().setFromPoints(arrayOf(
            Vector3(0.0, 0.0, -rangeX), Vector3(0.0, 0.0, rangeX))),
            LineBasicMaterial(js("({color:0x3333bb,transparent:true,opacity:0.4})"))))
    }

    // ================================================================
    //  UI Panel — left sidebar, opinionated design
    // ================================================================

    private fun buildPanel() {
        if (panelEl != null) return
        panelEl = (document.createElement("div") as HTMLElement).apply {
            id = "graphPanel"
            className = "graph-panel"
        }
        document.body?.appendChild(panelEl!!)
        rebuildPanel()
    }

    private fun rebuildPanel() {
        val panel = panelEl ?: return
        val sb = StringBuilder()

        sb.append("<div class='gp-header'>equations</div>")

        for (i in surfaces.indices) {
            val s = surfaces[i]
            val sel = if (i == selectedIndex) " gp-eq-selected" else ""
            val (cr, cg, cb) = s.colorBase
            val colorHex = "#${((cr*255).toInt().toString(16).padStart(2,'0'))}${((cg*255).toInt().toString(16).padStart(2,'0'))}${((cb*255).toInt().toString(16).padStart(2,'0'))}"
            sb.append("""
                <div class='gp-eq$sel' data-idx='$i'>
                    <span class='gp-dot' style='background:$colorHex'></span>
                    <span class='gp-formula'>${s.equation.displayString()}</span>
                    ${if (surfaces.size > 1) "<button class='gp-rm' data-rm='$i'>×</button>" else ""}
                </div>
            """.trimIndent())
        }

        val lockLabel = if (locked) "unlock" else "lock"
        val lockIcon = if (locked) "🔒" else "🔓"
        sb.append("""
            <div class='gp-actions'>
                <button class='gp-btn' id='gpAdd'>+ add</button>
                <button class='gp-btn' id='gpCycle'>type ▸</button>
                <button class='gp-btn' id='gpReset'>reset</button>
            </div>
            <div class='gp-actions' style='margin-top:4px'>
                <button class='gp-btn gp-lock${if (locked) " gp-locked" else ""}' id='gpLock'>$lockIcon $lockLabel</button>
            </div>
            <div class='gp-hint'>${if (locked) "editing locked — gestures won't change coefficients" else "pinch + drag → a, b · ↑↓ → c · ←→ → pan"}</div>
        """.trimIndent())

        panel.innerHTML = sb.toString()

        // Wire events via window callbacks (avoids Kotlin name mangling issues)
        val mg = this
        val numTemplates = EQUATION_TEMPLATES.size
        val numSurfaces = surfaces.size
        js("window._graphSelectSurface = function(i) { mg.selectSurface(i); }")
        js("window._graphRemoveSurface = function(i) { mg.removeSurface(i); }")
        js("window._graphAddSurface = function() { mg.addSurface(numSurfaces % numTemplates); }")
        js("window._graphCycleType = function() { mg.cycleSelectedType(); }")
        js("window._graphReset = function() { mg.resetSelected(); }")
        js("window._graphToggleLock = function() { mg.toggleLock(); }")

        // Now wire using onclick attributes in JS
        js("""
        (function() {
            var p = document.getElementById('graphPanel');
            if (!p) return;
            var eqs = p.querySelectorAll('.gp-eq');
            for (var i = 0; i < eqs.length; i++) {
                (function(el) {
                    el.addEventListener('click', function() {
                        var idx = parseInt(el.getAttribute('data-idx'));
                        if (!isNaN(idx)) window._graphSelectSurface(idx);
                    });
                })(eqs[i]);
            }
            var rms = p.querySelectorAll('.gp-rm');
            for (var i = 0; i < rms.length; i++) {
                (function(el) {
                    el.addEventListener('click', function(e) {
                        e.stopPropagation();
                        var idx = parseInt(el.getAttribute('data-rm'));
                        if (!isNaN(idx)) window._graphRemoveSurface(idx);
                    });
                })(rms[i]);
            }
            var addBtn = document.getElementById('gpAdd');
            if (addBtn) addBtn.onclick = function() { window._graphAddSurface(); };
            var cycBtn = document.getElementById('gpCycle');
            if (cycBtn) cycBtn.onclick = function() { window._graphCycleType(); };
            var resBtn = document.getElementById('gpReset');
            if (resBtn) resBtn.onclick = function() { window._graphReset(); };
            var lockBtn = document.getElementById('gpLock');
            if (lockBtn) lockBtn.onclick = function() { window._graphToggleLock(); };
        })()
        """)
    }

    private fun syncInputs() {
        val eq = selectedEquation ?: return

        // Read FROM inputs if focused (user is typing) — write TO inputs otherwise
        fun syncField(id: String, getter: () -> Double, setter: (Double) -> Unit) {
            val el = document.getElementById(id) as? org.w3c.dom.HTMLInputElement ?: return
            val isFocused = document.asDynamic().activeElement == el
            if (isFocused) {
                // User is typing → read value and apply to equation
                val typed = el.value.toDoubleOrNull()
                if (typed != null) setter(typed)
            } else {
                // Not focused → display current value
                el.value = ((getter() * 100).toInt() / 100.0).toString()
            }
        }
        syncField("gInputA", { eq.a }, { eq.a = it.coerceIn(-5.0, 5.0) })
        syncField("gInputB", { eq.b }, { eq.b = it.coerceIn(-5.0, 5.0) })
        syncField("gInputC", { eq.c }, { eq.c = it.coerceIn(-10.0, 10.0) })

        // Update the formula text in the equation list
        for (i in surfaces.indices) {
            val formEls = panelEl?.querySelectorAll(".gp-formula")?.asDynamic()
            if (formEls != null && i < (formEls.length as Int)) {
                (formEls[i] as HTMLElement).textContent = surfaces[i].equation.displayString()
            }
        }
    }
}

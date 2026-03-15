package strings

import three.*

/**
 * Renders the string as a Desmos-style graph: thin colored curve on a
 * coordinate grid with axes, tick marks, and subtle gridlines.
 */
class StringRenderer(private val scene: Scene, private val physics: StringSystem) {

    private val mainLines = arrayOfNulls<Line>(physics.numStrings)
    private val gridGroup = Group()
    private var gridBuilt = false

    fun setup() {
        // Build grid (hidden until setVisible)
        buildGrid()
        gridGroup.visible = false
        scene.add(gridGroup)

        // Create line for each string
        for (i in 0 until physics.numStrings) {
            val points = buildPoints(i)
            val geo = BufferGeometry().setFromPoints(points)
            val mat = LineBasicMaterial(js("""({
                color: 0x2d70b3,
                linewidth: 2
            })"""))
            val line = Line(geo, mat)
            line.visible = false
            mainLines[i] = line
            scene.add(line)
        }
    }

    fun update(elapsed: Double) {
        for (i in 0 until physics.numStrings) {
            val line = mainLines[i] ?: continue
            val points = buildPoints(i)
            val oldGeo = line.geometry
            val newGeo = BufferGeometry().setFromPoints(points)
            js("line.geometry = newGeo")
            oldGeo.dispose()

            // Color shifts slightly with energy
            val energy = physics.strings[i].getEnergy()
            val activity = (energy * 40.0).coerceAtMost(0.3)
            val mat = line.material
            // Desmos blue base (0x2d70b3 = r:0.176, g:0.439, b:0.702)
            mat.color.r = (0.176 + activity).coerceAtMost(0.5)
            mat.color.g = (0.439 + activity * 0.3).coerceAtMost(0.7)
            mat.color.b = (0.702 + activity * 0.2).coerceAtMost(1.0)
        }
    }

    fun setVisible(visible: Boolean) {
        gridGroup.visible = visible
        for (i in 0 until physics.numStrings) {
            mainLines[i]?.visible = visible
        }
    }

    private fun buildPoints(stringIndex: Int): Array<Vector3> {
        val s = physics.strings[stringIndex]
        return Array(s.numPoints) { i ->
            Vector3(s.points[i].x, s.points[i].y, s.points[i].z)
        }
    }

    private fun buildGrid() {
        if (gridBuilt) return
        gridBuilt = true

        val gridZ = -0.01 // slightly behind the curve

        // Grid range: x from -5 to 5, y from -3 to 3
        val xMin = -5.0; val xMax = 5.0
        val yMin = -3.0; val yMax = 3.0

        // Subtle gridlines
        val gridMat = LineBasicMaterial(js("""({
            color: 0x444444,
            transparent: true,
            opacity: 0.3
        })"""))

        // Vertical gridlines
        var x = xMin
        while (x <= xMax) {
            if (x != 0.0) { // skip origin, axis will draw there
                val geo = BufferGeometry().setFromPoints(arrayOf(
                    Vector3(x, yMin, gridZ), Vector3(x, yMax, gridZ)
                ))
                val line = Line(geo, gridMat)
                gridGroup.add(line)
            }
            x += 1.0
        }

        // Horizontal gridlines
        var y = yMin
        while (y <= yMax) {
            if (y != 0.0) {
                val geo = BufferGeometry().setFromPoints(arrayOf(
                    Vector3(xMin, y, gridZ), Vector3(xMax, y, gridZ)
                ))
                val line = Line(geo, gridMat)
                gridGroup.add(line)
            }
            y += 1.0
        }

        // Axes (thicker look via separate material)
        val axisMat = LineBasicMaterial(js("""({
            color: 0x888888,
            transparent: true,
            opacity: 0.7
        })"""))

        // X axis
        val xAxisGeo = BufferGeometry().setFromPoints(arrayOf(
            Vector3(xMin, 0.0, gridZ), Vector3(xMax, 0.0, gridZ)
        ))
        gridGroup.add(Line(xAxisGeo, axisMat))

        // Y axis
        val yAxisGeo = BufferGeometry().setFromPoints(arrayOf(
            Vector3(0.0, yMin, gridZ), Vector3(0.0, yMax, gridZ)
        ))
        gridGroup.add(Line(yAxisGeo, axisMat))

        // Tick marks on X axis
        x = xMin
        while (x <= xMax) {
            if (x != 0.0) {
                val tickGeo = BufferGeometry().setFromPoints(arrayOf(
                    Vector3(x, -0.08, gridZ), Vector3(x, 0.08, gridZ)
                ))
                gridGroup.add(Line(tickGeo, axisMat))
            }
            x += 1.0
        }

        // Tick marks on Y axis
        y = yMin
        while (y <= yMax) {
            if (y != 0.0) {
                val tickGeo = BufferGeometry().setFromPoints(arrayOf(
                    Vector3(-0.08, y, gridZ), Vector3(0.08, y, gridZ)
                ))
                gridGroup.add(Line(tickGeo, axisMat))
            }
            y += 1.0
        }
    }
}

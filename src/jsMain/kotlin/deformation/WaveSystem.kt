package deformation

/**
 * Global modal oscillation system that simulates coherent wave propagation
 * across the surface of a water balloon. 5 modes approximate the lowest-order
 * spherical harmonics: 3 axial slosh modes, 1 breathing mode, 1 oblate-prolate mode.
 */
class WaveSystem {
    // 5 modes: Y-slosh, X-slosh, Z-slosh, radial breathing, oblate-prolate
    private val amp = DoubleArray(5)
    private val vel = DoubleArray(5)
    private val freq = doubleArrayOf(3.5, 3.5, 3.5, 5.5, 3.8)
    private val damp = doubleArrayOf(0.7, 0.7, 0.7, 1.0, 0.8)

    fun excite(dirX: Double, dirY: Double, dirZ: Double, strength: Double) {
        vel[0] += dirY * strength
        vel[1] += dirX * strength
        vel[2] += dirZ * strength
        vel[3] += strength * 0.3
        val yBias = dirY * dirY - 0.5 * (dirX * dirX + dirZ * dirZ)
        vel[4] += yBias * strength * 0.5
    }

    fun update(dt: Double) {
        for (i in 0 until 5) {
            val force = -freq[i] * freq[i] * amp[i] - damp[i] * vel[i]
            vel[i] += force * dt
            amp[i] += vel[i] * dt
            amp[i] = amp[i].coerceIn(-1.5, 1.5)
            vel[i] = vel[i].coerceIn(-8.0, 8.0)
        }
    }

    /** Returns radial offset for a vertex at unit normal (nx, ny, nz) on the sphere */
    fun getRadialOffset(nx: Double, ny: Double, nz: Double): Double {
        return amp[0] * ny +
               amp[1] * nx +
               amp[2] * nz +
               amp[3] +
               amp[4] * (3.0 * ny * ny - 1.0) * 0.25
    }

    val totalEnergy: Double
        get() {
            var e = 0.0
            for (i in 0 until 5) {
                e += vel[i] * vel[i] + freq[i] * freq[i] * amp[i] * amp[i]
            }
            return e
        }

    fun reset() {
        for (i in 0 until 5) { amp[i] = 0.0; vel[i] = 0.0 }
    }
}

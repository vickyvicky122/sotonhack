package deformation

import org.khronos.webgl.get
import org.khronos.webgl.set
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

class DeformationController(private val spring: SpringPhysics) {

    private val vertexCount: Int = spring.originalPositions.length / 3

    // Foam rubber: generous deformation, feels like squeezing
    private val deformRate = 3.0
    private val twistRate = 2.5
    private val pulseStrength = 0.5
    private val squeezeRate = 2.5

    private var activeKeys = 0

    fun applyKeyboardDeformation(pressedKeys: Set<String>, dt: Double) {
        activeKeys = 0
        for (i in 0 until vertexCount) {
            val ox = spring.getOriginalX(i)
            val oy = spring.getOriginalY(i)
            val oz = spring.getOriginalZ(i)
            val origLen = sqrt(ox * ox + oy * oy + oz * oz)

            // W/S: stretch/compress vertically
            if ("w" in pressedKeys || "W" in pressedKeys) {
                activeKeys++
                val sign = if (oy >= 0) 1.0 else -1.0
                spring.targetOffsets[i * 3 + 1] = (spring.targetOffsets[i * 3 + 1] + (deformRate * sign * dt).toFloat())
            }
            if ("s" in pressedKeys || "S" in pressedKeys) {
                activeKeys++
                val sign = if (oy >= 0) 1.0 else -1.0
                spring.targetOffsets[i * 3 + 1] = (spring.targetOffsets[i * 3 + 1] - (deformRate * sign * dt).toFloat())
            }

            // A/D: widen/narrow horizontally
            if ("d" in pressedKeys || "D" in pressedKeys) {
                activeKeys++
                val sign = if (ox >= 0) 1.0 else -1.0
                spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] + (deformRate * sign * dt).toFloat())
            }
            if ("a" in pressedKeys || "A" in pressedKeys) {
                activeKeys++
                val sign = if (ox >= 0) 1.0 else -1.0
                spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] - (deformRate * sign * dt).toFloat())
            }

            // Q/E: twist
            if ("q" in pressedKeys || "Q" in pressedKeys) {
                activeKeys++
                val angle = -twistRate * oy * dt * 0.3
                val cosA = cos(angle)
                val sinA = sin(angle)
                spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] + ((ox * cosA - oz * sinA) - ox).toFloat())
                spring.targetOffsets[i * 3 + 2] = (spring.targetOffsets[i * 3 + 2] + ((ox * sinA + oz * cosA) - oz).toFloat())
            }
            if ("e" in pressedKeys || "E" in pressedKeys) {
                activeKeys++
                val angle = twistRate * oy * dt * 0.3
                val cosA = cos(angle)
                val sinA = sin(angle)
                spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] + ((ox * cosA - oz * sinA) - ox).toFloat())
                spring.targetOffsets[i * 3 + 2] = (spring.targetOffsets[i * 3 + 2] + ((ox * sinA + oz * cosA) - oz).toFloat())
            }

            // F: whole-ball squeeze (compress uniformly inward — like gripping the ball)
            if ("f" in pressedKeys || "F" in pressedKeys) {
                activeKeys++
                if (origLen > 0.001) {
                    spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] - (squeezeRate * ox / origLen * dt).toFloat())
                    spring.targetOffsets[i * 3 + 1] = (spring.targetOffsets[i * 3 + 1] - (squeezeRate * oy / origLen * dt).toFloat())
                    spring.targetOffsets[i * 3 + 2] = (spring.targetOffsets[i * 3 + 2] - (squeezeRate * oz / origLen * dt).toFloat())
                }
            }
        }
    }

    fun isDeforming(): Boolean = activeKeys > 0

    /** Grip squeeze — continuous inward compression proportional to grip strength (0-1) */
    fun applyGrip(gripAmount: Double, dt: Double) {
        if (gripAmount < 0.05) return  // dead zone to avoid micro-jitter
        val strength = squeezeRate * gripAmount * 1.2
        for (i in 0 until vertexCount) {
            val ox = spring.getOriginalX(i)
            val oy = spring.getOriginalY(i)
            val oz = spring.getOriginalZ(i)
            val origLen = sqrt(ox * ox + oy * oy + oz * oz)
            if (origLen > 0.001) {
                spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] - (strength * ox / origLen * dt).toFloat())
                spring.targetOffsets[i * 3 + 1] = (spring.targetOffsets[i * 3 + 1] - (strength * oy / origLen * dt).toFloat())
                spring.targetOffsets[i * 3 + 2] = (spring.targetOffsets[i * 3 + 2] - (strength * oz / origLen * dt).toFloat())
            }
        }
    }

    fun applyPulse() {
        for (i in 0 until vertexCount) {
            val ox = spring.getOriginalX(i)
            val oy = spring.getOriginalY(i)
            val oz = spring.getOriginalZ(i)
            val len = sqrt(ox * ox + oy * oy + oz * oz)
            if (len > 0.001) {
                spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] + (pulseStrength * ox / len).toFloat())
                spring.targetOffsets[i * 3 + 1] = (spring.targetOffsets[i * 3 + 1] + (pulseStrength * oy / len).toFloat())
                spring.targetOffsets[i * 3 + 2] = (spring.targetOffsets[i * 3 + 2] + (pulseStrength * oz / len).toFloat())
            }
        }
    }

    /** Poke — wide radius like pressing a thumb into foam */
    fun applyPoke(intersectPoint: three.Vector3, radius: Double = 1.2, strength: Double = -0.5) {
        for (i in 0 until vertexCount) {
            val ox = spring.getOriginalX(i) + spring.targetOffsets[i * 3].toDouble()
            val oy = spring.getOriginalY(i) + spring.targetOffsets[i * 3 + 1].toDouble()
            val oz = spring.getOriginalZ(i) + spring.targetOffsets[i * 3 + 2].toDouble()

            val dx = ox - intersectPoint.x
            val dy = oy - intersectPoint.y
            val dz = oz - intersectPoint.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)

            if (dist < radius) {
                // Wide, smooth Gaussian — like a thumb impression
                val falloff = exp(-(dist * dist) / (radius * radius * 0.4))
                val origLen = sqrt(
                    spring.getOriginalX(i) * spring.getOriginalX(i) +
                    spring.getOriginalY(i) * spring.getOriginalY(i) +
                    spring.getOriginalZ(i) * spring.getOriginalZ(i)
                )
                if (origLen > 0.001) {
                    spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] +
                        (strength * falloff * spring.getOriginalX(i) / origLen).toFloat())
                    spring.targetOffsets[i * 3 + 1] = (spring.targetOffsets[i * 3 + 1] +
                        (strength * falloff * spring.getOriginalY(i) / origLen).toFloat())
                    spring.targetOffsets[i * 3 + 2] = (spring.targetOffsets[i * 3 + 2] +
                        (strength * falloff * spring.getOriginalZ(i) / origLen).toFloat())
                }
            }
        }
    }

    /** Explode — massive radial burst with random scatter, vertices fly apart */
    fun applyExplode() {
        spring.maxOffset = 3.0
        spring.maxVelocity = 15.0
        spring.volumePreservation = 0.1  // let it fly apart
        val strength = 2.5
        for (i in 0 until vertexCount) {
            val ox = spring.getOriginalX(i)
            val oy = spring.getOriginalY(i)
            val oz = spring.getOriginalZ(i)
            val len = sqrt(ox * ox + oy * oy + oz * oz)
            if (len > 0.001) {
                val rx = (kotlin.random.Random.nextDouble() - 0.5) * 1.2
                val ry = (kotlin.random.Random.nextDouble() - 0.5) * 1.2
                val rz = (kotlin.random.Random.nextDouble() - 0.5) * 1.2
                spring.targetOffsets[i * 3] = (strength * ox / len + rx).toFloat()
                spring.targetOffsets[i * 3 + 1] = (strength * oy / len + ry).toFloat()
                spring.targetOffsets[i * 3 + 2] = (strength * oz / len + rz).toFloat()
                // Kick velocities for instant visual impact
                spring.velocities[i * 3] = (strength * 2.5 * ox / len + rx * 3.0).toFloat()
                spring.velocities[i * 3 + 1] = (strength * 2.5 * oy / len + ry * 3.0).toFloat()
                spring.velocities[i * 3 + 2] = (strength * 2.5 * oz / len + rz * 3.0).toFloat()
            }
        }
    }

    /** Scramble — chaotic random displacement, blob becomes a messy blob */
    fun applyScramble() {
        spring.maxOffset = 2.5
        spring.maxVelocity = 12.0
        spring.volumePreservation = 0.1  // let it go wild
        for (i in 0 until vertexCount) {
            val rx = (kotlin.random.Random.nextDouble() - 0.5) * 3.0
            val ry = (kotlin.random.Random.nextDouble() - 0.5) * 3.0
            val rz = (kotlin.random.Random.nextDouble() - 0.5) * 3.0
            spring.targetOffsets[i * 3] = rx.toFloat()
            spring.targetOffsets[i * 3 + 1] = ry.toFloat()
            spring.targetOffsets[i * 3 + 2] = rz.toFloat()
            spring.velocities[i * 3] = (rx * 4.0).toFloat()
            spring.velocities[i * 3 + 1] = (ry * 4.0).toFloat()
            spring.velocities[i * 3 + 2] = (rz * 4.0).toFloat()
        }
    }

    /** Punch — hard directional impact, dents one side and blows out the other */
    fun applyPunch(dirX: Double, dirY: Double) {
        spring.maxOffset = 2.5
        spring.maxVelocity = 15.0
        // Punch direction (add forward component into screen)
        val dLen = sqrt(dirX * dirX + dirY * dirY + 0.25)
        val ndx = dirX / dLen
        val ndy = dirY / dLen
        val ndz = -0.5 / dLen
        val strength = 2.2

        for (i in 0 until vertexCount) {
            val ox = spring.getOriginalX(i)
            val oy = spring.getOriginalY(i)
            val oz = spring.getOriginalZ(i)
            val origLen = sqrt(ox * ox + oy * oy + oz * oz)
            if (origLen < 0.001) continue

            // How much this vertex faces the punch direction
            val dot = (ox * ndx + oy * ndy + oz * ndz) / origLen

            if (dot > 0.0) {
                // Front-facing: push inward hard (dent)
                val push = strength * dot * dot
                spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] - (push * ox / origLen).toFloat())
                spring.targetOffsets[i * 3 + 1] = (spring.targetOffsets[i * 3 + 1] - (push * oy / origLen).toFloat())
                spring.targetOffsets[i * 3 + 2] = (spring.targetOffsets[i * 3 + 2] - (push * oz / origLen).toFloat())
                // Velocity kick
                spring.velocities[i * 3] = (spring.velocities[i * 3] + (ndx * push * 3.0).toFloat())
                spring.velocities[i * 3 + 1] = (spring.velocities[i * 3 + 1] + (ndy * push * 3.0).toFloat())
                spring.velocities[i * 3 + 2] = (spring.velocities[i * 3 + 2] + (ndz * push * 3.0).toFloat())
            } else {
                // Back-facing: push outward (exit bulge)
                val bulge = strength * 0.4 * dot * dot
                spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] + (bulge * ox / origLen).toFloat())
                spring.targetOffsets[i * 3 + 1] = (spring.targetOffsets[i * 3 + 1] + (bulge * oy / origLen).toFloat())
                spring.targetOffsets[i * 3 + 2] = (spring.targetOffsets[i * 3 + 2] + (bulge * oz / origLen).toFloat())
            }
        }
    }

    /** Pinch — localized depression between thumb and index finger, like pinching clay */
    fun applyPinch(point: three.Vector3, pinchAmount: Double, radius: Double = 0.6) {
        val strength = -0.8 * pinchAmount  // deeper pinch = more deformation
        for (i in 0 until vertexCount) {
            val ox = spring.getOriginalX(i) + spring.targetOffsets[i * 3].toDouble()
            val oy = spring.getOriginalY(i) + spring.targetOffsets[i * 3 + 1].toDouble()
            val oz = spring.getOriginalZ(i) + spring.targetOffsets[i * 3 + 2].toDouble()

            val dx = ox - point.x
            val dy = oy - point.y
            val dz = oz - point.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)

            if (dist < radius) {
                // Tight Gaussian — pinch creates a narrow depression
                val falloff = exp(-(dist * dist) / (radius * radius * 0.2))
                val origLen = sqrt(
                    spring.getOriginalX(i) * spring.getOriginalX(i) +
                    spring.getOriginalY(i) * spring.getOriginalY(i) +
                    spring.getOriginalZ(i) * spring.getOriginalZ(i)
                )
                if (origLen > 0.001) {
                    spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] +
                        (strength * falloff * spring.getOriginalX(i) / origLen).toFloat())
                    spring.targetOffsets[i * 3 + 1] = (spring.targetOffsets[i * 3 + 1] +
                        (strength * falloff * spring.getOriginalY(i) / origLen).toFloat())
                    spring.targetOffsets[i * 3 + 2] = (spring.targetOffsets[i * 3 + 2] +
                        (strength * falloff * spring.getOriginalZ(i) / origLen).toFloat())
                }
            }
        }
    }

    /** Pull — stretches the ball outward from a point, like pulling taffy */
    fun applyPull(point: three.Vector3, pullDirX: Double, pullDirY: Double, strength: Double = 1.2) {
        val radius = 1.5
        // Normalize pull direction with a forward Z component
        val pLen = sqrt(pullDirX * pullDirX + pullDirY * pullDirY + 0.01)
        val pdx = pullDirX / pLen
        val pdy = pullDirY / pLen

        for (i in 0 until vertexCount) {
            val ox = spring.getOriginalX(i) + spring.targetOffsets[i * 3].toDouble()
            val oy = spring.getOriginalY(i) + spring.targetOffsets[i * 3 + 1].toDouble()
            val oz = spring.getOriginalZ(i) + spring.targetOffsets[i * 3 + 2].toDouble()

            val dx = ox - point.x
            val dy = oy - point.y
            val dz = oz - point.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)

            if (dist < radius) {
                val falloff = exp(-(dist * dist) / (radius * radius * 0.5))
                // Pull vertices in the direction of hand movement
                spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] + (strength * falloff * pdx).toFloat())
                spring.targetOffsets[i * 3 + 1] = (spring.targetOffsets[i * 3 + 1] + (strength * falloff * pdy).toFloat())
            }
        }
    }

    /** Slap — fast broad contact impact with proper bounce */
    fun applySlap(dirX: Double, dirY: Double) {
        spring.maxOffset = 3.5
        spring.maxVelocity = 20.0
        val dLen = sqrt(dirX * dirX + dirY * dirY + 0.25)
        val ndx = dirX / dLen
        val ndy = dirY / dLen
        val ndz = -0.5 / dLen
        val impactStrength = 3.5

        for (i in 0 until vertexCount) {
            val ox = spring.getOriginalX(i)
            val oy = spring.getOriginalY(i)
            val oz = spring.getOriginalZ(i)
            val origLen = sqrt(ox * ox + oy * oy + oz * oz)
            if (origLen < 0.001) continue

            // How much this vertex faces the slap direction
            val dot = (ox * ndx + oy * ndy + oz * ndz) / origLen

            if (dot > 0.0) {
                // Impact side — dent inward hard
                val push = impactStrength * dot * dot
                // Push inward (toward center)
                spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] - (push * ox / origLen).toFloat())
                spring.targetOffsets[i * 3 + 1] = (spring.targetOffsets[i * 3 + 1] - (push * oy / origLen).toFloat())
                spring.targetOffsets[i * 3 + 2] = (spring.targetOffsets[i * 3 + 2] - (push * oz / origLen).toFloat())
                // Strong velocity in slap direction for bounce travel
                val velKick = impactStrength * dot * 5.0
                spring.velocities[i * 3] = (spring.velocities[i * 3] + (ndx * velKick).toFloat())
                spring.velocities[i * 3 + 1] = (spring.velocities[i * 3 + 1] + (ndy * velKick).toFloat())
                spring.velocities[i * 3 + 2] = (spring.velocities[i * 3 + 2] + (ndz * velKick).toFloat())
            } else {
                // Opposite side — bulge outward (ball squishes through)
                val bulge = impactStrength * 0.6 * dot * dot
                spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] + (bulge * ox / origLen).toFloat())
                spring.targetOffsets[i * 3 + 1] = (spring.targetOffsets[i * 3 + 1] + (bulge * oy / origLen).toFloat())
                spring.targetOffsets[i * 3 + 2] = (spring.targetOffsets[i * 3 + 2] + (bulge * oz / origLen).toFloat())
                // Outward velocity for bouncy rebound
                val outVel = impactStrength * 0.4 * dot * dot * 4.0
                spring.velocities[i * 3] = (spring.velocities[i * 3] + (ox / origLen * outVel).toFloat())
                spring.velocities[i * 3 + 1] = (spring.velocities[i * 3 + 1] + (oy / origLen * outVel).toFloat())
                spring.velocities[i * 3 + 2] = (spring.velocities[i * 3 + 2] + (oz / origLen * outVel).toFloat())
            }

            // Wobble perpendicular to impact for jelly effect
            val wobble = (kotlin.random.Random.nextDouble() - 0.5) * impactStrength * 0.8
            spring.velocities[i * 3] = (spring.velocities[i * 3] + (ndy * wobble).toFloat())
            spring.velocities[i * 3 + 1] = (spring.velocities[i * 3 + 1] - (ndx * wobble).toFloat())
        }
    }

    /** Knead — oscillating squeeze that alternates compression direction, like kneading dough */
    fun applyKnead(phase: Double, intensity: Double, dt: Double) {
        val kneadStrength = 1.5 * intensity
        // Alternate between X-axis and Y-axis compression
        val xFactor = cos(phase) * kneadStrength
        val yFactor = sin(phase) * kneadStrength

        for (i in 0 until vertexCount) {
            val ox = spring.getOriginalX(i)
            val oy = spring.getOriginalY(i)
            val oz = spring.getOriginalZ(i)
            val origLen = sqrt(ox * ox + oy * oy + oz * oz)
            if (origLen < 0.001) continue

            // Squeeze on one axis while expanding on the other (volume-preserving feel)
            val xDisp = -xFactor * (ox / origLen) * dt
            val yDisp = yFactor * (oy / origLen) * dt
            spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] + xDisp.toFloat())
            spring.targetOffsets[i * 3 + 1] = (spring.targetOffsets[i * 3 + 1] + yDisp.toFloat())
        }
    }

    /** Resize — uniform scale from two-hand spread/pinch gesture */
    fun applyResize(scaleDelta: Double, dt: Double) {
        val resizeRate = 4.0
        val amount = scaleDelta * resizeRate * dt
        for (i in 0 until vertexCount) {
            val ox = spring.getOriginalX(i)
            val oy = spring.getOriginalY(i)
            val oz = spring.getOriginalZ(i)
            val origLen = sqrt(ox * ox + oy * oy + oz * oz)
            if (origLen > 0.001) {
                spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] + (amount * ox / origLen).toFloat())
                spring.targetOffsets[i * 3 + 1] = (spring.targetOffsets[i * 3 + 1] + (amount * oy / origLen).toFloat())
                spring.targetOffsets[i * 3 + 2] = (spring.targetOffsets[i * 3 + 2] + (amount * oz / origLen).toFloat())
            }
        }
    }

    /** Multi-finger poke — all visible fingertips push into the ball simultaneously */
    fun applyMultiFingerPoke(points: List<three.Vector3>, radius: Double = 0.6, strength: Double = -0.25) {
        for (point in points) {
            applyPoke(point, radius, strength)
        }
    }

    /** Slice — karate chop splits the ball into two visual halves along a cutting plane.
     *  Vertices on each side get pushed apart, then spring back together. */
    fun applySlice(planeNormalX: Double, planeNormalY: Double, planeNormalZ: Double, planeOffset: Double = 0.0) {
        spring.maxOffset = 2.5
        spring.maxVelocity = 12.0
        spring.volumePreservation = 0.3  // let the halves separate visually
        val strength = 1.8

        // Normalize the plane normal
        val nLen = sqrt(planeNormalX * planeNormalX + planeNormalY * planeNormalY + planeNormalZ * planeNormalZ)
        val nnx = if (nLen > 0.001) planeNormalX / nLen else 1.0
        val nny = if (nLen > 0.001) planeNormalY / nLen else 0.0
        val nnz = if (nLen > 0.001) planeNormalZ / nLen else 0.0

        for (i in 0 until vertexCount) {
            val ox = spring.getOriginalX(i)
            val oy = spring.getOriginalY(i)
            val oz = spring.getOriginalZ(i)

            // Signed distance from vertex to the cutting plane
            val dist = ox * nnx + oy * nny + oz * nnz - planeOffset

            // Push each side away from the plane — farther vertices get pushed less (tapered)
            val side = if (dist >= 0.0) 1.0 else -1.0
            // Falloff: vertices near the plane get the most displacement, edges get less
            val absDist = kotlin.math.abs(dist)
            val falloff = exp(-absDist * absDist * 0.3) // Gaussian-like: strong near plane, fades at edges
            val push = strength * side * falloff

            spring.targetOffsets[i * 3] = (spring.targetOffsets[i * 3] + (push * nnx).toFloat())
            spring.targetOffsets[i * 3 + 1] = (spring.targetOffsets[i * 3 + 1] + (push * nny).toFloat())
            spring.targetOffsets[i * 3 + 2] = (spring.targetOffsets[i * 3 + 2] + (push * nnz).toFloat())

            // Velocity kick for immediate visual pop
            spring.velocities[i * 3] = (spring.velocities[i * 3] + (push * nnx * 2.5).toFloat())
            spring.velocities[i * 3 + 1] = (spring.velocities[i * 3 + 1] + (push * nny * 2.5).toFloat())
            spring.velocities[i * 3 + 2] = (spring.velocities[i * 3 + 2] + (push * nnz * 2.5).toFloat())
        }
    }

    fun reset() {
        spring.reset()
        spring.maxOffset = 2.5
        spring.maxVelocity = 10.0
        spring.volumePreservation = 0.7
    }
}

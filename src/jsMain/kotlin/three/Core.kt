@file:JsModule("three")
@file:JsNonModule

package three

import org.khronos.webgl.Float32Array
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement

external class Scene : Object3D {
    var background: dynamic
    var environment: dynamic
}

open external class Object3D {
    var visible: Boolean
    val position: Vector3
    val rotation: Euler
    val scale: Vector3
    fun add(obj: Object3D)
    fun remove(obj: Object3D)
}

external class Group : Object3D

external class Vector3(x: Double = definedExternally, y: Double = definedExternally, z: Double = definedExternally) {
    var x: Double
    var y: Double
    var z: Double
    fun set(x: Double, y: Double, z: Double): Vector3
    fun clone(): Vector3
    fun copy(v: Vector3): Vector3
    fun add(v: Vector3): Vector3
    fun sub(v: Vector3): Vector3
    fun normalize(): Vector3
    fun multiplyScalar(s: Double): Vector3
    fun length(): Double
    fun distanceTo(v: Vector3): Double
    fun unproject(camera: PerspectiveCamera): Vector3
}

external class Vector2(x: Double = definedExternally, y: Double = definedExternally) {
    var x: Double
    var y: Double
    fun set(x: Double, y: Double): Vector2
}

external class Euler {
    var x: Double
    var y: Double
    var z: Double
}

external class Color(color: dynamic = definedExternally) {
    var r: Double
    var g: Double
    var b: Double
    fun set(color: dynamic): Color
    fun setHSL(h: Double, s: Double, l: Double): Color
    fun lerpColors(color1: Color, color2: Color, alpha: Double): Color
}

external class WebGLRenderer(params: dynamic = definedExternally) {
    val domElement: HTMLCanvasElement
    var toneMapping: Int
    var toneMappingExposure: Double
    fun setSize(width: Int, height: Int)
    fun setPixelRatio(ratio: Double)
    fun render(scene: Scene, camera: PerspectiveCamera)
}

external class Clock {
    fun getDelta(): Double
    fun getElapsedTime(): Double
}

external class Raycaster {
    fun setFromCamera(coords: Vector2, camera: PerspectiveCamera)
    fun intersectObject(obj: Object3D, recursive: Boolean = definedExternally): Array<dynamic>
}

open external class BufferGeometry {
    fun getAttribute(name: String): BufferAttribute
    fun setAttribute(name: String, attribute: BufferAttribute): BufferGeometry
    fun setFromPoints(points: Array<Vector3>): BufferGeometry
    fun computeVertexNormals()
    fun dispose()
    val attributes: dynamic
}

external class TubeGeometry(
    path: dynamic,
    tubularSegments: Int = definedExternally,
    radius: Double = definedExternally,
    radialSegments: Int = definedExternally,
    closed: Boolean = definedExternally
) : BufferGeometry

external class CatmullRomCurve3(points: Array<Vector3>) {
    var curveType: String
    var tension: Double
}

external class Line(
    geometry: BufferGeometry = definedExternally,
    material: dynamic = definedExternally
) : Object3D {
    val geometry: BufferGeometry
    val material: dynamic
}

external class LineBasicMaterial(params: dynamic = definedExternally) {
    var color: Color
    var linewidth: Double
    var transparent: Boolean
    var opacity: Double
}

open external class Texture {
    var wrapS: Int
    var wrapT: Int
    val repeat: Vector2
    var needsUpdate: Boolean
}

external class CanvasTexture(canvas: HTMLCanvasElement) : Texture

external class SphereGeometry(
    radius: Double = definedExternally,
    widthSegments: Int = definedExternally,
    heightSegments: Int = definedExternally
) : BufferGeometry

external class PMREMGenerator(renderer: WebGLRenderer) {
    fun fromScene(scene: Scene, sigma: Double = definedExternally): dynamic
    fun dispose()
}

external val RepeatWrapping: Int

external class BufferAttribute(array: Float32Array, itemSize: Int) {
    val array: Float32Array
    val count: Int
    val itemSize: Int
    var needsUpdate: Boolean
    fun getX(index: Int): Double
    fun getY(index: Int): Double
    fun getZ(index: Int): Double
    fun setXYZ(index: Int, x: Double, y: Double, z: Double): BufferAttribute
}

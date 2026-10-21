package dnz.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Matrix33
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import java.awt.image.BufferedImage
import java.io.File
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A skin ready to draw: the texture and whether its arms are slim (3 pixels wide). */
class LoadedSkin(val image: Image, val slim: Boolean, val legacy: Boolean)

/**
 * The player's skin for the 3D character: the signed-in account's skin, or Minecraft's default one (read from
 * a game jar the launcher already downloaded).
 */
suspend fun loadSkin(uuid: String?): LoadedSkin? = withContext(Dispatchers.IO) {
    val (image, slim) = uuid?.let { Accounts.skin(it) } ?: defaultSkin()?.let { it to false } ?: return@withContext null
    LoadedSkin(Image.makeFromBitmap(image.toComposeImageBitmap().asSkiaBitmap()), slim, image.height == 32)
}

private fun defaultSkin(): BufferedImage? = runCatching {
    val versions = File(GameLauncher.root, "versions").listFiles().orEmpty().sortedByDescending { it.lastModified() }
    for (dir in versions) {
        val jar = File(dir, dir.name + ".jar").takeIf { it.isFile } ?: continue
        ZipFile(jar).use { zip ->
            val entry = zip.getEntry("assets/minecraft/textures/entity/player/wide/steve.png") ?: return@use null
            zip.getInputStream(entry).use { ImageIO.read(it) }
        }?.let { return@runCatching it }
    }
    null
}.getOrNull()

// ------------------------------------------------------------------ the model

private class Vec(val x: Float, val y: Float, val z: Float)

private class Part(
    val w: Float, val h: Float, val d: Float,
    val x: Float, val y: Float, val z: Float,
    val u: Int, val v: Int,
    val pivot: Vec,
    val grow: Float = 0f,
    val tw: Int = w.toInt(), val th: Int = h.toInt(), val td: Int = d.toInt(),
)

private class Face(val p0: Vec, val p1: Vec, val p3: Vec, val u: Int, val v: Int, val tw: Int, val th: Int, val shade: Float, val depth: Float)

private fun rotate(p: Vec, pivot: Vec, pitch: Float, roll: Float, yaw: Float): Vec {
    var x = p.x - pivot.x; var y = p.y - pivot.y; var z = p.z - pivot.z
    // around X (pitch), then Z (roll), then Y (yaw)
    val cy = cos(pitch); val sy = sin(pitch)
    val y1 = y * cy - z * sy; val z1 = y * sy + z * cy; y = y1; z = z1
    val cr = cos(roll); val sr = sin(roll)
    val x2 = x * cr - y * sr; val y2 = x * sr + y * cr; x = x2; y = y2
    val cw = cos(yaw); val sw = sin(yaw)
    val x3 = x * cw + z * sw; val z3 = -x * sw + z * cw; x = x3; z = z3
    return Vec(x + pivot.x, y + pivot.y, z + pivot.z)
}

private fun faces(part: Part, pose: (Vec) -> Vec, out: MutableList<Face>) {
    val g = part.grow
    val x0 = part.x - g; val x1 = part.x + part.w + g
    val y0 = part.y - g; val y1 = part.y + part.h + g
    val z0 = part.z - g; val z1 = part.z + part.d + g
    val u = part.u; val v = part.v; val w = part.tw; val h = part.th; val d = part.td
    fun add(a: Vec, b: Vec, c: Vec, tu: Int, tv: Int, tw: Int, th: Int, shade: Float) {
        val p0 = pose(a); val p1 = pose(b); val p3 = pose(c)
        // Outward normal = (p3 - p0) x (p1 - p0); only faces turned to the viewer (+z) are drawn
        val ax = p3.x - p0.x; val ay = p3.y - p0.y; val az = p3.z - p0.z
        val bx = p1.x - p0.x; val by = p1.y - p0.y; val bz = p1.z - p0.z
        if (ax * by - ay * bx <= 0f) return
        val depth = (p0.z + p1.z + p3.z + (p1.z + p3.z - p0.z)) / 4f
        out += Face(p0, p1, p3, tu, tv, tw, th, shade, depth)
    }
    add(Vec(x0, y1, z1), Vec(x1, y1, z1), Vec(x0, y0, z1), u + d, v + d, w, h, 1.0f) // front
    add(Vec(x0, y1, z0), Vec(x0, y1, z1), Vec(x0, y0, z0), u, v + d, d, h, 0.78f) // right side (player's right)
    add(Vec(x1, y1, z1), Vec(x1, y1, z0), Vec(x1, y0, z1), u + d + w, v + d, d, h, 0.78f) // left side
    add(Vec(x1, y1, z0), Vec(x0, y1, z0), Vec(x1, y0, z0), u + 2 * d + w, v + d, w, h, 0.7f) // back
    add(Vec(x0, y1, z0), Vec(x1, y1, z0), Vec(x0, y1, z1), u + d, v, w, d, 1.1f) // top
    add(Vec(x0, y0, z1), Vec(x1, y0, z1), Vec(x0, y0, z0), u + d + w, v, w, d, 0.55f) // bottom
}

/** The player's skin as a 3D character that breathes, swings its arms a little and looks at the mouse. */
@Composable
fun PlayerModel(skin: LoadedSkin, animate: Boolean, modifier: Modifier = Modifier) {
    var time by remember { mutableStateOf(0f) }
    var mouse by remember { mutableStateOf<Offset?>(null) }
    var spin by remember { mutableStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var waveAt by remember { mutableStateOf(-10f) }
    // where the head looks now: [yaw, pitch] (eased towards the mouse while drawing)
    val look = remember { FloatArray(2) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        val start = withFrameNanos { it }
        var last = start
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                time = (now - start) / 1e9f
                // turn back slowly to the resting angle after a drag
                if (!dragging) spin -= spin * min(1f, dt * 2.5f)
            }
        }
    }
    val paint = remember { Paint().apply { isAntiAlias = true } }
    val sampling = remember { FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE) }
    Canvas(
        modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                var pressAt: Offset? = null
                var moved = 0f
                while (true) {
                    val e = awaitPointerEvent()
                    val c = e.changes.firstOrNull() ?: continue
                    when (e.type) {
                        PointerEventType.Press -> { pressAt = c.position; moved = 0f; dragging = true }
                        PointerEventType.Release -> {
                            if (moved < 6f) waveAt = time
                            pressAt = null; dragging = false
                        }
                        PointerEventType.Exit -> mouse = null
                        else -> {
                            val prev = mouse
                            mouse = c.position
                            if (pressAt != null && prev != null) {
                                val dx = c.position.x - prev.x
                                moved += abs(dx)
                                spin += dx / 120f
                            }
                        }
                    }
                }
            }
        },
    ) {
        val scale = min(size.height / 38f, size.width / 22f)
        val cx = size.width / 2f
        val cy = size.height / 2f + 1f * scale
        // Head follows the mouse (smoothly), within a natural range
        val m = mouse
        val wantYaw = if (m == null || !animate) 0f else ((m.x - cx) / size.width * 1.6f).coerceIn(-0.7f, 0.7f)
        val wantPitch = if (m == null || !animate) 0f else ((m.y - (cy - 12f * scale)) / size.height * 1.2f).coerceIn(-0.5f, 0.45f)
        look[0] += (wantYaw - look[0]) * 0.12f
        look[1] += (wantPitch - look[1]) * 0.12f
        val lookYaw = look[0]; val lookPitch = look[1]

        val t = if (animate) time else 0f
        val breath = sin(t * 2.2f) * 0.18f
        val swing = sin(t * 1.7f) * 0.07f
        val wave = ((t - waveAt) / 1.6f).let { if (it in 0f..1f) sin(it * PI.toFloat()) else 0f }
        val waveSwing = sin(t * 14f) * 0.35f * wave
        val modelYaw = -0.42f + spin
        val camPitch = 0.16f
        // model space → view space: the whole body turns, then the camera looks slightly down
        val origin = Vec(0f, 0f, 0f)
        fun view(p: Vec): Vec = rotate(rotate(Vec(p.x, p.y - 16f, p.z), origin, 0f, 0f, modelYaw), origin, camPitch, 0f, 0f)
        fun posed(pitch: Float, roll: Float, yaw: Float, pivot: Vec, lift: Float): (Vec) -> Vec = { p ->
            val q = rotate(Vec(p.x, p.y + lift, p.z), Vec(pivot.x, pivot.y + lift, pivot.z), pitch, roll, yaw)
            view(q)
        }

        val armW = if (skin.slim) 3f else 4f
        val legacy = skin.legacy
        val list = ArrayList<Face>(80)
        // Head (+ hat), turned towards the mouse
        val head = posed(lookPitch, 0f, lookYaw, Vec(0f, 24f, 0f), breath * 0.6f)
        faces(Part(8f, 8f, 8f, -4f, 24f, -4f, 0, 0, Vec(0f, 24f, 0f)), head, list)
        faces(Part(8f, 8f, 8f, -4f, 24f, -4f, 32, 0, Vec(0f, 24f, 0f), grow = 0.5f), head, list)
        // Body (+ jacket)
        val body = posed(0f, 0f, 0f, Vec(0f, 12f, 0f), breath * 0.5f)
        faces(Part(8f, 12f, 4f, -4f, 12f, -2f, 16, 16, Vec(0f, 12f, 0f)), body, list)
        if (!legacy) faces(Part(8f, 12f, 4f, -4f, 12f, -2f, 16, 32, Vec(0f, 12f, 0f), grow = 0.25f), body, list)
        // Arms: a small swing; the right one waves after a click
        val shoulderR = Vec(-4f - armW / 2f, 22f, 0f)
        val shoulderL = Vec(4f + armW / 2f, 22f, 0f)
        val rightArm = posed(swing - 2.6f * wave, -0.06f + 0.56f * wave + waveSwing, 0f, shoulderR, breath * 0.5f)
        val leftArm = posed(-swing, 0.06f, 0f, shoulderL, breath * 0.5f)
        val aw = armW.toInt()
        faces(Part(armW, 12f, 4f, -4f - armW, 12f, -2f, 40, 16, shoulderR, tw = aw), rightArm, list)
        faces(Part(armW, 12f, 4f, 4f, 12f, -2f, if (legacy) 40 else 32, if (legacy) 16 else 48, shoulderL, tw = aw), leftArm, list)
        if (!legacy) {
            faces(Part(armW, 12f, 4f, -4f - armW, 12f, -2f, 40, 32, shoulderR, grow = 0.25f, tw = aw), rightArm, list)
            faces(Part(armW, 12f, 4f, 4f, 12f, -2f, 48, 48, shoulderL, grow = 0.25f, tw = aw), leftArm, list)
        }
        // Legs
        val hipR = Vec(-2f, 12f, 0f); val hipL = Vec(2f, 12f, 0f)
        val rightLeg = posed(0f, 0f, 0f, hipR, 0f)
        val leftLeg = posed(0f, 0f, 0f, hipL, 0f)
        faces(Part(4f, 12f, 4f, -4f, 0f, -2f, 0, 16, hipR), rightLeg, list)
        faces(Part(4f, 12f, 4f, 0f, 0f, -2f, if (legacy) 0 else 16, if (legacy) 16 else 48, hipL), leftLeg, list)
        if (!legacy) {
            faces(Part(4f, 12f, 4f, -4f, 0f, -2f, 0, 32, hipR, grow = 0.25f), rightLeg, list)
            faces(Part(4f, 12f, 4f, 0f, 0f, -2f, 0, 48, hipL, grow = 0.25f), leftLeg, list)
        }

        // Soft shadow under the feet
        drawOval(
            Color.Black.copy(alpha = 0.35f),
            topLeft = Offset(cx - 9f * scale, cy + 15.2f * scale), size = androidx.compose.ui.geometry.Size(18f * scale, 3.2f * scale),
        )
        list.sortBy { it.depth }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            for (f in list) {
                // Screen points of the face's top-left, top-right and bottom-left corners
                val s0x = cx + f.p0.x * scale; val s0y = cy - f.p0.y * scale
                val s1x = cx + f.p1.x * scale; val s1y = cy - f.p1.y * scale
                val s3x = cx + f.p3.x * scale; val s3y = cy - f.p3.y * scale
                native.save()
                native.concat(Matrix33((s1x - s0x) / f.tw, (s3x - s0x) / f.th, s0x, (s1y - s0y) / f.tw, (s3y - s0y) / f.th, s0y, 0f, 0f, 1f))
                val s = (f.shade.coerceAtMost(1f) * 255).toInt()
                paint.colorFilter = ColorFilter.makeBlend(org.jetbrains.skia.Color.makeARGB(255, s, s, s), BlendMode.MODULATE)
                native.drawImageRect(
                    skin.image,
                    Rect.makeXYWH(f.u.toFloat(), f.v.toFloat(), f.tw.toFloat(), f.th.toFloat()),
                    Rect.makeXYWH(-0.03f, -0.03f, f.tw + 0.06f, f.th + 0.06f),
                    sampling, paint, true,
                )
                native.restore()
            }
        }
    }
}

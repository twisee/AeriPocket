package com.controleco.app.ui.personal

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.roundToInt

internal data class ActorPoseSpec(
    val path: String,
    val fitVisibleBounds: Boolean = false,
    val minBudgetPercent: Int = 0,
)

/** Pose IDs name the artwork; the pack defines the actual budget boundaries separately. */
internal fun actorStageForProgress(progress: Float, poses: Map<Int, ActorPoseSpec>): Int =
    poses.entries.filter { progress >= it.value.minBudgetPercent / 100f }
        .maxByOrNull { it.value.minBudgetPercent }?.key
        ?: poses.entries.minByOrNull { it.value.minBudgetPercent }?.key ?: 0

internal data class ActorAtlas(
    val image: ImageBitmap,
    val frameWidth: Int,
    val openAnchor: Float,
    val closedAnchor: Float,
    val visibleBounds: IntRange? = null,
) {
    fun placement(width: Float, height: Float, stage: Int, closed: Boolean, edgeInset: Float, bottomInset: Float): ActorPlacement =
        actorPlacement(width, height, 0f, stage, frameWidth, openAnchor, closedAnchor, closed, 0f, 0f,
            visibleBounds, edgeInset, bottomInset)

    companion object {
        fun load(context: Context, path: String, fitVisibleBounds: Boolean = false): ActorAtlas {
            val bitmap = context.assets.open(path).use { BitmapFactory.decodeStream(it) }
            val frameWidth = bitmap.width / 2
            require(bitmap.width == bitmap.height * 2) { "Character atlas must contain two square frames" }
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            // Scan once while loading, never during animation. Use the approved open-eye crop.
            val visibleBounds = if (fitVisibleBounds) {
                var left = frameWidth
                var right = -1
                for (y in 0 until bitmap.height) for (x in 0 until frameWidth) {
                    if ((pixels[y * bitmap.width + x] ushr 24) > 64) {
                        left = minOf(left, x)
                        right = maxOf(right, x)
                    }
                }
                if (right >= left) left..right else 0 until frameWidth
            } else null
            // Align the body's alpha-weighted horizontal centre, excluding the expressive face.
            // Registration happens once per loaded atlas, never during animation frames.
            fun anchor(frame: Int): Float {
                var weightedX = 0.0; var weight = 0.0
                for (y in bitmap.height / 3 until bitmap.height step 6) for (x in 0 until frameWidth step 6) {
                    val alpha = (pixels[y * bitmap.width + frame * frameWidth + x] ushr 24) and 255
                    if (alpha > 32) { weightedX += x.toDouble() * alpha; weight += alpha }
                }
                return if (weight == 0.0) frameWidth / 2f else (weightedX / weight).toFloat()
            }
            return ActorAtlas(bitmap.asImageBitmap(), frameWidth, anchor(0), anchor(1), visibleBounds)
        }
    }
}

internal data class ActorPlacement(val left: Int, val top: Int, val side: Int)

internal fun actorPlacement(width: Float, height: Float, sceneTop: Float, stage: Int, frameWidth: Int,
    openAnchor: Float, closedAnchor: Float, closed: Boolean, breath: Float, reaction: Float,
    visibleBounds: IntRange? = null, edgeInset: Float = 12f, bottomInset: Float = 8f): ActorPlacement {
    val available = (height - sceneTop - 12f).coerceAtLeast(1f)
    val widthScale = when(stage) { 100 -> 1.06f; 25, 75 -> 1.55f; else -> 1.65f }
    // Keep width and horizontal pivot fixed throughout breathing/taps. Only move vertically.
    if (visibleBounds != null) {
        val visibleWidth = (visibleBounds.last - visibleBounds.first + 1).coerceAtLeast(1)
        val center = (visibleBounds.first + visibleBounds.last) / 2f
        val side = minOf(available, (height - sceneTop - bottomInset).coerceAtLeast(1f), width * widthScale,
            (width - edgeInset * 2).coerceAtLeast(1f) * frameWidth / visibleWidth).toInt().coerceAtLeast(1)
        val alignment = if (closed) (openAnchor - closedAnchor) * side / frameWidth else 0f
        val left = (width / 2 - center * side / frameWidth + alignment).toInt()
        val top = (height - side - bottomInset - breath * 2f - reaction * 8f).toInt()
        return ActorPlacement(left, top, side)
    }
    val side = minOf(available, width * widthScale).roundToInt().coerceAtLeast(1)
    val alignment = if (closed) (openAnchor - closedAnchor) * side / frameWidth else 0f
    val left = ((width - side) / 2 + alignment).roundToInt()
    val top = ((if (stage == 100) sceneTop + (available - side) * .4f else height - side - 8f) - breath * 2f - reaction * 8f).roundToInt()
    return ActorPlacement(left, top, side)
}

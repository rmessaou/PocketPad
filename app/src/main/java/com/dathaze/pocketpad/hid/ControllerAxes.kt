package com.dathaze.pocketpad.hid

import kotlin.math.abs
import kotlin.math.roundToInt

/** Normalize Android motion samples using the range advertised for that axis and source. */
internal object ControllerAxes {
    fun centered(value: Float, min: Float, max: Float, flat: Float): Float {
        if (!valid(value, min, max)) return 0f
        val center = if (min < 0f && max > 0f) 0f else (min + max) / 2f
        val offset = value - center
        if (abs(offset) <= flat) return 0f
        val span = if (offset < 0f) center - min else max - center
        return (offset / span).coerceIn(-1f, 1f)
    }

    fun trigger(value: Float, min: Float, max: Float, flat: Float): Float {
        if (!valid(value, min, max) || value - min <= flat) return 0f
        return ((value - min) / (max - min)).coerceIn(0f, 1f)
    }

    private fun valid(value: Float, min: Float, max: Float): Boolean =
        value.isFinite() && min.isFinite() && max.isFinite() && min < max

    fun stickByte(centered: Float): Int = ((centered + 1f) * 127.5f).roundToInt()
}

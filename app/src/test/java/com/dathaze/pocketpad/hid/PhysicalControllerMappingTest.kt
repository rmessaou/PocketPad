package com.dathaze.pocketpad.hid

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalControllerMappingTest {
    @Test
    fun androidHostButtonBitsMatchLinuxEvdevAndGenericKeyLayout() {
        val evdevCodes = mapOf(
            HidConstants.BTN_CROSS to 304, HidConstants.BTN_CIRCLE to 305,
            HidConstants.BTN_SQUARE to 307, HidConstants.BTN_TRIANGLE to 308,
            HidConstants.BTN_L1 to 310, HidConstants.BTN_R1 to 311,
            HidConstants.BTN_L2 to 312, HidConstants.BTN_R2 to 313,
            HidConstants.BTN_SHARE to 314, HidConstants.BTN_OPTIONS to 315,
            HidConstants.BTN_PS to 316, HidConstants.BTN_L3 to 317, HidConstants.BTN_R3 to 318,
        )
        fun field(report: ByteArray) =
            (report[0].toInt() and 0xff) or ((report[1].toInt() and 0xff) shl 8)
        val state = GamepadState().apply { hat = 4; lx = 0; ry = 255; rt = 64 }
        for ((button, evdev) in evdevCodes) {
            state.setButton(button, true)
            val legacy = state.toReport()
            val android = state.toReport(androidButtonLayout = true)
            assertEquals(1 shl button, field(legacy))
            assertEquals(1 shl (evdev - 304), field(android))
            assertArrayEquals(legacy.copyOfRange(2, 9), android.copyOfRange(2, 9))
            state.setButton(button, false)
            assertEquals(0, field(state.toReport(androidButtonLayout = true)))
        }
    }

    @Test
    fun bothHostLayoutsSendTheSameIdleReport() {
        val expected = byteArrayOf(0, 0, 8, 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0, 0)
        assertArrayEquals(expected, GamepadState().toReport())
        assertArrayEquals(expected, GamepadState().toReport(androidButtonLayout = true))
    }

    @Test
    fun simultaneousPhysicalInputsAndTheirReleaseReachTheAndroidReportQueue() {
        val source = ExternalInputTracker.Source()
        source.state.setButton(HidConstants.BTN_SQUARE, true)
        source.state.lx = 0
        source.state.ry = 255
        source.setHatAxes(1f, 1f)
        source.setTriggerKey(left = false, pressed = true)

        val queue = ReportQueue()
        queue.offer(source.state.toReport(androidButtonLayout = true))
        source.state.setButton(HidConstants.BTN_SQUARE, false)
        source.state.lx = 128
        source.state.ry = 128
        source.setHatAxes(0f, 0f)
        source.setTriggerKey(left = false, pressed = false)
        queue.offer(source.state.toReport(androidButtonLayout = true))

        val held = listOf(0x08, 0x02, 3, 0, 128, 128, 255, 0, 255)
        assertArrayEquals(held.map(Int::toByte).toByteArray(), queue.peek())
        queue.onSent()
        assertArrayEquals(HidConstants.neutralGamepadReport(), queue.peek())
    }

    @Test
    fun digitalTriggerSurvivesStickMotionAndReleasesIndependentlyOfAnalogTrigger() {
        val source = ExternalInputTracker.Source()
        source.setTriggerKey(left = true, pressed = true)
        source.setTriggerAxes(left = 0f, right = 0.5f)
        assertEquals(255, source.state.lt)
        assertEquals(128, source.state.rt)
        assertTrue(source.state.isPressed(HidConstants.BTN_L2))
        assertTrue(source.state.isPressed(HidConstants.BTN_R2))
        source.setTriggerKey(left = true, pressed = false)
        assertEquals(0, source.state.lt)
        assertEquals(128, source.state.rt)
        source.setTriggerAxes(left = 0f, right = 0f)
        assertArrayEquals(HidConstants.neutralGamepadReport(), source.state.toReport())
    }

    @Test
    fun releasingDuplicateTriggerKeyDoesNotClearRemainingAnalogPressure() {
        val source = ExternalInputTracker.Source()
        source.setTriggerAxes(left = 0.25f, right = 0f)
        source.setTriggerKey(left = true, pressed = true)
        assertEquals(64, source.state.lt)
        source.setTriggerKey(left = true, pressed = false)
        assertEquals(64, source.state.lt)
        assertTrue(source.state.isPressed(HidConstants.BTN_L2))
        source.setTriggerAxes(left = 0f, right = 0f)
        assertFalse(source.state.isPressed(HidConstants.BTN_L2))
        assertEquals(0, source.state.lt)
    }

    @Test
    fun motionRangeAndFlatNormalizeStickHatAndTriggerSamples() {
        assertEquals(128, ControllerAxes.stickByte(ControllerAxes.centered(4f, -128f, 127f, 5f)))
        assertEquals(0, ControllerAxes.stickByte(ControllerAxes.centered(-128f, -128f, 127f, 5f)))
        assertEquals(255, ControllerAxes.stickByte(ControllerAxes.centered(127f, -128f, 127f, 5f)))
        assertEquals(128, ControllerAxes.stickByte(ControllerAxes.centered(Float.NaN, -1f, 1f, 0f)))
        assertEquals(0f, ControllerAxes.trigger(-1f, -1f, 1f, 0.05f), 0f)
        assertEquals(0.5f, ControllerAxes.trigger(0f, -1f, 1f, 0.05f), 0f)
        assertEquals(1f, ControllerAxes.trigger(1023f, 0f, 1023f, 0f), 0f)

        val source = ExternalInputTracker.Source()
        fun hatY(raw: Float) = ControllerAxes.centered(raw, 0f, 2f, 0f)
        source.setHatAxes(0f, hatY(2f))
        assertEquals(4, source.state.hat)
        source.keyLeft = true
        source.setHatAxes(0f, hatY(1f))
        assertEquals(6, source.state.hat)
        source.keyLeft = false
        source.applyDpad()
        assertEquals(HidConstants.HAT_NEUTRAL, source.state.hat)
    }

    @Test
    fun clearingAndroidInputDoesNotDropUsbStateOrReviveDetachedDevices() {
        val tracker = ExternalInputTracker()
        tracker.source(4).state.hat = 4
        tracker.source(ExternalInputTracker.SOURCE_DS3).state.setButton(HidConstants.BTN_L1, true)
        tracker.remove(7)
        assertTrue(tracker.clearAndroidSources())
        val out = GamepadState()
        tracker.combineInto(out)
        assertTrue(out.isPressed(HidConstants.BTN_L1))
        assertEquals(HidConstants.HAT_NEUTRAL, out.hat)
        assertTrue(tracker.isDetached(7))
    }
}

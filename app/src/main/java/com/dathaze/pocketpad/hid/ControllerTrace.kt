package com.dathaze.pocketpad.hid

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import android.view.InputDevice

/** Opt-in via `adb shell setprop log.tag.PocketPadInput DEBUG`, debug builds only. */
internal class ControllerTrace(context: Context) {
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    inline fun log(message: () -> String) {
        if (enabled()) Log.d(TAG, message())
    }

    fun enabled(): Boolean = debuggable && Log.isLoggable(TAG, Log.DEBUG)

    companion object {
        const val TAG = "PocketPadInput"

        fun device(id: Int, eventSource: Int, device: InputDevice?): String =
            "id=$id name=${device?.name} source=0x${eventSource.toString(16)} " +
                "supported=0x${device?.sources?.toString(16)}"

        fun hex(bytes: ByteArray): String = bytes.joinToString(" ") { "%02x".format(it.toInt() and 0xff) }
    }
}

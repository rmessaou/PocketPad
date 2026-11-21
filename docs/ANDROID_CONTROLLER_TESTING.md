# Test physical controller passthrough to an Android host

This guide covers the Anbernic RG556 as the PocketPad sender and the Samsung Galaxy Tab S7 as the Bluetooth receiver. The Android layout corrects the button slots expected by Android's generic HID driver. It does not change the advertised descriptor or the original TV layout. The sender also keeps physical trigger keys separate from analog pressure so unrelated joystick motion cannot clear a held trigger.

Hardware verification is still required. A neutral report is correctly encoded in unit tests, but that does not establish the cause of the phantom D-pad Down observed on the tablet.

## Install and pair

From the repository root, with Android SDK Platform 36 and JDK 17:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
adb devices -l
adb -s RG556_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
```

Replace `RG556_SERIAL` with the handheld's actual ADB serial. Enable USB debugging on the RG556 and approve the computer when prompted. Under WSL, use Windows `adb.exe` with a Windows APK path if the handheld is attached to Windows rather than passed through to Linux.

1. Close other Bluetooth gamepad apps on the RG556. Open PocketPad and allow Nearby devices.
2. In **Settings → Controller**, turn **Android host button layout** on and **TV menu keys** off.
3. If already bonded, use **Settings → Connect to a paired device** and select the tablet. Keep PocketPad open in the foreground.
4. If the tablet recognizes the wrong controls or fails to connect, forget the bond on **both** devices. In PocketPad choose **Make discoverable**, then scan from the tablet's Bluetooth settings, pair with the RG556, and use PocketPad's paired-device picker to connect again. This rules out a cached descriptor from an older build or another app; the new layout itself does not change the descriptor.

## Verify on the tablet

Use a gamepad tester that displays Android key names and axis values before testing an emulator with fresh external-controller bindings. Verify:

- At connect and for several seconds without touching the RG556, the host sees a gamepad, no buttons, a centered D-pad and sticks, and released triggers.
- A/B/X/Y, all four D-pad directions, L1/R1, L2/R2, and Start/Select each register correctly and release. Verify both stick axes in both directions, stick clicks if present, and gradual trigger travel if the handheld exposes it.
- Hold a D-pad direction and a face button while moving both sticks and squeezing the triggers. Release in a different order and check that nothing stays held. Keep playing for several minutes, then disconnect/reconnect and check idle again.
- Compare the equivalent touchscreen controls with the physical ones. If only physical input fails, capture the raw sender data below. If both fail, inspect the host event stream and pairing.

## Capture a trace

Logging is off by default and only available in debuggable builds. Start this **before** connecting:

```sh
adb -s RG556_SERIAL shell setprop log.tag.PocketPadInput DEBUG
adb -s RG556_SERIAL logcat -v threadtime -s PocketPadInput:D '*:S' > rg556-pocketpad.log
```

Connect, leave the controller untouched for five seconds, then press/release one control at a time, including D-pad Down and R2, and finally try simultaneous inputs. Stop with Ctrl+C. For a host-side view, use a second terminal (if the tablet permits ADB access to its input nodes):

```sh
adb -s TAB_S7_SERIAL shell dumpsys input > tab-s7-input.txt
adb -s TAB_S7_SERIAL shell getevent -lp
adb -s TAB_S7_SERIAL shell getevent -lt /dev/input/eventN > tab-s7-events.log
```

Replace `/dev/input/eventN` with the tablet's PocketPad input node from `getevent -lp`. If access is denied, use the tester's readings instead. Record the handset's controller mode, the Android versions, and whether a fresh bond or touchscreen input changes the result. Review logs for identifying device details before sharing.

The RG556 trace lists raw keys and axis ranges, merged physical state, report ID and hex payload, and whether Android accepted each send. `accepted=true` is **not** an acknowledgment from the tablet. For ID 1, idle is `00 00 08 80 80 80 80 00 00`. A third byte of `04` means PocketPad sent Down; `08` means it sent a released hat. ID 2 is the separate keyboard report and should have no held key when TV menu keys are off.

Turn the trace off afterward:

```sh
adb -s RG556_SERIAL shell setprop log.tag.PocketPadInput INFO
```

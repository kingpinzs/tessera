# The first lines of every dev-camera device script (sourced): the device, this worktree's APK, the lock, the install.
export ANDROID_SERIAL=emulator-5554
WT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../../.." && pwd)"
export TILESHELL_APK="$WT/app/build/outputs/apk/debug/app-debug.apk"
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"; . "$(dirname "${BASH_SOURCE[0]}")/cam.sh"
take_device_lock
install_mine; ensure_camera_grant

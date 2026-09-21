#!/usr/bin/env bash
# Which app.tileshell build is on the emulator right now. The device is shared with another session, so every
# check names the APK it ran against: "mine" is the build this worktree produced.
export PATH=$HOME/Android/Sdk/platform-tools:$PATH
REPO=/home/jeremyking/projects/metro-launcher/.claude/worktrees/agent-a00901a5ef0b39ec2
MINE=$(md5sum "$REPO/app/build/outputs/apk/debug/app-debug.apk" | cut -d' ' -f1)
ON_DEVICE=$(adb shell "md5sum \$(pm path app.tileshell | sed 's/package://' | tr -d '\r')" | cut -d' ' -f1)
echo "worktree apk md5 : $MINE"
echo "installed apk md5: $ON_DEVICE"
if [ "$MINE" = "$ON_DEVICE" ]; then echo "INSTALLED BUILD = MINE"; exit 0; else echo "INSTALLED BUILD = SOMEONE ELSE'S"; exit 1; fi

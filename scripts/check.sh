#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -d .tools/jdk/Contents/Home ]; then
    export JAVA_HOME="$PWD/.tools/jdk/Contents/Home"
fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.tools/gradle-home}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$PWD/.tools/android-user}"
./gradlew --no-daemon spotlessCheck :domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest

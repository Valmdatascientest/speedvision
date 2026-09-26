#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -d .tools/jdk/Contents/Home ]; then
    export JAVA_HOME="$PWD/.tools/jdk/Contents/Home"
fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$PWD/.tools/gradle-home}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-$PWD/.tools/android-user}"
python3 -m unittest discover -s testing/detection
python3 -m unittest discover -s testing/speed
python3 -m unittest discover -s testing/performance
if [ -x .tools/calibration-env/bin/python ]; then
    .tools/calibration-env/bin/python -m unittest discover -s testing/calibration
else
    python3 -m unittest discover -s testing/calibration
fi
./gradlew --no-daemon spotlessCheck :domain:test :domain:benchmarkSpeed :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest

python3 scripts/audit_android_manifest.py

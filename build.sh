#!/usr/bin/env bash
# Builds Clean Home without the Android SDK: aapt2 (from apktool), dx, and a framework jar.
# TOOLS must contain: aapt2, android-framework.jar (resources), android-all-*.jar (classes), dalvik-dx-*.jar
set -euo pipefail
cd "$(dirname "$0")"
TOOLS=${TOOLS:?set TOOLS to the toolchain directory}
AAPT2=$TOOLS/prebuilt/linux/aapt2
FRAMEWORK=$TOOLS/android-framework.jar
CLASSES_JAR=$(ls $TOOLS/android-all-*.jar | head -1)
DX=$(ls $TOOLS/dalvik-dx-*.jar | head -1)
OUT=build
rm -rf $OUT && mkdir -p $OUT/res $OUT/gen $OUT/classes

$AAPT2 compile --dir res -o $OUT/res/
$AAPT2 link -o $OUT/unsigned.apk -I $FRAMEWORK --manifest AndroidManifest.xml \
  --java $OUT/gen --min-sdk-version 22 --target-sdk-version 28 $OUT/res/*.flat

javac -nowarn --release 8 -cp "$CLASSES_JAR" -d $OUT/classes \
  $(find src $OUT/gen -name '*.java') 2>&1 | grep -v 'warning: \[options\]' || true
[ -f $OUT/classes/com/clean/tvhome/HomeActivity.class ] || { echo "javac failed"; exit 1; }

java -cp "$DX" com.android.dx.command.Main --dex --min-sdk-version=22 --output=$OUT/classes.dex $OUT/classes
(cd $OUT && cp unsigned.apk app.apk && zip -q app.apk classes.dex)

KS=${KEYSTORE:-$HOME/.clean-home.keystore}
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass cleanhome -keypass cleanhome -alias home \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Clean Home" >/dev/null 2>&1
jarsigner -keystore "$KS" -storepass cleanhome -sigalg SHA256withRSA -digestalg SHA-256 \
  -signedjar CleanHome.apk $OUT/app.apk home >/dev/null
echo "Built $(pwd)/CleanHome.apk"

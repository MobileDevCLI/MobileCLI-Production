#!/data/data/com.termux/files/usr/bin/bash
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=/data/data/com.termux/files/home/android-sdk
cd /data/user/0/com.termux/files/home/MobileCLI-Production
./gradlew :app:assembleUserDebug
cp app/build/outputs/apk/user/debug/app-user-debug.apk /sdcard/Download/MobileCLI-v162.apk
echo "Build complete! APK at /sdcard/Download/MobileCLI-v162.apk"

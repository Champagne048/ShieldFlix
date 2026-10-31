#!/bin/bash
# Chạy thử ShieldFlix trên máy ảo Android TV: mở app, đi lại bằng phím remote, phát thử một torrent hợp pháp (Big Buck Bunny, CC-BY).
mkdir -p out
shot() { adb exec-out screencap -p > "out/$1.png"; }
adb install -r ShieldFlix.apk > out/install.txt 2>&1
adb logcat -c
adb shell am start -W -n vn.son.shieldflix/.MainActivity > out/start.txt 2>&1
sleep 20
shot 01-start
adb shell pidof vn.son.shieldflix > out/pid-start.txt 2>&1 || echo DEAD > out/pid-start.txt
adb forward tcp:8090 tcp:8090
curl -s -m 10 http://127.0.0.1:8090/ > out/config.html 2>&1 || echo "config page fail" > out/config.html
for k in DPAD_DOWN DPAD_RIGHT DPAD_UP; do adb shell input keyevent KEYCODE_$k; sleep 2; done
shot 02-after-keys
MAG='magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c&dn=Big+Buck+Bunny&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=udp%3A%2F%2Fexplodie.org%3A6969&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A6969'
adb shell "am start -a android.intent.action.VIEW -d '$MAG'" > out/magnet.txt 2>&1
for i in 1 2 3 4 5 6 7 8; do sleep 25; shot "1$i-play"; done
adb shell input keyevent KEYCODE_DPAD_UP; sleep 3; shot 20-subpanel
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_DPAD_RIGHT; sleep 8; shot 21-seek
adb shell input keyevent KEYCODE_BACK; sleep 2; adb shell input keyevent KEYCODE_BACK; sleep 4; shot 22-back
adb shell pidof vn.son.shieldflix > out/pid-end.txt 2>&1 || echo DEAD > out/pid-end.txt
adb logcat -d -b crash > out/crash.txt 2>&1
adb logcat -d > out/logcat.txt 2>&1
grep -iE "shieldflix|AndroidRuntime|ExoPlayer|libtorrent|torrent4j|NanoHTTPD|FATAL" out/logcat.txt > out/logcat-app.txt
exit 0

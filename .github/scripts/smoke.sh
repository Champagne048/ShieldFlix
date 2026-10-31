#!/bin/bash
# Chạy thử ShieldFlix trên máy ảo Android TV: mở app, đi lại bằng phím remote, phát thử một torrent hợp pháp (Big Buck Bunny, CC-BY).
mkdir -p out
shot() { adb exec-out screencap -p > "out/$1.png"; }
key() { for k in "$@"; do adb shell input keyevent "KEYCODE_$k"; sleep 1.2; done; }
adb install -r ShieldFlix.apk > out/install.txt 2>&1
adb logcat -c
adb shell am start -W -n vn.son.shieldflix/.MainActivity > out/start.txt 2>&1
sleep 25
shot 01-home
adb shell pidof vn.son.shieldflix > out/pid-start.txt 2>&1 || echo DEAD > out/pid-start.txt
key DPAD_RIGHT DPAD_RIGHT; sleep 3; shot 02-top10-right
key DPAD_DOWN; sleep 4; shot 03-row-movies
key DPAD_DOWN DPAD_RIGHT; sleep 4; shot 04-row-series
key DPAD_CENTER; sleep 8; shot 05-detail-series
key DPAD_DOWN DPAD_DOWN; sleep 4; shot 06-detail-episodes
key BACK; sleep 3; shot 07-back-home
key DPAD_UP; sleep 2; key DPAD_CENTER; sleep 8; shot 08-detail-movie
key BACK; sleep 2
key DPAD_UP DPAD_UP DPAD_UP DPAD_UP; sleep 2; shot 09-topbar
key DPAD_RIGHT DPAD_CENTER; sleep 3
adb shell input text "dune"; sleep 8; shot 10-search
key BACK BACK; sleep 2
MAG='magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c&dn=Big+Buck+Bunny&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce&tr=udp%3A%2F%2Fexplodie.org%3A6969&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A6969'
adb shell "am start -a android.intent.action.VIEW -d '$MAG'" > out/magnet.txt 2>&1
sleep 12; shot 11-loading
sleep 60; shot 12-play
key BACK; sleep 2; key BACK; sleep 3; shot 13-after-player
adb shell pidof vn.son.shieldflix > out/pid-end.txt 2>&1 || echo DEAD > out/pid-end.txt
adb logcat -d -b crash > out/crash.txt 2>&1
adb logcat -d > out/logcat.txt 2>&1
grep -iE "shieldflix|AndroidRuntime|ExoPlayer|libtorrent|torrent4j|NanoHTTPD|FATAL|coil" out/logcat.txt > out/logcat-app.txt
exit 0

#!/usr/bin/env bash
# ============================================================
#  PixelCode — первичная настройка инструментов сборки в Termux.
#  Запускается приложением через RUN_COMMAND, можно и вручную:
#    bash termux-bootstrap.sh
# ============================================================
set -u
CACHE="${PIXELCODE_CACHE:-$HOME/.pixelcode}"
mkdir -p "$CACHE"
LOG="$CACHE/bootstrap.log"
exec > >(tee "$LOG") 2>&1

say() { echo "[pixelcode-bootstrap] $*"; }

say "обновление списков пакетов…"
pkg update -y || apt update -y || true
pkg upgrade -y || true

say "установка инструментов (java, aapt, apksigner, zip)…"
# openjdk-17 нужен для javac/keytool; ecj и dx — запасные компилятор/дексер
ok=0
for set in \
  "openjdk-17 aapt apksigner zip findutils curl" \
  "aapt apksigner zip findutils curl ecj openjdk-17" \
  "aapt apksigner zip findutils curl ecj dx" ; do
  say "  пробую: pkg install $set"
  if pkg install -y $set; then ok=1; break; fi
done
[ $ok -eq 1 ] || { say "ОШИБКА: не удалось установить пакеты"; exit 1; }
command -v zipalign >/dev/null 2>&1 || pkg install -y zipalign || true
command -v d8 >/dev/null 2>&1 || pkg install -y d8 || true
command -v dx  >/dev/null 2>&1 || pkg install -y dx  || true

say "скачиваю android.jar (API 34) — это нужно один раз (~26 МБ)…"
if [ ! -f "$CACHE/android.jar" ]; then
  for u in \
    "https://raw.githubusercontent.com/Sable/android-platforms/master/android-34/android.jar" \
    "https://cdn.jsdelivr.net/gh/Sable/android-platforms@master/android-34/android.jar" ; do
    say "  источник: $u"
    if curl -fL --retry 2 --connect-timeout 20 -o "$CACHE/android.jar.part" "$u"; then
      mv "$CACHE/android.jar.part" "$CACHE/android.jar" && break
    fi
  done
fi
[ -f "$CACHE/android.jar" ] && say "android.jar готов" || say "ПРЕДУПРЕЖДЕНИЕ: android.jar не скачался — build-apk.sh попробует сам"

say "=== Готово! Теперь можно собирать APK из приложения PixelCode. ==="

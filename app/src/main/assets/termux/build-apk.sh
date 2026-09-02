#!/usr/bin/env bash
# ============================================================
#  PixelCode — сборка APK прямо на устройстве (Termux / Linux)
#  Использование:  bash build-apk.sh <каталог-проекта>
#  Результат:      <проект>/build/app.apk
#  Лог:            <проект>/build/build.log
#  Статус:         <проект>/build/status.txt (started/ok/error)
# ============================================================
set -u

PROJ_ARG="${1:-}"
[ -z "$PROJ_ARG" ] && { echo "Использование: build-apk.sh <каталог-проекта>"; exit 1; }
PROJ="$(cd "$PROJ_ARG" 2>/dev/null && pwd)" || { echo "Каталог не найден: $PROJ_ARG"; exit 1; }

BUILD="$PROJ/build"
LOG="$BUILD/build.log"
STATUS="$BUILD/status.txt"
APK="$BUILD/app.apk"
mkdir -p "$BUILD"
rm -f "$APK"
echo "started" > "$STATUS"
exec > >(tee "$LOG") 2>&1

say()  { echo "[pixelcode] $*"; }
die()  { echo "[pixelcode] ОШИБКА: $*"; echo "error" > "$STATUS"; exit 1; }

say "=== Сборка APK: $PROJ ==="
date

# ---------- 1. Поиск манифеста / ресурсов / исходников ----------
MANIFEST=""
for c in "$PROJ/app/src/main/AndroidManifest.xml" "$PROJ/AndroidManifest.xml"; do
  [ -f "$c" ] && { MANIFEST="$c"; break; }
done
[ -z "$MANIFEST" ] && die "не найден AndroidManifest.xml"

RES=""
for c in "$PROJ/app/src/main/res" "$PROJ/res"; do
  [ -d "$c" ] && { RES="$c"; break; }
done

ASSETS=""
for c in "$PROJ/app/src/main/assets" "$PROJ/assets"; do
  [ -d "$c" ] && { ASSETS="$c"; break; }
done

SRCDIRS=()
for c in "$PROJ/app/src/main/java" "$PROJ/src" "$PROJ/java"; do
  [ -d "$c" ] && SRCDIRS+=("$c")
done
[ ${#SRCDIRS[@]} -eq 0 ] && die "не найден каталог с java-исходниками"
say "манифест: $MANIFEST"
say "ресурсы:  ${RES:-<нет>}"
say "исходники: ${SRCDIRS[*]}"

# ---------- 2. Инструменты ----------
PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
CACHE="${PIXELCODE_CACHE:-$HOME/.pixelcode}"
mkdir -p "$CACHE"

command -v java  >/dev/null 2>&1 || die "нет java. Выполни: pkg install openjdk-17"

# Компилятор: javac -> ecj
JAVAC_BIN="$(command -v javac || true)"
ECJ_JAR=""
if [ -z "$JAVAC_BIN" ]; then
  JAVAC_BIN="$(command -v ecj || true)"
  if [ -z "$JAVAC_BIN" ]; then
    for j in "$PREFIX/share/java/ecj.jar" "$PREFIX/share/ecj.jar"; do
      [ -f "$j" ] && { ECJ_JAR="$j"; break; }
    done
    [ -z "$ECJ_JAR" ] && die "нет javac/ecj. Выполни: pkg install openjdk-17 (или ecj)"
  fi
fi

AAPT2_BIN="$(command -v aapt2 || true)"
AAPT_BIN="$(command -v aapt || true)"
[ -z "$AAPT2_BIN" ] && [ -z "$AAPT_BIN" ] && die "нет aapt/aapt2. Выполни: pkg install aapt"

D8_BIN="$(command -v d8 || true)"
DX_BIN="$(command -v dx || true)"
ZIPALIGN_BIN="$(command -v zipalign || true)"
[ -z "$ZIPALIGN_BIN" ] && say "ПРЕДУПРЕЖДЕНИЕ: zipalign не найден — APK будет без выравнивания (на установку не влияет)"
command -v apksigner >/dev/null 2>&1 || die "нет apksigner. Выполни: pkg install apksigner"
command -v keytool   >/dev/null 2>&1 || die "нет keytool. Выполни: pkg install openjdk-17"
command -v zip       >/dev/null 2>&1 || die "нет zip. Выполни: pkg install zip"
FIND_BIN="$(command -v find || true)"
[ -z "$FIND_BIN" ] && die "нет find. Выполни: pkg install findutils"

# android.jar (SDK-фреймворк для компиляции)
ANDROID_JAR="${ANDROID_JAR:-$CACHE/android.jar}"
if [ ! -f "$ANDROID_JAR" ]; then
  say "скачиваю android.jar (API 34, ~26 МБ)…"
  ok=0
  for u in \
    "https://raw.githubusercontent.com/Sable/android-platforms/master/android-34/android.jar" \
    "https://cdn.jsdelivr.net/gh/Sable/android-platforms@master/android-34/android.jar" ; do
    say "  источник: $u"
    if curl -fL --retry 2 --connect-timeout 20 -o "$ANDROID_JAR.part" "$u" 2>/dev/null; then
      mv "$ANDROID_JAR.part" "$ANDROID_JAR" && ok=1 && break
    fi
  done
  [ $ok -eq 1 ] || die "не удалось скачать android.jar. Положи его в $ANDROID_JAR вручную"
  say "android.jar готов"
fi

# r8.jar — D8-дексер, если нет d8/dx
R8_JAR="$CACHE/r8.jar"
if [ -z "$D8_BIN" ] && [ -z "$DX_BIN" ]; then
  if [ ! -f "$R8_JAR" ]; then
    say "скачиваю r8.jar (дексер, ~12 МБ)…"
    curl -fL --retry 2 --connect-timeout 20 -o "$R8_JAR.part" \
      "https://maven.google.com/com/android/tools/r8/8.5.35/r8-8.5.35.jar" \
      && mv "$R8_JAR.part" "$R8_JAR" || die "не удалось скачать r8.jar"
  fi
fi

TMPDIR="${TMPDIR:-$PREFIX/tmp}"
mkdir -p "$TMPDIR"
WORK="$(mktemp -d "$TMPDIR/pixelcode.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/gen" "$WORK/classes"

# ---------- 3. Ресурсы: aapt2 или aapt ----------
if [ -n "$AAPT2_BIN" ]; then
  say "упаковка ресурсов (aapt2)…"
  if [ -n "$RES" ]; then
    "$AAPT2_BIN" compile --dir "$RES" -o "$WORK/res.zip" || die "aapt2 compile не удался"
  else
    : > "$WORK/res.zip"
  fi
  A2_ARGS=(link -o "$WORK/base.apk" -I "$ANDROID_JAR" --manifest "$MANIFEST" --java "$WORK/gen" --auto-add-overlay)
  [ -s "$WORK/res.zip" ] && A2_ARGS+=("$WORK/res.zip")
  [ -n "$ASSETS" ] && A2_ARGS+=(-A "$ASSETS")
  "$AAPT2_BIN" "${A2_ARGS[@]}" || die "aapt2 link не удался (см. лог выше)"
else
  say "упаковка ресурсов (aapt)…"
  A1_ARGS=(package -f -I "$ANDROID_JAR" -M "$MANIFEST" -J "$WORK/gen" -F "$WORK/base.apk")
  [ -n "$RES" ] && A1_ARGS+=(-S "$RES")
  [ -n "$ASSETS" ] && A1_ARGS+=(-A "$ASSETS")
  "$AAPT_BIN" "${A1_ARGS[@]}" || die "aapt package не удался (см. лог выше)"
fi

# ---------- 4. Компиляция Java ----------
say "компиляция java…"
"$FIND_BIN" "${SRCDIRS[@]}" "$WORK/gen" -name "*.java" -type f > "$WORK/sources.list"
[ -s "$WORK/sources.list" ] || die "java-файлы не найдены"
if [ -n "$ECJ_JAR" ]; then
  java -jar "$ECJ_JAR" -1.8 -encoding UTF-8 -nowarn \
       -bootclasspath "$ANDROID_JAR" -d "$WORK/classes" @"$WORK/sources.list" \
       || die "компиляция (ecj) не удалась"
elif [ "$(basename "$JAVAC_BIN")" = "ecj" ]; then
  "$JAVAC_BIN" -1.8 -encoding UTF-8 -nowarn \
       -bootclasspath "$ANDROID_JAR" -d "$WORK/classes" @"$WORK/sources.list" \
       || die "компиляция (ecj) не удалась"
else
  "$JAVAC_BIN" -source 1.8 -target 1.8 -encoding UTF-8 -nowarn -Xlint:-options \
       -bootclasspath "$ANDROID_JAR" -d "$WORK/classes" @"$WORK/sources.list" \
       || die "компиляция (javac) не удалась"
fi

# ---------- 5. Дексация ----------
say "дексация (d8)…"
(cd "$WORK/classes" && zip -qr "$WORK/classes.zip" .)
if [ -n "$D8_BIN" ]; then
  "$D8_BIN" --release --lib "$ANDROID_JAR" --output "$WORK" "$WORK/classes.zip" || die "d8 не удался"
elif [ -n "$DX_BIN" ]; then
  "$DX_BIN" --dex --no-strict --output="$WORK" "$WORK/classes.zip" || die "dx не удался"
else
  java -cp "$R8_JAR" com.android.tools.r8.D8 --release --lib "$ANDROID_JAR" \
       --output "$WORK" "$WORK/classes.zip" || die "r8/d8 не удался"
fi
[ -f "$WORK/classes.dex" ] || die "classes.dex не создан"

# ---------- 6. Сборка APK ----------
say "сборка apk…"
cp "$WORK/base.apk" "$WORK/unsigned.apk"
(cd "$WORK" && zip -q unsigned.apk classes.dex) || die "не удалось добавить classes.dex"

if [ -n "$ZIPALIGN_BIN" ]; then
  "$ZIPALIGN_BIN" -f 4 "$WORK/unsigned.apk" "$WORK/aligned.apk" || die "zipalign не удался"
else
  cp "$WORK/unsigned.apk" "$WORK/aligned.apk"
fi

# ---------- 7. Подпись ----------
KS="$CACHE/debug.keystore"
if [ ! -f "$KS" ]; then
  say "генерирую debug-ключ…"
  keytool -genkeypair -keystore "$KS" -storepass pixelcode -keypass pixelcode \
    -alias pixelcode -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=PixelCode, O=PixelCode" >/dev/null 2>&1 || die "keytool не создал хранилище ключей"
fi
say "подпись…"
apksigner sign --ks "$KS" --ks-pass pass:pixelcode --key-pass pass:pixelcode \
  --min-sdk-version 21 --out "$APK" "$WORK/aligned.apk" || die "подпись не удалась"
apksigner verify "$APK" >/dev/null 2>&1 || die "проверка подписи не прошла"

SZ=$(du -h "$APK" | cut -f1)
echo "ok" > "$STATUS"
say "ГОТОВО: $APK ($SZ)"
say "установка: pm install -r $APK"
date

# ⚡ PixelCode — ИИ-кодер для Android (аналог opencode в кармане)

**PixelCode** — Android-приложение, в котором нейросеть **Mistral** (по твоему API-ключу) пишет
игры и приложения, а APK **собирается прямо на телефоне** — через **Termux** (у тебя уже есть 🙂)
и, при желании, тихо ставится через **Shizuku**.

- 🟣 Фиолетовая пиксельная тема, шрифт **Pixelify Sans** (поддерживает кириллицу)
- 🤖 Чат с Mistral (Codestral / Mistral Large / Small / Nemo / Magistral / Devstral) со стримингом
- 📝 Редактор кода с подсветкой синтаксиса Java/XML и нумерацией строк
- 🎮 Шаблоны проектов: игра (SurfaceView + игровой цикл), приложение-кликер, пустой
- 🔨 Сборка APK **без gradle и без ПК**: aapt + javac + d8 + apksigner в Termux
- 📲 Установка готового APK в один тап (или через Shizuku — вообще без диалога)

```
 Чат с ИИ ──⚡ Файлы──▶ /sdcard/PixelCode/<проект> ──🔨 APK──▶ Termux ──▶ app.apk ──▶ Установка
```

---

## 🚀 Быстрый старт

### Шаг 0. Установить Termux (если ещё нет)
Только **F-Droid** или **GitHub Releases** (версия из Google Play устарела и не подойдёт):
https://f-droid.org/packages/com.termux / https://github.com/termux/termux-app/releases

### Шаг 1. Разрешить PixelCode запускать команды в Termux (один раз)
Открой Termux и выполни:

```bash
mkdir -p ~/.termux
echo "allow-external-apps=true" >> ~/.termux/termux.properties
termux-reload-settings
```

### Шаг 2. Поставить инструменты сборки
Либо кнопкой **«Установить инструменты сборки (Termux)»** во вкладке «Настройки» приложения,
либо руками в Termux:

```bash
pkg update
pkg install -y openjdk-17 aapt apksigner zipalign zip ecj dx curl
```

(Это ~500 МБ. `android.jar` — ещё ~26 МБ — скрипт сборки скачает сам при первой сборке.)

### Шаг 3. Вставить API-ключ Mistral
console.mistral.ai → **API Keys** → скопировать `sk-…` → вкладка «Настройки» → поле «API-ключ».
Кнопка «Проверить API-ключ» должна ответить ✅.

### Шаг 4. Кодить!
1. «Проекты» → **+ Создать проект** (например `arkanoid`, шаблон «Игра»)
2. «ИИ-кодер» → опиши идею: *«сделай арканоид: платформа внизу, управление касанием, фиолетовые пиксельные кирпичи, счёт и рекорд»*
3. ИИ пришлёт файлы блоками `###FILE: …` → жми **⚡ Файлы** — они запишутся в проект
4. Жми **🔨 APK** — откроется сессия Termux, где пойдёт сборка (первая — дольше всех)
5. Когда в диалоге появится ✅ — **Установить**. Готово, игра на телефоне!

Все проекты лежат в `/sdcard/PixelCode/<имя>/`, готовый APK — в `<проект>/build/app.apk`.

---

## 🔨 Как работает сборка (без Android SDK и gradle)

`tools/build-apk.sh` (его копия лежит в assets приложения) делает всё сам:

```
aapt/aapt2 package  +  javac (или ecj)  +  d8/dx/r8  +  zipalign  +  apksigner
```

- `android.jar` (API 34) один раз скачивается в `~/.pixelcode/` с github.com/Sable/android-platforms
- если нет `d8`/`dx` — скачивается `r8.jar` с maven.google.com
- debug-ключ генерируется `keytool`'ом в `~/.pixelcode/debug.keystore`
- лог сборки: `<проект>/build/build.log`, статус: `<проект>/build/status.txt`

Приложение запускает скрипт интентом `com.termux.RUN_COMMAND` (служба
`com.termux.app.RunCommandService`) — поэтому и нужен шаг 1 и разрешение
`com.termux.permission.RUN_COMMAND` (приложение запросит само).

---

## 🩹 Shizuku (тихая установка и не только)

В диалоге сборки есть кнопка **«Скопировать rish-команду»** — с Shizuku это ставит APK вообще
без диалогов и без «неизвестных источников»:

```bash
# один раз: настроить rish (см. приложение Shizuku → «Use Shizuku in terminal»)
rish -c 'pm install -r /sdcard/PixelCode/arkanoid/build/app.apk'
```

Через rish же можно дать PixelCode «доступ ко всем файлам» без залезания в настройки:

```bash
rish -c 'appops set com.pixelcode.ai MANAGE_EXTERNAL_STORAGE allow'
```

---

## 🛠 Сборка самого PixelCode

### Вариант А — на телефоне в Termux (тот же путь, что и для проектов!)
```bash
pkg install -y openjdk-17 aapt apksigner zipalign zip ecj dx curl git
git clone https://github.com/eklem805-collab/openandriod
bash openandriod/tools/build-apk.sh openandriod
# → openandriod/build/app.apk
```

### Вариант Б — GitHub Actions (кнопкой)
Если в репозитории включены Actions (Settings → Actions → General → Allow all actions),
воркфлоу `build-apk` собирает APK ровно тем же скриптом при каждом пуше или кнопкой
**Run workflow** и кладёт его в `apk/PixelCode.apk` (плюс артефакт во вкладке Artifacts).

### Вариант В — Android Studio
Обычный Gradle-проект (AGP 7.4.2, `android.useAndroidX=false`, зависимостей нет).
Пакет объявлен в `AndroidManifest.xml` — как любят aapt-сборки.

> ✅ **Готовый APK уже собран CI и лежит в [`apk/PixelCode.apk`](apk/PixelCode.apk)** — можно просто
> скачать и поставить на телефон (лог сборки: `apk/build.log`).
> Обновляется автоматически при каждом пуше.

---

## 🧠 Что важно знать ИИ (и тебе)

Системный промпт PixelCode требует от модели:
- только **Java 8**, только **чистый android.\*** (без AndroidX/библиотек) — иначе не соберётся
- структуру `app/src/main/…` и формат `###FILE: путь` — приложение парсит и записывает файлы
- игры на **SurfaceView + Canvas + поток игрового цикла**
- полные файлы без сокращений

Если сборка упала — кнопка **«🤖 Исправить через ИИ»** в диалоге сборки отправит хвост лога
в чат, модель исправит файлы, снова «⚡ Файлы» → «🔨 APK».

---

## ❓ FAQ / Ограничения

- **Установка без Shizuku** — работает через системный PackageInstaller: один раз разреши
  PixelCode «устанавливать неизвестные приложения».
- **Доступ к файлам**: на Android 11+ при первом запуске приложение попросит «доступ ко всем
  файлам» — это нужно, чтобы проекты лежали в `/sdcard/PixelCode` и Termux их видел. Без
  разрешения проекты уйдут во внутренний каталог приложения и собрать их не получится.
- **Android 10 (API 29)**: с targetSdk 34 запись в общий каталог без MANAGE может не работать
  — дай разрешение через rish (команда выше) или собирай на Android 11+.
- **Первая сборка** самая долгая: скачивается `android.jar` (~26 МБ). Дальше — секунды.
- **Первый запуск модели**: новые модели Mistral появляются на api.mistral.ai/v1 — впиши имя
  в поле «своя модель» в Настройках.
- **Фон**: приложение держит чат в `<проект>/.pixelchat.json` — история сохраняется.

## 📁 Структура репозитория

```
app/                       исходники PixelCode (Java, без зависимостей)
  src/main/java/com/pixelcode/ai/   17 классов: чат, редактор, сборка, шаблоны…
  src/main/res/             фиолетовая пиксельная тема, layouts, иконки
  src/main/assets/          шрифт, шаблоны проектов, скрипты сборки для Termux
tools/build-apk.sh         главный скрипт сборки APK (Termux/Linux/CI)
tools/termux-bootstrap.sh  установка зависимостей в Termux
.github/workflows/build-apk.yml   CI: собирает apk/PixelCode.apk
apk/                       готовый APK от CI
```

## ⚖️ Лицензии

- Шрифт **Pixelify Sans** — SIL Open Font License (google.com/fonts/specimen/Pixelify+Sans)
- `android.jar` для компиляции — публичный артефакт github.com/Sable/android-platforms
- Код PixelCode — делай что хочешь (MIT по-дружески).

# Книга граблей (`PITFALLS.md`) — «Кӗсье Тӳме / кТӳме»

Короткие рецепты по неочевидным ловушкам сборки, Jetpack Compose, DataStore и Android `InputMethodService`, на которые уже наступали в проекте.

---

## Сборка, Gradle и подпись (Windows / JDK)

- **Пробелы в путях Windows и Kotlin Daemon:** в `src/gradle.properties` установлен `kotlin.incremental=false` → не удалять, он предотвращает `java.rmi.UnmarshalException` в Kotlin-демоне.
- **Файл `src/gradle/gradle-daemon-jvm.properties` удалён намеренно:** он задавал `toolchainVersion=25`, из-за чего `lintDebug` падал на `> 25.0.3` в `JavaVersion.parse`, игнорируя `org.gradle.java.home` → никогда не создавать его заново (в том числе через `updateDaemonJvm`); JDK 21 задаётся в пользовательском `~/.gradle/gradle.properties`.
- **Ключ релиза `ktume-release.jks` и Obtainium:** SHA-256 отпечаток сертификата опубликован в `README.md` и жёстко сверяется Obtainium при обновлениях → всегда хранить резервную копию `.jks` и паролей **вне** рабочей папки проекта.
- **Словари `src/app/src/main/assets/dict/cv.tsv` и `ru.tsv`:** не править вручную → пересобирать только скриптом `tools/dictionaries/build_dictionaries.py` (в шапке — источники и контрольные суммы; `ru.tsv` под CC BY-SA 4.0 отражён в `docs/THIRD_PARTY_NOTICES.md`).
- **Сборка в `git worktree` и `ANDROID_HOME`:** файл `src/local.properties` добавлен в `.gitignore` и не переносится в новые worktree → при запуске Gradle из worktree задавать `$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"`.

## Хранение настроек (Jetpack DataStore)

- **Смена значения по умолчанию уже существующей настройки:** если ключ уже записан на телефоне пользователя, новый дефолт в `KeyboardPreferences()` не применится сам → обязательно поднимать суффикс версии ключа в `PreferencesRepository` (например, `_v5` → `_v6`, при необходимости с fallback-чтением старого ключа).
- **Гонка при старте экрана настроек:** без гейта загрузки стартовый снимок дефолтов перезатирает пользовательские настройки и кастомные раскладки → `SettingsActivity` ждёт `PreferencesRepository.loaded == true` (выставляется в `finally`), а ранние изменения защищены флагом `hasPendingUserUpdate`.
- **Личный словарь (`personal_words_v1`) и активный язык (`active_lang_v1`):** хранятся отдельными ключами от основного `update` настроек → «Сбросить все настройки» не должен стирать личный словарь пользователя, а в полях с `IME_FLAG_NO_PERSONALIZED_LEARNING` (инкогнито) словарь не пополняется.

## Compose UI, жесты и геометрия клавиатуры

- **Соты (`HoneycombHexGrid.kt`) и смещённые попадания:** ряды сот рисуются с вертикальным шагом `0.8 × keyHeight` и перекрываются → каждый `Row` обязан иметь `.height(rowHitHeight).clipToBounds()` (обрезает и отрисовку, и hit-test, иначе тап по низу клавиши печатает букву из ряда ниже).
- **Устаревший API кривых в `Canvas` (`KesjeIcons.kt`):** в современном Compose `Path` использовать `quadraticTo(...)`, а не депрекейтнутый `quadraticBezierTo(...)` (даёт предупреждения компилятора).
- **Замыкания внутри `pointerInput` (`Toolbar.kt`, `Keys.kt`):** долгоживущие жестовые корутины захватывают старые лямбды при рекомпозиции → все коллбэки внутри `pointerInput` оборачивать в `rememberUpdatedState`.
- **Альбомная геометрия (`compactLandscape`):** сжатие высоты в ландшафте считается чистой функцией `resolveEffectiveGeometry` (`renderPrefs`) во время рендера → никогда не мутировать сохранённые портретные настройки пользователя при повороте экрана.

## Android IME, `InputConnection` и Unicode

- **`Backspace` и `Enter` в пустых полях, SMS-ячейках и терминалах (`TYPE_NULL`):** `deleteSurroundingText` там игнорируется → в пустом поле и в `TYPE_NULL` отправлять нажатия клавиш `KEYCODE_DEL` / `KEYCODE_ENTER` через `sendDownUpKeyEvents`.
- **Удаление символов и свайп-удаление слов:** никогда не резать строку по `Char` (`length - 1`) → использовать `BreakIterator` (`computeLastGraphemeLength`, `computeDeleteWordLength`), чтобы не рвать суррогатные пары и ZWJ-эмодзи на границе окна и не пробивать `\n`.
- **Составные правки (`", "`, автозамена, автозаглавные имена):** оборачивать в `beginBatchEdit()` / `endBatchEdit()`, иначе редакторы дёргают курсор и ломают историю Undo.
- **«Ложная латиница» (`ăĕçÿ → ӑӗҫӳ`) при вставке из буфера:** исправлять **только** в словах, где уже есть кириллица, и **никогда** в полях паролей (`isPasswordInputType`), чтобы не портить пароли и настоящий латинский/французский/чувашско-латинский текст (`garçon`, `Çăvaş`).
- **Поля `CAP_CHARACTERS` (ввод капсом подряд):** синхронизировать автозаглавную на каждый инкремент `editorContextVersion`, а не только при смене `Boolean` `false → true`, иначе заглавной окажется только первая буква.
- **`ViewTree*Owner` в `InputMethodService`:** в `KesjeImeService` привязывать `LifecycleOwner`, `ViewModelStoreOwner` и `SavedStateRegistryOwner` и к `window.window.decorView`, и к самому `ComposeView`. При переключении подтипа IME фильтровать промежуточные коллбэки системы через `pendingSubtypeLang`.

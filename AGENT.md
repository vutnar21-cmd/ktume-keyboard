# AGENT.md — инструкция для ИИ-агентов (Кӗсье Тӳме / кТӳме — Kĕsje Tüme / kTüme)

Этот документ содержит обязательные правила, архитектурные инварианты, команды сборки и контекст
проекта **«Кӗсье Тӳме / кТӳме (Kĕsje Tüme / kTüme)»** (`org.chuvash.ktume`). Любой ИИ-агент (Hermes, Claude Code, Codex,
Cursor, Gemini и др.) перед изменением кода обязан изучить этот файл.

---

## 1. О проекте

- **Название:** Кӗсье Тӳме (`app_name`) / кТӳме (`ime_name`), латиницей — Kĕsje Tüme / kTüme — кастомизируемая чувашская многоязычная клавиатура для Android.
- **Пакет и версия:** `org.chuvash.ktume`, `versionName = "1.3.0"` (`versionCode = 4`).
- **Стек:** Kotlin `2.4.21` (`org.jetbrains.kotlin.plugin.compose`, built-in Kotlin AGP 9), Jetpack Compose (Material 3,
  BOM `2024.09.00`), DataStore Preferences `1.1.1`, Android Gradle Plugin `9.5.0`, JUnit `4.13.2`.
- **SDK:** `minSdk = 26` (Android 8.0), `targetSdk = 37` (Android 17), `compileSdk = 37`.
- **Приватность:** полностью локальное приложение без доступа в сеть (в `AndroidManifest.xml`
  только разрешение `VIBRATE`), без аналитики и сторонних облачных SDK.
- **Языки:** Чувашский (`cv` — Чӑвашла), Русский (`ru` — Русская), Латиница (`lat` — Latina),
  Греческий (`el` — Ελληνικά).

---

## 2. Сборка и проверка (Windows / Git-Bash / PowerShell)

В пользовательском `~/.gradle/gradle.properties` прописан `org.gradle.java.home` на локальный Temurin JDK `21.0.7`
(`C\:/Program Files/COMSOL/COMSOL64/Multiphysics/java/win64/jre` — вынесен из репозиторного `gradle.properties`,
чтобы не ломать кроссплатформенную сборку на Linux / F-Droid). Для запуска wrapper экспортируй тот же JDK 21:

```bash
export JAVA_HOME="C:/Program Files/COMSOL/COMSOL64/Multiphysics/java/win64/jre"
./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon
```

В PowerShell:

```powershell
$env:JAVA_HOME = "C:/Program Files/COMSOL/COMSOL64/Multiphysics/java/win64/jre"
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --no-daemon
```

- **Важно про пути и Kotlin Daemon на Windows:** в `gradle.properties` установлен
  `kotlin.incremental=false`. Не удаляй его — он предотвращает `java.rmi.UnmarshalException`
  в Kotlin-демоне при пробелах в путях Windows.
- **Файл `gradle/gradle-daemon-jvm.properties` удалён намеренно.** Он задавал
  `toolchainVersion=25`, из-за чего lint падал с `> 25.0.3` в `JavaVersion.parse`, игнорируя
  `org.gradle.java.home`. Не создавай его заново (в том числе через `updateDaemonJvm`):
  JDK демона должен определяться только из `org.gradle.java.home`.
- **Выходной APK:** `app/build/outputs/apk/debug/app-debug.apk`.
- **Правило проверки:** любое изменение в `.kt` или `.xml` считается завершённым **только после
  успешного прохождения `./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon`
  без ошибок и предупреждений компилятора**.
- **Тесты (`app/src/test/java/org/chuvash/ktume/`):**
  - `ime/KeyboardLayoutsTest.kt` — раскладки, сохранность клавиш в Hex, выбор вариантов Long-Press,
    свайп вверх, конечный автомат `nextShiftStateOnTap`, удаление Unicode/ZWJ-графем и слов без
    разрыва суррогатных пар, слово и пробелы перед курсором, двойной пробел, пустая строка и правила
    автозаглавной Gboard, контекстный `Enter`, авто-Numpad, альбомная геометрия, слои символов и дефолты;
  - `data/MorphemeEngineTest.kt` — сингармонизм, выбор формы морфем по окончанию основы, склейка
    с сохранением заглавной буквы, фильтрация экспериментальных морфем;
  - `data/ChuvashNamesTest.kt` — экспериментальная капитализация имён собственных.
  Любая правка раскладок, морфем, слоёв символов, пресетов, Numpad или значений по умолчанию должна
  сопровождаться тестом (или обновлением существующего).

---

## 3. Карта исходного кода

Исходники в `app/src/main/java/org/chuvash/ktume/` разделены на три пакета без циклических
зависимостей (`ui.theme` → только ресурсы `R`; `data` → чистые модели, данные и DataStore без
зависимости от IME; `ime` → использует `data` и `ui.theme`). Ни один исходный файл не превышает ~480 строк:

### 3.1. Пакет `ui.theme`

- **`ui/theme/ThemePalettes.kt`**
  - Локальный шрифт `Comfortaa` (`res/font/comfortaa.ttf`) через `KesjeFontFamily` / `KesjeTypography`.
  - 7 цветовых палитр (`KesjePalettes`: 5 светлых и 2 тёмные) и конвертер `toColorScheme()` в Material 3.

### 3.2. Пакет `data`

- **`data/KeyboardPreferences.kt`**
  - Неизменяемая модель всех настроек клавиатуры, геометрии, флагов ввода, экспериментов и
    пользовательских рядов `customRowsByLang`.
- **`data/PreferencesRepository.kt`**
  - `PreferencesState` — общие `MutableStateFlow` (`preferences`, `shouldAutoCapitalize`,
    `inputSessionId`, `currentWord`, `enterKeyAction`, `preferNumericMode`), которые читают сервис и
    экран настроек без связи через `companion object` сервиса.
  - `PreferencesRepository` — чтение и запись в Jetpack DataStore (`kesje_keyboard_prefs_v3`),
    поддерживает коалесцирующий `debounceDisk = true` (`220 мс`) для ползунков.
  - **Важно:** меняя дефолт настройки, которая уже могла сохраниться на устройстве, подними
    суффикс версии ключа `preferencesKey` (например, `_v5` → `_v6`, при необходимости с чтением
    старого ключа как fallback), иначе новое значение по умолчанию не применится.
  - Асинхронная загрузка защищена флагами `loaded: StateFlow<Boolean>` (выставляется в `finally`)
    и `hasPendingUserUpdate`: правка настроек до завершения чтения не затирается фоновой загрузкой
    и не блокирует стартовый гейт.
- **`data/KeyboardLayouts.kt`**
  - `KeyboardLang` (`CHUVASH`, `RUSSIAN`, `LATINA`, `GREEK`) и `ChuvashLayoutPreset`
    (`BALANCED_26`, `HEX_4ROW`, `FULL_28`).
  - Таблицы Long-Press: `chuvashLongPress`, `russianLongPress`, `latinaLongPress`,
    `greekLongPress`, `digitAndSymbolLongPress` (включая символы слоёв `?123` / `=\<`).
  - Слои: `numberRow` (`1–0`), `symbolsLayer1` (`10 + 10 + 7`), `symbolsLayer2` (`10 + 10 + 7`),
    `numpadRows` (3 колонки × 4 ряда).
  - `getHex4RowLayout` — 4-рядная упаковка сот без потери символов;
    `getDisplayedLongPressVariants` / `computeLongPressSelectedIndex` / `initialLongPressVariantIndex` /
    `primaryLongPressVariant` / `isSwipeUpGesture` — выбор вариантов Long-Press и конус свайпа вверх.
  - Делегирует категории эмодзи, текстовую логику редактора и морфемные подсказки модулям
    `EmojiCatalog`, `EditorTextLogic` и `MorphemeEngine`.
- **`data/EditorTextLogic.kt`**
  - `EnterKeyAction`, `EffectiveKeyboardGeometry` и чистые функции работы с текстом и контекстом поля:
    `normalizeChuvashUnicode`, `computeLastGraphemeLength`, `dropLastGrapheme`,
    `extractCurrentWordBeforeCursor`, `extractMorphemeWordBeforeCursor`,
    `computeTrailingHorizontalSpacesAfterWord` (только после букв, не съедает пробел после пунктуации),
    `computeDeleteWordLength` (без пробития `\n` и без разрезания суррогатной пары на границе окна),
    `canReplaceSpaceWithCommaBeforeCursor`, `isCursorOnEmptyLine`, `resolveEditorCapFlags`,
    `shouldAutoCapitalize`, `resolveEnterKeyAction`, `prefersNumericPad`, `resolveEffectiveGeometry`.
- **`data/EmojiCatalog.kt`**
  - `EmojiCategory` и локальный каталог смайликов `EmojiCatalog.categories` по 6 категориям.
- **`data/ChuvashMorphemes.kt` и `data/MorphemeEngine.kt`**
  - Файл-словарь чувашских частиц, числа, 8 падежей, форм принадлежности и словообразовательных
    аффиксов (мягкий/твёрдый ряд, правила `AttachRule`, флаг `experimental`).
  - Движок определения гармонии по последней гласной (`harmonyOf`), подбора формы (`pickForm`),
    склейки с сохранением заглавной (`attach`) и выдачи чипов для ленты (`suggestions`).
- **`data/ChuvashNames.kt`**
  - Базовый словарь имён собственных (~95) и `capitalizeIfName` для экспериментальной автозаглавной.

### 3.3. Пакет `ime`

- **`ime/KesjeImeService.kt`**
  - `InputMethodService` с `LifecycleOwner`, `ViewModelStoreOwner`, `SavedStateRegistryOwner`
    (деревья `ViewTree*Owner` привязаны и к `decorView`, и к самому `ComposeView`).
  - Отслеживает контекст автозаглавной по образцу Gboard (`PreferencesState.shouldAutoCapitalize`
    через `resolveEditorCapFlags` + `shouldAutoCapitalize`), смену поля ввода (`inputSessionId`)
    и слово перед курсором (`PreferencesState.currentWord`) в `onStartInputView` / `onUpdateSelection`.
  - Удаление через `computeLastGraphemeLength` (целые кластеры Unicode), единый хелпер звука
    `playKeypressSound` (`AudioManager.FX_KEYPRESS_STANDARD` с учётом `STREAM_MUSIC` и
    `soundVolumePercent`), кэшируемый `Vibrator`, экспериментальная капитализация имён перед пробелом.
- **`ime/KeyboardScreen.kt` и `ime/KeyboardRows.kt`**
  - `KeyboardScreen` — корневой UI-компонент клавиатуры (строго read-only по отношению к
    `KeyboardPreferences`), общий для сервиса и превью настроек; режимы `KeyboardMode`
    (`LETTERS`, `SYMBOLS_1`, `SYMBOLS_2`, `NUMPAD`, `EMOJI_PAGE`), регистр `ShiftState` с единым
    хелпером `nextShiftStateOnTap` (`DOUBLE_TAP_WINDOW_MS = 420L`) и `autoShiftArmed`.
  - `KeyboardRows` — `NumberRowStrip`, `AlphaSymbolRows`, `KeyboardBottomBar` и попап `GlobeLanguageMenuPopup`.
- **`ime/Keys.kt` и `ime/FunctionalKeys.kt`**
  - `Keys.kt` — общий модификатор поверхности прямоугольных и сотовых клавиш `rememberKeySurfaceModifier`,
    `KeyButton` (бесшовные хитбоксы, Long-Press с ведением пальца, быстрый свайп вверх с мини-превью
    `KeyPressPreviewPopup` для `variants[0]`, микро-метки, свайп влево на точке, попап `LongPressVariantsPopup`).
  - `FunctionalKeys.kt` — `BackspaceKey` (автоповтор + свайп удаления слова), `SpecialKey`,
    `SpacebarKey` (двойной пробел и горизонтальный свайп перемещения курсора).
  - Клавиши имеют семантику (`Role.Button`, `contentDescription`, `onClick`) и игнорируют
    отменённый жест (`ACTION_CANCEL`).
- **`ime/Toolbar.kt`**
  - `TopToolbarStrip` — строго однорядная верхняя полоса (`36 dp`, без второго ряда и скачка высоты):
    справа три кнопки (`📋`, `😊`, `⚙️`), а слева контекстно один из 4 режимов:
    1) встроенная капсула буфера обмена (по нажатию `📋`, вставка тапом с неявной нормализацией `ăĕçÿ → ӑӗҫӳ`);
    2) на странице смайликов (`isEmojiPage`) — 6 компактных вкладок категорий смайликов (`🕒 😊 👍 ❤️ 🌿 ☕`);
    3) в `Чӑвашла` (при включённых частицах) — лента сингармонических частиц и аффиксов;
    4) в остальных случаях — стрелки курсора `←` / `→` (с автоповтором) и кнопка `Вставить`.
    Все коллбэки внутри `pointerInput` обёрнуты в `rememberUpdatedState`.
- **`ime/HoneycombHexGrid.kt`** — 4-рядная паркетная сотовая сетка с `clipToBounds` на каждом ряду и `onGestureTick`.
- **`ime/NumpadGridView.kt`** — телефонная сетка 3 × 4 с колонкой действий `⌫ / − / + / ↵`.
- **`ime/EmojiPickerPage.kt`** — страница смайликов с нулевым скачком высоты (`computeKeyboardBodyHeightDp`),
  горизонтальным пейджером `HorizontalPager` по 6 категориям (`EmojiCatalog`, включая `🕒 Недавние` из
  `recentEmojis` в DataStore), чистой 8-колоночной сеткой без рамок с «каплей» превью и нижним рядом
  `АБВ · Пробел · ⌫ · ↵` (контекстный `Enter`).
- **`ime/KesjeIcons.kt`**
  - Векторные иконки на `Canvas` вместо текстовых символов.
  - **Важно:** в Compose `Path` используй `quadraticTo(...)`, а не устаревший `quadraticBezierTo(...)`.
- **`ime/SettingsActivity.kt`, `ime/SettingsSections.kt`, `ime/SettingsPreviewBench.kt`, `ime/SettingsComponents.kt`, `ime/SettingsRowEditor.kt`**
  - `SettingsActivity` — каркас студии настроек (шапка с переключателем темы, вкладки, прокрутка, диалог сброса).
  - `SettingsSections` — `SettingsTab` и 5 секций («Раскладка», «Оформление», «Ввод», «Эксперименты», «Система»).
  - `SettingsPreviewBench` — живое тестовое поле `SettingsTestFieldCard` и закреплённый снизу стенд `SettingsKeyboardPreview`.
  - `SettingsComponents` и `SettingsRowEditor` — переиспользуемые карточки/тумблеры/ползунки и интерактивный конструктор рядов.

---

## 4. Строгие дизайн- и UX-инварианты (не нарушать)

1. **Эталонная геометрия и тема по умолчанию (`KeyboardPreferences`):**
   - Палитра (`paletteId`): **`"obsidian_forest"`** (*Ночной Лес OLED*)
   - Высота клавиш (`keyHeightDp`): **`48f`**
   - Скругление (`keyCornerRadiusDp`): **`14f`**
   - Горизонтальный зазор (`keyGapHDp`): **`3f`**
   - Вертикальный зазор (`keyGapVDp`): **`7f`** (воздух между рядами без сжатия ширины клавиш;
     Hex-режим использует только `keyGapHDp`)
   - Шрифт (`fontSizeSp`): **`26f`**
   - Задержка Long-Press (`longPressDelayMs`): **`210`**
   - Нижний отступ (`bottomInsetDp`): **`20f`**
   - `showNumberRow` — **`true`**, `showParticlesStrip` — **`true`**, `swipeUpFirstVariant` — **`true`**,
     `showKeyPressPopup` — **`true`**, `compactLandscape` — **`true`**,
     `showSubLabels` — **`false`**, `experimentalCapitalizeNames` — **`false`**.
2. **Чувашская раскладка по умолчанию (`BALANCED_26`):** строго `10 + 9 + 7 = 26` букв.
   - Ряд 1 (10): `й, ӑ, у, к, е, н, ӗ, ш, х, ӳ`
   - Ряд 2 (9): `ф, ы, в, а, п, р, о, л, ҫ`
   - Ряд 3 (7 + Shift + ⌫): `я, ч, с, м, и, т, ю`
3. **Верхняя панель и низ без лишних кнопок.** Hex и перестановка клавиш — только в настройках.
   Словарные подсказки слов в верхнем баре отключены (в ленте только частицы и аффиксы). Кастомного
   нижнего ряда со «Скрыть клавиатуру» нет — он дублирует системную навигацию.
4. **Жесты:** короткий свайп вверх по любой клавише с вариантами Long-Press (`swipeUpFirstVariant`,
   включён по умолчанию во вкладке «Ввод») показывает мини-превью и при отпускании вставляет
   1-й по частотности вариант (`variants[0]`: `к→г`, `т→д`, `п→б`, `ш→ж`, `.→-`); возврат пальца
   вниз к клавише до отпускания отменяет свайп и вводит основную букву. Полный список вариантов —
   Long-Press с ведением пальца; дефис — отдельная клавиша и свайп влево от точки; двойной пробел
   ставит `", "` (с проверкой контекста: одиночный пробел после буквы/цифры, без выделения).
5. **Слои символов и Numpad — по образцу Gboard:**
   - **Цифры `1–0` — общий верхний ряд** (`KeyboardLayouts.numberRow`): показывается и над
     буквами, и над страницами символов, поэтому высота клавиатуры не меняется при переключении
     `?123` / `=\<`. На страницах символов цифр быть не должно — иначе ряд дублируется.
   - `?123`: `% ^ ~ ` | \ { } [ ]` / `@ # $ _ & - + ( ) /` / `* " ' : ; ! ?`
     (+ `=\<` слева и `⌫` справа).
   - `=\<`: `~ \` | • √ π ÷ × ¶ ∆` / `€ £ ¥ ₽ © ® ™ § № =` / `… — – ‹ › « »`.
   - `1234`: телефонная сетка 3 × 4 с колонкой `⌫ / − / + / ↵`, снизу `?123 · пробел · АБВ`.
   - **Нижний ряд:** `?123 · , · глобус · пробел · - · . · ↵` (запятая перед глобусом).
     На клавише пробела — только название раскладки, без обрамляющих `‹ ›`.
6. **Настройки — вкладками.** 5 вкладок («Раскладка», «Оформление», «Ввод», «Эксперименты»,
   «Система»): контент прокручивается, клавиатура закреплена снизу и не дублируется в тексте.
   Новые настройки добавляй в подходящую вкладку (нестабильные фичи — в «Эксперименты», выключенными
   по умолчанию).
7. **Никаких правок «на глаз».** UI проверяется по коду и сборке; при сомнении в геометрии
   добавляй unit-тест на раскладочные функции, а не магические отступы.
8. **Гейт загрузки настроек.** `PreferencesRepository.loaded` становится `true` только после
   чтения DataStore; до этого `SettingsActivity` показывает индикатор и не принимает правок.
   Без этого снимок значений по умолчанию перезаписывает сохранённые настройки и раскладки.
9. **Соты: зона нажатия равна высоте ряда.** Ряды рисуются с шагом `0.8 × keyHeight` и
   перекрываются, поэтому каждый `Row` ограничен `.height(rowHitHeight).clipToBounds()`.
   `clipToBounds` обрезает и отрисовку, и hit-test — иначе тап по нижней части клавиши
   печатает букву из ряда ниже. Не убирай `clipToBounds` при рефакторинге сот.
10. **Контекст слова и автозаглавной — только из редактора (как в Gboard).** `KeyboardScreen` не
    ведёт собственный буфер слова: единственный источник — `editorCurrentWord`. Автозаглавная
    (`resolveEditorCapFlags` + `shouldAutoCapitalize` + `autoShiftArmed`) включается **только если
    само поле запрашивает `TYPE_TEXT_FLAG_CAP_*`** (не в URL, email, паролях, поиске, терминале),
    когда справа на текущей строке нет символов и строка пуста либо перед курсором конец предложения
    **с пробелом** (`". "`, `"! "`, `"? "`). Нажатие `.` без пробела (`site.ru`, `user.name`, `./cmd`)
    заглавную не включает.
11. **Преселект Long-Press совпадает с микро-меткой и сохраняет частотность.** На левой половине
    (`colFraction < 0.5f`) варианты идут слева направо `[v0, v1, ...]`, выбор начинается с `0` и
    идёт вправо. На всей правой половине (`colFraction >= 0.5f`, `isRightSideKey`) варианты
    раскладываются **справа налево** `[..., v1, v0]`, попап прижат `TopEnd`, а выбор начинается с
    крайней правой ячейки (`v0` — самый частый символ) и уходит влево. Микро-метка в углу клавиши
    всегда показывает `v0`.
12. **Единая однорядная верхняя полоса и буфер обмена.** Верхняя панель всегда занимает ровно один
    ряд (`36 dp`), чтобы клавиатура не прыгала по высоте: нажатие `📋` раскрывает капсулу буфера
    в левой части **этой же строки** (`InlineClipboardCapsule`), тап по тексту сразу вставляет содержимое
    (отдельной кнопки «Вставить» внутри капсулы нет). Когда лента чувашских частиц не показывается,
    левую часть занимают стрелки курсора `←` / `→` и быстрая кнопка `Вставить`. Нормализация
    «ложной латиницы» (`ăĕçÿ → ӑӗҫӳ`) выполняется неявно при вставке; системный буфер не перезаписывается.
13. **Бесшовные хитбоксы (`gap / 2`) и контекстное поведение поля.** В прямоугольной раскладке
    каждая клавиша владеет `hGap / 2` и `vGap / 2` вокруг видимого прямоугольника — мёртвых щелей нет
    (в режиме сот `HoneycombHexGrid` отступы `0 dp`). При переходе в новое поле (включая `restartInput`
    со сменой сигнатуры `EditorInfo` в Jetpack Compose) клавиатура сбрасывает застрявшие режимы,
    автоматически открывает `NUMPAD` на `TYPE_CLASS_NUMBER` / `TYPE_CLASS_PHONE` и рисует контекстную
    иконку `Enter` (`SEARCH`, `SEND`, `GO`, `NEXT`, `DONE`, `NEWLINE`). Удержание `🌐` открывает меню
    раскладок и выбора системного метода ввода (`showInputMethodPicker`).

---

## 5. История и задачи на развитие

### Ключевые вехи

- **Итерация 5:** переименование `LATIN` → `LATINA`, разделение `app_name` / `ime_name`, слои
  символов и Numpad в стиле Gboard, вкладки настроек с закреплённой клавиатурой, `clipToBounds`
  в сотах, гейт `PreferencesRepository.loaded`, удаление локального `typedWordBuffer`.
- **Пост-итерация 5 (задачи 002, 006, 007, 010–015):**
  - развязан цикл `ui.theme ⇄ ime`, выделен пакет `data/`, разбиты `KeyboardScreen` и `SettingsActivity`;
  - добавлены словарь чувашских морфем (`ChuvashMorphemes`) и движок сингармонизма (`MorphemeEngine`);
  - добавлена вкладка «Эксперименты» и экспериментальная капитализация имён (`ChuvashNames`);
  - автозаглавная ограничена реально пустой строкой (`isCursorOnEmptyLine` + `getCursorCapsMode`);
  - Long-Press правых клавиш разворачивает выбор справа налево с синхронной микро-меткой;
  - вставка из буфера переведена на тап по тексту с неявной нормализацией `ăĕçÿ → ӑӗҫӳ`;
  - зазор разделён на `keyGapHDp = 3f` и `keyGapVDp = 7f` с двумя ползунками и миграцией `_v5 → _v6`.

### Открытые задачи

Все задачи на развитие живут в каталоге [`tasks/`](tasks/README.md) — один файл на задачу,
плюс индекс со статусами и процентом готовности.

**Правила работы с задачами (обязательны для агента):**

1. **Текст задачи пишет только владелец.** Блок между маркерами `ЗОНА ВЛАДЕЛЬЦА` и
   `КОНЕЦ ЗОНЫ ВЛАДЕЛЬЦА` не редактируется: ни формулировка, ни критерии, ни приоритет.
2. **Агент только дописывает отчёты** в секцию `## Отчёты` в конце файла задачи:
   дата, готовность в процентах, что сделано, что осталось, чем проверено (`assembleDebug`,
   тесты, lint, устройство). Предыдущие отчёты не удаляются и не переписываются.
3. **Статус и готовность** ведутся только в таблице индекса `tasks/README.md`.
4. **Новую задачу** владелец создаёт копией `tasks/_template.md` под следующим свободным номером.
5. **По завершении** ставится статус `review`, а не `done`: приёмка всегда за владельцем.
6. **Если задача требует решения владельца** (например, выбор названия или списка морфем) —
   статус `blocked` и вопрос в отчёте, а не догадка.

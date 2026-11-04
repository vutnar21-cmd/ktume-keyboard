# AGENT.md — инструкция для ИИ-агентов (Кӗсье Тӳме / кТӳме — Kĕsje Tüme / kTüme)

Этот документ содержит обязательные правила, архитектурные инварианты, команды сборки и контекст
проекта **«Кӗсье Тӳме / кТӳме (Kĕsje Tüme / kTüme)»** (`org.chuvash.ktume`). Любой ИИ-агент (Hermes, Claude Code, Codex,
Cursor, Gemini и др.) перед изменением кода обязан изучить этот файл.

---

## 1. О проекте

- **Название:** Кӗсье Тӳме (`app_name`) / кТӳме (`ime_name`), латиницей — Kĕsje Tüme / kTüme — кастомизируемая чувашская многоязычная клавиатура для Android.
- **Пакет и версия:** `org.chuvash.ktume`, `versionName = "1.4.0"` (`versionCode = 5`).
- **Стек:** Kotlin `2.4.21` (`org.jetbrains.kotlin.plugin.compose`, built-in Kotlin AGP 9), Jetpack Compose (Material 3,
  BOM `2026.09.00`), DataStore Preferences `1.3.0`, Lifecycle `2.11.0`, Android Gradle Plugin `9.5.0`,
  Gradle `9.8.2`, JUnit `4.13.2`.
- **Распространение:** GitHub Releases + Obtainium (подпись сверяется по отпечатку из README); метаданные
  `fastlane/` сохранены для Google Play / RuStore. F-Droid не используется.
- **SDK:** `minSdk = 26` (Android 8.0), `targetSdk = 37` (Android 17), `compileSdk = 37`.
- **Приватность:** полностью локальное приложение без доступа в сеть (в `AndroidManifest.xml`
  нет ни одного разрешения для пользователя; отклик клавиш — `performHapticFeedback`), без аналитики
  и сторонних облачных SDK.
- **Языки:** Чувашский (`cv` — Чӑвашла), Русский (`ru` — Русская), Латиница (`lat` — Latina),
  Греческий (`el` — Ελληνικά).

---

## 2. Сборка и проверка (Windows / Git-Bash / PowerShell)

В пользовательском `~/.gradle/gradle.properties` прописан `org.gradle.java.home` на локальный Temurin JDK `21.0.7`
(`C\:/Program Files/COMSOL/COMSOL64/Multiphysics/java/win64/jre` — вынесен из репозиторного `gradle.properties`,
чтобы не ломать сборку на других машинах и в CI). Для запуска wrapper экспортируй тот же JDK 21:

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
    свайп вверх, конечные автоматы `nextShiftStateOnTap` и `resolveAutoShift`, удаление Unicode/ZWJ-графем
    и слов без разрыва суррогатных пар, слово перед курсором, двойной пробел, пустая строка и правила
    автозаглавной Gboard, «ложная латиница» при вставке, поля паролей, подтипы IME, контекстный `Enter`,
    авто-Numpad, альбомная геометрия, слои символов и дефолты;
  - `data/ChuvashNamesTest.kt` — экспериментальная капитализация имён собственных;
  - `data/autocorrect/AutocorrectorTest.kt` — экспериментальная автозамена: поиск кандидатов в
    `WordTrie`, цены правок `TypoCostModel`, правила регистра, полей и токенов, защита известных слов,
    откат `⌫` (`AutocorrectSession`);
  - `data/autocorrect/AutocorrectDictionaryTest.kt` — автозамена на реальных словарях `assets/dict/*.tsv`:
    типичные опечатки исправляются, частые чувашские и русские слова не трогаются, поиск укладывается
    в бюджет времени, отчёт о ложных исправлениях на редких формах (выборка из `scripts/dictionaries/.cache/`,
    если она есть).
  Любая правка раскладок, слоёв символов, пресетов, Numpad, автозаглавной, автозамены или значений по
  умолчанию должна сопровождаться тестом (или обновлением существующего).

---

## 3. Карта исходного кода

Исходники в `app/src/main/java/org/chuvash/ktume/` разделены на три пакета без циклических
зависимостей (`ui.theme` → только ресурсы `R`; `data` → чистые модели, данные и DataStore без
зависимости от IME; `ime` → использует `data` и `ui.theme`). Держи файлы в пределах ~500 строк; самый
крупный — `KeyboardScreen.kt` — кандидат на следующее разбиение:

### 3.1. Пакет `ui.theme`

- **`ui/theme/ThemePalettes.kt`**
  - Локальный шрифт Kesje Rounded (`res/font/kesje_rounded.ttf`) через `KesjeFontFamily` / `KesjeTypography`:
    это Comfortaa (OFL, Reserved Font Name — поэтому другое имя) с одной правкой — строчная «т» в русской
    форме вместо болгарской «m». Собирается скриптом `scripts/fonts/patch_comfortaa_te.py`; Λ-образную «л»
    и остальные буквы не трогать — это осознанный выбор владельца.
  - 7 цветовых палитр (`KesjePalettes`: 5 светлых и 2 тёмные) и конвертер `toColorScheme()` в Material 3.

### 3.2. Пакет `data`

- **`data/KeyboardPreferences.kt`**
  - Неизменяемая модель всех настроек клавиатуры, геометрии, флагов ввода, экспериментов и
    пользовательских рядов `customRowsByLang`.
- **`data/PreferencesRepository.kt`**
  - `PreferencesState` — общие `MutableStateFlow` (`preferences`, `shouldAutoCapitalize`,
    `inputSessionId`, `editorContextVersion`, `enterKeyAction`, `preferNumericMode`, `isPasswordField`,
    `activeLang`), которые читают сервис и экран настроек без связи через `companion object` сервиса.
  - `saveActiveLang` пишет выбранную раскладку отдельным ключом `active_lang_v1`, не трогая остальные
    настройки (в `update` она не входит).
  - `PreferencesRepository` — чтение и запись в Jetpack DataStore (`kesje_keyboard_prefs_v3`),
    поддерживает коалесцирующий `debounceDisk = true` (`220 мс`) для ползунков.
  - **Важно:** меняя дефолт настройки, которая уже могла сохраниться на устройстве, подними
    суффикс версии ключа `preferencesKey` (например, `_v5` → `_v6`, при необходимости с чтением
    старого ключа как fallback), иначе новое значение по умолчанию не применится.
  - Асинхронная загрузка защищена флагами `loaded: StateFlow<Boolean>` (выставляется в `finally`)
    и `hasPendingUserUpdate`: правка настроек до завершения чтения не затирается фоновой загрузкой
    и не блокирует стартовый гейт.
- **`data/KeyboardLayouts.kt`**
  - `KeyboardLang` (`CHUVASH`, `RUSSIAN`, `LATINA`, `GREEK`) с `subtypeLanguage` и `fromSubtypeTag`
    (связь с подтипами `res/xml/method.xml`) и `ChuvashLayoutPreset` (`BALANCED_26`, `HEX_4ROW`, `FULL_28`).
  - Таблицы Long-Press: `chuvashLongPress`, `russianLongPress`, `latinaLongPress`,
    `greekLongPress`, `digitAndSymbolLongPress` (включая символы слоёв `?123` / `=\<`).
  - Слои: `numberRow` (`1–0`), `symbolsLayer1` (`10 + 10 + 7`), `symbolsLayer2` (`10 + 10 + 7`),
    `numpadRows` (3 колонки × 4 ряда).
  - `getHex4RowLayout` — 4-рядная упаковка сот без потери символов;
    `getDisplayedLongPressVariants` / `computeLongPressSelectedIndex` / `initialLongPressVariantIndex` /
    `primaryLongPressVariant` / `isSwipeUpGesture` — выбор вариантов Long-Press и конус свайпа вверх.
  - Делегирует категории эмодзи и текстовую логику редактора модулям `EmojiCatalog` и `EditorTextLogic`.
- **`data/EditorTextLogic.kt`**
  - `EnterKeyAction`, `EffectiveKeyboardGeometry` и чистые функции работы с текстом и контекстом поля:
    `normalizeChuvashUnicode` (только в словах с кириллицей), `isPasswordInputType`,
    `computeLastGraphemeLength`, `dropLastGrapheme`, `extractCurrentWordBeforeCursor`,
    `computeDeleteWordLength` (без пробития `\n` и без разрезания суррогатной пары на границе окна),
    `canReplaceSpaceWithCommaBeforeCursor`, `isCursorOnEmptyLine`, `resolveEditorCapFlags`,
    `shouldAutoCapitalize`, `resolveEnterKeyAction`, `prefersNumericPad`, `resolveEffectiveGeometry`.
- **`data/EmojiCatalog.kt`**
  - `EmojiCategory` и локальный каталог смайликов `EmojiCatalog.categories` по 6 категориям.
- **`data/ChuvashNames.kt`**
  - Базовый словарь имён собственных (~95) и `capitalizeIfName` для экспериментальной автозаглавной.
- **`data/autocorrect/`** — экспериментальная автозамена опечаток (задача 013), чистая логика без UI:
  - `WordTrie.kt` — компактное префиксное дерево в плоских массивах (`fromTsv`), поиск кандидатов со
    взвешенным расстоянием Дамерау–Левенштейна и отсечением ветвей по допустимой цене.
  - `TypoCostModel.kt` — цены правок: обычная `FULL = 2`, «дешёвая» `CHEAP = 1` — соседняя клавиша
    текущей раскладки (`fromRows`), пропущенный надстрочный знак (`а/ӑ`, `е/ӗ/ё/э`, `с/ҫ`, `у/ӳ`, `и/й`,
    `ь/ъ/ы`, `ш/щ`), сдвоенная или потерянная двойная буква; надбавка за правку первой буквы
    (`firstLetterExtra = 1`).
  - `Autocorrector.kt` — решение «исправлять ли и на что»: правила слова и регистра, допустимая цена по
    длине (`maxCostFor`), оценка `ln(частота / всего) − 2,3 · цена` + бонус словарю раскладки
    (`ln(10)`), пороги выгоды над гипотезой редкого слова (`minGain = 2,0`) и разрыва между кандидатами
    (`minMargin = 1,5`), фильтр обсценной лексики; политика полей `isFieldAllowed`, `isLearningAllowed`,
    разделители `isSeparator`, `extractCorrectableWord`.
  - `AutocorrectSession.kt` — состояние одного поля: ожидающий откат первым `⌫` и отклонённые слова.
  - `AutocorrectEngine.kt` — фоновая загрузка `assets/dict/cv.tsv` / `ru.tsv` только при включённом
    эксперименте (`sync`), выгрузка при выключении, единая точка `findCorrection` для сервиса и
    тестового поля настроек.
  - Словари `app/src/main/assets/dict/*.tsv` собираются скриптом `scripts/dictionaries/build_dictionaries.py`
    (нужны `pyarrow` и `spylls`; кэш исходников `scripts/dictionaries/.cache/` в `.gitignore`).

### 3.3. Пакет `ime`

- **`ime/KesjeImeService.kt`**
  - `InputMethodService` с `LifecycleOwner`, `ViewModelStoreOwner`, `SavedStateRegistryOwner`
    (деревья `ViewTree*Owner` привязаны и к `decorView`, и к самому `ComposeView`).
  - Отслеживает контекст автозаглавной по образцу Gboard (`PreferencesState.shouldAutoCapitalize`
    через `resolveEditorCapFlags` + `shouldAutoCapitalize`, счётчик `editorContextVersion`), смену поля
    ввода (`inputSessionId`) и поле пароля (`isPasswordField`) в `onStartInputView` / `onUpdateSelection`.
  - Удаление через `computeLastGraphemeLength` (целые кластеры Unicode); в пустом поле и в полях `TYPE_NULL`
    `⌫` и перевод строки уходят как `KEYCODE_DEL` / `KEYCODE_ENTER` (`sendDownUpKeyEvents`). Составные
    правки (`", "`, автозаглавные имена) — внутри `beginBatchEdit` / `endBatchEdit`.
  - Раскладка: `saveActiveLang` + синхронизация с системным подтипом (`switchInputMethod` на API 28+,
    `onCurrentInputMethodSubtypeChanged` с фильтром `pendingSubtypeLang` от промежуточных ответов).
  - Единый хелпер звука `playKeypressSound` (`AudioManager.FX_KEYPRESS_STANDARD` с учётом `STREAM_MUSIC`
    и `soundVolumePercent`), тактильный отклик через `performHapticFeedback` (`KEYBOARD_TAP`, для жестов `CLOCK_TICK`), экспериментальная капитализация имён перед пробелом.
  - Экспериментальная автозамена (`AutocorrectSession` + `AutocorrectEngine`): исправление слова перед
    разделителем в `onCommitText` и перед переводом строки в `onEnter` (одной batch-правкой), откат
    первым `⌫` в `onBackspace` с пополнением личного словаря (если включено и поле не в инкогнито);
    сброс состояния при смене поля и выделении текста.
- **`ime/KeyboardScreen.kt` и `ime/KeyboardRows.kt`**
  - `KeyboardScreen` — корневой UI-компонент клавиатуры (строго read-only по отношению к
    `KeyboardPreferences`), общий для сервиса и превью настроек; режимы `KeyboardMode`
    (`LETTERS`, `SYMBOLS_1`, `SYMBOLS_2`, `NUMPAD`, `EMOJI_PAGE`), регистр `ShiftState` с чистыми
    хелперами `nextShiftStateOnTap` (`DOUBLE_TAP_WINDOW_MS = 420L`) и `resolveAutoShift` (`AutoShift`:
    состояние + флаг «взведено автозаглавной»).
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
    справа три кнопки (`📋`, `😊`, `⚙️`), а слева контекстно один из 3 режимов:
    1) встроенная капсула буфера обмена (по нажатию `📋`, вставка тапом);
    2) на странице смайликов (`isEmojiPage`) — 6 компактных вкладок категорий смайликов (`🕒 😊 👍 ❤️ 🌿 ☕`);
    3) в остальных случаях — стрелки курсора `←` / `→` с автоповтором (настройка `showCursorArrows`).
    Отдельной кнопки «Вставить» нет: вставка — через `📋`.
    Вставка идёт через единую точку `pasteFromClipboard`: `normalizeChuvashUnicode`, кроме полей паролей.
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
- **`ime/SettingsActivity.kt`, `ime/SettingsSections.kt`, `ime/SettingsPreviewBench.kt`, `ime/SettingsComponents.kt`, `ime/SettingsRowEditor.kt`, `ime/SettingsAutocorrect.kt`**
  - `SettingsActivity` — каркас студии настроек (шапка с переключателем темы, вкладки, прокрутка, диалог сброса).
  - `SettingsSections` — `SettingsTab` и 5 секций («Раскладка», «Оформление», «Ввод», «Эксперименты», «Система»).
  - `SettingsPreviewBench` — живое тестовое поле `SettingsTestFieldCard` (с автозаменой и откатом `⌫`,
    когда эксперимент включён) и закреплённый снизу стенд `SettingsKeyboardPreview`.
  - `SettingsAutocorrect` — блок `AutocorrectSettingsBlock` на вкладке «Эксперименты»: тумблер автозамены,
    языки словарей, «Запоминать отменённые слова», список слов личного словаря с удалением и очистка.
  - `SettingsComponents` и `SettingsRowEditor` — переиспользуемые карточки/тумблеры/ползунки и интерактивный конструктор рядов.

---

## 4. Строгие дизайн- и UX-инварианты (не нарушать)

1. **Эталонная геометрия и тема по умолчанию (`KeyboardPreferences`):**
   - Палитра (`paletteId`): **`"obsidian_forest"`** (*Ночной Лес OLED*)
   - Высота клавиш (`keyHeightDp`): **`50f`**
   - Скругление (`keyCornerRadiusDp`): **`14f`**
   - Горизонтальный зазор (`keyGapHDp`): **`3f`**
   - Вертикальный зазор (`keyGapVDp`): **`7f`** (воздух между рядами без сжатия ширины клавиш;
     Hex-режим использует только `keyGapHDp`)
   - Шрифт (`fontSizeSp`): **`26f`**
   - Задержка Long-Press (`longPressDelayMs`): **`300`** (как в Gboard)
   - Нижний отступ (`bottomInsetDp`): **`20f`**
   - `showNumberRow` — **`true`**, `swipeUpFirstVariant` — **`true`**, `showCursorArrows` — **`true`**,
     `showHyphenKey` — **`false`** (шире пробел; дефис — свайп влево от точки),
     `showKeyPressPopup` — **`true`**, `compactLandscape` — **`true`**,
     `showSubLabels` — **`false`**, `experimentalCapitalizeNames` — **`false`**,
     `experimentalAutocorrect` — **`false`**.
2. **Чувашская раскладка по умолчанию (`BALANCED_26`):** строго `10 + 9 + 7 = 26` букв.
   - Ряд 1 (10): `й, ӑ, у, к, е, н, ӗ, ш, х, ӳ`
   - Ряд 2 (9): `ф, ы, в, а, п, р, о, л, ҫ`
   - Ряд 3 (7 + Shift + ⌫): `я, ч, с, м, и, т, ю`
3. **Верхняя панель и низ без лишних кнопок.** Hex и перестановка клавиш — только в настройках.
   Ленты подсказок, чувашских частиц и аффиксов нет: она удалена по решению владельца (2026-11-02),
   не возвращай её без новой задачи. Кастомного нижнего ряда со «Скрыть клавиатуру» нет — он дублирует
   системную навигацию.
4. **Жесты:** короткий свайп вверх по любой клавише с вариантами Long-Press (`swipeUpFirstVariant`,
   включён по умолчанию во вкладке «Ввод») показывает мини-превью и при отпускании вставляет
   1-й по частотности вариант (`variants[0]`: `к→г`, `т→д`, `п→б`, `ш→ж`, `.→-`); возврат пальца
   вниз к клавише до отпускания отменяет свайп и вводит основную букву. Полный список вариантов —
   Long-Press с ведением пальца; дефис — свайп влево от точки (отдельная клавиша — опция); двойной пробел
   ставит `", "` (с проверкой контекста: одиночный пробел после буквы/цифры, без выделения).
5. **Слои символов и Numpad — по образцу Gboard:**
   - **Цифры `1–0` — общий верхний ряд** (`KeyboardLayouts.numberRow`): показывается и над
     буквами, и над страницами символов, поэтому высота клавиатуры не меняется при переключении
     `?123` / `=\<`. На страницах символов цифр быть не должно — иначе ряд дублируется.
   - `?123`: `% ^ ~ ` | \ { } [ ]` / `@ # $ _ & - + ( ) /` / `* " ' : ; ! ?`
     (+ `=\<` слева и `⌫` справа).
   - `=\<`: `~ \` | • √ π ÷ × ¶ ∆` / `€ £ ¥ ₽ © ® ™ § № =` / `… — – ‹ › « »`.
   - `1234`: телефонная сетка 3 × 4 с колонкой `⌫ / − / + / ↵`, снизу `?123 · пробел · АБВ`.
   - **Нижний ряд:** `?123 · , · глобус · пробел · . · ↵` (запятая перед глобусом; клавиша `-` перед
     точкой — только при включённом `showHyphenKey`, иначе пробел шире: вес `4.2` вместо `3.4`).
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
10. **Контекст автозаглавной — только из редактора (как в Gboard).** `KeyboardScreen` не ведёт
    собственный буфер текста. Автозаглавная (`resolveEditorCapFlags` + `shouldAutoCapitalize` +
    `resolveAutoShift`) включается **только если само поле запрашивает `TYPE_TEXT_FLAG_CAP_*`** (не в URL,
    email, паролях, поиске, терминале), когда справа на текущей строке нет символов и строка пуста либо
    перед курсором конец предложения **с пробелом** (`". "`, `"! "`, `"? "`). Нажатие `.` без пробела
    (`site.ru`, `user.name`, `./cmd`) заглавную не включает. Синхронизация идёт на каждое обновление
    редактора (`editorContextVersion`), а не только на смену `true/false`: иначе в полях `CAP_CHARACTERS`
    заглавной становится лишь первая буква.
11. **Преселект Long-Press совпадает с микро-меткой и сохраняет частотность.** На левой половине
    (`colFraction < 0.5f`) варианты идут слева направо `[v0, v1, ...]`, выбор начинается с `0` и
    идёт вправо. На всей правой половине (`colFraction >= 0.5f`, `isRightSideKey`) варианты
    раскладываются **справа налево** `[..., v1, v0]`, попап прижат `TopEnd`, а выбор начинается с
    крайней правой ячейки (`v0` — самый частый символ) и уходит влево. Микро-метка в углу клавиши
    всегда показывает `v0`.
12. **Единая однорядная верхняя полоса и буфер обмена.** Верхняя панель всегда занимает ровно один
    ряд (`36 dp`), чтобы клавиатура не прыгала по высоте: нажатие `📋` раскрывает капсулу буфера
    в левой части **этой же строки** (`InlineClipboardCapsule`), тап по тексту сразу вставляет содержимое
    (отдельной кнопки «Вставить» внутри капсулы нет). В остальное время левую часть занимают стрелки
    курсора `←` / `→` (если включены в настройках). «Ложная латиница» (`ăĕçÿ → ӑӗҫӳ`) исправляется при
    вставке **только в словах с кириллицей** и никогда — в полях паролей (`isPasswordInputType`):
    латинский текст («garçon», чувашская латиница «Çăvaş») не меняется. Системный буфер не перезаписывается.
13. **Бесшовные хитбоксы (`gap / 2`) и контекстное поведение поля.** В прямоугольной раскладке
    каждая клавиша владеет `hGap / 2` и `vGap / 2` вокруг видимого прямоугольника — мёртвых щелей нет
    (в режиме сот `HoneycombHexGrid` отступы `0 dp`). При переходе в новое поле (включая `restartInput`
    со сменой сигнатуры `EditorInfo` в Jetpack Compose) клавиатура сбрасывает застрявшие режимы,
    автоматически открывает `NUMPAD` на `TYPE_CLASS_NUMBER` / `TYPE_CLASS_PHONE` и рисует контекстную
    иконку `Enter` (`SEARCH`, `SEND`, `GO`, `NEXT`, `DONE`, `NEWLINE`). Удержание `🌐` открывает меню
    раскладок и выбора системного метода ввода (`showInputMethodPicker`).
14. **Совместимость с приложениями.** В пустом поле и в полях `TYPE_NULL` (терминалы) `⌫` отправляется
    как `KEYCODE_DEL`, а перевод строки в `TYPE_NULL` — как `KEYCODE_ENTER`: ячейки SMS-кода, «чипы»
    получателей и консоли понимают только нажатия клавиш. Выбранная раскладка хранится в
    `PreferencesState.activeLang` (ключ `active_lang_v1`) и переживает поворот экрана и перезапуск.
15. **Автозамена (эксперимент, задача 013).** Выключена по умолчанию (`experimentalAutocorrect = false`,
    ключ `exp_autocorrect_v1`), живёт на вкладке «Эксперименты»; ленты подсказок нет (см. инвариант № 3).
    - Слово, найденное в **любом** подключённом словаре (чувашском, русском) или в личном словаре,
      не исправляется — так чувашские слова не превращаются в русские. ВСЕ ЗАГЛАВНЫЕ, смешанный регистр,
      слова короче 3 букв и токены с цифрами, латиницей, `@ # /` и т. п. не трогаются.
    - Поля — по правилу AOSP LatinIME (`Autocorrector.isFieldAllowed`): только `TYPE_CLASS_TEXT`, не пароль,
      URI, e-mail или фильтр, без `TYPE_TEXT_FLAG_NO_SUGGESTIONS`; однострочное поле — только с
      `TYPE_TEXT_FLAG_AUTO_CORRECT`.
    - Исправление — только по разделителю (пробел, `. , ! ? ; : ) » "`, `Enter` в многострочных полях).
      **Первый `⌫` сразу после исправления** возвращает набранное слово (разделитель остаётся), и в этом
      поле оно больше не исправляется.
    - Личный словарь (`personal_words_v1`, `PreferencesState.personalWords`) хранится **только на
      устройстве**, не пополняется при `IME_FLAG_NO_PERSONALIZED_LEARNING` (инкогнито) и не сбрасывается
      кнопкой «Сбросить все настройки».
    - Словари `assets/dict/cv.tsv` и `ru.tsv` **не редактировать руками** — только пересборкой скриптом
      `scripts/dictionaries/build_dictionaries.py` (в шапке файла — источник, лицензия и контрольные суммы).
      `ru.tsv` распространяется под CC BY-SA 4.0 — атрибуция в `THIRD_PARTY_NOTICES.md`.
    - Словари грузятся в фоне только при включённом эксперименте и выгружаются при выключении; пока
      словарь не загружен, автозамена просто не срабатывает.

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
- **Ревью 2026-11-02:**
  - удалена лента частиц и аффиксов вместе с `ChuvashMorphemes`, `MorphemeEngine` и настройкой
    `showParticlesStrip` (решение владельца);
  - вставка исправляет «ложную латиницу» только в кириллических словах и не трогает пароли;
  - автозаглавная работает в полях `CAP_CHARACTERS` (`resolveAutoShift` + `editorContextVersion`);
  - `KEYCODE_DEL` / `KEYCODE_ENTER` в пустых полях и `TYPE_NULL`, batch-правки, запоминание раскладки
    и синхронизация с системным подтипом IME;
  - F-Droid больше не целевая площадка: убраны его проверки и упоминания, скриншоты перенесены в
    `fastlane/.../images/phoneScreenshots/`, добавлен `keystore.properties.example`;
  - зависимости обновлены (Compose BOM `2026.09.00`, Lifecycle `2.11.0`, DataStore `1.3.0`, Gradle `9.8.2`).
- **Версия 1.4.0 (2026-11-02):**
  - шрифт Kesje Rounded: Comfortaa с перерисованной строчной «т» (не путается с «м»), Λ-«л» сохранена;
  - клавиши `50 dp` (`key_height_dp_v6`), Long-Press `300 мс` (`long_press_ms_v6`), клавиша дефиса
    по умолчанию выключена (`show_hyphen_key_v2`) — пробел шире;
  - кнопка «Вставить» убрана с верхней панели (дублировала `📋`), стрелки курсора — настройка
    `showCursorArrows`;
  - служебные клавиши в тёмных темах светлее букв; отклик клавиш через `performHapticFeedback`,
    разрешение `VIBRATE` удалено;
  - `PreferencesRepository` берёт значения по умолчанию из `KeyboardPreferences()`, а не из литералов.
- **Автозамена (эксперимент, ветка `feature/013-autocorrect`, 2026-11-04):**
  - пакет `data/autocorrect/` (`WordTrie`, `TypoCostModel`, `Autocorrector`, `AutocorrectSession`,
    `AutocorrectEngine`) и блок настроек `SettingsAutocorrect` на вкладке «Эксперименты», выключено по умолчанию;
  - исправление по разделителю и `Enter` с откатом первым `⌫`, языки `cv` и `ru` с выбором в настройках;
  - личный словарь «Запоминать отменённые слова» (`personal_words_v1`) с просмотром, удалением и очисткой;
  - встроенные словари по 160 000 словоформ: `cv.tsv` из корпуса `alexantonov/chuvash_mono` (CC0-1.0),
    `ru.tsv` из `hermitdave/FrequencyWords` (OpenSubtitles 2018, CC BY-SA 4.0); скрипт сборки
    `scripts/dictionaries/build_dictionaries.py`.

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

# AGENT.md — инструкция для ИИ-агентов (Кеске / Kĕske Android Keyboard)

Этот документ содержит обязательные правила, архитектурные инварианты, команды сборки и контекст
проекта **«Кеске (Kĕske)»** (`org.chuvash.keske`). Любой ИИ-агент (Hermes, Claude Code, Codex,
Cursor, Gemini и др.) перед изменением кода обязан изучить этот файл.

---

## 1. О проекте

- **Название:** Кеске / Кӗске (Kĕske) — кастомизируемая чувашская многоязычная клавиатура для Android.
- **Пакет и версия:** `org.chuvash.keske`, `versionName = "1.3.0"` (`versionCode = 4`).
- **Стек:** Kotlin `2.0.21` (`org.jetbrains.kotlin.plugin.compose`), Jetpack Compose (Material 3,
  BOM `2024.09.00`), DataStore Preferences `1.1.1`, Android Gradle Plugin `8.7.3`, JUnit `4.13.2`.
- **SDK:** `minSdk = 26` (Android 8.0), `targetSdk = 35`, `compileSdk = 35`.
- **Приватность:** полностью локальное приложение без доступа в сеть (в `AndroidManifest.xml`
  только разрешение `VIBRATE`), без аналитики и сторонних облачных SDK.
- **Языки:** Чувашский (`cv` — Чӑвашла), Русский (`ru` — Русская), Латиница (`lat` — Latina),
  Греческий (`el` — Ελληνικά).

---

## 2. Сборка и проверка (Windows / Git-Bash / PowerShell)

В `gradle.properties` прописан `org.gradle.java.home` на локальный Temurin JDK `21.0.7`
(`C:/Program Files/COMSOL/COMSOL64/Multiphysics/java/win64/jre`). JDK 25 в `JAVA_HOME` ломает
`lintAnalyzeDebug` (`JavaVersion.parse`), поэтому для запуска wrapper экспортируй тот же JDK 21:

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
- **Тесты (`app/src/test/java/org/chuvash/keske/`):**
  - `ime/KeyboardLayoutsTest.kt` — раскладки, сохранность клавиш в Hex, выбор вариантов Long-Press,
    удаление Unicode/ZWJ-графем, слово перед курсором, двойной пробел, пустая строка, слои символов,
    Numpad и эталонные дефолты;
  - `data/MorphemeEngineTest.kt` — сингармонизм, выбор формы морфем по окончанию основы, склейка
    с сохранением заглавной буквы, фильтрация экспериментальных морфем;
  - `data/ChuvashNamesTest.kt` — экспериментальная капитализация имён собственных.
  Любая правка раскладок, морфем, слоёв символов, пресетов, Numpad или значений по умолчанию должна
  сопровождаться тестом (или обновлением существующего).

---

## 3. Карта исходного кода

Исходники в `app/src/main/java/org/chuvash/keske/` разделены на три пакета без циклических
зависимостей (`ui.theme` → только ресурсы `R`; `data` → чистые модели, данные и DataStore без
зависимости от IME; `ime` → использует `data` и `ui.theme`):

### 3.1. Пакет `ui.theme`

- **`ui/theme/ThemePalettes.kt`**
  - Локальный шрифт `Comfortaa` (`res/font/comfortaa.ttf`) через `KeskeFontFamily` / `KeskeTypography`.
  - 7 цветовых палитр (`KeskePalettes`: 5 светлых и 2 тёмные) и конвертер `toColorScheme()` в Material 3.

### 3.2. Пакет `data`

- **`data/KeyboardPreferences.kt`**
  - Неизменяемая модель всех настроек клавиатуры, геометрии, флагов ввода, экспериментов и
    пользовательских рядов `customRowsByLang`.
- **`data/PreferencesRepository.kt`**
  - `PreferencesState` — общие `MutableStateFlow` (`preferences`, `shouldAutoCapitalize`,
    `inputSessionId`, `currentWord`), которые читают сервис и экран настроек без связи через
    `companion object` сервиса.
  - `PreferencesRepository` — чтение и запись в Jetpack DataStore (`keske_keyboard_prefs_v3`).
  - **Важно:** меняя дефолт настройки, которая уже могла сохраниться на устройстве, подними
    суффикс версии ключа `preferencesKey` (например, `_v5` → `_v6`, при необходимости с чтением
    старого ключа как fallback), иначе новое значение по умолчанию не применится.
  - Асинхронная загрузка защищена флагами `loaded: StateFlow<Boolean>` и `hasPendingUserUpdate`:
    правка настроек до завершения чтения не должна затираться фоновой загрузкой.
- **`data/KeyboardLayouts.kt`**
  - `KeyboardLang` (`CHUVASH`, `RUSSIAN`, `LATINA`, `GREEK`) и `ChuvashLayoutPreset`
    (`BALANCED_26`, `HEX_4ROW`, `FULL_28`).
  - Таблицы Long-Press: `chuvashLongPress`, `russianLongPress`, `latinaLongPress`,
    `greekLongPress`, `digitAndSymbolLongPress` (включая символы слоёв `?123` / `=\<`).
  - Слои: `numberRow` (`1–0`), `symbolsLayer1` (`10 + 10 + 7`), `symbolsLayer2` (`10 + 10 + 7`),
    `numpadRows` (3 колонки × 4 ряда), `emojiCategories`.
  - `getHex4RowLayout` — 4-рядная упаковка сот без потери символов;
    `getDisplayedLongPressVariants` / `computeLongPressSelectedIndex` / `initialLongPressVariantIndex` —
    единый видимый список вариантов и выбор индекса (включая обратный порядок у правого края).
  - Работа с текстом и Unicode: `normalizeChuvashUnicode`, `computeLastGraphemeLength`,
    `dropLastGrapheme`, `extractCurrentWordBeforeCursor`, `canReplaceSpaceWithCommaBeforeCursor`,
    `isCursorOnEmptyLine`.
  - `getSynharmonicParticlesAndAffixes` — фасад к `MorphemeEngine.suggestions`.
- **`data/ChuvashMorphemes.kt` и `data/MorphemeEngine.kt`**
  - Файл-словарь чувашских частиц, числа, 8 падежей, форм принадлежности и словообразовательных
    аффиксов (мягкий/твёрдый ряд, правила `AttachRule`, флаг `experimental`).
  - Движок определения гармонии по последней гласной (`harmonyOf`), подбора формы (`pickForm`),
    склейки с сохранением заглавной (`attach`) и выдачи чипов для ленты (`suggestions`).
- **`data/ChuvashNames.kt`**
  - Базовый словарь имён собственных (~95) и `capitalizeIfName` для экспериментальной автозаглавной.

### 3.3. Пакет `ime`

- **`ime/KeskeImeService.kt`**
  - `InputMethodService` с `LifecycleOwner`, `ViewModelStoreOwner`, `SavedStateRegistryOwner`.
  - Отслеживает контекст автозаглавной по образцу Gboard (`PreferencesState.shouldAutoCapitalize`
    через `resolveEditorCapFlags` + `shouldAutoCapitalize`, не включая заглавную в полях без `CAP_*`,
    URL, email, паролях, командах и сразу после точки без пробела), смену поля ввода (`inputSessionId`)
    и слово перед курсором (`PreferencesState.currentWord`) в `onStartInputView` / `onUpdateSelection`.
  - Удаление через `computeLastGraphemeLength` (целые кластеры Unicode), звук
    `AudioManager.FX_KEYPRESS_STANDARD` с учётом `STREAM_MUSIC` и `soundVolumePercent`, вибро,
    экспериментальная капитализация имён перед пробелом.
- **`ime/KeyboardScreen.kt`**
  - Корневой UI-компонент клавиатуры, общий для сервиса и превью настроек.
  - Режимы `KeyboardMode`: `LETTERS`, `SYMBOLS_1`, `SYMBOLS_2`, `NUMPAD`, `EMOJI_PAGE`;
    регистр `ShiftState` с флагом `autoShiftArmed`.
- **`ime/Keys.kt`**
  - `KeyButton` (Long-Press с ведением пальца, микро-метки, свайп влево на точке, hex-отрисовка),
    `BackspaceKey` (автоповтор + свайп удаления слова), `SpecialKey`, `SpacebarKey` (двойной пробел
    и горизонтальный свайп перемещения курсора).
  - Клавиши имеют семантику (`Role.Button`, `contentDescription`, `onClick`) и игнорируют
    отменённый жест (`ACTION_CANCEL`).
- **`ime/Toolbar.kt`**
  - `TopToolbarStrip` (лента сингармонических частиц/аффиксов + кнопки буфера, смайлов, настроек)
    и `ClipboardOverlayBar` (вставка тапом по тексту с неявной нормализацией `ăĕçÿ → ӑӗҫӳ`).
- **`ime/HoneycombHexGrid.kt`** — 4-рядная паркетная сотовая сетка с `clipToBounds` на каждом ряду.
- **`ime/NumpadGridView.kt`** — телефонная сетка 3 × 4 с колонкой действий `⌫ / − / + / ↵`.
- **`ime/EmojiPickerPage.kt`** — полноразмерная страница смайликов по категориям.
- **`ime/KeskeIcons.kt`**
  - Векторные иконки на `Canvas` вместо текстовых символов.
  - **Важно:** в Compose `Path` используй `quadraticTo(...)`, а не устаревший `quadraticBezierTo(...)`.
- **`ime/SettingsActivity.kt`, `ime/SettingsComponents.kt`, `ime/SettingsRowEditor.kt`**
  - 5 вкладок `SettingsTab`: «Раскладка», «Оформление», «Ввод», «Эксперименты», «Система».
  - Шапка с переключателем светлой/тёмной темы, переиспользуемые карточки/тумблеры/ползунки,
    конструктор рядов, тестовое поле `TextFieldValue` и **единственный** экземпляр `KeyboardScreen`,
    закреплённый снизу.

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
   - `showNumberRow` — **`true`**, `showParticlesStrip` — **`true`**, `showSubLabels` — **`false`**,
     `experimentalCapitalizeNames` — **`false`**.
2. **Чувашская раскладка по умолчанию (`BALANCED_26`):** строго `10 + 9 + 7 = 26` букв.
   - Ряд 1 (10): `й, ӑ, у, к, е, н, ӗ, ш, х, ӳ`
   - Ряд 2 (9): `ф, ы, в, а, п, р, о, л, ҫ`
   - Ряд 3 (7 + Shift + ⌫): `я, ч, с, м, и, т, ю`
3. **Верхняя панель и низ без лишних кнопок.** Hex и перестановка клавиш — только в настройках.
   Словарные подсказки слов в верхнем баре отключены (в ленте только частицы и аффиксы). Кастомного
   нижнего ряда со «Скрыть клавиатуру» нет — он дублирует системную навигацию.
4. **Жесты:** свайп вверх по буквам отключён; альтернативы — только Long-Press с ведением пальца;
   дефис — отдельная клавиша и свайп влево от точки; двойной пробел ставит `", "` (с проверкой
   контекста: одиночный пробел после буквы/цифры, без выделения).
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
12. **Полоса буфера обмена.** Тап по тексту в `ClipboardOverlayBar` сразу вставляет содержимое
    (отдельной кнопки «Вставить» нет). Нормализация «ложной латиницы» (`ăĕçÿ → ӑӗҫӳ`) выполняется
    неявно при вставке; системный буфер обмена не перезаписывается.

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

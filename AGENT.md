# AGENT.md — инструкция для ИИ-агентов (Кеске / Kĕske Android Keyboard)

Этот документ содержит обязательные правила, архитектурные инварианты, команды сборки и контекст
проекта **«Кеске (Kĕske)»** (`org.chuvash.keske`). Любой ИИ-агент (Hermes, Claude Code, Codex,
Cursor, Gemini и др.) перед изменением кода обязан изучить этот файл.

---

## 1. О проекте

- **Название:** Кеске (Kĕske) — кастомизируемая чувашская многоязычная клавиатура для Android.
- **Пакет:** `org.chuvash.keske`
- **Стек:** Kotlin `2.0.21` (`org.jetbrains.kotlin.plugin.compose`), Jetpack Compose (Material 3,
  BOM `2024.09.00`), DataStore Preferences `1.1.1`, Android Gradle Plugin `8.7.3`, JUnit `4.13.2`.
- **SDK:** `minSdk = 26` (Android 8.0), `targetSdk = 35`, `compileSdk = 35`.
- **Языки:** Чувашский (`cv` — Чӑвашла), Русский (`ru` — Русская), Латиница (`lat` — Latina),
  Греческий (`el` — Ελληνικά).

---

## 2. Сборка и проверка (Windows / Git-Bash)

В `gradle.properties` прописан `org.gradle.java.home` на локальный Temurin JDK `21.0.7`
(`C:/Program Files/COMSOL/COMSOL64/Multiphysics/java/win64/jre`). JDK 25 в `JAVA_HOME` ломает
`lintAnalyzeDebug` (`JavaVersion.parse`), поэтому для запуска wrapper экспортируй тот же JDK 21:

```bash
export JAVA_HOME="C:/Program Files/COMSOL/COMSOL64/Multiphysics/java/win64/jre"
./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon
```

- **Важно про пути и Kotlin Daemon на Windows:** в `gradle.properties` установлен
  `kotlin.incremental=false`. Не удаляй его — он предотвращает `java.rmi.UnmarshalException`
  в Kotlin-демоне из-за пробелов в пути проекта (`keyboard 5`).
- **Файл `gradle/gradle-daemon-jvm.properties` удалён намеренно.** Он задавал
  `toolchainVersion=25`, из-за чего lint падал с `> 25.0.3` в `JavaVersion.parse`, игнорируя
  `org.gradle.java.home`. Не создавай его заново (в том числе через `updateDaemonJvm`):
  JDK демона должен определяться только из `org.gradle.java.home`.
- **Выходной APK:** `app/build/outputs/apk/debug/app-debug.apk`.
- **Правило проверки:** любое изменение в `.kt` или `.xml` считается завершённым **только после
  успешного прохождения `./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon`
  без ошибок и предупреждений компилятора**.
- **Тесты:** `app/src/test/java/org/chuvash/keske/ime/KeyboardLayoutsTest.kt`. Любая правка
  раскладок, слоёв символов, пресетов, Numpad или значений по умолчанию должна сопровождаться
  тестом (или обновлением существующего).

---

## 3. Карта исходного кода

Все исходники находятся в `app/src/main/`:

1. **`java/org/chuvash/keske/ui/theme/ThemePalettes.kt`**
   - Локальный шрифт `Comfortaa` (`res/font/comfortaa.ttf`) через `KeskeFontFamily` / `KeskeTypography`.
   - 7 цветовых тем (`KeskePalettes`: 5 светлых и 2 тёмные) и конвертер в Material 3 `ColorScheme`.
   - Модель настроек `KeyboardPreferences` и хранилище `PreferencesRepository` на DataStore.
   - **Важно:** меняя дефолт настройки, которая уже могла сохраниться на устройстве, подними
     суффикс версии ключа `preferencesKey` (например, `_v5` → `_v6`), иначе новое значение
     по умолчанию не применится.
   - Загрузка асинхронная: правка настроек до завершения чтения не должна затираться фоновой
     загрузкой (это уже обеспечено, не ломай).

2. **`java/org/chuvash/keske/ime/KeyboardLayouts.kt`**
   - `KeyboardLang` (`CHUVASH`, `RUSSIAN`, `LATINA`, `GREEK`) и `ChuvashLayoutPreset`
     (`BALANCED_26`, `HEX_4ROW`, `FULL_28`).
   - Таблицы Long-Press: `chuvashLongPress`, `russianLongPress`, `latinaLongPress`,
     `greekLongPress`, `digitAndSymbolLongPress` (включая символы слоёв `?123` / `=\<`).
   - Слои: `symbolsLayer1` (`10 + 10 + 7`), `symbolsLayer2` (`10 + 10 + 7`), `numpadRows` (3 колонки × 4 ряда).
   - `getHex4RowLayout` — 4-рядная упаковка сот без потери символов;
     `getDisplayedLongPressVariants` / `computeLongPressSelectedIndex` — единый видимый список вариантов.
   - Сингармонизм `getSynharmonicParticlesAndAffixes`, нормализатор `normalizeChuvashUnicode`,
     работа с Unicode: `computeLastGraphemeLength`, `dropLastGrapheme`,
     `extractCurrentWordBeforeCursor`, `canReplaceSpaceWithCommaBeforeCursor`.
   - `dictionarySuggestions` — задел под словарь, в UI пока не используется.

3. **`java/org/chuvash/keske/ime/KeyboardScreen.kt`**
   - Единственный UI-компонент клавиатуры, общий для сервиса и превью настроек.
   - Режимы: `LETTERS`, `SYMBOLS_1`, `SYMBOLS_2`, `NUMPAD`, `EMOJI_PAGE`.
   - Компоненты: `TopToolbarStrip` (частицы + буфер + смайлы + настройки), `HoneycombHexGrid`,
     `EmojiPickerPage`, `NumpadGridView`, `KeyButton`, `BackspaceKey`, `SpacebarKey`, `SpecialKey`.
   - Клавиши имеют семантику (`Role.Button`, `contentDescription`, `onClick`) и игнорируют
     отменённый жест (`ACTION_CANCEL`).

4. **`java/org/chuvash/keske/ime/KeskeIcons.kt`**
   - Векторные иконки на `Canvas` вместо текстовых символов.
   - **Важно:** в Compose `Path` используй `quadraticTo(...)`, а не устаревший `quadraticBezierTo(...)`.

5. **`java/org/chuvash/keske/ime/KeskeImeService.kt`**
   - `InputMethodService` с `LifecycleOwner`, `ViewModelStoreOwner`, `SavedStateRegistryOwner`.
   - Отслеживает пустоту поля (`isFieldEmptyState`) и текущее слово перед курсором
     (`currentWordState`) в `onStartInputView` / `onUpdateSelection`.
   - Удаление через `dropLastGrapheme` (целые кластеры Unicode).
   - Звук `AudioManager.FX_KEYPRESS_STANDARD` с учётом `STREAM_MUSIC` и `soundVolumePercent`, вибро.

6. **`java/org/chuvash/keske/ime/SettingsActivity.kt`**
   - Вкладки `SettingsTab`: «Раскладка», «Оформление», «Ввод», «Система».
   - Шапка с переключателем светлой/тёмной темы, тестовое поле `TextFieldValue` и **единственный**
     экземпляр `KeyboardScreen`, закреплённый снизу: настройки видно и проверяемо на любой вкладке.
   - Конструктор рядов, пресеты, палитры, ползунки геометрии, громкость.

---

## 4. Строгие дизайн- и UX-инварианты (не нарушать)

1. **Эталонная геометрия и тема по умолчанию (`KeyboardPreferences`):**
   - Палитра (`paletteId`): **`"obsidian_forest"`** (*Ночной Лес OLED*)
   - Высота клавиш (`keyHeightDp`): **`48f`**
   - Скругление (`keyCornerRadiusDp`): **`14f`**
   - Зазор (`keyGapDp`): **`3f`**
   - Шрифт (`fontSizeSp`): **`26f`**
   - Задержка Long-Press (`longPressDelayMs`): **`210`**
   - Нижний отступ (`bottomInsetDp`): **`20f`**
   - `showNumberRow` — **`true`**, `showParticlesStrip` — **`true`**, `showSubLabels` — **`false`**.
2. **Чувашская раскладка по умолчанию (`BALANCED_26`):** строго `10 + 9 + 7 = 26` букв.
   - Ряд 1 (10): `й, ӑ, у, к, е, н, ӗ, ш, х, ӳ`
   - Ряд 2 (9): `ф, ы, в, а, п, р, о, л, ҫ`
   - Ряд 3 (7 + Shift + ⌫): `я, ч, с, м, и, т, ю`
3. **Верхняя панель и низ без лишних кнопок.** Hex и перестановка клавиш — только в настройках.
   Словарные подсказки слов в верхнем баре отключены. Кастомного нижнего ряда с «Скрыть
   клавиатуру» нет — он дублирует системную навигацию.
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
6. **Настройки — вкладками.** Контент прокручивается, клавиатура закреплена снизу и не дублируется
   в тексте. Новые настройки добавляй в подходящую вкладку, а не в общий список.
7. **Никаких правок «на глаз».** UI проверяется по коду и сборке; при сомнении в геометрии
   добавляй unit-тест на раскладочные функции, а не магические отступы.
8. **Гейт загрузки настроек.** `PreferencesRepository.loaded` становится `true` только после
   чтения DataStore; до этого `SettingsActivity` показывает индикатор и не принимает правок.
   Без этого снимок значений по умолчанию перезаписывает сохранённые настройки и раскладки.
9. **Соты: зона нажатия равна высоте ряда.** Ряды рисуются с шагом `0.8 × keyHeight` и
   перекрываются, поэтому каждый `Row` ограничен `.height(rowHitHeight).clipToBounds()`.
   `clipToBounds` обрезает и отрисовку, и hit-test — иначе тап по нижней части клавиши
   печатает букву из ряда ниже. Не убирай `clipToBounds` при рефакторинге сот.
10. **Текущее слово — только из редактора.** `KeyboardScreen` не ведёт собственный буфер слова:
    единственный источник — `editorCurrentWord` (`currentWordState` сервиса либо текст превью).
    Локальный дубль расходится с реальным полем (вставка, отмена, смена редактора).
11. **Преселект Long-Press всегда 0.** Микро-метка в углу клавиши показывает первый вариант,
    поэтому долгое нажатие без движения вставляет именно его; прижатие попапа к краю —
    только позиционирование.

---

## 5. Задачи на развитие

### Сделано в итерации 5 (`keyboard 5`)

- Переименование `LATIN` → `LATINA` (интерфейс и код), `ime_name` отделён от `app_name`.
- Слои символов и Numpad переработаны в стиле Gboard (см. п. 4.5).
- Настройки разбиты на вкладки, клавиатура всегда видна снизу.
- Обновлены `README.md`, `AGENT.md`, doc-комментарии модулей; добавлены тесты на слои и Numpad.
- По итогам код-ревью исправлено:
  - зона нажатия сот ограничена высотой ряда (`clipToBounds`) — больше не печатается буква из ряда ниже;
  - гейт `PreferencesRepository.loaded` — правки невозможны до чтения DataStore, настройки не затираются;
  - преселект Long-Press всегда первый и совпадает с микро-меткой;
  - Hex больше не выключает цифровой ряд навсегда;
  - сброс к эталону — только на вкладке «Система» и через диалог подтверждения;
  - удалён мёртвый `typedWordBuffer` — слово берётся только из редактора;
  - ошибки чтения и записи DataStore логируются (`Log.w`), а не проглатываются.

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

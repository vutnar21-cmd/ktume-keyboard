# Архитектура и структура проекта «Кӗсье Тӳме / кТӳме»

> Этот документ описывает реальное текущее устройство репозитория и пакетов внутри `src/`. При добавлении, переименовании или разделении модулей обновляй эту карту.

## 1. Дерево верхнего уровня

```text
ktume-keyboard/
├── docs/                       # Утверждённая документация проекта
│   ├── ARCHITECTURE.md         # Эта карта структуры репозитория, пакетов и тестов
│   ├── autocorrect.md          # Алгоритмы и математическая модель автозамены опечаток
│   ├── DECISIONS.md            # Журнал продуктовых и архитектурных решений
│   ├── features-and-mechanics.md # Подробное описание всех раскладок и механик ввода
│   ├── PITFALLS.md             # Книга граблей (Gradle, DataStore, Compose, InputConnection)
│   ├── PRIVACY_POLICY.md       # Политика конфиденциальности (100 % офлайн, 0 разрешений)
│   ├── README.md               # Оглавление раздела документации
│   ├── THIRD_PARTY_NOTICES.md  # Лицензии шрифта Kesje Rounded (OFL-1.1), словарей и библиотек
│   └── *.svg                   # SVG-бейджи скачивания и QR-код для публичного README.md
├── releases/                   # Собранные локальные APK (в .gitignore)
├── research/                   # Исследования, выбор названия и референсы шрифтов
│   ├── notes/                  # app-name-research.md, референсы глифов Comfortaa / Kesje Rounded
│   ├── raw/                    # Тяжёлые некоммиченные материалы + MANIFEST.md
│   ├── spikes/                 # Экспериментальные замеры и прототипы
│   └── INDEX.md                # Реестр исследовательских материалов со статусом выводов
├── src/                        # Полный Android / Gradle-проект (открывать эту папку в Android Studio)
│   ├── app/                    # Модуль приложения и сервиса клавиатуры (org.chuvash.ktume)
│   ├── fastlane/               # Метаданные карточки магазина (metadata/android/: описания, скриншоты, changelogs)
│   ├── gradle/                 # Gradle Wrapper (9.8.2)
│   ├── build.gradle.kts        # Корневой скрипт Gradle (AGP 9.5.0, Kotlin 2.4.21 + compose-плагин)
│   ├── gradle.properties       # JVM-флаги и kotlin.incremental=false
│   ├── gradlew / gradlew.bat   # Скрипты запуска Gradle Wrapper
│   ├── keystore.properties.example # Шаблон подписи (рабочие local.properties, keystore.properties, *.jks — в .gitignore)
│   └── settings.gradle.kts     # rootProject.name = "KTumeKeyboard", include(":app")
├── tasks/                      # Операционный центр задач
│   ├── img/                    # Скриншоты и иллюстрации к задачам
│   ├── specs/                  # Подробные разборы и отчёты по задачам (001..018 и новые)
│   ├── archive.md              # Архив закрытых задач
│   └── task.md                 # Живая очередь (Inbox → Backlog → In Progress → Done)
├── tools/                      # Скрипты автоматизации и git-хуки
│   ├── dictionaries/           # build_dictionaries.py — сборка частотных словарей cv.tsv и ru.tsv
│   ├── fonts/                  # patch_comfortaa_te.py — сборка шрифта kesje_rounded.ttf (русская «т»)
│   ├── git-hooks/pre-commit    # Защита tasks/task.md и AGENTS.md от коммитов вне main/master
│   ├── release.bat             # Быстрый запуск локальной публикации релиза (обёртка над release.ps1)
│   └── release.ps1             # Локальная сборка, проверка SHA-256 подписи и публикация через gh CLI
│
├── .gitattributes              # Правила переводов строк и бинарных файлов в Git
├── .gitignore                  # Исключения Git (кэши сборки, ключи .jks, releases/*, research/raw/*)
├── AGENTS.md                   # Компактные правила и инварианты для ИИ-агентов (< 10 КБ)
├── LICENSE                     # Лицензия кода (Apache License 2.0)
└── README.md                   # Публичное описание, бейджи установки (GitHub / Obtainium), инструкции
```

---

## 2. Пакеты и зависимости внутри `src/app/src/main/java/org/chuvash/ktume/`

Исходный код разделён на три пакета с однонаправленными связями без циклов (все исходные файлы удерживаются в бюджете `< 500` строк):

```text
ui.theme  ◄──  ime  ──►  data (включая data.autocorrect)
```

### 2.1. Пакет `ui.theme` (зависит только от Android `R`)
- **`ui/theme/ThemePalettes.kt`** — локальный шрифт `Kesje Rounded` (`res/font/kesje_rounded.ttf`) через `KesjeFontFamily` / `KesjeTypography`, 7 палитр `KesjePalettes` (5 светлых + 2 тёмные, по умолчанию `"obsidian_forest"`) и конвертация `toColorScheme()` в Material 3.

### 2.2. Пакет `data` (чистые модели, текстовые алгоритмы и DataStore — без зависимости от IME и UI)
- **`data/KeyboardPreferences.kt`** — неизменяемая модель настроек внешнего вида, геометрии, флагов ввода, автозамены (`autocorrectEnabled = true`), пользовательских рядов `customRowsByLang`, выборки рядов `rowsFor` и санитизации под тип сборки `sanitizedForBuild` (в Release фиксирует эталонную геометрию и выключает соты, в Debug оставляет всё доступным).
- **`data/PreferencesRepository.kt`** — реактивный хаб `PreferencesState` (`MutableStateFlow` для `preferences`, `personalWords`, `activeLang`, `shouldAutoCapitalize`, `inputSessionId`, `editorContextVersion`, `enterKeyAction`, `preferNumericMode`, `isPasswordField`) и хранилище `PreferencesRepository` поверх Jetpack DataStore (`kesje_keyboard_prefs_v3`, коалесцирующий `debounceDisk = true` на `220 мс` для ползунков, защита старта `loaded` + `hasPendingUserUpdate`, отдельное сохранение `active_lang_v1` и `personal_words_v1`).
- **`data/KeyboardLayouts.kt`** — `KeyboardLang` (`CHUVASH`, `RUSSIAN`, `LATINA`, `GREEK`, связь с `res/xml/method.xml`), пресеты `ChuvashLayoutPreset` (`BALANCED_26`, `RUSSIAN_35`, а также `HEX_4ROW` с `debugOnly = true`), таблицы Long-Press, слои `numberRow` / `symbolsLayer1` / `symbolsLayer2` / `numpadRows`, 4-рядная сотовая упаковка `getHex4RowLayout`, геометрия выбора вариантов Long-Press и конуса свайпа вверх `isSwipeUpGesture`.
- **`data/EditorTextLogic.kt`** — `EnterKeyAction`, `EffectiveKeyboardGeometry` и чистые функции контекста поля и графем: `normalizeChuvashUnicode`, `isPasswordInputType`, `computeLastGraphemeLength`, `dropLastGrapheme`, `extractCurrentWordBeforeCursor`, `computeDeleteWordLength`, `canReplaceSpaceWithCommaBeforeCursor`, `isCursorOnEmptyLine`, `resolveEditorCapFlags`, `shouldAutoCapitalize`, `resolveEnterKeyAction`, `prefersNumericPad`, `resolveEffectiveGeometry`, `computeKeyboardBodyHeightDp`.
- **`data/EmojiCatalog.kt`** — локальный каталог смайликов по 6 категориям.
- **`data/ChuvashNames.kt`** — базовый список личных имён (без омонимов нарицательных существительных), встроенный в движок автозамены.
- **`data/autocorrect/`** — офлайн-движок автозамены опечаток и автозаглавной имён собственных:
  - `WordTrie.kt` — компактное префиксное дерево в плоских массивах (`fromTsv`, `build`), взвешенный поиск Дамерау–Левенштейна с отсечением ветвей;
  - `TypoCostModel.kt` — модель цен правок (`FULL = 2`, `CHEAP = 1` для соседних клавиш раскладки, чувашских диакритик `а/ӑ`, `е/ӗ/ё/э`, `с/ҫ`, `у/ӳ`, `и/й`, `ь/ъ/ы`, `ш/щ` и удвоений, `firstLetterExtra = 1`);
  - `Autocorrector.kt` — формула оценки кандидатов, интеграция `ChuvashNames` (включая починку опечаток в именах и откат по `⌫`), пороги `minGain = 2.0` и `minMargin = 1.5`, политика полей `isFieldAllowed` / `isLearningAllowed`, фильтр обсценной лексики;
  - `AutocorrectSession.kt` — состояние текущего поля (откат первым `⌫` и список отменённых слов сессии);
  - `AutocorrectEngine.kt` — ленивая фоновая загрузка `assets/dict/cv.tsv` и `ru.tsv` при включённой автозамене и выгрузка при отключении.

### 2.3. Пакет `ime` (сервис клавиатуры, Compose-экран клавиатуры и студия настроек)
- **`ime/KesjeImeService.kt`** — `InputMethodService` с `LifecycleOwner` (`ON_PAUSE` в `onFinishInputView`, `ON_RESUME` в `onStartInputView`) / `ViewModelStoreOwner` / `SavedStateRegistryOwner`, синхронизация подтипа IME, контекст автозаглавной и полей ввода, графемное удаление, звук/виброотклик и интеграция автозамены в `onCommitText` / `onEnter` / `onBackspace`.
- **`ime/ShiftStateMachine.kt`**, **`ime/KeyboardScreen.kt`** и **`ime/KeyboardRows.kt`** — режимы `KeyboardMode`, конечный автомат регистра `ShiftState` (`nextShiftStateOnTap`, `resolveAutoShift`), корневой UI клавиатуры (read-only к `KeyboardPreferences`, общий для сервиса и стенда настроек), ряды клавиш с адаптивным кеглем для плотных 12-клавишных рядов и меню `GlobeLanguageMenuPopup`.
- **`ime/Keys.kt` и `ime/FunctionalKeys.kt`** — поверхность клавиш `rememberKeySurfaceModifier`, `KeyButton` (бесшовные хитбоксы `gap / 2`, «капля» нажатия, свайп вверх, зеркальный Long-Press), `BackspaceKey`, `SpecialKey`, `SpacebarKey` (двойной пробел и свайп курсора).
- **`ime/Toolbar.kt`** — строго однорядная верхняя полоса `TopToolbarStrip` (`36 dp`): капсула буфера обмена, вкладки категорий эмодзи или стрелки курсора `←` / `→`, единая точка `pasteFromClipboard`.
- **`ime/HoneycombHexGrid.kt`**, **`ime/NumpadGridView.kt`**, **`ime/EmojiPickerPage.kt`**, **`ime/KesjeIcons.kt`** — сотовая сетка (активна только в `BuildConfig.DEBUG`), телефонная панель `3 × 4`, пейджер эмодзи с нулевым скачком высоты и векторные иконки на `Canvas` с переиспользуемым `Path`.
- **`ime/SettingsActivity.kt`**, **`SettingsSections.kt`**, **`SettingsPreviewBench.kt`**, **`SettingsComponents.kt`**, **`SettingsRowEditor.kt`**, **`SettingsAutocorrect.kt`** — студия кастомизации из 5 вкладок с закреплённым снизу живым стендом клавиатуры и конструктором рядов.

---

## 3. Наборы тестов (`src/app/src/test/java/org/chuvash/ktume/`)

1. **`ime/KeyboardLayoutsTest.kt`** (25 тестов) — целостность всех языков в Hex, пресеты `BALANCED_26` и `RUSSIAN_35`, санитизация Release/Debug (`sanitizedForBuild`), зеркальный Long-Press справа и контракт микро-метки, свайп вверх, автоматы `ShiftState` / `resolveAutoShift`, нормализация `ăĕçÿ`, поля паролей, подтипы IME, свайп-удаление слов и графемы Unicode/ZWJ, эмодзи и неизменность высоты корпуса, геометрия альбомного режима и эталонные дефолты.
2. **`data/ChuvashNamesTest.kt`** (5 тестов) — капитализация имён собственных и исключение омонимов нарицательных существительных.
3. **`data/autocorrect/AutocorrectorTest.kt`** (16 тестов) — поиск в `WordTrie`, цены опечаток `TypoCostModel`, пороги `minGain`/`minMargin`, политика полей AOSP, защита известных/личных слов, встроенная капитализация имён и откат `⌫`.
4. **`data/autocorrect/AutocorrectDictionaryTest.kt`** (7 тестов) — проверка на реальных словарях `cv.tsv` и `ru.tsv`, укладывание поиска в бюджет `< 30 мс` на слово, отчёт на выборках `tools/dictionaries/.cache/*_holdout.tsv`.

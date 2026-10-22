# Кеске (Kĕske) — чувашская клавиатура для Android

**Кеске** — кастомизируемая многоязычная клавиатура (IME) для Android: чувашский (`Чӑвашла`),
русский, латиница (`Latina`) и греческий. Kotlin + Jetpack Compose (Material 3) + Jetpack DataStore.

- Пакет: `org.chuvash.keske`
- `minSdk = 26` (Android 8.0), `targetSdk = 35`, `compileSdk = 35`
- Kotlin `2.0.21`, AGP `8.7.3`, Compose BOM `2024.09.00`, DataStore `1.1.1`

---

## 1. Возможности

1. **Эталонная раскладка `10 + 9 + 7 = 26`.** Чувашские `ӑ, ӗ, ҫ, ӳ` — на первом слое.
   Редкие заимствованные буквы (`б, г, д, ж, з, ц, щ, э, ь, ъ, ё`) вынесены на долгое нажатие
   (`п→б`, `к→г`, `т→д`, `ш→ж/щ`, `с→з/ҫ/ц`, `е→э/ё`, `ы→ь/ъ`), поэтому клавиши не сжимаются.
2. **Паркетные соты (Hex), 4 ряда.** Опциональный режим: 26 букв и функциональные клавиши
   раскладываются в `7 + 7 + 7 + [Shift + 5 букв + ⌫]`. Чётные ряды сдвинуты на пол-соты,
   ширина соты считается из фактического числа колонок — ряды не выезжают за край.
   Раскладки другого размера (русская 31, FULL_28, греческая 25, пользовательские) распределяются
   по четырём рядам **без потери и без подмены символов**.
3. **Долгое нажатие с ведением пальца.** Зажатие открывает `Popup` с вариантами; палец ведётся
   влево/вправо без отрыва. Отображаемый список, подсветка и вставляемый символ — один и тот же
   набор (`getDisplayedLongPressVariants` / `computeLongPressSelectedIndex`). У краёв экрана
   `Popup` меняет привязку (`TopStart` / `TopEnd`).
4. **Символы и цифры в духе Gboard.** `?123` — цифры и частотные символы (`10 + 10 + 7`),
   `=\<` — валюты, математика и типографика, `1234` — телефонная сетка `3 × 4` с колонкой
   `⌫ / − / + / ↵` и нижним рядом `?123 · пробел · АБВ`.
5. **Чувашская типографика.** Дефис отдельной клавишей и свайпом влево от точки; двойной пробел
   ставит `", "` (только если перед курсором одиночный пробел после буквы/цифры и нет выделения);
   лента сингармонических частиц и аффиксов (`-и?`, `-ши?`, `-ха`, `-ҫкӗ`, `+сем`, `+ре/+ра`, …);
   нормализатор `ăĕçÿ → ӑӗҫӳ` для вставки из буфера.
6. **Корректное удаление Unicode.** `Backspace` удаляет целый графемный кластер
   (`BreakIterator`): суррогатные пары, ZWJ-эмодзи, составные символы.
7. **Доступность.** Клавиши, переключатели, ползунки и ячейки конструктора имеют семантику
   (`Role.Button` / `Role.Switch`, `contentDescription`, `onClick`, `selected`); отменённый жест
   (`ACTION_CANCEL`) не вызывает ввод.
8. **Звук и вибрация.** Щелчок клавиш учитывает системную громкость (`STREAM_MUSIC`) и ползунок
   `soundVolumePercent`; отдельный мягкий виброотклик.
9. **Экран настроек с вкладками.** «Раскладка», «Оформление», «Ввод», «Система»; тестовое поле
   и единственный экземпляр клавиатуры всегда видны снизу, поэтому настройки проверяются вживую.

### Значения по умолчанию

| Параметр | Значение |
|---|---|
| Палитра | `obsidian_forest` (Ночной Лес OLED) |
| Высота клавиши | `48 dp` |
| Скругление | `14 dp` |
| Зазор между клавишами | `3 dp` |
| Шрифт | Comfortaa `26 sp` |
| Задержка Long-Press | `210 мс` |
| Нижний отступ | `20 dp` |
| Цифровой ряд / лента частиц | включены |
| Микро-метки в углах клавиш | выключены |

---

## 2. Структура проекта

```text
keske_android_keyboard_project/
├── build.gradle.kts                  # AGP 8.7.3, Kotlin 2.0.21, compose-плагин
├── settings.gradle.kts
├── gradle.properties                 # JVM-параметры, путь к JDK 21, kotlin.incremental=false
├── README.md
└── app/
    ├── build.gradle.kts              # SDK 26..35, Compose BOM, DataStore
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml   # SettingsActivity (LAUNCHER) + KeskeImeService (BIND_INPUT_METHOD)
        │   ├── res/
        │   │   ├── font/comfortaa.ttf
        │   │   ├── values/strings.xml    # app_name, ime_name, названия подтипов
        │   │   └── xml/method.xml        # подтипы: cv_RU, ru_RU, en_US, el_GR
        │   └── java/org/chuvash/keske/
        │       ├── ui/theme/ThemePalettes.kt   # шрифт, 7 палитр, KeyboardPreferences, DataStore
        │       └── ime/
        │           ├── KeskeIcons.kt           # векторные иконки на Canvas
        │           ├── KeyboardLayouts.kt      # раскладки, пресеты, слои символов, Long-Press, сингармонизм
        │           ├── KeyboardScreen.kt       # UI клавиатуры: режимы, соты, Numpad, эмодзи, жесты
        │           ├── KeskeImeService.kt      # InputMethodService, InputConnection, звук/вибро
        │           └── SettingsActivity.kt     # настройки: вкладки, конструктор, живой стенд
        └── test/java/org/chuvash/keske/ime/KeyboardLayoutsTest.kt
```

### Модули

**`ThemePalettes.kt`** — `KeskeFontFamily` / `KeskeTypography`, 7 палитр (`KeskePalettes`,
`toColorScheme()`), дата-класс `KeyboardPreferences` и репозиторий `PreferencesRepository`
на DataStore. Ключи хранилища версионированы суффиксом (`_v5`): при смене значения по умолчанию
суффикс нужно поднять, иначе на устройстве останется старое сохранённое значение.

**`KeyboardLayouts.kt`** — `KeyboardLang` (`CHUVASH`, `RUSSIAN`, `LATINA`, `GREEK`),
`ChuvashLayoutPreset` (`BALANCED_26`, `HEX_4ROW`, `FULL_28`), таблицы Long-Press
(`chuvashLongPress`, `russianLongPress`, `latinaLongPress`, `greekLongPress`,
`digitAndSymbolLongPress`), слои (`symbolsLayer1`, `symbolsLayer2`, `numpadRows`), эмодзи,
`getSynharmonicParticlesAndAffixes`, `normalizeChuvashUnicode`, `computeLastGraphemeLength`,
`extractCurrentWordBeforeCursor`, `canReplaceSpaceWithCommaBeforeCursor`.

**`KeyboardScreen.kt`** — единственный экземпляр UI, общий для сервиса и превью настроек:
`TopToolbarStrip`, `HoneycombHexGrid`, `EmojiPickerPage`, `NumpadGridView`, `KeyButton`,
`BackspaceKey`, `SpacebarKey`, `SpecialKey`.

**`KeskeImeService.kt`** — `InputMethodService` с `LifecycleOwner` / `ViewModelStoreOwner` /
`SavedStateRegistryOwner` для Compose; отслеживает пустоту поля и слово перед курсором,
отправляет текст, удаление, перемещение курсора и `Enter` через `InputConnection`.

**`SettingsActivity.kt`** — вкладки настроек, конструктор рядов, пресеты, палитры и живой стенд.

---

## 3. Сборка и проверка

Gradle-демону и Lint нужен JDK 21 (на JDK 25 `lint` падает в `JavaVersion.parse`).
Путь прописан в `gradle.properties` (`org.gradle.java.home`), для запуска wrapper достаточно:

```bash
export JAVA_HOME="C:/Program Files/COMSOL/COMSOL64/Multiphysics/java/win64/jre"
./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

`kotlin.incremental=false` оставлен намеренно: из-за пробела в пути проекта Kotlin-демон
падает с `java.rmi.UnmarshalException` при инкрементальной сборке.

### Тесты

`KeyboardLayoutsTest` покрывает: сохранность всех клавиш в Hex для каждого языка и пресета,
согласованность видимых вариантов Long-Press, удаление суррогатных пар и ZWJ-эмодзи,
извлечение слова перед курсором, безопасную замену двойного пробела, форму слоёв символов
и телефонную сетку Numpad, а также эталонные значения по умолчанию.

---

## 4. Открытые вопросы

- **Название приложения.** Сейчас в `strings.xml` используется `Кеске` (`app_name` и `ime_name`).
  После выбора финального названия нужно переименовать переменные и строки.
- **Словарь и автокоррекция.** Списки `dictionarySuggestions` пока не используются в UI.
- **Список графем Long-Press.** Требует редакторской вычитки: убрать дубли внутри раскладки.
- **Функциональные клавиши в сотах.** Стоит ли вписывать `Shift` / `⌫` / `↵` в шестиугольники.

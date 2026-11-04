# Кӗсье Тӳме / кТӳме (Kĕsje Tüme / kTüme) — чувашская клавиатура для Android

**Кӗсье Тӳме** (кратко **кТӳме** / **kTüme**) — кастомизируемая автономная многоязычная клавиатура (IME) для Android:
чувашский (`Чӑвашла`), русский, латиница (`Latina`) и греческий. Приложение работает полностью
локально (без доступа в интернет, аналитики и облачных сервисов).

- Пакет: `org.chuvash.ktume` (`v1.5.0`, `versionCode = 6`)
- Репозиторий: https://github.com/vutnar21-cmd/ktume-keyboard
- `minSdk = 26` (Android 8.0), `targetSdk = 37` (Android 17), `compileSdk = 37`
- Kotlin `2.4.21`, AGP `9.5.0`, Gradle `9.8.2`, Jetpack Compose (Material 3, BOM `2026.09.00`), DataStore `1.3.0`

### Скачать для Android (8.0+)

[![Скачать APK с GitHub Releases](docs/badge-github-apk.svg)](https://github.com/vutnar21-cmd/ktume-keyboard/releases/latest)
[![Добавить в Obtainium для автообновлений](docs/badge-obtainium.svg)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22org.chuvash.ktume%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fvutnar21-cmd%2Fktume-keyboard%22%2C%22author%22%3A%22vutnar21-cmd%22%2C%22name%22%3A%22%D0%9A%D3%97%D1%81%D1%8C%D0%B5%20%D0%A2%D3%B3%D0%BC%D0%B5%20%28kT%C3%BCme%29%22%7D)

| QR-код (`releases/latest`) | Установка и обновления |
| :---: | :--- |
| [![QR-код скачивания APK](docs/qr-download.svg)](https://github.com/vutnar21-cmd/ktume-keyboard/releases/latest) | **Вручную:** наведите камеру телефона на QR-код (или откройте [`releases/latest`](https://github.com/vutnar21-cmd/ktume-keyboard/releases/latest)) и установите `ktume-<версия>.apk`.<br>**С автообновлением:** нажмите **«Добавить в Obtainium»** (или вставьте URL репозитория в [Obtainium](https://github.com/ImranR98/Obtainium)) — обновления с GitHub будут приходить автоматически с проверкой подписи.<br>**Отпечаток сертификата подписи (`SHA-256`):** `3fd72afafa41e973b820e0c9ee1cd9e818f95a3431fbf4f9722ed9fc4b301312` |

---

## 1. Возможности

1. **Эталонная раскладка `10 + 9 + 7 = 26`.** Чувашские `ӑ, ӗ, ҫ, ӳ` — на первом слое.
   Редкие заимствованные буквы (`б, г, д, ж, з, ц, щ, э, ь, ъ, ё`) вынесены на долгое нажатие
   (`п→б`, `к→г`, `т→д`, `ш→ж/щ`, `с→з`, `ч→ц`, `е→э/ё`, `ы→ь/ъ`), поэтому клавиши не сжимаются.
2. **Паркетные соты (Hex), 4 ряда.** Опциональный режим (на вкладке «Эксперименты»): 26 букв и функциональные клавиши
   раскладываются в `7 + 7 + 7 + [Shift + 5 букв + ⌫]`. Чётные ряды сдвинуты на пол-соты,
   ширина соты считается из фактического числа колонок — ряды не выезжают за край.
   Раскладки другого размера (русская 31, `FULL_28`, греческая 25, пользовательские) распределяются
   по четырём рядам **без потери и без подмены символов**.
3. **Долгое нажатие, «капля» нажатия и быстрый свайп вверх.**
   - **Всплывающая «капля» (`showKeyPressPopup`, включена по умолчанию):** при касании над клавишей
     показывается превью буквы, которое при свайпе вверх на `20 dp` бесшовно превращается в превью
     1-го акцентированного символа с микро-тиком вибрации.
   - **Свайп вверх (`swipeUpFirstVariant`, включён по умолчанию во вкладке «Ввод»):** короткий
     росчерк вверх по любой клавише с вариантами Long-Press сразу вводит самый частый 1-й вариант
     (`variants[0]`: `к→г`, `т→д`, `п→б`, `ш→ж`, `с→з`, `ч→ц`, `е→э`, `ы→ь`, `.→-`) без ожидания таймера.
   - **Долгое нажатие:** зажатие открывает `Popup` со всеми вариантами (шаг ведения пальца `40 dp` с
     тактильным тиком); на всей правой половине клавиатуры (`colFraction >= 0.5f`) варианты раскладываются
     справа налево с сохранением частотности. Удержание `🌐` открывает быстрое меню раскладок и системных клавиатур.
4. **Бесшовные хитбоксы, контекстный `Enter` и авто-Numpad.**
   - Визуальные зазоры между клавишами не содержат «мёртвых зон»: хитбокс каждой прямоугольной клавиши
     владеет половиной зазора (`hGap / 2`, `vGap / 2`) и имеет живую подсветку нажатия (`isPressed`).
   - Общий цифровой ряд `1–0` сохраняет одинаковую высоту страниц `?123` и `=\<`. В числовых и телефонных
     полях автоматически открывается телефонная сетка `1234` (`3 × 4`), а клавиша `Enter` меняет векторную
     иконку под контекст поля (`🔍 Поиск`, `➤ Отправить`, `→ Перейти`, `⇥ Далее`, `✓ Готово`, `↵ Абзац`).
5. **Типографика и верхняя панель.**
   - Дефис — свайпом влево от точки (отдельная клавиша включается в настройках); двойной пробел ставит `", "` (только если
     перед курсором одиночный пробел после буквы/цифры и нет выделения).
   - Единая однорядная верхняя панель (`36 dp` без скачков высоты): справа `[📋] [😊] [⚙️]`, слева —
     либо встроенная в эту же строку капсула буфера обмена (по `📋`, вставка тапом), либо стрелки курсора
     `←` и `→` (отключаются в настройках).
   - При вставке «ложная латиница» `ăĕçÿ` исправляется на `ӑӗҫӳ` только внутри слов с кириллицей
     («Чăваш» → «Чӑваш»). Латинский текст («garçon», «Çăvaş») и поля паролей не меняются.
6. **Автозаглавная как в Gboard и удаление Unicode.** Автозаглавная включается только в тех полях,
   которые сами запрашивают `TYPE_TEXT_FLAG_CAP_*` (в браузере, поиске, email, паролях и терминале
   клавиатура начинает со строчной), только на пустой строке или после конца предложения **с пробелом**
   (`". "`), не мешая вводить `user.name@mail.ru`, `chuvash.org` и `./gradlew`. В полях «всё заглавными»
   (`CAP_CHARACTERS`: коды, номера) заглавные идут подряд. `Backspace` удаляет целый графемный кластер
   (`BreakIterator`): суррогатные пары, ZWJ-эмодзи, составные символы. В пустом поле и в терминалах
   (`TYPE_NULL`) `⌫` и `Enter` отправляются как нажатия клавиш — ячейки SMS-кода и консоли их понимают.
   Выбранная раскладка запоминается и совпадает с системным подтипом клавиатуры (язык проверки орфографии).
7. **Доступность, звук и вибрация.** Клавиши и элементы настроек имеют семантику TalkBack
   (`Role.Button` / `Role.Switch`, `contentDescription`, `onClick`, `selected`); отменённый жест
   не вызывает ввод. Щелчок клавиш учитывает `STREAM_MUSIC` и ползунок `soundVolumePercent`.
8. **Экран настроек с 5 вкладками.** «Раскладка», «Оформление», «Ввод», «Эксперименты», «Система»;
   тестовое поле и единственный экземпляр клавиатуры всегда закреплены снизу для живой проверки.
9. **Автозамена опечаток (эксперимент, выключена по умолчанию).** На вкладке «Эксперименты»: слово с
   опечаткой исправляется по встроенным частотным словарям (чувашский и русский, по ~160 тыс. словоформ)
   при вводе пробела, знака препинания или переноса строки («превет» → «привет», «сак» → «ҫак»); первый
   `⌫` сразу после исправления возвращает набранное слово. Слова из любого словаря не трогаются, поэтому
   чувашские слова не превращаются в русские; пароли, адреса, e-mail и аббревиатуры не исправляются.
   Личный словарь «Запоминать отменённые слова» хранится только на телефоне, его можно просмотреть и
   очистить. Ленты подсказок нет — только исправление. Подробное описание алгоритма — в
   [`docs/autocorrect.md`](docs/autocorrect.md).

> Полная документация по архитектуре и работе всех функций: **[`docs/README.md`](docs/README.md)**,
> **[`docs/features-and-mechanics.md`](docs/features-and-mechanics.md)** и **[`docs/autocorrect.md`](docs/autocorrect.md)**.

### Значения по умолчанию

| Параметр | Значение |
|---|---|
| Палитра | `obsidian_forest` (Ночной Лес OLED) |
| Высота клавиши | `50 dp` |
| Скругление | `14 dp` |
| Зазор по горизонтали (`keyGapHDp`) | `3 dp` |
| Зазор по вертикали (`keyGapVDp`) | `7 dp` |
| Шрифт | Kesje Rounded (Comfortaa с русской «т») `26 sp` |
| Задержка Long-Press | `300 мс` |
| Нижний отступ | `20 dp` |
| Цифровой ряд / свайп вверх / стрелки курсора | включены |
| Отдельная клавиша дефиса | выключена (дефис — свайп влево от точки) |
| Всплывающая «капля» / компактный альбомный режим | включены |
| Микро-метки в углах клавиш | выключены |
| Автозаглавные имена (эксперимент) | выключены |
| Автозамена опечаток (эксперимент) | выключена |

---

## 2. Структура проекта

```text
ktume-keyboard/
├── AGENT.md                          # Обязательные правила и инварианты для ИИ-агентов
├── README.md
├── build.gradle.kts                  # AGP 9.5.0, Kotlin 2.4.21 (built-in + compose-плагин)
├── settings.gradle.kts
├── gradle.properties                 # JVM-параметры, kotlin.incremental=false
├── keystore.properties.example       # Шаблон настроек подписи релиза (копируется в keystore.properties)
├── release.bat                       # Запуск локальной автоматизации релиза (обёртка над scripts/release.ps1)
├── scripts/release.ps1               # Автосборка, проверка подписи, тег и публикация в GitHub Releases
├── scripts/fonts/                    # Сборка шрифта Kesje Rounded из Comfortaa (русская «т»)
├── scripts/dictionaries/             # Сборка словарей автозамены assets/dict/cv.tsv и ru.tsv
├── docs/                             # Документация проекта (README.md, features-and-mechanics.md, autocorrect.md) и SVG-ресурсы
├── fastlane/metadata/android/        # Карточка для магазинов: описания, иконка, скриншоты, changelogs/<versionCode>.txt
├── tasks/                            # Каталог задач на развитие и индекс статусов (tasks/README.md)
└── app/
    ├── build.gradle.kts              # SDK 26..37, Compose BOM, DataStore
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml   # SettingsActivity (LAUNCHER) + KesjeImeService (BIND_INPUT_METHOD)
        │   ├── assets/dict/          # частотные словари автозамены cv.tsv (CC0) и ru.tsv (CC BY-SA 4.0)
        │   ├── res/
        │   │   ├── font/kesje_rounded.ttf  # Comfortaa с русской «т» (scripts/fonts/patch_comfortaa_te.py)
        │   │   ├── values/strings.xml    # app_name, ime_name, названия подтипов
        │   │   └── xml/method.xml        # подтипы: cv_RU, ru_RU, en_US, el_GR
        │   └── java/org/chuvash/ktume/
        │       ├── ui/theme/
        │       │   └── ThemePalettes.kt        # шрифт Kesje Rounded, 7 палитр и Material 3 ColorScheme
        │       ├── data/
        │       │   ├── autocorrect/            # экспериментальная автозамена: WordTrie, TypoCostModel,
        │       │   │                           # Autocorrector, AutocorrectSession, AutocorrectEngine
        │       │   ├── ChuvashNames.kt         # базовый словарь имён собственных
        │       │   ├── EditorTextLogic.kt      # Unicode-графемы, автозаглавная, контекст поля, геометрия
        │       │   ├── EmojiCatalog.kt         # локальный каталог эмодзи по 6 категориям
        │       │   ├── KeyboardLayouts.kt      # раскладки, пресеты, слои символов, Numpad, Long-Press
        │       │   ├── KeyboardPreferences.kt  # неизменяемая модель настроек клавиатуры
        │       │   └── PreferencesRepository.kt# PreferencesState и хранилище DataStore
        │       └── ime/
        │           ├── EmojiPickerPage.kt      # полноразмерная страница эмодзи
        │           ├── FunctionalKeys.kt       # BackspaceKey, SpecialKey, SpacebarKey
        │           ├── HoneycombHexGrid.kt     # 4-рядная паркетная сетка сот
        │           ├── KesjeIcons.kt           # векторные иконки на Canvas
        │           ├── KesjeImeService.kt      # InputMethodService, InputConnection, звук/вибро
        │           ├── KeyboardRows.kt         # цифровой ряд, ряды букв/символов, нижний бар и меню 🌐
        │           ├── KeyboardScreen.kt       # корневой экран клавиатуры, режимы и автомат Shift
        │           ├── Keys.kt                 # поверхность клавиш (Rect/Hex), KeyButton и попапы
        │           ├── NumpadGridView.kt       # телефонная панель 3 × 4
        │           ├── SettingsActivity.kt     # каркас экрана настроек с 5 вкладками
        │           ├── SettingsAutocorrect.kt  # блок автозамены и личного словаря («Эксперименты»)
        │           ├── SettingsComponents.kt   # карточки секций, превью палитр, тумблеры, ползунки
        │           ├── SettingsPreviewBench.kt # живое тестовое поле и закреплённый стенд клавиатуры
        │           ├── SettingsRowEditor.kt    # интерактивный конструктор рядов раскладки
        │           ├── SettingsSections.kt     # содержимое 5 вкладок настроек
        │           └── Toolbar.kt              # однорядная верхняя полоса (36 dp): правка / буфер / вкладки эмодзи
        └── test/java/org/chuvash/ktume/
            ├── data/ChuvashNamesTest.kt
            ├── data/autocorrect/AutocorrectorTest.kt
            ├── data/autocorrect/AutocorrectDictionaryTest.kt
            └── ime/KeyboardLayoutsTest.kt
```

### Пакеты и ответственность

- **`ui.theme`** — визуальная тема (`KesjeFontFamily`, `KesjeTypography`, 7 палитр `KesjePalettes`
  и `toColorScheme()`). Не зависит от `data` и `ime`.
- **`data`** — чистые данные, текстовые алгоритмы и хранение без зависимости от Android IME:
  `KeyboardPreferences`, `PreferencesState` / `PreferencesRepository` (DataStore с версионированными
  ключами и гейтом загрузки `loaded`), `KeyboardLayouts`, `EditorTextLogic`, `EmojiCatalog`,
  словарь имён (`ChuvashNames`), экспериментальная автозамена (`data/autocorrect/`).
- **`ime`** — сервис клавиатуры `KesjeImeService`, модульный Compose-интерфейс клавиатуры
  (`KeyboardScreen`, `KeyboardRows`, `Keys`, `FunctionalKeys`, `Toolbar`, `HoneycombHexGrid`,
  `NumpadGridView`, `EmojiPickerPage`, `KesjeIcons`) и студия кастомизации (`SettingsActivity`,
  `SettingsSections`, `SettingsPreviewBench`, `SettingsComponents`, `SettingsRowEditor`, `SettingsAutocorrect`).

---

## 3. Сборка и проверка

Gradle-демону и Android Lint нужен JDK 21 (локальный путь `org.gradle.java.home` задаётся в
пользовательском `~/.gradle/gradle.properties`, чтобы репозиторий собирался и на других машинах):

```bash
export JAVA_HOME="C:/Program Files/COMSOL/COMSOL64/Multiphysics/java/win64/jre"
./gradlew assembleDebug testDebugUnitTest lintDebug --no-daemon
```

Отладочный APK: `app/build/outputs/apk/debug/app-debug.apk`.

### Релизная сборка (GitHub Releases, Google Play, RuStore)

1. Скопируйте [`keystore.properties.example`](keystore.properties.example) в `keystore.properties`
   (добавлен в `.gitignore`), сгенерируйте ключ `keytool` по инструкции внутри файла и укажите пароли.
   **Храните копию `.jks` и паролей вне папки проекта:** отпечаток сертификата опубликован выше и
   проверяется Obtainium, без этого ключа выпустить обновление невозможно.
2. Соберите нужный артефакт:
   - `./gradlew assembleRelease --no-daemon` → подписанный `app/build/outputs/apk/release/app-release.apk`
     (для GitHub Releases и RuStore; без `keystore.properties` собирается неподписанный
     `app-release-unsigned.apk`).
   - `./gradlew bundleRelease --no-daemon` → `app/build/outputs/bundle/release/app-release.aab`
     (для Google Play).
3. Метаданные карточки магазина (описание, иконка `512×512`, скриншоты в `images/phoneScreenshots/`,
   список изменений) хранятся в `fastlane/metadata/android/` (`ru-RU` и `en-US`).
4. **Автоматический выпуск новых версий одной командой (`release.bat`):**
   - Приватный ключ `.jks` никуда в облако не загружается — сборка, проверка подписи и публикация
     выполняются локально через официальный `gh` CLI (`winget install --id GitHub.cli -e`,
     затем один раз `gh auth login`).
   - Для выпуска новой версии поднимите `versionCode` и `versionName` в `app/build.gradle.kts`, добавьте
     `fastlane/metadata/android/{ru-RU,en-US}/changelogs/<versionCode>.txt` (до 500 байт), закоммитьте изменения и запустите:
     ```powershell
     .\release.bat -DryRun   # предварительная проверка сборки, подписи и текста релиза без пуша
     .\release.bat           # пуш main, тег v<versionName> и публикация ktume-<versionName>.apk на GitHub
     ```
   - После публикации `ktume-<versionName>.apk` на GitHub пользователи Obtainium получат обновление
     автоматически, а Obtainium сверит подпись с опубликованным отпечатком.

`kotlin.incremental=false` сохранён намеренно для предотвращения `java.rmi.UnmarshalException`
в Kotlin-демоне на Windows при наличии пробелов в путях.

### Тесты (50 unit-тестов)

- **`KeyboardLayoutsTest`** (23 теста): сохранность всех клавиш в Hex для каждого языка и пресета,
  выбор вариантов Long-Press на всей правой половине справа налево с сохранением частотности и
  контракта микро-метки, свайп вверх `swipeUpFirstVariant`, конечный автомат `nextShiftStateOnTap`,
  синхронизация автозаглавной `resolveAutoShift` (повторное включение в полях `CAP_CHARACTERS`),
  исправление «ложной латиницы» только в кириллических словах, распознавание полей паролей,
  сопоставление системных подтипов IME с раскладками,
  безопасное удаление слова по свайпу `⌫` с защитой от разрезания суррогатной пары на границе окна,
  6 категорий смайликов (кратных 8 колонкам) и MRU-очередь `recentEmojis`, точное совпадение высоты
  `computeKeyboardBodyHeightDp` для прямоугольной и сотовой раскладок, контекстный `Enter` и
  авто-Numpad, компактная альбомная геометрия `resolveEffectiveGeometry`, удаление суррогатных пар и
  ZWJ-эмодзи, безопасная замена двойного пробела, пустая строка и правила автозаглавной Gboard
  (`resolveEditorCapFlags`, `shouldAutoCapitalize`), форма слоёв символов, телефонная сетка Numpad и
  эталонные значения `KeyboardPreferences`.
- **`ChuvashNamesTest`** (5 тестов): капитализация имён со строчной буквы и игнорирование обычных
  слов и уже капитализированных имён.
- **`AutocorrectorTest`** (15 тестов): префиксное дерево и поиск со всеми видами правок, дешёвые соседние
  клавиши и надстрочные знаки, регистр, защита известных и личных слов, порог выгоды над гипотезой
  редкого слова (`minGain`), пропуск коротких, заглавных и иноязычных слов, фильтр обсценной лексики,
  политика полей по образцу AOSP, откат `⌫`.
- **`AutocorrectDictionaryTest`** (7 тестов): исправление типичных опечаток на реальных словарях `cv.tsv` /
  `ru.tsv`, частые слова не трогаются, бюджет времени поиска, отчёт о ложных исправлениях и калибровка порогов.

---

## 4. Открытые задачи и вопросы

План работ и статусы ведутся в каталоге [`tasks/README.md`](tasks/README.md). Ключевые открытые темы:

- **Финальное название приложения** ([`004`](tasks/004-app-name.md)): утверждено название
  **Кӗсье Тӳме / Kĕsje Tüme** (кратко **кТӳме / kTüme**) и идентификатор пакета `org.chuvash.ktume` (на приёмке).
- **Таблицы Long-Press** ([`005`](tasks/005-longpress-glyphs.md)): очистка дублей и выверка
  частотности вариантов. Лента частиц и аффиксов ([`002`](tasks/002-chuvash-morphemes.md)) удалена
  по решению владельца.
- **Эргономика нажатий и зазоров** ([`015`](tasks/015-key-gap-8.md), [`016`](tasks/016-hyphen-vs-space.md),
  [`018`](tasks/018-tap-in-gap.md)): подбор финальных `hGap`/`vGap` на устройстве, бесшовные хитбоксы
  клавиш в зазорах (`gap / 2`) и защита от случайного нажатия дефиса вместо пробела.
- **Полировка сот и техдолг** ([`001`](tasks/001-hex-polish.md), [`007`](tasks/007-code-cleanup.md),
  [`009`](tasks/009-tech-debt-review.md)): проверка `HoneycombHexGrid` на разных экранах, удаление
  остатков мёртвого кода и мелкие UX-исправления по итогам код-ревью.
- **Автозамена опечаток** ([`013`](tasks/013-microdictionary.md)): эксперимент для чувашского и русского;
  проверка на устройстве и подбор порогов, вопрос о ленте подсказок отложен.

---

## 5. Лицензия и приватность

- **Код проекта:** распространяется под лицензией [Apache License 2.0](LICENSE).
- **Шрифт и сторонние библиотеки:** шрифт Kesje Rounded (`app/src/main/res/font/kesje_rounded.ttf`) —
  изменённая версия Comfortaa (перерисована строчная «т»); распространяется под свободной лицензией **SIL Open Font License 1.1** (`OFL-1.1`); подробности —
  в [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- **Словари автозамены:** `app/src/main/assets/dict/cv.tsv` — из корпуса `alexantonov/chuvash_mono`
  (`CC0-1.0`); `app/src/main/assets/dict/ru.tsv` — из `hermitdave/FrequencyWords` (OpenSubtitles 2018),
  распространяется под **CC BY-SA 4.0** (лицензия относится только к этому файлу, код остаётся Apache-2.0);
  подробности — в [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- **Политика конфиденциальности:** [PRIVACY_POLICY.md](PRIVACY_POLICY.md) (приложение работает на 100 %
  офлайн и не запрашивает разрешение `INTERNET`).

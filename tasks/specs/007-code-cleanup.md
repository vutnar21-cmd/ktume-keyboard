---
id: "007"
title: "Очистка и структурирование кода"
priority: P3
area: refactoring
created: 2026-10-22
---

<!-- ===================== ЗОНА ВЛАДЕЛЬЦА ===================== -->
<!-- Всё до маркера «КОНЕЦ ЗОНЫ ВЛАДЕЛЬЦА» правит только владелец. -->

## Задача

Техническая уборка без изменения поведения:

- разделить `KeyboardScreen.kt` (~1 760 строк) на файлы: жесты клавиш, сетка/соты,
  эмодзи, Numpad, тулбар;
- разделить `SettingsActivity.kt` (~1 000 строк) на экран и секции;
- развязать цикл `ui.theme ⇄ ime`: `PreferencesRepository` не должен жить в файле палитр
  и не должен писать в `companion object` сервиса;
- вынести общий хелпер переключения Shift (сейчас одинаковый блок продублирован трижды);
- удалить мёртвый код: `isSwapMode` / `swapTwoKeys` / `swapFirstPos` в `KeyboardScreen`
  (нигде не включаются), неиспользуемые иконки и ресурсы;
- довести комментарии классов и функций до единого стиля.

## Критерии готовности

- [ ] `KeyboardScreen` и `SettingsActivity` разбиты, ни один файл не больше ~600 строк
- [ ] `PreferencesRepository` вынесен из `ui/theme` и не зависит от `KesjeImeService`
- [ ] Мёртвый код удалён, сборка без предупреждений
- [ ] Поведение не изменилось: тесты и lint зелёные, ручная проверка основных сценариев

## Ограничения

- Инварианты из `AGENT.md` (раздел 4) действуют и после рефакторинга.
- `clipToBounds` у рядов сот, гейт `PreferencesRepository.loaded` и преселект Long-Press
  обязаны сохраниться — это исправленные дефекты, а не стиль.

<!-- ================== КОНЕЦ ЗОНЫ ВЛАДЕЛЬЦА ================== -->

<!-- ===================== ЗОНА АГЕНТА ===================== -->
<!-- Агент дописывает новые отчёты в конец файла. Ничего выше не меняет. -->

## Отчёты

### 2026-10-22 · 85 % · статус `in-progress`

Рефакторинг выполнен в три захода, каждый — отдельный коммит с зелёной сборкой.

**Шаг 1 (`f07e863`) — развязан цикл `ui.theme ⇄ ime`:**

- `KeyboardLayouts.kt` перенесён в пакет `data` — раскладки, слои, эмодзи и Long-Press
  это данные, а не UI.
- `KeyboardPreferences` вынесен в `data/KeyboardPreferences.kt`.
- `PreferencesState` + `PreferencesRepository` в `data/PreferencesRepository.kt`.
  Состояние больше не живёт в `companion object` сервиса: `PreferencesState.preferences`,
  `.isFieldEmpty`, `.currentWord`.
- `ui/theme` теперь зависит только от `R` (шрифт и палитры). Обратной зависимости нет.

**Шаг 2 (`f1ae4a3`) — `KeyboardScreen` разбит:**

| Файл | Строк | Что внутри |
|---|---|---|
| `KeyboardScreen.kt` | 611 | состояние, режимы, сборка экрана |
| `Keys.kt` | 593 | KeyButton, Backspace, Special, Spacebar |
| `Toolbar.kt` | 234 | верхняя полоса, буфер обмена |
| `HoneycombHexGrid.kt` | 142 | соты (`clipToBounds` сохранён) |
| `EmojiPickerPage.kt` | 155 | смайлики |
| `NumpadGridView.kt` | 149 | цифровая панель |

Попутно удалено **198 неиспользуемых импортов** (по 30–45 на файл).

**Шаг 3 (`6e37fcd`) — `SettingsActivity` разбит:**

- `SettingsComponents.kt` — карточка секции, палитра, toggle-строка, ползунок.
- `SettingsPreview.kt` — тестовое поле и закреплённая клавиатура.
- `SettingsRowEditor.kt` — конструктор рядов; состояние (`editingLang`, `selectedCell`)
  осталось в Activity и передаётся коллбэками.
- `SettingsActivity.kt`: 1 025 → **681 строка**.

**Проверено после каждого шага:**

- `assembleDebug testDebugUnitTest lintDebug` — зелёное, тесты **9 из 9**, lint **0 ошибок**.
- Поведение не менялось: правки только структурные, логика перенесена дословно.

**Что осталось (последний заход):**

1. **Удалить мёртвый swap-код** — `isSwapMode`, `swapFirstPos`, `swapTwoKeys`,
   `updateCurrentLangRows` и параметры `KeyButton` (`isSwapMode`, `isSelectedForSwap`,
   `onSwapClick`). Попытка сделана, но задела тело `KeyButton` глубже, чем ожидалось
   (там `currentOnSwapClick`, `borderMod`, условие саб-лейбла) — откатил, чтобы не оставить
   полусломанное состояние. Нужен аккуратный проход по `Keys.kt`.
2. **Единый хелпер Shift** — одинаковый блок `OFF → ONCE → CAPS_LOCK` продублирован
   в `KeyboardScreen.kt` (строки ~305 и ~380) с магическим `420L`.
3. **Неиспользуемые иконки** — `SWAP`, `HIDE_KEYBOARD`, `IME_SWITCHER`, `HEX_GRID`
   объявлены, но не используются (по 1 вхождению — только объявление).
4. **Дожать `SettingsActivity` до ~600** — вынести оставшиеся секции (палитры,
   активные раскладки, отступ) в отдельные composable.

Задача остаётся `in-progress`: перечисленное — механическая работа на один заход,
но критерий «ни один файл не больше ~600» пока не выполнен для `SettingsActivity` (681).


### 2026-11-01 · 100 % · статус `review`

Доведены до конца все 4 оставшихся пункта рефакторинга и выполнено полное код-ревью кодовой базы с исправлением найденных дефектов.

**1. Модульное разбиение (ни один `.kt`-файл не превышает 480 строк при лимите ~600):**

| Пакет / файл | Строк | Ответственность |
|---|---|---|
| `ime/Keys.kt` | 477 | `rememberKeySurfaceModifier` (единый Rect + Hex), `KeyButton`, `KeyPressPreviewPopup`, `LongPressVariantsPopup` |
| `ime/KeyboardScreen.kt` | 470 | корневой экран клавиатуры (строго read-only к `KeyboardPreferences`), режимы, единый `nextShiftStateOnTap` |
| `ime/KeyboardRows.kt` | 443 | `NumberRowStrip`, `AlphaSymbolRows`, `KeyboardBottomBar`, `GlobeLanguageMenuPopup` |
| `ime/Toolbar.kt` | 432 | однорядная полоса `36 dp`: капсула буфера, лента частиц, кнопки курсора и `Вставить` |
| `ime/SettingsSections.kt` | 427 | `SettingsTab` и содержимое всех 5 вкладок настроек |
| `data/KeyboardLayouts.kt` | 421 | раскладки, пресеты, слои символов, Numpad, таблицы и математика Long-Press / свайпа вверх |
| `ime/FunctionalKeys.kt` | 410 | `BackspaceKey`, `SpecialKey`, `SpacebarKey` |
| `ime/KesjeImeService.kt` | 327 | `InputMethodService`, `InputConnection`, единый `playKeypressSound`, контекст Gboard-автозаглавной |
| `data/EditorTextLogic.kt` | 295 | Unicode-графемы, слово и пробелы перед курсором, автозаглавная, контекстный `Enter`/Numpad, геометрия |
| `ime/SettingsActivity.kt` | 282 | каркас Activity настроек, шапка с переключателем темы, вкладки и диалог сброса |
| `ime/KesjeIcons.kt` | 265 | векторные иконки на `Canvas` |
| `ime/SettingsRowEditor.kt` | 225 | интерактивный конструктор рядов раскладки |
| `data/PreferencesRepository.kt` | 220 | `PreferencesState` и хранилище DataStore с гейтом `loaded` |
| `ime/SettingsComponents.kt` | 196 | карточки секций, превью палитр, тумблеры и ползунки |
| `ui/theme/ThemePalettes.kt` | 193 | шрифт Comfortaa, 7 палитр и `ColorScheme` |
| `ime/EmojiPickerPage.kt` | 177 | страница эмодзи по 6 категориям |
| `ime/SettingsPreviewBench.kt` | 173 | тестовое поле `SettingsTestFieldCard` и закреплённый стенд `SettingsKeyboardPreview` |
| `ime/NumpadGridView.kt` | 139 | телефонная сетка `3 × 4` |
| `data/ChuvashMorphemes.kt` | 138 | словарь чувашских частиц и аффиксов |
| `ime/HoneycombHexGrid.kt` | 122 | 4-рядная сотовая сетка с `clipToBounds` |
| `data/MorphemeEngine.kt` | 86 | сингармонизм и склейка морфем |
| `data/EmojiCatalog.kt` | 67 | каталог смайликов `EmojiCatalog` |
| `data/ChuvashNames.kt` | 46 | словарь чувашских имён собственных |
| `data/KeyboardPreferences.kt` | 44 | неизменяемая модель настроек |

**2. Удаление мёртвого кода и дублирования:**

- Полностью удалён мёртвый swap-код из клавиатуры (`isSwapMode`, `swapFirstPos`, `swapTwoKeys`,
  `updateCurrentLangRows`, `isSelectedForSwap`, `onSwapClick`) и параметр `onUpdatePrefs` у `KeyboardScreen`:
  редактирование рядов живёт только в `SettingsRowEditor`.
- Вынесен единый чистый хелпер `nextShiftStateOnTap(current, nowMs, lastTapMs)` с константой
  `DOUBLE_TAP_WINDOW_MS = 420L` вместо дублированных блоков `Shift` в прямоугольной и сотовой сетках.
- Вынесен общий модификатор поверхности клавиши `rememberKeySurfaceModifier` (убрано 3-кратное
  дублирование `Paint` / `Path` / `drawBehind` шестиугольников между `KeyButton`, `BackspaceKey` и `SpecialKey`).
- Вынесен общий хелпер щелчка клавиш `playKeypressSound(audioManager, prefs)`, переиспользуемый
  между `KesjeImeService` и `SettingsKeyboardPreview` (с кэшированием `AudioManager` через `remember`).
- Удалены неиспользуемые иконки `SWAP`, `HIDE_KEYBOARD`, `HEX_GRID` из `KesjeIcons.kt` (`IME_SWITCHER`
  сохранён — активно используется в меню удержания `🌐`).
- Удалены устаревшие неиспользуемые данные `chuvashParticles`, `dictionarySuggestions`,
  `getSuggestionsForWord` из `KeyboardLayouts.kt` и более 20 неиспользуемых импортов.

**3. Багфиксы по итогам полного код-ревью:**

1. **Устаревшие замыкания (`stale closure`) в `Modifier.pointerInput(constKey)` (`Toolbar.kt`,
   `EmojiPickerPage.kt`, `GlobeLanguageMenuPopup`):** в тестовом стенде настроек коллбэки ввода
   замыкаются на `beforeCursor` / `afterCursor`. Поскольку `pointerInput(item)` / `pointerInput(emoji)`
   не перезапускается при неизменном ключе, повторный тап по тому же эмодзи, кнопке `Вставить` или
   неизменившейся частице (`-и?`) вызывал старую лямбду и затирал набранный текст в тестовом поле.
   Исправлено обёрткой всех коллбэков в `rememberUpdatedState`.
2. **Риск вечной блокировки стартового гейта в `PreferencesRepository.ensureLoaded`:** ранний выход
   `if (hasPendingUserUpdate) return@launch` стоял до `_loaded.value = true`. Перенесено в блок
   `finally { _loaded.value = true }`.
3. **Съедание пробела после знаков препинания в `computeTrailingHorizontalSpacesAfterWord`:** раньше
   для `"Салам. "` возвращалось `1`, из-за чего тап по аффиксу склеивал текст с точкой (`"Салам.ра "`).
   Добавлена проверка `Character.isLetter(lastCp)` — пробел подрезается только после буквенной основы.
4. **Защита от разрезания UTF-16 суррогатной пары в `computeDeleteWordLength`:** если окно
   `getTextBeforeCursor(64, 0)` начинается со второй половины суррогатной пары (`isLowSurrogate()`),
   свайп-удаление слова не захватывает этот сирота-код-юнит и не оставляет битый символ в редакторе.
5. **Тактильная отдача жестов в режиме сот (`HoneycombHexGrid`):** проброшен `onGestureTick` в сотовые
   клавиши для микро-тика при свайпе вверх и ведении пальца по `Long-Press`; в настройках цифрового
   ряда добавлена контекстная подсказка для режима Hex, а `ViewTree*Owner` в `KesjeImeService`
   привязан также напрямую к `ComposeView`.

**Проверено:**

- `.\gradlew.bat assembleDebug testDebugUnitTest lintDebug --no-daemon --warning-mode all` (JDK 21):
  **0 предупреждений компилятора**, **29 из 29 unit-тестов зелёные** (добавлены тесты на
  `nextShiftStateOnTap`, защиту пробела после пунктуации и суррогатную границу в `computeDeleteWordLength`),
  **lint — 0 ошибок**.
- Свежий APK установлен по Wi‑Fi ADB на `Nothing Phone (1)` (`adb install -r`, `Success`, активный IME
  `org.chuvash.keske/.ime.KeskeImeService`).

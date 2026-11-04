# Документация проекта «Кӗске Тӳме / кТӳме» (`Kĕske Tüme / kTüme`)

Добро пожаловать в техническую и пользовательскую документацию чувашской многоязычной клавиатуры **Кӗске Тӳме** (`org.chuvash.ktume`) для Android.

---

## Содержание раздела `docs/`

1. **[Устройство и работа всех функций (`features-and-mechanics.md`)](features-and-mechanics.md)**
   Подробное описание всех механик клавиатуры:
   - раскладки и пресеты (`BALANCED_26`, `HEX_4ROW`, `FULL_28`, Русская, Латиница, Греческая, конструктор рядов);
   - геометрия клавиш, паркетные соты (`HoneycombHexGrid`) и бесшовные хитбоксы (`hGap / 2`, `vGap / 2`);
   - жесты: всплывающая «капля», быстрый свайп вверх (`variants[0]`), Long-Press с зеркальным порядком на правой половине, свайп дефиса, свайп курсора по пробелу, двойной пробел и удаление слов;
   - конечный автомат регистра (`ShiftState`) и автозаглавная буква по правилам Gboard;
   - однорядная верхняя панель (`36 dp`), буфер обмена и исправление «ложной латиницы» (`ăĕçÿ → ӑӗҫӳ`);
   - слои символов (`?123`, `=\<`), телефонный `Numpad`, страница эмодзи и совместимость с терминалами (`TYPE_NULL`);
   - экспериментальная автозаглавная для чувашских имён и топонимов (`ChuvashNames`).
2. **[Система автозамены опечаток (`autocorrect.md`)](autocorrect.md)**
   Детальный разбор экспериментальной автозамены для чувашского и русского языков:
   - конвейер принятия решения от нажатия разделителя до `BatchEdit`;
   - структура компактного префиксного дерева `WordTrie` в плоских массивах;
   - модель цен опечаток `TypoCostModel` (соседние клавиши раскладки, чувашские надстрочные знаки `ӑ ӗ ҫ ӳ`, удвоения букв, штраф первой буквы);
   - математическая формула оценки кандидатов, бонус активного языка и пороги отсечения (`minGain`, `minMargin`);
   - откат одним нажатием `⌫` и устройство локального личного словаря;
   - источники корпусов (`alexantonov/chuvash_mono`, `hermitdave/FrequencyWords`), очистка опечаток через Hunspell (`hunspell.chv.su` и LibreOffice) и инструкция по пересборке `cv.tsv` / `ru.tsv`.
3. **[Исследование названия приложения (`app-name-research.md`)](app-name-research.md)**
   История выбора названия **Кӗске Тӳме / кТӳме** (*«Короткая кнопка / клавиша»*).

---

## Архитектура проекта

Приложение написано на **Kotlin** с использованием **Jetpack Compose (Material 3)** и **Android Jetpack DataStore**. Код разделён на три пакета с однонаправленными зависимостями без циклов:

```text
ui.theme  ◄──  ime  ──►  data (включая data.autocorrect)
```

| Пакет | Назначение | Зависимости |
|---|---|---|
| `org.chuvash.ktume.ui.theme` | Шрифт `Keske Rounded` (`Comfortaa` с русской строчной «т»), 7 цветовых палитр (`KeskePalettes`) и конвертация в `ColorScheme` Material 3 | Только ресурсы Android (`R`) |
| `org.chuvash.ktume.data` | Чистые структуры данных, раскладки, текстовые алгоритмы редактора (`EditorTextLogic`), каталог эмодзи, словарь имён, движок автозамены (`data.autocorrect`) и хранилище настроек `PreferencesRepository` / `PreferencesState` | Без зависимости от `InputMethodService` и Compose UI клавиатуры (легко покрывается быстрыми JVM unit-тестами) |
| `org.chuvash.ktume.ime` | Сервис клавиатуры `KeskeImeService`, экран и клавиши клавиатуры (`KeyboardScreen`, `Keys`, `FunctionalKeys`, `Toolbar`, `HoneycombHexGrid`, `NumpadGridView`, `EmojiPickerPage`) и студия настроек (`SettingsActivity`, `SettingsSections`, `SettingsPreviewBench`, `SettingsRowEditor`, `SettingsAutocorrect`) | Использует `data` и `ui.theme` |

### Поток состояния между сервисом и настройками (`PreferencesState`)

Сервис клавиатуры (`KeskeImeService`) и экран настроек (`SettingsActivity`) общаются не через `companion object` сервиса, а через реактивные потоки `StateFlow` в объекте `PreferencesState` (`data/PreferencesRepository.kt`):

- `preferences: StateFlow<KeyboardPreferences>` — текущие настройки внешнего вида, геометрии, жестов и раскладок;
- `personalWords: StateFlow<Set<String>>` — локальный личный словарь пользователя;
- `activeLang: StateFlow<KeyboardLang>` — активный язык ввода (переживает поворот экрана и перезапуск процесса);
- `shouldAutoCapitalize: StateFlow<Boolean>` и `editorContextVersion: StateFlow<Int>` — сигналы контекста поля ввода для автозаглавной буквы;
- `inputSessionId: StateFlow<Int>`, `enterKeyAction: StateFlow<EnterKeyAction>`, `preferNumericMode: StateFlow<Boolean>`, `isPasswordField: StateFlow<Boolean>` — контекст текущего поля (`EditorInfo`).

Защита от гонки при старте обеспечивается флагом `PreferencesRepository.loaded`: пока чтение DataStore с диска не завершилось, экран настроек не перезаписывает сохранённые данные дефолтным снимком.

---

## Принципы приватности и автономности

1. **Ноль разрешений в `AndroidManifest.xml`.** Приложение не запрашивает `INTERNET`, `READ_CONTACTS` и даже `VIBRATE` (тактильный отклик работает через системный `View.performHapticFeedback`).
2. **Все словари и шрифты встроены в APK.** Частотные словари `assets/dict/cv.tsv` и `ru.tsv`, список чувашских имён `ChuvashNames` и шрифт `res/font/keske_rounded.ttf` работают офлайн.
3. **Личный словарь и настройки хранятся только локально** во внутреннем хранилище приложения (`DataStore`). В полях с флагом инкогнито (`IME_FLAG_NO_PERSONALIZED_LEARNING`) личный словарь не пополняется.

---

## Вспомогательные скрипты (`scripts/`)

- [`scripts/fonts/patch_comfortaa_te.py`](../scripts/fonts/patch_comfortaa_te.py) — пересборка шрифта `keske_rounded.ttf` из оригинального `Comfortaa`: заменяет болгарскую форму глифа строчной «т» (`m`) на стандартную русскую «т», сохраняя фирменную Λ-образную «л».
- [`scripts/dictionaries/build_dictionaries.py`](../scripts/dictionaries/build_dictionaries.py) — воспроизводимая сборка частотных словарей автозамены `assets/dict/cv.tsv` и `ru.tsv` из открытых корпусов с фильтрацией опечаток через Hunspell.
- [`scripts/release.ps1`](../scripts/release.ps1) (и обёртка [`release.bat`](../release.bat)) — локальная сборка подписанного релизного APK, проверка SHA-256 отпечатка сертификата для Obtainium и публикация релиза через `gh` CLI без выгрузки приватного ключа `.jks` в облако.

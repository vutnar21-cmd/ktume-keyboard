# Документация проекта «Кӗсье Тӳме / кТӳме» (`Kĕsje Tüme / kTüme`)

Добро пожаловать в документацию чувашской многоязычной клавиатуры **Кӗсье Тӳме** (`org.chuvash.ktume`) для Android.

---

## Содержание раздела `docs/`

1. **[Архитектура и структура (`ARCHITECTURE.md`)](ARCHITECTURE.md)** — дерево репозитория, пакеты `ui.theme` / `data` / `ime` внутри `src/`, поток состояния `PreferencesState` и наборы тестов.
2. **[Журнал решений и идей (`DECISIONS.md`)](DECISIONS.md)** — принятые, заменённые и отклонённые продуктовые/архитектурные решения (`принято` / `заменено` / `отклонено` / `отложено`).
3. **[Книга граблей (`PITFALLS.md`)](PITFALLS.md)** — тонкости сборки Gradle/JDK на Windows, Jetpack DataStore, сотовой сетки и `InputConnection`.
4. **[Устройство и работа всех функций (`features-and-mechanics.md`)](features-and-mechanics.md)** — раскладки, жесты, верхняя полоса, автозаглавная и слои символов.
5. **[Система автозамены опечаток (`autocorrect.md`)](autocorrect.md)** — алгоритмы `WordTrie`, `TypoCostModel`, формула оценки и пересборка словарей `cv.tsv` / `ru.tsv`.
6. **[Политика конфиденциальности (`PRIVACY_POLICY.md`)](PRIVACY_POLICY.md)** — 100 % офлайн-работа и отсутствие разрешений.
7. **[Сторонние компоненты и лицензии (`THIRD_PARTY_NOTICES.md`)](THIRD_PARTY_NOTICES.md)** — атрибуция шрифта `Kesje Rounded` (`OFL-1.1`), словарей и библиотек.

Исследовательские черновики и референсы глифов ведутся локально в папке `research/` (вне Git).

## Вспомогательные скрипты (`tools/`)

- [`tools/fonts/patch_comfortaa_te.py`](../tools/fonts/patch_comfortaa_te.py) — пересборка шрифта `kesje_rounded.ttf` из оригинального `Comfortaa` (русская строчная «т» с сохранением Λ-«л»).
- [`tools/dictionaries/build_dictionaries.py`](../tools/dictionaries/build_dictionaries.py) — воспроизводимая сборка частотных словарей автозамены `src/app/src/main/assets/dict/cv.tsv` и `ru.tsv`.
- [`tools/release.ps1`](../tools/release.ps1) (и обёртка [`tools/release.bat`](../tools/release.bat)) — локальная сборка подписанного релизного APK, сверка SHA-256 отпечатка сертификата для Obtainium и публикация релиза через `gh` CLI.

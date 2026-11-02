# Сторонние компоненты и лицензии / Third-Party Notices

Исходный код приложения **Кӗске Тӳме / кТӳме** (`Kĕske Tüme / kTüme`, `org.chuvash.ktume`)
распространяется под лицензией [Apache License 2.0](LICENSE). В состав проекта входят следующие
свободные компоненты:

## 1. Шрифт Keske Rounded на основе Comfortaa (`app/src/main/res/font/keske_rounded.ttf`)

- **Автор оригинала:** Johan Aakerlund (и соавторы проекта Comfortaa)
- **Лицензия:** SIL Open Font License, Version 1.1 (`OFL-1.1`, https://openfontlicense.org)
- **Источник:** https://fonts.google.com/specimen/Comfortaa
- **Изменения:** строчная «т» (U+0442) перерисована в стандартной русской форме из штрихов самого
  шрифта (скрипт [`scripts/fonts/patch_comfortaa_te.py`](scripts/fonts/patch_comfortaa_te.py)).
  Так как «Comfortaa» — зарезервированное имя шрифта (Reserved Font Name), изменённая версия
  называется «Keske Rounded» и распространяется под той же лицензией OFL-1.1.

## 2. Библиотеки AndroidX, Jetpack Compose и Kotlin Standard Library

- **Правообладатели:** The Android Open Source Project, JetBrains s.r.o.
- **Лицензия:** Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0)

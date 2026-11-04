# Сторонние компоненты и лицензии / Third-Party Notices

Исходный код приложения **Кӗсье Тӳме / кТӳме** (`Kĕsje Tüme / kTüme`, `org.chuvash.ktume`)
распространяется под лицензией [Apache License 2.0](LICENSE). В состав проекта входят следующие
свободные компоненты:

## 1. Шрифт Kesje Rounded на основе Comfortaa (`app/src/main/res/font/kesje_rounded.ttf`)

- **Автор оригинала:** Johan Aakerlund (и соавторы проекта Comfortaa)
- **Лицензия:** SIL Open Font License, Version 1.1 (`OFL-1.1`, https://openfontlicense.org)
- **Источник:** https://fonts.google.com/specimen/Comfortaa
- **Изменения:** строчная «т» (U+0442) перерисована в стандартной русской форме из штрихов самого
  шрифта (скрипт [`scripts/fonts/patch_comfortaa_te.py`](scripts/fonts/patch_comfortaa_te.py)).
  Так как «Comfortaa» — зарезервированное имя шрифта (Reserved Font Name), изменённая версия
  называется «Kesje Rounded» и распространяется под той же лицензией OFL-1.1.

## 2. Библиотеки AndroidX, Jetpack Compose и Kotlin Standard Library

- **Правообладатели:** The Android Open Source Project, JetBrains s.r.o.
- **Лицензия:** Apache License, Version 2.0 (https://www.apache.org/licenses/LICENSE-2.0)

## 3. Чувашский частотный словарь автозамены (`app/src/main/assets/dict/cv.tsv`)

- **Источник:** корпус чувашских текстов [alexantonov/chuvash_mono](https://huggingface.co/datasets/alexantonov/chuvash_mono)
  на Hugging Face (~3,1 млн предложений; автор набора — пользователь Hugging Face `alexantonov`)
- **Лицензия источника:** Creative Commons Zero v1.0 Universal (`CC0-1.0`,
  https://creativecommons.org/publicdomain/zero/1.0/) — общественное достояние, без ограничений
- **Изменения:** из корпуса посчитаны частоты словоформ (скрипт
  [`scripts/dictionaries/build_dictionaries.py`](scripts/dictionaries/build_dictionaries.py)); оставлены
  только кириллические слова в нижнем регистре; «ложная латиница» `ă ĕ ç ÿ ŭ` внутри кириллических слов
  приведена к `ӑ ӗ ҫ ӳ`; убраны написания без надстрочных знаков, если вариант со знаками встречается
  в 30 раз чаще и более, а также явные опечатки корпуса (отвергнутые на этапе сборки проверкой Hunspell
  `Chuvash_Spell 1.13` с `hunspell.chv.su`, если в одной правке есть слово в 20 раз частотнее; сам словарь
  Hunspell в приложение не включается); в словарь вошли 160 000 самых частых форм (частота ≥ 4). Источник,
  условия отбора и контрольные суммы исходных файлов записаны в шапке `cv.tsv`.

## 4. Русский частотный словарь автозамены (`app/src/main/assets/dict/ru.tsv`)

- **Источник:** [hermitdave/FrequencyWords](https://github.com/hermitdave/FrequencyWords), файл
  `content/2018/ru/ru_full.txt` — частотный список слов по субтитрам OpenSubtitles 2018
  (автор проекта — Hermit Dave; исходные данные — [OpenSubtitles.org](https://www.opensubtitles.org/)
  через корпус [OPUS](https://opus.nlpl.eu/))
- **Лицензия:** Creative Commons Attribution-ShareAlike 4.0 International (`CC BY-SA 4.0`,
  https://creativecommons.org/licenses/by-sa/4.0/)
- **Изменения:** оставлены только слова из кириллических букв, приведённые к нижнему регистру; убраны
  явные опечатки субтитров (отвергнутые на этапе сборки проверкой Hunspell `ru_RU` из LibreOffice, если
  в одной правке есть слово в 20 раз частотнее; сам словарь Hunspell в приложение не включается); в словарь
  вошли 160 000 самых частых форм (частота ≥ 17) — скрипт
  [`scripts/dictionaries/build_dictionaries.py`](scripts/dictionaries/build_dictionaries.py). Источник,
  лицензия, условия отбора и контрольная сумма исходного файла записаны в шапке `ru.tsv`.
- **Важно:** лицензия CC BY-SA 4.0 распространяется **только на файл `app/src/main/assets/dict/ru.tsv`**
  (производный список слов, который распространяется на тех же условиях CC BY-SA 4.0). Исходный код
  приложения, включая движок автозамены и скрипт сборки словарей, остаётся под лицензией Apache-2.0.

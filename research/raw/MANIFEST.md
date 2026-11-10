# Опись локальных исходников (`research/raw/` и кэши корпусов)

Тяжёлые внешние корпуса не хранятся в Git, чтобы репозиторий оставался лёгким и автономным.

| Расположение | Источник / лицензия | Назначение |
| --- | --- | --- |
| `tools/dictionaries/.cache/` | `alexantonov/chuvash_mono` (Hugging Face, ~3,1 млн предложений, `CC0-1.0`); `hermitdave/FrequencyWords` (`ru_full.txt`, OpenSubtitles 2018, `CC BY-SA 4.0`); словари Hunspell `cv_RU` (`hunspell.chv.su`, 1.13) и `ru_RU` (LibreOffice) | Скачивается автоматически скриптом `tools/dictionaries/build_dictionaries.py` для пересборки `src/app/src/main/assets/dict/cv.tsv` и `ru.tsv`, а также выборок `*_holdout.tsv` для `AutocorrectDictionaryTest`. |

"""
Сборка частотных словарей автозамены: src/app/src/main/assets/dict/cv.tsv и ru.tsv.

Формат вывода: строки `# ...` — комментарии (источник, лицензия), затем `слово<TAB>частота`,
отсортировано по убыванию частоты. Все слова в нижнем регистре, только кириллица
(в чувашском — плюс ӑ ӗ ҫ ӳ); «ложная латиница» (ă ĕ ç ÿ ŭ) внутри кириллических слов
приводится к чувашским буквам, как при вставке из буфера в самой клавиатуре.

Источники (скачиваются в tools/dictionaries/.cache/, в репозиторий не попадают):
  - чувашский: alexantonov/chuvash_mono (Hugging Face, ~3,1 млн предложений, CC0-1.0);
  - русский: hermitdave/FrequencyWords, content/2018/ru/ru_full.txt (OpenSubtitles 2018, CC BY-SA 4.0);
  - фильтр опечаток корпуса при сборке: Hunspell cv_RU (hunspell.chv.su, 1.13) и ru_RU (LibreOffice).

Запуск (нужны pyarrow и spylls только для пересборки, приложению они не нужны):
    python -m pip install pyarrow spylls
    python tools/dictionaries/build_dictionaries.py
"""
from __future__ import annotations

import collections
import hashlib
import re
import sys
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(__file__).resolve().parent / ".cache"
OUT = ROOT / "src" / "app" / "src" / "main" / "assets" / "dict"

CV_SOURCES = [
    "https://huggingface.co/datasets/alexantonov/chuvash_mono/resolve/main/data/train-00000-of-00001.parquet",
    "https://huggingface.co/datasets/alexantonov/chuvash_mono/resolve/main/data/train-kusaru-20261018.parquet",
]
RU_SOURCE = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/ru/ru_full.txt"

# Пороги отбора: слово должно встретиться не реже MIN_COUNT раз; в словарь идут не более MAX_WORDS слов.
CV_MIN_COUNT = 3
CV_MAX_WORDS = 160_000
RU_MIN_COUNT = 3
RU_MAX_WORDS = 160_000
# Вариант без надстрочных знаков выбрасывается, если вариант со знаками чаще во столько раз
CV_VARIANT_RATIO = 30
# Слово, отвергнутое Hunspell, выбрасывается, если в одной правке есть слово чаще во столько раз
TYPO_RATIO = 20
RU_HUNSPELL = "https://raw.githubusercontent.com/LibreOffice/dictionaries/master/ru_RU"
CV_HUNSPELL = "http://hunspell.chv.su/files/chuvash_spell_dictionary-1.13.xpi"
PLAIN = str.maketrans({"ӑ": "а", "ӗ": "е", "ҫ": "с", "ӳ": "у"})

FALSE_LATIN = str.maketrans({"ă": "ӑ", "ĕ": "ӗ", "ç": "ҫ", "ÿ": "ӳ", "ŭ": "ӳ",
                             "Ă": "Ӑ", "Ĕ": "Ӗ", "Ç": "Ҫ", "Ÿ": "Ӳ", "Ŭ": "Ӳ"})
TOKEN = re.compile(r"[^\W\d_]+")
CYRILLIC = re.compile(r"[\u0400-\u04FF]")
CV_WORD = re.compile(r"^[а-яёӑӗҫӳ]+$")
RU_WORD = re.compile(r"^[а-яё]+$")


def download(url: str) -> Path:
    CACHE.mkdir(parents=True, exist_ok=True)
    target = CACHE / url.rsplit("/", 1)[-1]
    if target.exists() and target.stat().st_size > 0:
        return target
    print(f"скачиваю {url}", file=sys.stderr)
    tmp = target.with_suffix(target.suffix + ".part")
    with urllib.request.urlopen(url) as resp, open(tmp, "wb") as out:
        while chunk := resp.read(1 << 20):
            out.write(chunk)
    tmp.replace(target)
    return target


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(1 << 20):
            h.update(chunk)
    return h.hexdigest()


def normalize_token(token: str) -> str:
    if CYRILLIC.search(token):
        token = token.translate(FALSE_LATIN)
    return token.lower()


def count_chuvash(paths: list[Path]) -> collections.Counter:
    import pyarrow.parquet as pq

    counts: collections.Counter = collections.Counter()
    for path in paths:
        pf = pq.ParquetFile(path)
        column = pf.schema_arrow.names[0]
        for batch in pf.iter_batches(columns=[column], batch_size=20_000):
            for text in batch.column(0).to_pylist():
                if not text:
                    continue
                for token in TOKEN.findall(text):
                    word = normalize_token(token)
                    if CV_WORD.match(word):
                        counts[word] += 1
    return counts


def count_russian(path: Path) -> collections.Counter:
    counts: collections.Counter = collections.Counter()
    with open(path, encoding="utf-8") as f:
        for line in f:
            parts = line.split()
            if len(parts) != 2:
                continue
            word, raw = normalize_token(parts[0]), parts[1]
            if RU_WORD.match(word) and raw.isdigit():
                counts[word] += int(raw)
    return counts


def select(counts: collections.Counter, min_count: int, max_words: int) -> list[tuple[str, int]]:
    return [(w, c) for w, c in counts.most_common() if c >= min_count][:max_words]


def write_tsv(path: Path, selected: list[tuple[str, int]], header: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        for line in header:
            f.write(f"# {line}\n")
        for word, count in selected:
            f.write(f"{word}\t{count}\n")


def drop_diacriticless_variants(counts: collections.Counter, ratio: int) -> int:
    """
    Убирает из чувашского словаря «облегчённые» написания без надстрочных знаков, если в корпусе
    есть вариант со знаками, который встречается в [ratio] раз чаще («весем» при «вӗсем»).
    Иначе такая опечатка корпуса считалась бы известным словом и не исправлялась.
    """
    groups: dict[str, list[str]] = collections.defaultdict(list)
    for word in counts:
        groups[word.translate(PLAIN)].append(word)
    dropped = 0
    for words in groups.values():
        if len(words) < 2:
            continue
        top = max(counts[w] for w in words)
        for w in words:
            if counts[w] * ratio <= top:
                del counts[w]
                dropped += 1
    return dropped


def write_holdout(path: Path, counts: collections.Counter, lo: int, hi: int, limit: int) -> None:
    """Редкие формы (не вошли в словарь) — для оценки ложных исправлений в unit-тесте."""
    rare = [w for w, c in counts.items() if lo <= c <= hi]
    rare.sort()
    step = max(1, len(rare) // limit)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        for w in rare[::step][:limit]:
            f.write(f"{w}\t{counts[w]}\n")


def deletes(word: str):
    yield word
    for i in range(len(word)):
        yield word[:i] + word[i + 1:]


def drop_corpus_typos(counts: collections.Counter, speller, ratio: int, attractors: int = 40_000) -> list[tuple[str, str]]:
    """
    Убирает опечатки самого корпуса («пожалйста», «здраствуйте»): слово выбрасывается, только если
    его отвергает Hunspell И рядом (одна правка) есть слово в [ratio] раз частотнее. Слова, которые
    Hunspell признаёт («танк» рядом с «так», «ывӑл» рядом с «вӑл»), остаются.
    Hunspell используется только при сборке и в приложение не попадает.
    """
    ranked = [w for w, _ in counts.most_common(attractors)]
    index: dict[str, list[str]] = collections.defaultdict(list)
    for w in ranked:
        for k in set(deletes(w)):
            index[k].append(w)
    dropped = []
    for w, c in list(counts.items()):
        if len(w) < 3:
            continue
        neighbour = None
        for k in set(deletes(w)):
            for a in index.get(k, ()):
                if a != w and counts[a] >= c * ratio:
                    neighbour = a
                    break
            if neighbour:
                break
        if neighbour and not speller.lookup(w):
            dropped.append((w, neighbour))
    for w, _ in dropped:
        del counts[w]
    return dropped


def load_speller(name: str):
    """Hunspell-словарь из кэша (ru_RU — LibreOffice dictionaries, cv_RU — hunspell.chv.su)."""
    from spylls.hunspell import Dictionary

    return Dictionary.from_files(str(CACHE / name))


def ensure_hunspell() -> None:
    for name in ("ru_RU.dic", "ru_RU.aff"):
        if not (CACHE / name).exists():
            download(f"{RU_HUNSPELL}/{name}")
    if not (CACHE / "cv_RU.dic").exists():
        xpi = download(CV_HUNSPELL)
        with zipfile.ZipFile(xpi) as z:
            for name in ("cv_RU.aff", "cv_RU.dic"):
                (CACHE / name).write_bytes(z.read(f"dictionaries/{name}"))


def main() -> None:
    cv_paths = [download(u) for u in CV_SOURCES]
    ru_path = download(RU_SOURCE)
    ensure_hunspell()

    cv = count_chuvash(cv_paths)
    dropped = drop_diacriticless_variants(cv, CV_VARIANT_RATIO)
    print(f"cv: убрано вариантов без надстрочных знаков: {dropped}", file=sys.stderr)
    typos = drop_corpus_typos(cv, load_speller("cv_RU"), TYPO_RATIO)
    print(f"cv: убрано опечаток корпуса: {len(typos)}", file=sys.stderr)
    cv_selected = select(cv, CV_MIN_COUNT, CV_MAX_WORDS)
    cv_cutoff = cv_selected[-1][1]
    write_holdout(CACHE / "cv_holdout.tsv", cv, 2, cv_cutoff - 1, 5000)
    write_tsv(
        OUT / "cv.tsv", cv_selected,
        [
            "Чувашский частотный словарь автозамены Кӗсье Тӳме.",
            "Источник: alexantonov/chuvash_mono (https://huggingface.co/datasets/alexantonov/chuvash_mono), CC0-1.0.",
            f"Изменения: только кириллица, нижний регистр, частота >= {cv_cutoff}, {len(cv_selected)} слов;",
            f"убраны написания без ӑӗҫӳ, если вариант со знаками чаще в {CV_VARIANT_RATIO} раз и более.",
            "Сборка: scripts/dictionaries/build_dictionaries.py; "
            + ", ".join(f"{p.name} sha256={sha256(p)[:16]}" for p in cv_paths),
        ],
    )
    ru = count_russian(ru_path)
    ru_typos = drop_corpus_typos(ru, load_speller("ru_RU"), TYPO_RATIO)
    print(f"ru: убрано опечаток корпуса: {len(ru_typos)}", file=sys.stderr)
    with open(CACHE / "dropped_typos.tsv", "w", encoding="utf-8", newline="\n") as f:
        for w, a in typos + ru_typos:
            f.write(f"{w}\t{a}\n")
    ru_selected = select(ru, RU_MIN_COUNT, RU_MAX_WORDS)
    write_holdout(CACHE / "ru_holdout.tsv", ru, max(2, ru_selected[-1][1] // 2), ru_selected[-1][1] - 1, 5000)
    write_tsv(
        OUT / "ru.tsv", ru_selected,
        [
            "Русский частотный словарь автозамены Кӗсье Тӳме.",
            "Источник: hermitdave/FrequencyWords, OpenSubtitles 2018 (https://github.com/hermitdave/FrequencyWords),",
            "лицензия CC BY-SA 4.0 (https://creativecommons.org/licenses/by-sa/4.0/). Изменения: только кириллица,",
            f"нижний регистр, частота >= {ru_selected[-1][1]}, {len(ru_selected)} слов.",
            f"Сборка: scripts/dictionaries/build_dictionaries.py; {ru_path.name} sha256={sha256(ru_path)[:16]}",
        ],
    )
    print(f"cv: {len(cv)} уникальных форм, в словаре {len(cv_selected)} (частота >= {cv_cutoff})", file=sys.stderr)
    print(f"ru: {len(ru)} уникальных форм, в словаре {len(ru_selected)}", file=sys.stderr)


if __name__ == "__main__":
    main()

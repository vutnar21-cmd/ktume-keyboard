package org.chuvash.ktume.data.autocorrect

/**
 * МОДУЛЬ: WordTrie.kt — компактное префиксное дерево частотного словаря автозамены.
 *
 * Узлы лежат в плоских массивах (дети одного узла — непрерывный блок), поэтому словарь на
 * ~150 тыс. словоформ занимает единицы мегабайт и строится за один проход по отсортированному списку.
 * Поиск исправлений — обход дерева с построчным расчётом взвешенного расстояния Дамерау–Левенштейна
 * ([TypoCostModel]) и отсечением ветвей, где минимум строки уже превысил допустимую цену.
 *
 * Не зависит от Android: строится и проверяется в JVM unit-тестах.
 */
class WordTrie private constructor(
    private val chars: CharArray,
    private val firstChild: IntArray,
    private val childCount: ShortArray,
    private val counts: IntArray,
    /** Число слов в словаре. */
    val size: Int,
    /** Сумма частот всех слов: знаменатель относительной частоты. */
    val totalCount: Long,
    /** Наименьшая частота в словаре — порог отбора: «неизвестное» слово могло бы быть чуть реже. */
    val minCount: Int
) {

    /** Кандидат исправления: слово словаря, цена правки (в единицах [TypoCostModel]) и частота. */
    data class Match(val word: String, val cost: Int, val count: Int)

    /** Частота слова или `0`, если слова в словаре нет. Ожидает слово в нижнем регистре. */
    fun countOf(word: String): Int {
        var node = ROOT
        for (ch in word) {
            node = findChild(node, ch)
            if (node < 0) return 0
        }
        return counts[node]
    }

    operator fun contains(word: String): Boolean = countOf(word) > 0

    /**
     * Все слова, отличающиеся от [typed] не дороже [maxCost] по модели [costs].
     * [typed] — в нижнем регистре; результат не отсортирован.
     */
    fun search(typed: String, maxCost: Int, costs: TypoCostModel): List<Match> {
        if (typed.isEmpty() || maxCost < 0) return emptyList()
        val n = typed.length
        // Каждая полная правка стоит не меньше одной единицы: длиннее typed + maxCost кандидат быть не может.
        val maxDepth = n + maxCost
        val rows = Array(maxDepth + 1) { IntArray(n + 1) }
        val path = CharArray(maxDepth + 1)
        val insertCosts = IntArray(n) { j -> costs.insertionCost(typed, j) }
        // Строка глубины 0: пустой префикс кандидата превращается в первые j набранных символов вставками
        for (j in 1..n) rows[0][j] = rows[0][j - 1] + insertCosts[j - 1]
        val out = ArrayList<Match>()
        val first = firstChild[ROOT]
        for (k in 0 until childCount[ROOT]) {
            walk(first + k, 1, typed, maxCost, maxDepth, costs, insertCosts, rows, path, out)
        }
        return out
    }

    private fun walk(
        node: Int,
        depth: Int,
        typed: String,
        maxCost: Int,
        maxDepth: Int,
        costs: TypoCostModel,
        insertCosts: IntArray,
        rows: Array<IntArray>,
        path: CharArray,
        out: MutableList<Match>
    ) {
        val c = chars[node]
        path[depth] = c
        val prev = rows[depth - 1]
        val row = rows[depth]
        val n = typed.length
        val prevC = if (depth >= 2) path[depth - 1] else NO_CHAR
        val omitCost = costs.omissionCost(c, prevC)
        // Правки первой буквы дороже: в начале слова ошибаются реже всего
        val firstExtra = if (depth == 1) costs.firstLetterExtra else 0
        row[0] = prev[0] + omitCost + firstExtra
        var rowMin = row[0]
        for (j in 1..n) {
            val t = typed[j - 1]
            val subst = costs.substitutionCost(c, t)
            var best = prev[j - 1] + subst + if (j == 1 && subst > 0) firstExtra else 0
            val omit = prev[j] + omitCost
            if (omit < best) best = omit
            val insert = row[j - 1] + insertCosts[j - 1]
            if (insert < best) best = insert
            if (depth >= 2 && j >= 2 && c == typed[j - 2] && prevC == t && c != t) {
                val swap = rows[depth - 2][j - 2] + costs.transpositionCost
                if (swap < best) best = swap
            }
            row[j] = best
            if (best < rowMin) rowMin = best
        }
        if (counts[node] > 0 && row[n] <= maxCost) {
            out.add(Match(String(path, 1, depth), row[n], counts[node]))
        }
        if (rowMin > maxCost || depth >= maxDepth) return
        val first = firstChild[node]
        for (k in 0 until childCount[node]) {
            walk(first + k, depth + 1, typed, maxCost, maxDepth, costs, insertCosts, rows, path, out)
        }
    }

    private fun findChild(node: Int, ch: Char): Int {
        var lo = firstChild[node]
        var hi = lo + childCount[node] - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = chars[mid]
            when {
                c < ch -> lo = mid + 1
                c > ch -> hi = mid - 1
                else -> return mid
            }
        }
        return -1
    }

    companion object {
        private const val ROOT = 0
        private const val NO_CHAR = '\u0000'

        /**
         * Строит дерево из пар «слово → частота». Слова приводятся к нижнему регистру,
         * повторы складываются, пустые и неположительные частоты отбрасываются.
         */
        fun build(entries: Map<String, Int>): WordTrie {
            val merged = HashMap<String, Int>(entries.size * 2)
            for ((w, c) in entries) {
                if (w.isEmpty() || c <= 0) continue
                val key = w.lowercase()
                merged[key] = (merged[key] ?: 0) + c
            }
            val words = merged.keys.toTypedArray()
            words.sort()
            val wordCounts = IntArray(words.size) { merged.getValue(words[it]) }
            return Builder(words, wordCounts).build()
        }

        /**
         * Строит дерево прямо из строк словаря `слово<TAB>частота` (строки `#` — комментарии) без
         * промежуточной карты: строки сортируются целиком — табуляция меньше любой буквы, поэтому
         * порядок совпадает с порядком слов. Слова ожидаются в нижнем регистре; повторы складываются.
         */
        fun fromTsv(lines: Sequence<String>): WordTrie {
            val sorted = ArrayList<String>()
            for (raw in lines) {
                if (raw.isBlank() || raw[0] == '#') continue
                sorted.add(raw.trimEnd())
            }
            sorted.sort()
            val words = ArrayList<String>(sorted.size)
            val wordCounts = IntArray(sorted.size)
            for (line in sorted) {
                val tab = line.indexOf('\t')
                val word = if (tab < 0) line else line.substring(0, tab)
                val count = if (tab < 0) 1 else line.substring(tab + 1).trim().toIntOrNull() ?: continue
                if (word.isEmpty() || count <= 0) continue
                if (words.isNotEmpty() && words[words.size - 1] == word) {
                    wordCounts[words.size - 1] += count
                } else {
                    wordCounts[words.size] = count
                    words.add(word)
                }
            }
            return Builder(words.toTypedArray(), wordCounts.copyOf(words.size)).build()
        }

        /**
         * Разбирает словарь в формате `слово<TAB>частота` (строки `#` — комментарии).
         * Строки без частоты получают частоту `1`.
         */
        fun parseTsv(lines: Sequence<String>): Map<String, Int> {
            val result = HashMap<String, Int>()
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val tab = line.indexOf('\t')
                val word = if (tab < 0) line else line.substring(0, tab)
                val count = if (tab < 0) 1 else line.substring(tab + 1).trim().toIntOrNull() ?: continue
                if (word.isNotEmpty()) result[word] = count
            }
            return result
        }
    }

    /** Однопроходная сборка по отсортированному списку: дети каждого узла выделяются одним блоком. */
    private class Builder(private val words: Array<String>, private val wordCounts: IntArray) {
        private var chars = CharArray(1024)
        private var firstChild = IntArray(1024)
        private var childCount = ShortArray(1024)
        private var counts = IntArray(1024)
        private var nodeCount = 1
        private var total = 0L

        fun build(): WordTrie {
            var min = Int.MAX_VALUE
            for (c in wordCounts) {
                total += c
                if (c < min) min = c
            }
            fill(ROOT, 0, words.size, 0)
            return WordTrie(
                chars.copyOf(nodeCount),
                firstChild.copyOf(nodeCount),
                childCount.copyOf(nodeCount),
                counts.copyOf(nodeCount),
                words.size,
                total,
                if (words.isEmpty()) 1 else min
            )
        }

        /** Заполняет узел [node] для слов `[lo, hi)`, общий префикс которых имеет длину [depth]. */
        private fun fill(node: Int, lo: Int, hi: Int, depth: Int) {
            var start = lo
            if (start < hi && words[start].length == depth) {
                counts[node] = wordCounts[start]
                start++
            }
            // Сначала считаем группы по символу на позиции depth, затем выделяем блок детей
            var groups = 0
            var i = start
            while (i < hi) {
                val ch = words[i][depth]
                while (i < hi && words[i][depth] == ch) i++
                groups++
            }
            if (groups == 0) return
            val block = allocate(groups)
            firstChild[node] = block
            childCount[node] = groups.toShort()
            var g = 0
            i = start
            while (i < hi) {
                val ch = words[i][depth]
                val groupStart = i
                while (i < hi && words[i][depth] == ch) i++
                chars[block + g] = ch
                fill(block + g, groupStart, i, depth + 1)
                g++
            }
        }

        private fun allocate(n: Int): Int {
            val start = nodeCount
            val need = nodeCount + n
            if (need > chars.size) {
                var cap = chars.size
                while (cap < need) cap *= 2
                chars = chars.copyOf(cap)
                firstChild = firstChild.copyOf(cap)
                childCount = childCount.copyOf(cap)
                counts = counts.copyOf(cap)
            }
            nodeCount = need
            return start
        }
    }
}

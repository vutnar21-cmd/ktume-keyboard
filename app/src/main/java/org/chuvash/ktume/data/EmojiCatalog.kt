package org.chuvash.ktume.data

/**
 * МОДУЛЬ: EmojiCatalog.kt — категории, стартовый набор и учёт недавних смайликов для страницы эмодзи.
 *
 * Все категории кратны 8 смайликам (`8` колонок в ряду без неполных «хвостов»):
 * 0. `🕒 Недавние` (24 = 3 × 8)
 * 1. `😊 Эмоции` (56 = 7 × 8)
 * 2. `👍 Жесты и люди` (48 = 6 × 8)
 * 3. `❤️ Сердца и символы` (48 = 6 × 8)
 * 4. `🌿 Природа и животные` (48 = 6 × 8)
 * 5. `☕ Еда, праздник и быт` (48 = 6 × 8)
 */
data class EmojiCategory(
    val id: String,
    val icon: String,
    val title: String,
    val emojis: List<String>
)

internal object EmojiCatalog {

    const val MAX_RECENT_EMOJIS = 24

    val defaultRecentEmojis: List<String> = listOf(
        "😊", "😂", "❤️", "👍", "🙏", "🔥", "😍", "🥰",
        "🥳", "😎", "🤗", "✨", "🎉", "🤝", "☀️", "🌺",
        "🤣", "😘", "👌", "💪", "💖", "✅", "🌸", "☕"
    )

    /**
     * Формирует полный список из [MAX_RECENT_EMOJIS] смайликов для вкладки «Недавние»:
     * пользовательские недавние идут первыми, недостающие слоты дополняются из [defaultRecentEmojis].
     */
    fun effectiveRecentEmojis(recentEmojis: List<String>): List<String> {
        val result = ArrayList<String>(MAX_RECENT_EMOJIS)
        for (emoji in recentEmojis) {
            val trimmed = emoji.trim()
            if (trimmed.isNotEmpty() && trimmed !in result) {
                result.add(trimmed)
                if (result.size >= MAX_RECENT_EMOJIS) return result
            }
        }
        for (fallback in defaultRecentEmojis) {
            if (fallback !in result) {
                result.add(fallback)
                if (result.size >= MAX_RECENT_EMOJIS) break
            }
        }
        return result
    }

    /**
     * Записывает выбранный [emoji] на 1-е место списка недавних (без дублей, максимум [MAX_RECENT_EMOJIS]).
     */
    fun recordRecentEmoji(current: List<String>, emoji: String): List<String> {
        val clean = emoji.trim()
        if (clean.isEmpty()) return current
        val base = if (current.isEmpty()) defaultRecentEmojis else current
        val updated = ArrayList<String>(MAX_RECENT_EMOJIS)
        updated.add(clean)
        for (item in base) {
            if (item != clean && item.isNotBlank() && item !in updated) {
                updated.add(item)
                if (updated.size >= MAX_RECENT_EMOJIS) break
            }
        }
        return updated
    }

    val thematicCategories: List<EmojiCategory> = listOf(
        EmojiCategory(
            id = "smileys",
            icon = "😊",
            title = "Эмоции",
            emojis = listOf(
                "😊", "😂", "🤣", "😍", "🥰", "😘", "😎", "🤩",
                "🥳", "🤗", "🤔", "😏", "😌", "😉", "🙃", "😇",
                "😅", "😆", "😁", "😄", "😃", "😀", "🥲", "😋",
                "😛", "😜", "🤪", "🤨", "🧐", "🤓", "😒", "😞",
                "😔", "😟", "😕", "🙁", "😣", "😖", "😫", "😩",
                "🥺", "😢", "😭", "😤", "😠", "😡", "🤯", "😳",
                "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "😴"
            )
        ),
        EmojiCategory(
            id = "gestures",
            icon = "👍",
            title = "Жесты и люди",
            emojis = listOf(
                "👍", "👎", "👌", "🤌", "🤏", "✌️", "🤞", "🤟",
                "🤘", "🤙", "👈", "👉", "👆", "👇", "☝️", "✋",
                "🤚", "🖐️", "🖖", "👋", "🤝", "🙏", "💪", "🦾",
                "👏", "🙌", "👐", "🤲", "🤜", "🤛", "✊", "👊",
                "🫶", "🤳", "✍️", "💅", "💃", "🕺", "🚶", "🏃",
                "🧘", "🫂", "👀", "🧠", "👑", "🎓", "👶", "🧓"
            )
        ),
        EmojiCategory(
            id = "hearts",
            icon = "❤️",
            title = "Сердца и символы",
            emojis = listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍",
                "🤎", "💔", "❤️‍🔥", "❤️‍🩹", "❣️", "💕", "💞", "💓",
                "💗", "💖", "💘", "💝", "💟", "🔥", "✨", "🌟",
                "💫", "⭐", "💥", "💯", "✅", "☑️", "✔️", "❌",
                "❗", "❓", "⚠️", "🔔", "💬", "💭", "🗯️", "💤",
                "➕", "➖", "➡️", "⬅️", "⬆️", "⬇️", "🔄", "♾️"
            )
        ),
        EmojiCategory(
            id = "nature",
            icon = "🌿",
            title = "Природа и животные",
            emojis = listOf(
                "☀️", "🌤️", "⛅", "🌧️", "⛈️", "🌈", "❄️", "⚡",
                "🌙", "⭐", "🌊", "💧", "🌺", "🌸", "🌼", "🌻",
                "🌹", "🌷", "🌱", "🌿", "☘️", "🍀", "🌲", "🌳",
                "🍁", "🍂", "🍄", "🌾", "🐱", "🐶", "🦊", "🐻",
                "🐼", "🦁", "🐯", "🐴", "🦄", "🐝", "🦋", "🐞",
                "🐢", "🐍", "🦅", "🦉", "🕊️", "🦢", "🐸", "🐾"
            )
        ),
        EmojiCategory(
            id = "food_life",
            icon = "☕",
            title = "Еда, праздник и быт",
            emojis = listOf(
                "☕", "🍵", "🥛", "🍯", "🍺", "🍻", "🥂", "🍷",
                "🥟", "🍲", "🥘", "🍞", "🥐", "🥞", "🧀", "🍖",
                "🍕", "🍔", "🍟", "🥗", "🍎", "🍓", "🍒", "🍉",
                "🍌", "🍋", "🍇", "🎂", "🍰", "🧁", "🍫", "🍬",
                "🎉", "🎊", "🎈", "🎁", "🏆", "🥇", "🎯", "🎵",
                "🎶", "🎸", "🏡", "🚗", "✈️", "🚀", "📚", "💡"
            )
        )
    )

    fun resolveCategories(recentEmojis: List<String> = emptyList()): List<EmojiCategory> =
        listOf(
            EmojiCategory(
                id = "recent",
                icon = "🕒",
                title = "Недавние",
                emojis = effectiveRecentEmojis(recentEmojis)
            )
        ) + thematicCategories

    val categories: List<EmojiCategory> = resolveCategories()
}

package com.example.financeplanner.data.ai

/**
 * Постобработка «сырого» ответа LLM (требование ЛР №5, п.3 — UseCase
 * формирует промпт, вызывает модель и обрабатывает результат).
 */
object LlmTextPostProcessor {

    private val cutMarkers = listOf(
        "<|im_end|>",
        "<|im_start|>",
        "</s>",
        "<|endoftext|>",
        "<|end|>",
        "<|eot_id|>",
    )

    /**
     * Обрезает служебные токены ChatML, убирает «эхо» промпта
     * и лишние пустые строки.
     */
    fun clean(raw: String): String {
        var text = raw

        cutMarkers.forEach { marker ->
            val index = text.indexOf(marker)
            if (index >= 0) {
                text = text.substring(0, index)
            }
        }

        // Некоторые маленькие модели начинают ответ с "assistant" или "Ассистент:".
        text = text.trim()
        listOf("assistant", "Assistant", "ассистент", "Ассистент")
            .forEach { prefix ->
                if (text.startsWith(prefix)) {
                    text = text.removePrefix(prefix).trimStart(':', ' ', '\n', '-')
                }
            }

        // Маленькие модели любят markdown: на экране «**» и «###» выглядят мусором.
        text = stripMarkdown(text)

        return text
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    /** Убирает markdown-разметку, сохраняя структуру строк. */
    private fun stripMarkdown(text: String): String = text
        .replace(Regex("(?m)^\\s*#{1,6}\\s*"), "")
        .replace(Regex("\\*\\*(.+?)\\*\\*")) { it.groupValues[1] }
        .replace(Regex("(?<!\\*)\\*([^*\n]+)\\*(?!\\*)")) { it.groupValues[1] }
        .replace(Regex("__(.+?)__")) { it.groupValues[1] }
        .replace("`", "")
        .replace(Regex("(?m)^\\s*[-*]\\s+"), "• ")

    /**
     * Готовит текст для TextToSpeech: убирает markdown-разметку, чтобы
     * «звёздочки» и решётки не читались вслух.
     */
    fun forSpeech(text: String): String = text
        .replace(Regex("(?m)^\\s*#{1,6}\\s*"), "")
        .replace(Regex("\\*\\*(.+?)\\*\\*")) { it.groupValues[1] }
        .replace(Regex("__(.+?)__")) { it.groupValues[1] }
        .replace("`", "")
        .replace("•", ",")
        .replace(Regex("(?m)^\\s*[-*]\\s+"), "")
        .replace(Regex("\\[(\\d+)]")) { "пункт " + it.groupValues[1] }
        .replace(Regex("\\s{2,}"), " ")
        .trim()
}

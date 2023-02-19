package com.chatwaifu.vits.utils.text

import java.text.Normalizer

class JapaneseCleaners {
    private val _symbols_to_japanese: Map<String, String> = mapOf(
        "％" to "パーセント",
        "%" to "パーセント"
    )
    private val _japanese_marks =
        Regex("[^A-Za-z\\d\\u3005\\u3040-\\u30ff\\u4e00-\\u9fff\\uff11-\\uff19\\uff21-\\uff3a\\uff41-\\uff5a\\uff66-\\uff9d]")
    private val _japanese_characters =
        Regex("[A-Za-z\\d\\u3005\\u3040-\\u30ff\\u4e00-\\u9fff\\uff11-\\uff19\\uff21-\\uff3a\\uff41-\\uff5a\\uff66-\\uff9d]")

    private fun symbols_to_japanese(text: String): String {
        var result = text
        for (s in _symbols_to_japanese) {
            val pattern = Regex(s.key)
            result = pattern.replace(result, s.value)
        }
        return result
    }

    private fun japanese_to_romaji_with_accent(_text: String): String {
        var text = symbols_to_japanese(_text)
        val sentences = text.split(_japanese_marks)
        val marks = ArrayList<String>()
        _japanese_marks.findAll(text).forEach {
            marks.add(it.value)
        }
        text = ""
        for (i in sentences.indices) {
            val sentence = sentences[i]
            if (_japanese_characters.find(sentence)?.value != null) {
                if (text != "") {
                    text += " "
                }
                val labels = extract_labels(sentence)
                for (n in labels.indices) {
                    val label = labels[n]
                    val phoneme = Regex("\\-([^\\+]*)\\+").find(label)!!.groups[1]!!.value
                    if (phoneme != "sil" && phoneme != "pau") {
                        text += phoneme.replace("ch", "ʧ")
                            .replace("sh", "ʃ").replace("cl", "Q")
                    } else {
                        continue
                    }
                    val a1 = Regex("/A:(\\-?[0-9]+)\\+").find(label)!!.groups[1]!!.value.toInt()
                    val a2 = Regex("\\+(\\d+)\\+").find(label)!!.groups[1]!!.value.toInt()
                    val a3 = Regex("\\+(\\d+)/").find(label)!!.groups[1]!!.value.toInt()
                    var a2_next = 0
                    val t = Regex("\\-([^\\+]*)\\+").find(labels[n + 1])!!.groups[1]!!.value
                    if (t == "sil" || t == "pau") {
                        a2_next = -1
                    } else {
                        a2_next =
                            Regex("\\+(\\d+)\\+").find(labels[n + 1])!!.groups[1]!!.value.toInt()
                    }
                    // Accent phrase boundary
                    if (a3 == 1 && a2_next == 1) {
                        text += ' '
                    } else if (a1 == 0 && a2_next == a2 + 1) {
                        text += '↓'
                    } else if (a2 == 1 && a2_next == 2) {
                        text += '↑'
                    }
                }
            }
            if (i < marks.count()) {
                val normalized = Normalizer.normalize(marks[i], Normalizer.Form.NFD)
                text += normalized.replace(" ", "")
            }
        }
        return text
    }

    fun japanese_clean_text1(text: String): String {
        val _text = japanese_to_romaji_with_accent(text)
        val cleaned = _text.replace(Regex("([A-Za-z])\$"), "$1.")
        return cleaned
    }

    fun japanese_clean_text2(text: String): String {
        val _text = japanese_clean_text1(text)
        var cleaned = _text.replace("ts", "ʦ")
            .replace("...", "…")
            .replace("、", ",")
        val brackets = listOf(
            "(", ")", "{", "}", "[", "]", "<", ">", "*"
        )
        for (b in brackets) {
            if (cleaned.contains(b)) {
                cleaned = cleaned.replace(b, "")
            }
        }
        return cleaned
    }

    // 生成labels
    external fun extract_labels(text: String): List<String>

    init {
        System.loadLibrary("moereng-vits")
    }
}
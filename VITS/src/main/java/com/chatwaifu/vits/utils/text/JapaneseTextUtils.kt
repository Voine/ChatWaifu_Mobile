package com.chatwaifu.vits.utils.text

import android.content.res.AssetManager
import android.util.Log

class JapaneseTextUtils(
    override val symbols: List<String>,
    override val cleanerName: String,
    override val assetManager: AssetManager
) : TextUtils {
    private var openJtalkInitialized = false

    private fun initDictionary(assetManager: AssetManager) {
        // init openjatlk
        if (!openJtalkInitialized) {
            openJtalkInitialized = initOpenJtalk(assetManager)
            if (!openJtalkInitialized) {
                throw RuntimeException("初始化openjtalk字典失败！")
            }
            Log.i("TextUtils", "Openjtalk字典初始化成功！")
        }
    }

    override fun cleanInputs(text: String): String {
        return text.replace("\"", "").replace("\'", "")
            .replace("\t", " ").replace("\n", "、")
            .replace("”", "")
    }

    override fun splitSentence(text: String): List<String> {
        val splittedSentence = splitSentenceCpp(text)
        var sentences = splittedSentence.replace("EOS\n", "").split("\n")
        sentences = sentences.subList(0, sentences.size - 1)
        return sentences
    }

    override fun wordsToLabels(text: String): IntArray {
        val labels = ArrayList<Int>()
        labels.add(0)

        // symbol to id
        val symbolToIndex = HashMap<String, Int>()
        symbols.forEachIndexed { index, s ->
            symbolToIndex[s] = index
        }

        // clean text
        var cleanedText = ""
        val cleaner = JapaneseCleaners()
        when{
            (cleanerName == "japanese_cleaners" || cleanerName == "japanese_cleaners1")-> {
                cleanedText = cleaner.japanese_clean_text1(text)
            }
            cleanerName == "japanese_cleaners2" -> {
                cleanedText = cleaner.japanese_clean_text2(text)
            }
        }

        // symbol to label
        for (symbol in cleanedText) {
            if (!symbols.contains(symbol.toString())) {
                continue
            }
            val label = symbolToIndex[symbol.toString()]
            if (label != null) {
                labels.add(label)
                labels.add(0)
            }
        }
        return labels.toIntArray()
    }

    override fun convertSentenceToLabels(
        text: String
    ): List<IntArray> {
        val sentences = splitSentence(text)

        val outputs = ArrayList<IntArray>()

        var sentence = ""
        for (i in sentences.indices) {
            val s = sentences[i]
            sentence += s.split("\t")[0]
            if (s.contains("記号,読点") ||
                s.contains("記号,句点") ||
                s.contains("記号,一般") ||
                s.contains("記号,空白") ||
                i == sentences.size - 1
            ) {
                if (sentence.length > 100) {
                    throw RuntimeException("句子过长")
                }
                val labels = wordsToLabels(sentence)
                if (labels.isEmpty() || labels.sum() == 0)
                    continue
                outputs.add(labels)
                sentence = ""
            }
        }
        return outputs
    }

    override fun convertText(
        text: String
    ): List<IntArray> {
        // init dict
        initDictionary(assetManager)

        // clean inputs
        val cleanedInputs = cleanInputs(text)

        // convert inputs
        return convertSentenceToLabels(cleanedInputs)
    }

    external fun initOpenJtalk(assetManager: AssetManager): Boolean

    external fun splitSentenceCpp(text: String): String

    init {
        System.loadLibrary("moereng-vits")
    }
}
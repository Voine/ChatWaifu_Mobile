package com.chatwaifu.vits.utils.text;

import android.util.Log;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;

import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;

public class ChineseCleaners {

    boolean debug_flag = true;


    ArrayList<ArrayList<String>> latin_map = new ArrayList<ArrayList<String>>() {{
        add(new ArrayList<>(Arrays.asList("a", "ㄟˉ")));
        add(new ArrayList<>(Arrays.asList("b", "ㄅㄧˋ")));
        add(new ArrayList<>(Arrays.asList("c", "ㄙㄧˉ")));
        add(new ArrayList<>(Arrays.asList("d", "ㄉㄧˋ")));
        add(new ArrayList<>(Arrays.asList("e", "ㄧˋ")));
        add(new ArrayList<>(Arrays.asList("f", "ㄝˊㄈㄨˋ")));
        add(new ArrayList<>(Arrays.asList("g", "ㄐㄧˋ")));
        add(new ArrayList<>(Arrays.asList("h", "ㄝˇㄑㄩˋ")));
        add(new ArrayList<>(Arrays.asList("i", "ㄞˋ")));
        add(new ArrayList<>(Arrays.asList("j", "ㄐㄟˋ")));
        add(new ArrayList<>(Arrays.asList("k", "ㄎㄟˋ")));
        add(new ArrayList<>(Arrays.asList("l", "ㄝˊㄛˋ")));
        add(new ArrayList<>(Arrays.asList("m", "ㄝˊㄇㄨˋ")));
        add(new ArrayList<>(Arrays.asList("n", "ㄣˉ")));
        add(new ArrayList<>(Arrays.asList("o", "ㄡˉ")));
        add(new ArrayList<>(Arrays.asList("p", "ㄆㄧˉ")));
        add(new ArrayList<>(Arrays.asList("q", "ㄎㄧㄡˉ")));
        add(new ArrayList<>(Arrays.asList("r", "ㄚˋ")));
        add(new ArrayList<>(Arrays.asList("s", "ㄝˊㄙˋ")));
        add(new ArrayList<>(Arrays.asList("t", "ㄊㄧˋ")));
        add(new ArrayList<>(Arrays.asList("u", "ㄧㄡˉ")));
        add(new ArrayList<>(Arrays.asList("v", "ㄨㄧˉ")));
        add(new ArrayList<>(Arrays.asList("w", "ㄉㄚˋㄅㄨˋㄌㄧㄡˋ")));
        add(new ArrayList<>(Arrays.asList("x", "ㄝˉㄎㄨˋㄙˋ")));
        add(new ArrayList<>(Arrays.asList("y", "ㄨㄞˋ")));
        add(new ArrayList<>(Arrays.asList("z", "ㄗㄟˋ")));

        add(new ArrayList<>(Arrays.asList("1", "一")));
        add(new ArrayList<>(Arrays.asList("2", "二")));
        add(new ArrayList<>(Arrays.asList("3", "三")));
        add(new ArrayList<>(Arrays.asList("4", "四")));
        add(new ArrayList<>(Arrays.asList("5", "五")));
        add(new ArrayList<>(Arrays.asList("6", "六")));
        add(new ArrayList<>(Arrays.asList("7", "七")));
        add(new ArrayList<>(Arrays.asList("8", "八")));
        add(new ArrayList<>(Arrays.asList("9", "九")));
        add(new ArrayList<>(Arrays.asList("0", "零")));
    }};


    //uang单独4位
    ArrayList<ArrayList<String>> pinyin_to_prepin_p = new ArrayList<ArrayList<String>>() {{
        add(new ArrayList<>(Arrays.asList("uang", "ㄨㄤ")));
    }};

    // 特殊拼读和整体化
    ArrayList<ArrayList<String>> pinyin_to_prepin_s = new ArrayList<ArrayList<String>>() {{
        add(new ArrayList<>(Arrays.asList("zh", "Z")));
        add(new ArrayList<>(Arrays.asList("ch", "C")));
        add(new ArrayList<>(Arrays.asList("sh", "S")));
        add(new ArrayList<>(Arrays.asList("ai", "A")));
        add(new ArrayList<>(Arrays.asList("ei", "I")));
        add(new ArrayList<>(Arrays.asList("ao", "O")));
        add(new ArrayList<>(Arrays.asList("ou", "U")));
        add(new ArrayList<>(Arrays.asList("er", "R")));
        add(new ArrayList<>(Arrays.asList("ue", "Y")));
        add(new ArrayList<>(Arrays.asList("ie", "E")));
        add(new ArrayList<>(Arrays.asList("ye", "E")));

        add(new ArrayList<>(Arrays.asList("ju", "ㄐㄩ")));
        add(new ArrayList<>(Arrays.asList("qu", "ㄑㄩ")));
        add(new ArrayList<>(Arrays.asList("xu", "ㄒㄩ")));
        add(new ArrayList<>(Arrays.asList("ui", "ㄨㄟ")));
        add(new ArrayList<>(Arrays.asList("iu", "ㄧㄡ")));
        add(new ArrayList<>(Arrays.asList("uan", "ㄨㄢ")));
        add(new ArrayList<>(Arrays.asList("uo", "ㄨㄛ")));
        add(new ArrayList<>(Arrays.asList("ying", "ㄧㄥ")));
        add(new ArrayList<>(Arrays.asList("ang", "ㄤ")));
        add(new ArrayList<>(Arrays.asList("eng", "ㄥ")));
        add(new ArrayList<>(Arrays.asList("ing", "ㄧㄥ")));
        add(new ArrayList<>(Arrays.asList("ong", "ㄨㄥ")));
    }};

    //整体认读转化
    ArrayList<ArrayList<String>> pinyin_to_prepin_l = new ArrayList<ArrayList<String>>() {{
        add(new ArrayList<>(Arrays.asList("yu", "ㄩ")));
        add(new ArrayList<>(Arrays.asList("yan", "ㄧㄢ")));
        add(new ArrayList<>(Arrays.asList("Zi", "ㄓ ")));
        add(new ArrayList<>(Arrays.asList("Ci", "ㄔㄖ")));
        add(new ArrayList<>(Arrays.asList("Si", "ㄕ ")));
        add(new ArrayList<>(Arrays.asList("zi", "ㄗ ")));
        add(new ArrayList<>(Arrays.asList("ci", "ㄘㄖ")));
        add(new ArrayList<>(Arrays.asList("ri", "ㄖ ")));
        add(new ArrayList<>(Arrays.asList("si", "ㄙ ")));
        add(new ArrayList<>(Arrays.asList("yin", "ㄧㄣ")));
        add(new ArrayList<>(Arrays.asList("wu", "ㄨ")));
        add(new ArrayList<>(Arrays.asList("an", "ㄢ")));
        add(new ArrayList<>(Arrays.asList("en", "ㄣ")));
        add(new ArrayList<>(Arrays.asList("in", "ㄧㄣ")));
        add(new ArrayList<>(Arrays.asList("ua", "ㄨㄚ")));
    }};

    ArrayList<ArrayList<String>> prepin_to_bopomofo = new ArrayList<ArrayList<String>>() {{
        add(new ArrayList<>(Arrays.asList("b", "ㄅ")));
        add(new ArrayList<>(Arrays.asList("p", "ㄆ")));
        add(new ArrayList<>(Arrays.asList("m", "ㄇ")));
        add(new ArrayList<>(Arrays.asList("f", "ㄈ")));
        add(new ArrayList<>(Arrays.asList("d", "ㄉ")));
        add(new ArrayList<>(Arrays.asList("t", "ㄊ")));
        add(new ArrayList<>(Arrays.asList("n", "ㄋ")));
        add(new ArrayList<>(Arrays.asList("l", "ㄌ")));
        add(new ArrayList<>(Arrays.asList("g", "ㄍ")));
        add(new ArrayList<>(Arrays.asList("k", "ㄎ")));
        add(new ArrayList<>(Arrays.asList("h", "ㄏ")));
        add(new ArrayList<>(Arrays.asList("j", "ㄐ")));
        add(new ArrayList<>(Arrays.asList("q", "ㄑ")));
        add(new ArrayList<>(Arrays.asList("x", "ㄒ")));
        add(new ArrayList<>(Arrays.asList("Z", "ㄓ")));  //zh
        add(new ArrayList<>(Arrays.asList("C", "ㄔ")));  //ch
        add(new ArrayList<>(Arrays.asList("S", "ㄕ")));  //sh
        add(new ArrayList<>(Arrays.asList("r", "ㄖ")));
        add(new ArrayList<>(Arrays.asList("z", "ㄗ")));
        add(new ArrayList<>(Arrays.asList("c", "ㄘ")));
        add(new ArrayList<>(Arrays.asList("s", "ㄙ")));
        add(new ArrayList<>(Arrays.asList("yi", "ㄧ")));
        add(new ArrayList<>(Arrays.asList("i", "ㄧ")));
        add(new ArrayList<>(Arrays.asList("y", "ㄧ")));

        add(new ArrayList<>(Arrays.asList("u", "ㄨ")));
        add(new ArrayList<>(Arrays.asList("w", "ㄨ")));

        add(new ArrayList<>(Arrays.asList("v", "ㄩ")));
        add(new ArrayList<>(Arrays.asList("a", "ㄚ")));
        add(new ArrayList<>(Arrays.asList("o", "ㄛ")));
        add(new ArrayList<>(Arrays.asList("e", "ㄜ")));
        add(new ArrayList<>(Arrays.asList("Y", "ㄩㄝ"))); //ue
        add(new ArrayList<>(Arrays.asList("E", "ㄧㄝ"))); //ie
        add(new ArrayList<>(Arrays.asList("A", "ㄞ")));  //ai
        add(new ArrayList<>(Arrays.asList("I", "ㄟ")));  //ei
        add(new ArrayList<>(Arrays.asList("O", "ㄠ")));  //ao
        add(new ArrayList<>(Arrays.asList("U", "ㄡ")));  //ou


        add(new ArrayList<>(Arrays.asList("R", "ㄦ")));  //er
        add(new ArrayList<>(Arrays.asList("1", "ˉ")));
        add(new ArrayList<>(Arrays.asList("2", "ˊ")));
        add(new ArrayList<>(Arrays.asList("3", "ˇ")));
        add(new ArrayList<>(Arrays.asList("4", "ˋ")));
    }};

    ArrayList<ArrayList<String>> bopomofo_to_romaji_t = new ArrayList<ArrayList<String>>() {{
        add(new ArrayList<>(Arrays.asList("ㄅㄛ", "p⁼wo")));
        add(new ArrayList<>(Arrays.asList("ㄆㄛ", "pʰwo")));
        add(new ArrayList<>(Arrays.asList("ㄇㄛ", "mwo")));
        add(new ArrayList<>(Arrays.asList("ㄈㄛ", "fwo")));
        add(new ArrayList<>(Arrays.asList("ㄧㄢ", "yeNN")));
        add(new ArrayList<>(Arrays.asList("ㄧㄣ", "iNN")));
        add(new ArrayList<>(Arrays.asList("ㄧㄥ", "iNg")));
        add(new ArrayList<>(Arrays.asList("ㄨㄥ", "uNg")));
        add(new ArrayList<>(Arrays.asList("ㄩㄥ", "yuNg")));

    }};
    ArrayList<ArrayList<String>> bopomofo_to_romaji = new ArrayList<ArrayList<String>>() {{
        add(new ArrayList<>(Arrays.asList("ㄅ", "p⁼")));
        add(new ArrayList<>(Arrays.asList("ㄆ", "pʰ")));
        add(new ArrayList<>(Arrays.asList("ㄇ", "m")));
        add(new ArrayList<>(Arrays.asList("ㄈ", "f")));
        add(new ArrayList<>(Arrays.asList("ㄉ", "t⁼")));
        add(new ArrayList<>(Arrays.asList("ㄊ", "tʰ")));
        add(new ArrayList<>(Arrays.asList("ㄋ", "n")));
        add(new ArrayList<>(Arrays.asList("ㄌ", "l")));
        add(new ArrayList<>(Arrays.asList("ㄍ", "k⁼")));
        add(new ArrayList<>(Arrays.asList("ㄎ", "kʰ")));
        add(new ArrayList<>(Arrays.asList("ㄏ", "h")));
        add(new ArrayList<>(Arrays.asList("ㄐ", "ʧ⁼")));
        add(new ArrayList<>(Arrays.asList("ㄑ", "ʧʰ")));
        add(new ArrayList<>(Arrays.asList("ㄒ", "ʃ")));
        add(new ArrayList<>(Arrays.asList("ㄓ", "ʦ`⁼")));
        add(new ArrayList<>(Arrays.asList("ㄔ", "ʦ`ʰ")));
        add(new ArrayList<>(Arrays.asList("ㄕ", "s`")));
        add(new ArrayList<>(Arrays.asList("ㄖ", "ɹ`")));
        add(new ArrayList<>(Arrays.asList("ㄗ", "ʦ⁼")));
        add(new ArrayList<>(Arrays.asList("ㄘ", "ʦʰ")));
        add(new ArrayList<>(Arrays.asList("ㄙ", "s")));
        add(new ArrayList<>(Arrays.asList("ㄚ", "a")));
        add(new ArrayList<>(Arrays.asList("ㄛ", "o")));
        add(new ArrayList<>(Arrays.asList("ㄜ", "ə")));
        add(new ArrayList<>(Arrays.asList("ㄝ", "e")));
        add(new ArrayList<>(Arrays.asList("ㄞ", "ai")));
        add(new ArrayList<>(Arrays.asList("ㄟ", "ei")));
        add(new ArrayList<>(Arrays.asList("ㄠ", "au")));
        add(new ArrayList<>(Arrays.asList("ㄡ", "ou")));
        add(new ArrayList<>(Arrays.asList("ㄢ", "aNN")));
        add(new ArrayList<>(Arrays.asList("ㄣ", "əNN")));
        add(new ArrayList<>(Arrays.asList("ㄤ", "aNg")));
        add(new ArrayList<>(Arrays.asList("ㄥ", "əNg")));
        add(new ArrayList<>(Arrays.asList("ㄦ", "əɻ")));
        add(new ArrayList<>(Arrays.asList("ㄧ", "i")));
        add(new ArrayList<>(Arrays.asList("ㄨ", "u")));
        add(new ArrayList<>(Arrays.asList("ㄩ", "ɥ")));
        add(new ArrayList<>(Arrays.asList("ˉ", "→")));
        add(new ArrayList<>(Arrays.asList("ˊ", "↑")));
        add(new ArrayList<>(Arrays.asList("ˇ", "↓↑")));
        add(new ArrayList<>(Arrays.asList("ˋ", "↓")));
        add(new ArrayList<>(Arrays.asList("˙", " ")));
        add(new ArrayList<>(Arrays.asList("、", ",")));
        add(new ArrayList<>(Arrays.asList("、", ",")));
        add(new ArrayList<>(Arrays.asList("，", ",")));
        add(new ArrayList<>(Arrays.asList("。", ".")));
        add(new ArrayList<>(Arrays.asList("！", "!")));
        add(new ArrayList<>(Arrays.asList("？", "?")));
        add(new ArrayList<>(Arrays.asList("—", "-")));
    }};


    public String latin_to_bopomofo(String s) {
        String ans = s;
        for (ArrayList<String> i : latin_map) {
            ans = ans.replaceAll(i.get(0), i.get(1));
        }
        return ans;
    }

    public String pinyin_to_prepin(String s) {
        String prepin_p = s;
        for (ArrayList<String> j : pinyin_to_prepin_p) {
            prepin_p = prepin_p.replaceAll(j.get(0), j.get(1));
        }
        String prepin_s = prepin_p;
        for (ArrayList<String> j : pinyin_to_prepin_s) {
            prepin_s = prepin_s.replaceAll(j.get(0), j.get(1));
        }
        String prepin_l = prepin_s;
        for (ArrayList<String> j : pinyin_to_prepin_l) {
            prepin_l = prepin_l.replaceAll(j.get(0), j.get(1));
        }
        String bopomofo = prepin_l;
        for (ArrayList<String> j : prepin_to_bopomofo) {
            bopomofo = bopomofo.replaceAll(j.get(0), j.get(1));
            Log.e("tmp", bopomofo + " " + j.get(0) + " " + j.get(1));
        }
        return bopomofo;
    }

    public String bopomofo_to_romaji(String s) {
        String romaji_t = s;
        for (ArrayList<String> j : bopomofo_to_romaji_t) {
            romaji_t = romaji_t.replaceAll(j.get(0), j.get(1));
        }
        String romaji = romaji_t;
        for (ArrayList<String> j : bopomofo_to_romaji) {
            romaji = romaji.replaceAll(j.get(0), j.get(1));
        }
        return romaji;
    }

    public String chinese_clean_text1(String s) {

        StringBuilder sb = new StringBuilder();
        String sent = null;

        sent = latin_to_bopomofo(s);

        String pinyin = sent;

        try {
            //pinyin = PinyinHelper.toHanyuPinyinStringArray(sent.charAt(i))[0];
            pinyin = PinyinHelper.toPinyin(sent,PinyinStyleEnum.NUM_LAST);
        } catch (Exception ignore) {
        }

        String bopomofo = pinyin_to_prepin(pinyin);
        String romaji = bopomofo_to_romaji(bopomofo);

        romaji = romaji.replaceAll("i([aoe])", "y$1");
        romaji = romaji.replaceAll("u([aoəe])", "w$1");
        romaji = romaji.replaceAll("([ʦsɹ]`[⁼ʰ]?)([→↓↑]+)", "$1ɹ`$2").replace("ɻ", "ɹ`");
        romaji = romaji.replaceAll("([ʦs][⁼ʰ]?)([→↓↑]+)", "$1ɹ$2");
        romaji = romaji.replace(romaji, romaji + " ");
        sb.append(romaji);

        if (debug_flag) sb.append(' ');
        return sb.toString();
    }

    @NotNull
    public String chinese_clean_text2(@NotNull String text) {
        // TODO
        return null;
    }
}
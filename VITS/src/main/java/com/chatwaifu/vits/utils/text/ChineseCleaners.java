package com.chatwaifu.vits.utils.text;

import android.util.Log;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;

public class ChineseCleaners {

    boolean debug_flag = true;

    public String no_punctuation(String s) {
        String ans = s;
        String[] punctuation_list = new String[]{"，", "、", "；", "：", "。", "？", ",", ".", "?", "\"", "'"};
        for (String i : punctuation_list) {
            ans = ans.replace(i, " ");
        }
        return ans;
    }


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
        add(new ArrayList<>(Arrays.asList("1", "ㄧˉ")));
        add(new ArrayList<>(Arrays.asList("2", "ㄦˋ")));
        add(new ArrayList<>(Arrays.asList("3", "ㄙㄢˉ")));
        add(new ArrayList<>(Arrays.asList("4", "ㄙˋ")));
        add(new ArrayList<>(Arrays.asList("5", "ㄨˇ")));
        add(new ArrayList<>(Arrays.asList("6", "ㄌㄧㄡˋ")));
        add(new ArrayList<>(Arrays.asList("7", "ㄑㄧˉ")));
        add(new ArrayList<>(Arrays.asList("8", "ㄅㄚˉ")));
        add(new ArrayList<>(Arrays.asList("9", "ㄐㄧㄡˇ")));
        add(new ArrayList<>(Arrays.asList("0", "ㄌㄧㄥˊ")));
    }};

    public String latin_to_bopomofo(String s) {
        String ans = s;
        for (ArrayList<String> i : latin_map) {
            ans = ans.replaceAll(i.get(0), i.get(1));
        }
        return ans;
    }


    ArrayList<ArrayList<String>> pinyin_map = new ArrayList<ArrayList<String>>() {{
        add(new ArrayList<>(Arrays.asList("^m(\\d)$", "mu$1")));
        add(new ArrayList<>(Arrays.asList("^n(\\d)$", "N$1")));
        add(new ArrayList<>(Arrays.asList("^r5$", "er5")));
        add(new ArrayList<>(Arrays.asList("iu", "iou")));
        add(new ArrayList<>(Arrays.asList("ui", "uei")));
        add(new ArrayList<>(Arrays.asList("ong", "ung")));
        add(new ArrayList<>(Arrays.asList("^yi?", "i")));
        add(new ArrayList<>(Arrays.asList("^wu?", "u")));
        add(new ArrayList<>(Arrays.asList("iu", "v")));
        add(new ArrayList<>(Arrays.asList("^([jqx])u", "$1v")));
        add(new ArrayList<>(Arrays.asList("([iuv])n", "$1en")));
        add(new ArrayList<>(Arrays.asList("^zhi?", "Z")));
        add(new ArrayList<>(Arrays.asList("^chi?", "C")));
        add(new ArrayList<>(Arrays.asList("^shi?", "S")));
        add(new ArrayList<>(Arrays.asList("^([zcsr])i", "$1")));
        add(new ArrayList<>(Arrays.asList("ai", "A")));
        add(new ArrayList<>(Arrays.asList("ei", "I")));
        add(new ArrayList<>(Arrays.asList("ao", "O")));
        add(new ArrayList<>(Arrays.asList("ou", "U")));
        add(new ArrayList<>(Arrays.asList("ang", "K")));
        add(new ArrayList<>(Arrays.asList("eng", "G")));
        add(new ArrayList<>(Arrays.asList("an", "M")));
        add(new ArrayList<>(Arrays.asList("en", "N")));
        add(new ArrayList<>(Arrays.asList("er", "R")));
        add(new ArrayList<>(Arrays.asList("eh", "E")));
        add(new ArrayList<>(Arrays.asList("([iv])e", "$1E")));
        add(new ArrayList<>(Arrays.asList("([^0-4])$", "$g<1>0")));
    }};

    String[] bopomofo_table = new String[]{"bpmfdtnlgkhjqxZCSrzcsiuvaoeEAIOUMNKGR12340",
            "ㄅㄆㄇㄈㄉㄊㄋㄌㄍㄎㄏㄐㄑㄒㄓㄔㄕㄖㄗㄘㄙㄧㄨㄩㄚㄛㄜㄝㄞㄟㄠㄡㄢㄣㄤㄥㄦˉˊˇˋ˙"};
    int bopomofo_table_len = 42;

    public String chinese_to_bopomofo(String s) {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setToneType(HanyuPinyinToneType.WITH_TONE_NUMBER);
        StringBuilder sb = new StringBuilder();
        String pinyin;
        for (int i = 0; i < s.length(); ++i) {
            pinyin = null;
            try {
                pinyin = PinyinHelper.toHanyuPinyinStringArray(s.charAt(i))[0];
            } catch (Exception ignore) {
            }
            if (pinyin == null) {  //无法转拼音，直接拷贝
                sb.append(s.charAt(i));
            } else {
                String bopomofo = pinyin;
                for (ArrayList<String> j : pinyin_map) {
                    bopomofo = bopomofo.replaceAll(j.get(0), j.get(1));
                    Log.e("tmp", bopomofo + " " + j.get(0) + " " + j.get(1));
                }
                Log.e("tmp", bopomofo);
                for (int j = 0; j < bopomofo_table_len; j++)
                    bopomofo = bopomofo.replace(bopomofo_table[0].charAt(j), bopomofo_table[1].charAt(j));
                sb.append(bopomofo);
            }
            if (debug_flag) sb.append(' ');
        }
        return sb.toString();
    }

//    public String bopomofotest(String text){
//        String cleaned_text = no_punctuation(text);
//        cleaned_text = latin_to_bopomofo(cleaned_text);
//        cleaned_text = chinese_to_bopomofo(cleaned_text);
//        return cleaned_text;
//    }

    public String chinese_clean_text1(String text){
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setToneType(HanyuPinyinToneType.WITH_TONE_NUMBER);
        StringBuilder sb = new StringBuilder();
        String pinyin;
        for (int i = 0; i < text.length(); ++i) {
            pinyin = null;
            try {
                pinyin = PinyinHelper.toHanyuPinyinStringArray(text.charAt(i))[0];
            } catch (Exception ignore) {
            }
            sb.append(pinyin);
            if (debug_flag) sb.append(' ');
        }
        return sb.toString();
    }

    @NotNull
    public String chinese_clean_text2(@NotNull String text) {
        // TODO
        return null;
    }
}

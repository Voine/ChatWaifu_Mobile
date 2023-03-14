/* ----------------------------------------------------------------- */
/*           The Japanese TTS System "Open JTalk"                    */
/*           developed by HTS Working Group                          */
/*           http://open-jtalk.sourceforge.net/                      */
/* ----------------------------------------------------------------- */
/*                                                                   */
/*  Copyright (c) 2008-2016  Nagoya Institute of Technology          */
/*                           Department of Computer Science          */
/*                                                                   */
/* All rights reserved.                                              */
/*                                                                   */
/* Redistribution and use in source and binary forms, with or        */
/* without modification, are permitted provided that the following   */
/* conditions are met:                                               */
/*                                                                   */
/* - Redistributions of source code must retain the above copyright  */
/*   notice, this list of conditions and the following disclaimer.   */
/* - Redistributions in binary form must reproduce the above         */
/*   copyright notice, this list of conditions and the following     */
/*   disclaimer in the documentation and/or other materials provided */
/*   with the distribution.                                          */
/* - Neither the name of the HTS working group nor the names of its  */
/*   contributors may be used to endorse or promote products derived */
/*   from this software without specific prior written permission.   */
/*                                                                   */
/* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND            */
/* CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,       */
/* INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF          */
/* MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE          */
/* DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS */
/* BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,          */
/* EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED   */
/* TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,     */
/* DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON */
/* ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,   */
/* OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY    */
/* OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE           */
/* POSSIBILITY OF SUCH DAMAGE.                                       */
/* ----------------------------------------------------------------- */

#ifndef NJD_SET_DIGIT_RULE_H
#define NJD_SET_DIGIT_RULE_H

#ifdef __cplusplus
#define NJD_SET_DIGIT_RULE_H_START extern "C" {
#define NJD_SET_DIGIT_RULE_H_END   }
#else
#define NJD_SET_DIGIT_RULE_H_START
#define NJD_SET_DIGIT_RULE_H_END
#endif                          /* __CPLUSPLUS */

NJD_SET_DIGIT_RULE_H_START;

#define NJD_SET_DIGIT_KIGOU "記号"
#define NJD_SET_DIGIT_MEISHI "名詞"
#define NJD_SET_DIGIT_KAZU "数"
#define NJD_SET_DIGIT_SUUSETSUZOKU "数接続"
#define NJD_SET_DIGIT_JOSUUSHI "助数詞"
#define NJD_SET_DIGIT_FUKUSHIKANOU "副詞可能"
#define NJD_SET_DIGIT_HAIHUN1 "―"     /* horizontal bar */
#define NJD_SET_DIGIT_HAIHUN2 "−"     /* minus sign */
#define NJD_SET_DIGIT_HAIHUN3 "‐"     /* hyphen */
#define NJD_SET_DIGIT_HAIHUN4 "—"     /* em dash */
#define NJD_SET_DIGIT_HAIHUN5 "－"     /* fullwidth hyphen-minus */
#define NJD_SET_DIGIT_KAKKO1 "（"
#define NJD_SET_DIGIT_KAKKO2 "）"
#define NJD_SET_DIGIT_BANGOU "番号"
#define NJD_SET_DIGIT_COMMA "，"
#define NJD_SET_DIGIT_TEN1 "．"
#define NJD_SET_DIGIT_TEN2 "・"
#define NJD_SET_DIGIT_TEN_FEATURE "．,名詞,接尾,助数詞,*,*,*,．,テン,テン,0/2,*,-1"
#define NJD_SET_DIGIT_ZERO1 "〇"
#define NJD_SET_DIGIT_ZERO2 "０"
#define NJD_SET_DIGIT_ZERO_BEFORE_DP "レー"
#define NJD_SET_DIGIT_ZERO_AFTER_DP "ゼロ"
#define NJD_SET_DIGIT_TWO "二"
#define NJD_SET_DIGIT_TWO_BEFORE_DP "ニー"
#define NJD_SET_DIGIT_TWO_AFTER_DP "ニー"
#define NJD_SET_DIGIT_FIVE "五"
#define NJD_SET_DIGIT_FIVE_BEFORE_DP "ゴー"
#define NJD_SET_DIGIT_FIVE_AFTER_DP "ゴー"
#define NJD_SET_DIGIT_SIX "六"
#define NJD_SET_DIGIT_NIN "人"
#define NJD_SET_DIGIT_GATSU "月"
#define NJD_SET_DIGIT_NICHI "日"
#define NJD_SET_DIGIT_NICHIKAN "日間"
#define NJD_SET_DIGIT_ONE "一"
#define NJD_SET_DIGIT_TSUITACHI "一日,名詞,副詞可能,*,*,*,*,一日,ツイタチ,ツイタチ,4/4,*"
#define NJD_SET_DIGIT_FOUR "四"
#define NJD_SET_DIGIT_TEN "十"
#define NJD_SET_DIGIT_JUYOKKA "十四日,名詞,副詞可能,*,*,*,*,十四日,ジュウヨッカ,ジューヨッカ,1/5,*"
#define NJD_SET_DIGIT_JUYOKKAKAN "十四日間,名詞,副詞可能,*,*,*,*,十四日間,ジュウヨッカカン,ジューヨッカカン,5/7,*"
#define NJD_SET_DIGIT_NIJU "二十,名詞,副詞可能,*,*,*,*,二十,ニジュウ,ニジュー,1/3,*"
#define NJD_SET_DITIT_YOKKA "四日,名詞,副詞可能,*,*,*,*,四日,ヨッカ,ヨッカ,0/3,*,0"
#define NJD_SET_DIGIT_YOKKAKAN "四日間,名詞,副詞可能,*,*,*,*,四日間,ヨッカカン,ヨッカカン,3/5,*,0"
#define NJD_SET_DITIT_HATSUKA "二十日,名詞,副詞可能,*,*,*,*,二十日,ハツカ,ハツカ,0/3,*"
#define NJD_SET_DIGIT_HATSUKAKAN "二十日間,名詞,副詞可能,*,*,*,*,二十日間,ハツカカン,ハツカカン,3/5,*"

static const char *njd_set_digit_rule_numeral_list1[] = {
   "○", "0", "〇",
   "１", "1", "一",
   "２", "2", "二",
   "３", "3", "三",
   "４", "4", "四",
   "５", "5", "五",
   "６", "6", "六",
   "７", "7", "七",
   "８", "8", "八",
   "９", "9", "九",
   "一", "1", "一",
   "二", "2", "二",
   "三", "3", "三",
   "四", "4", "四",
   "五", "5", "五",
   "六", "6", "六",
   "七", "7", "七",
   "八", "8", "八",
   "九", "9", "九",
   "いち", "1", "一",
   "に", "2", "二",
   "さん", "3", "三",
   "よん", "4", "四",
   "ご", "5", "五",
   "ろく", "6", "六",
   "なな", "7", "七",
   "はち", "8", "八",
   "きゅう", "9", "九",
   "〇", "0", "〇",
   "０", "0", "０",
   "壱", "1", "一",
   "弐", "2", "二",
   "貳", "2", "二",
   "ニ", "2", "二",
   "参", "3", "三",
   "し", "4", "四",
   "しち", "7", "七",
   "く", "9", "九",
   NULL, NULL, NULL
};

static const char *njd_set_digit_rule_numeral_list2[] = {
   "",
   "十,名詞,数,*,*,*,*,十,ジュウ,ジュー,1/2,*",
   "百,名詞,数,*,*,*,*,百,ヒャク,ヒャク,2/2,*",
   "千,名詞,数,*,*,*,*,千,セン,セン,1/2,*",
   NULL
};

static const char *njd_set_digit_rule_numeral_list3[] = {
   "",
   "万,名詞,数,*,*,*,*,万,マン,マン,1/2,*",
   "億,名詞,数,*,*,*,*,億,オク,オク,1/2,*",
   "兆,名詞,数,*,*,*,*,兆,チョウ,チョー,1/2,C3",
   "京,名詞,数,*,*,*,*,京,ケイ,ケー,1/2,*",
   "垓,名詞,数,*,*,*,*,垓,ガイ,ガイ,1/2,*",
   "𥝱,名詞,数,*,*,*,*,𥝱,ジョ,ジョ,1/1,*",
   "穣,名詞,数,*,*,*,*,穣,ジョウ,ジョー,1/2,*",
   "溝,名詞,数,*,*,*,*,溝,コウ,コウ,1/2,*",
   "澗,名詞,数,*,*,*,*,澗,カン,カン,1/2,*",
   "正,名詞,数,*,*,*,*,正,セイ,セー,1/2,*",
   "載,名詞,数,*,*,*,*,載,サイ,サイ,1/2,*",
   "極,名詞,数,*,*,*,*,極,ゴク,ゴク,1/2,*",
   "恒河沙,名詞,数,*,*,*,*,恒河沙,ゴウガシャ,ゴウガシャ,1/4,*",
   "阿僧祇,名詞,数,*,*,*,*,阿僧祇,アソウギ,アソーギ,2/4,*",
   "那由他,名詞,数,*,*,*,*,那由他,ナユタ,ナユタ,1/3,*",
   "不可思議,名詞,数,*,*,*,*,不可思議,フカシギ,フカシギ,2/4,*",
   "無量大数,名詞,数,*,*,*,*,無量大数,ムリョウタイスウ,ムリョータイスー,6/7,*",
   NULL
};

static const char *njd_set_digit_rule_numeral_list4[] = {
   "一", "二", "三", "四", "五", "六", "七", "八", "九", "何", "幾", "数",
   NULL
};

static const char *njd_set_digit_rule_numeral_list5[] = {
   "十", "百", "千", "万", "億", "兆", "京", "垓",
   "𥝱",
   "穣", "溝", "澗", "正", "載", "極",
   "恒河沙", "阿僧祇", "那由他", "不可思議", "無量大数",
   NULL
};

static const char *njd_set_digit_rule_numeral_list6[] = {
   "百", "千", NULL
};

static const char *njd_set_digit_rule_numeral_list7[] = {
   "三", "1",
   "六", "2",
   "八", "2",
   "何", "1",
   NULL, NULL
};

static const char *njd_set_digit_rule_numeral_list8[] = {
   "百", NULL
};

static const char *njd_set_digit_rule_numeral_list9[] = {
   "六", "ロッ", "0", "2",
   "八", "ハッ", "0", "2",
   NULL, NULL, NULL
};

static const char *njd_set_digit_rule_numeral_list10[] = {
   "千", "兆", NULL
};

static const char *njd_set_digit_rule_numeral_list11[] = {
   "一", "イッ", "0", "2",
   "八", "ハッ", "0", "2",
   "十", "ジュッ", "1", "2",
   NULL, NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class1b[] = {
   /* from paper */
   "年" /* ねん */ , "円",
   /* from dictionary */
   "年間", "年生", "年代", "年度", "年版", "年余", "年余", "年来", "えん",
   NULL
};

static const char *njd_set_digit_rule_conv_table1b[] = {
   "四", "ヨ", "0", "1",
   NULL, NULL, NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class1c1[] = {
   /* from paper */
   "人",
   /* from dictionary */
   "人月", "人前", "人組",
   NULL
};

static const char *njd_set_digit_rule_conv_table1c1[] = {
   "四", "ヨ", "0", "1",
   "七", "シチ", "1", "2",
   NULL, NULL, NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class1c2[] = {
   /* from paper */
   "時", "時間",
   /* from dictionary */
   "時限", "時半",
   NULL
};

static const char *njd_set_digit_rule_conv_table1c2[] = {
   "四", "ヨ", "0", "1",
   "七", "シチ", "1", "2",
   "九", "ク", "0", "1",
   NULL, NULL, NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class1d[] = {
   /* from paper */
   "日" /* にち */ ,
   /* from dictionary */
   "日間",
   NULL
};

static const char *njd_set_digit_rule_conv_table1d[] = {
   /* "四", "ヨッ", "1", "2", *//* modified */
   "七", "シチ", "1", "2",
   "九", "ク", "0", "1",
   NULL, NULL, NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class1e[] = {
   /* from paper */
   "月" /* がつ */ ,
   NULL
};

static const char *njd_set_digit_rule_conv_table1e[] = {
   "四", "シ", "0", "1",
   "七", "シチ", "1", "2",
   "九", "ク", "0", "1",
   NULL, NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class1f[] = {
   /* from paper */
   /* "羽", "把", *//* modified */
   NULL
};

static const char *njd_set_digit_rule_conv_table1f[] = {
   "六", "ロッ", "1", "2",
   "八", "ハッ", "1", "2",
   "十", "ジュッ", "1", "2",
   "百", "ヒャッ", "1", "2",
   NULL, NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class1g[] = {
   /* from paper */
   "個", "階", "分" /* ふん */ , "発", "本", "鉢", "口", "切れ", "箱",
   /* from dictionary */
   "か月", "か国", "か所", "か条", "か村", "か年", "カ月", "カ国", "カ寺", "カ所", "カ条", "カ村",
   "カ店", "カ年", "ケ月", "ケ国", "ケ所", "ケ条", "ケ村", "ケ年", "ヵ月", "ヵ国", "ヵ所",
   "ヵ条", "ヵ村", "ヵ年", "ヶ月", "ヶ国", "ヶ所", "ヶ条", "ヶ村", "ヶ年", "個月", "個口",
   "個国", "個条", "個年", "箇月", "箇国", "箇所", "箇条", "箇年", "かけ", "くだり", "けた",
   "価", "課", "画", "回", "回忌", "回生", "回戦", "回線", "回分", "海里", "カイリ", "浬", "角",
   "株", "冠", "巻", "缶", "貫", "貫目", "間", "基", "期", "期生", "機", "気圧", "季", "騎",
   "客", "脚", "球", "級", "橋", "局", "曲", "極", "重ね", "斤", "金", "句", "区", "躯", "計",
   "桁", "ケタ", "校", "港", /* "行", */ "項", "組", "件", "軒", "言", "戸", "湖", "光年", "石",
   "ぴき", "ぺん", "波", "派", "敗", "杯", "拍", "泊", "版", "犯", "班", "匹", "疋", "筆", "俵",
   "票", "品", "分間", "分目", "片", "片", "篇", "編", "辺", "遍", "歩", "歩", "報", "方", "方",
   "法", "本立て", "頭身",
   NULL
};

static const char *njd_set_digit_rule_conv_table1g[] = {
   "一", "イッ", "1", "2",
   "六", "ロッ", "1", "2",
   "八", "ハッ", "1", "2",
   "十", "ジュッ", "1", "2",
   "百", "ヒャッ", "1", "2",
   NULL, NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class1h[] = {
   /* from paper */
   "．", "・", "才", "頭", "着", "足", "尺", "坪", "通り", "センチ", "シーシー",
   /* from dictionary */
   "ＣＣ", "ｃｃ", "ｃｍ", "サイクル", "サンチーム", "シーズン", "シート", "シリング",
   "シンガポールドル", "スイスフラン", "スウェーデンクローネ", "スクレ", "セット", "セント",
   "ソル", "ゾーン", "糎", "糎", "竿", "差", "差し", "歳", "歳児", "作", "冊", "刷", "皿", "棹",
   "艘", "子", "視", "式", "失", "室", "射", "社", "勺", "種", "首", "周", "周忌", "周年", "州",
   "週", "週間", "集", "宿", "所", "勝", "升", "床", "章", "色", "色", "食", "親等", "進",
   "進数", "品", "すじ", "そう", "そろい", "筋", "数", "寸", "世", "隻", "席", "石", "節", "戦",
   "線", "選", "銭", "層", "相", "揃", "たび", "つかみ", "つがい", "つぶ", "つまみ", "つ折",
   "つ折り", "とおり", "とき", "ところ", "とせ", "玉", "月", "手", "束", "続き", "体", "対",
   "卓", "樽", "反", "丁", "丁目", "鳥", "通", "掴み", "艇", "滴", "店", "転", "点", "斗", "棟",
   "盗", "灯", "等", "等席", "等地", "等分", "答", "得", "噸", "粒", "種類", "歳馬", "世紀",
   "車種",
   NULL
};

static const char *njd_set_digit_rule_conv_table1h[] = {
   "一", "イッ", "1", "2",
   "八", "ハッ", "1", "2",
   "十", "ジュッ", "1", "2",
   NULL, NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class1i[] = {
   /* from paper */
   "キロ", "カロリー",
   /* from dictionary */
   "ｃａｌ", "ｋｂ", "ｋｇ", "ｋｌ", "ｋｍ", "ｋｔ", "ｋｗ", "ｋグラム", "ｋバイト", "ｋヘルツ",
   "ｋメートル", "ｋリットル", "ｋワット", "カナダドル", "カラット", "ガロン", "キュリー",
   "キロカロリー", "キログラム", "キロトン", "キロバイト", "キロヘルツ", "キロメートル",
   "キロリットル", "キロワット", "キロワット時", "クラス", "クローナ", "クローネ", "グァラニ",
   "ケース", "コース", "粁",
   NULL
};

static const char *njd_set_digit_rule_conv_table1i[] = {
   "六", "ロッ", "1", "2",
   "十", "ジュッ", "1", "2",
   "百", "ヒャッ", "1", "2",
   NULL, NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class1j[] = {
   /* from paper */
   "トン",
   /* from dictionary */
   "ｔ", "タル", "テラ", "トライ",
   NULL
};

static const char *njd_set_digit_rule_conv_table1j[] = {
   "一", "イッ", "1", "2",
   "十", "ジュッ", "1", "2",
   NULL, NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class1k[] = {
   /* from paper */
   "房" /* ふさ */ , "柱", "％", "ポンド",
   /* from dictionary */
   "ｐａ", "ｐｐｍ", "パーセント", "パーミル", "パスカル", "パック", "パット", "ピーピーエム",
   "ピコ", "ページ", "頁", "ペア", "ペセタ", "ペソ", "ペニー", "ペニヒ", "ペンス", "ポイント",
   "振り", "針", "袋", "張り", "平米", "平方キロ", "平方キロメートル", "平方センチメートル",
   "平方メートル", "品目",
   NULL
};

static const char *njd_set_digit_rule_conv_table1k[] = {
   "十", "ジュッ", "1", "2",
   NULL, NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class2b[] = {
   /* from paper */
   "分", "版", "敗", "発", "拍", "鉢",
   /* from dictionary */
   "波", "派", "泊", "犯", "班", "品", "分間", "分目", "片", "篇", "編", "辺", "遍", "歩", "報",
   "方",
   NULL
};

static const char *njd_set_digit_rule_conv_table2b[] = {
   "一", "2",
   "三", "2",
   "四", "2",
   "六", "2",
   "八", "2",
   "十", "2",
   "百", "2",
   "千", "2",
   "万", "2",
   "何", "2",
   NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class2c[] = {
   /* from paper */
   "本", "匹", "疋", "票", "俵", "箱",
   /* from dictionary */
   "本立て", "杯", "針", "柱",
   NULL
};

static const char *njd_set_digit_rule_conv_table2c[] = {
   "一", "2",
   "三", "1",
   "六", "2",
   "八", "2",
   "十", "2",
   "百", "2",
   "千", "1",
   "万", "1",
   "何", "1",
   NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class2d[] = {
   /* from paper */
   /* "羽", "把", *//* modified */
   NULL
};

static const char *njd_set_digit_rule_conv_table2d[] = {
   "三", "1",
   "六", "2",
   "八", "2",
   "十", "2",
   "百", "2",
   "千", "1",
   "万", "1",
   "何", "1",
   NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class2e[] = {
   /* from paper */
   "軒", "石", "足", "尺",
   /* from dictionary */
   "かけ", "重ね", "件", "勺",
   NULL
};

static const char *njd_set_digit_rule_conv_table2e[] = {
   "三", "1",
   "千", "1",
   "万", "1",
   NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class2f[] = {
   /* from paper */
   "階",
   NULL
};

static const char *njd_set_digit_rule_conv_table2f[] = {
   "三", "1",
   NULL, NULL
};

static const char *njd_set_digit_rule_voiced_sound_symbol_list[] = {
   "カ", "ガ",
   "キ", "ギ",
   "ク", "グ",
   "ケ", "ゲ",
   "コ", "ゴ",
   "サ", "ザ",
   "シ", "ジ",
   "ス", "ズ",
   "セ", "ゼ",
   "ソ", "ゾ",
   "タ", "ダ",
   "チ", "ヂ",
   "ツ", "ヅ",
   "テ", "デ",
   "ト", "ド",
   "ハ", "バ",
   "ヒ", "ビ",
   "フ", "ブ",
   "ヘ", "ベ",
   "ホ", "ボ",
   NULL, NULL
};

static const char *njd_set_digit_rule_semivoiced_sound_symbol_list[] = {
   "ハ", "パ",
   "ヒ", "ピ",
   "フ", "プ",
   "ヘ", "ペ",
   "ホ", "ポ",
   NULL, NULL
};

static const char *njd_set_digit_rule_numerative_class3[] = {
   /* from paper */
   "棟", "ムネ",
   /* from dictionary */
   "かけ", "カケ",
   "くだり", "クダリ",
   "けた", "ケタ",
   "すじ", "スジ",
   "そろい", "ソロイ",
   "たび", "タビ",
   "つかみ", "ツカミ",
   "つがい", "ツガイ",
   "つまみ", "ツマミ",
   "とおり", "トオリ",
   "ところ", "トコロ",
   "とせ", "トセ",
   "まわり", "マワリ",
   "シーズン", "シーズン",
   "セット", "セット",
   "握り", "ニギリ",
   "回り", "マワリ",
   "株", "カブ",
   "竿", "サオ",
   "筋", "スジ",
   "桁", "ケタ",
   "ケタ", "ケタ",
   "月", "ツキ",
   "言", "コト",
   "口", "クチ",
   "差し", "サシ",
   "皿", "サラ",
   "山", "ヤマ",
   "勺", "シャク",
   "尺", "シャク",
   "重ね", "カサネ",
   "振り", "フリ",
   "針", "ハリ",
   "切れ", "キレ",
   "束", "タバ",
   "続き", "ツヅキ",
   "揃", "ソロイ",
   "袋", "フクロ",
   "柱", "ハシラ",
   "張り", "ハリ",
   "通り", "トオリ",
   "掴み", "ツカミ",
   "坪", "ツボ",
   "箱", "ハコ",
   "鉢", "ハチ",
   "晩", "バン",
   "品", "シナ",
   "瓶", "ビン",
   "分け", "ワケ",
   "幕", "マク",
   "夜", "ヤ",
   "夜", "ヨ",
   "粒", "ツブ",
   "枠", "ワク",
   "棹", "サオ",
   "つ折", "ツオリ",
   "つ折り", "ツオリ",
   "粒", "ツブ",
   "つぶ", "ツブ",
   "とき", "トキ",
   "重ね", "ガサネ",
   NULL, NULL
};

static const char *njd_set_digit_rule_conv_table3[] = {
   "一", "ヒト", "0", "2",
   "二", "フタ", "0", "2",
   /* "三", "ミ", "1", "1", *//* modified */
   NULL, NULL, NULL, NULL
};

static const char *njd_set_digit_rule_conv_table4[] = {
   "一", "一人,名詞,副詞可能,*,*,*,*,一人,ヒトリ,ヒトリ,2/3,*",
   "二", "二人,名詞,副詞可能,*,*,*,*,二人,フタリ,フタリ,3/3,*",
   NULL, NULL
};

static const char *njd_set_digit_rule_conv_table5[] = {
   "一", "一日,名詞,副詞可能,*,*,*,*,一日,イチニチ,イチニチ,4/4,*",
   "二", "二日,名詞,副詞可能,*,*,*,*,二日,フツカ,フツカ,0/3,*",
   "三", "三日,名詞,副詞可能,*,*,*,*,三日,ミッカ,ミッカ,0/3,*",
   "四", "四日,名詞,副詞可能,*,*,*,*,四日,ヨッカ,ヨッカ,0/3,*",
   "五", "五日,名詞,副詞可能,*,*,*,*,五日,イツカ,イツカ,0/3,*",
   "六", "六日,名詞,副詞可能,*,*,*,*,六日,ムイカ,ムイカ,0/3,*",
   "七", "七日,名詞,副詞可能,*,*,*,*,七日,ナノカ,ナノカ,0/3,*",
   "八", "八日,名詞,副詞可能,*,*,*,*,八日,ヨウカ,ヨーカ,0/3,*",
   "九", "九日,名詞,副詞可能,*,*,*,*,九日,ココノカ,ココノカ,0/4,*",
   "十", "十日,名詞,副詞可能,*,*,*,*,十日,トウカ,トーカ,0/3,*",
   NULL, NULL
};

static const char *njd_set_digit_rule_conv_table6[] = {
   "一", "一日間,名詞,副詞可能,*,*,*,*,一日間,イチニチカン,イチニチカン,4/6,*",
   "二", "二日間,名詞,副詞可能,*,*,*,*,二日,フツカカン,フツカカン,3/5,*",
   "三", "三日間,名詞,副詞可能,*,*,*,*,三日,ミッカカン,ミッカカン,3/5,*",
   "四", "四日間,名詞,副詞可能,*,*,*,*,四日,ヨッカカン,ヨッカカン,3/5,*",
   "五", "五日間,名詞,副詞可能,*,*,*,*,五日,イツカカン,イツカカン,3/5,*",
   "六", "六日間,名詞,副詞可能,*,*,*,*,六日,ムイカカン,ムイカカン,3/5,*",
   "七", "七日間,名詞,副詞可能,*,*,*,*,七日,ナノカカン,ナノカカン,3/5,*",
   "八", "八日間,名詞,副詞可能,*,*,*,*,八日,ヨウカカン,ヨーカカン,3/5,*",
   "九", "九日間,名詞,副詞可能,*,*,*,*,九日,ココノカカン,ココノカカン,4/6,*",
   "十", "十日間,名詞,副詞可能,*,*,*,*,十日,トウカカン,トーカカン,3/5,*",
   NULL, NULL
};

NJD_SET_DIGIT_RULE_H_END;

#endif                          /* !NJD_SET_DIGIT_RULE_H */

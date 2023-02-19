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

#ifndef JPCOMMON_RULE_H
#define JPCOMMON_RULE_H

#ifdef __cplusplus
#define JPCOMMON_RULE_H_START extern "C" {
#define JPCOMMON_RULE_H_END   }
#else
#define JPCOMMON_RULE_H_START
#define JPCOMMON_RULE_H_END
#endif                          /* __CPLUSPLUS */

JPCOMMON_RULE_H_START;

#define JPCOMMON_MORA_UNVOICE "’"
#define JPCOMMON_MORA_LONG_VOWEL "ー"
#define JPCOMMON_MORA_SHORT_PAUSE "、"
#define JPCOMMON_MORA_QUESTION "？"
#define JPCOMMON_PHONEME_SHORT_PAUSE "pau"
#define JPCOMMON_PHONEME_SILENT "sil"
#define JPCOMMON_PHONEME_UNKNOWN "xx"
#define JPCOMMON_FLAG_QUESTION "1"

static const char *jpcommon_unvoice_list[] = {
   "a", "A",
   "i", "I",
   "u", "U",
   "e", "E",
   "o", "O",
   NULL, NULL
};

static const char *jpcommon_mora_list[] = {
   "ヴョ", "by", "o",
   "ヴュ", "by", "u",
   "ヴャ", "by", "a",
   "ヴォ", "v", "o",
   "ヴェ", "v", "e",
   "ヴィ", "v", "i",
   "ヴァ", "v", "a",
   "ヴ", "v", "u",
   "ン", "N", NULL,
   "ヲ", "o", NULL,
   "ヱ", "e", NULL,
   "ヰ", "i", NULL,
   "ワ", "w", "a",
   "ヮ", "w", "a",
   "ロ", "r", "o",
   "レ", "r", "e",
   "ル", "r", "u",
   "リョ", "ry", "o",
   "リュ", "ry", "u",
   "リャ", "ry", "a",
   "リェ", "ry", "e",
   "リ", "r", "i",
   "ラ", "r", "a",
   "ヨ", "y", "o",
   "ョ", "y", "o",
   "ユ", "y", "u",
   "ュ", "y", "u",
   "ヤ", "y", "a",
   "ャ", "y", "a",
   "モ", "m", "o",
   "メ", "m", "e",
   "ム", "m", "u",
   "ミョ", "my", "o",
   "ミュ", "my", "u",
   "ミャ", "my", "a",
   "ミェ", "my", "e",
   "ミ", "m", "i",
   "マ", "m", "a",
   "ポ", "p", "o",
   "ボ", "b", "o",
   "ホ", "h", "o",
   "ペ", "p", "e",
   "ベ", "b", "e",
   "ヘ", "h", "e",
   "プ", "p", "u",
   "ブ", "b", "u",
   "フォ", "f", "o",
   "フェ", "f", "e",
   "フィ", "f", "i",
   "ファ", "f", "a",
   "フ", "f", "u",
   "ピョ", "py", "o",
   "ピュ", "py", "u",
   "ピャ", "py", "a",
   "ピェ", "py", "e",
   "ピ", "p", "i",
   "ビョ", "by", "o",
   "ビュ", "by", "u",
   "ビャ", "by", "a",
   "ビェ", "by", "e",
   "ビ", "b", "i",
   "ヒョ", "hy", "o",
   "ヒュ", "hy", "u",
   "ヒャ", "hy", "a",
   "ヒェ", "hy", "e",
   "ヒ", "h", "i",
   "パ", "p", "a",
   "バ", "b", "a",
   "ハ", "h", "a",
   "ノ", "n", "o",
   "ネ", "n", "e",
   "ヌ", "n", "u",
   "ニョ", "ny", "o",
   "ニュ", "ny", "u",
   "ニャ", "ny", "a",
   "ニェ", "ny", "e",
   "ニ", "n", "i",
   "ナ", "n", "a",
   "ドゥ", "d", "u",
   "ド", "d", "o",
   "トゥ", "t", "u",
   "ト", "t", "o",
   "デョ", "dy", "o",
   "デュ", "dy", "u",
   "デャ", "dy", "a",
   "ディ", "d", "i",
   "デ", "d", "e",
   "テョ", "ty", "o",
   "テュ", "ty", "u",
   "テャ", "ty", "a",
   "ティ", "t", "i",
   "テ", "t", "e",
   "ヅ", "z", "u",
   "ツォ", "ts", "o",
   "ツェ", "ts", "e",
   "ツィ", "ts", "i",
   "ツァ", "ts", "a",
   "ツ", "ts", "u",
   "ッ", "cl", NULL,
   "ヂ", "j", "i",
   "チョ", "ch", "o",
   "チュ", "ch", "u",
   "チャ", "ch", "a",
   "チェ", "ch", "e",
   "チ", "ch", "i",
   "ダ", "d", "a",
   "タ", "t", "a",
   "ゾ", "z", "o",
   "ソ", "s", "o",
   "ゼ", "z", "e",
   "セ", "s", "e",
   "ズィ", "z", "i",
   "ズ", "z", "u",
   "スィ", "s", "i",
   "ス", "s", "u",
   "ジョ", "j", "o",
   "ジュ", "j", "u",
   "ジャ", "j", "a",
   "ジェ", "j", "e",
   "ジ", "j", "i",
   "ショ", "sh", "o",
   "シュ", "sh", "u",
   "シャ", "sh", "a",
   "シェ", "sh", "e",
   "シ", "sh", "i",
   "ザ", "z", "a",
   "サ", "s", "a",
   "ゴ", "g", "o",
   "コ", "k", "o",
   "ゲ", "g", "e",
   "ケ", "k", "e",
   "ヶ", "k", "e",
   "グヮ", "gw", "a",
   "グ", "g", "u",
   "クヮ", "kw", "a",
   "ク", "k", "u",
   "ギョ", "gy", "o",
   "ギュ", "gy", "u",
   "ギャ", "gy", "a",
   "ギェ", "gy", "e",
   "ギ", "g", "i",
   "キョ", "ky", "o",
   "キュ", "ky", "u",
   "キャ", "ky", "a",
   "キェ", "ky", "e",
   "キ", "k", "i",
   "ガ", "g", "a",
   "カ", "k", "a",
   "オ", "o", NULL,
   "ォ", "o", NULL,
   "エ", "e", NULL,
   "ェ", "e", NULL,
   "ウォ", "w", "o",
   "ウェ", "w", "e",
   "ウィ", "w", "i",
   "ウ", "u", NULL,
   "ゥ", "u", NULL,
   "イェ", "y", "e",
   "イ", "i", NULL,
   "ィ", "i", NULL,
   "ア", "a", NULL,
   "ァ", "a", NULL,
   NULL, NULL, NULL
};

static const char *jpcommon_pos_list[] = {
   "その他", "xx",
   "感動詞", "09",
   "記号", "xx",
   "形状詞", "19",
   "形容詞", "01",
   "助詞-その他", "23",
   "助詞-格助詞", "13",
   "助詞-係助詞", "24",
   "助詞-終助詞", "14",
   "助詞-接続助詞", "12",
   "助詞-副助詞", "11",
   "助動詞", "10",
   "接続詞", "08",
   "接頭辞", "16",
   "接頭辞-形状詞的", "16",
   "接頭辞-形容詞的", "16",
   "接頭辞-動詞的", "16",
   "接頭辞-名詞的", "16",
   "接尾辞-形状詞的", "15",
   "接尾辞-形容詞的", "15",
   "接尾辞-動詞的", "15",
   "接尾辞-名詞的", "15",
   "代名詞", "04",
   "動詞", "20",
   "動詞-非自立", "17",
   "副詞", "06",
   "名詞-サ変接続", "03",
   "名詞-固有名詞", "18",
   "名詞-数詞", "05",
   "名詞-非自立", "22",
   "名詞-普通名詞", "02",
   "連体詞", "07",
   "フィラー", "25",
   NULL, NULL
};


static const char *jpcommon_cform_list[] = {
   "*", "xx",
   "その他", "6",
   "仮定形", "4",
   "基本形", "2",
   "未然形", "0",
   "命令形", "5",
   "連体形", "3",
   "連用形", "1",
   NULL, NULL
};

static const char *jpcommon_ctype_list[] = {
   "*", "xx",
   "カ行変格", "5",
   "サ行変格", "4",
   "ラ行変格", "6",
   "一段", "3",
   "形容詞", "7",
   "五段", "1",
   "四段", "6",
   "助動詞", "7",
   "二段", "6",
   "不変化", "6",
   "文語助動詞", "6",
   NULL, NULL
};

JPCOMMON_RULE_H_END;

#endif                          /* !JPCOMMON_RULE_H */

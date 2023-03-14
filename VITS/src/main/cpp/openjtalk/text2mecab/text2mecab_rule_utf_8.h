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

#ifndef TEXT2MECAB_RULE_H
#define TEXT2MECAB_RULE_H
#define CHARSET_UTF_8 1

#ifdef __cplusplus
#define TEXT2MECAB_RULE_H_START extern "C" {
#define TEXT2MECAB_RULE_H_END   }
#else
#define TEXT2MECAB_RULE_H_START
#define TEXT2MECAB_RULE_H_END
#endif                          /* __CPLUSPLUS */

TEXT2MECAB_RULE_H_START;

static const char text2mecab_control_range[] = {
   0x00, 0x7F
};

static const char text2mecab_kanji_range[] = {
#ifdef CHARSET_EUC_JP
   2, 0xA1, 0xFE,
   3, 0x8F, 0x8F,
#endif                          /* CHARSET_EUC_JP */
#ifdef CHARSET_SHIFT_JIS
   2, 0x81, 0xFC,
#endif                          /* CHARSET_SHIFT_JIS */
#ifdef CHARSET_UTF_8
   2, 0xC0, 0xDF,
   3, 0xE0, 0xEF,
   4, 0xF0, 0xF7,
#endif                          /* CHARSET_UTF_8 */
   -1, -1, -1
};

static const char *text2mecab_conv_list[] = {
   " ", "　",
   "!", "！",
   "\"", "”",
   "#", "＃",
   "$", "＄",
   "%", "％",
   "&", "＆",
   "'", "’",
   "(", "（",
   ")", "）",
   "*", "＊",
   "+", "＋",
   ",", "，",
   "-", "−",
   ".", "．",
   "/", "／",
   "0", "０",
   "1", "１",
   "2", "２",
   "3", "３",
   "4", "４",
   "5", "５",
   "6", "６",
   "7", "７",
   "8", "８",
   "9", "９",
   ":", "：",
   ";", "；",
   "<", "＜",
   "=", "＝",
   ">", "＞",
   "?", "？",
   "@", "＠",
   "A", "Ａ",
   "B", "Ｂ",
   "C", "Ｃ",
   "D", "Ｄ",
   "E", "Ｅ",
   "F", "Ｆ",
   "G", "Ｇ",
   "H", "Ｈ",
   "I", "Ｉ",
   "J", "Ｊ",
   "K", "Ｋ",
   "L", "Ｌ",
   "M", "Ｍ",
   "N", "Ｎ",
   "O", "Ｏ",
   "P", "Ｐ",
   "Q", "Ｑ",
   "R", "Ｒ",
   "S", "Ｓ",
   "T", "Ｔ",
   "U", "Ｕ",
   "V", "Ｖ",
   "W", "Ｗ",
   "X", "Ｘ",
   "Y", "Ｙ",
   "Z", "Ｚ",
   "[", "［",
   "\\", "￥",
   "]", "］",
   "^", "＾",
   "_", "＿",
   "`", "‘",
   "a", "ａ",
   "b", "ｂ",
   "c", "ｃ",
   "d", "ｄ",
   "e", "ｅ",
   "f", "ｆ",
   "g", "ｇ",
   "h", "ｈ",
   "i", "ｉ",
   "j", "ｊ",
   "k", "ｋ",
   "l", "ｌ",
   "m", "ｍ",
   "n", "ｎ",
   "o", "ｏ",
   "p", "ｐ",
   "q", "ｑ",
   "r", "ｒ",
   "s", "ｓ",
   "t", "ｔ",
   "u", "ｕ",
   "v", "ｖ",
   "w", "ｗ",
   "x", "ｘ",
   "y", "ｙ",
   "z", "ｚ",
   "{", "｛",
   "|", "｜",
   "}", "｝",
   "~", "〜",
   "ｳﾞ", "ヴ",
   "ｶﾞ", "ガ",
   "ｷﾞ", "ギ",
   "ｸﾞ", "グ",
   "ｹﾞ", "ゲ",
   "ｺﾞ", "ゴ",
   "ｻﾞ", "ザ",
   "ｼﾞ", "ジ",
   "ｽﾞ", "ズ",
   "ｾﾞ", "ゼ",
   "ｿﾞ", "ゾ",
   "ﾀﾞ", "ダ",
   "ﾁﾞ", "ヂ",
   "ﾂﾞ", "ヅ",
   "ﾃﾞ", "デ",
   "ﾄﾞ", "ド",
   "ﾊﾞ", "バ",
   "ﾋﾞ", "ビ",
   "ﾌﾞ", "ブ",
   "ﾍﾞ", "ベ",
   "ﾎﾞ", "ボ",
   "ﾊﾟ", "パ",
   "ﾋﾟ", "ピ",
   "ﾌﾟ", "プ",
   "ﾍﾟ", "ペ",
   "ﾎﾟ", "ポ",
   "｡", "。",
   "｢", "「",
   "｣", "」",
   "､", "、",
   "･", "・",
   "ｦ", "ヲ",
   "ｧ", "ァ",
   "ｨ", "ィ",
   "ｩ", "ゥ",
   "ｪ", "ェ",
   "ｫ", "ォ",
   "ｬ", "ャ",
   "ｭ", "ュ",
   "ｮ", "ョ",
   "ｯ", "ッ",
   "ｰ", "ー",
   "ｱ", "ア",
   "ｲ", "イ",
   "ｳ", "ウ",
   "ｴ", "エ",
   "ｵ", "オ",
   "ｶ", "カ",
   "ｷ", "キ",
   "ｸ", "ク",
   "ｹ", "ケ",
   "ｺ", "コ",
   "ｻ", "サ",
   "ｼ", "シ",
   "ｽ", "ス",
   "ｾ", "セ",
   "ｿ", "ソ",
   "ﾀ", "タ",
   "ﾁ", "チ",
   "ﾂ", "ツ",
   "ﾃ", "テ",
   "ﾄ", "ト",
   "ﾅ", "ナ",
   "ﾆ", "ニ",
   "ﾇ", "ヌ",
   "ﾈ", "ネ",
   "ﾉ", "ノ",
   "ﾊ", "ハ",
   "ﾋ", "ヒ",
   "ﾌ", "フ",
   "ﾍ", "ヘ",
   "ﾎ", "ホ",
   "ﾏ", "マ",
   "ﾐ", "ミ",
   "ﾑ", "ム",
   "ﾒ", "メ",
   "ﾓ", "モ",
   "ﾔ", "ヤ",
   "ﾕ", "ユ",
   "ﾖ", "ヨ",
   "ﾗ", "ラ",
   "ﾘ", "リ",
   "ﾙ", "ル",
   "ﾚ", "レ",
   "ﾛ", "ロ",
   "ﾜ", "ワ",
   "ﾝ", "ン",
   "ﾞ", "",
   "ﾟ", "",
   NULL, NULL
};

TEXT2MECAB_RULE_H_END;

#endif                          /* !TEXT2MECAB_RULE_H */

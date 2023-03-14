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

#ifndef NJD2JPCOMMON_RULE_H
#define NJD2JPCOMMON_RULE_H

#ifdef __cplusplus
#define NJD2JPCOMMON_RULE_H_START extern "C" {
#define NJD2JPCOMMON_RULE_H_END   }
#else
#define NJD2JPCOMMON_RULE_H_START
#define NJD2JPCOMMON_RULE_H_END
#endif                          /* __CPLUSPLUS */

NJD2JPCOMMON_RULE_H_START;

static const char *njd2jpcommon_pos_list[] = {
   "その他", "間投", "*", "*", "その他",
   "フィラー", "*", "*", "*", "感動詞",
   "感動詞", "*", "*", "*", "感動詞",
   "記号", "*", "*", "*", "記号",
   "記号", "アルファベット", "*", "*", "記号",
   "記号", "一般", "*", "*", "記号",
   "記号", "括弧開", "*", "*", "記号",
   "記号", "括弧閉", "*", "*", "記号",
   "記号", "句点", "*", "*", "記号",
   "記号", "空白", "*", "*", "記号",
   "記号", "数", "*", "*", "記号",
   "記号", "読点", "*", "*", "記号",
   "形容詞", "自立", "*", "*", "形容詞",
   "形容詞", "接尾", "*", "*", "接尾辞-形容詞的",
   "形容詞", "非自立", "*", "*", "形容詞",
   "助詞", "格助詞", "一般", "*", "助詞-格助詞",
   "助詞", "格助詞", "引用", "*", "助詞-格助詞",
   "助詞", "格助詞", "連語", "*", "助詞-格助詞",
   "助詞", "係助詞", "*", "*", "助詞-係助詞",
   "助詞", "終助詞", "*", "*", "助詞-終助詞",
   "助詞", "接続助詞", "*", "*", "助詞-接続助詞",
   "助詞", "特殊", "*", "*", "助詞-その他",
   "助詞", "副詞化", "*", "*", "助詞-その他",
   "助詞", "副助詞", "*", "*", "助詞-副助詞",
   "助詞", "副助詞／並立助詞／終助詞", "*", "*", "助詞-その他",
   "助詞", "並立助詞", "*", "*", "助詞-その他",
   "助詞", "連体化", "*", "*", "助詞-その他",
   "助動詞", "*", "*", "*", "助動詞",
   "接続詞", "*", "*", "*", "接続詞",
   "接頭詞", "形容詞接続", "*", "*", "接頭辞",
   "接頭詞", "数接続", "*", "*", "接頭辞",
   "接頭詞", "動詞接続", "*", "*", "接頭辞",
   "接頭詞", "名詞接続", "*", "*", "接頭辞",
   "動詞", "自立", "*", "*", "動詞",
   "動詞", "接尾", "*", "*", "接尾辞-動詞的",
   "動詞", "非自立", "*", "*", "動詞-非自立",
   "副詞", "*", "*", "*", "副詞",
   "副詞", "一般", "*", "*", "副詞",
   "副詞", "助詞類接続", "*", "*", "副詞",
   "名詞", "サ変接続", "*", "*", "名詞-サ変接続",
   "名詞", "ナイ形容詞語幹", "*", "*", "名詞-普通名詞",
   "名詞", "一般", "*", "*", "名詞-普通名詞",
   "名詞", "引用文字列", "*", "*", "名詞-普通名詞",
   "名詞", "形容動詞語幹", "*", "*", "形状詞",
   "名詞", "固有名詞", "一般", "*", "名詞-固有名詞",
   "名詞", "固有名詞", "人名", "一般", "名詞-固有名詞",
   "名詞", "固有名詞", "人名", "姓", "名詞-固有名詞",
   "名詞", "固有名詞", "人名", "名", "名詞-固有名詞",
   "名詞", "固有名詞", "組織", "*", "名詞-固有名詞",
   "名詞", "固有名詞", "地域", "一般", "名詞-固有名詞",
   "名詞", "固有名詞", "地域", "国", "名詞-固有名詞",
   "名詞", "数", "*", "*", "名詞-数詞",
   "名詞", "接続詞的", "*", "*", "名詞-普通名詞",
   "名詞", "接尾", "サ変接続", "*", "接尾辞-名詞的",
   "名詞", "接尾", "一般", "*", "接尾辞-名詞的",
   "名詞", "接尾", "形容動詞語幹", "*", "接尾辞-形状詞的",
   "名詞", "接尾", "助数詞", "*", "接尾辞-名詞的",
   "名詞", "接尾", "助動詞語幹", "*", "接尾辞-名詞的",
   "名詞", "接尾", "人名", "*", "接尾辞-名詞的",
   "名詞", "接尾", "地域", "*", "接尾辞-名詞的",
   "名詞", "接尾", "特殊", "*", "接尾辞-名詞的",
   "名詞", "接尾", "副詞可能", "*", "接尾辞-名詞的",
   "名詞", "代名詞", "一般", "*", "代名詞",
   "名詞", "代名詞", "縮約", "*", "代名詞",
   "名詞", "動詞非自立的", "*", "*", "名詞-普通名詞",
   "名詞", "特殊", "助動詞語幹", "*", "名詞-普通名詞",
   "名詞", "非自立", "一般", "*", "名詞-非自立",
   "名詞", "非自立", "形容動詞語幹", "*", "名詞-非自立",
   "名詞", "非自立", "助動詞語幹", "*", "名詞-非自立",
   "名詞", "非自立", "副詞可能", "*", "名詞-非自立",
   "名詞", "非自立", "*", "*", "名詞-非自立",
   "名詞", "副詞可能", "*", "*", "名詞-普通名詞",
   "連体詞", "*", "*", "*", "連体詞",
   NULL, NULL, NULL, NULL, NULL
};

static const char *njd2jpcommon_cform_list[] = {
   "*", "*",
   "ガル接続", "その他",
   "音便基本形", "基本形",
   "仮定形", "仮定形",
   "仮定縮約１", "仮定形",
   "仮定縮約２", "仮定形",
   "基本形", "基本形",
   "基本形-促音便", "基本形",
   "現代基本形", "基本形",
   "体言接続", "連体形",
   "体言接続特殊", "連体形",
   "体言接続特殊２", "連体形",
   "文語基本形", "基本形",
   "未然ウ接続", "未然形",
   "未然ヌ接続", "未然形",
   "未然レル接続", "未然形",
   "未然形", "未然形",
   "未然特殊", "未然形",
   "命令ｅ", "命令形",
   "命令ｉ", "命令形",
   "命令ｒｏ", "命令形",
   "命令ｙｏ", "命令形",
   "連用ゴザイ接続", "連用形",
   "連用タ接続", "連用形",
   "連用テ接続", "連用形",
   "連用デ接続", "連用形",
   "連用ニ接続", "連用形",
   "連用形", "連用形",
   NULL, NULL
};

static const char *njd2jpcommon_ctype_list[] = {
   "*", "*",
   "カ変・クル", "カ行変格",
   "カ変・来ル", "カ行変格",
   "サ変・−スル", "サ行変格",
   "サ変・−ズル", "サ行変格",
   "サ変・スル", "サ行変格",
   "ラ変", "ラ行変格",
   "一段", "一段",
   "一段・クレル", "一段",
   "一段・得ル", "一段",
   "下二・カ行", "二段",
   "下二・ガ行", "二段",
   "下二・タ行", "二段",
   "下二・ダ行", "二段",
   "下二・ハ行", "二段",
   "下二・マ行", "二段",
   "下二・得", "二段",
   "形容詞・アウオ段", "形容詞",
   "形容詞・イイ", "形容詞",
   "形容詞・イ段", "形容詞",
   "五段・カ行イ音便", "五段",
   "五段・カ行促音便", "五段",
   "五段・カ行促音便ユク", "五段",
   "五段・ガ行", "五段",
   "五段・サ行", "五段",
   "五段・タ行", "五段",
   "五段・ナ行", "五段",
   "五段・バ行", "五段",
   "五段・マ行", "五段",
   "五段・ラ行", "五段",
   "五段・ラ行アル", "五段",
   "五段・ラ行特殊", "五段",
   "五段・ワ行ウ音便", "五段",
   "五段・ワ行促音便", "五段",
   "四段・サ行", "四段",
   "四段・タ行", "四段",
   "四段・ハ行", "四段",
   "四段・バ行", "四段",
   "上二・ダ行", "二段",
   "上二・ハ行", "二段",
   "特殊・ジャ", "助動詞",
   "特殊・タ", "助動詞",
   "特殊・タイ", "助動詞",
   "特殊・ダ", "助動詞",
   "特殊・デス", "助動詞",
   "特殊・ナイ", "助動詞",
   "特殊・ヌ", "助動詞",
   "特殊・マス", "助動詞",
   "特殊・ヤ", "助動詞",
   "不変化型", "不変化",
   "文語・キ", "文語助動詞",
   "文語・ケリ", "文語助動詞",
   "文語・ゴトシ", "文語助動詞",
   "文語・ナリ", "文語助動詞",
   "文語・ベシ", "文語助動詞",
   "文語・マジ", "文語助動詞",
   "文語・リ", "文語助動詞",
   "文語・ル", "文語助動詞",
   NULL, NULL
};

NJD2JPCOMMON_RULE_H_END;

#endif                          /* !NJD2JPCOMMON_RULE_H */

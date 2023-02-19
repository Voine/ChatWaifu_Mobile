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

#ifndef NJD_SET_ACCENT_PHRASE_RULE_H
#define NJD_SET_ACCENT_PHRASE_RULE_H

#ifdef __cplusplus
#define NJD_SET_ACCENT_PHRASE_RULE_H_START extern "C" {
#define NJD_SET_ACCENT_PHRASE_RULE_H_END   }
#else
#define NJD_SET_ACCENT_PHRASE_RULE_H_START
#define NJD_SET_ACCENT_PHRASE_RULE_H_END
#endif                          /* __CPLUSPLUS */

NJD_SET_ACCENT_PHRASE_RULE_H_START;

/*
  Rule 01 デフォルトはくっつける
  Rule 02 「名詞」の連続はくっつける
  Rule 03 「形容詞」の後に「名詞」がきたら別のアクセント句に
  Rule 04 「名詞,形容動詞語幹」の後に「名詞」がきたら別のアクセント句に
  Rule 05 「動詞」の後に「形容詞」or「名詞」がきたら別のアクセント句に
  Rule 06 「副詞」，「接続詞」，「連体詞」は単独のアクセント句に
  Rule 07 「名詞,副詞可能」（すべて，など）は単独のアクセント句に
  Rule 08 「助詞」or「助動詞」（付属語）は前にくっつける
  Rule 09 「助詞」or「助動詞」（付属語）の後の「助詞」，「助動詞」以外（自立語）は別のアクセント句に
  Rule 10 「*,接尾」の後の「名詞」は別のアクセント句に
  Rule 11 「形容詞,非自立」は「動詞,連用*」or「形容詞,連用*」or「助詞,接続助詞,て」or「助詞,接続助詞,で」に接続する場合に前にくっつける
  Rule 12 「動詞,非自立」は「動詞,連用*」or「名詞,サ変接続」に接続する場合に前にくっつける
  Rule 13 「名詞」の後に「動詞」or「形容詞」or「名詞,形容動詞語幹」がきたら別のアクセント句に
  Rule 14 「記号」は単独のアクセント句に
  Rule 15 「接頭詞」は単独のアクセント句に
  Rule 16 「*,*,*,姓」の後の「名詞」は別のアクセント句に
  Rule 17 「名詞」の後の「*,*,*,名」は別のアクセント句に
  Rule 18 「*,接尾」は前にくっつける
*/

#define NJD_SET_ACCENT_PHRASE_MEISHI "名詞"
#define NJD_SET_ACCENT_PHRASE_KEIYOUSHI "形容詞"
#define NJD_SET_ACCENT_PHRASE_DOUSHI "動詞"
#define NJD_SET_ACCENT_PHRASE_FUKUSHI "副詞"
#define NJD_SET_ACCENT_PHRASE_SETSUZOKUSHI "接続詞"
#define NJD_SET_ACCENT_PHRASE_RENTAISHI "連体詞"
#define NJD_SET_ACCENT_PHRASE_JODOUSHI "助動詞"
#define NJD_SET_ACCENT_PHRASE_JOSHI "助詞"
#define NJD_SET_ACCENT_PHRASE_KIGOU "記号"
#define NJD_SET_ACCENT_PHRASE_KEIYOUDOUSHI_GOKAN "形容動詞語幹"
#define NJD_SET_ACCENT_PHRASE_FUKUSHI_KANOU "副詞可能"
#define NJD_SET_ACCENT_PHRASE_SETSUBI "接尾"
#define NJD_SET_ACCENT_PHRASE_HIJIRITSU "非自立"
#define NJD_SET_ACCENT_PHRASE_RENYOU "連用"
#define NJD_SET_ACCENT_PHRASE_SETSUZOKUJOSHI "接続助詞"
#define NJD_SET_ACCENT_PHRASE_SAHEN_SETSUZOKU "サ変接続"
#define NJD_SET_ACCENT_PHRASE_TE "て"
#define NJD_SET_ACCENT_PHRASE_DE "で"
#define NJD_SET_ACCENT_PHRASE_SETTOUSHI "接頭詞"
#define NJD_SET_ACCENT_PHRASE_SEI "姓"
#define NJD_SET_ACCENT_PHRASE_MEI "名"

NJD_SET_ACCENT_PHRASE_RULE_H_END;

#endif                          /* !NJD_SET_ACCENT_PHRASE_RULE_H */

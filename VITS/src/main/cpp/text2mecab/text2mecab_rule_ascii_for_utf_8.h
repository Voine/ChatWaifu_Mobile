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
   " ", "\xe3\x80\x80",
   "!", "\xef\xbc\x81",
   "\"", "\xe2\x80\x9d",
   "#", "\xef\xbc\x83",
   "$", "\xef\xbc\x84",
   "%", "\xef\xbc\x85",
   "&", "\xef\xbc\x86",
   "'", "\xe2\x80\x99",
   "(", "\xef\xbc\x88",
   ")", "\xef\xbc\x89",
   "*", "\xef\xbc\x8a",
   "+", "\xef\xbc\x8b",
   ",", "\xef\xbc\x8c",
   "-", "\xe2\x88\x92",
   ".", "\xef\xbc\x8e",
   "/", "\xef\xbc\x8f",
   "0", "\xef\xbc\x90",
   "1", "\xef\xbc\x91",
   "2", "\xef\xbc\x92",
   "3", "\xef\xbc\x93",
   "4", "\xef\xbc\x94",
   "5", "\xef\xbc\x95",
   "6", "\xef\xbc\x96",
   "7", "\xef\xbc\x97",
   "8", "\xef\xbc\x98",
   "9", "\xef\xbc\x99",
   ":", "\xef\xbc\x9a",
   ";", "\xef\xbc\x9b",
   "<", "\xef\xbc\x9c",
   "=", "\xef\xbc\x9d",
   ">", "\xef\xbc\x9e",
   "?", "\xef\xbc\x9f",
   "@", "\xef\xbc\xa0",
   "A", "\xef\xbc\xa1",
   "B", "\xef\xbc\xa2",
   "C", "\xef\xbc\xa3",
   "D", "\xef\xbc\xa4",
   "E", "\xef\xbc\xa5",
   "F", "\xef\xbc\xa6",
   "G", "\xef\xbc\xa7",
   "H", "\xef\xbc\xa8",
   "I", "\xef\xbc\xa9",
   "J", "\xef\xbc\xaa",
   "K", "\xef\xbc\xab",
   "L", "\xef\xbc\xac",
   "M", "\xef\xbc\xad",
   "N", "\xef\xbc\xae",
   "O", "\xef\xbc\xaf",
   "P", "\xef\xbc\xb0",
   "Q", "\xef\xbc\xb1",
   "R", "\xef\xbc\xb2",
   "S", "\xef\xbc\xb3",
   "T", "\xef\xbc\xb4",
   "U", "\xef\xbc\xb5",
   "V", "\xef\xbc\xb6",
   "W", "\xef\xbc\xb7",
   "X", "\xef\xbc\xb8",
   "Y", "\xef\xbc\xb9",
   "Z", "\xef\xbc\xba",
   "[", "\xef\xbc\xbb",
   "\\", "\xef\xbf\xa5",
   "]", "\xef\xbc\xbd",
   "^", "\xef\xbc\xbe",
   "_", "\xef\xbc\xbf",
   "`", "\xe2\x80\x98",
   "a", "\xef\xbd\x81",
   "b", "\xef\xbd\x82",
   "c", "\xef\xbd\x83",
   "d", "\xef\xbd\x84",
   "e", "\xef\xbd\x85",
   "f", "\xef\xbd\x86",
   "g", "\xef\xbd\x87",
   "h", "\xef\xbd\x88",
   "i", "\xef\xbd\x89",
   "j", "\xef\xbd\x8a",
   "k", "\xef\xbd\x8b",
   "l", "\xef\xbd\x8c",
   "m", "\xef\xbd\x8d",
   "n", "\xef\xbd\x8e",
   "o", "\xef\xbd\x8f",
   "p", "\xef\xbd\x90",
   "q", "\xef\xbd\x91",
   "r", "\xef\xbd\x92",
   "s", "\xef\xbd\x93",
   "t", "\xef\xbd\x94",
   "u", "\xef\xbd\x95",
   "v", "\xef\xbd\x96",
   "w", "\xef\xbd\x97",
   "x", "\xef\xbd\x98",
   "y", "\xef\xbd\x99",
   "z", "\xef\xbd\x9a",
   "{", "\xef\xbd\x9b",
   "|", "\xef\xbd\x9c",
   "}", "\xef\xbd\x9d",
   "~", "\xe3\x80\x9c",
   "\xef\xbd\xb3\xef\xbe\x9e", "\xe3\x83\xb4",
   "\xef\xbd\xb6\xef\xbe\x9e", "\xe3\x82\xac",
   "\xef\xbd\xb7\xef\xbe\x9e", "\xe3\x82\xae",
   "\xef\xbd\xb8\xef\xbe\x9e", "\xe3\x82\xb0",
   "\xef\xbd\xb9\xef\xbe\x9e", "\xe3\x82\xb2",
   "\xef\xbd\xba\xef\xbe\x9e", "\xe3\x82\xb4",
   "\xef\xbd\xbb\xef\xbe\x9e", "\xe3\x82\xb6",
   "\xef\xbd\xbc\xef\xbe\x9e", "\xe3\x82\xb8",
   "\xef\xbd\xbd\xef\xbe\x9e", "\xe3\x82\xba",
   "\xef\xbd\xbe\xef\xbe\x9e", "\xe3\x82\xbc",
   "\xef\xbd\xbf\xef\xbe\x9e", "\xe3\x82\xbe",
   "\xef\xbe\x80\xef\xbe\x9e", "\xe3\x83\x80",
   "\xef\xbe\x81\xef\xbe\x9e", "\xe3\x83\x82",
   "\xef\xbe\x82\xef\xbe\x9e", "\xe3\x83\x85",
   "\xef\xbe\x83\xef\xbe\x9e", "\xe3\x83\x87",
   "\xef\xbe\x84\xef\xbe\x9e", "\xe3\x83\x89",
   "\xef\xbe\x8a\xef\xbe\x9e", "\xe3\x83\x90",
   "\xef\xbe\x8b\xef\xbe\x9e", "\xe3\x83\x93",
   "\xef\xbe\x8c\xef\xbe\x9e", "\xe3\x83\x96",
   "\xef\xbe\x8d\xef\xbe\x9e", "\xe3\x83\x99",
   "\xef\xbe\x8e\xef\xbe\x9e", "\xe3\x83\x9c",
   "\xef\xbe\x8a\xef\xbe\x9f", "\xe3\x83\x91",
   "\xef\xbe\x8b\xef\xbe\x9f", "\xe3\x83\x94",
   "\xef\xbe\x8c\xef\xbe\x9f", "\xe3\x83\x97",
   "\xef\xbe\x8d\xef\xbe\x9f", "\xe3\x83\x9a",
   "\xef\xbe\x8e\xef\xbe\x9f", "\xe3\x83\x9d",
   "\xef\xbd\xa1", "\xe3\x80\x82",
   "\xef\xbd\xa2", "\xe3\x80\x8c",
   "\xef\xbd\xa3", "\xe3\x80\x8d",
   "\xef\xbd\xa4", "\xe3\x80\x81",
   "\xef\xbd\xa5", "\xe3\x83\xbb",
   "\xef\xbd\xa6", "\xe3\x83\xb2",
   "\xef\xbd\xa7", "\xe3\x82\xa1",
   "\xef\xbd\xa8", "\xe3\x82\xa3",
   "\xef\xbd\xa9", "\xe3\x82\xa5",
   "\xef\xbd\xaa", "\xe3\x82\xa7",
   "\xef\xbd\xab", "\xe3\x82\xa9",
   "\xef\xbd\xac", "\xe3\x83\xa3",
   "\xef\xbd\xad", "\xe3\x83\xa5",
   "\xef\xbd\xae", "\xe3\x83\xa7",
   "\xef\xbd\xaf", "\xe3\x83\x83",
   "\xef\xbd\xb0", "\xe3\x83\xbc",
   "\xef\xbd\xb1", "\xe3\x82\xa2",
   "\xef\xbd\xb2", "\xe3\x82\xa4",
   "\xef\xbd\xb3", "\xe3\x82\xa6",
   "\xef\xbd\xb4", "\xe3\x82\xa8",
   "\xef\xbd\xb5", "\xe3\x82\xaa",
   "\xef\xbd\xb6", "\xe3\x82\xab",
   "\xef\xbd\xb7", "\xe3\x82\xad",
   "\xef\xbd\xb8", "\xe3\x82\xaf",
   "\xef\xbd\xb9", "\xe3\x82\xb1",
   "\xef\xbd\xba", "\xe3\x82\xb3",
   "\xef\xbd\xbb", "\xe3\x82\xb5",
   "\xef\xbd\xbc", "\xe3\x82\xb7",
   "\xef\xbd\xbd", "\xe3\x82\xb9",
   "\xef\xbd\xbe", "\xe3\x82\xbb",
   "\xef\xbd\xbf", "\xe3\x82\xbd",
   "\xef\xbe\x80", "\xe3\x82\xbf",
   "\xef\xbe\x81", "\xe3\x83\x81",
   "\xef\xbe\x82", "\xe3\x83\x84",
   "\xef\xbe\x83", "\xe3\x83\x86",
   "\xef\xbe\x84", "\xe3\x83\x88",
   "\xef\xbe\x85", "\xe3\x83\x8a",
   "\xef\xbe\x86", "\xe3\x83\x8b",
   "\xef\xbe\x87", "\xe3\x83\x8c",
   "\xef\xbe\x88", "\xe3\x83\x8d",
   "\xef\xbe\x89", "\xe3\x83\x8e",
   "\xef\xbe\x8a", "\xe3\x83\x8f",
   "\xef\xbe\x8b", "\xe3\x83\x92",
   "\xef\xbe\x8c", "\xe3\x83\x95",
   "\xef\xbe\x8d", "\xe3\x83\x98",
   "\xef\xbe\x8e", "\xe3\x83\x9b",
   "\xef\xbe\x8f", "\xe3\x83\x9e",
   "\xef\xbe\x90", "\xe3\x83\x9f",
   "\xef\xbe\x91", "\xe3\x83\xa0",
   "\xef\xbe\x92", "\xe3\x83\xa1",
   "\xef\xbe\x93", "\xe3\x83\xa2",
   "\xef\xbe\x94", "\xe3\x83\xa4",
   "\xef\xbe\x95", "\xe3\x83\xa6",
   "\xef\xbe\x96", "\xe3\x83\xa8",
   "\xef\xbe\x97", "\xe3\x83\xa9",
   "\xef\xbe\x98", "\xe3\x83\xaa",
   "\xef\xbe\x99", "\xe3\x83\xab",
   "\xef\xbe\x9a", "\xe3\x83\xac",
   "\xef\xbe\x9b", "\xe3\x83\xad",
   "\xef\xbe\x9c", "\xe3\x83\xaf",
   "\xef\xbe\x9d", "\xe3\x83\xb3",
   "\xef\xbe\x9e", "",
   "\xef\xbe\x9f", "",
   NULL, NULL
};

TEXT2MECAB_RULE_H_END;

#endif                          /* !TEXT2MECAB_RULE_H */

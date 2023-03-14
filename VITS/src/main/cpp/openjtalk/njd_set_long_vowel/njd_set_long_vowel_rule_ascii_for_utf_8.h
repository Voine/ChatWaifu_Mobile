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

#ifndef NJD_SET_LONG_VOWEL_RULE_H
#define NJD_SET_LONG_VOWEL_RULE_H

#ifdef __cplusplus
#define NJD_SET_LONG_VOWEL_RULE_H_START extern "C" {
#define NJD_SET_LONG_VOWEL_RULE_H_END   }
#else
#define NJD_SET_LONG_VOWEL_RULE_H_START
#define NJD_SET_LONG_VOWEL_RULE_H_END
#endif                          /* __CPLUSPLUS */

NJD_SET_LONG_VOWEL_RULE_H_START;

static const char njd_set_long_vowel_kanji_range[] = {
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

static const char *njd_set_long_vowel_table[] = {
   "\xe3\x82\xa8\xe3\x82\xa4", "\xe3\x82\xa8\xe3\x83\xbc",
   "\xe3\x82\xb1\xe3\x82\xa4", "\xe3\x82\xb1\xe3\x83\xbc",
   "\xe3\x82\xbb\xe3\x82\xa4", "\xe3\x82\xbb\xe3\x83\xbc",
   "\xe3\x83\x86\xe3\x82\xa4", "\xe3\x83\x86\xe3\x83\xbc",
   "\xe3\x83\x8d\xe3\x82\xa4", "\xe3\x83\x8d\xe3\x83\xbc",
   "\xe3\x83\x98\xe3\x82\xa4", "\xe3\x83\x98\xe3\x83\xbc",
   "\xe3\x83\xa1\xe3\x82\xa4", "\xe3\x83\xa1\xe3\x83\xbc",
   "\xe3\x83\xac\xe3\x82\xa4", "\xe3\x83\xac\xe3\x83\xbc",
   "\xe3\x82\xb2\xe3\x82\xa4", "\xe3\x82\xb2\xe3\x83\xbc",
   "\xe3\x82\xbc\xe3\x82\xa4", "\xe3\x82\xbc\xe3\x83\xbc",
   "\xe3\x83\x87\xe3\x82\xa4", "\xe3\x83\x87\xe3\x83\xbc",
   "\xe3\x83\x99\xe3\x82\xa4", "\xe3\x83\x99\xe3\x83\xbc",
   "\xe3\x83\x9a\xe3\x82\xa4", "\xe3\x83\x9a\xe3\x83\xbc",
   "\xe3\x83\xb1\xe3\x82\xa4", "\xe3\x83\xb1\xe3\x83\xbc",
   NULL, NULL
};

NJD_SET_LONG_VOWEL_RULE_H_END;

#endif                          /* !NJD_SET_LONG_VOWEL_RULE_H */

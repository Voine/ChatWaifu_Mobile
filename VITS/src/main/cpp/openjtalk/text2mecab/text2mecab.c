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

#ifndef TEXT2MECAB_C
#define TEXT2MECAB_C
#define CHARSET_UTF_8 1
#define ASCII_HEADER 1

#ifdef __cplusplus
#define TEXT2MECAB_C_START extern "C" {
#define TEXT2MECAB_C_END   }
#else
#define TEXT2MECAB_C_START
#define TEXT2MECAB_C_END
#endif                          /* __CPLUSPLUS */

TEXT2MECAB_C_START;

#include <stdio.h>
#include <string.h>

#include "text2mecab.h"

#ifdef ASCII_HEADER
#if defined(CHARSET_EUC_JP)
#include "text2mecab_rule_ascii_for_euc_jp.h"
#elif defined(CHARSET_SHIFT_JIS)
#include "text2mecab_rule_ascii_for_shift_jis.h"
#elif defined(CHARSET_UTF_8)
#include "text2mecab_rule_ascii_for_utf_8.h"
#else
#error CHARSET is not specified
#endif
#else
#if defined(CHARSET_EUC_JP)
#include "text2mecab_rule_euc_jp.h"
#elif defined(CHARSET_SHIFT_JIS)
#include "text2mecab_rule_shift_jis.h"
#elif defined(CHARSET_UTF_8)
#include "text2mecab_rule_utf_8.h"
#else
#error CHARSET is not specified
#endif
#endif

static int strtopcmp(const char *str, const char *pattern)
{
   int i;

   for (i = 0;; i++) {
      if (pattern[i] == '\0')
         return i;
      if (str[i] == '\0')
         return -1;
      if (str[i] != pattern[i])
         return -1;
   }
}

void text2mecab(char* output, const char*input)
{
    int i, j;
    const int length = strlen(input);
    const char* str;
    int index = 0;
    int s, e = -1;

    for (s = 0; s < length;) {
        str = &input[s];
        /* search */
        for (i = 0; text2mecab_conv_list[i] != NULL; i += 2) {
            e = strtopcmp(str, text2mecab_conv_list[i]);
            if (e != -1)
                break;
        }
        if (e != -1) {
            /* convert */
            s += e;
            str = text2mecab_conv_list[i + 1];
            for (j = 0; str[j] != '\0'; j++)
                output[index++] = str[j];
        }
        else if (text2mecab_control_range[0] <= str[0] && str[0] <= text2mecab_control_range[1]) {
            /* control character */
            s++;
        }
        else {
            /* multi byte character */
            e = -1;
            for (j = 0; text2mecab_kanji_range[j] > 0; j += 3) {
                if (text2mecab_kanji_range[j + 1] <= str[0] && text2mecab_kanji_range[j + 2] >= str[0]) {
                    e = text2mecab_kanji_range[j];
                    break;
                }
            }
            if (e > 0) {
                for (j = 0; j < e; j++)
                    output[index++] = input[s++];
            }
            else {
                /* unknown */
                fprintf(stderr, "WARNING: openjtalk.text2mecab() in openjtalk.text2mecab.c: Wrong character.\n");
                s++;
            }
        }
    }
   output[index] = '\0';
}

TEXT2MECAB_C_END;

#endif                          /* !TEXT2MECAB_C */

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

#ifndef NJD_SET_PRONUNCIATION_C
#define NJD_SET_PRONUNCIATION_C

#ifdef __cplusplus
#define NJD_SET_PRONUNCIATION_C_START extern "C" {
#define NJD_SET_PRONUNCIATION_C_END   }
#else
#define NJD_SET_PRONUNCIATION_C_START
#define NJD_SET_PRONUNCIATION_C_END
#endif                          /* __CPLUSPLUS */

NJD_SET_PRONUNCIATION_C_START;

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "njd_set_pronunciation.h"

#ifdef ASCII_HEADER
#if defined(CHARSET_EUC_JP)
#include "njd_set_pronunciation_rule_ascii_for_euc_jp.h"
#elif defined(CHARSET_SHIFT_JIS)
#include "njd_set_pronunciation_rule_ascii_for_shift_jis.h"
#elif defined(CHARSET_UTF_8)
#include "njd_set_pronunciation_rule_ascii_for_utf_8.h"
#else
#error CHARSET is not specified
#endif
#else
#if defined(CHARSET_EUC_JP)
#include "njd_set_pronunciation_rule_euc_jp.h"
#elif defined(CHARSET_SHIFT_JIS)
#include "njd_set_pronunciation_rule_shift_jis.h"
#elif defined(CHARSET_UTF_8)
#include "njd_set_pronunciation_rule_utf_8.h"
#else
#error CHARSET is not specified
#endif
#endif

#define MAXBUFLEN 1024

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

void njd_set_pronunciation(NJD * njd)
{
   NJDNode *node;
   const char *str;
   int i, j = 0;
   int pos;
   int len;

   for (node = njd->head; node != NULL; node = node->next) {
      if (NJDNode_get_mora_size(node) == 0) {
         NJDNode_set_read(node, NULL);
         NJDNode_set_pron(node, NULL);
         /* if the word is kana, set them as filler */
         {
            str = NJDNode_get_string(node);
            len = strlen(str);
            for (pos = 0; pos < len;) {
               for (i = 0, j = 0; njd_set_pronunciation_list[i] != NULL; i += 3) {
                  j = strtopcmp(&str[pos], njd_set_pronunciation_list[i]);
                  if (j > 0)
                     break;
               }
               if (j > 0) {
                  pos += j;
                  NJDNode_add_read(node, (char *) njd_set_pronunciation_list[i + 1]);
                  NJDNode_add_pron(node, (char *) njd_set_pronunciation_list[i + 1]);
                  NJDNode_add_mora_size(node, atoi(njd_set_pronunciation_list[i + 2]));
               } else {
                  pos++;
               }
            }
            /* if filler, overwrite pos */
            if (NJDNode_get_mora_size(node) != 0) {
               NJDNode_set_pos(node, NJD_SET_PRONUNCIATION_FILLER);
               NJDNode_set_pos_group1(node, NULL);
               NJDNode_set_pos_group2(node, NULL);
               NJDNode_set_pos_group3(node, NULL);
            }
            if (strcmp(NJDNode_get_orig(node), "*") == 0) {
               NJDNode_set_orig(node, str);
            }
         }
         /* if known symbol, set the pronunciation */
         if (strcmp(NJDNode_get_pron(node), "*") == 0) {
            for (i = 0; njd_set_pronunciation_symbol_list[i] != NULL; i += 2) {
               if (strcmp(NJDNode_get_string(node), njd_set_pronunciation_symbol_list[i]) == 0) {
                  NJDNode_set_read(node, (char *) njd_set_pronunciation_symbol_list[i + 1]);
                  NJDNode_set_pron(node, (char *) njd_set_pronunciation_symbol_list[i + 1]);
                  break;
               }
            }
         }
         /* if the word is not kana, set pause symbol */
         if (strcmp(NJDNode_get_pron(node), "*") == 0) {
            NJDNode_set_read(node, NJD_SET_PRONUNCIATION_TOUTEN);
            NJDNode_set_pron(node, NJD_SET_PRONUNCIATION_TOUTEN);
            NJDNode_set_pos(node, NJD_SET_PRONUNCIATION_KIGOU);
         }
      }
   }
   NJD_remove_silent_node(njd);

   /* chain kana sequence */
   {
      NJDNode *head_of_kana_filler_sequence = NULL;
      int find;
      for (node = njd->head; node != NULL; node = node->next) {
         if (strcmp(NJDNode_get_pos(node), NJD_SET_PRONUNCIATION_FILLER) == 0) {
            find = 0;
            for (i = 0; njd_set_pronunciation_list[i] != NULL; i += 3) {
               if (strcmp(NJDNode_get_string(node), njd_set_pronunciation_list[i]) == 0) {
                  find = 1;
                  if (head_of_kana_filler_sequence == NULL) {
                     head_of_kana_filler_sequence = node;
                  } else {
                     NJDNode_add_string(head_of_kana_filler_sequence, NJDNode_get_string(node));
                     NJDNode_add_orig(head_of_kana_filler_sequence, NJDNode_get_orig(node));
                     NJDNode_add_read(head_of_kana_filler_sequence, NJDNode_get_read(node));
                     NJDNode_add_pron(head_of_kana_filler_sequence, NJDNode_get_pron(node));
                     NJDNode_add_mora_size(head_of_kana_filler_sequence,
                                           NJDNode_get_mora_size(node));
                     NJDNode_set_pron(node, NULL);
                  }
                  break;
               }
            }
            if (find == 0) {
               head_of_kana_filler_sequence = NULL;
            }
         } else {
            head_of_kana_filler_sequence = NULL;
         }
      }
   }
   NJD_remove_silent_node(njd);

   for (node = njd->head; node != NULL; node = node->next) {
      if (node->next != NULL
          && strcmp(NJDNode_get_pron(node->next), NJD_SET_PRONUNCIATION_U) == 0
          && strcmp(NJDNode_get_pos(node->next), NJD_SET_PRONUNCIATION_JODOUSHI) == 0
          && (strcmp(NJDNode_get_pos(node), NJD_SET_PRONUNCIATION_DOUSHI) == 0
              || strcmp(NJDNode_get_pos(node), NJD_SET_PRONUNCIATION_JODOUSHI) == 0)
          && NJDNode_get_mora_size(node) > 0) {
         NJDNode_set_pron(node->next, NJD_SET_PRONUNCIATION_CHOUON);
      }
      if (node->next != NULL
          && strcmp(NJDNode_get_pos(node), NJD_SET_PRONUNCIATION_JODOUSHI) == 0
          && strcmp(NJDNode_get_string(node->next), NJD_SET_PRONUNCIATION_QUESTION) == 0) {
         if (strcmp(NJDNode_get_string(node), NJD_SET_PRONUNCIATION_DESU_STR) == 0)
            NJDNode_set_pron(node, NJD_SET_PRONUNCIATION_DESU_PRON);
         else if (strcmp(NJDNode_get_string(node), NJD_SET_PRONUNCIATION_MASU_STR) == 0)
            NJDNode_set_pron(node, NJD_SET_PRONUNCIATION_MASU_PRON);
      }
   }
}

NJD_SET_PRONUNCIATION_C_END;

#endif                          /* !NJD_SET_PRONUNCIATION_C */

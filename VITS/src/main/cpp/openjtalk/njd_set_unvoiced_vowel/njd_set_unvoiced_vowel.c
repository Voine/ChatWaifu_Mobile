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

#ifndef NJD_SET_UNVOICED_VOWEL_C
#define NJD_SET_UNVOICED_VOWEL_C

#ifdef __cplusplus
#define NJD_SET_UNVOICED_VOWEL_C_START extern "C" {
#define NJD_SET_UNVOICED_VOWEL_C_END   }
#else
#define NJD_SET_UNVOICED_VOWEL_C_START
#define NJD_SET_UNVOICED_VOWEL_C_END
#endif                          /* __CPLUSPLUS */

NJD_SET_UNVOICED_VOWEL_C_START;

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "njd_set_unvoiced_vowel.h"

#ifdef ASCII_HEADER
#if defined(CHARSET_EUC_JP)
#include "njd_set_unvoiced_vowel_rule_ascii_for_euc_jp.h"
#elif defined(CHARSET_SHIFT_JIS)
#include "njd_set_unvoiced_vowel_rule_ascii_for_shift_jis.h"
#elif defined(CHARSET_UTF_8)
#include "njd_set_unvoiced_vowel_rule_ascii_for_utf_8.h"
#else
#error CHARSET is not specified
#endif
#else
#if defined(CHARSET_EUC_JP)
#include "njd_set_unvoiced_vowel_rule_euc_jp.h"
#elif defined(CHARSET_SHIFT_JIS)
#include "njd_set_unvoiced_vowel_rule_shift_jis.h"
#elif defined(CHARSET_UTF_8)
#include "njd_set_unvoiced_vowel_rule_utf_8.h"
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

static void get_mora_information(NJDNode * node, int index, const char **mora, NJDNode ** nlink,
                                 int *flag, int *size, int *midx, int *atype)
{
   int i;
   int matched_size;
   const char *str = NJDNode_get_pron(node);
   int len = strlen(str);

   /* find next word */
   if (index >= len) {
      if (node->next != NULL) {
         get_mora_information(node->next, index - len, mora, nlink, flag, size, midx, atype);
      } else {
         *mora = NULL;
         *nlink = NULL;
         *flag = -1;
         *size = 0;
         *midx = 0;
         *atype = 0;
      }
      return;
   }

   *nlink = node;

   /* reset mora index and accent type for new word */
   if (index == 0 && NJDNode_get_chain_flag(node) != 1) {
      *midx = 0;
      *atype = NJDNode_get_acc(node);
   }

   /* special symbol */
   if (strcmp(str, NJD_SET_UNVOICED_VOWEL_TOUTEN) == 0) {
      *mora = NJD_SET_UNVOICED_VOWEL_TOUTEN;
      *flag = 0;
      *size = strlen(NJD_SET_UNVOICED_VOWEL_TOUTEN);
      return;
   }
   if (strcmp(str, NJD_SET_UNVOICED_VOWEL_QUESTION) == 0) {
      *mora = NJD_SET_UNVOICED_VOWEL_QUESTION;
      *flag = 0;
      *size = strlen(NJD_SET_UNVOICED_VOWEL_QUESTION);
      return;
   }

   /* reset */
   *mora = NULL;
   *flag = -1;
   *size = 0;

   /* get mora */
   for (i = 0; njd_set_unvoiced_vowel_mora_list[i] != NULL; i++) {
      matched_size = strtopcmp(&str[index], njd_set_unvoiced_vowel_mora_list[i]);
      if (matched_size > 0) {
         *mora = njd_set_unvoiced_vowel_mora_list[i];
         *size = matched_size;
         break;
      }
   }

   /* get unvoice flag */
   matched_size = strtopcmp(&str[index + (*size)], NJD_SET_UNVOICED_VOWEL_QUOTATION);
   if (matched_size > 0) {
      *flag = 1;
      *size += matched_size;
   }
}

static int apply_unvoice_rule(const char *current, const char *next)
{
   int i, j;

   if (next == NULL)
      return 0;

   for (i = 0; njd_set_unvoiced_vowel_candidate_list1[i] != NULL; i++) {
      if (strcmp(current, njd_set_unvoiced_vowel_candidate_list1[i]) == 0) {
         for (j = 0; njd_set_unvoiced_vowel_next_mora_list1[j] != NULL; j++)
            if (strtopcmp(next, njd_set_unvoiced_vowel_next_mora_list1[j]) > 0)
               return 1;
         return 0;
      }
   }
   for (i = 0; njd_set_unvoiced_vowel_candidate_list2[i] != NULL; i++) {
      if (strcmp(current, njd_set_unvoiced_vowel_candidate_list2[i]) == 0) {
         for (j = 0; njd_set_unvoiced_vowel_next_mora_list2[j] != NULL; j++)
            if (strtopcmp(next, njd_set_unvoiced_vowel_next_mora_list2[j]) > 0)
               return 1;
         return 0;
      }
   }
   for (i = 0; njd_set_unvoiced_vowel_candidate_list3[i] != NULL; i++) {
      if (strcmp(current, njd_set_unvoiced_vowel_candidate_list3[i]) == 0) {
         for (j = 0; njd_set_unvoiced_vowel_next_mora_list3[j] != NULL; j++) {
            if (strtopcmp(next, njd_set_unvoiced_vowel_next_mora_list3[j]) > 0)
               return 1;
         }
         return 0;
      }
   }

   return -1;                   /* unknown */
}

void njd_set_unvoiced_vowel(NJD * njd)
{
   NJDNode *node;
   int index;
   int len;
   char buff[MAXBUFLEN];
   const char *str;

   /* mora information for current, next, and next-next moras */
   const char *mora1 = NULL, *mora2 = NULL, *mora3 = NULL;
   NJDNode *nlink1 = NULL, *nlink2 = NULL, *nlink3 = NULL;
   int size1 = 0, size2 = 0, size3 = 0;
   int flag1 = -1, flag2 = -1, flag3 = -1;      /* unknown:-1, voice:0, unvoiced:1 */
   int midx1 = 0, midx2 = 1, midx3 = 2;
   int atype1 = 0, atype2 = 0, atype3 = 0;

   for (node = njd->head; node != NULL; node = node->next) {
      buff[0] = '\0';

      /* get pronunciation */
      str = NJDNode_get_pron(node);
      len = strlen(str);

      /* parse pronunciation */
      for (index = 0; index < len;) {
         /* get mora information */
         if (mora1 == NULL)
            get_mora_information(node, index, &mora1, &nlink1, &flag1, &size1, &midx1, &atype1);
         if (mora1 == NULL) {
            fprintf(stderr,
                    "WARNING: set_unvoiced_vowel() in openjtalk.njd_set_unvoiced_vowel.c: Wrong pron.");
            return;
         }
         if (mora2 == NULL) {
            midx2 = midx1 + 1;
            atype2 = atype1;
            get_mora_information(node, index + size1, &mora2, &nlink2, &flag2, &size2, &midx2,
                                 &atype2);
         }
         if (mora3 == NULL) {
            midx3 = midx2 + 1;
            atype3 = atype2;
            get_mora_information(node, index + size1 + size2, &mora3, &nlink3, &flag3, &size3,
                                 &midx3, &atype3);
         }

         /* rule 1: look-ahead for 'masu' and 'desu' */
         if (mora2 != NULL && mora3 != NULL && nlink1 == nlink2 && nlink2 != nlink3 &&
             (strcmp(mora1, NJD_SET_UNVOICED_VOWEL_MA) == 0
              || strcmp(mora1, NJD_SET_UNVOICED_VOWEL_DE) == 0)
             && strcmp(mora2, NJD_SET_UNVOICED_VOWEL_SU) == 0
             && (strcmp(NJDNode_get_pos(nlink2), NJD_SET_UNVOICED_VOWEL_DOUSHI) == 0
                 || strcmp(NJDNode_get_pos(nlink2), NJD_SET_UNVOICED_VOWEL_JODOUSHI) == 0
                 || strcmp(NJDNode_get_pos(nlink2), NJD_SET_UNVOICED_VOWEL_KANDOUSHI) == 0)
             ) {
            if (strcmp(NJDNode_get_pron(nlink3), NJD_SET_UNVOICED_VOWEL_QUESTION) == 0
                || strcmp(NJDNode_get_pron(nlink3), NJD_SET_UNVOICED_VOWEL_CHOUON) == 0)
               flag2 = 0;
            else
               flag2 = 1;
         }

         /* rule 2: look-ahead for 'shi' */
         if (flag1 != 1 && flag2 == -1 && flag3 != 1 && mora2 != NULL &&
             strcmp(NJDNode_get_pron(nlink2), NJD_SET_UNVOICED_VOWEL_SHI) == 0 &&
             (strcmp(NJDNode_get_pos(nlink2), NJD_SET_UNVOICED_VOWEL_DOUSHI) == 0 ||
              strcmp(NJDNode_get_pos(nlink2), NJD_SET_UNVOICED_VOWEL_JODOUSHI) == 0 ||
              strcmp(NJDNode_get_pos(nlink2), NJD_SET_UNVOICED_VOWEL_JOSHI) == 0)) {
            if (atype2 == midx2 + 1) {
               /* rule 4 */
               flag2 = 0;
            } else {
               /* rule 5 */
               flag2 = apply_unvoice_rule(mora2, mora3);
            }
            if (flag2 == 1) {
               if (flag1 == -1)
                  flag1 = 0;
               if (flag3 == -1)
                  flag3 = 0;
            }
         }

         /* estimate unvoice */
         if (flag1 == -1) {
            if (strcmp(NJDNode_get_pos(nlink1), NJD_SET_UNVOICED_VOWEL_FILLER) == 0) {
               /* rule 0 */
               flag1 = 0;
            } else if (flag2 == 1) {
               /* rule 3 */
               flag1 = 0;
            } else if (atype1 == midx1 + 1) {
               /* rule 4 */
               flag1 = 0;
            } else {
               /* rule 5 */
               flag1 = apply_unvoice_rule(mora1, mora2);
            }
         }
         if (flag1 == 1 && flag2 == -1) {
            flag2 = 0;
         }

         /* store pronunciation */
         strcat(buff, mora1);
         if (flag1 == 1)
            strcat(buff, NJD_SET_UNVOICED_VOWEL_QUOTATION);

         /* prepare next step */
         index += size1;

         mora1 = mora2;
         nlink1 = nlink2;
         size1 = size2;
         flag1 = flag2;
         midx1 = midx2;
         atype1 = atype2;

         mora2 = mora3;
         nlink2 = nlink3;
         size2 = size3;
         flag2 = flag3;
         midx2 = midx3;
         atype2 = atype3;

         mora3 = NULL;
         nlink3 = NULL;
         size3 = 0;
         flag3 = -1;
         midx3 = 0;
         atype3 = 0;
      }

      NJDNode_set_pron(node, buff);
   }
}

NJD_SET_UNVOICED_VOWEL_C_END;

#endif                          /* !NJD_SET_UNVOICED_VOWEL_C */

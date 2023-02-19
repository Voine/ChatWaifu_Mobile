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

#ifndef JPCOMMON_LABEL_C
#define JPCOMMON_LABEL_C

#ifdef __cplusplus
#define JPCOMMON_LABEL_C_START extern "C" {
#define JPCOMMON_LABEL_C_END   }
#else
#define JPCOMMON_LABEL_C_START
#define JPCOMMON_LABEL_C_END
#endif                          /* __CPLUSPLUS */

JPCOMMON_LABEL_C_START;

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jpcommon.h"

#ifdef ASCII_HEADER
#if defined(CHARSET_EUC_JP)
#include "jpcommon_rule_ascii_for_euc_jp.h"
#elif defined(CHARSET_SHIFT_JIS)
#include "jpcommon_rule_ascii_for_shift_jis.h"
#elif defined(CHARSET_UTF_8)
#include "jpcommon_rule_ascii_for_utf_8.h"
#else
#error CHARSET is not specified
#endif
#else
#if defined(CHARSET_EUC_JP)
#include "jpcommon_rule_euc_jp.h"
#elif defined(CHARSET_SHIFT_JIS)
#include "jpcommon_rule_shift_jis.h"
#elif defined(CHARSET_UTF_8)
#include "jpcommon_rule_utf_8.h"
#else
#error CHARSET is not specified
#endif
#endif

#define MAXBUFLEN 1024
#define MAX_S     19
#define MAX_M     49
#define MAX_L     99
#define MAX_LL    199

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

static int limit(int in, int min, int max)
{
   if (in <= min)
      return min;
   if (in >= max)
      return max;
   return in;
}

static void JPCommonLabelPhoneme_initialize(JPCommonLabelPhoneme * p, const char *phoneme,
                                            JPCommonLabelPhoneme * prev,
                                            JPCommonLabelPhoneme * next, JPCommonLabelMora * up)
{
   p->phoneme = strdup(phoneme);
   p->prev = prev;
   p->next = next;
   p->up = up;
}

static void JPCommonLabelPhoneme_convert_unvoice(JPCommonLabelPhoneme * p)
{
   int i;

   for (i = 0; jpcommon_unvoice_list[i] != NULL; i += 2) {
      if (strcmp(jpcommon_unvoice_list[i], p->phoneme) == 0) {
         free(p->phoneme);
         p->phoneme = strdup(jpcommon_unvoice_list[i + 1]);
         return;
      }
   }

   fprintf(stderr,
           "WARNING: JPCommonLabelPhoneme_convert_unvoice() in jpcommon_label.c: %s cannot be unvoiced.\n",
           p->phoneme);
}

static void JPCommonLabelPhoneme_clear(JPCommonLabelPhoneme * p)
{
   free(p->phoneme);
}

static void JPCommonLabelMora_initialize(JPCommonLabelMora * m, const char *mora,
                                         JPCommonLabelPhoneme * head, JPCommonLabelPhoneme * tail,
                                         JPCommonLabelMora * prev, JPCommonLabelMora * next,
                                         JPCommonLabelWord * up)
{
   m->mora = strdup(mora);
   m->head = head;
   m->tail = tail;
   m->prev = prev;
   m->next = next;
   m->up = up;
}

static void JPCommonLabelMora_clear(JPCommonLabelMora * m)
{
   free(m->mora);
}

static void JPCommonLabelWord_initialize(JPCommonLabelWord * w, const char *pron, const char *pos,
                                         const char *ctype, const char *cform,
                                         JPCommonLabelMora * head, JPCommonLabelMora * tail,
                                         JPCommonLabelWord * prev, JPCommonLabelWord * next)
{
   int i, find;

   w->pron = strdup(pron);
   for (i = 0, find = 0; jpcommon_pos_list[i] != NULL; i += 2) {
      if (strcmp(jpcommon_pos_list[i], pos) == 0) {
         find = 1;
         break;
      }
   }
   if (find == 0) {
      fprintf(stderr,
              "WARNING: JPCommonLabelWord_initialize() in jpcommon_label.c: %s is unknown POS.\n",
              pos);
      i = 0;
   }
   w->pos = strdup(jpcommon_pos_list[i + 1]);
   for (i = 0, find = 0; jpcommon_ctype_list[i] != NULL; i += 2) {
      if (strcmp(jpcommon_ctype_list[i], ctype) == 0) {
         find = 1;
         break;
      }
   }
   if (find == 0) {
      fprintf(stderr,
              "WARNING: JPCommonLabelWord_initialize() in jpcommon_label.c: %s is unknown conjugation type.\n",
              ctype);
      i = 0;
   }
   w->ctype = strdup(jpcommon_ctype_list[i + 1]);
   for (i = 0, find = 0; jpcommon_cform_list[i] != NULL; i += 2) {
      if (strcmp(jpcommon_cform_list[i], cform) == 0) {
         find = 1;
         break;
      }
   }
   if (find == 0) {
      fprintf(stderr,
              "WARNING: JPCommonLabelWord_initialize() in jpcommon_label.c: %s is unknown conjugation form .\n",
              cform);
      i = 0;
   }
   w->cform = strdup(jpcommon_cform_list[i + 1]);
   w->head = head;
   w->tail = tail;
   w->prev = prev;
   w->next = next;
}

static void JPCommonLabelWord_clear(JPCommonLabelWord * w)
{
   free(w->pron);
   free(w->pos);
   free(w->ctype);
   free(w->cform);
}

static void JPCommonLabelAccentPhrase_initialize(JPCommonLabelAccentPhrase * a, int acc,
                                                 const char *emotion, JPCommonLabelWord * head,
                                                 JPCommonLabelWord * tail,
                                                 JPCommonLabelAccentPhrase * prev,
                                                 JPCommonLabelAccentPhrase * next,
                                                 JPCommonLabelBreathGroup * up)
{
   a->accent = acc;
   if (emotion != NULL)
      a->emotion = strdup(emotion);
   else
      a->emotion = NULL;
   a->head = head;
   a->tail = tail;
   a->prev = prev;
   a->next = next;
   a->up = up;
}

static void JPCommonLabelAccentPhrase_clear(JPCommonLabelAccentPhrase * a)
{
   if (a->emotion != NULL)
      free(a->emotion);
}

static void JPCommonLabelBreathGroup_initialize(JPCommonLabelBreathGroup * b,
                                                JPCommonLabelAccentPhrase * head,
                                                JPCommonLabelAccentPhrase * tail,
                                                JPCommonLabelBreathGroup * prev,
                                                JPCommonLabelBreathGroup * next)
{
   b->head = head;
   b->tail = tail;
   b->prev = prev;
   b->next = next;
}

static void JPCommonLabelBreathGroup_clear(JPCommonLabelBreathGroup * b)
{
}

static int index_mora_in_accent_phrase(JPCommonLabelMora * m)
{
   int i;
   JPCommonLabelMora *index;

   for (i = 0, index = m->up->up->head->head; index != NULL; index = index->next) {
      i++;
      if (index == m)
         break;
   }
   return i;
}

static int count_mora_in_accent_phrase(JPCommonLabelMora * m)
{
   int i;
   JPCommonLabelMora *index;

   for (i = 0, index = m->up->up->head->head; index != NULL; index = index->next) {
      i++;
      if (index == m->up->up->tail->tail)
         break;
   }
   return i;
}

static int index_accent_phrase_in_breath_group(JPCommonLabelAccentPhrase * a)
{
   int i;
   JPCommonLabelAccentPhrase *index;

   for (i = 0, index = a->up->head; index != NULL; index = index->next) {
      i++;
      if (index == a)
         break;
   }
   return i;
}

static int count_accent_phrase_in_breath_group(JPCommonLabelAccentPhrase * a)
{
   int i;
   JPCommonLabelAccentPhrase *index;

   for (i = 0, index = a->up->head; index != NULL; index = index->next) {
      i++;
      if (index == a->up->tail)
         break;
   }
   return i;
}

static int index_mora_in_breath_group(JPCommonLabelMora * m)
{
   int i;
   JPCommonLabelMora *index;

   for (i = 0, index = m->up->up->up->head->head->head; index != NULL; index = index->next) {
      i++;
      if (index == m)
         break;
   }
   return i;
}

static int count_mora_in_breath_group(JPCommonLabelMora * m)
{
   int i;
   JPCommonLabelMora *index;

   for (i = 0, index = m->up->up->up->head->head->head; index != NULL; index = index->next) {
      i++;
      if (index == m->up->up->up->tail->tail->tail)
         break;
   }
   return i;
}

static int index_breath_group_in_utterance(JPCommonLabelBreathGroup * b)
{
   int i;
   JPCommonLabelBreathGroup *index;

   for (i = 0, index = b; index != NULL; index = index->prev)
      i++;
   return i;
}

static int count_breath_group_in_utterance(JPCommonLabelBreathGroup * b)
{
   int i;
   JPCommonLabelBreathGroup *index;

   for (i = 0, index = b->next; index != NULL; index = index->next)
      i++;
   return index_breath_group_in_utterance(b) + i;
}

static int index_accent_phrase_in_utterance(JPCommonLabelAccentPhrase * a)
{
   int i;
   JPCommonLabelAccentPhrase *index;

   for (i = 0, index = a; index != NULL; index = index->prev)
      i++;
   return i;
}

static int count_accent_phrase_in_utterance(JPCommonLabelAccentPhrase * a)
{
   int i;
   JPCommonLabelAccentPhrase *index;

   for (i = 0, index = a->next; index != NULL; index = index->next)
      i++;
   return index_accent_phrase_in_utterance(a) + i;
}

static int index_mora_in_utterance(JPCommonLabelMora * m)
{
   int i;
   JPCommonLabelMora *index;

   for (i = 0, index = m; index != NULL; index = index->prev)
      i++;
   return i;
}

static int count_mora_in_utterance(JPCommonLabelMora * m)
{
   int i;
   JPCommonLabelMora *index;

   for (i = 0, index = m->next; index != NULL; index = index->next)
      i++;
   return index_mora_in_utterance(m) + i;
}

void JPCommonLabel_initialize(JPCommonLabel * label)
{
   label->short_pause_flag = 0;
   label->breath_head = NULL;
   label->breath_tail = NULL;
   label->accent_head = NULL;
   label->accent_tail = NULL;
   label->word_head = NULL;
   label->word_tail = NULL;
   label->mora_head = NULL;
   label->mora_tail = NULL;
   label->phoneme_head = NULL;
   label->phoneme_tail = NULL;

   label->size = 0;
   label->feature = NULL;
}

static void JPCommonLabel_insert_pause(JPCommonLabel * label)
{
   /* insert short pause */
   if (label->short_pause_flag == 1) {
      if (label->phoneme_tail != NULL) {
         if (strcmp(label->phoneme_tail->phoneme, JPCOMMON_PHONEME_SHORT_PAUSE) == 0) {
            fprintf(stderr,
                    "WARNING: JPCommonLabel_insert_pause() in jpcommon_label.c: Short pause should not be chained.\n");
            return;
         }
         label->phoneme_tail->next =
             (JPCommonLabelPhoneme *) calloc(1, sizeof(JPCommonLabelPhoneme));
         JPCommonLabelPhoneme_initialize(label->phoneme_tail->next, JPCOMMON_PHONEME_SHORT_PAUSE,
                                         label->phoneme_tail, NULL, NULL);
         label->phoneme_tail = label->phoneme_tail->next;
      } else {
         fprintf(stderr,
                 "WARNING: JPCommonLabel_insert_pause() in jpcommon_label.c: First mora should not be short pause.\n");
      }
      label->short_pause_flag = 0;
   }
}

void JPCommonLabel_push_word(JPCommonLabel * label, const char *pron, const char *pos,
                             const char *ctype, const char *cform, int acc, int chain_flag)
{
   int i;
   int find;
   int is_first_word = 1;

   if (strcmp(pron, JPCOMMON_MORA_SHORT_PAUSE) == 0) {
      label->short_pause_flag = 1;
      return;
   }

   /* set emotion flag */
   if (strcmp(pron, JPCOMMON_MORA_QUESTION) == 0) {
      if (label->phoneme_tail != NULL) {
         if (strcmp(label->phoneme_tail->phoneme, JPCOMMON_PHONEME_SHORT_PAUSE) == 0) {
            if (label->phoneme_tail->prev->up->up->up->emotion == NULL)
               label->phoneme_tail->prev->up->up->up->emotion = strdup(JPCOMMON_FLAG_QUESTION);
         } else {
            if (label->phoneme_tail->up->up->up->emotion == NULL)
               label->phoneme_tail->up->up->up->emotion = strdup(JPCOMMON_FLAG_QUESTION);
         }
      } else {
         fprintf(stderr,
                 "WARNING: JPCommonLabel_push_word() in jpcommon_label.c: First mora should not be question flag.\n");
      }
      label->short_pause_flag = 1;
      return;
   }

   /* analysis pron */
   while (pron[0] != '\0') {
      find = strtopcmp(pron, JPCOMMON_MORA_LONG_VOWEL);
      if (find != -1) {
         /* for long vowel */
         if (label->phoneme_tail != NULL && label->short_pause_flag == 0) {
            JPCommonLabel_insert_pause(label);
            label->phoneme_tail->next =
                (JPCommonLabelPhoneme *) calloc(1, sizeof(JPCommonLabelPhoneme));
            label->mora_tail->next = (JPCommonLabelMora *) calloc(1, sizeof(JPCommonLabelMora));
            JPCommonLabelPhoneme_initialize(label->phoneme_tail->next, label->phoneme_tail->phoneme,
                                            label->phoneme_tail, NULL, label->mora_tail->next);
            JPCommonLabelMora_initialize(label->mora_tail->next, JPCOMMON_MORA_LONG_VOWEL,
                                         label->phoneme_tail->next, label->phoneme_tail->next,
                                         label->mora_tail, NULL, label->mora_tail->up);
            label->phoneme_tail = label->phoneme_tail->next;
            label->mora_tail = label->mora_tail->next;
            label->word_tail->tail = label->mora_tail;
         } else {
            fprintf(stderr,
                    "WARNING: JPCommonLabel_push_word() in jpcommon_label.c: First mora should not be long vowel symbol.\n");
         }
         pron += find;
      } else {
         find = strtopcmp(pron, JPCOMMON_MORA_UNVOICE);
         if (find != -1) {
            /* for unvoice */
            if (label->phoneme_tail != NULL && is_first_word != 1)
               JPCommonLabelPhoneme_convert_unvoice(label->phoneme_tail);
            else
               fprintf(stderr,
                       "WARNING: JPCommonLabel_push_word() in jpcommon_label.c: First mora should not be unvoice flag.\n");
            pron += find;
         } else {
            /* for normal word */
            for (i = 0; jpcommon_mora_list[i] != NULL; i += 3) {
               find = strtopcmp(pron, jpcommon_mora_list[i]);
               if (find != -1)
                  break;
            }
            if (find != -1) {
               if (label->phoneme_tail == NULL) {
                  JPCommonLabel_insert_pause(label);
                  label->phoneme_tail =
                      (JPCommonLabelPhoneme *) calloc(1, sizeof(JPCommonLabelPhoneme));
                  label->mora_tail = (JPCommonLabelMora *) calloc(1, sizeof(JPCommonLabelMora));
                  label->word_tail = (JPCommonLabelWord *) calloc(1, sizeof(JPCommonLabelWord));
                  JPCommonLabelPhoneme_initialize(label->phoneme_tail, jpcommon_mora_list[i + 1],
                                                  NULL, NULL, label->mora_tail);
                  JPCommonLabelMora_initialize(label->mora_tail, jpcommon_mora_list[i],
                                               label->phoneme_tail, label->phoneme_tail, NULL, NULL,
                                               label->word_tail);
                  JPCommonLabelWord_initialize(label->word_tail, pron, pos, ctype, cform,
                                               label->mora_tail, label->mora_tail, NULL, NULL);
                  label->phoneme_head = label->phoneme_tail;
                  label->mora_head = label->mora_tail;
                  label->word_head = label->word_tail;
                  is_first_word = 0;
               } else {
                  if (is_first_word == 1) {
                     JPCommonLabel_insert_pause(label);
                     label->phoneme_tail->next =
                         (JPCommonLabelPhoneme *) calloc(1, sizeof(JPCommonLabelPhoneme));
                     label->mora_tail->next =
                         (JPCommonLabelMora *) calloc(1, sizeof(JPCommonLabelMora));
                     label->word_tail->next =
                         (JPCommonLabelWord *) calloc(1, sizeof(JPCommonLabelWord));
                     JPCommonLabelPhoneme_initialize(label->phoneme_tail->next,
                                                     jpcommon_mora_list[i + 1], label->phoneme_tail,
                                                     NULL, label->mora_tail->next);
                     JPCommonLabelMora_initialize(label->mora_tail->next, jpcommon_mora_list[i],
                                                  label->phoneme_tail->next,
                                                  label->phoneme_tail->next, label->mora_tail, NULL,
                                                  label->word_tail->next);
                     JPCommonLabelWord_initialize(label->word_tail->next, pron, pos, ctype, cform,
                                                  label->mora_tail->next, label->mora_tail->next,
                                                  label->word_tail, NULL);
                     label->phoneme_tail = label->phoneme_tail->next;
                     label->mora_tail = label->mora_tail->next;
                     label->word_tail = label->word_tail->next;
                     is_first_word = 0;
                  } else {
                     JPCommonLabel_insert_pause(label);
                     label->phoneme_tail->next =
                         (JPCommonLabelPhoneme *) calloc(1, sizeof(JPCommonLabelPhoneme));
                     label->mora_tail->next =
                         (JPCommonLabelMora *) calloc(1, sizeof(JPCommonLabelMora));
                     JPCommonLabelPhoneme_initialize(label->phoneme_tail->next,
                                                     jpcommon_mora_list[i + 1], label->phoneme_tail,
                                                     NULL, label->mora_tail->next);
                     JPCommonLabelMora_initialize(label->mora_tail->next, jpcommon_mora_list[i],
                                                  label->phoneme_tail->next,
                                                  label->phoneme_tail->next, label->mora_tail, NULL,
                                                  label->mora_tail->up);
                     label->phoneme_tail = label->phoneme_tail->next;
                     label->mora_tail = label->mora_tail->next;
                     label->word_tail->tail = label->mora_tail;
                  }
               }
               if (jpcommon_mora_list[i + 2] != NULL) {
                  JPCommonLabel_insert_pause(label);
                  label->phoneme_tail->next =
                      (JPCommonLabelPhoneme *) calloc(1, sizeof(JPCommonLabelPhoneme));
                  JPCommonLabelPhoneme_initialize(label->phoneme_tail->next,
                                                  jpcommon_mora_list[i + 2], label->phoneme_tail,
                                                  NULL, label->mora_tail);
                  label->phoneme_tail = label->phoneme_tail->next;
                  label->mora_tail->tail = label->phoneme_tail;
               }
               pron += find;
            } else {
               fprintf(stderr,
                       "WARNING: JPCommonLabel_push_word() in jpcommon_label.c: %s is wrong mora list.\n",
                       pron);
               break;
            }
         }
      }
   }

   /* check */
   if (is_first_word == 1)
      return;
   if (label->phoneme_tail == NULL)
      return;
   if (strcmp(label->phoneme_tail->phoneme, JPCOMMON_PHONEME_SHORT_PAUSE) == 0)
      return;

   /* make accent, phrase */
   if (label->word_head == label->word_tail) {
      /* first word */
      label->accent_tail =
          (JPCommonLabelAccentPhrase *) calloc(1, sizeof(JPCommonLabelAccentPhrase));

      label->breath_tail = (JPCommonLabelBreathGroup *) calloc(1, sizeof(JPCommonLabelBreathGroup));
      label->word_tail->up = label->accent_tail;
      JPCommonLabelAccentPhrase_initialize(label->accent_tail, acc, NULL, label->word_tail,
                                           label->word_tail, NULL, NULL, label->breath_tail);
      JPCommonLabelBreathGroup_initialize(label->breath_tail, label->accent_tail,
                                          label->accent_tail, NULL, NULL);
      label->accent_head = label->accent_tail;
      label->breath_head = label->breath_tail;
   } else if (chain_flag == 1) {
      /* common accent phrase and common phrase */
      label->word_tail->up = label->accent_tail;
      label->accent_tail->tail = label->word_tail;
   } else
       if (strcmp(label->word_tail->prev->tail->tail->next->phoneme, JPCOMMON_PHONEME_SHORT_PAUSE)
           != 0) {
      /* different accent phrase && common phrase */
      label->accent_tail->next =
          (JPCommonLabelAccentPhrase *) calloc(1, sizeof(JPCommonLabelAccentPhrase));
      label->word_tail->up = label->accent_tail->next;
      JPCommonLabelAccentPhrase_initialize(label->accent_tail->next, acc, NULL, label->word_tail,
                                           label->word_tail, label->accent_tail, NULL,
                                           label->breath_tail);
      label->breath_tail->tail = label->accent_tail->next;
      label->accent_tail = label->accent_tail->next;
   } else {
      /* different accent phrase && different phrase */
      label->accent_tail->next =
          (JPCommonLabelAccentPhrase *) calloc(1, sizeof(JPCommonLabelAccentPhrase));
      label->breath_tail->next =
          (JPCommonLabelBreathGroup *) calloc(1, sizeof(JPCommonLabelBreathGroup));
      label->word_tail->up = label->accent_tail->next;
      JPCommonLabelAccentPhrase_initialize(label->accent_tail->next, acc, NULL, label->word_tail,
                                           label->word_tail, label->accent_tail, NULL,
                                           label->breath_tail->next);
      JPCommonLabelBreathGroup_initialize(label->breath_tail->next, label->accent_tail->next,
                                          label->accent_tail->next, label->breath_tail, NULL);
      label->accent_tail = label->accent_tail->next;
      label->breath_tail = label->breath_tail->next;
   }
}

void JPCommonLabel_make(JPCommonLabel * label)
{
   int i, tmp1, tmp2, tmp3;
   char buff[MAXBUFLEN];
   JPCommonLabelPhoneme *p;
   JPCommonLabelWord *w;
   JPCommonLabelAccentPhrase *a;
   JPCommonLabelBreathGroup *b;
   char **phoneme_list;
   int short_pause_flag;

   /* initialize */
   for (p = label->phoneme_head, label->size = 0; p != NULL; p = p->next)
      label->size++;
   if (label->size < 1) {
      fprintf(stderr, "WARNING: JPCommonLabel_make() in jcomon_label.c: No phoneme.\n");
      return;
   }
   label->size += 2;
   label->feature = (char **) calloc(label->size, sizeof(char *));
   for (i = 0; i < label->size; i++)
      label->feature[i] = (char *) calloc(MAXBUFLEN, sizeof(char));

   /* phoneme list */
   phoneme_list = (char **) calloc(label->size + 4, sizeof(char *));
   phoneme_list[0] = JPCOMMON_PHONEME_UNKNOWN;
   phoneme_list[1] = JPCOMMON_PHONEME_UNKNOWN;
   phoneme_list[2] = JPCOMMON_PHONEME_SILENT;
   phoneme_list[label->size + 1] = JPCOMMON_PHONEME_SILENT;
   phoneme_list[label->size + 2] = JPCOMMON_PHONEME_UNKNOWN;
   phoneme_list[label->size + 3] = JPCOMMON_PHONEME_UNKNOWN;
   for (i = 3, p = label->phoneme_head; p != NULL; p = p->next)
      phoneme_list[i++] = p->phoneme;

   for (i = 0, p = label->phoneme_head; i < label->size; i++) {
      if (strcmp(p->phoneme, JPCOMMON_PHONEME_SHORT_PAUSE) == 0)
         short_pause_flag = 1;
      else
         short_pause_flag = 0;

      /* for phoneme */
      sprintf(label->feature[i], "%s^%s-%s+%s=%s", phoneme_list[i], phoneme_list[i + 1],
              phoneme_list[i + 2], phoneme_list[i + 3], phoneme_list[i + 4]);
      /* for A: */
      if (i == 0 || i == label->size - 1 || short_pause_flag == 1)
         sprintf(buff, "/A:xx+xx+xx");
      else {
         tmp1 = index_mora_in_accent_phrase(p->up);
         tmp2 =
             p->up->up->up->accent ==
             0 ? count_mora_in_accent_phrase(p->up) : p->up->up->up->accent;
         sprintf(buff, "/A:%d+%d+%d", limit(tmp1 - tmp2, -MAX_M, MAX_M), limit(tmp1, 1, MAX_M),
                 limit(count_mora_in_accent_phrase(p->up) - tmp1 + 1, 1, MAX_M));
      }
      strcat(label->feature[i], buff);
      /* for B: */
      if (short_pause_flag == 1)
         w = p->prev->up->up;
      else if (p->up->up->prev == NULL)
         w = NULL;
      else if (i == label->size - 1)
         w = p->up->up;
      else
         w = p->up->up->prev;
      if (w == NULL)
         sprintf(buff, "/B:xx-xx_xx");
      else
         sprintf(buff, "/B:%s-%s_%s", w->pos, w->ctype, w->cform);
      strcat(label->feature[i], buff);
      /* for C: */
      if (i == 0 || i == label->size - 1 || short_pause_flag)
         sprintf(buff, "/C:xx_xx+xx");
      else
         sprintf(buff, "/C:%s_%s+%s", p->up->up->pos, p->up->up->ctype, p->up->up->cform);
      strcat(label->feature[i], buff);
      /* for D: */
      if (short_pause_flag == 1)
         w = p->next->up->up;
      else if (p->up->up->next == NULL)
         w = NULL;
      else if (i == 0)
         w = p->up->up;
      else
         w = p->up->up->next;
      if (w == NULL)
         sprintf(buff, "/D:xx+xx_xx");
      else
         sprintf(buff, "/D:%s+%s_%s", w->pos, w->ctype, w->cform);
      strcat(label->feature[i], buff);
      /* for E: */
      if (short_pause_flag == 1)
         a = p->prev->up->up->up;
      else if (i == label->size - 1)
         a = p->up->up->up;
      else
         a = p->up->up->up->prev;
      if (a == NULL)
         sprintf(buff, "/E:xx_xx!xx_xx");
      else
         sprintf(buff, "/E:%d_%d!%s_xx",
                 limit(count_mora_in_accent_phrase(a->head->head), 1, MAX_M),
                 limit(a->accent == 0 ? count_mora_in_accent_phrase(a->head->head) : a->accent, 1,
                       MAX_M), a->emotion == NULL ? "0" : a->emotion);
      strcat(label->feature[i], buff);
      if (i == 0 || i == label->size - 1 || short_pause_flag == 1 || a == NULL)
         sprintf(buff, "-xx");
      else
         sprintf(buff, "-%d",
                 strcmp(a->tail->tail->tail->next->phoneme,
                        JPCOMMON_PHONEME_SHORT_PAUSE) == 0 ? 0 : 1);
      strcat(label->feature[i], buff);
      /* for F: */
      if (i == 0 || i == label->size - 1 || short_pause_flag == 1)
         a = NULL;
      else
         a = p->up->up->up;
      if (a == NULL)
         sprintf(buff, "/F:xx_xx#xx_xx@xx_xx|xx_xx");
      else {
         tmp1 = index_accent_phrase_in_breath_group(a);
         tmp2 = index_mora_in_breath_group(a->head->head);
         sprintf(buff, "/F:%d_%d#%s_xx@%d_%d|%d_%d",
                 limit(count_mora_in_accent_phrase(a->head->head), 1, MAX_M),
                 limit(a->accent == 0 ? count_mora_in_accent_phrase(a->head->head) : a->accent, 1,
                       MAX_M), a->emotion == NULL ? "0" : a->emotion, limit(tmp1, 1, MAX_M),
                 limit(count_accent_phrase_in_breath_group(a) - tmp1 + 1, 1, MAX_M), limit(tmp2, 1,
                                                                                           MAX_L),
                 limit(count_mora_in_breath_group(a->head->head) - tmp2 + 1, 1, MAX_L));
      }
      strcat(label->feature[i], buff);
      /* for G: */
      if (short_pause_flag == 1)
         a = p->next->up->up->up;
      else if (i == 0)
         a = p->up->up->up;
      else
         a = p->up->up->up->next;
      if (a == NULL)
         sprintf(buff, "/G:xx_xx%%xx_xx");
      else
         sprintf(buff, "/G:%d_%d%%%s_xx",
                 limit(count_mora_in_accent_phrase(a->head->head), 1, MAX_M),
                 limit(a->accent == 0 ? count_mora_in_accent_phrase(a->head->head) : a->accent, 1,
                       MAX_M), a->emotion == NULL ? "0" : a->emotion);
      strcat(label->feature[i], buff);
      if (i == 0 || i == label->size - 1 || short_pause_flag == 1 || a == NULL)
         sprintf(buff, "_xx");
      else
         sprintf(buff, "_%d",
                 strcmp(a->head->head->head->prev->phoneme,
                        JPCOMMON_PHONEME_SHORT_PAUSE) == 0 ? 0 : 1);
      strcat(label->feature[i], buff);
      /* for H: */
      if (short_pause_flag == 1)
         b = p->prev->up->up->up->up;
      else if (i == label->size - 1)
         b = p->up->up->up->up;
      else
         b = p->up->up->up->up->prev;
      if (b == NULL)
         sprintf(buff, "/H:xx_xx");
      else
         sprintf(buff, "/H:%d_%d", limit(count_accent_phrase_in_breath_group(b->head), 1, MAX_M),
                 limit(count_mora_in_breath_group(b->head->head->head), 1, MAX_L));
      strcat(label->feature[i], buff);
      /* for I: */
      if (i == 0 || i == label->size - 1 || short_pause_flag == 1)
         b = NULL;
      else
         b = p->up->up->up->up;
      if (b == NULL)
         sprintf(buff, "/I:xx-xx@xx+xx&xx-xx|xx+xx");
      else {
         tmp1 = index_breath_group_in_utterance(b);
         tmp2 = index_accent_phrase_in_utterance(b->head);
         tmp3 = index_mora_in_utterance(b->head->head->head);
         sprintf(buff, "/I:%d-%d@%d+%d&%d-%d|%d+%d",
                 limit(count_accent_phrase_in_breath_group(b->head), 1, MAX_M),
                 limit(count_mora_in_breath_group(b->head->head->head), 1, MAX_L), limit(tmp1, 1,
                                                                                         MAX_S),
                 limit(count_breath_group_in_utterance(b) - tmp1 + 1, 1, MAX_S), limit(tmp2, 1,
                                                                                       MAX_M),
                 limit(count_accent_phrase_in_utterance(b->head) - tmp2 + 1, 1, MAX_M), limit(tmp3,
                                                                                              1,
                                                                                              MAX_LL),
                 limit(count_mora_in_utterance(b->head->head->head) - tmp3 + 1, 1, MAX_LL));
      }
      strcat(label->feature[i], buff);
      /* for J: */
      if (short_pause_flag == 1)
         b = p->next->up->up->up->up;
      else if (i == 0)
         b = p->up->up->up->up;
      else
         b = p->up->up->up->up->next;
      if (b == NULL)
         sprintf(buff, "/J:xx_xx");
      else
         sprintf(buff, "/J:%d_%d", limit(count_accent_phrase_in_breath_group(b->head), 1, MAX_M),
                 limit(count_mora_in_breath_group(b->head->head->head), 1, MAX_L));
      strcat(label->feature[i], buff);
      /* for K: */
      sprintf(buff, "/K:%d+%d-%d",
              limit(count_breath_group_in_utterance(label->breath_head), 1, MAX_S),
              limit(count_accent_phrase_in_utterance(label->accent_head), 1, MAX_M),
              limit(count_mora_in_utterance(label->mora_head), 1, MAX_LL));
      strcat(label->feature[i], buff);

      if (0 < i && i < label->size - 2)
         p = p->next;
   }

   /* free */
   free(phoneme_list);
}

int JPCommonLabel_get_size(JPCommonLabel * label)
{
   return label->size;
}

char **JPCommonLabel_get_feature(JPCommonLabel * label)
{
   return label->feature;
}

void JPCommonLabel_print(JPCommonLabel * label)
{
   JPCommonLabel_fprint(label, stdout);
}

void JPCommonLabel_fprint(JPCommonLabel * label, FILE * fp)
{
   JPCommonLabelPhoneme *p;
   JPCommonLabelMora *m;
   JPCommonLabelWord *w;
   JPCommonLabelAccentPhrase *a;
   JPCommonLabelBreathGroup *b;
   int i = 0;
   int j = 0;

   for (b = label->breath_head; b != NULL; b = b->next) {
      fprintf(fp, "%d\n", j++);
      for (a = b->head; a != NULL; a = a->next) {
         fprintf(fp, "   %d\n", i++);
         for (w = a->head; w != NULL; w = w->next) {
            fprintf(fp, "      %s %s %s %s\n", w->pron, w->pos, w->ctype, w->cform);
            for (m = w->head; m != NULL; m = m->next) {
               fprintf(fp, "         %s\n", m->mora);
               for (p = m->head; p != NULL; p = p->next) {
                  fprintf(fp, "            %s\n", p->phoneme);
                  if (p == m->tail)
                     break;
               }
               if (m == w->tail)
                  break;
            }
            if (w == a->tail)
               break;
         }
         if (a == b->tail)
            break;
      }
   }
}

void JPCommonLabel_clear(JPCommonLabel * label)
{
   int i;
   JPCommonLabelPhoneme *p, *pn;
   JPCommonLabelMora *m, *mn;
   JPCommonLabelWord *w, *wn;
   JPCommonLabelAccentPhrase *a, *an;
   JPCommonLabelBreathGroup *b, *bn;

   for (p = label->phoneme_head; p != NULL; p = pn) {
      pn = p->next;
      JPCommonLabelPhoneme_clear(p);
      free(p);
   }
   for (m = label->mora_head; m != NULL; m = mn) {
      mn = m->next;
      JPCommonLabelMora_clear(m);
      free(m);
   }
   for (w = label->word_head; w != NULL; w = wn) {
      wn = w->next;
      JPCommonLabelWord_clear(w);
      free(w);
   }
   for (a = label->accent_head; a != NULL; a = an) {
      an = a->next;
      JPCommonLabelAccentPhrase_clear(a);
      free(a);
   }
   for (b = label->breath_head; b != NULL; b = bn) {
      bn = b->next;
      JPCommonLabelBreathGroup_clear(b);
      free(b);
   }
   if (label->feature != NULL) {
      for (i = 0; i < label->size; i++)
         free(label->feature[i]);
      free(label->feature);
   }
}

JPCOMMON_LABEL_C_END;

#endif                          /* !JPCOMMON_LABEL_C */

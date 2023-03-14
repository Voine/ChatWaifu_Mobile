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

#ifndef JPCOMMON_H
#define JPCOMMON_H

#ifdef __cplusplus
#define JPCOMMON_H_START extern "C" {
#define JPCOMMON_H_END   }
#else
#define JPCOMMON_H_START
#define JPCOMMON_H_END
#endif                          /* __CPLUSPLUS */

#define ASCII_HEADER 1
#define CHARSET_UTF_8 1

JPCOMMON_H_START;

/* JPCommonLabel */

struct _JPCommonLabelPhoneme;
struct _JPCommonLabelMora;
struct _JPCommonLabelWord;
struct _JPCommonLabelAccentPhrase;
struct _JPCommonLabelBreathGroup;

typedef struct _JPCommonLabelPhoneme {
   char *phoneme;
   struct _JPCommonLabelPhoneme *prev;
   struct _JPCommonLabelPhoneme *next;
   struct _JPCommonLabelMora *up;
} JPCommonLabelPhoneme;

typedef struct _JPCommonLabelMora {
   char *mora;
   struct _JPCommonLabelPhoneme *head;
   struct _JPCommonLabelPhoneme *tail;
   struct _JPCommonLabelMora *prev;
   struct _JPCommonLabelMora *next;
   struct _JPCommonLabelWord *up;
} JPCommonLabelMora;

typedef struct _JPCommonLabelWord {
   char *pron;
   char *pos;
   char *ctype;
   char *cform;
   struct _JPCommonLabelMora *head;
   struct _JPCommonLabelMora *tail;
   struct _JPCommonLabelWord *prev;
   struct _JPCommonLabelWord *next;
   struct _JPCommonLabelAccentPhrase *up;
} JPCommonLabelWord;

typedef struct _JPCommonLabelAccentPhrase {
   int accent;
   char *emotion;
   struct _JPCommonLabelWord *head;
   struct _JPCommonLabelWord *tail;
   struct _JPCommonLabelAccentPhrase *prev;
   struct _JPCommonLabelAccentPhrase *next;
   struct _JPCommonLabelBreathGroup *up;
} JPCommonLabelAccentPhrase;

typedef struct _JPCommonLabelBreathGroup {
   struct _JPCommonLabelAccentPhrase *head;
   struct _JPCommonLabelAccentPhrase *tail;
   struct _JPCommonLabelBreathGroup *prev;
   struct _JPCommonLabelBreathGroup *next;
} JPCommonLabelBreathGroup;

typedef struct _JPCommonLabel {
   int size;
   char **feature;
   JPCommonLabelBreathGroup *breath_head;
   JPCommonLabelBreathGroup *breath_tail;
   JPCommonLabelAccentPhrase *accent_head;
   JPCommonLabelAccentPhrase *accent_tail;
   JPCommonLabelWord *word_head;
   JPCommonLabelWord *word_tail;
   JPCommonLabelMora *mora_head;
   JPCommonLabelMora *mora_tail;
   JPCommonLabelPhoneme *phoneme_head;
   JPCommonLabelPhoneme *phoneme_tail;
   int short_pause_flag;
} JPCommonLabel;

void JPCommonLabel_initialize(JPCommonLabel * label);
void JPCommonLabel_push_word(JPCommonLabel * label, const char *pron, const char *pos,
                             const char *ctype, const char *cform, int acc, int chain_flag);
void JPCommonLabel_make(JPCommonLabel * label);
int JPCommonLabel_get_size(JPCommonLabel * label);
char **JPCommonLabel_get_feature(JPCommonLabel * label);
void JPCommonLabel_print(JPCommonLabel * label);
void JPCommonLabel_fprint(JPCommonLabel * label, FILE * fp);
void JPCommonLabel_clear(JPCommonLabel * label);

/* JPCommonNode */

typedef struct _JPCommonNode {
   char *pron;                  /* pronunciation */
   char *pos;                   /* part of speech */
   char *ctype;                 /* conjugation type */
   char *cform;                 /* conjugation form */
   int acc;                     /* accent type */
   int chain_flag;              /* chain flag */
   struct _JPCommonNode *prev;
   struct _JPCommonNode *next;
} JPCommonNode;

void JPCommonNode_initialize(JPCommonNode * node);
void JPCommonNode_set_pron(JPCommonNode * node, const char *str);
void JPCommonNode_set_pos(JPCommonNode * node, const char *str);
void JPCommonNode_set_ctype(JPCommonNode * node, const char *str);
void JPCommonNode_set_cform(JPCommonNode * node, const char *str);
void JPCommonNode_set_acc(JPCommonNode * node, int acc);
void JPCommonNode_set_chain_flag(JPCommonNode * node, int flag);
const char *JPCommonNode_get_pron(JPCommonNode * node);
const char *JPCommonNode_get_pos(JPCommonNode * node);
const char *JPCommonNode_get_ctype(JPCommonNode * node);
const char *JPCommonNode_get_cform(JPCommonNode * node);
int JPCommonNode_get_acc(JPCommonNode * node);
int JPCommonNode_get_chain_flag(JPCommonNode * node);
void JPCommonNode_print(JPCommonNode * node);
void JPCommonNode_fprint(JPCommonNode * node, FILE * fp);
void JPCommonNode_clear(JPCommonNode * node);

/* JPCommon */

typedef struct _JPCommon {
   JPCommonNode *head;
   JPCommonNode *tail;
   JPCommonLabel *label;
} JPCommon;

void JPCommon_initialize(JPCommon * jpcommon);
void JPCommon_push(JPCommon * jpcommon, JPCommonNode * node);
void JPCommon_make_label(JPCommon * jpcommon);
int JPCommon_get_label_size(JPCommon * jpcommon);
char **JPCommon_get_label_feature(JPCommon * jpcommon);
void JPCommon_print(JPCommon * jpcommon);
void JPCommon_fprint(JPCommon * jpcommon, FILE * fp);
void JPCommon_refresh(JPCommon * jpcommon);
void JPCommon_clear(JPCommon * jpcommon);

JPCOMMON_H_END;

#endif                          /* !JPCOMMON_H */

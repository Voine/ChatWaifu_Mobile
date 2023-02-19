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

#ifndef NJD_H
#define NJD_H

#ifdef __cplusplus
#define NJD_H_START extern "C" {
#define NJD_H_END   }
#else
#define NJD_H_START
#define NJD_H_END
#endif                          /* __CPLUSPLUS */

NJD_H_START;

#define ASCII_HEADER 1
#define CHARSET_UTF_8 1

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* NJDNode */

typedef struct _NJDNode {
   char *string;
   char *pos;
   char *pos_group1;
   char *pos_group2;
   char *pos_group3;
   char *ctype;                 /* conjugation type */
   char *cform;                 /* conjugation form */
   char *orig;                  /* genkei */
   char *read;                  /* yomi */
   char *pron;                  /* hatsuon */
   int acc;                     /* accent */
   int mora_size;
   char *chain_rule;
   int chain_flag;
   struct _NJDNode *prev;
   struct _NJDNode *next;
} NJDNode;

void NJDNode_initialize(NJDNode * node);
void NJDNode_set_string(NJDNode * node, const char *str);
void NJDNode_set_pos(NJDNode * node, const char *str);
void NJDNode_set_pos_group1(NJDNode * node, const char *str);
void NJDNode_set_pos_group2(NJDNode * node, const char *str);
void NJDNode_set_pos_group3(NJDNode * node, const char *str);
void NJDNode_set_ctype(NJDNode * node, const char *str);
void NJDNode_set_cform(NJDNode * node, const char *str);
void NJDNode_set_orig(NJDNode * node, const char *str);
void NJDNode_set_read(NJDNode * node, const char *str);
void NJDNode_set_pron(NJDNode * node, const char *str);
void NJDNode_set_acc(NJDNode * node, int acc);
void NJDNode_set_mora_size(NJDNode * node, int size);
void NJDNode_set_chain_rule(NJDNode * node, const char *str);
void NJDNode_set_chain_flag(NJDNode * node, int flag);
void NJDNode_add_string(NJDNode * node, const char *str);
void NJDNode_add_orig(NJDNode * node, const char *str);
void NJDNode_add_read(NJDNode * node, const char *str);
void NJDNode_add_pron(NJDNode * node, const char *str);
void NJDNode_add_acc(NJDNode * node, int acc);
void NJDNode_add_mora_size(NJDNode * node, int size);
const char *NJDNode_get_string(NJDNode * node);
const char *NJDNode_get_pos(NJDNode * node);
const char *NJDNode_get_pos_group1(NJDNode * node);
const char *NJDNode_get_pos_group2(NJDNode * node);
const char *NJDNode_get_pos_group3(NJDNode * node);
const char *NJDNode_get_ctype(NJDNode * node);
const char *NJDNode_get_cform(NJDNode * node);
const char *NJDNode_get_orig(NJDNode * node);
const char *NJDNode_get_read(NJDNode * node);
const char *NJDNode_get_pron(NJDNode * node);
int NJDNode_get_acc(NJDNode * node);
int NJDNode_get_mora_size(NJDNode * node);
const char *NJDNode_get_chain_rule(NJDNode * node);
int NJDNode_get_chain_flag(NJDNode * node);
void NJDNode_load(NJDNode * node, const char *str);
NJDNode *NJDNode_insert(NJDNode * prev, NJDNode * next, NJDNode * node);
void NJDNode_copy(NJDNode * node1, NJDNode * node2);
void NJDNode_print(NJDNode * node);
void NJDNode_fprint(NJDNode * node, FILE * fp);
void NJDNode_sprint(NJDNode * node, char *buff, const char *split_code);
void NJDNode_clear(NJDNode * node);

/* NJD */

typedef struct _NJD {
   NJDNode *head;
   NJDNode *tail;
} NJD;

void NJD_initialize(NJD * njd);
void NJD_load(NJD * njd, const char *str);
void NJD_load_from_fp(NJD * njd, FILE * fp);
int NJD_get_size(NJD * njd);
void NJD_push_node(NJD * njd, NJDNode * node);
NJDNode *NJD_remove_node(NJD * njd, NJDNode * node);
void NJD_remove_silent_node(NJD * njd);
void NJD_print(NJD * njd);
void NJD_fprint(NJD * njd, FILE * fp);
void NJD_sprint(NJD * njd, char *buff, const char *split_code);
void NJD_refresh(NJD * njd);
void NJD_clear(NJD * wl);

NJD_H_END;

#endif                          /* !NJD_H */

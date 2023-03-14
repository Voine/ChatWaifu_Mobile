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

#ifndef JPCOMMON_NODE_C
#define JPCOMMON_NODE_C

#ifdef __cplusplus
#define JPCOMMON_NODE_C_START extern "C" {
#define JPCOMMON_NODE_C_END   }
#else
#define JPCOMMON_NODE_C_START
#define JPCOMMON_NODE_C_END
#endif                          /* __CPLUSPLUS */

JPCOMMON_NODE_C_START;

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "jpcommon.h"

void JPCommonNode_initialize(JPCommonNode * node)
{
   node->pron = NULL;
   node->pos = NULL;
   node->ctype = NULL;
   node->cform = NULL;
   node->acc = 0;
   node->chain_flag = -1;
   node->prev = NULL;
   node->next = NULL;
}

void JPCommonNode_set_pron(JPCommonNode * node, const char *str)
{
   if (node->pron != NULL)
      free(node->pron);
   node->pron = strdup(str);
}

void JPCommonNode_set_pos(JPCommonNode * node, const char *str)
{
   if (node->pos != NULL)
      free(node->pos);
   node->pos = strdup(str);
}

void JPCommonNode_set_ctype(JPCommonNode * node, const char *str)
{
   if (node->ctype != NULL)
      free(node->ctype);
   node->ctype = strdup(str);
}

void JPCommonNode_set_cform(JPCommonNode * node, const char *str)
{
   if (node->cform != NULL)
      free(node->cform);
   node->cform = strdup(str);
}

void JPCommonNode_set_acc(JPCommonNode * node, int acc)
{
   node->acc = acc;
}

void JPCommonNode_set_chain_flag(JPCommonNode * node, int flag)
{
   node->chain_flag = flag;
}

const char *JPCommonNode_get_pron(JPCommonNode * node)
{
   return node->pron;
}

const char *JPCommonNode_get_pos(JPCommonNode * node)
{
   return node->pos;
}

const char *JPCommonNode_get_ctype(JPCommonNode * node)
{
   return node->ctype;
}

const char *JPCommonNode_get_cform(JPCommonNode * node)
{
   return node->cform;
}

int JPCommonNode_get_acc(JPCommonNode * node)
{
   return node->acc;
}

int JPCommonNode_get_chain_flag(JPCommonNode * node)
{
   return node->chain_flag;
}

void JPCommonNode_copy(JPCommonNode * node1, JPCommonNode * node2)
{
   JPCommonNode_set_pron(node1, node2->pron);
   JPCommonNode_set_pos(node1, node2->pos);
   JPCommonNode_set_ctype(node1, node2->ctype);
   JPCommonNode_set_cform(node1, node2->cform);
   JPCommonNode_set_acc(node1, node2->acc);
   JPCommonNode_set_chain_flag(node1, node2->chain_flag);
}

void JPCommonNode_print(JPCommonNode * node)
{
   JPCommonNode_fprint(node, stdout);
}

void JPCommonNode_fprint(JPCommonNode * node, FILE * fp)
{
   fprintf(fp, "%s,%s,%s,%s,%d,%d\n", node->pron, node->pos, node->ctype, node->cform, node->acc,
           node->chain_flag);
}

void JPCommonNode_clear(JPCommonNode * node)
{
   if (node->pron != NULL) {
      free(node->pron);
      node->pron = NULL;
   }
   if (node->pos != NULL) {
      free(node->pos);
      node->pos = NULL;
   }
   if (node->ctype != NULL) {
      free(node->ctype);
      node->ctype = NULL;
   }
   if (node->cform != NULL) {
      free(node->cform);
      node->cform = NULL;
   }
   node->acc = 0;
   node->chain_flag = -1;
   node->prev = NULL;
   node->next = NULL;
}

JPCOMMON_NODE_C_END;

#endif                          /* !JPCOMMON_NODE_C */

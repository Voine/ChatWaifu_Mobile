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

#ifndef NJD2JPCOMMON_C
#define NJD2JPCOMMON_C

#ifdef __cplusplus
#define NJD2JPCOMMON_C_START extern "C" {
#define NJD2JPCOMMON_C_END   }
#else
#define NJD2JPCOMMON_C_START
#define NJD2JPCOMMON_C_END
#endif                          /* __CPLUSPLUS */

NJD2JPCOMMON_C_START;

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "njd2jpcommon.h"

#ifdef ASCII_HEADER
#if defined(CHARSET_EUC_JP)
#include "njd2jpcommon_rule_ascii_for_euc_jp.h"
#elif defined(CHARSET_SHIFT_JIS)
#include "njd2jpcommon_rule_ascii_for_shift_jis.h"
#elif defined(CHARSET_UTF_8)
#include "njd2jpcommon_rule_ascii_for_utf_8.h"
#else
#error CHARSET is not specified
#endif
#else
#if defined(CHARSET_EUC_JP)
#include "njd2jpcommon_rule_euc_jp.h"
#elif defined(CHARSET_SHIFT_JIS)
#include "njd2jpcommon_rule_shift_jis.h"
#elif defined(CHARSET_UTF_8)
#include "njd2jpcommon_rule_utf_8.h"
#else
#error CHARSET is not specified
#endif
#endif

#define MAXBUFLEN 1024

static void convert_pos(char *buff, const char *pos, const char *pos_group1, const char *pos_group2,
                        const char *pos_group3)
{
   int i;

   for (i = 0; njd2jpcommon_pos_list[i] != NULL; i += 5) {
      if (strcmp(njd2jpcommon_pos_list[i], pos) == 0 &&
          strcmp(njd2jpcommon_pos_list[i + 1], pos_group1) == 0 &&
          strcmp(njd2jpcommon_pos_list[i + 2], pos_group2) == 0 &&
          strcmp(njd2jpcommon_pos_list[i + 3], pos_group3) == 0) {
         strcpy(buff, njd2jpcommon_pos_list[i + 4]);
         return;
      }
   }
   fprintf(stderr,
           "WARNING: convert_pos() in openjtalk.njd2jpcommon.c: %s %s %s %s are not appropriate POS.\n", pos,
           pos_group1, pos_group2, pos_group3);
   strcpy(buff, njd2jpcommon_pos_list[4]);
}

static void convert_ctype(char *buff, const char *ctype)
{
   int i;

   for (i = 0; njd2jpcommon_ctype_list[i] != NULL; i += 2) {
      if (strcmp(njd2jpcommon_ctype_list[i], ctype) == 0) {
         strcpy(buff, njd2jpcommon_ctype_list[i + 1]);
         return;
      }
   }
   fprintf(stderr,
           "WARNING: convert_ctype() in openjtalk.njd2jpcommon.c: %s is not appropriate conjugation type.\n",
           ctype);
   strcpy(buff, njd2jpcommon_ctype_list[1]);
}

static void convert_cform(char *buff, const char *cform)
{
   int i;

   for (i = 0; njd2jpcommon_cform_list[i] != NULL; i += 2) {
      if (strcmp(njd2jpcommon_cform_list[i], cform) == 0) {
         strcpy(buff, njd2jpcommon_cform_list[i + 1]);
         return;
      }
   }
   fprintf(stderr,
           "WARNING: convert_cform() in openjtalk.njd2jpcommon.c: %s is not appropriate conjugation form.\n",
           cform);
   strcpy(buff, njd2jpcommon_cform_list[1]);
}

void njd2jpcommon(JPCommon * jpcommon, NJD * njd)
{
   char buff[MAXBUFLEN];
   NJDNode *inode;
   JPCommonNode *jnode;

   for (inode = njd->head; inode != NULL; inode = inode->next) {
      jnode = (JPCommonNode *) calloc(1, sizeof(JPCommonNode));
      JPCommonNode_initialize(jnode);
      JPCommonNode_set_pron(jnode, NJDNode_get_pron(inode));
      convert_pos(buff, NJDNode_get_pos(inode), NJDNode_get_pos_group1(inode),
                  NJDNode_get_pos_group2(inode), NJDNode_get_pos_group3(inode));
      JPCommonNode_set_pos(jnode, buff);
      convert_ctype(buff, NJDNode_get_ctype(inode));
      JPCommonNode_set_ctype(jnode, buff);
      convert_cform(buff, NJDNode_get_cform(inode));
      JPCommonNode_set_cform(jnode, buff);
      JPCommonNode_set_acc(jnode, NJDNode_get_acc(inode));
      JPCommonNode_set_chain_flag(jnode, NJDNode_get_chain_flag(inode));
      JPCommon_push(jpcommon, jnode);
   }
}

NJD2JPCOMMON_C_END;

#endif                          /* !NJD2JPCOMMON_C */

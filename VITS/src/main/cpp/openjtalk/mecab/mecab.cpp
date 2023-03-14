//  MeCab -- Yet Another Part-of-Speech and Morphological Analyzer
//
//
//  Copyright(C) 2001-2006 Taku Kudo <taku@chasen.org>
//  Copyright(C) 2004-2006 Nippon Telegraph and Telephone Corporation

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

#ifndef MECAB_CPP
#define MECAB_CPP

#include <stdlib.h>
#include <string.h>

#include <iostream>

#include "mecab.h"

#ifdef __cplusplus
#define MECAB_CPP_START extern "C" {
#define MECAB_CPP_END   }
#else
#define MECAB_CPP_START
#define MECAB_CPP_END
#endif                          /* __CPLUSPLUS */

MECAB_CPP_START;

BOOL Mecab_initialize(Mecab *m)
{
   m->feature = NULL;
   m->size = 0;
   m->model = NULL;
   m->tagger = NULL;
   m->lattice = NULL;
   return TRUE;
}

BOOL Mecab_load(Mecab *m, const char *dicdir, AssetJNI* asjni)
{
   int i;
   int argc = 3;
   char **argv;

   if(m == NULL)
      return FALSE;

   if(dicdir == NULL || strlen(dicdir) == 0)
      return FALSE;

   Mecab_clear(m);

   argv = (char **) malloc(sizeof(char *) * argc);

   argv[0] = strdup("openjtalk.mecab");
   argv[1] = strdup("-d");
   argv[2] = strdup(dicdir);

   MeCab::Model *model = MeCab::createModel(asjni, argc, argv);

   for(i = 0; i < argc; i++)
      free(argv[i]);
   free(argv);

   if(model == NULL) {
      fprintf(stderr, "ERROR: Mecab_load() in openjtalk.mecab.cpp: Cannot open %s.\n", dicdir);
      return FALSE;
   }

   MeCab::Tagger *tagger = model->createTagger();
   if(tagger == NULL) {
      delete model;
      fprintf(stderr, "ERROR: Mecab_load() in openjtalk.mecab.cpp: Cannot open %s.\n", dicdir);
      return FALSE;
   }

   MeCab::Lattice *lattice = model->createLattice();
   if(lattice == NULL) {
      delete model;
      delete tagger;
      fprintf(stderr, "ERROR: Mecab_load() in openjtalk.mecab.cpp: Cannot open %s.\n", dicdir);
      return FALSE;
   }

   m->model = (void *) model;
   m->tagger = (void *) tagger;
   m->lattice = (void *) lattice;

   return TRUE;
}

BOOL Mecab_analysis(Mecab *m, const char *str)
{
   if(m->model == NULL || m->tagger == NULL || m->lattice == NULL || str == NULL)
      return FALSE;

   if(m->size > 0 || m->feature != NULL)
      Mecab_refresh(m);

   MeCab::Tagger *tagger = (MeCab::Tagger *) m->tagger;
   MeCab::Lattice *lattice = (MeCab::Lattice *) m->lattice;

   lattice->set_sentence(str);

   if(tagger->parse(lattice) == false) {
      lattice->clear();
      return FALSE;
   }

   for (const MeCab::Node* node = lattice->bos_node(); node; node = node->next) {
      if(node->stat != MECAB_BOS_NODE && node->stat != MECAB_EOS_NODE)
         m->size++;
   }

   if(m->size == 0)
      return TRUE;

   m->feature = (char **) calloc(m->size, sizeof(char *));
   int index = 0;
   for (const MeCab::Node* node = lattice->bos_node(); node; node = node->next) {
      if(node->stat != MECAB_BOS_NODE && node->stat != MECAB_EOS_NODE) {
         std::string f(node->surface, node->length);
         f += ",";
         f += node->feature;
         m->feature[index] = strdup(f.c_str());
         index++;
      }
   }

   lattice->clear();

   return TRUE;
}

BOOL Mecab_print(Mecab *m)
{
   int i;

   for(i = 0; i < m->size; i++)
      printf("%s\n", m->feature[i]);
   return TRUE;
}

int Mecab_get_size(Mecab *m)
{
   return m->size;
}

char **Mecab_get_feature(Mecab *m)
{
   return m->feature;
}

BOOL Mecab_refresh(Mecab *m)
{
   int i;

   if(m->feature != NULL) {
      for(i = 0; i < m->size; i++)
         free(m->feature[i]);
      free(m->feature);
      m->feature = NULL;
      m->size = 0;
   }

   return TRUE;
}

BOOL Mecab_clear(Mecab *m)
{
   Mecab_refresh(m);

   if(m->lattice) {
      MeCab::Lattice *lattice = (MeCab::Lattice *) m->lattice;
      delete lattice;
      m->lattice = NULL;
   }

   if(m->tagger) {
      MeCab::Tagger *tagger = (MeCab::Tagger *) m->tagger;
      delete tagger;
      m->tagger = NULL;
   }

   if(m->model) {
      MeCab::Model *model = (MeCab::Model *) m->model;
      delete model;
      m->model = NULL;
   }

   return TRUE;
}

MECAB_CPP_END;

#endif                          /* !MECAB_CPP */

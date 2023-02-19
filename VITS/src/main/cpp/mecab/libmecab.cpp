//
//  MeCab -- Yet Another Part-of-Speech and Morphological Analyzer
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

#if defined(_WIN32) && !defined(__CYGWIN__)
#include <windows.h>
#endif

#include "mecab.h"
#include "tokenizer.h"
#include "utils.h"

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

namespace {
const char kUnknownError[] = "Unknown Error";
const size_t kErrorBufferSize = 256;
}

namespace {
#ifdef HAVE_TLS_KEYWORD
__thread char kErrorBuffer[kErrorBufferSize];
#else
char kErrorBuffer[kErrorBufferSize];
#endif
}

const char *getGlobalError() {
  return kErrorBuffer;
}

void setGlobalError(const char *str) {
  strncpy(kErrorBuffer, str, kErrorBufferSize - 1);
  kErrorBuffer[kErrorBufferSize - 1] = '\0';
}
/* for Open JTalk
#endif
*/

mecab_t* mecab_new(AssetJNI* asjni, int argc, char **argv) {
  MeCab::Tagger *tagger = MeCab::createTagger(asjni, argc, argv);
  if (!tagger) {
    MeCab::deleteTagger(tagger);
    return 0;
  }
  return reinterpret_cast<mecab_t *>(tagger);
}

mecab_t* mecab_new2(AssetJNI* asjni, const char *arg) {
  MeCab::Tagger *tagger = MeCab::createTagger(asjni, arg);
  if (!tagger) {
    MeCab::deleteTagger(tagger);
    return 0;
  }
  return reinterpret_cast<mecab_t *>(tagger);
}

const char *mecab_version() {
  return MeCab::Tagger::version();
}

const char* mecab_strerror(mecab_t *tagger) {
  if (!tagger) {
    return MeCab::getLastError();
  }
  return reinterpret_cast<MeCab::Tagger *>(tagger)->what();
}

void mecab_destroy(mecab_t *tagger) {
  MeCab::Tagger *ptr = reinterpret_cast<MeCab::Tagger *>(tagger);
  MeCab::deleteTagger(ptr);
  ptr = 0;
}

int  mecab_get_partial(mecab_t *tagger) {
  return reinterpret_cast<MeCab::Tagger *>(tagger)->partial();
}

void mecab_set_partial(mecab_t *tagger, int partial) {
  reinterpret_cast<MeCab::Tagger *>(tagger)->set_partial(partial);
}

float  mecab_get_theta(mecab_t *tagger) {
  return reinterpret_cast<MeCab::Tagger *>(tagger)->theta();
}

void mecab_set_theta(mecab_t *tagger, float theta) {
  reinterpret_cast<MeCab::Tagger *>(tagger)->set_theta(theta);
}

int  mecab_get_lattice_level(mecab_t *tagger) {
  return reinterpret_cast<MeCab::Tagger *>(tagger)->lattice_level();
}

void mecab_set_lattice_level(mecab_t *tagger, int level) {
  reinterpret_cast<MeCab::Tagger *>(tagger)->set_lattice_level(level);
}

int mecab_get_all_morphs(mecab_t *tagger) {
  return static_cast<int>(
      reinterpret_cast<MeCab::Tagger *>(tagger)->all_morphs());
}

void mecab_set_all_morphs(mecab_t *tagger, int all_morphs) {
  reinterpret_cast<MeCab::Tagger *>(tagger)->set_all_morphs(all_morphs);
}

const char* mecab_sparse_tostr(mecab_t *tagger, const char *str) {
  return reinterpret_cast<MeCab::Tagger *>(tagger)->parse(str);
}

const char* mecab_sparse_tostr2(mecab_t *tagger, const char *str, size_t len) {
  return reinterpret_cast<MeCab::Tagger *>(tagger)->parse(str, len);
}

char* mecab_sparse_tostr3(mecab_t *tagger, const char *str, size_t len,
                          char *out, size_t len2) {
  return const_cast<char *>(
      reinterpret_cast<MeCab::Tagger *>(tagger)->parse(
          str, len, out, len2));
}

const mecab_node_t* mecab_sparse_tonode(mecab_t *tagger, const char *str) {
  return reinterpret_cast<const mecab_node_t *>(
      reinterpret_cast<MeCab::Tagger *>(tagger)->parseToNode(str));
}

const mecab_node_t* mecab_sparse_tonode2(mecab_t *tagger,
                                         const char *str, size_t len) {
  return reinterpret_cast<const mecab_node_t *>(
      reinterpret_cast<MeCab::Tagger *>(tagger)->parseToNode(str, len));
}

const char* mecab_nbest_sparse_tostr(mecab_t *tagger, size_t N,
                                     const char *str) {
  return reinterpret_cast<MeCab::Tagger *>(tagger)->parseNBest(N, str);
}

const char* mecab_nbest_sparse_tostr2(mecab_t *tagger, size_t N,
                                      const char* str, size_t len) {
  return reinterpret_cast<MeCab::Tagger *>(
      tagger)->parseNBest(N, str, len);
}

char* mecab_nbest_sparse_tostr3(mecab_t *tagger, size_t N,
                                const char *str, size_t len,
                                char *out, size_t len2) {
  return const_cast<char *>(
      reinterpret_cast<MeCab::Tagger *>(
          tagger)->parseNBest(N, str, len, out, len2));
}

int mecab_nbest_init(mecab_t *tagger, const char *str) {
  return reinterpret_cast<
      MeCab::Tagger *>(tagger)->parseNBestInit(str);
}

int mecab_nbest_init2(mecab_t *tagger, const char *str, size_t len) {
  return reinterpret_cast<
      MeCab::Tagger *>(tagger)->parseNBestInit(str, len);
}

const char* mecab_nbest_next_tostr(mecab_t *tagger) {
  return reinterpret_cast<MeCab::Tagger *>(tagger)->next();
}

char* mecab_nbest_next_tostr2(mecab_t *tagger, char *out, size_t len2) {
  return const_cast<char *>(
      reinterpret_cast<MeCab::Tagger *>(tagger)->next(out, len2));
}

const mecab_node_t* mecab_nbest_next_tonode(mecab_t *tagger) {
  return reinterpret_cast<const mecab_node_t *>(
      reinterpret_cast<MeCab::Tagger *>(tagger)->nextNode());
}

const char* mecab_format_node(mecab_t *tagger, const mecab_node_t* n) {
  return reinterpret_cast<MeCab::Tagger *>(tagger)->formatNode(n);
}

const mecab_dictionary_info_t *mecab_dictionary_info(mecab_t *tagger) {
  return reinterpret_cast<const mecab_dictionary_info_t *>(
      reinterpret_cast<MeCab::Tagger *>(tagger)->dictionary_info());
}

int mecab_parse_lattice(mecab_t *mecab, mecab_lattice_t *lattice) {
  return static_cast<int>(
      reinterpret_cast<MeCab::Tagger *>(mecab)->parse(
          reinterpret_cast<MeCab::Lattice *>(lattice)));
}

mecab_lattice_t *mecab_lattice_new() {
  return reinterpret_cast<mecab_lattice_t *>(MeCab::createLattice());
}

void mecab_lattice_destroy(mecab_lattice_t *lattice) {
  MeCab::Lattice *ptr = reinterpret_cast<MeCab::Lattice *>(lattice);
  MeCab::deleteLattice(ptr);
  ptr = 0;
}

void mecab_lattice_clear(mecab_lattice_t *lattice) {
  reinterpret_cast<MeCab::Lattice *>(lattice)->clear();
}

int mecab_lattice_is_available(mecab_lattice_t *lattice) {
  return static_cast<int>(
      reinterpret_cast<MeCab::Lattice *>(lattice)->is_available());
}
mecab_node_t *mecab_lattice_get_bos_node(mecab_lattice_t *lattice) {
  return reinterpret_cast<mecab_node_t *>(
      reinterpret_cast<MeCab::Lattice *>(lattice)->bos_node());
}

mecab_node_t *mecab_lattice_get_eos_node(mecab_lattice_t *lattice) {
  return reinterpret_cast<mecab_node_t *>(
      reinterpret_cast<MeCab::Lattice *>(lattice)->eos_node());
}

mecab_node_t **mecab_lattice_get_all_begin_nodes(mecab_lattice_t *lattice) {
  return reinterpret_cast<mecab_node_t **>(
      reinterpret_cast<MeCab::Lattice *>(lattice)->begin_nodes());
}

mecab_node_t **mecab_lattice_get_all_end_nodes(mecab_lattice_t *lattice) {
  return reinterpret_cast<mecab_node_t **>(
      reinterpret_cast<MeCab::Lattice *>(lattice)->end_nodes());
}

mecab_node_t *mecab_lattice_get_begin_nodes(mecab_lattice_t *lattice,
                                            size_t pos) {
  return reinterpret_cast<mecab_node_t *>(
      reinterpret_cast<MeCab::Lattice *>(lattice)->begin_nodes(pos));
}

mecab_node_t    *mecab_lattice_get_end_nodes(mecab_lattice_t *lattice,
                                             size_t pos) {
  return reinterpret_cast<mecab_node_t *>(
      reinterpret_cast<MeCab::Lattice *>(lattice)->end_nodes(pos));
}

const char  *mecab_lattice_get_sentence(mecab_lattice_t *lattice) {
  return reinterpret_cast<MeCab::Lattice *>(lattice)->sentence();
}

void  mecab_lattice_set_sentence(mecab_lattice_t *lattice,
                                 const char *sentence) {
  reinterpret_cast<MeCab::Lattice *>(lattice)->set_sentence(sentence);
}

void mecab_lattice_set_sentence2(mecab_lattice_t *lattice,
                                 const char *sentence, size_t len) {
  reinterpret_cast<MeCab::Lattice *>(lattice)->set_sentence(
      sentence, len);
}

size_t mecab_lattice_get_size(mecab_lattice_t *lattice) {
  return reinterpret_cast<MeCab::Lattice *>(lattice)->size();
}

double mecab_lattice_get_z(mecab_lattice_t *lattice) {
  return reinterpret_cast<MeCab::Lattice *>(lattice)->Z();
}

void mecab_lattice_set_z(mecab_lattice_t *lattice, double Z) {
  reinterpret_cast<MeCab::Lattice *>(lattice)->set_Z(Z);
}

double mecab_lattice_get_theta(mecab_lattice_t *lattice) {
  return reinterpret_cast<MeCab::Lattice *>(lattice)->theta();
}

void mecab_lattice_set_theta(mecab_lattice_t *lattice, double theta) {
  reinterpret_cast<MeCab::Lattice *>(lattice)->set_theta(theta);
}

int mecab_lattice_next(mecab_lattice_t *lattice) {
  return static_cast<int>(
      reinterpret_cast<MeCab::Lattice *>(lattice)->next());
}

int mecab_lattice_get_request_type(mecab_lattice_t *lattice) {
  return reinterpret_cast<MeCab::Lattice *>(lattice)->request_type();
}

int mecab_lattice_has_request_type(mecab_lattice_t *lattice,
                                   int request_type) {
  return reinterpret_cast<MeCab::Lattice *>(
      lattice)->has_request_type(request_type);
}

void mecab_lattice_set_request_type(mecab_lattice_t *lattice,
                                    int request_type) {
  reinterpret_cast<MeCab::Lattice *>(
      lattice)->set_request_type(request_type);
}

void mecab_lattice_add_request_type(mecab_lattice_t *lattice,
                                    int request_type) {
  reinterpret_cast<MeCab::Lattice *>(
      lattice)->add_request_type(request_type);
}

void mecab_lattice_remove_request_type(mecab_lattice_t *lattice,
                                       int request_type) {
  return reinterpret_cast<MeCab::Lattice *>(
      lattice)->remove_request_type(request_type);
}

mecab_node_t    *mecab_lattice_new_node(mecab_lattice_t *lattice) {
  return reinterpret_cast<mecab_node_t *>(
      reinterpret_cast<MeCab::Lattice *>(lattice)->newNode());
}

const char *mecab_lattice_tostr(mecab_lattice_t *lattice) {
  return reinterpret_cast<MeCab::Lattice *>(lattice)->toString();
}

const char *mecab_lattice_tostr2(mecab_lattice_t *lattice,
                                 char *buf, size_t size) {
  return reinterpret_cast<MeCab::Lattice *>(
      lattice)->toString(buf, size);
}
const char *mecab_lattice_nbest_tostr(mecab_lattice_t *lattice,
                                      size_t N) {
  return reinterpret_cast<MeCab::Lattice *>(
      lattice)->enumNBestAsString(N);
}
const char *mecab_lattice_nbest_tostr2(mecab_lattice_t *lattice,
                                       size_t N, char *buf, size_t size) {
  return reinterpret_cast<MeCab::Lattice *>(
      lattice)->enumNBestAsString(N, buf, size);
}

int mecab_lattice_has_constraint(mecab_lattice_t *lattice) {
  return static_cast<bool>(reinterpret_cast<MeCab::Lattice *>(
                               lattice)->has_constraint());
}

int mecab_lattice_get_boundary_constraint(mecab_lattice_t *lattice,
                                          size_t pos) {
  return reinterpret_cast<MeCab::Lattice *>(
      lattice)->boundary_constraint(pos);
}

const char *mecab_lattice_get_feature_constraint(mecab_lattice_t *lattice,
                                                 size_t pos) {
  return reinterpret_cast<MeCab::Lattice *>(
      lattice)->feature_constraint(pos);
}

void mecab_lattice_set_boundary_constraint(mecab_lattice_t *lattice,
                                           size_t pos, int boundary_type) {
  return reinterpret_cast<MeCab::Lattice *>(
      lattice)->set_boundary_constraint(pos, boundary_type);
}

void mecab_lattice_set_feature_constraint(mecab_lattice_t *lattice,
                                          size_t begin_pos, size_t end_pos,
                                          const char *feature) {
  return reinterpret_cast<MeCab::Lattice *>(
      lattice)->set_feature_constraint(begin_pos, end_pos, feature);
}

void mecab_lattice_set_result(mecab_lattice_t *lattice,
                              const char *result) {
  return reinterpret_cast<MeCab::Lattice *>(lattice)->set_result(result);
}

const char *mecab_lattice_strerror(mecab_lattice_t *lattice) {
  return reinterpret_cast<MeCab::Lattice *>(lattice)->what();
}

mecab_model_t *mecab_model_new(AssetJNI* asjni,int argc, char **argv) {
  MeCab::Model *model = MeCab::createModel(asjni, argc, argv);
  if (!model) {
    MeCab::deleteModel(model);
    return 0;
  }
  return reinterpret_cast<mecab_model_t *>(model);
}

mecab_model_t *mecab_model_new2(AssetJNI* asjni,const char *arg) {
  MeCab::Model *model = MeCab::createModel(asjni, arg);
  if (!model) {
    MeCab::deleteModel(model);
    return 0;
  }
  return reinterpret_cast<mecab_model_t *>(model);
}

void mecab_model_destroy(mecab_model_t *model) {
  MeCab::Model *ptr = reinterpret_cast<MeCab::Model *>(model);
  MeCab::deleteModel(ptr);
  ptr = 0;
}

mecab_t *mecab_model_new_tagger(mecab_model_t *model) {
  return reinterpret_cast<mecab_t *>(
      reinterpret_cast<MeCab::Model *>(model)->createTagger());
}

mecab_lattice_t *mecab_model_new_lattice(mecab_model_t *model) {
  return reinterpret_cast<mecab_lattice_t *>(
      reinterpret_cast<MeCab::Model *>(model)->createLattice());
}

int mecab_model_swap(mecab_model_t *model, mecab_model_t *new_model) {
  return static_cast<int>(
      reinterpret_cast<MeCab::Model *>(model)->swap(
          reinterpret_cast<MeCab::Model *>(new_model)));
}

const mecab_dictionary_info_t* mecab_model_dictionary_info(
    mecab_model_t *model) {
  return reinterpret_cast<const mecab_dictionary_info_t *>(
      reinterpret_cast<MeCab::Model *>(model)->dictionary_info());
}

int mecab_model_transition_cost(mecab_model_t *model,
                                unsigned short rcAttr,
                                unsigned short lcAttr) {
  return reinterpret_cast<MeCab::Model *>(model)->transition_cost(
      rcAttr, lcAttr);
}

mecab_node_t *mecab_model_lookup(mecab_model_t *model,
                                 const char *begin,
                                 const char *end,
                                 mecab_lattice_t *lattice) {
  return reinterpret_cast<mecab_node_t *>(
      reinterpret_cast<MeCab::Model *>(model)->lookup(
          begin, end,
          reinterpret_cast<MeCab::Lattice *>(lattice)));
}

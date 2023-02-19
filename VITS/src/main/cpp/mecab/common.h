// MeCab -- Yet Another Part-of-Speech and Morphological Analyzer
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

#ifndef MECAB_COMMON_H_
#define MECAB_COMMON_H_

/* for Open JTalk
#include <algorithm>
#include <cmath>
*/
#include <cstdlib>
#include <cstdio>
#include <cstring>
#include <string>
#include <iostream>
/* for Open JTalk
#include <sstream>
*/

/* for Open JTalk
#ifdef __CYGWIN__
#define _GLIBCXX_EXPORT_TEMPLATE
#endif
*/

#define HAVE_CONFIG_H 1

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

/* for Open JTalk
#if defined(_MSC_VER) || defined(__CYGWIN__)
#define NOMINMAX
#define snprintf _snprintf
#endif
*/

/* for Open JTalk */
#if defined(_WIN32) && !defined(__CYGWIN__)
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef snprintf
#define snprintf _snprintf
#endif
#endif

#include <algorithm> /* for Open JTalk */
#include <cmath>     /* for Open JTalk */
#include <sstream>   /* for Open JTalk */

#define COPYRIGHT "MeCab: Yet Another Part-of-Speech and Morphological Analyzer\n\
\nCopyright(C) 2001-2012 Taku Kudo \nCopyright(C) 2004-2008 Nippon Telegraph and Telephone Corporation\n"

#define SYS_DIC_FILE            "sys.dic"
#define UNK_DEF_FILE            "unk.def"
#define UNK_DIC_FILE            "unk.dic"
#define MATRIX_DEF_FILE         "matrix.def"
#define MATRIX_FILE             "matrix.bin"
#define CHAR_PROPERTY_DEF_FILE  "char.def"
#define CHAR_PROPERTY_FILE      "char.bin"
#define FEATURE_FILE            "feature.def"
#define REWRITE_FILE            "rewrite.def"
#define LEFT_ID_FILE            "left-id.def"
#define RIGHT_ID_FILE           "right-id.def"
#define POS_ID_FILE             "pos-id.def"
#define MODEL_DEF_FILE          "model.def"
#define MODEL_FILE              "model.bin"
#define DICRC                   "dicrc"
#define BOS_KEY                 "BOS/EOS"

#define DEFAULT_MAX_GROUPING_SIZE 24

#define CHAR_PROPERTY_DEF_DEFAULT "DEFAULT 1 0 0\nSPACE   0 1 0\n0x0020 SPACE\n"
#define UNK_DEF_DEFAULT           "DEFAULT,0,0,0,*\nSPACE,0,0,0,*\n"
#define MATRIX_DEF_DEFAULT        "1 1\n0 0 0\n"

#ifdef MECAB_USE_UTF8_ONLY
#define MECAB_DEFAULT_CHARSET "UTF-8"
#endif

#ifndef MECAB_DEFAULT_CHARSET
#if defined(_WIN32) && !defined(__CYGWIN__)
#define MECAB_DEFAULT_CHARSET "SHIFT-JIS"
#else
#define MECAB_DEFAULT_CHARSET "EUC-JP"
#endif
#endif

#define NBEST_MAX 512
#define NODE_FREELIST_SIZE 512
#define PATH_FREELIST_SIZE 2048
#define MIN_INPUT_BUFFER_SIZE 8192
#define MAX_INPUT_BUFFER_SIZE (8192*640)
#define BUF_SIZE 8192

#ifndef EXIT_FAILURE
#define EXIT_FAILURE 1
#endif

#ifndef EXIT_SUCCESS
#define EXIT_SUCCESS 0
#endif

#if defined(_WIN32) && !defined(__CYGWIN__)
#define WPATH(path) (MeCab::Utf8ToWide(path).c_str())
#else
#define WPATH(path) (path)
#endif

namespace MeCab {
class die {
 public:
  die() {}
  ~die() {
    std::cerr << std::endl;
    /* for Open JTalk
    exit(-1);
    */
  }
  int operator&(std::ostream&) { return 0; }
};

struct whatlog {
  std::ostringstream stream_;
  std::string str_;
  const char *str() {
    str_ = stream_.str();
    return str_.c_str();
  }
};

class wlog {
 public:
  wlog(whatlog *what) : what_(what) {
    what_->stream_.clear();
  }
  bool operator&(std::ostream &) {
    return false;
  }
 private:
  whatlog *what_;
};
}  // MeCab

#define WHAT what_.stream_

#define CHECK_FALSE(condition) \
 if (condition) {} else return \
   wlog(&what_) & what_.stream_ <<              \
      __FILE__ << "(" << __LINE__ << ") [" << #condition << "] "

#define CHECK_DIE(condition) \
(condition) ? 0 : die() & std::cerr << __FILE__ << \
"(" << __LINE__ << ") [" << #condition << "] "

#endif  // MECAB_COMMON_H_

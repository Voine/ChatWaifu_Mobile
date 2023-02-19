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

#include <cstdio>
#include <fstream>
#include "common.h"
#include "param.h"
#include "string_buffer.h"
#include "utils.h"
#ifdef __ANDROID__ /* for Open JTalk */
#include "ctype.h"
#endif /* __ANDROID__ */

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

namespace MeCab {
namespace {
void init_param(std::string *help,
                std::string *version,
                const std::string &system_name,
                const Option *opts) {
  *help = std::string(COPYRIGHT) + "\nUsage: " +
      system_name + " [options] files\n";

  *version = std::string(PACKAGE) + " of " + VERSION + '\n';

  size_t max = 0;
  for (size_t i = 0; opts[i].name; ++i) {
    size_t l = 1 + std::strlen(opts[i].name);
    if (opts[i].arg_description)
      l += (1 + std::strlen(opts[i].arg_description));
    max = std::max(l, max);
  }

  for (size_t i = 0; opts[i].name; ++i) {
    size_t l = std::strlen(opts[i].name);
    if (opts[i].arg_description)
      l += (1 + std::strlen(opts[i].arg_description));
    *help += " -";
    *help += opts[i].short_name;
    *help += ", --";
    *help += opts[i].name;
    if (opts[i].arg_description) {
      *help += '=';
      *help += opts[i].arg_description;
    }
    for (; l <= max; l++) *help += ' ';
    *help += opts[i].description;
    *help += '\n';
  }

  *help += '\n';
  return;
}
}  // namespace

void Param::dump_config(std::ostream *os) const {
  for (std::map<std::string, std::string>::const_iterator it = conf_.begin();
       it != conf_.end();
       ++it) {
    *os << it->first << ": " << it->second << std::endl;
  }
}

bool Param::load(const char *filename) {
  std::ifstream ifs(WPATH(filename));
  set<std::string>("cost-factor","800",false);
  set<std::string>("bos-feature","BOS/EOS,*,*,*,*,*,*,*,*",false);
  set<std::string>("eval-size","8",false);
  set<std::string>("unk-eval-size","4",false);
  set<std::string>("node-format-yomi","%pS%f[7]",false);
  set<std::string>("unk-format-yomi","%M",false);
  set<std::string>("eos-format-yomi","\n",false);
  set<std::string>("node-format-simple","%m\t%F-[0,1,2,3]\n",false);
  set<std::string>("eos-format-simple","EOS\n",false);
  set<std::string>("node-format-chasen","%m\t%f[7]\t%f[6]\t%F-[0,1,2,3]\t%f[4]\t%f[5]\n",false);
  set<std::string>("unk-format-chasen","%m\t%m\t%m\t%F-[0,1,2,3]\t\t\n",false);
  set<std::string>("eos-format-chasen","EOS\n",false);
  set<std::string>("node-format-chasen2","%M\t%f[7]\t%f[6]\t%F-[0,1,2,3]\t%f[4]\t%f[5]\n",false);
  set<std::string>("unk-format-chasen2","%M\t%m\t%m\t%F-[0,1,2,3]\t\t\n",false);
  set<std::string>("eos-format-chasen2","EOS\n",false);

  return true;
}

bool Param::open(int argc, char **argv, const Option *opts) {
  int ind = 0;
  int _errno = 0;

#define GOTO_ERROR(n) {                         \
    _errno = n;                                 \
    goto ERROR; } while (0)

  if (argc <= 0) {
    system_name_ = "unknown";
    return true;  // this is not error
  }

  system_name_ = std::string(argv[0]);

  init_param(&help_, &version_, system_name_, opts);

  for (size_t i = 0; opts[i].name; ++i) {
    if (opts[i].default_value) set<std::string>
                                   (opts[i].name, opts[i].default_value);
  }

  for (ind = 1; ind < argc; ind++) {
    if (argv[ind][0] == '-') {
      // long options
      if (argv[ind][1] == '-') {
        char *s;
        for (s = &argv[ind][2]; *s != '\0' && *s != '='; s++);
        size_t len = (size_t)(s - &argv[ind][2]);
        if (len == 0) return true;  // stop the scanning

        bool hit = false;
        size_t i = 0;
        for (i = 0; opts[i].name; ++i) {
          size_t nlen = std::strlen(opts[i].name);
          if (nlen == len && std::strncmp(&argv[ind][2],
                                          opts[i].name, len) == 0) {
            hit = true;
            break;
          }
        }

        if (!hit) GOTO_ERROR(0);

        if (opts[i].arg_description) {
          if (*s == '=') {
            set<std::string>(opts[i].name, s+1);
          } else {
            if (argc == (ind+1)) GOTO_ERROR(1);
            set<std::string>(opts[i].name, argv[++ind]);
          }
        } else {
          if (*s == '=') GOTO_ERROR(2);
          set<int>(opts[i].name, 1);
        }

        // short options
      } else if (argv[ind][1] != '\0') {
        size_t i = 0;
        bool hit = false;
        for (i = 0; opts[i].name; ++i) {
          if (opts[i].short_name == argv[ind][1]) {
            hit = true;
            break;
          }
        }

        if (!hit) GOTO_ERROR(0);

        if (opts[i].arg_description) {
          if (argv[ind][2] != '\0') {
            set<std::string>(opts[i].name, &argv[ind][2]);
          } else {
            if (argc == (ind+1)) GOTO_ERROR(1);
            set<std::string>(opts[i].name, argv[++ind]);
          }
        } else {
          if (argv[ind][2] != '\0') GOTO_ERROR(2);
          set<int>(opts[i].name, 1);
        }
      }
    } else {
      rest_.push_back(std::string(argv[ind]));  // others
    }
  }

  return true;

ERROR:
  switch (_errno) {
    case 0: WHAT << "unrecognized option `" << argv[ind] << "`"; break;
    case 1: WHAT << "`" << argv[ind] << "` requires an argument";  break;
    case 2: WHAT << "`" << argv[ind] << "` doesn't allow an argument"; break;
  }
  return false;
}

void Param::clear() {
  conf_.clear();
  rest_.clear();
}

bool Param::open(const char *arg, const Option *opts) {
  scoped_fixed_array<char, BUF_SIZE> str;
  std::strncpy(str.get(), arg, str.size());
  char* ptr[64];
  unsigned int size = 1;
  ptr[0] = const_cast<char*>(PACKAGE);

  for (char *p = str.get(); *p;) {
    while (isspace(*p)) *p++ = '\0';
    if (*p == '\0') break;
    ptr[size++] = p;
    if (size == sizeof(ptr)) break;
    while (*p && !isspace(*p)) p++;
  }

  return open(size, ptr, opts);
}

int Param::help_version() const {
  if (get<bool>("help")) {
    std::cout << help();
    return 0;
  }

  if (get<bool>("version")) {
    std::cout << version();
    return 0;
  }

  return 1;
}
}

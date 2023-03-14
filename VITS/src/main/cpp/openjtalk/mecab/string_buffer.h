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

#ifndef MECAB_STRINGBUFFER_H
#define MECAB_STRINGBUFFER_H

#include <string>
#include "common.h"
#include "utils.h"

namespace MeCab {

#define _ITOA(n)  do { char fbuf[64]; itoa(n, fbuf); return this->write(fbuf); } while (0)
#define _UITOA(n) do { char fbuf[64]; uitoa(n, fbuf); return this->write(fbuf);} while (0)
#define _DTOA(n)  do { char fbuf[64]; dtoa(n, fbuf); return this->write(fbuf); } while (0)

class StringBuffer {
 private:
  size_t  size_;
  size_t  alloc_size_;
  char   *ptr_;
  bool    is_delete_;
  bool    error_;
  bool    reserve(size_t);

 public:
  explicit StringBuffer(): size_(0), alloc_size_(0),
                           ptr_(0), is_delete_(true), error_(false) {}
  explicit StringBuffer(char *_s, size_t _l):
      size_(0), alloc_size_(_l), ptr_(_s),
      is_delete_(false), error_(false) {}

  virtual ~StringBuffer();

  StringBuffer& write(char);
  StringBuffer& write(const char*, size_t);
  StringBuffer& write(const char*);
  StringBuffer& operator<<(double n)             { _DTOA(n); }
  StringBuffer& operator<<(short int n)          { _ITOA(n); }
  StringBuffer& operator<<(int n)                { _ITOA(n); }
  StringBuffer& operator<<(long int n)           { _ITOA(n); }
  StringBuffer& operator<<(unsigned short int n) { _UITOA(n); }
  StringBuffer& operator<<(unsigned int n)       { _UITOA(n); }
#ifdef _M_X64 /* for only 64bit compiler of vs2010 */
  StringBuffer& operator<<(size_t n)             { _UITOA(n); }
#endif
  StringBuffer& operator<<(unsigned long int n)  { _UITOA(n); }
#ifdef HAVE_UNSIGNED_LONG_LONG_INT
  StringBuffer& operator<<(unsigned long long int n) { _UITOA(n); }
#endif

  StringBuffer& operator<< (char n) {
    return this->write(n);
  }

  StringBuffer& operator<< (unsigned char n) {
    return this->write(n);
  }

  StringBuffer& operator<< (const char* n) {
    return this->write(n);
  }

  StringBuffer& operator<< (const std::string& n) {
    return this->write(n.c_str());
  }

  void clear() { size_ = 0; }
  const char *str() const {
    return error_ ?  0 : const_cast<const char*>(ptr_);
  }
};
}

#endif

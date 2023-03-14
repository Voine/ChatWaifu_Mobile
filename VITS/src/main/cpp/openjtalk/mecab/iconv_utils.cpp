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

#include <cstring>
#include <fstream>
#include <iostream>
#include <string>
#include "common.h"
#include "scoped_ptr.h"
#include "utils.h"

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "char_property.h"
#include "iconv_utils.h"

/* for Open JTalk
#if defined(_WIN32) && !defined(__CYGWIN__)
*/
#ifdef HAVE_WINDOWS_H /* for Open JTalk */
#include "windows.h"
#endif

namespace {

#ifdef HAVE_ICONV
const char * decode_charset_iconv(const char *str) {
  const int charset = MeCab::decode_charset(str);
  switch (charset) {
    case MeCab::UTF8:
      return "UTF-8";
    case MeCab::EUC_JP:
      return "EUC-JP";
    case  MeCab::CP932:
      return "SHIFT-JIS";
    case  MeCab::UTF16:
      return "UTF-16";
    case  MeCab::UTF16LE:
      return "UTF-16LE";
    case  MeCab::UTF16BE:
      return "UTF-16BE";
    default:
      std::cerr << "charset " << str
                << " is not defined, use " MECAB_DEFAULT_CHARSET;
      return MECAB_DEFAULT_CHARSET;
  }
  return MECAB_DEFAULT_CHARSET;
}
#endif

/* for Open JTalk
#if defined(_WIN32) && !defined(__CYGWIN__)
*/
#ifdef HAVE_WINDOWS_H /* for Open JTalk */
DWORD decode_charset_win32(const char *str) {
  const int charset = MeCab::decode_charset(str);
  switch (charset) {
    case MeCab::UTF8:
      return CP_UTF8;
    case MeCab::UTF16:
      return 1200;
    case MeCab::UTF16LE:
      return 1200;
    case MeCab::UTF16BE:
      return 1201;
    case MeCab::EUC_JP:
      //      return 51932;
      return 20932;
    case MeCab::CP932:
      return 932;
    default:
      std::cerr << "charset " << str
                << " is not defined, use 'CP_THREAD_ACP'";
      return CP_THREAD_ACP;
  }
  return 0;
}
#endif
}  // namespace

namespace MeCab {
bool Iconv::open(const char* from, const char* to) {
  ic_ = 0;
#if defined HAVE_ICONV
  const char *from2 = decode_charset_iconv(from);
  const char *to2 = decode_charset_iconv(to);
  if (std::strcmp(from2, to2) == 0) {
    return true;
  }
  ic_ = 0;
  ic_ = iconv_open(to2, from2);
  if (ic_ == (iconv_t)(-1)) {
    ic_ = 0;
    return false;
  }
#else
/* for Open JTalk
#if defined(_WIN32) && !defined(__CYGWIN__)
*/
#ifdef HAVE_WINDOWS_H /* for Open JTalk */
  from_cp_ = decode_charset_win32(from);
  to_cp_ = decode_charset_win32(to);
  if (from_cp_ == to_cp_) {
    return true;
  }
  ic_ = from_cp_;
#else
/* for Open JTalk
  std::cerr << "iconv_open is not supported" << std::endl;
*/
#endif
#endif

  return true;
}

bool Iconv::convert(std::string *str) {
  if (str->empty()) {
    return true;
  }
  if (ic_ == 0) {
    return true;
  }

#if defined HAVE_ICONV
  size_t ilen = 0;
  size_t olen = 0;
  ilen = str->size();
  olen = ilen * 4;
  std::string tmp;
  tmp.reserve(olen);
  char *ibuf = const_cast<char *>(str->data());
  char *obuf_org = const_cast<char *>(tmp.data());
  char *obuf = obuf_org;
  std::fill(obuf, obuf + olen, 0);
  size_t olen_org = olen;
  iconv(ic_, 0, &ilen, 0, &olen);  // reset iconv state
  while (ilen != 0) {
    if (iconv(ic_, (ICONV_CONST char **)&ibuf, &ilen, &obuf, &olen)
        == (size_t) -1) {
      return false;
    }
  }
  str->assign(obuf_org, olen_org - olen);
#else
/* for Open JTalk
#if defined(_WIN32) && !defined(__CYGWIN__)
*/
#ifdef HAVE_WINDOWS_H /* for Open JTalk */
  // covert it to wide character first
  const size_t wide_len = ::MultiByteToWideChar(from_cp_, 0,
                                                str->c_str(),
                                                -1, NULL, 0);
  if (wide_len == 0) {
    return false;
  }

  scoped_array<wchar_t> wide_str(new wchar_t[wide_len + 1]);

  if (!wide_str.get()) {
    return false;
  };

  if (::MultiByteToWideChar(from_cp_, 0, str->c_str(), -1,
                            wide_str.get(), wide_len + 1) == 0) {
    return false;
  }

  if (to_cp_ == 1200 || to_cp_ == 1201) {
    str->resize(2 * wide_len);
    std::memcpy(const_cast<char *>(str->data()),
                reinterpret_cast<char *>(wide_str.get()), wide_len * 2);
    if (to_cp_ == 1201) {
      char *buf = const_cast<char *>(str->data());
      for (size_t i = 0; i < 2 * wide_len; i += 2) {
        std::swap(buf[i], buf[i+1]);
      }
    }
    return true;
  }

  const size_t output_len = ::WideCharToMultiByte(to_cp_, 0,
                                                  wide_str.get(),
                                                  -1,
                                                  NULL, 0, NULL, NULL);

  if (output_len == 0) {
    return false;
  }

  scoped_array<char> encoded(new char[output_len + 1]);
  if (::WideCharToMultiByte(to_cp_, 0, wide_str.get(), wide_len,
                            encoded.get(), output_len + 1,
                            NULL, NULL) == 0) {
    return false;
  }

  str->assign(encoded.get());

#endif
#endif

  return true;
}

Iconv::Iconv() : ic_(0)  {}

Iconv::~Iconv() {
#if defined HAVE_ICONV
  if (ic_ != 0) iconv_close(ic_);
#endif
}
}

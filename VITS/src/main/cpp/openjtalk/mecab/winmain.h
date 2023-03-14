// MeCab -- Yet Another Part-of-Speech and Morphological Analyzer
//
//  Copyright(C) 2001-2011 Taku Kudo <taku@chasen.org>
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

/* for Open JTalk
#if defined(_WIN32) || defined(__CYGWIN__)
*/
#if defined(_WIN32) && !defined(__CYGWIN__) /* for Open JTalk */

#include <windows.h>
#include <string>

namespace {
class CommandLine {
 public:
  CommandLine(int argc, wchar_t **argv) : argc_(argc), argv_(0) {
    argv_ = new char * [argc_];
    for (int i = 0; i < argc_; ++i) {
      const std::string arg = WideToUtf8(argv[i]);
      argv_[i] = new char[arg.size() + 1];
      ::memcpy(argv_[i], arg.data(), arg.size());
      argv_[i][arg.size()] = '\0';
    }
  }
  ~CommandLine() {
    for (int i = 0; i < argc_; ++i) {
      delete [] argv_[i];
    }
    delete [] argv_;
  }

  int argc() const { return argc_; }
  char **argv() const { return argv_; }

 private:
  static std::string WideToUtf8(const std::wstring &input) {
    const int output_length = ::WideCharToMultiByte(CP_UTF8, 0,
                                                    input.c_str(), -1, NULL, 0,
                                                    NULL, NULL);
    if (output_length == 0) {
      return "";
    }

    char *input_encoded = new char[output_length + 1];
    const int result = ::WideCharToMultiByte(CP_UTF8, 0, input.c_str(), -1,
                                             input_encoded,
                                             output_length + 1, NULL, NULL);
    std::string output;
    if (result > 0) {
      output.assign(input_encoded);
    }
    delete [] input_encoded;
    return output;
  }

  int argc_;
  char **argv_;
};
}  // namespace

#define main(argc, argv) wmain_to_main_wrapper(argc, argv)

int wmain_to_main_wrapper(int argc, char **argv);

#if defined(__MINGW32__)
extern "C"
#endif
int wmain(int argc, wchar_t **argv) {
  CommandLine cmd(argc, argv);
  return wmain_to_main_wrapper(cmd.argc(), cmd.argv());
}
#endif

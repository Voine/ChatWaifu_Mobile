// MeCab -- Yet Another Part-of-Speech and Morphological Analyzer
//
//
//  Copyright(C) 2001-2006 Taku Kudo <taku@chasen.org>
//  Copyright(C) 2004-2006 Nippon Telegraph and Telephone Corporation
#ifndef MECAB_MMAP_H
#define MECAB_MMAP_H
#include <errno.h>
#include <string>
#include "../asset_manager_api/manager.h"

#define HAVE_CONFIG_H 1

// #define HAVE_WINDOWS_H 1

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

extern "C" {

#ifdef HAVE_SYS_TYPES_H
#include <sys/types.h>
#endif

#ifdef HAVE_SYS_STAT_H
#include <sys/stat.h>
#endif

#ifdef HAVE_FCNTL_H
#include <fcntl.h>
#endif

#ifdef HAVE_STRING_H
#include <string.h>
#endif

#if defined(_WIN32) && !defined(__CYGWIN__)
#ifdef HAVE_WINDOWS_H
#include <windows.h>
#endif
#else

#ifdef HAVE_SYS_MMAN_H
#include <sys/mman.h>
#endif

#ifdef HAVE_UNISTD_H
#include <unistd.h>
#endif
#endif
}

#include "common.h"
#include "utils.h"

#ifndef O_BINARY
#define O_BINARY 0
#endif

namespace MeCab {

template <class T> class Mmap {
 private:
  T            *text;
  size_t       length;
  std::string  fileName;
  whatlog what_;
  int    fd;
  int    flag;

 public:
  T&       operator[](size_t n)       { return *(text + n); }
  const T& operator[](size_t n) const { return *(text + n); }
  T*       begin()           { return text; }
  const T* begin()    const  { return text; }
  T*       end()           { return text + size(); }
  const T* end()    const  { return text + size(); }
  size_t size()               { return length/sizeof(T); }
  const char *what()          { return what_.str(); }
  const char *file_name()     { return fileName.c_str(); }
  size_t file_size()          { return length; }
  bool empty()                { return(length == 0); }

  // This code is imported from sufary, develoved by
  //  TATUO Yamashita <yto@nais.to> Thanks!

  bool open(const char *filename, AssetJNI* asjni, const char *mode = "r") {
    fileName = std::string(filename);
    unsigned char * buff = asset_loader(filename, asjni, &fd, &length);
    CHECK_FALSE(fd >= 0) << "open failed: " << filename;
    text = reinterpret_cast<T *>(buff);
    CHECK_FALSE(text) << "MapViewOfFile() failed: " << filename;
    return true;
  }

  void close() {
    if (text) {
      ::munmap(reinterpret_cast<char *>(text), length);
      text = 0;
    }
    text = 0;
  }

  Mmap(): text(0), fd(-1) {}
  virtual ~Mmap() { this->close(); }
};
}
#endif

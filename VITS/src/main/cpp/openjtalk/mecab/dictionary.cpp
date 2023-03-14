//  MeCab -- Yet Another Part-of-Speech and Morphological Analyzer
//
//
//  Copyright(C) 2001-2006 Taku Kudo <taku@chasen.org>
//  Copyright(C) 2004-2006 Nippon Telegraph and Telephone Corporation
#include <fstream>
#include <climits>
#include "connector.h"
#include "context_id.h"
#include "char_property.h"
#include "common.h"
#include "dictionary.h"
#include "dictionary_rewriter.h"
#include "feature_index.h"
#include "iconv_utils.h"
#include "mmap.h"
#include "param.h"
#include "scoped_ptr.h"
#include "utils.h"
#include "writer.h"

#define DIC_VERSION 102

namespace MeCab {
namespace {

const unsigned int DictionaryMagicID = 0xef718f77u;


int progress_bar_darts(size_t current, size_t total) {
  return progress_bar("emitting double-array", current, total);
}

template <typename T1, typename T2>
struct pair_1st_cmp: public std::binary_function<bool, T1, T2> {
  bool operator()(const std::pair<T1, T2> &x1,
                  const std::pair<T1, T2> &x2)  {
    return x1.first < x2.first;
  }
};
}  // namespace

bool Dictionary::open(const char *file, AssetJNI* asjni, const char *mode) {
  close();
  filename_.assign(file);
  CHECK_FALSE(dmmap_->open(file, asjni, mode))
      << "no such file or directory: " << file;

  CHECK_FALSE(dmmap_->size() >= 100)
      << "dictionary file is broken: " << file;

  const char *ptr = dmmap_->begin();

  unsigned int dsize;
  unsigned int tsize;
  unsigned int fsize;
  unsigned int magic;
  unsigned int dummy;

  read_static<unsigned int>(&ptr, magic);
  CHECK_FALSE((magic ^ DictionaryMagicID) == dmmap_->size())
      << "dictionary file is broken: " << file;

  read_static<unsigned int>(&ptr, version_);
  CHECK_FALSE(version_ == DIC_VERSION)
      << "incompatible version: " << version_;

  read_static<unsigned int>(&ptr, type_);
  read_static<unsigned int>(&ptr, lexsize_);
  read_static<unsigned int>(&ptr, lsize_);
  read_static<unsigned int>(&ptr, rsize_);
  read_static<unsigned int>(&ptr, dsize);
  read_static<unsigned int>(&ptr, tsize);
  read_static<unsigned int>(&ptr, fsize);
  read_static<unsigned int>(&ptr, dummy);

  charset_ = ptr;
  ptr += 32;
  da_.set_array(reinterpret_cast<void *>(const_cast<char*>(ptr)));

  ptr += dsize;

  token_ = reinterpret_cast<const Token *>(ptr);
  ptr += tsize;

  feature_ = ptr;
  ptr += fsize;

  CHECK_FALSE(ptr == dmmap_->end())
      << "dictionary file is broken: " << file;

  return true;
}

void Dictionary::close() {
  dmmap_->close();
}

}

aux_source_directory(api api_source)
aux_source_directory(asset_manager_api libassetmanager_source)
aux_source_directory(jpcommon libjpcommon_source)
aux_source_directory(mecab libmecab_source)
aux_source_directory(mecab2njd libmecab2njd_source)
aux_source_directory(njd libnjd_source)
aux_source_directory(njd_set_accent_phrase libnjd_set_accent_phrase_source)
aux_source_directory(njd_set_accent_type libnjd_set_accent_type_source)
aux_source_directory(njd_set_digit libnjd_set_digit_source)
aux_source_directory(njd_set_long_vowel libnjd_set_long_vowel_source)
aux_source_directory(njd_set_pronunciation libnjd_set_pronunciation_source)
aux_source_directory(njd_set_unvoiced_vowel libnjd_set_unvoiced_vowel_source)
aux_source_directory(njd2jpcommon libnjd2jpcommon_source)
aux_source_directory(text2mecab libtext2mecab_source)

add_library(libopenjtalk ${api_source} ${libassetmanager_source}
        ${libmecab_source} ${libjpcommon_source} ${libmecab2njd_source}
        ${libnjd_source} ${libnjd2jpcommon_source}
        ${libnjd_set_accent_phrase_source} ${libnjd_set_accent_type_source}
        ${libnjd_set_digit_source} ${libnjd_set_long_vowel_source}
        ${libnjd_set_pronunciation_source} ${libnjd_set_unvoiced_vowel_source}
        ${libtext2mecab_source})
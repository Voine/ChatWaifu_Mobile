cmake_minimum_required(VERSION 2.6)

# build configuration
project(moereng-vits)
set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn/${ANDROID_ABI}/lib/cmake/ncnn)

find_package(ncnn REQUIRED)
aux_source_directory(mecab_api libmecabapi_source)
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
aux_source_directory(fftpack fft_pack_source)
aux_source_directory(audio_process audio_process_source)
aux_source_directory(vits ncnn_vits_source)


include_directories(${CMAKE_SOURCE_DIR}/asset_manager_api ${CMAKE_SOURCE_DIR}/jpcommon ${CMAKE_SOURCE_DIR}/mecab ${CMAKE_SOURCE_DIR}/mecab2njd
        ${CMAKE_SOURCE_DIR}/njd ${CMAKE_SOURCE_DIR}/njd2jpcommon ${CMAKE_SOURCE_DIR}/njd_set_accent_phrase
        ${CMAKE_SOURCE_DIR}/njd_set_accent_type ${CMAKE_SOURCE_DIR}/njd_set_digit ${CMAKE_SOURCE_DIR}/njd_set_long_vowel
        ${CMAKE_SOURCE_DIR}/njd_set_pronunciation ${CMAKE_SOURCE_DIR}/njd_set_unvoiced_vowel ${CMAKE_SOURCE_DIR}/text2mecab
        ${CMAKE_SOURCE_DIR}/fftpack ${CMAKE_SOURCE_DIR}/audio_process ${CMAKE_SOURCE_DIR}/vits)


add_library(moereng-vits SHARED ${libassetmanager_source} ${libmecab_source} ${libjpcommon_source}  ${libmecab2njd_source}
                      ${libnjd_source} ${libnjd2jpcommon_source}
                      ${libnjd_set_accent_phrase_source} ${libnjd_set_accent_type_source}
                      ${libnjd_set_digit_source} ${libnjd_set_long_vowel_source}
                      ${libnjd_set_pronunciation_source} ${libnjd_set_unvoiced_vowel_source}
                      ${libtext2mecab_source} ${libmecabapi_source} ${fft_pack_source} ${audio_process_source}
                      ${ncnn_vits_source} vitsncnn_jni.cpp)

find_library( # Sets the name of the path variable.
        android-lib
        # Specifies the name of the NDK library that
        # you want CMake to locate.
        android)


target_link_libraries(moereng-vits ncnn ${android-lib} ${log-lib})

# set_target_properties(mecabandroid PROPERTIES VERSION ${MECAB_ANDROID_VERSION})
#set_target_properties(moereng-vits PROPERTIES SO_VERSION 1.0)

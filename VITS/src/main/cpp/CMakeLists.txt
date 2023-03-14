cmake_minimum_required(VERSION 2.6)

project(moereng)
set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn/${ANDROID_ABI}/lib/cmake/ncnn)

find_package(ncnn REQUIRED)

add_subdirectory(openjtalk)
add_subdirectory(fftpack)
add_subdirectory(wave_utils)

aux_source_directory(vits vits_source)
include_directories(${CMAKE_SOURCE_DIR}/vits)

add_library(moereng SHARED ${vits_source} vitsncnn_jni.cpp)

find_library(
        android-lib
        android
)

target_link_libraries(moereng ncnn ${android-lib} ${log-lib} libopenjtalk libfftpack libwaveutils)
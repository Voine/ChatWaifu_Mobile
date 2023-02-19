cmake_minimum_required(VERSION 3.10)

project(chatwaifu-live2d)
# Set app name.
set(APP_NAME chatwaifu-live2d)
# Set directory paths.
set(SDK_ROOT_PATH ${CMAKE_CURRENT_LIST_DIR}/src/SDKRoot)
set(CORE_PATH ${CMAKE_CURRENT_LIST_DIR}/src/SDKRoot/Core)
set(FRAMEWORK_PATH ${SDK_ROOT_PATH}/Framework)
set(THIRD_PARTY_PATH ${SDK_ROOT_PATH}/OpenGL/thirdParty)
set(STB_PATH ${THIRD_PARTY_PATH}/stb)

# Specify version of compiler.
set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Add Cubism Core.
# Import as static library.
add_library(Live2DCubismCore STATIC IMPORTED)
# Find library path.
set_target_properties(Live2DCubismCore
  PROPERTIES
    IMPORTED_LOCATION
      ${CORE_PATH}/lib/android/${ANDROID_ABI}/libLive2DCubismCore.a
    INTERFACE_INCLUDE_DIRECTORIES ${CORE_PATH}/include
)


# Specify Cubism Framework rendering.
set(FRAMEWORK_SOURCE OpenGL)
# Add Cubism Native Framework.
add_subdirectory(${FRAMEWORK_PATH} ${CMAKE_CURRENT_BINARY_DIR}/SDKRoot/Framework)
# Add rendering definition to framework.
target_compile_definitions(Framework PUBLIC CSM_TARGET_ANDROID_ES2)

# Make library for android app.
add_library(${APP_NAME} SHARED)
# Add source files.
add_subdirectory(src/main/cpp)
# Link libraries to app.
target_link_libraries(${APP_NAME}
  Framework
  Live2DCubismCore
  GLESv2
  log
)
# Specify include directories.
target_include_directories(${APP_NAME} PRIVATE ${STB_PATH})

#
# * USE_RENDER_TARGET
#   Renderer draws to target of LAppView.
# * USE_MODEL_RENDER_TARGET
#   Renderer draws to target of each LAppModel.
# * default
#   Renderer draws to default main framebuffer.
#
# INFO: USE_RENDER_TARGET has higher priority than USE_MODEL_RENDER_TARGET.
#
# target_compile_definitions(${APP_NAME}
#   PRIVATE
#     USE_RENDER_TARGET
#     USE_MODEL_RENDER_TARGET
# )

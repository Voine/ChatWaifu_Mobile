target_sources(${LIB_NAME}
  PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/CubismOffscreenSurface_Metal.mm
    ${CMAKE_CURRENT_SOURCE_DIR}/CubismOffscreenSurface_Metal.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/CubismRenderer_Metal.mm
    ${CMAKE_CURRENT_SOURCE_DIR}/CubismRenderer_Metal.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/CubismCommandBuffer_Metal.mm
    ${CMAKE_CURRENT_SOURCE_DIR}/CubismCommandBuffer_Metal.hpp
    ${CMAKE_CURRENT_SOURCE_DIR}/CubismRenderingInstanceSingleton_Metal.m
    ${CMAKE_CURRENT_SOURCE_DIR}/CubismRenderingInstanceSingleton_Metal.h
    ${CMAKE_CURRENT_SOURCE_DIR}/MetalShaderTypes.h
)

set_source_files_properties(
  CubismRenderingInstanceSingleton_Metal.m
  TARGET_DIRECTORY ${LIB_NAME}
  PROPERTIES COMPILE_FLAGS "-fno-objc-arc"
)

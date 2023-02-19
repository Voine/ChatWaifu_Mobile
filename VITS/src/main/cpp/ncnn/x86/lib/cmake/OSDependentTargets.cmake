
        message(WARNING "Using `OSDependentTargets.cmake` is deprecated: use `find_package(glslang)` to find glslang CMake targets.")

        if (NOT TARGET glslang::OSDependent)
            include("${CMAKE_CURRENT_LIST_DIR}/../../lib/cmake/glslang/glslang-targets.cmake")
        endif()

        add_library(OSDependent ALIAS glslang::OSDependent)
    
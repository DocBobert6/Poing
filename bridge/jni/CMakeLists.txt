# Poing! Phase 2 — JNI bridge module.
#
# The bridge is a SEPARATE CMake module from the core: the core's own
# CMakeLists.txt is untouched, and nothing under the core's src/ is
# modified. The bridge links against the prebuilt mgba_libretro.so
# the core build produces (build-android/), so the CI order is:
# build core -> build bridge -> stage both .so files into
# app/src/main/jniLibs/arm64-v8a/ -> Gradle assembles the APK.
#
# Invoked by CI as:
#   cmake -S bridge/jni -B build-bridge \
#     -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
#     -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-21 \
#     -DMGBA_ROOT=$GITHUB_WORKSPACE -DMGBA_SO=$GITHUB_WORKSPACE/$BUILT_SO
cmake_minimum_required(VERSION 3.10)
project(poing-bridge)

if(NOT DEFINED MGBA_ROOT OR NOT DEFINED MGBA_SO)
    message(FATAL_ERROR "MGBA_ROOT and MGBA_SO are required (see bridge/jni/CMakeLists.txt header)")
endif()

# The stock core, as a prebuilt shared library. Only the retro_*
# symbols are exported (src/platform/libretro/link.T), which is all
# the bridge needs.
add_library(mgba_libretro SHARED IMPORTED)
set_target_properties(mgba_libretro PROPERTIES IMPORTED_LOCATION "${MGBA_SO}")

add_library(poing-bridge SHARED bridge.cpp)
target_include_directories(poing-bridge PRIVATE "${MGBA_ROOT}/src/platform/libretro")
target_link_libraries(poing-bridge mgba_libretro log android)

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

# Phase 6 (item 8) — RetroAchievements: rcheevos 12.5.0, vendored
# under deps/rcheevos (MIT), wrapped by ra_client.c. The bridge is
# the only target that needs it; the core is untouched.
set(RCHEEVOS_ROOT "${MGBA_ROOT}/deps/rcheevos")
add_library(poing-bridge SHARED
    bridge.cpp
    ra_client.c
    sensors.c
    ${RCHEEVOS_ROOT}/src/rc_client.c
    ${RCHEEVOS_ROOT}/src/rc_compat.c
    ${RCHEEVOS_ROOT}/src/rc_util.c
    ${RCHEEVOS_ROOT}/src/rc_version.c
    ${RCHEEVOS_ROOT}/src/rcheevos/alloc.c
    ${RCHEEVOS_ROOT}/src/rcheevos/condition.c
    ${RCHEEVOS_ROOT}/src/rcheevos/condset.c
    ${RCHEEVOS_ROOT}/src/rcheevos/consoleinfo.c
    ${RCHEEVOS_ROOT}/src/rcheevos/format.c
    ${RCHEEVOS_ROOT}/src/rcheevos/lboard.c
    ${RCHEEVOS_ROOT}/src/rcheevos/memref.c
    ${RCHEEVOS_ROOT}/src/rcheevos/operand.c
    ${RCHEEVOS_ROOT}/src/rcheevos/rc_validate.c
    ${RCHEEVOS_ROOT}/src/rcheevos/richpresence.c
    ${RCHEEVOS_ROOT}/src/rcheevos/runtime.c
    ${RCHEEVOS_ROOT}/src/rcheevos/runtime_progress.c
    ${RCHEEVOS_ROOT}/src/rcheevos/trigger.c
    ${RCHEEVOS_ROOT}/src/rcheevos/value.c
    ${RCHEEVOS_ROOT}/src/rhash/aes.c
    ${RCHEEVOS_ROOT}/src/rhash/cdreader.c
    ${RCHEEVOS_ROOT}/src/rhash/hash.c
    ${RCHEEVOS_ROOT}/src/rhash/hash_disc.c
    ${RCHEEVOS_ROOT}/src/rhash/hash_encrypted.c
    ${RCHEEVOS_ROOT}/src/rhash/hash_rom.c
    ${RCHEEVOS_ROOT}/src/rhash/hash_zip.c
    ${RCHEEVOS_ROOT}/src/rhash/md5.c
    ${RCHEEVOS_ROOT}/src/rapi/rc_api_common.c
    ${RCHEEVOS_ROOT}/src/rapi/rc_api_editor.c
    ${RCHEEVOS_ROOT}/src/rapi/rc_api_info.c
    ${RCHEEVOS_ROOT}/src/rapi/rc_api_runtime.c
    ${RCHEEVOS_ROOT}/src/rapi/rc_api_user.c)
target_include_directories(poing-bridge PRIVATE
    "${MGBA_ROOT}/src/platform/libretro"
    "${RCHEEVOS_ROOT}/include"
    "${RCHEEVOS_ROOT}/src"
    "${RCHEEVOS_ROOT}/src/rcheevos"
    "${RCHEEVOS_ROOT}/src/rhash"
    "${RCHEEVOS_ROOT}/src/rapi")
# rcheevos needs the hash support compiled in (RC_CLIENT_SUPPORTS_HASH)
# and _GNU_SOURCE for its platform layer; it is C99.
target_compile_definitions(poing-bridge PRIVATE RC_CLIENT_SUPPORTS_HASH _GNU_SOURCE)
set_source_files_properties(ra_client.c
    sensors.c
    ${RCHEEVOS_ROOT}/src/rc_client.c
    ${RCHEEVOS_ROOT}/src/rc_compat.c
    ${RCHEEVOS_ROOT}/src/rc_util.c
    ${RCHEEVOS_ROOT}/src/rc_version.c
    ${RCHEEVOS_ROOT}/src/rcheevos/alloc.c
    ${RCHEEVOS_ROOT}/src/rcheevos/condition.c
    ${RCHEEVOS_ROOT}/src/rcheevos/condset.c
    ${RCHEEVOS_ROOT}/src/rcheevos/consoleinfo.c
    ${RCHEEVOS_ROOT}/src/rcheevos/format.c
    ${RCHEEVOS_ROOT}/src/rcheevos/lboard.c
    ${RCHEEVOS_ROOT}/src/rcheevos/memref.c
    ${RCHEEVOS_ROOT}/src/rcheevos/operand.c
    ${RCHEEVOS_ROOT}/src/rcheevos/rc_validate.c
    ${RCHEEVOS_ROOT}/src/rcheevos/richpresence.c
    ${RCHEEVOS_ROOT}/src/rcheevos/runtime.c
    ${RCHEEVOS_ROOT}/src/rcheevos/runtime_progress.c
    ${RCHEEVOS_ROOT}/src/rcheevos/trigger.c
    ${RCHEEVOS_ROOT}/src/rcheevos/value.c
    ${RCHEEVOS_ROOT}/src/rhash/aes.c
    ${RCHEEVOS_ROOT}/src/rhash/cdreader.c
    ${RCHEEVOS_ROOT}/src/rhash/hash.c
    ${RCHEEVOS_ROOT}/src/rhash/hash_disc.c
    ${RCHEEVOS_ROOT}/src/rhash/hash_encrypted.c
    ${RCHEEVOS_ROOT}/src/rhash/hash_rom.c
    ${RCHEEVOS_ROOT}/src/rhash/hash_zip.c
    ${RCHEEVOS_ROOT}/src/rhash/md5.c
    ${RCHEEVOS_ROOT}/src/rapi/rc_api_common.c
    ${RCHEEVOS_ROOT}/src/rapi/rc_api_editor.c
    ${RCHEEVOS_ROOT}/src/rapi/rc_api_info.c
    ${RCHEEVOS_ROOT}/src/rapi/rc_api_runtime.c
    ${RCHEEVOS_ROOT}/src/rapi/rc_api_user.c
    PROPERTIES COMPILE_FLAGS "-std=c99")
target_link_libraries(poing-bridge mgba_libretro log android)

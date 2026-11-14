/* Host-only stand-in for the NDK's <android/log.h>, for the
 * bridge host tests (bridge/tests/).
 *
 * NOT the NDK header: the real one pulls in Bionic-only
 * headers (<sys/cdefs.h> etc.) that do not compile under host
 * gcc. This provides only what bridge.cpp uses — the log
 * priority values and the __android_log_print declaration —
 * with the same values as the NDK header. The test stubs the
 * function itself (see video_test.cpp). Never include this
 * from device code.
 */

#pragma once

#include <stdarg.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum android_LogPriority {
    ANDROID_LOG_UNKNOWN = 0,
    ANDROID_LOG_DEFAULT,
    ANDROID_LOG_VERBOSE,
    ANDROID_LOG_DEBUG,
    ANDROID_LOG_INFO,
    ANDROID_LOG_WARN,
    ANDROID_LOG_ERROR,
    ANDROID_LOG_FATAL,
    ANDROID_LOG_SILENT,
} android_LogPriority;

int __android_log_print(int prio, const char *tag, const char *fmt, ...);

#ifdef __cplusplus
}
#endif

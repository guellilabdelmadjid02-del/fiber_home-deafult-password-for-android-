#include <jni.h>
#include <string>
#include <cstring>
#include "lib.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_fiberhome_MainActivity_generatePassword(
        JNIEnv* env,
        jobject /* this */,
        jstring ssid) {

    const char *nativeSsid = env->GetStringUTFChars(ssid, 0);
    if (nativeSsid == nullptr) return nullptr;

    // The logic expects the part after FH_
    // Example: FH_123456 -> 123456
    const char *start = strstr(nativeSsid, "_");
    if (start != nullptr) {
        start++; // Move past the underscore
    } else {
        start = nativeSsid; // Fallback to full string if no underscore found
    }

    size_t len = strlen(start);
    char *pass = (char *)malloc(len + 5); // "wlan" + password + null
    strcpy(pass, "wlan");

    for (size_t i = 0; i < len; i++) {
        char mapped = map_char(start[i]);
        if (mapped == 0) {
            // Invalid character for mapping
            env->ReleaseStringUTFChars(ssid, nativeSsid);
            free(pass);
            return env->NewStringUTF("Invalid SSID format");
        }
        pass[4 + i] = mapped;
    }
    pass[4 + len] = '\0';

    jstring result = env->NewStringUTF(pass);

    env->ReleaseStringUTFChars(ssid, nativeSsid);
    free(pass);

    return result;
}

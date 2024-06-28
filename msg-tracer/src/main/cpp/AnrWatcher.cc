#include <string>
#include <optional>
#include "managed_jnienv.h"
#include "AnrWatcher.h"
#include "AnrDumper.h"

#define JAVA_CLASS "indie/riki/msgtracer/AnrWatcher"

using namespace MsgTracer;
using namespace std;

static std::optional<AnrDumper> sAnrDumper;

static struct StacktraceJNI {
    jclass AnrDetective;
    jmethodID AnrDetector_onANRDumped;
} gJ;

static void startWatchAnrSignal(JNIEnv *env, jobject thiz) {
    sAnrDumper.emplace();
}

static void stopWatchAnrSignal(JNIEnv *env, jobject thiz) {
    sAnrDumper.reset();
}

static JNINativeMethod methods[] = {
        {"startWatchAnrSignal", "()V", (void *) startWatchAnrSignal},
        {"stopWatchAnrSignal",  "()V", (void *) stopWatchAnrSignal}
};

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JniInvocation::init(vm);

    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass(JAVA_CLASS);
    if (clazz == nullptr) {
        return JNI_ERR;
    }

    gJ.AnrDetective = static_cast<jclass>(env->NewGlobalRef(clazz));
    gJ.AnrDetector_onANRDumped = env->GetStaticMethodID(clazz, "onANRDumped", "()V");

    jint result = env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0]));
    if (result < 0) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}

bool anrDumpCallback() {
    JNIEnv *env = JniInvocation::getEnv();
    if (!env) return false;
    env->CallStaticVoidMethod(gJ.AnrDetective, gJ.AnrDetector_onANRDumped);
    return true;
}
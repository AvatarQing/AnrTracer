#ifndef EAGLEAPM_MANAGED_JNIENV_H
#define EAGLEAPM_MANAGED_JNIENV_H

#include <jni.h>

namespace JniInvocation {
    void init(JavaVM *vm);
    JavaVM *getJavaVM();
    JNIEnv *getEnv();
}

#endif


#include "AnrDumper.h"
#include "AnrWatcher.h"
#include "Support.h"
#include <string>
#include <unistd.h>
#include <dirent.h>
#include <syscall.h>
#include <fcntl.h>
#include <cinttypes>
#include <android/log.h>

#define SIGNAL_CATCHER_THREAD_NAME "Signal Catcher"
#define SIGNAL_CATCHER_THREAD_SIGBLK 0x1000

namespace MsgTracer {
    static sigset_t old_sigSet;

    AnrDumper::AnrDumper() {
        __android_log_print(ANDROID_LOG_INFO, "AnrDumper", "AnrDumper构造");

        sigset_t sigSet;
        sigemptyset(&sigSet);
        sigaddset(&sigSet, SIGQUIT);
        pthread_sigmask(SIG_UNBLOCK, &sigSet, &old_sigSet);
    }

    AnrDumper::~AnrDumper() {
        pthread_sigmask(SIG_SETMASK, &old_sigSet, nullptr);
    }

    static int getSignalCatcherThreadId() {
        char taskDirPath[128];
        DIR *taskDir;
        long long sigblk;
        int signalCatcherTid = -1;
        int firstSignalCatcherTid = -1;

        snprintf(taskDirPath, sizeof(taskDirPath), "/proc/%d/task", getpid());
        if ((taskDir = opendir(taskDirPath)) == nullptr) {
            return -1;
        }
        struct dirent *dent;
        pid_t tid;
        while ((dent = readdir(taskDir)) != nullptr) {
            tid = atoi(dent->d_name);
            if (tid <= 0) {
                continue;
            }

            char threadName[1024];
            char commFilePath[1024];
            snprintf(commFilePath, sizeof(commFilePath), "/proc/%d/task/%d/comm", getpid(), tid);

            Support::readFileAsString(commFilePath, threadName, sizeof(threadName));

            if (strncmp(SIGNAL_CATCHER_THREAD_NAME, threadName, sizeof(SIGNAL_CATCHER_THREAD_NAME) - 1) != 0) {
                continue;
            }

            if (firstSignalCatcherTid == -1) {
                firstSignalCatcherTid = tid;
            }

            sigblk = 0;
            char taskPath[128];
            snprintf(taskPath, sizeof(taskPath), "/proc/%d/status", tid);

            ScopedFileDescriptor fd(open(taskPath, O_RDONLY, 0));
            LineReader lr(fd.get());
            const char *line;
            size_t len;
            while (lr.getNextLine(&line, &len)) {
                if (1 == sscanf(line, "SigBlk: %" SCNx64, &sigblk)) {
                    break;
                }
                lr.popLine(len);
            }
            if (SIGNAL_CATCHER_THREAD_SIGBLK != sigblk) {
                continue;
            }
            signalCatcherTid = tid;
            break;
        }
        closedir(taskDir);

        if (signalCatcherTid == -1) {
            signalCatcherTid = firstSignalCatcherTid;
        }
        return signalCatcherTid;
    }

    static void sendSigToSignalCatcher() {
        int tid = getSignalCatcherThreadId();
        syscall(SYS_tgkill, getpid(), tid, SIGQUIT);
    }

    static void *anrCallback(void *arg) {
        anrDumpCallback();
        sendSigToSignalCatcher();
        return nullptr;
    }

    static void *siUserCallback(void *arg) {
        sendSigToSignalCatcher();
        return nullptr;
    }

    void AnrDumper::handleSignal(int sig, const siginfo_t *info, void *uc) {
        __android_log_print(ANDROID_LOG_INFO, "AnrDumper", "收到了SIGQUIT信号");

        int fromPid1 = info->_si_pad[3];
        int fromPid2 = info->_si_pad[4];
        int myPid = getpid();
        bool fromMySelf = fromPid1 == myPid || fromPid2 == myPid;
        if (sig == SIGQUIT) {
            pthread_t thd;
            if (!fromMySelf) {
                pthread_create(&thd, nullptr, anrCallback, nullptr);
            } else {
                pthread_create(&thd, nullptr, siUserCallback, nullptr);
            }
            pthread_detach(thd);
        }
    }
}
#include "SignalHandler.h"

#include <malloc.h>
#include <syscall.h>
#include <dirent.h>
#include <unistd.h>
#include <android/log.h>

#include <mutex>
#include <vector>
#include <algorithm>
#include <cinttypes>

#define SIGNAL_CATCHER_THREAD_NAME "Signal Catcher"
#define SIGNAL_CATCHER_THREAD_SIGBLK 0x1000

namespace MsgTracer {

    struct sigaction sOldHandlers;
    static bool sHandlerInstalled = false;
    static std::vector<SignalHandler *> *sHandlerStack = nullptr;
    static std::mutex sHandlerStackMutex;

    SignalHandler::SignalHandler() {
        std::lock_guard<std::mutex> lock(sHandlerStackMutex);
        if (!sHandlerStack) sHandlerStack = new std::vector<SignalHandler *>;
        installHandlersLocked();
        sHandlerStack->push_back(this);
    }

    SignalHandler::~SignalHandler() {
        std::lock_guard<std::mutex> lock(sHandlerStackMutex);
        auto it = std::find(sHandlerStack->begin(), sHandlerStack->end(), this);
        sHandlerStack->erase(it);
        if (sHandlerStack->empty()) {
            delete sHandlerStack;
            sHandlerStack = nullptr;
            restoreHandlersLocked();
        }
    }

    bool SignalHandler::installHandlersLocked() {
        if (sigaction(TARGET_SIG, nullptr, &sOldHandlers) == -1) return false;

        struct sigaction sa{};
        sa.sa_sigaction = signalHandler;
        sa.sa_flags = SA_ONSTACK | SA_SIGINFO | SA_RESTART;

        if (sigaction(TARGET_SIG, &sa, nullptr) == -1) return false;

        sHandlerInstalled = true;
        return true;
    }

    void SignalHandler::restoreHandlersLocked() {
        if (!sHandlerInstalled) return;
        if (sigaction(TARGET_SIG, &sOldHandlers, nullptr) == -1) {
            installDefaultHandler(TARGET_SIG);
        }
        sHandlerInstalled = false;
    }

    void SignalHandler::installDefaultHandler(int sig) {
        struct sigaction sa{};
        memset(&sa, 0, sizeof(sa));
        sigemptyset(&sa.sa_mask);
        sa.sa_handler = SIG_DFL;
        sa.sa_flags = SA_RESTART;
        sigaction(sig, &sa, nullptr);
    }

    void SignalHandler::signalHandler(int sig, siginfo_t *info, void *uc) {
        std::unique_lock<std::mutex> lock(sHandlerStackMutex);
        for (auto it = sHandlerStack->rbegin(); it != sHandlerStack->rend(); ++it) {
            (*it)->handleSignal(sig, info, uc);
        }
        lock.unlock();
    }
}
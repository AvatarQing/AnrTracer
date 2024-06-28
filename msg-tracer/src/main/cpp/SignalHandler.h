#ifndef MSGTRACER_SIGNALHANDLER_H
#define MSGTRACER_SIGNALHANDLER_H

#include <signal.h>

namespace MsgTracer {

    class SignalHandler {
    public:
        SignalHandler();

        virtual ~SignalHandler();

    protected:
        static const int TARGET_SIG = SIGQUIT;

        virtual void handleSignal(int sig, const siginfo_t *info, void *uc) = 0;

        static bool installHandlersLocked();

        static void restoreHandlersLocked();

        static void installDefaultHandler(int sig);

    private:
        static void signalHandler(int sig, siginfo_t *info, void *uc);

        SignalHandler(const SignalHandler &) = delete;

        SignalHandler &operator=(const SignalHandler &) = delete;
    };
}

#endif

//
// Created by LENOVO on 2024/2/20.
//

#ifndef EAGLEAPM_ANRDUMPER_H
#define EAGLEAPM_ANRDUMPER_H

#include "SignalHandler.h"

namespace MsgTracer {
    class AnrDumper : public SignalHandler {
    public:
        AnrDumper();

        ~AnrDumper();

    private:
        void handleSignal(int sig, const siginfo_t *info, void *uc) final;
    };
}

#endif //EAGLEAPM_ANRDUMPER_H

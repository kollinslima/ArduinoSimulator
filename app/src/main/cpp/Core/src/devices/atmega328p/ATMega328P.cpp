//
// Created by kollins on 12/09/20.
//

#include <unistd.h>
#include "../../../include/devices/atmega328p/ATMega328P.h"
#include "../../../include/SofiaCoreController.h"
#include "../../../include/parsers/IntelParser.h"
#include "../../../include/devices/components/avr/cpu/AVRCPU_AVRe.h"

#define SOFIA_ATMEGA328P_TAG "SOFIA ATMEGA328P"

#define DEFAULT_CLOCK_FREQ 16000000
#define CPU_MODULE_INDEX 0

ATMega328P::ATMega328P(SofiaNotifier *notifier) {
    this->notifier = notifier;

    programMemory = new ProgramMemory_ATMega328P();
    dataMemory = new DataMemory_ATMega328P(notifier);

    cpu = new AVRCPU_AVRe(programMemory, dataMemory);

    //Cofigure CPU to ATMega328P
    //ATmega328P has a 14-bits PC, but the behavior is the same as a 16-bits for the CPU
    cpu->setPCSize(AVRCPU::PCBits::PC16);
    cpu->setIOBaseAddr(0x0020);

    clockFreq = DEFAULT_CLOCK_FREQ;
    isRunning = false;
}

ATMega328P::~ATMega328P() {
    isRunning = false;
    for (auto &i : scheduler) {
        i.join();
    }
    syncThread.join();

    delete cpu;
    cpu = nullptr;

    delete dataMemory;
    dataMemory = nullptr;

    delete programMemory;
    programMemory = nullptr;
}

void ATMega328P::start() {
    isRunning = true;
    for (unsigned int & i : syncCounter) {
        i = clockFreq;
    }
    scheduler[CPU_MODULE_INDEX] = thread(&ATMega328P::cpuThread, this);
//    for (int i = 0; i < NUM_MODULES; ++i) {
//        scheduler[i] = thread(&ATMega328P::stubThread, this, i);
//    }
    syncThread = thread(&ATMega328P::syncronizationThread, this);
}

void ATMega328P::stop() {
    isRunning = false;
    for (auto &i : scheduler) {
        i.join();
    }
    syncThread.join();
}

void ATMega328P::load(int fd) {
    switch (programMemory->loadFile(fd)) {
        case INTEL_CHECKSUM_ERROR:
            notifier->addNotification(CHECKSUM_ERROR_LISTENER);
            break;
        case INTEL_INVALID_FILE:
            notifier->addNotification(INVALID_FILE_LISTENER);
            break;
        case INTEL_FILE_OPEN_FAILED:
            notifier->addNotification(FILE_OPEN_FAIL_LISTENER);
            break;
        default:
            notifier->addNotification(LOAD_SUCCESS_LISTENER);
            break;
    }
}

void ATMega328P::cpuThread() {
    while (isRunning) {
        cpu->run();
        syncCounter[CPU_MODULE_INDEX]--;
        while (!syncCounter[CPU_MODULE_INDEX]) {usleep(1000);}
    }
}

//void ATMega328P::stubThread(int index) {
//    AVRCPU stubCPU(programMemory);
//    while (isRunning) {
//        stubCPU.start();
//        syncCounter[index]--;
//        while (!syncCounter[index]) {usleep(1000);}
//    }
//}

void ATMega328P::syncronizationThread() {
    unsigned int finishCondition[NUM_MODULES];
    unsigned int initialCondition[NUM_MODULES];

    for (unsigned int &i : initialCondition) {
        i = clockFreq;
    }
    memset(finishCondition, 0, sizeof(finishCondition));

    while (isRunning) {
        while (memcmp(syncCounter, finishCondition, sizeof(syncCounter)) != 0) {usleep(1000);}
        notifier->addNotification(TIME_UPDATE_LISTENER);
        memcpy(syncCounter, initialCondition, sizeof(syncCounter));
    }
}


//
// Created by kollins on 18/04/21.
//

#ifndef PROJECTSOFIA_TIMER2_ATMEGA328P_H
#define PROJECTSOFIA_TIMER2_ATMEGA328P_H

#include "Timer_ATMega328P.h"

class Timer2_ATMega328P : public Timer_ATMega328P {

public:
    Timer2_ATMega328P(DataMemory_ATMega328P& dataMemory);
    virtual ~Timer2_ATMega328P() {}

private:
    bool clockSource_011();  //Prescaler 32
    bool clockSource_100();  //Prescaler 64
    bool clockSource_101();  //Prescaler 128
    bool clockSource_110();  //Prescaler 256
    bool clockSource_111();  //Prescaler 1024

    void prepare();
    void operate();
    void writeBack();

    void normal();
    void pwmPhaseCorrect2();
    void ctc1();
    void fastPWM2();
    void pwmPhaseAndFreqCorrect1();
    void pwmPhaseCorrect4();
    void ctc2();
};

#endif //PROJECTSOFIA_TIMER2_ATMEGA328P_H
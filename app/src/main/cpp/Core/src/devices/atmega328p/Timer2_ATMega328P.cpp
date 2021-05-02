//
// Created by kollins on 18/04/21.
//

#include "../../../include/devices/atmega328p/Timer2_ATMega328P.h"

#define OC2A_MASK           0x08
#define OC2B_MASK           0x08

#define TOP                 0xFF
#define BOTTOM              0x00

Timer2_ATMega328P::Timer2_ATMega328P(DataMemory_ATMega328P &dataMemory) :
        Timer_ATMega328P(dataMemory) {

    endOfScale = TOP;
    bottom = BOTTOM;
    outARegAddr = PORTD_ADDR;
    outBRegAddr = PORTB_ADDR;
    tccrxAAddr = TCCR2A_ADDR;
    tccrxBAddr = TCCR2B_ADDR;
    ocxaMask = OC2A_MASK;
    ocxbMask = OC2B_MASK;
}

void Timer2_ATMega328P::prepare() {
    interrFlags = 0x00;
    matchA = tccrxbReg & FOCXA_MASK;
    matchB = tccrxbReg & FOCXB_MASK;

    progress = datMem.buffer[TCNT2_ADDR];
}

void Timer2_ATMega328P::operate() {
    (this->*mode[(tccrxbReg & WGM_TCCR2B_MASK) | ((tccrxaReg << 1) & WGM_TCCR2A_MASK)])();
}

void Timer2_ATMega328P::writeBack() {
    datMem.buffer[TIFR2_ADDR] = interrFlags;
    datMem.buffer[TCNT2_ADDR] = progress;
}

bool Timer2_ATMega328P::clockSource_011() {
    //Prescaler 32
    return !((++clockCount) & PRESCALER_32_MASK);
}

bool Timer2_ATMega328P::clockSource_100() {
    //Prescaler 64
    return !((++clockCount) & PRESCALER_64_MASK);
}

bool Timer2_ATMega328P::clockSource_101() {
    //Prescaler 128
    return !((++clockCount) & PRESCALER_128_MASK);
}

bool Timer2_ATMega328P::clockSource_110() {
    //Prescaler 256
    return !((++clockCount) & PRESCALER_256_MASK);
}

bool Timer2_ATMega328P::clockSource_111() {
    //Prescaler 1024
    return !((++clockCount) & PRESCALER_1024_MASK);
}

void Timer2_ATMega328P::normal() {
    ocrxa = datMem.buffer[OCR2A_ADDR];
    ocrxb = datMem.buffer[OCR2B_ADDR];

    Timer_ATMega328P::normal();
}

void Timer2_ATMega328P::pwmPhaseCorrect2() {
    if (progress == TOP) {
        //Update double buffer on TOP
        datMem.buffer[OCR2A_ADDR] = datMem.doubleBuffer[DOUBLE_BUFFER_OCR2A];
        datMem.buffer[OCR2B_ADDR] = datMem.doubleBuffer[DOUBLE_BUFFER_OCR2B];
    }
    ocrxa = datMem.buffer[OCR2A_ADDR];
    ocrxb = datMem.buffer[OCR2B_ADDR];

    Timer_ATMega328P::pwmPhaseCorrect2();
}

void Timer2_ATMega328P::ctc1() {
    ocrxa = datMem.buffer[OCR2A_ADDR];
    ocrxb = datMem.buffer[OCR2B_ADDR];

    Timer_ATMega328P::ctc1();
}

void Timer2_ATMega328P::fastPWM2() {
    if (progress == TOP) {
        //Update double buffer on TOP
        datMem.buffer[OCR2A_ADDR] = datMem.doubleBuffer[DOUBLE_BUFFER_OCR2A];
        datMem.buffer[OCR2B_ADDR] = datMem.doubleBuffer[DOUBLE_BUFFER_OCR2B];
    }
    ocrxa = datMem.buffer[OCR2A_ADDR];
    ocrxb = datMem.buffer[OCR2B_ADDR];

    Timer_ATMega328P::fastPWM2();
}

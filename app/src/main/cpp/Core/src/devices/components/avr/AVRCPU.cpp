//
// Created by kollins on 19/09/20.
//

#include <cstdlib>
#include "../../../../include/devices/components/avr/AVRCPU.h"
#include "../../../../include/CommonCore.h"
#include "../../../../include/devices/components/avr/GenericAVRDataMemory.h"

#define INSTRUCTION_ADC_MASK  0x1C00
#define INSTRUCTION_ADD_MASK  1
#define INSTRUCTION_ADIW_MASK  2
#define INSTRUCTION_AND_MASK  3
#define INSTRUCTION_ANDI_MASK  4
#define INSTRUCTION_ASR_MASK  5
#define INSTRUCTION_BCLR_MASK  6
#define INSTRUCTION_BLD_MASK  7
#define INSTRUCTION_BRBC_MASK  8
#define INSTRUCTION_BRBS_MASK  9
#define INSTRUCTION_BREAK_MASK  10
#define INSTRUCTION_BSET_MASK  11
#define INSTRUCTION_BST_MASK  12
#define INSTRUCTION_CALL_MASK  13
#define INSTRUCTION_CBI_MASK  14
#define INSTRUCTION_COM_MASK  15
#define INSTRUCTION_CP_MASK  16
#define INSTRUCTION_CPC_MASK  17
#define INSTRUCTION_CPI_MASK  18
#define INSTRUCTION_CPSE_MASK  19
#define INSTRUCTION_DEC_MASK  20
#define INSTRUCTION_EOR_MASK  21
#define INSTRUCTION_FMUL_MASK  22
#define INSTRUCTION_FMULS_MASK  23
#define INSTRUCTION_FMULSU_MASK  24
#define INSTRUCTION_ICALL_MASK  25
#define INSTRUCTION_IJMP_MASK  26
#define INSTRUCTION_IN_MASK  27
#define INSTRUCTION_INC_MASK  28
#define INSTRUCTION_JMP_MASK  29
#define INSTRUCTION_LD_X_POST_INCREMENT_MASK  30
#define INSTRUCTION_LD_X_PRE_INCREMENT_MASK  31
#define INSTRUCTION_LD_X_UNCHANGED_MASK  32
#define INSTRUCTION_LD_Y_POST_INCREMENT_MASK  33
#define INSTRUCTION_LD_Y_PRE_INCREMENT_MASK  34
#define INSTRUCTION_LD_Y_UNCHANGED_MASK  35
#define INSTRUCTION_LD_Z_POST_INCREMENT_MASK  36
#define INSTRUCTION_LD_Z_PRE_INCREMENT_MASK  37
#define INSTRUCTION_LD_Z_UNCHANGED_MASK  38
#define INSTRUCTION_LDD_Y_MASK  39
#define INSTRUCTION_LDD_Z_MASK  40
#define INSTRUCTION_LDI_MASK  41 //LDI - SER
#define INSTRUCTION_LDS_MASK  42
#define INSTRUCTION_LPM_Z_POST_INCREMENT_MASK  43
#define INSTRUCTION_LPM_Z_UNCHANGED_DEST_R0_MASK  44
#define INSTRUCTION_LPM_Z_UNCHANGED_MASK  45
#define INSTRUCTION_LSR_MASK  46
#define INSTRUCTION_MOV_MASK  47
#define INSTRUCTION_MOVW_MASK  48
#define INSTRUCTION_MUL_MASK  49
#define INSTRUCTION_MULS_MASK  50
#define INSTRUCTION_MULSU_MASK  51
#define INSTRUCTION_NEG_MASK  52
#define INSTRUCTION_NOP_MASK  53
#define INSTRUCTION_OR_MASK  54
#define INSTRUCTION_ORI_MASK  55
#define INSTRUCTION_OUT_MASK  56
#define INSTRUCTION_POP_MASK  57
#define INSTRUCTION_PUSH_MASK  58
#define INSTRUCTION_RCALL_MASK  59
#define INSTRUCTION_RET_MASK  60
#define INSTRUCTION_RETI_MASK  61
#define INSTRUCTION_RJMP_MASK  62
#define INSTRUCTION_ROR_MASK  63
#define INSTRUCTION_SBC_MASK  64
#define INSTRUCTION_SBCI_MASK  65
#define INSTRUCTION_SBI_MASK  66
#define INSTRUCTION_SBIC_MASK  67
#define INSTRUCTION_SBIS_MASK  68
#define INSTRUCTION_SBIW_MASK  69
#define INSTRUCTION_SBRC_MASK  70
#define INSTRUCTION_SBRS_MASK  71
#define INSTRUCTION_SLEEP_MASK  72
#define INSTRUCTION_SPM_MASK  73
#define INSTRUCTION_ST_X_POST_INCREMENT_MASK  74
#define INSTRUCTION_ST_X_PRE_INCREMENT_MASK  75
#define INSTRUCTION_ST_X_UNCHANGED_MASK  76
#define INSTRUCTION_ST_Y_POST_INCREMENT_MASK  77
#define INSTRUCTION_ST_Y_PRE_INCREMENT_MASK  78
#define INSTRUCTION_ST_Y_UNCHANGED_MASK  79
#define INSTRUCTION_ST_Z_POST_INCREMENT_MASK  80
#define INSTRUCTION_ST_Z_PRE_INCREMENT_MASK  81
#define INSTRUCTION_ST_Z_UNCHANGED_MASK  82
#define INSTRUCTION_STD_Y_MASK  83
#define INSTRUCTION_STD_Z_MASK  84
#define INSTRUCTION_STS_MASK  85
#define INSTRUCTION_SUB_MASK  86
#define INSTRUCTION_SUBI_MASK  87
#define INSTRUCTION_SWAP_MASK  88
#define INSTRUCTION_WDR_MASK  89

#define H_FLAG_MASK 0x20
#define S_FLAG_MASK 0x10
#define V_FLAG_MASK 0x08
#define N_FLAG_MASK 0x04
#define Z_FLAG_MASK 0x02
#define C_FLAG_MASK 0x01

#define SOFIA_AVRCPU_TAG "SOFIA AVRCPU CONTROLLER"

AVRCPU::AVRCPU(GenericProgramMemory *programMemory, GenericAVRDataMemory *dataMemory) {
    pc = 0;
    progMem = programMemory;
    datMem = dataMemory;
    sregAddr = datMem->getSREGAddres();
    setupInstructionDecoder();
}

AVRCPU::~AVRCPU() {
}

void AVRCPU::setupInstructionDecoder() {
    for (int i = 0; i < INSTRUCTION_DECODER_SIZE; ++i) {
        if (!((i & INSTRUCTION_ADC_MASK) ^ INSTRUCTION_ADC_MASK)) {
            instructionDecoder[i] = &AVRCPU::instructionADC;
            continue;
        }
        instructionDecoder[i] = &AVRCPU::unknownInstruction;
    }
}

void AVRCPU::run() {
    progMem->loadInstruction(pc++, &instruction);
    (this->*instructionDecoder[instruction])();
    pc = 0;
}

void AVRCPU::instructionADC() {
    /*************************ADC***********************/
    LOGD(SOFIA_AVRCPU_TAG, "Instruction ADC");

    wbAddr = (0x01F0 & instruction) >> 4;

    datMem->read(wbAddr, &regD);
    datMem->read(((0x0200 & instruction) >> 5) | (0x000F & instruction), &regR);
    datMem->read(sregAddr, &sreg);

    result = regD + regR + (sreg & 0x01);
    sreg = 0;

    sbyte regD_AND_regR = regD & regR;
    sbyte NOT_result = ~result;

    sbyte HC = regD_AND_regR | (regR&NOT_result) | (NOT_result&regD);

    //Flag H
    sreg |= (HC<<2)&H_FLAG_MASK;

    //Flag V
    sreg |= (((regD_AND_regR & result) | ((~regD) & (~regR) & result) )>>4)&V_FLAG_MASK;

    //Flag N
    sreg |= (result>>5)&N_FLAG_MASK;

    //Flag S
    sreg |= (((sreg<<1)^sreg)<<1)&S_FLAG_MASK;

    //Flag Z
    sreg |= (((((((((((((NOT_result>>1)&NOT_result)>>1)&NOT_result)>>1)&NOT_result)>>1)&NOT_result)>>1)&NOT_result)>>1)&NOT_result)&(NOT_result<<1))&Z_FLAG_MASK;

    //Flag C
    sreg |= (HC>>7)&C_FLAG_MASK;

    datMem->write(wbAddr, &result);
    datMem->write(sregAddr, &sreg);

}

void AVRCPU::instructionADD() {

}

void AVRCPU::instructionADIW() {

}

void AVRCPU::instructionAND() {

}

void AVRCPU::instructionANDI() {

}

void AVRCPU::instructionASR() {

}

void AVRCPU::instructionBCLR() {

}

void AVRCPU::instructionBLD() {

}

void AVRCPU::instructionBRBC() {

}

void AVRCPU::instructionBRBS() {

}

void AVRCPU::instructionBREAK() {

}

void AVRCPU::instructionBSET() {

}

void AVRCPU::instructionBST() {

}

void AVRCPU::instructionCALL() {

}

void AVRCPU::instructionCBI() {

}

void AVRCPU::instructionCOM() {

}

void AVRCPU::instructionCP() {

}

void AVRCPU::instructionCPC() {

}

void AVRCPU::instructionCPI() {

}

void AVRCPU::instructionCPSE() {

}

void AVRCPU::instructionDEC() {

}

void AVRCPU::instructionEOR() {

}

void AVRCPU::instructionFMUL() {

}

void AVRCPU::instructionFMULS() {

}

void AVRCPU::instructionFMULSU() {

}

void AVRCPU::instructionICALL() {

}

void AVRCPU::instructionIJMP() {

}

void AVRCPU::instructionIN() {

}

void AVRCPU::instructionINC() {

}

void AVRCPU::instructionJMP() {

}

void AVRCPU::instructionLD_X_POST_INCREMENT() {

}

void AVRCPU::instructionLD_X_PRE_INCREMENT() {

}

void AVRCPU::instructionLD_X_UNCHANGED() {

}

void AVRCPU::instructionLD_Y_POST_INCREMENT() {

}

void AVRCPU::instructionLD_Y_PRE_INCREMENT() {

}

void AVRCPU::instructionLD_Y_UNCHANGED() {

}

void AVRCPU::instructionLD_Z_POST_INCREMENT() {

}

void AVRCPU::instructionLD_Z_PRE_INCREMENT() {

}

void AVRCPU::instructionLD_Z_UNCHANGED() {

}

void AVRCPU::instructionLDD_Y() {

}

void AVRCPU::instructionLDD_Z() {

}

void AVRCPU::instructionLDI() {

}

void AVRCPU::instructionLDS() {

}

void AVRCPU::instructionLPM_Z_POST_INCREMENT() {

}

void AVRCPU::instructionLPM_Z_UNCHANGED_DEST_R() {

}

void AVRCPU::instructionLPM_Z_UNCHANGED() {

}

void AVRCPU::instructionLSR() {

}

void AVRCPU::instructionMOV() {

}

void AVRCPU::instructionMOVW() {

}

void AVRCPU::instructionMUL() {

}

void AVRCPU::instructionMULS() {

}

void AVRCPU::instructionMULSU() {

}

void AVRCPU::instructionNEG() {

}

void AVRCPU::instructionNOP() {

}

void AVRCPU::instructionOR() {

}

void AVRCPU::instructionORI() {

}

void AVRCPU::instructionOUT() {

}

void AVRCPU::instructionPOP() {

}

void AVRCPU::instructionPUSH() {

}

void AVRCPU::instructionRCALL() {

}

void AVRCPU::instructionRET() {

}

void AVRCPU::instructionRETI() {

}

void AVRCPU::instructionRJMP() {

}

void AVRCPU::instructionROR() {

}

void AVRCPU::instructionSBC() {

}

void AVRCPU::instructionSBCI() {

}

void AVRCPU::instructionSBI() {

}

void AVRCPU::instructionSBIC() {

}

void AVRCPU::instructionSBIS() {

}

void AVRCPU::instructionSBIW() {

}

void AVRCPU::instructionSBRC() {

}

void AVRCPU::instructionSBRS() {

}

void AVRCPU::instructionSLEEP() {

}

void AVRCPU::instructionSPM() {

}

void AVRCPU::instructionST_X_POST_INCREMENT() {

}

void AVRCPU::instructionST_X_PRE_INCREMENT() {

}

void AVRCPU::instructionST_X_UNCHANGED() {

}

void AVRCPU::instructionST_Y_POST_INCREMENT() {

}

void AVRCPU::instructionST_Y_PRE_INCREMENT() {

}

void AVRCPU::instructionST_Y_UNCHANGED() {

}

void AVRCPU::instructionST_Z_POST_INCREMENT() {

}

void AVRCPU::instructionST_Z_PRE_INCREMENT() {

}

void AVRCPU::instructionST_Z_UNCHANGED() {

}

void AVRCPU::instructionSTD_Y() {

}

void AVRCPU::instructionSTD_Z() {

}

void AVRCPU::instructionSTS() {

}

void AVRCPU::instructionSUB() {

}

void AVRCPU::instructionSUBI() {

}

void AVRCPU::instructionSWAP() {

}

void AVRCPU::instructionWDR() {

}

void AVRCPU::unknownInstruction() {
//    LOGD(SOFIA_AVRCPU_TAG, "Unknown Instruction");
}


/*
 * Copyright 2018
 * Kollins Lima (kollins.lima@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.kollins.sofia;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.kollins.sofia.atmega328p.iomodule_atmega328p.output.OutputFragment_ATmega328P;
import com.example.kollins.sofia.ucinterfaces.ADCModule;
import com.example.kollins.sofia.ucinterfaces.DataMemory;
import com.example.kollins.sofia.ucinterfaces.IOModule;
import com.example.kollins.sofia.ucinterfaces.InterruptionModule;
import com.example.kollins.sofia.ucinterfaces.OutputFragment;
import com.example.kollins.sofia.ucinterfaces.ProgramMemory;
import com.example.kollins.sofia.ucinterfaces.Timer0Module;
import com.example.kollins.sofia.ucinterfaces.Timer1Module;
import com.example.kollins.sofia.ucinterfaces.Timer2Module;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Created by kollins on 3/7/18.
 */

public class UCModule extends AppCompatActivity {

    //To calculate efective clock
//    private static int sum, n = 0;
//    private static long time1 = 0, time2 = 0;

    private static Context context;

    public static final short CPU_ID = 0;
    public static final short SIMULATED_TIMER_ID = 1;
    public static final short TIMER0_ID = 2;
    public static final short TIMER1_ID = 3;
    public static final short TIMER2_ID = 4;
    public static final short ADC_ID = 5;

    public static String PACKAGE_NAME;

    //Default location
    public static final String DEFAULT_HEX_LOCATION = "SOFIA/code.hex";
    private String hexFileLocation = DEFAULT_HEX_LOCATION;

    //Default device
    public static String device;
    public static String model;
    private static int numberOfModules;

    public static Resources resources;

    public static final int RESET_ACTION = 0;
    public static final int CLOCK_ACTION = 1;
    public static final int SHORT_CIRCUIT_ACTION = 2;
    public static final int STOP_ACTION = 3;

    public static final String MY_LOG_TAG = "LOG_SIMULATOR";

    public static CopyOnWriteArrayList<Boolean> clockVector;

    public static InterruptionModule interruptionModule;

    private DataMemory dataMemory;
    private ProgramMemory programMemory;

    private CPUModule cpuModule;
    private Thread threadCPU;

    private Timer0Module timer0;
    private Thread threadTimer0;

    private Timer1Module timer1;
    private Thread threadTimer1;

    private Timer2Module timer2;
    private Thread threadTimer2;

    private ADCModule adc;
    private Thread threadADC;

    private UCHandler uCHandler;

    private boolean resetFlag;
    private boolean shortCircuitFlag;

    public static boolean setUpSuccessful;

    private FrameLayout frameIO;
    private UCModule_View ucView;
    private Thread threadUCView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_view);

        context = getApplicationContext();

        PACKAGE_NAME = getApplicationContext().getPackageName();
        resources = getResources();

        uCHandler = new UCHandler();

        //Load device
        SharedPreferences prefDevice = getSharedPreferences("deviceConfig", MODE_PRIVATE);
        model = prefDevice.getString("arduinoModel", "UNO");
        device = getDevice(model);
        setTitle("Arduino " + model);

        numberOfModules = getDeviceModules() + 1;

//        clockVector = new boolean[getDeviceModules() + 1];    //+1 for SIMULATED_TIMER;
//        clockVector.setSize(getDeviceModules() + 1);

        resetClockVector();

        setUpSuccessful = false;
        shortCircuitFlag = false;

        ucView = new UCModule_View();

        frameIO = (FrameLayout) findViewById(R.id.fragmentIO);

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        ft.add(R.id.fragmentIO, ucView, OutputFragment.TAG_OUTPUT_FRAGMENT);
        ft.commit();

        ucView.setUCHandler(uCHandler);
        ucView.setUCDevice(this);


        try {
            Class interruptionDevice = Class.forName(PACKAGE_NAME + "." + device.toLowerCase() + ".InterruptionModule_" + device);
            interruptionModule = (InterruptionModule) interruptionDevice.newInstance();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpUc();
    }

    @Override
    protected void onPause() {
        super.onPause();
        reset();
    }

    private void setUpUc() {
        Log.i(MY_LOG_TAG, "SetUp");

        if (shortCircuitFlag && ucView.getIOModule().checkShortCircuit()) {
            return;
        }

        shortCircuitFlag = false;
        setResetFlag(false);
        resetClockVector();

        try {
            //Init FLASH
            Class programMemoryDevice = Class.forName(PACKAGE_NAME + "." + device.toLowerCase() + ".ProgramMemory_" + device);
            programMemory = (ProgramMemory) programMemoryDevice
                    .getDeclaredConstructor(Handler.class)
                    .newInstance(uCHandler);

            Log.d(MY_LOG_TAG, "Flash size: " + programMemory.getMemorySize());

            if (programMemory.loadProgramMemory(hexFileLocation)) {
                //hexFile read Successfully

                programMemory.setPC(0);
                ((TextView) findViewById(R.id.hexFileErrorInstructions)).setVisibility(View.GONE);

                //Init RAM
                Class dataMemoryDevice = Class.forName(PACKAGE_NAME + "." + device.toLowerCase() + ".DataMemory_" + device);
                dataMemory = (DataMemory) dataMemoryDevice.getDeclaredConstructor(IOModule.class)
                        .newInstance(ucView.getIOModule());

                ucView.getIOModule().getPINConfig();

                Log.d(MY_LOG_TAG, "SDRAM size: " + dataMemory.getMemorySize());

                ucView.setMemoryIO(dataMemory);
                interruptionModule.setMemory(dataMemory);

                threadUCView = new Thread(ucView);
                threadUCView.start();

                //Init CPU
                cpuModule = new CPUModule(programMemory, dataMemory, this, uCHandler);
                threadCPU = new Thread(cpuModule);
                threadCPU.start();

                //Init Timer0
                Class timer0Device = Class.forName(PACKAGE_NAME + "." + device.toLowerCase() + ".Timer0_" + device);
                timer0 = (Timer0Module) timer0Device.getDeclaredConstructor(DataMemory.class, Handler.class, UCModule.class, IOModule.class)
                        .newInstance(dataMemory, uCHandler, this, ucView.getIOModule());

                threadTimer0 = new Thread(timer0);
                threadTimer0.start();

                //Init Timer1
                Class timer1Device = Class.forName(PACKAGE_NAME + "." + device.toLowerCase() + ".Timer1_" + device);
                timer1 = (Timer1Module) timer1Device.getDeclaredConstructor(DataMemory.class, Handler.class, UCModule.class, IOModule.class)
                        .newInstance(dataMemory, uCHandler, this, ucView.getIOModule());

                threadTimer1 = new Thread(timer1);
                threadTimer1.start();

                //Init Timer2
                Class timer2Device = Class.forName(PACKAGE_NAME + "." + device.toLowerCase() + ".Timer2_" + device);
                timer2 = (Timer2Module) timer2Device.getDeclaredConstructor(DataMemory.class, Handler.class, UCModule.class, IOModule.class)
                        .newInstance(dataMemory, uCHandler, this, ucView.getIOModule());

                threadTimer2 = new Thread(timer2);
                threadTimer2.start();

                //Init ADC
                Class adcDevice = Class.forName(PACKAGE_NAME + "." + device.toLowerCase() + ".ADC_" + device);
                adc = (ADCModule) adcDevice.getDeclaredConstructor(DataMemory.class, Handler.class, UCModule.class)
                        .newInstance(dataMemory, uCHandler, this);

                threadADC = new Thread(adc);
                threadADC.start();

                setUpSuccessful = true;
                ucView.setStatus(UCModule_View.LED_STATUS.RUNNING);

            } else {
                setUpSuccessful = false;
                ucView.setStatus(UCModule_View.LED_STATUS.HEX_FILE_ERROR);
            }

        } catch (ClassNotFoundException |
                IllegalAccessException |
                InstantiationException |
                NoSuchMethodException |
                InvocationTargetException e) {
            setUpSuccessful = false;
            ucView.setStatus(UCModule_View.LED_STATUS.HEX_FILE_ERROR);
            Log.e(MY_LOG_TAG, "Error Set-up", e);
        }

    }

    public int getMemoryUsage() {
//        return (int) (dataMemory.getMemoryUsage() * 100);
        return dataMemory.getMemoryUsage();
    }

    public static int getDeviceModules() {
        int id = resources.getIdentifier(device, "integer", PACKAGE_NAME);
        return resources.getInteger(id);
    }

    public static String getDevice(String model) {
        int id = resources.getIdentifier(model, "string", PACKAGE_NAME);
        return resources.getString(id);
    }

    public static int getDefaultPinPosition() {
        int id = resources.getIdentifier(UCModule.model + "_defaultPinPosition", "integer", PACKAGE_NAME);
        return resources.getInteger(id);
    }

    public static String[] getPinArray() {
        int id = resources.getIdentifier(UCModule.model + "_pins", "array", PACKAGE_NAME);
        return resources.getStringArray(id);
    }

    public static boolean[] getHiZInput() {
        boolean[] hiZInput = new boolean[getPinArray().length];
        for (int i = 0; i < hiZInput.length; i++) {
            hiZInput[i] = true;
        }
        return hiZInput;
    }

    public static String[] getPinModeArray() {
        return resources.getStringArray(R.array.inputModes);
    }

    public static int getSourcePower() {
        return resources.getInteger(R.integer.defaultSourcePower);
    }

    public static double getMaxVoltageLowState() {
        return (resources.getInteger(R.integer.maxVoltageLow) / 1000f);
    }

    public static double getMinVoltageHighState() {
        return (resources.getInteger(R.integer.minVoltageHigh) / 1000f);
    }

    public static String[] getPinArrayWithHint() {
        int id = resources.getIdentifier(UCModule.model + "_pins", "array", PACKAGE_NAME);
        String[] pinArrayWithHint = resources.getStringArray(id);
        pinArrayWithHint = Arrays.copyOf(pinArrayWithHint, pinArrayWithHint.length + 1);

        pinArrayWithHint[pinArrayWithHint.length - 1] = resources.getString(R.string.inputHint);
        return pinArrayWithHint;
    }

    public static String getNumberSelected(int number) {
        return resources.getQuantityString(
                R.plurals.number_selected,
                number, number
        );
    }

    public static int[] getInputMemoryAddress() {
        int id = resources.getIdentifier(UCModule.model + "_inputMemoryAddress", "array", PACKAGE_NAME);
        return resources.getIntArray(id);
    }

    public static int[] getInputMemoryBitPosition() {
        int id = resources.getIdentifier(UCModule.model + "_inputMemoryBitPosition", "array", PACKAGE_NAME);
        return resources.getIntArray(id);
    }

    public static int getSelectedColor() {
        return ContextCompat.getColor(context, R.color.selectedItem);
    }

    public static int getButonOnCollor() {
        return ContextCompat.getColor(context, R.color.on_button);
    }

    public static int getButonOffCollor() {
        return ContextCompat.getColor(context, R.color.off_button);
    }

    public static String getButtonTextOn() {
        return resources.getString(R.string.buttonOn);
    }

    public static String getButtonTextOff() {
        return resources.getString(R.string.buttonOff);
    }

    public static String getStatusRunning() {
        return resources.getString(R.string.running);
    }

    public static int getStatusRunningColor() {
        return ContextCompat.getColor(context, R.color.running);
    }

    public static String getStatusShortCircuit() {
        return resources.getString(R.string.short_circuit);
    }

    public static int getStatusShortCircuitColor() {
        return ContextCompat.getColor(context, R.color.short_circuit);
    }

    public static String getStatusHexFileError() {
        return resources.getString(R.string.hex_file_read_fail);
    }

    public static int getStatusHexFileErrorColor() {
        return ContextCompat.getColor(context, R.color.hex_file_error);
    }

    public synchronized boolean getResetFlag() {
        return resetFlag;
    }

    private synchronized void setResetFlag(boolean state) {
        resetFlag = state;
    }

    private void reset() {

        Log.i(MY_LOG_TAG, "Reset");

        stopSystem();
        ucView.resetIO();
        setUpUc();
    }

    private void stopSystem() {
        Log.i(MY_LOG_TAG, "Stopping system");

        setResetFlag(true);

        try {
            while (threadCPU.isAlive()
                    || threadUCView.isAlive()
                    || threadTimer0.isAlive()
                    || threadTimer1.isAlive()
                    || threadTimer2.isAlive()
                    || threadADC.isAlive()) {

                resetClockVector();
//                cpuModule.clockCPU();
//                ucView.clockUCView();
//                timer0.clockTimer0();
//                timer1.clockTimer1();
//                timer2.clockTimer2();
//                adc.clockADC();
            }


            threadCPU.join();
            threadUCView.join();
            threadTimer0.join();
            threadTimer1.join();
            threadTimer2.join();
            threadADC.join();
        } catch (InterruptedException | NullPointerException e) {
            Log.e(MY_LOG_TAG, "ERROR: stopSystem", e);
        }

        for (int i = 0; i < OutputFragment_ATmega328P.evalFreq.length; i++) {
            OutputFragment_ATmega328P.evalFreq[i] = false;
        }

        if (setUpSuccessful) {
            programMemory.stopCodeObserver();
        }
    }

    private void shortCircuit() {
        Log.i(MY_LOG_TAG, "Short Circuit - UCModule");
        shortCircuitFlag = true;
        ucView.setStatus(UCModule_View.LED_STATUS.SHORT_CIRCUIT);
        stopSystem();
    }

    public static void resetClockVector() {
        Boolean[] array = new Boolean[numberOfModules];
        Arrays.fill(array, Boolean.FALSE);
        clockVector = new CopyOnWriteArrayList<>(array);

        //Measure efective clock
//        time2 = SystemClock.elapsedRealtimeNanos();
//        Log.i("Clock", String.valueOf(getAvgClock(Math.pow(10, 9) / (time2 - time1))));
//        time1 = time2;
    }

//    private static double getAvgClock(double newClock) {
//        sum += newClock;
//        return (sum / ++n);
//    }

    public void changeFileLocation(String newHexFileLocation) {
        if (newHexFileLocation.substring(newHexFileLocation.length() - 3).equals("hex")) {
            hexFileLocation = newHexFileLocation.replace("/storage/emulated/0/", "");
            Log.d("FileImporter", "New Path: " + hexFileLocation);
        } else {
            Toast.makeText(this, "Not a .hex file", Toast.LENGTH_SHORT).show();
        }
    }

    public class UCHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            int action = msg.what;

            switch (action) {
//                case CLOCK_ACTION:

//                    cpuModule.clockCPU();
//                    ucView.clockUCView();
//                    timer0.clockTimer0();
//                    timer1.clockTimer1();
//                    timer2.clockTimer2();
//                    adc.clockADC();
//                    break;

                case RESET_ACTION:
                    reset();
                    break;

                case SHORT_CIRCUIT_ACTION:
                    shortCircuit();
                    break;

                case STOP_ACTION:
                    stopSystem();
                    break;

                default:
                    Log.e(MY_LOG_TAG, "ERROR: Action not found UCModule");
                    break;
            }
        }
    }

}

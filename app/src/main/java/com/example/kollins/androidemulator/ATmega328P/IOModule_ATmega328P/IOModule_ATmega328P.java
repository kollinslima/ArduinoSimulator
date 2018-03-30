package com.example.kollins.androidemulator.ATmega328P.IOModule_ATmega328P;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.kollins.androidemulator.ATmega328P.DataMemory_ATmega328P;
import com.example.kollins.androidemulator.ATmega328P.IOModule_ATmega328P.Input.InputFragment_ATmega328P;
import com.example.kollins.androidemulator.ATmega328P.IOModule_ATmega328P.Input.InputPin_ATmega328P;
import com.example.kollins.androidemulator.ATmega328P.IOModule_ATmega328P.Output.OutputFragment_ATmega328P;
import com.example.kollins.androidemulator.ATmega328P.IOModule_ATmega328P.Output.OutputPin_ATmega328P;
import com.example.kollins.androidemulator.UCModule;
import com.example.kollins.androidemulator.UCModule_View;
import com.example.kollins.androidemulator.uCInterfaces.InputFragment;
import com.example.kollins.androidemulator.uCInterfaces.IOModule;
import com.example.kollins.androidemulator.uCInterfaces.OutputFragment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Created by kollins on 3/23/18.
 */

public class IOModule_ATmega328P extends Handler implements IOModule {

    private byte portRead;
    private byte configRead;
    private boolean updatingIO;

    private static UCModule.uCHandler uCHandler;
    private OutputFragment_ATmega328P outputFragment;
    private InputFragment_ATmega328P inputFragment;

    public IOModule_ATmega328P(OutputFragment outputFragment,
                               InputFragment inputFragment) {

        this.uCHandler = uCHandler;
        this.outputFragment = (OutputFragment_ATmega328P) outputFragment;
        this.inputFragment = (InputFragment_ATmega328P) inputFragment;
        setUpdatingIO(false);
    }

    @Override
    public void handleMessage(Message msg) {

        switch (msg.what) {
            case PORTB_EVENT:

                portRead = msg.getData().getByte(IOModule.PORT_IOMESSAGE);
                configRead = msg.getData().getByte(IOModule.CONFIG_IOMESSAGE);

                List<OutputPin_ATmega328P> outputPins = outputFragment.getOutputPins();

//                Log.i(UCModule.MY_LOG_TAG, "PortB: " + Integer.toBinaryString(portRead));
//                Log.i(UCModule.MY_LOG_TAG, "DDRB: " + Integer.toBinaryString(configRead));
                new PortBUpdateView().execute(outputPins);

                break;
        }
    }

    public static void sendShortCircuit() {
        UCModule_View.sendShortCircuit();
    }

    @Override
    public synchronized boolean isUpdatingIO() {
        return false;
    }

    private synchronized void setUpdatingIO(boolean state) {
        updatingIO = state;
    }

    @Override
    public boolean checkShortCircuit() {
        List<InputPin_ATmega328P> inputPins = inputFragment.getPinList();
        List<OutputPin_ATmega328P> outputPins = outputFragment.getPinList();

        try {
            /****************Check short circuit between inputs*****************/
            if (inputPins.size() > 0) {
                InputPin_ATmega328P pi, pj;
                for (int i = 0; i < inputPins.size(); i++) {
                    for (int j = i; j < inputPins.size(); j++) {
                        pi = inputPins.get(i);
                        pj = inputPins.get(j);

                        if (pi.getPinSpinnerPosition() == pj.getPinSpinnerPosition()) {
                            if (pi.getHiZ(pi.getPinSpinnerPosition())) {
                                continue;
                            }
                            if (pi.getPinState() == IOModule.TRI_STATE || pj.getPinState() == IOModule.TRI_STATE) {
                                continue;
                            }
                            if (pi.getPinState() != pj.getPinState()) {
                                return true;
                            }
                        }
                    }
                }


                /****************Check short circuit between input and output*****************/
                if (outputPins.size() > 0) {
                    OutputPin_ATmega328P pk;

                    for (int i = 0; i < inputPins.size(); i++) {
                        for (int k = 0; k < outputPins.size(); k++) {
                            pi = inputPins.get(i);
                            pk = outputPins.get(k);

                            if (pi.getPinState() == IOModule.TRI_STATE ||
                                    pk.getPinState(pk.getPinPositionSpinner()) == IOModule.TRI_STATE) {
                                continue;
                            }
                            if (pi.getPinState() != pk.getPinState(pk.getPinPositionSpinner())) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (NullPointerException e){
            //input/output list is null
        }
        return false;
    }

    @Override
    public void getPINConfig() {
        inputFragment.getPINConfig();
    }

    private class PortBUpdateView extends AsyncTask<List<OutputPin_ATmega328P>, Integer, Void> {

        private int index;
        private boolean digitalPINState;
        private int bitPosition;

        @Override
        protected void onPreExecute() {
            index = 0;
        }

        @Override
        protected Void doInBackground(List<OutputPin_ATmega328P>... pins) {

            for (int i = 8, bitPosition = 0; i <= 13; i++, bitPosition++) {
                //Is input?
                if ((0x01 & (configRead >> bitPosition)) == 0) {
//                    Log.i(UCModule.MY_LOG_TAG, "Input");

                    digitalPINState = inputFragment.getPINState(DataMemory_ATmega328P.PINB_ADDR, bitPosition);

                    if (((0x01 & (portRead >> bitPosition)) == 1) && outputFragment.isPullUpEnabled()) {

//                        Log.i(UCModule.MY_LOG_TAG, "Port == 1 && pull-Up enabled");

                        if (!digitalPINState && inputFragment.isPinHiZ(i)) {
//                            Log.i(UCModule.MY_LOG_TAG, "Requesting pull up");
                            inputFragment.inputRequest_outputChanel(IOModule.HIGH_LEVEL, DataMemory_ATmega328P.PINB_ADDR, bitPosition, "Pin " + i);

                            /*
                            Each time pin is updated, this function is called again from dataMemory,
                            so there is no need to continue from here.
                             */

                            return null;
                        }

                        outputFragment.pinbuffer[i] = digitalPINState ? 1 : 0;

                    } else {
                        if (!inputFragment.isPinHiZ(i)) {
//                            Log.i(UCModule.MY_LOG_TAG, "Button pressed");
                            outputFragment.pinbuffer[i] = digitalPINState ? 1 : 0;
                        } else {
//                            Log.i(UCModule.MY_LOG_TAG, "Button not pressed");
                            outputFragment.pinbuffer[i] = IOModule.TRI_STATE;
                        }
                    }
                }
                //Is output!
                else {
//                    Log.i(UCModule.MY_LOG_TAG, "Output");
                    outputFragment.pinbuffer[i] = (0x01 & (portRead >> bitPosition));
                }

//                portRead = (byte) (portRead >> 1);
//                configRead = (byte) (configRead >> 1);
            }

            if (pins[0] != null) {
                try {
                    setUpdatingIO(true);
                    for (OutputPin_ATmega328P p : pins[0]) {
                        p.setPinState(outputFragment.pinbuffer[p.getPinPositionSpinner()], p.getPinPositionSpinner());
                        publishProgress(index);
                        index += 1;
                    }
                } finally {
                    setUpdatingIO(false);
                }
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            outputFragment.updateView(values[0]);
        }
    }
}
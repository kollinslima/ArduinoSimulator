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

import java.util.List;
import java.util.Objects;

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

        portRead = msg.getData().getByte(IOModule.PORT_IOMESSAGE);
        configRead = msg.getData().getByte(IOModule.CONFIG_IOMESSAGE);

        List<OutputPin_ATmega328P> outputPins = outputFragment.getOutputPins();

        switch (msg.what) {
            case PORTB_EVENT:

//                Log.i(UCModule.MY_LOG_TAG, "PortB: " + Integer.toBinaryString(portRead));
//                Log.i(UCModule.MY_LOG_TAG, "DDRB: " + Integer.toBinaryString(configRead));
                new PortBUpdateView().execute(outputPins);

                break;

            case PORTC_EVENT:

//                Log.i(UCModule.MY_LOG_TAG, "PortC: " + Integer.toBinaryString(portRead));
//                Log.i(UCModule.MY_LOG_TAG, "DDRC: " + Integer.toBinaryString(configRead));
                new PortCUpdateView().execute(outputPins);

                break;

            case PORTD_EVENT:

//                Log.i(UCModule.MY_LOG_TAG, "PortD: " + Integer.toBinaryString(portRead));
//                Log.i(UCModule.MY_LOG_TAG, "DDRD: " + Integer.toBinaryString(configRead));
                new PortDUpdateView().execute(outputPins);

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
                if (checkInputShortCircuit(inputPins)) {
                    return true;
                }

                /****************Check short circuit between input and output*****************/
                if (outputPins.size() > 0) {
                    if (checkInputOutputShortCircuit(inputPins, outputPins)) {

                        return true;
                    }
                }
            }
        } catch (NullPointerException e) {
            //input/output list is null
        }
        return false;
    }

    private boolean checkInputShortCircuit(List<InputPin_ATmega328P> inputPins) throws NullPointerException {
        InputPin_ATmega328P pi, pj;
        for (int i = 0; i < inputPins.size(); i++) {
            for (int j = i + 1; j < inputPins.size(); j++) {
                pi = inputPins.get(i);
                pj = inputPins.get(j);

                Log.i("Short", "Comparing " + pi.getPin() + " and " + pj.getPin());
                Log.i("Short", pi.getPin() + ": " + pi.getPinState());
                Log.i("Short", pj.getPin() + ": " + pj.getPinState());

                if (pi.getPinSpinnerPosition() == pj.getPinSpinnerPosition()) {
                    if (pi.getHiZ(pi.getPinSpinnerPosition())) {
                        continue;
                    }
                    if (pi.getPinState() == IOModule.TRI_STATE || pj.getPinState() == IOModule.TRI_STATE) {
                        continue;
                    }
                    if (pi.getPinState() != pj.getPinState()) {
                        Log.i("Short", "Send short circuit - CheckInput(IOModule)");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean checkInputOutputShortCircuit(List<InputPin_ATmega328P> inputPins, List<OutputPin_ATmega328P> outputPins) throws NullPointerException {
        InputPin_ATmega328P pi;
        OutputPin_ATmega328P pk;

        for (int i = 0; i < inputPins.size(); i++) {
            for (int k = 0; k < outputPins.size(); k++) {
                pi = inputPins.get(i);
                pk = outputPins.get(k);
                if (pi.getPinSpinnerPosition() == pk.getPinPositionSpinner()) {

                    if (pi.getPinState() == IOModule.TRI_STATE ||
                            pk.getPinState(pk.getPinPositionSpinner()) == IOModule.TRI_STATE ||
                            Objects.equals(pk.getPin(), pi.getPin())) {  //No short circuit if measuring input
                        continue;
                    }
                    if (pi.getPinState() != pk.getPinState(pk.getPinPositionSpinner())) {
                        Log.i("Short", "Send short circuit - CheckInputOutput(IOModule)");
                        return true;
                    }
                }
            }
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
            super.onPreExecute();
            index = 0;
            setUpdatingIO(true);
        }

        @Override
        protected Void doInBackground(List<OutputPin_ATmega328P>... pins) {

            //Pin8 - Pin13
            for (int i = 8, bitPosition = 0; i <= 13; i++, bitPosition++) {
                //Is input?
                if ((0x01 & (configRead >> bitPosition)) == 0) {
//                    Log.i(UCModule.MY_LOG_TAG, "Input: " + i);

                    digitalPINState = inputFragment.getPINState(DataMemory_ATmega328P.PINB_ADDR, bitPosition);

                    if (((0x01 & (portRead >> bitPosition)) == 1) && outputFragment.isPullUpEnabled()) {

//                        Log.i(UCModule.MY_LOG_TAG, "Port == 1 && pull-Up enabled");

                        if (!digitalPINState && inputFragment.isPinHiZ(i)) {
//                            Log.i(UCModule.MY_LOG_TAG, "Requesting pull up");
                            inputFragment.inputRequest_outputChanel(IOModule.HIGH_LEVEL, DataMemory_ATmega328P.PINB_ADDR, bitPosition, "Pin" + i);

//                            return null;
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
//                    Log.i(UCModule.MY_LOG_TAG, "Output: " + i);
                    outputFragment.pinbuffer[i] = (0x01 & (portRead >> bitPosition));
                    outputFragment.writeFeedback(DataMemory_ATmega328P.PINB_ADDR, bitPosition, outputFragment.pinbuffer[i] != 0);
                }

//                portRead = (byte) (portRead >> 1);
//                configRead = (byte) (configRead >> 1);
            }

            if (pins[0] != null) {
//                try {
//                    setUpdatingIO(true);
                for (OutputPin_ATmega328P p : pins[0]) {
                    p.setPinState(outputFragment.pinbuffer[p.getPinPositionSpinner()], p.getPinPositionSpinner());
                    publishProgress(index);
                    index += 1;
                }
//                } finally {
//                    setUpdatingIO(false);
//                }
            }

            try {
                if (checkInputOutputShortCircuit(inputFragment.getPinList(), pins[0])) {
                    sendShortCircuit();
                }
            } catch (NullPointerException e) {
                //Output list is null;
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            outputFragment.updateView(values[0]);
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            setUpdatingIO(false);
        }
    }

    private class PortCUpdateView extends AsyncTask<List<OutputPin_ATmega328P>, Integer, Void> {

        private int index;
        private boolean digitalPINState;
        private int bitPosition;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            index = 0;
            setUpdatingIO(true);
        }

        @Override
        protected Void doInBackground(List<OutputPin_ATmega328P>... pins) {

            //AN0 - AN5 (Pin14 - Pin19)
            for (int i = 14, bitPosition = 0; i <= 19; i++, bitPosition++) {
                //Is input?
                if ((0x01 & (configRead >> bitPosition)) == 0) {
//                    Log.i(UCModule.MY_LOG_TAG, "Input");

                    digitalPINState = inputFragment.getPINState(DataMemory_ATmega328P.PINC_ADDR, bitPosition);

                    if (((0x01 & (portRead >> bitPosition)) == 1) && outputFragment.isPullUpEnabled()) {

//                        Log.i(UCModule.MY_LOG_TAG, "Port == 1 && pull-Up enabled");

                        if (!digitalPINState && inputFragment.isPinHiZ(i)) {
//                            Log.i(UCModule.MY_LOG_TAG, "Requesting pull up");
                            inputFragment.inputRequest_outputChanel(IOModule.HIGH_LEVEL, DataMemory_ATmega328P.PINC_ADDR, bitPosition, "AN" + bitPosition);

//                            return null;
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
                    outputFragment.writeFeedback(DataMemory_ATmega328P.PINC_ADDR, bitPosition, outputFragment.pinbuffer[i] != 0);
                }

//                portRead = (byte) (portRead >> 1);
//                configRead = (byte) (configRead >> 1);
            }

            if (pins[0] != null) {
//                try {
//                    setUpdatingIO(true);
                    for (OutputPin_ATmega328P p : pins[0]) {
                        p.setPinState(outputFragment.pinbuffer[p.getPinPositionSpinner()], p.getPinPositionSpinner());
                        publishProgress(index);
                        index += 1;
                    }
//                } finally {
//                    setUpdatingIO(false);
//                }
            }

            try {
                if (checkInputOutputShortCircuit(inputFragment.getPinList(), pins[0])) {
                    sendShortCircuit();
                }
            } catch (NullPointerException e) {
                //Output list is null;
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            outputFragment.updateView(values[0]);
        }


        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            setUpdatingIO(false);
        }
    }

    private class PortDUpdateView extends AsyncTask<List<OutputPin_ATmega328P>, Integer, Void> {

        private int index;
        private boolean digitalPINState;
        private int bitPosition;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            index = 0;
            setUpdatingIO(true);
        }

        @Override
        protected Void doInBackground(List<OutputPin_ATmega328P>... pins) {

            //Pin0 - Pin7
            for (int i = 0, bitPosition = 0; i <= 7; i++, bitPosition++) {
                //Is input?
                if ((0x01 & (configRead >> bitPosition)) == 0) {
//                    Log.i(UCModule.MY_LOG_TAG, "Input");

                    digitalPINState = inputFragment.getPINState(DataMemory_ATmega328P.PIND_ADDR, bitPosition);

                    if (((0x01 & (portRead >> bitPosition)) == 1) && outputFragment.isPullUpEnabled()) {

//                        Log.i(UCModule.MY_LOG_TAG, "Port == 1 && pull-Up enabled");

                        if (!digitalPINState && inputFragment.isPinHiZ(i)) {
                            Log.i(UCModule.MY_LOG_TAG, "Requesting pull up");
                            Log.i(UCModule.MY_LOG_TAG, "HiZ[" + i + "]: " + inputFragment.isPinHiZ(i));
                            inputFragment.inputRequest_outputChanel(IOModule.HIGH_LEVEL, DataMemory_ATmega328P.PIND_ADDR, bitPosition, "Pin" + i);

//                            return null;
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
                    outputFragment.writeFeedback(DataMemory_ATmega328P.PIND_ADDR, bitPosition, outputFragment.pinbuffer[i] != 0);
                }

//                portRead = (byte) (portRead >> 1);
//                configRead = (byte) (configRead >> 1);
            }

            if (pins[0] != null) {
//                try {
//                    setUpdatingIO(true);
                    for (OutputPin_ATmega328P p : pins[0]) {
                        p.setPinState(outputFragment.pinbuffer[p.getPinPositionSpinner()], p.getPinPositionSpinner());
                        publishProgress(index);
                        index += 1;
                    }
//                } finally {
//                    setUpdatingIO(false);
//                }
            }

            try {
                if (checkInputOutputShortCircuit(inputFragment.getPinList(), pins[0])) {
                    sendShortCircuit();
                }
            } catch (NullPointerException e) {
                //Output list is null;
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            outputFragment.updateView(values[0]);
        }


        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            setUpdatingIO(false);
        }
    }
}

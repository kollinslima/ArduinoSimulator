package com.example.kollins.androidemulator.ATmega328P;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.FileObserver;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.example.kollins.androidemulator.UCModule;
import com.example.kollins.androidemulator.uCInterfaces.ProgramMemory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Created by kollins on 3/8/18.
 */

public class ProgramMemory_ATmega328P implements ProgramMemory {

    //32kBytes, each instruction is 16bits wide
    private final int FLASH_SIZE = 32 * ((int)Math.pow(2,10));
    private final int WORD_SIZE = 16;
    private byte[] flashMemory = null;

    private final int INTEL_DATA_SIZE = 0;
    private final int INTEL_ADDRESS = 1;
    private final int INTEL_REORD_TYPE = 3;
    private final int INTEL_DATA = 4;

    private FileObserver codeObserver;
    private Context ucContext;

    public ProgramMemory_ATmega328P(Context ucContext){
        this.ucContext = ucContext;
        flashMemory = new byte[FLASH_SIZE];
    }

    //Thanks Dave L.
    // (https://stackoverflow.com/questions/140131/convert-a-string-representation-of-a-hex-dump-to-a-byte-array-using-java)
    private byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }

    @Override
    public int getMemorySize() {
        return FLASH_SIZE;
    }

    public boolean loadProgramMemory(String hexFileLocation) {

        String state = Environment.getExternalStorageState();

        //All set to read and write data in SDCard
        if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {

            File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);

            if (dcimDir.exists()) {
                File hexFile = new File(dcimDir, hexFileLocation);

                //Watch for changes in hexFile
                codeObserver = new FileObserver(hexFile.getPath().toString(), FileObserver.DELETE_SELF) {
                    @Override
                    public void onEvent(int event, @Nullable String path) {
                        Log.d(UCModule.MY_LOG_TAG, "File event: " + event);

                        //Send Broadcast
                        Intent it = new Intent(UCModule.RESET_ACTION);
                        LocalBroadcastManager.getInstance(ucContext).sendBroadcast(it);
                    }
                };
                codeObserver.startWatching();

                if (hexFile.exists()) {
                    try {
                        FileInputStream fis = new FileInputStream(hexFile);
                        loadHexFile(fis);
                    } catch (FileNotFoundException e) {
                        Log.e(UCModule.MY_LOG_TAG, "ERROR: Load .hex file", e);
                        return false;
                    }
                }
            }

        } else {
            Log.e(UCModule.MY_LOG_TAG, "ERROR: Can't read SD card");
            return false;
        }

        return true;
    }

    @Override
    public char loadInstruction(char pc) throws ArrayIndexOutOfBoundsException{

        byte instPart1, instPart2;

        //little-endian read
        instPart1 = flashMemory[pc*2];
        instPart2 = flashMemory[(pc*2)+1];

        return (char) (((0x00FF & instPart2)<<8)|(0x00FF & instPart1));
    }

    private void loadHexFile(FileInputStream fis) {

        BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
        String line;

        byte[] readBytes;
        int dataSize;
        int memoryPosition;

        boolean extendedSegmentAddress = false;
        boolean extendedLinearAddress = false;

        int extendedAddress = 0;

        try {
            while ((line = reader.readLine()) != null) {
                //Remove colon
                line = line.substring(1);
                Log.v(UCModule.MY_LOG_TAG, line);

                readBytes = hexStringToByteArray(line);

                //Checksum()

                dataSize = readBytes[INTEL_DATA_SIZE];
                Log.d(UCModule.MY_LOG_TAG, "Data size: " + dataSize);

                switch (readBytes[INTEL_REORD_TYPE]) {
                    //Data
                    case 00:
                        Log.v(UCModule.MY_LOG_TAG, "Loading Data from hex");

                        //Avoid sign extention
                        memoryPosition = (((int) (0x000000FF & readBytes[INTEL_ADDRESS])) << 8)
                                | (int) (0x000000FF & readBytes[INTEL_ADDRESS + 1]);

                        if (extendedSegmentAddress) {
                            memoryPosition = memoryPosition + extendedAddress;
                        } else if (extendedLinearAddress) {
                            memoryPosition = (extendedAddress << 16) | memoryPosition;
                        }

                        Log.v(UCModule.MY_LOG_TAG, "Memory position: " + memoryPosition);

                        for (int i = 0; i < dataSize; i++) {
                            flashMemory[memoryPosition + i] = readBytes[INTEL_DATA + i];
                            Log.v(UCModule.MY_LOG_TAG, String.format("Added 0x%02X to flash", readBytes[INTEL_DATA + i]));
                        }

                        break;

                    //End of File
                    case 01:
                        Log.v(UCModule.MY_LOG_TAG, "End of File from hex");
                        break;

                    //Extended Segment Address
                    case 02:
                        Log.v(UCModule.MY_LOG_TAG, "Extended Segment Address from hex");
                        extendedSegmentAddress = true;
                        extendedLinearAddress = false;

                        extendedAddress = (((int) (0x000000FF & readBytes[INTEL_ADDRESS])) << 8)
                                | (int) (0x000000FF & readBytes[INTEL_ADDRESS + 1]);

                        Log.v(UCModule.MY_LOG_TAG, "Extended by: " + extendedAddress);
                        break;

                    //Extended Linear Address
                    case 04:
                        Log.v(UCModule.MY_LOG_TAG, "Extended Linear Address from hex");
                        extendedSegmentAddress = false;
                        extendedLinearAddress = true;

                        extendedAddress = (readBytes[INTEL_DATA] << 8) | readBytes[INTEL_DATA];
                        Log.v(UCModule.MY_LOG_TAG, "Extended by: " + extendedAddress);
                        break;

                    default:
                        Log.e(UCModule.MY_LOG_TAG, "Record Type unknown: " +
                                String.format("0x%02X", readBytes[INTEL_REORD_TYPE]));
                }

            }

            reader.close();
            fis.close();
        } catch (IOException e) {
            Log.e(UCModule.MY_LOG_TAG, "ERROR: Load .hex file", e);
        } catch (ArrayIndexOutOfBoundsException e){
            Log.e(UCModule.MY_LOG_TAG, "ERROR: .hex file bigger than 32kB", e);
        }
    }



}
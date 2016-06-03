package com.bignerdranch.android.altimeterapp;

/**
 * Created by hcc999 on 12/2/15.
 */
import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

public class SensorBMP085 {

    AndroidAccessoryInterface mInterface;
    Activity main;

    final static byte addr = 0x77;
    final static byte reg_ac1 = (byte) 0xaa;
    final static byte reg_ac2 = (byte) 0xac;
    final static byte reg_ac3 = (byte) 0xae;
    final static byte reg_ac4 = (byte) 0xb0;
    final static byte reg_ac5 = (byte) 0xb2;
    final static byte reg_ac6 = (byte) 0xb4;
    final static byte reg_b1 = (byte) 0xb6;
    final static byte reg_b2 = (byte) 0xb8;
    final static byte reg_mb = (byte) 0xba;
    final static byte reg_mc = (byte) 0xbc;
    final static byte reg_md = (byte) 0xbe;
    final static byte reg_f4 = (byte) 0xf4;
    final static byte reg_f6 = (byte) 0xf6;
    final static byte reg_f7 = (byte) 0xf7;
    final static byte reg_f8 = (byte) 0xf8;
    int AC1, AC2, AC3, AC4, AC5, AC6, B1, B2, MB, MC, MD;
    long UT;

    int signExtend16(int a) {
        if (0 != (a & 0x8000)){
            return a |= 0xffff0000;
        }
        return a;
    }

    public SensorBMP085(AndroidAccessoryInterface iface, Activity m){
        mInterface = iface;
        main = m;

    }

    public String calibrationString() throws AndroidAccessoryInterface.ConnectionTimeOutException, AndroidAccessoryInterface.NoUSBException {
        String calibration = "";
        AC1 = signExtend16(mInterface.i2cRead16(addr, reg_ac1));
        //calibration += "ac1: " + Integer.toString(AC1);
        AC2 = signExtend16(mInterface.i2cRead16(addr, reg_ac2));
        //calibration += " ac2: " + Integer.toString(AC2);
        AC3 = signExtend16(mInterface.i2cRead16(addr, reg_ac3));
        //calibration += " ac3: "+ Integer.toString(AC3);
        AC4 = mInterface.i2cRead16(addr, reg_ac4);
        //calibration += " ac4: "+ Integer.toString(AC4);
        AC5 = mInterface.i2cRead16(addr, reg_ac5);
        //calibration += " ac5: "+ Integer.toString(AC5);
        AC6 = mInterface.i2cRead16(addr, reg_ac6);
        //calibration += " ac6: " + Integer.toString(AC6);
        B1 = signExtend16(mInterface.i2cRead16(addr, reg_b1));
        //calibration += " b1: "+ Integer.toString(B1);
        B2 = signExtend16(mInterface.i2cRead16(addr, reg_b2));
        //calibration += " b2: "+ Integer.toString(B2);
        MB = signExtend16(mInterface.i2cRead16(addr, reg_mb));
        //calibration += " mb: "+ Integer.toString(MB);
        MC = signExtend16(mInterface.i2cRead16(addr, reg_mc));
        //calibration += " mc: "+ Integer.toString(MC);
        MD = signExtend16(mInterface.i2cRead16(addr, reg_md));
        //calibration += " md: "+ Integer.toString(MD);

        calibration += "ac1: " + Integer.toString(AC1);
        calibration += " ac2: " + Integer.toString(AC2);
        calibration += " ac3: "+ Integer.toString(AC3);
        calibration += " ac4: "+ Integer.toString(AC4);
        calibration += " ac5: "+ Integer.toString(AC5);
        calibration += " ac6: " + Integer.toString(AC6);
        calibration += " b1: "+ Integer.toString(B1);
        calibration += " b2: "+ Integer.toString(B2);
        calibration += " mb: "+ Integer.toString(MB);
        calibration += " mc: "+ Integer.toString(MC);
        calibration += " md: "+ Integer.toString(MD);


        return calibration;
    }
    //need to read Temperature before read Pressure
    public int readTemperature() throws AndroidAccessoryInterface.ConnectionTimeOutException, AndroidAccessoryInterface.NoUSBException {
        long X1,X2,B5,T;
        calibrationString();
        mInterface.i2cWrite(addr, reg_f4, (byte) 0x2e);
        SystemClock.sleep(10);

        int MSB = signExtend16(mInterface.i2cRead16(addr,reg_f6));
        int LSB = signExtend16(mInterface.i2cRead16(addr, reg_f7));

        UT = mInterface.i2cRead16(addr, reg_f6);
        //UT = MSB << 8 + LSB;

        //int AC6 = mInterface.i2cRead16(addr, reg_ac6);
        //int AC5 = mInterface.i2cRead16(addr, reg_ac5);
        X1 = (long) ((UT - AC6) *  ((double)AC5/(1 << 15)));
        //int MC = signExtend16(mInterface.i2cRead16(addr, reg_mc));
        //int MD = signExtend16(mInterface.i2cRead16(addr, reg_md));
        X2 = MC*(1 << 11)/ (X1 + MD);
        B5 = X1 + X2;
        T = (B5 + 8)/(1 << 4);
        //Temp is in .1 degrees C
        return (int) T;
    }

    public int readPressure(int oversampling) throws AndroidAccessoryInterface.ConnectionTimeOutException, AndroidAccessoryInterface.NoUSBException {
        long X1, X2, X3, B4, B5, B7, P, B3, B6;

        calibrationString();
        mInterface.i2cWrite(addr, reg_f4, (byte) (0x34 + (oversampling << 6)));
        SystemClock.sleep((long) 26.5);

        int MSB = mInterface.i2cRead16(addr,reg_f6);
        int LSB = mInterface.i2cRead16(addr, reg_f7);
        int XLSB = mInterface.i2cRead16(addr, reg_f8) & 0xff;

        //long UP = (MSB << 16 + LSB << 8 + XLSB) >> (8-oversampling); //was int before
        long UP = ((MSB << 8) | XLSB) >> (8-oversampling); //from ahmed and justin



        X1 = (long) ((UT - AC6) *  ((double)AC5/(1 << 15)));
        //int MC = signExtend16(mInterface.i2cRead16(addr, reg_mc));
        //int MD = signExtend16(mInterface.i2cRead16(addr, reg_md));
        X2 = MC*(1 << 11)/ (X1 + MD);
        B5 = X1 + X2; //redoing temp calculation to find B5
        B6 = B5 - 4000;
        X1 = (B2 * (B6 * B6/(1 << 12)))/(1 << 11);
        X2 = AC2 * B6/(1 << 11); //229

        X3 = X1 + X2; //230
        //debugToast(calibrationString() + " X3: " + Long.toString(X3));
        B3 = (((AC1 * 4 + X3) << oversampling) + 2)/4; //30141
        //int AC3 = signExtend16(mInterface.i2cRead16(addr, reg_ac3));
        X1 = AC3 * B6/(1 << 13); //692

        //int B1 = signExtend16(mInterface.i2cRead16(addr, reg_b1));
        X2 = (B1 * (B6 * B6/(1 << 12)))/(1 << 16);
        //debugToast("X2: "+ Long.toString(X2)); //2
        X3 = ((X1 + X2) + 2)/4;
        //int AC4 = mInterface.i2cRead16(addr, reg_ac4);
        B4 = AC4 * (X3 + 32768L)/(1 << 15);
        B7 = (UP - B3)*(50000L >> oversampling);
        P = (B7/B4)*2;


        X1 = (P/(1 << 8))*(P/(1 << 8));
        //debugToast("X1: " + Long.toString(X1));
        X1 = (X1 * 3038)/(1 << 16);
        X2 = (-7357*P)/(1 << 16);

        /*debugToast( "B6: "+ Long.toString(B6) +
                    " X1: "+ Long.toString(X1) +
                    " X2: "+ Long.toString(X2) +
                    " X3: "+ Long.toString(X3) +
                    " B4: "+ Long.toString(B4) +
                    " B7: "+ Long.toString(B7) +
                    " UP: "+ Long.toString(UP) +
                    " P: " + Long.toString(P));*/

        P = P + (X1 + X2 + 3791)/(1 << 4);
        return (int) P;

    }
    private void debugToast(String message) {
        final String finalMessage = message;
        final Activity context = main;
        main.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(context, finalMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

}


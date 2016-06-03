package com.bignerdranch.android.altimeterapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


/**
 * Created by hcc999 on 11/16/15.
 */
class ReaderMETAR {
    String s;
    long lastRetrieval;
    boolean hasInternetValue;
    double altitude;
    double mAltReading;
    public SharedPreferences.Editor mEditor;

    public SharedPreferences mSharedPrefs;
    public ReaderMETAR(Context context, String station){
        mSharedPrefs = context.getSharedPreferences("MainActivity",0);
        s = station;
        lastRetrieval = 0;
        hasInternetValue = false;
    }

    public String station(){
        return s;
    }

    public double readAltimeter() throws IOException {
        long timeNow = SystemClock.elapsedRealtime();

        if (lastRetrieval == 0)
            lastRetrieval = timeNow;

        long diff = lastRetrieval-timeNow; //difference in miliseconds
        diff = diff/(1000*60); //difference in minutes


        if (diff >= 15 || hasInternetValue == false) {
            URL x = new URL(s);
            BufferedReader in = new BufferedReader(new InputStreamReader(x.openStream()));
            String date = in.readLine();
            String line = in.readLine();

            String pattern = "A\\d{4}";
            String pres = ""; String pr = "";
            //parse string
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(line);
            if (m.find()) {
                pres += m.group();

                pr += pres.substring(1);//get rid of the leading A

                int pressure = Integer.parseInt(pr);

                altitude = (double) pressure/100; //inHg



                lastRetrieval = timeNow;
                hasInternetValue = true;
            }
            else {
                altitude = -1.0;
            }


        }
        mAltReading = altitude;
        if (mEditor == null) {
            mEditor = mSharedPrefs.edit();
            mEditor.putInt("Altitude", (int) (altitude*100));
        }

        return altitude;

    }

    public double altitude(double ph){
        //convert a pressure in Pascal to an altitude above mean sea level
        // using the altimeter reading of this METAR station
        double p_o = mAltReading * 33.86389;
        double p = ph/100;
        double convert_alt;
        convert_alt = 44330*(1-Math.pow((p/p_o),(1/5.255)));
        return convert_alt;
    }


}

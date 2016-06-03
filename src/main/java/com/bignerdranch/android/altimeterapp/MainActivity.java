package com.bignerdranch.android.altimeterapp;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.widget.TextView;
import android.widget.Toast;
import android.os.StrictMode;
import android.os.StrictMode.ThreadPolicy;



import java.io.IOException;

public class MainActivity extends AppCompatActivity implements AccessoryCallBack {

    public AndroidAccessoryInterface mInterface;
    public Handler mMainThreadHandler;
    public AverageExponential pressureAvg;
    public AverageExponential tempAvg;
    public ReaderMETAR mReaderMETAR;
    public FloorConversion mFloorConversion;
    public TextView mTextPressure;
    public TextView mTemperature;
    public TextView mFloorNum;
    public TextView mAltitude;
    public TextView mHgPressure;
    public TextView mMeasurementRate;
    public Context mApplicationContext;
    public TextView melevationRange;
    public TextView mpressureRange;
    long start;
    int counter;
    double urlPres;
    double temp;
    public SensorBMP085 mSensor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mApplicationContext = getApplicationContext();
        mInterface = new AndroidAccessoryInterface(this, this);
        mMainThreadHandler = new Handler();
        mFloorConversion = new FloorConversion();
        mReaderMETAR = new ReaderMETAR(mApplicationContext,
                "http://weather.noaa.gov/pub/data/observations/metar/stations/KTTN.TXT");
        pressureAvg = new AverageExponential(1, 15);


        mTemperature = (TextView) findViewById(R.id.m_Temperature);
        mpressureRange = (TextView) findViewById(R.id.m_pressureRange);
        melevationRange = (TextView) findViewById(R.id.m_elevationRange);
        mHgPressure = (TextView) findViewById(R.id.m_HgPressure);
        mAltitude = (TextView) findViewById(R.id.m_Altitude);
        mFloorNum = (TextView) findViewById(R.id.m_FloorNum);
        mMeasurementRate = (TextView) findViewById(R.id.m_MeasurementRate);
        mTextPressure = (TextView) findViewById(R.id.m_TextPressure);


        new Thread (new Runnable() {
            public void run() {
                try {
                    urlPres = mReaderMETAR.readAltimeter();
                    double x = mApplicationContext.getSharedPreferences("MainActivity", 0).getInt("Altitude", -200);
                    if (x != -200 && urlPres == 0.0)
                        urlPres = x/100;
                    Thread.sleep(100);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();



    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    public void onAccessoryConnect() {
        boolean isConnected = mInterface.isConnected();
        //debugToast("Is Connected!");
        mSensor = new SensorBMP085(mInterface, this); //need to figure out when to get values and where to save them
        startContinuousGet();
    }

    private void startContinuousGet() { //thread to get pressure and temp values
        final int OSR = 0;
        start = SystemClock.elapsedRealtime();
        counter = 0;
        new Thread (new Runnable() {
            public void run(){
                while(true) {
                    try {
                        Thread.sleep(20);
                        //debugToast(mSensor.calibrationString());
                        temp = (double) mSensor.readTemperature()/10;
                        pressureAvg.addValue(mSensor.readPressure(OSR));
                        counter++;

                        continuousGet();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (AndroidAccessoryInterface.ConnectionTimeOutException e) {
                        e.printStackTrace();
                    } catch (AndroidAccessoryInterface.NoUSBException e) {
                        e.printStackTrace();
                    }
                }

            }
        }).start();


    }

    private void continuousGet(){
        long readTime = SystemClock.elapsedRealtime();

        double frequency = 0.0;
        if (readTime - start >= 1000 && frequency ==  0.0){
                frequency = ((double) counter)/((readTime-start)/1000);
        }

        final double dispPres = pressureAvg.getAvg();
        final double pressureRange = pressureAvg.getStdDev();
        final double elevationRange = mReaderMETAR.altitude(dispPres-.5*pressureRange)-mReaderMETAR.altitude(dispPres+.5*pressureRange);
        final double altitude = mReaderMETAR.altitude(dispPres); //dispPres goes here!!!
        //debugToast("Altitude not connected");
        final int floorNum = mFloorConversion.convert(altitude);
        final double printTemp = temp;
        final double MeasurementRate = frequency;

        mMainThreadHandler.post(new Runnable(){
            public void run(){ //reader thread
                mTextPressure.setText(String.format("%.3fPa", dispPres));
                mTemperature.setText(String.format("%.1fC", printTemp));
                mFloorNum.setText(String.format("%d Floor", floorNum));
                mAltitude.setText(String.format("%.2fm", altitude)); //worked with print
                mHgPressure.setText(String.format("%.1finHg @KTTN", urlPres));
                mMeasurementRate.setText(String.format("%.1fHz", MeasurementRate));
                melevationRange.setText(String.format("%.1fm", elevationRange));
                mpressureRange.setText(String.format("%.1fPa", pressureRange));
            }
        });
    }

    public void onAccessoryDisconnect() {
        boolean isDisconnected = (!mInterface.isConnected());
    }

    @Override
    public void onStop() {
        mInterface.onStop();
        super.onStop();
    }

    @Override
    public void onStart() {
        mInterface.onStart();
        super.onStart();
    }

    @Override
    protected void onResume() {
        mInterface.onResume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        mInterface.onPause();
        super.onPause();
    }

    private void debugToast(String message) {
        final String finalMessage = message;
        final Activity context = this;
        this.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(context, finalMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

}
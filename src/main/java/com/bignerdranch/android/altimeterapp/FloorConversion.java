package com.bignerdranch.android.altimeterapp;

/**
 * Created by hcc999 on 11/16/15.
 */
public class FloorConversion {

    public int convert(double elevation){
        elevation = elevation - 55.5;
        double floor = elevation/4;
        return (int)floor;
    }
}
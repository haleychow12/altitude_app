package com.bignerdranch.android.altimeterapp;

/**
 * Created by hcc999 on 11/16/15.
 */
class AverageExponential {
    double sum;
    int numVals;
    int interval;
    double a;
    double[] values;
    int next;
    boolean isFull;
    public AverageExponential(double alpha, int stdDevInterval){
        sum = 0;
        numVals = 0;
        next = 0;
        interval = stdDevInterval;
        values = new double[interval];
        a = alpha;
        isFull = false;

    }
    public void reset(){
        sum = 0;
        numVals = 0;
        next = 0;
        int n = values.length;
        for (int i = 0; i < n; i++){
            values[i] = 0;
        }
    }
    public void addValue(double val){
        if (numVals >= values.length) {
            sum -= values[next];
            numVals--;
        }
        sum += val;
        numVals++;
        values[next] = val;
        next++;
        if (next == values.length) {
            next = 0;
            isFull = true;//if we are replacing a value, don't want to increase numVals
        }
    }
    public double getAvg(){
        if (numVals != 0)
            return sum/numVals;
        else
            return -1.0; //error
    }
    public double getStdDev(){
        double avg = getAvg();
        if (avg == -1.0) {
            return -1.0;
        }
        double x;
        double y = 0;
        for (int i = 0; i < numVals-1; i++){
            x = values[i] - avg;
            y += x*x;
        }
        y = y/(numVals-1);
        y = Math.sqrt(y);
        return y;
    }

    public boolean isFull(){
        return isFull;
    }
}




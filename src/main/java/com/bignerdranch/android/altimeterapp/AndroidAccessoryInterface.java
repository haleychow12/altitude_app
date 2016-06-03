package com.bignerdranch.android.altimeterapp;

/**
 * Created by hcc999 on 11/16/15.
 */

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.util.Log;


public class AndroidAccessoryInterface implements Runnable{

    public class NoUSBException extends Exception{
        private static final long serialVersionUID = 5434087780304677681L;
        public NoUSBException(String string) {
            super(string);
        }
    }

    public class ConnectionTimeOutException extends Exception{
        private static final long serialVersionUID = 8173380094015127689L;

        public ConnectionTimeOutException(String string) {
            super(string);
        }
    }

    private static final String TAG = "Android Accessory Interface";
    private static final String ACTION_USB_PERMISSION = "edu.princeton.ele301.lab4.action.USB_PERMISSION";

    boolean mIsConnected;
    private UsbManager mUsbManager;
    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;

    AccessoryCallBack mCallBack;

    Context mContext;

    UsbAccessory mAccessory = null;
    ParcelFileDescriptor mFileDescriptor = null;
    FileInputStream mInputStream = null;
    FileOutputStream mOutputStream = null;

    volatile byte mAckedNum;
    Thread mThread;

    int mSentCount;
    int mReceiveCount;

    public AndroidAccessoryInterface(Context context, AccessoryCallBack cb){
        Log.d(TAG,"on Create accessory");
        mContext = context;

        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        mPermissionIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(
                ACTION_USB_PERMISSION), 0);

        mIsConnected = false;
        mCallBack = cb;
        mCount = 0;
        mFirstMessage = true;
        mBuffers = new byte[256][];
        for (int i = 0; i < 256; i++){
            mBuffers[i] = new byte[c_PACKET_SIZE];
        }
        mSentCount = 0;
        mReceiveCount = 0;
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG,"onReceive USB status");
            String action = intent.getAction();
            if (/*ACTION_USB_PERMISSION.equals(action) ||*/ UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                synchronized (this) {
                    UsbAccessory[] accessories = mUsbManager.getAccessoryList();
                    UsbAccessory accessory = (accessories == null ? null : accessories[0]);
                    if (accessory != null){
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            openAccessory(accessory);
                        } else {
                            Log.d(TAG, "permission denied for accessory " + accessory);
                        }
                        mPermissionRequestPending = false;
                    }
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                Log.d(TAG,"on ACTION_USB_ACCESSORY_DETACHED......");
                //UsbAccessory accessory = UsbManager.getAccessory(intent);
                //UsbAccessory accessory = mUsbManager.getAccessoryList()[0];
                //if (accessory != null && accessory.equals(mAccessory)) {
                closeAccessory();
                //}
            }
        }
    };

    private boolean openAccessory(UsbAccessory accessory) {
        boolean isSuccess;
        mFileDescriptor = mUsbManager.openAccessory(accessory);
        Log.d(TAG,"openAccessory, checking mFIleDescriptor");
        if (mFileDescriptor != null) {
            mAccessory = accessory;
            Log.d(TAG,"opening I/O");
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);
            isSuccess = true;
            mIsConnected = true;

            if (mThread == null){
                mThread = new Thread(null, this, "bitbang");
                Log.d(TAG,"on Thread start......");
                mThread.start();
            }

            // do a little test
            try {
                readPin(0);
                readPin(1);
                mCallBack.onAccessoryConnect();
            } catch (Exception e) {
                Log.d(TAG, "accessory open fail, test doesn't work");
                isSuccess = false;
                mIsConnected = false;
                mCallBack.onAccessoryDisconnect();
            }
        }
        else {
            Log.d(TAG, "accessory open fail");
            isSuccess = false;
            mIsConnected = false;
            mCallBack.onAccessoryDisconnect();
        }
        return isSuccess;
    }

    private void reconnect(){
        Log.d(TAG,"reconnect");
        //Intent intent = getIntent();
        // if (mInputStream != null && mOutputStream != null) {
        // 	return;
        // }
        if (mIsConnected)
            return;

        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[0]);
        if (accessory != null) {
            if (mUsbManager.hasPermission(accessory)) {
                Log.d(TAG,"Has permission");
                openAccessory(accessory);
            } else {
                Log.d(TAG,"no permission");
                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        mUsbManager.requestPermission(accessory,
                                mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        } else {
            Log.d(TAG, "reconnect failed, mAccessory is null");
            mCallBack.onAccessoryDisconnect();
        }
    }

    public void onResume(){
        Log.d(TAG,"onResume");
        reconnect();
    }

    public void onPause(){
        Log.d(TAG,"onPause");
    }

    public void onStop(){
        Log.d(TAG,"onStop");
        if (mIsConnected)
            closeAccessory();
        mContext.unregisterReceiver(mUsbReceiver);
    }

    public void onStart(){
        Log.d(TAG,"onStart");
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        mContext.registerReceiver(mUsbReceiver, filter);
    }

    public boolean isConnected(){
        return mIsConnected;
    }

    private void writeTerminateThread(){
        byte[] buffer = new byte[c_PACKET_SIZE];
        buffer[0] = c_SEND_KILL;
        try{
            writeBuffer(buffer);
        }
        catch (NoUSBException e){
            // Nothing to do here if device is disconnected
        }
        catch (ConnectionTimeOutException e){};
    }

    @SuppressWarnings("deprecation")
    private void closeAccessory() {
        Log.d(TAG,"closeAccessory");

        mReadThreadDoneReading = false;
        if (mThread != null){
            mThread.interrupt();
            writeTerminateThread();
            for (int i = 0; i < 100000; i++){
                if (mReadThreadDoneReading)
                    break;
            }
            try {
                int timeout = 1000; // millis
                mThread.join(timeout);
            }
            catch (InterruptedException e) {};

            if (mThread.isAlive())
                mThread.stop();
            mThread = null;
        }

        try {
            mOutputStream.close();
            mInputStream.close();
        }
        catch (IOException e) {
            Log.d(TAG,"warning i/o not closed");
        }
        finally{
            Log.d(TAG,"runThread closed");
            mInputStream = null;
            mOutputStream = null;
        }

        try {
            if (mFileDescriptor != null) {
                mFileDescriptor.close();
                Log.d(TAG,"closed successfully");
            }
        }
        catch (IOException e) {
            Log.d(TAG,"closed not successfully");
        }
        finally {
            mFileDescriptor = null;
            mAccessory = null;
        }
        mIsConnected = false;
        mCallBack.onAccessoryDisconnect();
    }

    // read thread
    boolean mReadThreadDoneReading;
    public void run() {
        Log.d(TAG,"runThread started");
        byte[] buffer = new byte[16384];
        while(!Thread.interrupted()) {
            try{
                mInputStream.read(buffer, 0, c_PACKET_SIZE);
                mReadThreadDoneReading = true;
                Log.d(TAG,"runThreadReceived");
                packetHandler(buffer);
            }
            catch (IOException e) {
                Log.d(TAG,"runThread reading data error or closed");
                mReadThreadDoneReading = true;
                Log.d(TAG,"runThread exiting through exception");
                return;
            }
        }
        Log.d(TAG,"runThread exiting");
    }

    void sleep(int j){
        for (int i = 0; i < j; i++){

        }
    }


    private final static int c_PACKET_SIZE = 16;
    public static final boolean LOW = false;
    public static final boolean HIGH = true;

    public final static int c_PIN_SCL = 0;
    public final static int c_PIN_SDA = 1;

    private final static int c_CMD_ERROR = 0;
    private final static int c_PIN_ACCESS = 1;
    private final static int c_SEND_KILL = 99;
    private final static int c_CMD_I2C = 20;
    private final static int c_FLUSH = 100;

    private final static int c_ACK_PIN_READ = 1;
    private final static int c_ACK_PIN_WRITE = 2;
    private final static int c_ACK_I2C = 20;
    private final static int c_ACK_ERROR = 98;
    private final static int c_ACK_RESEND = 100;

    ////////////////
    // PIN ACCESS //
    ///////////////

    // INPUT
    final static int c_PIN_RW_POS = 8;
    final static int c_PIN_RW_READ = 1;
    final static int c_PIN_RW_WRITE = 2;

    final static int c_PIN_SEL_POS = 9;
    final static int c_PIN_VAL_POS = 10;


    // OUTPUT
    final static int c_PIN_ACK_READ_RESULT_POS = 8;
    ////////////
    // I2C //
    ////////////
    // INPUT
    final static int c_I2C_POS_ADDRESS = 1;
    final static int c_I2C_POS_COMMAND = 4;
    final static int c_I2C_POS_SUB_COMMAND = 0;
    final static int c_I2C_POS_SUB_REGISTER = 1;
    final static int c_I2C_POS_SUB_DELAY = 2;
    final static int c_I2C_POS_SUB_DATA = 3;
    final static int c_I2C_NUM_COM = 3;
    final static int c_I2C_LEN_COM = 4;
    // OUTPUT
    final static int c_I2C_POS_REPLY = 4;
    final static int c_I2C_POS_SUB_REPLY_RESULT = 0;
    final static int c_I2C_POS_SUB_REPLY_DATA = 1;
    // COMMANDS
    final static int c_I2C_COMMAND_NOP = 0;
    final static int c_I2C_COMMAND_READ = 1;
    final static int c_I2C_COMMAND_WRITE = 2;
    final static int c_I2C_COMMAND_READ16 = 3;

    byte[][] mBuffers;
    byte[] getBuffer(int index){
        return mBuffers[index];
    }

    byte mCount;
    public synchronized void writeBuffer(byte[] buffer) throws NoUSBException, ConnectionTimeOutException{
        buffer[3] = mCount;
        mCount++;

        if (!mIsConnected) throw new NoUSBException("USB not connected");
        try {
            Log.d(TAG, "Writing msg " + (mCount - 1));
            mAckedNum = (byte) (mCount - 2);
            int retries = 0;
            while (mAckedNum != (byte)(mCount - 1)){
                mOutputStream.write(buffer, 0 , c_PACKET_SIZE);
                mOutputStream.flush();
                mSentCount++;
                sleep(20000);
                retries++;
                if (retries == 1000){
                    throw new ConnectionTimeOutException("Timedout");
                }
            }
            Log.d(TAG, "Done writing msg " + (mCount - 1));
        }
        catch (IOException e) {
            Log.d(TAG, "write failed.", e);
            throw new ConnectionTimeOutException("Timedout");
        }
    }

    public synchronized void writePin(int pin, boolean value) throws NoUSBException,ConnectionTimeOutException{
        Log.d(TAG, "writePin");
        byte[] buffer = new byte[c_PACKET_SIZE];
        buffer[0] = c_PIN_ACCESS;
        buffer[c_PIN_RW_POS] = c_PIN_RW_WRITE;
        buffer[c_PIN_SEL_POS] = (byte)pin;
        byte pinval;
        if (value == LOW) pinval = 0;
        else pinval = 1;
        buffer[c_PIN_VAL_POS] = pinval;
        writeBuffer(buffer);
    }

    public synchronized boolean readPin(int pin) throws NoUSBException,ConnectionTimeOutException{
        Log.d(TAG, "readPin");
        byte[] buffer = new byte[c_PACKET_SIZE];
        buffer[0] = c_PIN_ACCESS;
        buffer[c_PIN_RW_POS] = c_PIN_RW_READ;
        buffer[c_PIN_SEL_POS] = (byte)pin;

        writeBuffer(buffer);

        while(mReadPinResultQueue.isEmpty()){
        }

        boolean val = LOW;
        if (!mReadPinResultQueue.isEmpty())
            val = mReadPinResultQueue.remove();
        else{
            Log.d(TAG, "IMPOSSIBLE!!!!!!!!!!");
            val = mReadPinResultQueue.remove(); // this will crash the program
        }

        Log.d(TAG, "ACK READ " + val);
        return val;
    }

    public synchronized int i2cRead(byte address, byte register) throws NoUSBException, ConnectionTimeOutException {
        byte[] buffer = new byte[c_PACKET_SIZE];
        buffer[0] = c_CMD_I2C;
        buffer[c_I2C_POS_ADDRESS] = address;
        buffer[c_I2C_POS_COMMAND+c_I2C_POS_SUB_COMMAND] = c_I2C_COMMAND_READ;
        buffer[c_I2C_POS_COMMAND+c_I2C_POS_SUB_DELAY] = 0;
        buffer[c_I2C_POS_COMMAND+c_I2C_POS_SUB_REGISTER] = register;
        buffer[c_I2C_POS_COMMAND+c_I2C_POS_SUB_DATA] = 0;

        for(int i = 1; i<c_I2C_NUM_COM; i++){
            buffer[c_I2C_POS_COMMAND+c_I2C_LEN_COM*i + c_I2C_POS_SUB_COMMAND] = c_I2C_COMMAND_NOP;
        }

        writeBuffer(buffer);

        while(mI2CResultQueue.isEmpty()){
        }

        return (int) (mI2CResultQueue.remove()&0xff);
    }

    public synchronized int i2cRead16(byte address, byte register) throws NoUSBException, ConnectionTimeOutException {
        byte[] buffer = new byte[c_PACKET_SIZE];
        buffer[0] = c_CMD_I2C;
        buffer[c_I2C_POS_ADDRESS] = address;
        buffer[c_I2C_POS_COMMAND+c_I2C_POS_SUB_COMMAND] = c_I2C_COMMAND_READ16;
        buffer[c_I2C_POS_COMMAND+c_I2C_POS_SUB_DELAY] = 0;
        buffer[c_I2C_POS_COMMAND+c_I2C_POS_SUB_REGISTER] = register;
        buffer[c_I2C_POS_COMMAND+c_I2C_POS_SUB_DATA] = 0;

        for(int i = 1; i<c_I2C_NUM_COM; i++){
            buffer[c_I2C_POS_COMMAND+c_I2C_LEN_COM*i + c_I2C_POS_SUB_COMMAND] = c_I2C_COMMAND_NOP;
        }

        writeBuffer(buffer);

        while(mI2CResultQueue.isEmpty()){
        }

        return (int) (mI2CResultQueue.remove()&0xffff);
    }

    public synchronized int i2cRead16s(byte address, byte register) throws NoUSBException, ConnectionTimeOutException {
        int msb = i2cRead(address,register);
        int lsb = i2cRead(address,(byte)(register+1));
        return (int)((lsb&0xff)| ((msb&0xff) <<8));
    }

    public synchronized boolean i2cWrite(byte address, byte register, byte data) throws NoUSBException, ConnectionTimeOutException{
        byte[] buffer = new byte[c_PACKET_SIZE];
        buffer[0] = c_CMD_I2C;
        buffer[c_I2C_POS_ADDRESS] = address;
        buffer[c_I2C_POS_COMMAND+c_I2C_POS_SUB_COMMAND] = c_I2C_COMMAND_WRITE;
        buffer[c_I2C_POS_COMMAND+c_I2C_POS_SUB_DELAY] = 0;
        buffer[c_I2C_POS_COMMAND+c_I2C_POS_SUB_REGISTER] = register;
        buffer[c_I2C_POS_COMMAND+c_I2C_POS_SUB_DATA] = data;

        for(int i = 1; i<c_I2C_NUM_COM; i++){
            buffer[c_I2C_POS_COMMAND+c_I2C_LEN_COM*i + c_I2C_POS_SUB_COMMAND] = c_I2C_COMMAND_NOP;
        }

        writeBuffer(buffer);

        while(mI2CResultQueue.isEmpty()){
        }

        mI2CResultQueue.remove();

        return true;
    }

    ArrayDeque<Boolean> mReadPinResultQueue = new ArrayDeque<Boolean>();
    ArrayDeque<Integer> mI2CResultQueue = new ArrayDeque<Integer>();

    byte mSeenCount = 0;
    boolean mFirstMessage = true;
    void packetHandler(byte[] data){
        Log.d(TAG,"onReadCallback");
        mReceiveCount++;
        byte command = data[0];

        // Check for messages
        byte master_count = data[3];
        mAckedNum = master_count;
        //byte slave_count = data[2];
        if (mFirstMessage){
            mSeenCount = master_count;
            mFirstMessage = false;
        }
        else{
            if (mSeenCount == master_count) return;
            else mSeenCount = master_count;
            //assert((mSeenCount + 1) == master_count || (mSeenCount + 2) == master_count);
        }


        byte byteData;
        boolean boolData;
        switch(command){
            case c_ACK_PIN_READ:
                Log.d(TAG, "c_ACK_PIN_READ");
                byteData = data[c_PIN_ACK_READ_RESULT_POS];
                boolData = (byteData == 0) ? LOW : HIGH;
                mReadPinResultQueue.add(boolData);
                break;
            case c_ACK_PIN_WRITE:
                Log.d(TAG, "c_ACK_PIN_WRITE");
                break;
            case c_ACK_ERROR:
                Log.d(TAG, "c_ACK_ERROR");
                break;
            case c_SEND_KILL:
                Log.d(TAG, "c_ACK_SEND_KILL");
                break;
            case c_ACK_I2C:
                Integer x;
                x = (data[c_I2C_POS_REPLY+c_I2C_POS_SUB_REPLY_DATA] & 0xff)
                        | ((data[c_I2C_POS_REPLY+c_I2C_POS_SUB_REPLY_DATA+1] & 0xff)<<8);
                mI2CResultQueue.add(x);
                break;
            default:
                Log.d(TAG, "c_ACK_ERROR");
        }
    }

    public int getDropCount(){
        return mSentCount - mReceiveCount;
    }

    public void resetDropCount(){
        mSentCount = 0;
        mReceiveCount = 0;
    }

}
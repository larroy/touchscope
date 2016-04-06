package de.uni_weimar.mheinz.androidtouchscope;

import android.support.v7.app.AppCompatActivity;
import android.os.Handler;

import java.io.UnsupportedEncodingException;

public class RigolScope implements BaseScope
{
    private static final int RIGOL_VENDOR_ID = 6833;
    private static final int RIGOL_PRODUCT_ID = 1416;

    private static final String CHAN_1 = "CHAN1";
    private static final String CHAN_2 = "CHAN2";
    private static final String CHAN_MATH = "MATH";

    private static final int READ_RATE = 100;

    private AppCompatActivity mActivity;
    private final Object mControllerLock = new Object();
    private UsbController mUsbController = null;
    private boolean mIsConnected = false;

    private WaveRequestPool mWaves1 = new WaveRequestPool(POOL_SIZE);
    private WaveRequestPool mWaves2 = new WaveRequestPool(POOL_SIZE);
    private WaveRequestPool mWavesM = new WaveRequestPool(POOL_SIZE);

    private boolean mIsChan1On = false;
    private boolean mIsChan2On = false;
    private boolean mIsChanMOn = false;

    private Handler mReadHandler = new Handler();

    public RigolScope(AppCompatActivity activity)
    {
        mActivity = activity;
    }

    public void open()
    {
        final RigolScope scope = this;
        mUsbController = new UsbController(mActivity, RIGOL_VENDOR_ID, RIGOL_PRODUCT_ID);
        mUsbController.open(new UsbController.OnDeviceChange()
        {
            @Override
            public void start()
            {
                mIsConnected = true;
                initSettings();
            }

            @Override
            public void stop()
            {
                mIsConnected = false;
                scope.stop();
            }
        });
    }

    public void close()
    {
        stop();

        if(mUsbController != null)
            mUsbController.close();
        mUsbController = null;
    }

    public void start()
    {
        stop();
        mReadHandler.postDelayed(mReadRunnable, 0);
    }

    public void stop()
    {
        mReadHandler.removeCallbacks(mReadRunnable);
    }

    public boolean isConnected()
    {
        return mIsConnected;
    }

    private void initSettings()
    {
        if(mUsbController == null)
            return;

        synchronized(mControllerLock)
        {
            mUsbController.write(":WAV:POIN:MODE NOR");
            forceCommand();
        }
    }

    public String getName()
    {
        if(mUsbController == null || !mIsConnected)
            return null;

        synchronized(mControllerLock)
        {
            mUsbController.write("*IDN?");
            byte[] data = mUsbController.read(300);
            forceCommand();
            return new String(data);
        }
    }

    public int doCommand(Command command, int channel, boolean force)
    {
        int val = 0;

        if(mUsbController == null || !mIsConnected)
            return val;

        synchronized(mControllerLock)
        {
            switch(command)
            {
                case IS_CHANNEL_ON:
                    val = isChannelOn(channel) ? 1 : 0;
                    break;
                case NO_COMMAND:
                default:
                    break;
            }

            if(force)
                forceCommand();
        }

        return val;
    }

    public WaveData getWave(int chan)
    {
        WaveData waveData = null;
        switch(chan)
        {
            case 1:
                waveData = mWaves1.peek();
                break;
            case 2:
                waveData = mWaves2.peek();
                break;
            case 3:
                waveData = mWavesM.peek();
                break;
        }
        return waveData;
    }

    private String getChannel(int chan)
    {
        String channel;
        switch (chan)
        {
            case 2:
                channel = CHAN_2;
                break;
            case 3:
                channel = CHAN_MATH;
                break;
            case 1:
            default:
                channel = CHAN_1;
                break;
        }

        return channel;
    }

    private boolean isChannelOn(int channel)
    {
        mUsbController.write(":" + getChannel(channel) + ":DISP?");
        byte[] on = mUsbController.read(20);
        boolean isOn = on.length > 0 && on[0] == 49;

        switch (channel)
        {
            case 1:
                mIsChan1On = isOn;
                break;
            case 2:
                mIsChan2On = isOn;
                break;
            case 3:
                mIsChanMOn = isOn;
                break;
        }

        return isOn;
    }

    private void readWave(int channel)
    {
        WaveData waveData;
        switch(channel)
        {
            case 1:
                waveData = mWaves1.requestWaveData();
                break;
            case 2:
                waveData = mWaves2.requestWaveData();
                break;
            case 3:
            default:
                waveData = mWavesM.requestWaveData();
                break;
        }

        synchronized(mControllerLock)
        {
            // get the raw data
            mUsbController.write(":WAV:DATA? " + getChannel(channel));
            waveData.data = mUsbController.read(610);

            //Get the voltage scale
            mUsbController.write(":" + getChannel(channel) + ":SCAL?");
            waveData.voltageScale = bytesToDouble(mUsbController.read(20));

            // And the voltage offset
            mUsbController.write(":" + getChannel(channel) + ":OFFS?");
            waveData.voltageOffset = bytesToDouble(mUsbController.read(20));

            // Get the timescale
            mUsbController.write(":TIM:SCAL?");
            waveData.timeScale = bytesToDouble(mUsbController.read(20));

            // Get the timescale offset
            mUsbController.write(":TIM:OFFS?");
            waveData.timeOffset = bytesToDouble(mUsbController.read(20));

            forceCommand();
        }

        switch (channel)
        {
            case 1:
                mWaves1.add(waveData);
                break;
            case 2:
                mWaves2.add(waveData);
                break;
            case 3:
                mWavesM.add(waveData);
                break;
        }
    }

    private float bytesToDouble(byte[] bytes)
    {
        float value = 0.0f;
        try
        {
            String strValue = new String(bytes, "UTF-8");
            value = Float.parseFloat(strValue);
        }
        catch(UnsupportedEncodingException e)
        {
            e.printStackTrace();
        }
        return value;
    }

    // use to re-allow human reaction with the scope
    private void forceCommand()
    {
        mUsbController.write(":KEY:FORC");
    }

    public static float actualVoltage(float offset, float scale, byte point)
    {
        // Walk through the data, and map it to actual voltages
        // This mapping is from Cibo Mahto
        // First invert the data
        double tPoint = point * -1 + 255;

        // Now, we know from experimentation that the scope display range is actually
        // 30-229.  So shift by 130 - the voltage offset in counts, then scale to
        // get the actual voltage.

        tPoint = (tPoint - 130.0 - (offset/ scale * 25)) / 25 * scale;
        return (float)tPoint;
    }

    private Runnable mReadRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            if(mIsChan1On)
                readWave(1);

            if(mIsChan2On)
                readWave(2);

            if(mIsChanMOn)
                readWave(3);

            mReadHandler.postDelayed(this, READ_RATE);
        }
    };
}

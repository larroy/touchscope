/*
 * MIT License
 *
 * Copyright (c) 2016 Matthew Heinz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package de.uni_weimar.mheinz.androidtouchscope.scope.wave;

import java.util.ArrayDeque;

public class WaveRequestPool
{
    private final int mCapacity;
    private final ArrayDeque<WaveData> mWaves;

    public WaveRequestPool(int capacity)
    {
        mCapacity = capacity;
        mWaves = new ArrayDeque<>(capacity);
    }

    public WaveData requestWaveData()
    {
        synchronized(mWaves)
        {
            WaveData waveData;
            if(mWaves.size() < mCapacity)
            {
                waveData = new WaveData();
            }
            else
            {
                waveData = mWaves.getFirst();
            }
            return waveData;
        }
    }

    public void add(WaveData waveData)
    {
        synchronized(mWaves)
        {
            if(mWaves.size() == mCapacity)
                mWaves.removeFirst();
            mWaves.addLast(waveData);
        }
    }

    public WaveData peek()
    {
        return mWaves.peek();
    }

    public WaveData poll()
    {
        synchronized(mWaves)
        {
            return mWaves.getFirst();
        }
    }
}
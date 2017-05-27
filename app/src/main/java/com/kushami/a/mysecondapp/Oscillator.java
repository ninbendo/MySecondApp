package com.kushami.a.mysecondapp;

/**
 * Created by a on 2017/04/29.
 */

public class Oscillator {
    private double frequency = 440;
    private double[] buffer;
    private double t = 0;
    private double sampleRate;

    public Oscillator(int bufferSize, int sampleRate) {
        buffer = new double[bufferSize];
        this.sampleRate = sampleRate;
    }

    /**
     * 呼び出す度に波形の一部を生成する。
     */
    public double[] nextBuffer() {
        for (int i = 0; i < buffer.length; i++) {
            // まずサイン波を生成して値が正なら1、負なら-1とすることで矩形波を生成する。
            double sin = Math.sin(2 * Math.PI * t * frequency);
            buffer[i] = sin > 0 ? 1 : -1;

            t += 1 / sampleRate;
            t = t % (2.0d * Math.PI); // これを記述すると途切れが少ない
        }
        return buffer;
    }

    /**
     * 生成波形の位相を初期状態に戻す。
     */
    public void reset() {
        t = 0;
    }

    public void setFrequency(double frequency) {
        this.frequency = frequency;
    }

}

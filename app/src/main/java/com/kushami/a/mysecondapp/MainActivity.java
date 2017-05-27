package com.kushami.a.mysecondapp;

import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    //タイマー処理
    Timer timerEntrance = null;
    Handler handler = new Handler();
    Timer timerExit = null;
    Handler handler2 = new Handler();
    String intervalValue;
    //AudioTrackで指定する変数
    private static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;
    private static final int SAMPLE_RATE = 48000; // 44100, 22050は試したが音が途切れた
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    private static final int MODE = AudioTrack.MODE_STREAM;
    private static final int RANGE_FREQUENCY_MAX = 24000;
    //左音声用発信器
    private Oscillator oscLeft = new Oscillator(BUFFER_SIZE, SAMPLE_RATE);
    //右音声用発信器
    private Oscillator oscRight = new Oscillator(BUFFER_SIZE, SAMPLE_RATE);
    //音声再生インスタンス
    private AudioTrack audioTrack;

    private Thread backgroundThread;
    private boolean running;
    private boolean touching;
    private short three_way = 0;

    private Button startButton;
    private Button endButton;

    private SeekBar freqBarLeft;
    private TextView freqTextLeft;
    private SeekBar freqBarRight;
    private TextView freqTextRight;

    private SeekBar volumeBar;
    private TextView volumeText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //スリープ状態にしない
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        //自動回転しない
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        //各部品の初期化
        initialize();
    }

    private void initialize() {
        startButton = (Button) findViewById(R.id.button1);
        endButton = (Button) findViewById(R.id.button2);
        endButton.setEnabled(false);
        /**
         * 周波数調整部品
         * */
        //左音声周波数の初期設定
        freqTextLeft = (TextView) findViewById(R.id.textView1);
        freqTextRight = (TextView) findViewById(R.id.textView2);

        freqBarLeft = (SeekBar) findViewById(R.id.seekBar1);
        freqBarRight = (SeekBar) findViewById(R.id.seekBar2);

        freqBarLeft.setMax(RANGE_FREQUENCY_MAX);
        freqBarLeft.setProgress(9800);
        int freqBarProgress = freqBarLeft.getProgress();
        oscLeft.setFrequency((double) freqBarProgress);
        freqTextLeft.setText(String.valueOf(freqBarProgress) + " Hz");

        freqBarRight.setMax(RANGE_FREQUENCY_MAX);
        freqBarRight.setProgress(9815);
        freqBarProgress = freqBarRight.getProgress();
        oscRight.setFrequency((double) freqBarProgress);
        freqTextRight.setText(String.valueOf(freqBarProgress) + " Hz");

        freqBarLeft.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                oscLeft.setFrequency((double) progress);
                freqTextLeft.setText(String.valueOf(progress) + " Hz");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        freqBarRight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                oscRight.setFrequency((double)progress);
                freqTextRight.setText(String.valueOf(progress) + " Hz");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        //音量
        volumeText = (TextView) findViewById(R.id.textView3);
        volumeBar = (SeekBar) findViewById(R.id.seekBar3);
        volumeText.setText(String.valueOf(volumeBar.getProgress()) + "%");
        volumeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float volume = (float) progress / 100;
                audioTrack.setStereoVolume(volume, volume);
                //audioTrack.setVolume(0.0f, volume); api Lv が高い場合こちらを使用
                volumeText.setText(String.valueOf(progress) + "%");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        //初回音声設定 左右両方
        RadioButton radioButtonBoth = (RadioButton) findViewById(R.id.radioButton2);
        radioButtonBoth.setChecked(true);
        //最初は予約解除ボタン非活性
        Button cancelButton = (Button) findViewById(R.id.button10);
        cancelButton.setEnabled(false);
    }

    /**
     * 再生ボタンを押下時の処理
     * */
    public void onPlay(View view) {
        startButton.setEnabled(false);
        endButton.setEnabled(true);
        touching = true; // true バッファ処理を継続、つまり音が鳴る / false バッファ処理で0を生成し、再生を停止
    }

    /**
     * 左周波数増加処理
     * */
    public void onPlusLeft(View view) {
        //左周波数
        int freqBarProgress = freqBarLeft.getProgress();
        if (freqBarProgress < RANGE_FREQUENCY_MAX) {
            freqBarProgress = freqBarProgress + 1;
            freqBarLeft.setProgress(freqBarProgress);
        }
    }

    /**
     * 右周波数増加処理
     * */
    public void onPlusRight(View view) {
        //右周波数
        int freqBarProgress = freqBarRight.getProgress();
        if (freqBarProgress < RANGE_FREQUENCY_MAX) {
            freqBarProgress = freqBarProgress + 1;
            freqBarRight.setProgress(freqBarProgress);
        }
    }

    /**
     * 左周波数減少処理
     * */
    public void onMinusLeft(View view) {
        //左周波数
        int freqBarProgress = freqBarLeft.getProgress();
        if (freqBarProgress > 0) {
            freqBarProgress = freqBarProgress - 1 ;
            freqBarLeft.setProgress(freqBarProgress);
        }
    }

    /**
     * 右周波数減少処理
     * */
    public void onMinusRight(View view) {
        //右周波数
        int freqBarProgress = freqBarRight.getProgress();
        if (freqBarProgress > 0) {
            freqBarProgress = freqBarProgress - 1 ;
            freqBarRight.setProgress(freqBarProgress);
        }
    }

    /**
     * 音量増加処理
     * */
    public void onPlusVolume(View view) {
        //音量上昇
        int volumeBarProgress = volumeBar.getProgress();
        if (volumeBarProgress < 100) {
            volumeBarProgress = volumeBarProgress + 1;
            volumeBar.setProgress(volumeBarProgress);
        }
    }

    /**
     * 音量減少処理
     * */
    public void onMinusVolume(View view) {
        //音量下降
        int volumeBarProgress = volumeBar.getProgress();
        if (volumeBarProgress > 0) {
            volumeBarProgress = volumeBarProgress - 1;
            volumeBar.setProgress(volumeBarProgress);
        }
    }

    /**
     * 左音声に切り替え
     * */
    public void onSwitchLeft(View view) {
        RadioButton radioButtonBoth = (RadioButton) findViewById(R.id.radioButton2);
        radioButtonBoth.setChecked(false);
        RadioButton radioButtonRight = (RadioButton) findViewById(R.id.radioButton3);
        radioButtonRight.setChecked(false);

        three_way = 1;
    }

    /**
     * 左右音声に切り替え
     * */
    public void onSwitchBoth(View view) {
        RadioButton radioButtonLeft = (RadioButton) findViewById(R.id.radioButton1);
        radioButtonLeft.setChecked(false);
        RadioButton radioButtonRight = (RadioButton) findViewById(R.id.radioButton3);
        radioButtonRight.setChecked(false);

        three_way = 0;
    }

    /**
     * 右音声に切り替え
     * */
    public void onSwitchRight(View view) {
        RadioButton radioButtonLeft = (RadioButton) findViewById(R.id.radioButton1);
        radioButtonLeft.setChecked(false);
        RadioButton radioButtonBoth = (RadioButton) findViewById(R.id.radioButton2);
        radioButtonBoth.setChecked(false);

        three_way = -1;
    }

    /**
     * タイマー予約処理
     * */
    public void onTimer(View view) {
        //鳴動停止
        endButton.callOnClick();
        //入力チェック
        EditText after = (EditText) findViewById(R.id.editText5);
        SpannableStringBuilder sb = (SpannableStringBuilder) after.getText();
        String afterValue = sb.toString();
        if ("".equals(afterValue)) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle("入力エラー");
            alert.setMessage("起動予定の分数を入力してください。");
            alert.setPositiveButton("OK", null);
            alert.show();
            return;
        }
        //入力チェック2
        EditText interval = (EditText) findViewById(R.id.editText6);
        sb = (SpannableStringBuilder) interval.getText();
        intervalValue = sb.toString();
        if ("".equals(intervalValue)) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle("入力エラー2");
            alert.setMessage("作動させる分数を入力してください。");
            alert.setPositiveButton("OK", null);
            alert.show();
            return;
        }
        //タイマー予約
        if (timerEntrance != null) timerEntrance = null;
        if (timerExit != null) timerExit = null;

        if(timerEntrance == null)
        {
            //==== タイマー作成 & スタート ====//
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.MINUTE, Integer.parseInt(afterValue));
            Date obeTime = cal.getTime();
            timerEntrance = new Timer();
            timerEntrance.schedule(new TimerTask()
            {
                @Override
                public void run()
                {
                    handler.post(new Runnable()
                    {
                        public void run()
                        {
                            //鳴動開始
                            startButton.callOnClick();
                            // 停止タイマーセット //
                            exitTimerSet(Integer.parseInt(intervalValue));
                        }
                    });
                }
            }, obeTime);
            //予約完了通知
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 HH時mm分ss秒");
            Toast.makeText(this, sdf.format(obeTime) + "に起動します", Toast.LENGTH_SHORT).show();
        }
        //予約ボタン非活性化
        Button timerButton = (Button) findViewById(R.id.button9);
        timerButton.setEnabled(false);
        //予約解除ボタン活性化
        Button cancelButton = (Button) findViewById(R.id.button10);
        cancelButton.setEnabled(true);
    }

    /**
     * タイマー解除処理
     * */
    private void exitTimerSet(int interval) {
        if (timerExit == null) {
            Calendar cal2 = Calendar.getInstance();
            cal2.add(Calendar.MINUTE, interval);
            Date returnTime = cal2.getTime();
            timerExit = new Timer();
            timerExit.schedule(new TimerTask() {
                @Override
                public void run() {
                    handler2.post(new Runnable() {
                        public void run() {
                            //鳴動停止
                            endButton.callOnClick();
                            if(timerEntrance != null)
                            {
                                timerEntrance.cancel();
                                timerEntrance.purge();
                                timerEntrance = null;
                            }
                        }
                    });
                }
            }, returnTime);
        }
    }

    /**
     * 予約解除処理
     * */
    public void onCancel(View view) {
        if(timerEntrance != null)
        {
            timerEntrance.cancel();
            timerEntrance.purge();
            timerEntrance = null;
        }
        if(timerExit != null)
        {
            timerExit.cancel();
            timerExit.purge();
            timerExit = null;
        }
        Toast.makeText(this, "予約を解除しました", Toast.LENGTH_SHORT).show();
        //予約ボタン活性化
        Button timerButton = (Button) findViewById(R.id.button9);
        timerButton.setEnabled(true);
        //予約解除ボタン非活性化
        Button cancelButton = (Button) findViewById(R.id.button10);
        cancelButton.setEnabled(false);
    }

    /**
     * 停止処理
     * */
    public void onHalt(View view) {
        startButton.setEnabled(true);
        endButton.setEnabled(false);
        /*****  重要処理 START  *****/
        touching = false;
        /*****  重要処理 END  *****/
    }

    @Override
    protected void onResume() {
        super.onResume();
        //音量を取得
        float volume = (float) volumeBar.getProgress() / 100;
        //左音声インスタンス生成
        audioTrack = new AudioTrack(STREAM_TYPE, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, BUFFER_SIZE, MODE);
        audioTrack.setStereoVolume(volume, volume);
        //左音声周波数設定
        int freqBarProgress = freqBarLeft.getProgress();
        oscLeft.setFrequency((double) freqBarProgress);
        //右音声周波数設定
        freqBarProgress = freqBarRight.getProgress();
        oscRight.setFrequency((double) freqBarProgress);
        //音声を流す
        startBackgroundThread();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBackgroundThread();
        audioTrack.release();
        audioTrack = null;
    }

    private void startBackgroundThread() {
        running = true;

        audioTrack.play();
        oscLeft.reset();
        oscRight.reset();

        backgroundThread = new Thread() {
            @Override
            public void run() {
                short[] sBuffer = new short[BUFFER_SIZE];
                while (running) {
                    // 画面を触っている間のみ音を鳴らす
                    if (touching) {
                        double[] dBufferLeft = oscLeft.nextBuffer();
                        double[] dBufferRight = oscRight.nextBuffer();
                        // bufferには -1 〜 +1 のデータが入るので、shortの値域に変換する
                        for (int i = 0; i < BUFFER_SIZE; i++) {
                            if(three_way == 1) {
                                if (i % 2 == 0) {
                                    //左音声信号のみ生成
                                    sBuffer[i] = (short) (dBufferLeft[i] * Short.MAX_VALUE);
                                } else {
                                    //右音声信号は0
                                    sBuffer[i] = (short) 0;
                                }
                            } else if (three_way == -1) {
                                if (i % 2 == 0) {
                                    //左音声信号は0
                                    sBuffer[i] = (short) 0;
                                } else {
                                    //右音声信号のみ生成
                                    sBuffer[i] = (short) (dBufferRight[i] * Short.MAX_VALUE);
                                }
                            } else {
                                if (i % 2 == 0) {
                                    //左音声信号の生成
                                    sBuffer[i] = (short) (dBufferLeft[i] * Short.MAX_VALUE);
                                } else {
                                    //右音声信号の生成
                                    sBuffer[i] = (short) (dBufferRight[i] * Short.MAX_VALUE);
                                }
                            }
                        }
                    } else {
                        // 無音
                        for (int i = 0; i < BUFFER_SIZE; i++) {
                            sBuffer[i] = 0;
                        }
                    }
                    audioTrack.write(sBuffer, 0, BUFFER_SIZE);
                }
            }
        };
        backgroundThread.start();
    }


    private void stopBackgroundThread() {
        running = false;
        audioTrack.stop();
    }

    /*
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touching = true;
                break;
            case MotionEvent.ACTION_UP:
                touching = false;
                oscLeft.reset();
                break;
        }
        return super.onTouchEvent(event);
    }
    */
}

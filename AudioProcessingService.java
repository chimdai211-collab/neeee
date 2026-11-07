package com.miceq.pro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

public class AudioProcessingService extends Service {

    private static final String CHANNEL_ID = "MicEQChannel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_START = "com.miceq.pro.START";
    public static final String ACTION_STOP  = "com.miceq.pro.STOP";

    // ── Audio config ─────────────────────────────────────────────────────────
    private static final int SAMPLE_RATE    = DSPEngine.getSampleRate();
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT   = AudioFormat.ENCODING_PCM_16BIT;
    private static final int OUT_CHANNEL    = AudioFormat.CHANNEL_OUT_MONO;

    private AudioRecord recorder;
    private AudioTrack  player;
    private Thread      processingThread;
    private volatile boolean running = false;

    private DSPEngine dsp = new DSPEngine();
    private volatile float vuLevel = 0f; // 0..1 for VU meter

    // ── Binder ───────────────────────────────────────────────────────────────
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public AudioProcessingService getService() { return AudioProcessingService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_START.equals(intent.getAction())) {
            startForeground(NOTIFICATION_ID, buildNotification("Processing mic…"));
            startAudio();
        } else if (ACTION_STOP.equals(intent.getAction())) {
            stopAudio();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopAudio();
        super.onDestroy();
    }

    // ── Audio pipeline ───────────────────────────────────────────────────────

    private void startAudio() {
        if (running) return;

        int bufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        bufSize = Math.max(bufSize, 2048); // at least 2048 samples

        recorder = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufSize * 4);

        int outBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, OUT_CHANNEL, AUDIO_FORMAT);
        outBuf = Math.max(outBuf, 2048);

        player = new AudioTrack.Builder()
                .setAudioAttributes(new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(OUT_CHANNEL)
                        .build())
                .setBufferSizeInBytes(outBuf * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        recorder.startRecording();
        player.play();
        running = true;

        final int BUF = bufSize;
        processingThread = new Thread(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
            short[] buf = new short[BUF];
            while (running) {
                int read = recorder.read(buf, 0, BUF);
                if (read > 0) {
                    // VU meter RMS
                    long sum = 0;
                    for (int i = 0; i < read; i++) sum += (long) buf[i] * buf[i];
                    float rms = (float) Math.sqrt((double) sum / read);
                    vuLevel = rms / 32768f;

                    // DSP
                    dsp.process(buf, read);

                    // Playback (monitor to earpiece — optional, default off)
                    if (monitorEnabled) {
                        player.write(buf, 0, read);
                    }
                }
            }
        }, "MicEQ-DSP");
        processingThread.start();
    }

    private volatile boolean monitorEnabled = false;

    public void setMonitorEnabled(boolean en) { monitorEnabled = en; }
    public boolean isMonitorEnabled() { return monitorEnabled; }

    private void stopAudio() {
        running = false;
        if (processingThread != null) {
            try { processingThread.join(2000); } catch (InterruptedException ignored) {}
            processingThread = null;
        }
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
    }

    // ── DSP proxy ────────────────────────────────────────────────────────────

    public DSPEngine getDsp()   { return dsp; }
    public float getVuLevel()   { return vuLevel; }
    public boolean isRunning()  { return running; }

    // ── Notification ─────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "MicEQ Processing", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Microphone DSP active");
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification(String text) {
        Intent stopIntent = new Intent(this, AudioProcessingService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPI = PendingIntent.getService(this, 0, stopIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPI = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("MicEQ Pro")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(openPI)
                .addAction(android.R.drawable.ic_delete, "Stop", stopPI)
                .setOngoing(true)
                .build();
    }
}

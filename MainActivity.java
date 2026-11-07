package com.miceq.pro;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int PERM_REQUEST = 100;
    private static final String PREFS = "miceq_prefs";

    // ── Service binding ───────────────────────────────────────────────────────
    private AudioProcessingService service;
    private boolean bound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ((AudioProcessingService.LocalBinder) binder).getService();
            bound = true;
            syncUIFromDsp();
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
        }
    };

    // ── UI refs ──────────────────────────────────────────────────────────────
    private Switch  swPower;
    private Switch  swMonitor;
    private TextView tvVuDb;
    private ProgressBar pbVu;

    // Gain
    private SeekBar sbGain;
    private TextView tvGain;

    // EQ (10 bands)
    private SeekBar[] sbEq;
    private TextView[] tvEq;
    private Button btnEqReset;
    private Button btnPresetFlat, btnPresetBass, btnPresetVoice, btnPresetAir;

    // Echo
    private SeekBar sbEchoDelay, sbEchoFeedback, sbEchoMix;
    private TextView tvEchoDelay, tvEchoFeedback, tvEchoMix;

    // Reverb
    private SeekBar sbRevRoom, sbRevDamp, sbRevMix;
    private TextView tvRevRoom, tvRevDamp, tvRevMix;

    // Noise Gate
    private SeekBar sbNoiseGate;
    private TextView tvNoiseGate;

    // Compressor
    private Switch swComp;
    private SeekBar sbCompThresh, sbCompRatio, sbCompMakeup;
    private TextView tvCompThresh, tvCompRatio, tvCompMakeup;

    // ── VU ticker ────────────────────────────────────────────────────────────
    private final Handler vuHandler = new Handler(Looper.getMainLooper());
    private final Runnable vuTick = new Runnable() {
        @Override public void run() {
            if (bound && service != null && service.isRunning()) {
                float v = service.getVuLevel();
                int pct = (int)(v * 100);
                pbVu.setProgress(pct);
                float db = v > 0.0001f ? 20f * (float) Math.log10(v) : -60f;
                tvVuDb.setText(String.format(Locale.US, "%.1f dB", db));
            }
            vuHandler.postDelayed(this, 80);
        }
    };

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViews();
        setupListeners();
        requestPermissions();
    }

    private void findViews() {
        swPower   = findViewById(R.id.swPower);
        swMonitor = findViewById(R.id.swMonitor);
        tvVuDb    = findViewById(R.id.tvVuDb);
        pbVu      = findViewById(R.id.pbVu);

        sbGain    = findViewById(R.id.sbGain);
        tvGain    = findViewById(R.id.tvGain);

        int[] eqIds = {R.id.sbEq0,R.id.sbEq1,R.id.sbEq2,R.id.sbEq3,R.id.sbEq4,
                       R.id.sbEq5,R.id.sbEq6,R.id.sbEq7,R.id.sbEq8,R.id.sbEq9};
        int[] eqTvIds = {R.id.tvEq0,R.id.tvEq1,R.id.tvEq2,R.id.tvEq3,R.id.tvEq4,
                         R.id.tvEq5,R.id.tvEq6,R.id.tvEq7,R.id.tvEq8,R.id.tvEq9};
        sbEq = new SeekBar[10]; tvEq = new TextView[10];
        for (int i = 0; i < 10; i++) {
            sbEq[i] = findViewById(eqIds[i]);
            tvEq[i] = findViewById(eqTvIds[i]);
        }
        btnEqReset    = findViewById(R.id.btnEqReset);
        btnPresetFlat = findViewById(R.id.btnPresetFlat);
        btnPresetBass = findViewById(R.id.btnPresetBass);
        btnPresetVoice= findViewById(R.id.btnPresetVoice);
        btnPresetAir  = findViewById(R.id.btnPresetAir);

        sbEchoDelay    = findViewById(R.id.sbEchoDelay);
        sbEchoFeedback = findViewById(R.id.sbEchoFeedback);
        sbEchoMix      = findViewById(R.id.sbEchoMix);
        tvEchoDelay    = findViewById(R.id.tvEchoDelay);
        tvEchoFeedback = findViewById(R.id.tvEchoFeedback);
        tvEchoMix      = findViewById(R.id.tvEchoMix);

        sbRevRoom = findViewById(R.id.sbRevRoom);
        sbRevDamp = findViewById(R.id.sbRevDamp);
        sbRevMix  = findViewById(R.id.sbRevMix);
        tvRevRoom = findViewById(R.id.tvRevRoom);
        tvRevDamp = findViewById(R.id.tvRevDamp);
        tvRevMix  = findViewById(R.id.tvRevMix);

        sbNoiseGate = findViewById(R.id.sbNoiseGate);
        tvNoiseGate = findViewById(R.id.tvNoiseGate);

        swComp       = findViewById(R.id.swComp);
        sbCompThresh = findViewById(R.id.sbCompThresh);
        sbCompRatio  = findViewById(R.id.sbCompRatio);
        sbCompMakeup = findViewById(R.id.sbCompMakeup);
        tvCompThresh = findViewById(R.id.tvCompThresh);
        tvCompRatio  = findViewById(R.id.tvCompRatio);
        tvCompMakeup = findViewById(R.id.tvCompMakeup);
    }

    private void setupListeners() {
        // Power toggle
        swPower.setOnCheckedChangeListener((v, checked) -> {
            Intent svc = new Intent(this, AudioProcessingService.class);
            if (checked) {
                svc.setAction(AudioProcessingService.ACTION_START);
                startForegroundService(svc);
                bindService(svc, connection, Context.BIND_AUTO_CREATE);
            } else {
                if (bound) { unbindService(connection); bound = false; service = null; }
                svc.setAction(AudioProcessingService.ACTION_STOP);
                startService(svc);
            }
        });

        swMonitor.setOnCheckedChangeListener((v, checked) -> {
            if (bound && service != null) service.setMonitorEnabled(checked);
        });

        // Gain: 0–200 → -20..+20 dB
        sbGain.setMax(200); sbGain.setProgress(100);
        sbGain.setOnSeekBarChangeListener(new SBL() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                float db = p - 100;
                tvGain.setText(String.format(Locale.US, "%.0f dB", db));
                if (bound && service != null) service.getDsp().setGainDb(db);
            }
        });

        // EQ bands: 0–240 → -12..+12 dB
        String[] freqLabels = {"31","63","125","250","500","1k","2k","4k","8k","16k"};
        for (int i = 0; i < 10; i++) {
            final int band = i;
            sbEq[i].setMax(240); sbEq[i].setProgress(120);
            tvEq[i].setText("0 dB\n" + freqLabels[i] + "Hz");
            sbEq[i].setOnSeekBarChangeListener(new SBL() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                    float db = (p - 120) / 10f;
                    tvEq[band].setText(String.format(Locale.US, "%.1f\n%sHz", db, freqLabels[band]));
                    if (bound && service != null) service.getDsp().setEqBandDb(band, db);
                }
            });
        }

        btnEqReset.setOnClickListener(v -> applyPreset(new float[10]));
        btnPresetFlat.setOnClickListener(v -> applyPreset(new float[10]));
        btnPresetBass.setOnClickListener(v -> applyPreset(new float[]{6,5,4,2,0,0,-1,-1,-1,0}));
        btnPresetVoice.setOnClickListener(v -> applyPreset(new float[]{-4,-3,0,3,5,4,2,1,0,-1}));
        btnPresetAir.setOnClickListener(v -> applyPreset(new float[]{0,0,0,0,0,2,3,5,7,8}));

        // Echo
        sbEchoDelay.setMax(1950); sbEchoDelay.setProgress(250); // 50..2000 ms
        sbEchoDelay.setOnSeekBarChangeListener(new SBL() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                float ms = p + 50;
                tvEchoDelay.setText(String.format(Locale.US, "%.0f ms", ms));
                if (bound && service != null) service.getDsp().setEchoDelayMs(ms);
            }
        });
        sbEchoFeedback.setMax(95); sbEchoFeedback.setProgress(40);
        sbEchoFeedback.setOnSeekBarChangeListener(new SBL() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                float fb = p / 100f;
                tvEchoFeedback.setText(String.format(Locale.US, "%.2f", fb));
                if (bound && service != null) service.getDsp().setEchoFeedback(fb);
            }
        });
        sbEchoMix.setMax(100); sbEchoMix.setProgress(0);
        sbEchoMix.setOnSeekBarChangeListener(new SBL() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                float mix = p / 100f;
                tvEchoMix.setText(String.format(Locale.US, "%.0f%%", mix * 100));
                if (bound && service != null) service.getDsp().setEchoMix(mix);
            }
        });

        // Reverb
        sbRevRoom.setMax(100); sbRevRoom.setProgress(50);
        sbRevRoom.setOnSeekBarChangeListener(new SBL() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                float r = p / 100f;
                tvRevRoom.setText(String.format(Locale.US, "%.0f%%", r * 100));
                if (bound && service != null) service.getDsp().setReverbRoomSize(r);
            }
        });
        sbRevDamp.setMax(100); sbRevDamp.setProgress(50);
        sbRevDamp.setOnSeekBarChangeListener(new SBL() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                float d = p / 100f;
                tvRevDamp.setText(String.format(Locale.US, "%.0f%%", d * 100));
                if (bound && service != null) service.getDsp().setReverbDamping(d);
            }
        });
        sbRevMix.setMax(100); sbRevMix.setProgress(0);
        sbRevMix.setOnSeekBarChangeListener(new SBL() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                float m = p / 100f;
                tvRevMix.setText(String.format(Locale.US, "%.0f%%", m * 100));
                if (bound && service != null) service.getDsp().setReverbMix(m);
            }
        });

        // Noise Gate: 0–80 → -80..0 dB
        sbNoiseGate.setMax(80); sbNoiseGate.setProgress(60);
        sbNoiseGate.setOnSeekBarChangeListener(new SBL() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                float db = p - 80;
                tvNoiseGate.setText(String.format(Locale.US, "%.0f dB", db));
                if (bound && service != null) service.getDsp().setNoiseGateThreshDb(db);
            }
        });

        // Compressor
        swComp.setOnCheckedChangeListener((v, checked) -> {
            if (bound && service != null) service.getDsp().setCompressorEnabled(checked);
        });
        sbCompThresh.setMax(60); sbCompThresh.setProgress(40); // -60..0 → p-60
        sbCompThresh.setOnSeekBarChangeListener(new SBL() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                float db = p - 60;
                tvCompThresh.setText(String.format(Locale.US, "%.0f dB", db));
                if (bound && service != null) service.getDsp().setCompThreshDb(db);
            }
        });
        sbCompRatio.setMax(190); sbCompRatio.setProgress(30); // 1:1 .. 20:1
        sbCompRatio.setOnSeekBarChangeListener(new SBL() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                float r = 1f + p / 10f;
                tvCompRatio.setText(String.format(Locale.US, "%.1f:1", r));
                if (bound && service != null) service.getDsp().setCompRatio(r);
            }
        });
        sbCompMakeup.setMax(240); sbCompMakeup.setProgress(120); // -12..+12 dB
        sbCompMakeup.setOnSeekBarChangeListener(new SBL() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                float db = (p - 120) / 10f;
                tvCompMakeup.setText(String.format(Locale.US, "%.1f dB", db));
                float lin = (float) Math.pow(10, db / 20.0);
                if (bound && service != null) service.getDsp().setCompMakeupGain(lin);
            }
        });
    }

    private void applyPreset(float[] bands) {
        for (int i = 0; i < 10; i++) {
            int progress = (int)(bands[i] * 10 + 120);
            sbEq[i].setProgress(progress);
        }
    }

    private void syncUIFromDsp() {
        if (!bound || service == null) return;
        DSPEngine d = service.getDsp();
        for (int i = 0; i < 10; i++) {
            sbEq[i].setProgress((int)(d.getEqBandDb(i) * 10 + 120));
        }
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    private void requestPermissions() {
        String[] perms = Build.VERSION.SDK_INT >= 33
                ? new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS}
                : new String[]{Manifest.permission.RECORD_AUDIO};
        boolean needRequest = false;
        for (String p : perms) if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) needRequest = true;
        if (needRequest) ActivityCompat.requestPermissions(this, perms, PERM_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        boolean ok = true;
        for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) { ok = false; break; }
        if (!ok) Toast.makeText(this, "Cần quyền Microphone để hoạt động!", Toast.LENGTH_LONG).show();
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        vuHandler.post(vuTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        vuHandler.removeCallbacks(vuTick);
    }

    @Override
    protected void onDestroy() {
        if (bound) { unbindService(connection); bound = false; }
        super.onDestroy();
    }

    // ── Helper: blank SeekBar listener ───────────────────────────────────────
    private abstract static class SBL implements SeekBar.OnSeekBarChangeListener {
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }
}

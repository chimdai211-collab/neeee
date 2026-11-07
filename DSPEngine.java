package com.miceq.pro;

/**
 * Real-time DSP Engine
 * Features: Gain, 10-band EQ (IIR Biquad), Echo, Reverb (Schroeder), Noise Gate, Compressor
 */
public class DSPEngine {

    private static final int SAMPLE_RATE = 44100;

    // ── Gain ──────────────────────────────────────────────────────────────────
    private float gainLinear = 1.0f; // 1.0 = 0dB

    // ── 10-band EQ (IIR Biquad) ──────────────────────────────────────────────
    // Bands: 31 63 125 250 500 1k 2k 4k 8k 16k Hz
    private static final float[] EQ_FREQS = {31f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f};
    private static final int EQ_BANDS = 10;
    private float[] eqGainDb = new float[EQ_BANDS]; // -12 to +12 dB each

    // Biquad coefficients [band][b0,b1,b2,a1,a2]
    private float[][] bqB = new float[EQ_BANDS][3];
    private float[][] bqA = new float[EQ_BANDS][2]; // a1,a2 (a0=1 normalized)
    // State (stereo → mono, so 1 channel)
    private float[][] bqX = new float[EQ_BANDS][2]; // x[n-1], x[n-2]
    private float[][] bqY = new float[EQ_BANDS][2]; // y[n-1], y[n-2]

    // ── Echo ─────────────────────────────────────────────────────────────────
    private float echoDelayMs  = 300f;  // 50..2000 ms
    private float echoFeedback = 0.4f;  // 0..0.9
    private float echoMix      = 0.0f;  // 0..1  (0 = off)
    private float[] echoBuffer;
    private int echoWritePos = 0;
    private int echoDelaySamples;

    // ── Reverb (Schroeder 4 comb + 2 allpass) ────────────────────────────────
    private float reverbRoomSize = 0.5f; // 0..1
    private float reverbDamping  = 0.5f; // 0..1
    private float reverbMix      = 0.0f; // 0..1 (0 = off)

    private static final int[] COMB_LENGTHS   = {1557, 1617, 1491, 1422};
    private static final int[] ALLPASS_LENGTHS = {225, 556};
    private float[][] combBuffer   = new float[4][];
    private int[]     combPos      = new int[4];
    private float[]   combFilter   = new float[4]; // LPF state
    private float[][] apBuffer     = new float[2][];
    private int[]     apPos        = new int[2];

    // ── Noise Gate ───────────────────────────────────────────────────────────
    private float noiseGateThreshDb = -60f; // -80..0 dB  (-60 = off)
    private float noiseGateThreshLin;
    private float gateState = 1.0f;
    private static final float GATE_ATTACK  = 0.001f;  // per sample smoothing
    private static final float GATE_RELEASE = 0.0001f;

    // ── Compressor ───────────────────────────────────────────────────────────
    private boolean compressorEnabled = false;
    private float compThreshDb  = -20f;
    private float compRatio     = 4f;
    private float compGain      = 1.0f; // makeup gain
    private float compEnvelope  = 0f;
    private static final float COMP_ATTACK  = 0.001f;
    private static final float COMP_RELEASE = 0.0005f;

    // ─────────────────────────────────────────────────────────────────────────

    public DSPEngine() {
        for (int i = 0; i < EQ_BANDS; i++) eqGainDb[i] = 0f;
        initEQ();
        initEcho();
        initReverb();
        updateNoiseGateThresh();
    }

    // ── EQ ───────────────────────────────────────────────────────────────────

    private void initEQ() {
        for (int i = 0; i < EQ_BANDS; i++) computeBiquad(i);
    }

    private void computeBiquad(int band) {
        float dBgain = eqGainDb[band];
        float f0 = EQ_FREQS[band];
        float Q  = 1.41f; // butterworth-ish, adjust per taste
        float A  = (float) Math.pow(10, dBgain / 40.0);
        float w0 = (float)(2 * Math.PI * f0 / SAMPLE_RATE);
        float alpha = (float)(Math.sin(w0) / (2 * Q));
        float cosw0 = (float) Math.cos(w0);

        // Peaking EQ biquad
        float b0 =  1 + alpha * A;
        float b1 = -2 * cosw0;
        float b2 =  1 - alpha * A;
        float a0 =  1 + alpha / A;
        float a1 = -2 * cosw0;
        float a2 =  1 - alpha / A;

        bqB[band][0] = b0 / a0;
        bqB[band][1] = b1 / a0;
        bqB[band][2] = b2 / a0;
        bqA[band][0] = a1 / a0;
        bqA[band][1] = a2 / a0;
    }

    private float processBiquad(int band, float x) {
        float y = bqB[band][0] * x
                + bqB[band][1] * bqX[band][0]
                + bqB[band][2] * bqX[band][1]
                - bqA[band][0] * bqY[band][0]
                - bqA[band][1] * bqY[band][1];
        bqX[band][1] = bqX[band][0];
        bqX[band][0] = x;
        bqY[band][1] = bqY[band][0];
        bqY[band][0] = y;
        return y;
    }

    // ── Echo ─────────────────────────────────────────────────────────────────

    private void initEcho() {
        int maxSamples = (int)(SAMPLE_RATE * 2.1f); // 2.1s max
        echoBuffer = new float[maxSamples];
        updateEchoDelay();
    }

    private void updateEchoDelay() {
        echoDelaySamples = (int)(echoDelayMs * SAMPLE_RATE / 1000f);
        if (echoDelaySamples < 1) echoDelaySamples = 1;
        if (echoDelaySamples >= echoBuffer.length) echoDelaySamples = echoBuffer.length - 1;
    }

    private float processEcho(float x) {
        int readPos = (echoWritePos - echoDelaySamples + echoBuffer.length) % echoBuffer.length;
        float delayed = echoBuffer[readPos];
        echoBuffer[echoWritePos] = x + delayed * echoFeedback;
        echoWritePos = (echoWritePos + 1) % echoBuffer.length;
        return x * (1f - echoMix) + delayed * echoMix;
    }

    // ── Reverb (Freeverb / Schroeder) ─────────────────────────────────────────

    private void initReverb() {
        for (int i = 0; i < 4; i++) {
            combBuffer[i] = new float[COMB_LENGTHS[i]];
            combPos[i] = 0;
        }
        for (int i = 0; i < 2; i++) {
            apBuffer[i] = new float[ALLPASS_LENGTHS[i]];
            apPos[i] = 0;
        }
    }

    private float processComb(int i, float input) {
        float out = combBuffer[i][combPos[i]];
        combFilter[i] = out * (1f - reverbDamping) + combFilter[i] * reverbDamping;
        combBuffer[i][combPos[i]] = input + combFilter[i] * reverbRoomSize;
        combPos[i] = (combPos[i] + 1) % COMB_LENGTHS[i];
        return out;
    }

    private float processAllPass(int i, float input) {
        float bufOut = apBuffer[i][apPos[i]];
        float out = -input + bufOut;
        apBuffer[i][apPos[i]] = input + bufOut * 0.5f;
        apPos[i] = (apPos[i] + 1) % ALLPASS_LENGTHS[i];
        return out;
    }

    private float processReverb(float x) {
        float wet = 0f;
        for (int i = 0; i < 4; i++) wet += processComb(i, x);
        wet /= 4f;
        wet = processAllPass(0, wet);
        wet = processAllPass(1, wet);
        return x * (1f - reverbMix) + wet * reverbMix;
    }

    // ── Noise Gate ───────────────────────────────────────────────────────────

    private void updateNoiseGateThresh() {
        noiseGateThreshLin = (float) Math.pow(10, noiseGateThreshDb / 20.0);
    }

    private float processNoiseGate(float x) {
        float abs = Math.abs(x);
        float target = abs > noiseGateThreshLin ? 1.0f : 0.0f;
        if (target > gateState) {
            gateState += GATE_ATTACK;
            if (gateState > 1f) gateState = 1f;
        } else {
            gateState -= GATE_RELEASE;
            if (gateState < 0f) gateState = 0f;
        }
        return x * gateState;
    }

    // ── Compressor ───────────────────────────────────────────────────────────

    private float processCompressor(float x) {
        if (!compressorEnabled) return x;
        float abs = Math.abs(x);
        if (abs > compEnvelope) compEnvelope += COMP_ATTACK * (abs - compEnvelope);
        else                    compEnvelope += COMP_RELEASE * (abs - compEnvelope);

        float envDb = 20f * (float) Math.log10(Math.max(compEnvelope, 1e-9f));
        float gainReductionDb = 0f;
        if (envDb > compThreshDb) {
            gainReductionDb = (compThreshDb - envDb) * (1f - 1f / compRatio);
        }
        float gainLin = (float) Math.pow(10, (gainReductionDb) / 20.0) * compGain;
        return x * gainLin;
    }

    // ── MAIN PROCESS BUFFER ──────────────────────────────────────────────────

    public void process(short[] buffer, int length) {
        for (int n = 0; n < length; n++) {
            float x = buffer[n] / 32768f;

            // 1. Noise Gate
            x = processNoiseGate(x);

            // 2. Gain
            x *= gainLinear;

            // 3. 10-band EQ
            for (int b = 0; b < EQ_BANDS; b++) {
                x = processBiquad(b, x);
            }

            // 4. Compressor
            x = processCompressor(x);

            // 5. Echo
            if (echoMix > 0.001f) x = processEcho(x);

            // 6. Reverb
            if (reverbMix > 0.001f) x = processReverb(x);

            // Clip to [-1, 1]
            if (x > 1f) x = 1f;
            if (x < -1f) x = -1f;

            buffer[n] = (short)(x * 32767f);
        }
    }

    // ── SETTERS ──────────────────────────────────────────────────────────────

    public synchronized void setGainDb(float db) {
        gainLinear = (float) Math.pow(10, db / 20.0);
    }

    public synchronized void setEqBandDb(int band, float db) {
        if (band < 0 || band >= EQ_BANDS) return;
        eqGainDb[band] = db;
        computeBiquad(band);
    }

    public synchronized void setEchoDelayMs(float ms) {
        echoDelayMs = ms;
        updateEchoDelay();
    }

    public synchronized void setEchoFeedback(float fb) { echoFeedback = Math.max(0f, Math.min(0.95f, fb)); }
    public synchronized void setEchoMix(float mix) { echoMix = Math.max(0f, Math.min(1f, mix)); }

    public synchronized void setReverbRoomSize(float r) { reverbRoomSize = Math.max(0f, Math.min(1f, r)); }
    public synchronized void setReverbDamping(float d)   { reverbDamping = Math.max(0f, Math.min(1f, d)); }
    public synchronized void setReverbMix(float mix)     { reverbMix = Math.max(0f, Math.min(1f, mix)); }

    public synchronized void setNoiseGateThreshDb(float db) {
        noiseGateThreshDb = db;
        updateNoiseGateThresh();
    }

    public synchronized void setCompressorEnabled(boolean en) { compressorEnabled = en; }
    public synchronized void setCompThreshDb(float db)        { compThreshDb = db; }
    public synchronized void setCompRatio(float ratio)        { compRatio = Math.max(1f, ratio); }
    public synchronized void setCompMakeupGain(float g)       { compGain = g; }

    // ── GETTERS for UI ───────────────────────────────────────────────────────
    public float getGainDb()          { return 20f * (float) Math.log10(gainLinear); }
    public float getEqBandDb(int b)   { return eqGainDb[b]; }
    public float getEchoDelayMs()     { return echoDelayMs; }
    public float getEchoFeedback()    { return echoFeedback; }
    public float getEchoMix()         { return echoMix; }
    public float getReverbRoomSize()  { return reverbRoomSize; }
    public float getReverbDamping()   { return reverbDamping; }
    public float getReverbMix()       { return reverbMix; }
    public float getNoiseGateThreshDb(){ return noiseGateThreshDb; }
    public boolean isCompressorEnabled(){ return compressorEnabled; }
    public float getCompThreshDb()    { return compThreshDb; }
    public float getCompRatio()       { return compRatio; }

    public static int getSampleRate() { return SAMPLE_RATE; }
    public static int getBandCount()  { return EQ_BANDS; }
    public static float[] getBandFreqs() { return EQ_FREQS; }
}

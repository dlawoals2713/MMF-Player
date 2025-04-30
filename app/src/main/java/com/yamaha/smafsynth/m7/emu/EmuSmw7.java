package com.yamaha.smafsynth.m7.emu;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class EmuSmw7 implements AudioTrack.OnPlaybackPositionUpdateListener {

    // 오디오 관련 변수들
    private int sampleRate = -1;
    private int minBufferSize = 0;
    private final int numberOfBuffers = 2;
    private int currentBufferIndexForPlayback = 0;
    public AudioTrack audioTrack = null;
    private int releaseDelayInMillis = 1000;
    private boolean[] isBufferReady;
    private byte[][] audioBuffers;
    private volatile boolean isGeneratingData = false;
    private final LinkedBlockingQueue<ByteBuffer> bufferQueue = new LinkedBlockingQueue<>(numberOfBuffers);
    // 기존의 DataGenerationThread 대신 ExecutorService 사용
    private volatile ExecutorService dataGenExecutor;
    private final ExecutorService uvExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService CheckPlaybackExecutor = Executors.newSingleThreadExecutor();
    private final Handler playstateHandler = new Handler(Looper.getMainLooper());
    public boolean isRepeating = false;

    // 네이티브 메서드들
    private native int getGenerateData(byte[] buffer);
    private native long getLength();
    private native long getPosition();
    private native long getState();
    private native long init(long sampleRate, int notificationPeriodInFrames);
    private native int setVolume(long volume);
    private native long start(byte[] buffer, long j, long j2, long j3, long j4, long j5);
    private native long stop();
    private native long term();
    private volatile long AUDIO_LEN_CACHE = 0;
    private final long minToleranceFrames = sampleRate / 400L; // 50ms (1/20초) → 1102프레임 (22050 기준)
    private final long maxToleranceFrames = sampleRate / 500L; // 100ms (1/10초) → 2205프레임 (22050 기준)
    public void EmuSmw7Init() {
        System.loadLibrary("M7_EmuSmw7");
        CheckPlaybackExecutor.submit(checkPlaybackRunnable);
        runningPlaystateHandler();
    }
    private void resetAudioLength(){ // 추가
        AUDIO_LEN_CACHE = 0;
    }
    private void runningPlaystateHandler(){
        playstateHandler.post(playstateRunner);
    }
    Runnable playstateRunner = new Runnable() {
        @Override
        public void run() {
            switch ((int) getState()){
                case 0:
                    break;
                case 1:
                    resetAudioLength();
                    break;
                case 2:
                    AUDIO_LEN_CACHE = getLength();
                    break;
            }
            playstateHandler.postDelayed(this, 1000);
        }

    };
    Runnable checkPlaybackRunnable = new Runnable() {

        @Override
        public void run() {
            synchronized (bufferQueue){
                long currentPosition = getPosition();
                long audioLength = AUDIO_LEN_CACHE;
                long diff = audioLength - currentPosition;
                long toleranceFrames;
                if (audioLength > maxToleranceFrames * 20) { // 최대치의 20배를 초과할경우
                    toleranceFrames = maxToleranceFrames;//최대값을 사용한다.
                    Log.d("AudioCheck", "maxToleranceFrames "+toleranceFrames);
                } else { // 그외에는 min값을 통해서 사용한다.
                    toleranceFrames = Math.min(audioLength / 10, minToleranceFrames);
                    Log.d("AudioCheck", "minToleranceFrames "+toleranceFrames);
                }
                //Log.d("AudioBufferMonitor", "checkPlaybackExecutor: audioLength: "+audioLength+", currentPosition: "+currentPosition+", diff: "+diff+", toleranceFrames: "+toleranceFrames);
                if (diff <= toleranceFrames && audioLength != 0L && !isRepeating) {
                    stop();
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            CheckPlaybackExecutor.submit(this);
        }
    };
    public interface ChannelDataListener {
        void onChannelDataReady(int leftChannelValue, int rightChannelValue);
    }

    private ChannelDataListener channelDataListener;

    public void setChannelDataListener(ChannelDataListener listener) {
        this.channelDataListener = listener;
    }

    public long startAudio(long sampleRate) {
        this.sampleRate = (int) sampleRate;
        Log.d("AudioTrack", "Sample rate: " + this.sampleRate);
        // 최소 버퍼 크기 계산
        this.minBufferSize = AudioTrack.getMinBufferSize(
                this.sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (this.minBufferSize <= 0) {
            Log.e("AudioTrack", "Invalid buffer size: " + this.minBufferSize);
            return -1;
        }
        // AudioAttributes 및 AudioFormat 구성
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioFormat audioFormatConfig = new AudioFormat.Builder()
                .setSampleRate(this.sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build();

        // AudioTrack 생성
        int notificationPeriodInFrames = 0;
        byte[] silenceBuffer = null;
        try {
            this.audioTrack = new AudioTrack(
                    audioAttributes,
                    audioFormatConfig,
                    this.minBufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
            );
            if (this.audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                Log.e("AudioTrack", "Failed to initialize AudioTrack");
                return -1;
            }
            // 알림 주기 및 지연 시간 설정
            notificationPeriodInFrames = this.minBufferSize / 4;
            int notificationPeriodInMillis = ((notificationPeriodInFrames * 1000) / this.sampleRate) + 1;
            this.releaseDelayInMillis = notificationPeriodInMillis * 2;

            // 침묵 데이터 버퍼 초기화
            silenceBuffer = new byte[this.minBufferSize];
            Arrays.fill(silenceBuffer, (byte) 0);
            this.audioTrack.setPositionNotificationPeriod(notificationPeriodInFrames);
            this.audioTrack.setPlaybackPositionUpdateListener(this);
        } catch (Exception e) {
            Log.e("AudioTrack", "Error initializing AudioTrack: " + e.getMessage());
            return -1;
        }

        // 버퍼 초기화
        initializeBuffers(this.minBufferSize);

        // 네이티브 엔진 초기화
        long initResult = init(this.sampleRate, notificationPeriodInFrames);
        if (initResult < 0) {
            Log.e("NativeInit", "Native audio engine initialization failed");
            releaseAudioResources();
            return initResult;
        }

        try {
            this.audioTrack.play();
        } catch (IllegalStateException e) {
            Log.e("AudioTrack", "Error starting playback: " + e.getMessage());
            releaseAudioResources();
            return -1;
        }

        // 침묵 데이터를 쓰고 안정적인 재생 환경 조성
        for (int i = 0; i < 3; i++) {
            int result = this.audioTrack.write(silenceBuffer, 0, this.minBufferSize);
            if (result < 0) {
                Log.e("AudioTrack", "Error writing silence data: " + result);
                return -1;
            }
        }

        // 데이터 생성 작업 시작 - UI 쓰레드 부하를 피하기 위해 Executor에 위임
        isGeneratingData = true;
        dataGenExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setPriority(Thread.MAX_PRIORITY);
            t.setName("DataGenerationThread");
            return t;
        });
        dataGenExecutor.submit(dataGenerationTask);
        return initResult;
    }
    /**
     * 데이터 생성 작업을 백그라운드에서 실행하기 위한 Runnable
     */
    private final Runnable dataGenerationTask = new Runnable() {
        @Override
        public void run() {
            //setThreadPriority(Thread.NORM_PRIORITY);
            while (isGeneratingData && !Thread.interrupted()) {
                try {
                    ByteBuffer buffer = bufferQueue.poll(50, TimeUnit.MILLISECONDS);
                    if(buffer == null) continue;
                    buffer.clear();
                    int result = getGenerateData(buffer.array());
                    if(result > 0){
                        synchronized(audioBuffers){
                            audioTrack.write(buffer.array(), 0, buffer.capacity());
                        }
                        uvExecutor.submit(()->{
                            separateChannelsAndSendData(buffer.array());
                        });
                        bufferQueue.put(buffer);
                    }
                    Thread.yield();
                }
                catch (InterruptedException e) {
                    break;
                }
            }
        }
    };

    @Override
    public void onPeriodicNotification(AudioTrack audioTrack) {
        int nextBufferIndex = (currentBufferIndexForPlayback + 1) % numberOfBuffers;
        if (isBufferReady[nextBufferIndex]) {
            isBufferReady[currentBufferIndexForPlayback] = false;
            currentBufferIndexForPlayback = nextBufferIndex;
            audioTrack.write(audioBuffers[currentBufferIndexForPlayback], 0, minBufferSize);
            // 채널 분리 및 데이터 전달
            separateChannelsAndSendData(audioBuffers[currentBufferIndexForPlayback]);
        }
    }
    /**
     * 채널을 분리하고 데이터를 MainActivity로 전달하는 메서드
     */
    private void separateChannelsAndSendData(byte[] buffer) {
        // 샘플 크기 (16비트 = 2바이트)
        final int sampleSize = 2;
        final int SAMPLE_SKIP = 8; // 4샘플마다 처리
        // 샘플 개수
        final int numberOfSamples = buffer.length / sampleSize;

        int leftChannelMaxValue = 0;
        int rightChannelMaxValue = 0;

        // 채널 데이터 분리 및 최댓값 계산
        for (int i = 0; i < numberOfSamples; i += SAMPLE_SKIP * 2) {
            short leftSample = ByteBuffer.wrap(buffer, i * sampleSize, sampleSize).order(ByteOrder.LITTLE_ENDIAN).getShort();
            short rightSample = ByteBuffer.wrap(buffer, (i + 1) * sampleSize, sampleSize).order(ByteOrder.LITTLE_ENDIAN).getShort() ;

            leftChannelMaxValue = Math.max(leftChannelMaxValue, Math.abs(leftSample));
            rightChannelMaxValue = Math.max(rightChannelMaxValue, Math.abs(rightSample));
        }

        // 최댓값을 기준으로 정규화
        final int maxPossibleValue = Short.MAX_VALUE; // 32767

        final float normalizedLeftValue = Math.max(0f, Math.min(1f, (float) leftChannelMaxValue / maxPossibleValue));
        final float normalizedRightValue = Math.max(0f, Math.min(1f, (float) rightChannelMaxValue / maxPossibleValue));

        // MainActivity로 데이터 전달
        if (channelDataListener != null) {
            channelDataListener.onChannelDataReady((int) (normalizedLeftValue * 100), (int) (normalizedRightValue * 100));
        }
    }
    @Override
    public void onMarkerReached(AudioTrack audioTrack) {
        // 필요 시 처리
    }

    /**
     * 버퍼들을 초기화하는 메서드
     */
    public void initializeBuffers(int bufferSize) {
        bufferQueue.clear();
        currentBufferIndexForPlayback = 0;
        isBufferReady = new boolean[numberOfBuffers];
        audioBuffers = (byte[][]) Array.newInstance(byte.class, numberOfBuffers, bufferSize);
        for (int i = 0; i < numberOfBuffers; i++) {
            bufferQueue.offer(ByteBuffer.allocateDirect(bufferSize));
            isBufferReady[i] = false;
            Arrays.fill(audioBuffers[i], (byte) 0);
        }
    }

    /**
     * 오디오 자원 해제 및 백그라운드 작업 종료
     */
    public void releaseAudioResources() {
        Log.i("AudioRelease", "Releasing audio resources...");
        // 데이터 생성 중지
        isGeneratingData = false;
        if (dataGenExecutor != null) {
            dataGenExecutor.shutdownNow();
            dataGenExecutor = null;
        }
        // AudioTrack 정지 및 해제
        if (audioTrack != null && audioTrack.getState() != AudioTrack.STATE_UNINITIALIZED) {
            try {
                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop();
                }
            } catch (IllegalStateException e) {
                Log.e("AudioRelease", "Error stopping AudioTrack!", e);
            }
            audioTrack.setPlaybackPositionUpdateListener(null);
            audioTrack.release();
            audioTrack = null;
        } else {
            Log.w("AudioRelease", "AudioTrack was not initialized or already released.");
        }
        // 네이티브 리소스 해제
        long termResult = term();
        if (termResult < 0) {
            Log.e("AudioRelease", "Failed to release native resources!");
        } else {
            Log.i("AudioRelease", "Native resources released.");
        }
        try {
            Thread.sleep(releaseDelayInMillis);
            Log.d("AudioRelease", "Release delay completed.");
        } catch (InterruptedException e) {
            Log.e("AudioRelease", "Release delay interrupted!", e);
            Thread.currentThread().interrupt();
        }
        Log.i("AudioRelease", "Audio resources released.");
    }

    public void setPlaybackVolume(int vol) {
        setVolume(vol);
    }

    public void stopPlayback() {
        stop();
    }

    public void startPlayback(byte[] mmfData, long l, long l1, long formatType, long l2, long l3) {
        start(mmfData, l, l1, formatType, l2, l3);
    }

    public long getPlaybackStatus() {
        return getState();
    }

    public long getAudioLength() {
        return getLength();
    }

    public long getCurrentPlaybackPosition() {
        return getPosition();
    }
}

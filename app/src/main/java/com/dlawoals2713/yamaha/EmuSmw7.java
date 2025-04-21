package com.yamaha.smafsynth.m7.emu;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import java.lang.reflect.Array;

public class EmuSmw7 implements AudioTrack.OnPlaybackPositionUpdateListener {

    /* renamed from: a  reason: collision with root package name */
    private a f5611a = null;

    /* renamed from: b  reason: collision with root package name */
    private int f5612b = 22050;

    /* renamed from: c  reason: collision with root package name */
    private int f5613c = 0;

    /* renamed from: d  reason: collision with root package name */
    private byte[] f5614d = null;

    /* renamed from: e  reason: collision with root package name */
    private int f5615e = 0;

    /* renamed from: f  reason: collision with root package name */
    private int f5616f = 0;

    /* renamed from: g  reason: collision with root package name */
    private final int f5617g = 5;
    /* access modifiers changed from: private */

    /* renamed from: h  reason: collision with root package name */
    public boolean f5618h = false;
    /* access modifiers changed from: private */

    /* renamed from: i  reason: collision with root package name */
    public int f5619i = 0;

    /* renamed from: j  reason: collision with root package name */
    private int f5620j = 0;
    /* access modifiers changed from: private */

    /* renamed from: k  reason: collision with root package name */
    public boolean[] f5621k;
    /* access modifiers changed from: private */

    /* renamed from: l  reason: collision with root package name */
    public byte[][] f5622l;

    /* renamed from: m  reason: collision with root package name */
    private AudioTrack f5623m = null;

    /* renamed from: n  reason: collision with root package name */
    private byte[] f5624n = null;

    /* renamed from: o  reason: collision with root package name */
    private int f5625o = 1000;

    public class a extends Thread {
        public a() {
        }

        public void run() {
            while (EmuSmw7.this.f5618h) {
                if (!EmuSmw7.this.f5621k[EmuSmw7.this.f5619i]) {
                    EmuSmw7 emuSmw7 = EmuSmw7.this;
                    if (emuSmw7.getGenerateData(emuSmw7.f5622l[EmuSmw7.this.f5619i]) > 0) {
                        EmuSmw7.this.f5621k[EmuSmw7.this.f5619i] = true;
                        EmuSmw7.m(EmuSmw7.this);
                        if (EmuSmw7.this.f5619i >= 5) {
                            int unused = EmuSmw7.this.f5619i = 0;
                        }
                    }
                }
                Thread.yield();
            }
        }
    }

    public EmuSmw7() {
        System.loadLibrary("M7_EmuSmw7");
    }

    /* access modifiers changed from: private */
    public native int getGenerateData(byte[] bArr);

    private native long getLength();

    private native long getPosition();

    private native long getState();

    private native long init(long j5, int i5);

    static /* synthetic */ int m(EmuSmw7 emuSmw7) {
        int i5 = emuSmw7.f5619i;
        emuSmw7.f5619i = i5 + 1;
        return i5;
    }

    private native int setVolume(long j5);

    private native long start(byte[] bArr, long j5, long j6, long j7, long j8, long j9);

    private native long stop();

    private native long term();

    public long a() {
        return getLength();
    }

    public long b() {
        return getPosition();
    }

    public long c() {
        return getState();
    }

    public long d(long j5, int i5, int i6) {
        int i7 = (int) j5;
        this.f5612b = i7;
        if (this.f5623m == null) {
            int minBufferSize = AudioTrack.getMinBufferSize(i7, 12, 2);
            this.f5613c = minBufferSize;
            this.f5615e = minBufferSize;
            int i8 = minBufferSize / 2;
            this.f5615e = i8;
            int i9 = i8 / 2;
            this.f5615e = i9;
            int i10 = ((i9 * 1000) / this.f5612b) + 1;
            this.f5616f = i10;
            this.f5625o = i10 * 2;
            this.f5624n = new byte[minBufferSize];
            for (int i11 = 0; i11 < this.f5613c; i11++) {
                this.f5624n[i11] = 0;
            }
            AudioTrack audioTrack = new AudioTrack(new AudioAttributes.Builder().setUsage(i6).setContentType(2).build(), new AudioFormat.Builder().setSampleRate(this.f5612b).setEncoding(2).setChannelMask(12).build(), this.f5613c, 1, 0);
            this.f5623m = audioTrack;
            audioTrack.setPositionNotificationPeriod(this.f5615e);
            this.f5623m.setPlaybackPositionUpdateListener(this);
        }
        p(this.f5613c);
        long init = init((long) this.f5612b, this.f5615e);
        this.f5623m.play();
        if (init >= 0) {
            a aVar = new a();
            this.f5611a = aVar;
            aVar.start();
            this.f5623m.write(this.f5624n, 0, this.f5613c);
            this.f5623m.write(this.f5624n, 0, this.f5613c);
            this.f5623m.write(this.f5624n, 0, this.f5613c);
        } else {
            this.f5623m.stop();
            try {
                Thread.sleep((long) this.f5625o);
            } catch (InterruptedException e5) {
                e5.printStackTrace();
            }
            this.f5623m.flush();
            this.f5623m.release();
            this.f5623m = null;
        }
        return init;
    }

    public int e(long j5) {
        return setVolume(j5);
    }

    public long f(byte[] bArr, long j5, long j6, long j7, long j8, long j9) {
        return start(bArr, j5, j6, j7, j8, j9);
    }

    public long g() {
        return stop();
    }

    public long h() {
        long term = term();
        if (term >= 0) {
            this.f5618h = false;
            a aVar = this.f5611a;
            if (aVar != null) {
                try {
                    aVar.join();
                    this.f5611a = null;
                } catch (InterruptedException e5) {
                    e5.printStackTrace();
                }
            }
            AudioTrack audioTrack = this.f5623m;
            if (audioTrack != null) {
                this.f5623m = null;
                audioTrack.setPlaybackPositionUpdateListener((AudioTrack.OnPlaybackPositionUpdateListener) null);
                audioTrack.stop();
                try {
                    Thread.sleep((long) this.f5625o);
                } catch (InterruptedException e6) {
                    e6.printStackTrace();
                }
                audioTrack.flush();
            }
        }
        return term;
    }

    public void onMarkerReached(AudioTrack audioTrack) {
    }

    public void onPeriodicNotification(AudioTrack audioTrack) {
        if (this.f5623m != null) {
            int i5 = this.f5620j;
            int i6 = i5 + 1;
            if (i6 >= 5) {
                i6 = 0;
            }
            boolean[] zArr = this.f5621k;
            if (zArr[i6]) {
                zArr[i5] = false;
                this.f5620j = i6;
            }
            audioTrack.write(this.f5622l[this.f5620j], 0, this.f5613c);
        }
    }

    public void p(int i5) {
        this.f5619i = 1;
        this.f5620j = 1;
        this.f5621k = new boolean[5];
        int[] iArr = new int[2];
        iArr[1] = this.f5613c;
        iArr[0] = 5;
        this.f5622l = (byte[][]) Array.newInstance(Byte.TYPE, iArr);
        for (int i6 = 0; i6 < 5; i6++) {
            this.f5621k[i6] = false;
            for (int i7 = 0; i7 < this.f5613c; i7++) {
                this.f5622l[i6][i7] = 0;
            }
        }
        this.f5618h = true;
    }
}
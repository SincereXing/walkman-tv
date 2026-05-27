package cn.toside.music.mobile.playback;

import android.media.audiofx.Visualizer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.modules.core.DeviceEventManagerModule;

/**
 * 使用系统 {@link Visualizer} 读取播放输出 FFT，供 TV 播放页波形随音乐跳动。
 * audioSession=0 表示输出混音（需设备支持；失败时 JS 侧回落动画）。
 */
public class PlaybackSpectrumModule extends ReactContextBaseJavaModule implements LifecycleEventListener {
  private static final String TAG = "PlaybackSpectrum";
  public static final String EVENT_SPECTRUM = "playbackSpectrum";

  private final ReactApplicationContext reactContext;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  @Nullable
  private Visualizer visualizer;
  private int listenerCount = 0;

  /** 与 TV 波形条数对齐（由 JS 再抽样） */
  private static final int FFT_BANDS_OUT = 64;

  public PlaybackSpectrumModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
    reactContext.addLifecycleEventListener(this);
  }

  @Override
  public String getName() {
    return "PlaybackSpectrumModule";
  }

  @ReactMethod
  public void addListener(String eventName) {
    listenerCount++;
  }

  @ReactMethod
  public void removeListeners(double count) {
    listenerCount -= (int) count;
    if (listenerCount <= 0) {
      listenerCount = 0;
    }
  }

  @ReactMethod
  public void start(Promise promise) {
    mainHandler.post(() -> {
      try {
        releaseInternal();
        Visualizer v = new Visualizer(0);
        int[] range = Visualizer.getCaptureSizeRange();
        safeSetCaptureSize(v, range);
        v.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
          @Override
          public void onWaveFormDataCapture(Visualizer visualizer, byte[] waveform, int samplingRate) {
          }

          @Override
          public void onFftDataCapture(Visualizer visualizer, byte[] fft, int samplingRate) {
            if (fft == null || fft.length < 4) return;
            WritableArray bands = fftToBands(fft, FFT_BANDS_OUT);
            emitSpectrum(bands);
          }
        }, Visualizer.getMaxCaptureRate() / 2, false, true);

        v.setEnabled(true);
        visualizer = v;
        promise.resolve(true);
      } catch (Throwable t) {
        Log.w(TAG, "Visualizer start failed", t);
        releaseInternal();
        promise.reject("E_VISUALIZER", t.getMessage(), t);
      }
    });
  }

  @ReactMethod
  public void stop(Promise promise) {
    mainHandler.post(() -> {
      releaseInternal();
      promise.resolve(null);
    });
  }

  private void emitSpectrum(WritableArray bands) {
    if (!reactContext.hasActiveReactInstance()) return;
    reactContext
        .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(EVENT_SPECTRUM, bands);
  }

  /**
   * Android FFT：fft.length == captureSize，每对 byte 为 (real, imag)，共 captureSize/2 个复数频点。
   */
  private static WritableArray fftToBands(byte[] fft, int bandCount) {
    WritableArray out = Arguments.createArray();
    int pairs = fft.length / 2;
    if (pairs < 2) {
      for (int i = 0; i < bandCount; i++) out.pushDouble(0);
      return out;
    }
    // 跳过 DC；高频端略少取避免噪声
    int start = 1;
    int usable = pairs - 2;
    if (usable < 1) usable = 1;
    double binsPerBand = (double) usable / bandCount;

    for (int b = 0; b < bandCount; b++) {
      double sum = 0;
      int from = start + (int) (b * binsPerBand);
      int to = start + (int) ((b + 1) * binsPerBand);
      if (to <= from) to = from + 1;
      int count = 0;
      for (int k = from; k < to && k < pairs; k++) {
        int idx = k * 2;
        if (idx + 1 >= fft.length) break;
        double r = fft[idx];
        double i = fft[idx + 1];
        sum += Math.sqrt(r * r + i * i);
        count++;
      }
      double avg = count > 0 ? sum / count : 0;
      // 经验缩放，约映射到 0~1
      double norm = Math.min(1.0, Math.pow(avg / 48.0, 0.85));
      out.pushDouble(norm);
    }
    return out;
  }

  private static void safeSetCaptureSize(Visualizer v, int[] range) {
    int max = range[1];
    int min = range[0];
    int start = Math.min(2048, max);
    if (start < min) start = max;
    for (int trySize = start; trySize >= min; trySize = trySize > min ? trySize / 2 : min - 1) {
      try {
        v.setCaptureSize(trySize);
        return;
      } catch (IllegalArgumentException e) {
        if (trySize <= min) break;
      }
    }
    v.setCaptureSize(min);
  }

  private void releaseInternal() {
    if (visualizer != null) {
      try {
        visualizer.setEnabled(false);
        visualizer.release();
      } catch (Throwable ignored) {
      }
      visualizer = null;
    }
  }

  @Override
  public void onHostResume() {
  }

  @Override
  public void onHostPause() {
  }

  @Override
  public void onHostDestroy() {
    mainHandler.post(this::releaseInternal);
  }
}

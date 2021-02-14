package com.minipif.boxingtimer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import androidx.annotation.Nullable;
import java.util.Locale;

public class GraphView extends View {

  private final static float IDLE_THRESHOLD = .1f;
  private final static float PRESS_THRESHOLD = .5f;

  private final static int IDLE_FRAME_COUNT = 20;

  private final SensorManager sensorManager;
  private final Sensor gyroSensor;

  private final Paint mainPaint;
  private final Paint textPaint;
  private final Paint graphPaint;

  private final float density;

  private Bitmap bufferBitmap;
  private Canvas bufferCanvas;
  private Bitmap prevBufferBitmap;
  private Canvas prevBufferCanvas;

  private boolean drawing = false;
  private int frameCount = 0;
  private float lastLrValue;
  private float lastUdValue;
  private float prevLrY;
  private float prevUdY;
  private int idleFrameCount;
  private String lastDirDebugStr = "";
  private int lastDirDebugFrameCount;

  private DirPressListener listener;

  interface DirPressListener {

    void onDirPressed(Dir dir);
  }

  private final SensorEventListener gyroListener = new SensorEventListener() {
    @Override
    public void onSensorChanged(SensorEvent event) {
      lastLrValue = event.values[0];
      lastUdValue = event.values[1];

      Dir lastDir = null;
      float lastDirValue = -1;
      if (idleFrameCount > IDLE_FRAME_COUNT) {
        if (Math.abs(lastLrValue) > Math.abs(lastUdValue)) {
          if (Math.abs(lastLrValue) > PRESS_THRESHOLD) {
            lastDir = lastLrValue < 0 ? Dir.LEFT : Dir.RIGHT;
            lastDirValue = Math.abs(lastLrValue);
          }
        } else {
          if (Math.abs(lastUdValue) > PRESS_THRESHOLD) {
            lastDir = lastUdValue < 0 ? Dir.DOWN : Dir.UP;
            lastDirValue = Math.abs(lastUdValue);
          }
        }
      }
      if (lastDir != null) {
        lastDirDebugStr = String.format(Locale.ROOT, "%5s %.2f", lastDir, lastDirValue);
        lastDirDebugFrameCount = 0;

        if (listener != null) {
          listener.onDirPressed(lastDir);
        }
      } else {
        lastDirDebugFrameCount++;
      }

      if (Math.abs(lastLrValue) < IDLE_THRESHOLD && Math.abs(lastUdValue) < IDLE_THRESHOLD) {
        idleFrameCount++;
      } else {
        idleFrameCount = 0;
      }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
  };

  {
    density = getResources().getDisplayMetrics().density;

    mainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    textPaint.setColor(Color.WHITE);
    textPaint.setTextSize(32 * density);
    textPaint.setTypeface(Typeface.MONOSPACE);

    graphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    graphPaint.setColor(Color.WHITE);

    sensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
    gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
  }


  public GraphView(Context context) {
    super(context);
  }

  public GraphView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public GraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public GraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr,
      int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  void setListener(DirPressListener listener) {
    this.listener = listener;
  }

  void clearListener() {
    this.listener = null;
  }

  void startGraph() {
    sensorManager.registerListener(gyroListener, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
    drawing = true;
    invalidate();
  }

  void stopGraph() {
    sensorManager.unregisterListener(gyroListener);
    drawing = false;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    if (bufferBitmap == null) {
      bufferBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
      bufferCanvas = new Canvas(bufferBitmap);

      prevBufferBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
      prevBufferCanvas = new Canvas(prevBufferBitmap);

      prevBufferCanvas.drawColor(Color.BLACK);
    }

    bufferCanvas.drawColor(Color.BLACK);
    bufferCanvas.drawBitmap(prevBufferBitmap, -1, 0, mainPaint);

    drawPersistent();
    prevBufferCanvas.drawBitmap(bufferBitmap, 0, 0, mainPaint);
    drawComposite();
    canvas.drawBitmap(bufferBitmap, 0, 0, mainPaint);

    frameCount++;
    if (drawing) {
      invalidate();
    }
  }

  private void drawPersistent() {
    float coef = .06f;
    float lrY = (.25f + lastLrValue * coef) * getHeight();
    float udY = (.75f + lastUdValue * coef) * getHeight();

    // bufferCanvas.drawPoint(getWidth() - 1,
    //     // new Random().nextInt(getHeight())
    //     // frameCount % getHeight()
    //     y
    //     , graphPaint);

    graphPaint.setColor(0xff666666);
    bufferCanvas.drawPoint(getWidth() - 1, getHeight() * .25f, graphPaint);
    bufferCanvas.drawPoint(getWidth() - 1, getHeight() * .75f, graphPaint);

    graphPaint.setColor(0xffff0000);
    if (frameCount == 0) {
      bufferCanvas.drawPoint(getWidth() - 1, lrY, graphPaint);
    } else {
      bufferCanvas.drawLine(getWidth() - 2, prevLrY, getWidth() - 1, lrY, graphPaint);
    }

    graphPaint.setColor(0xff00ff00);
    if (frameCount == 0) {
      bufferCanvas.drawPoint(getWidth() - 1, udY, graphPaint);
    } else {
      bufferCanvas.drawLine(getWidth() - 2, prevUdY, getWidth() - 1, udY, graphPaint);
    }

    prevLrY = lrY;
    prevUdY = udY;
  }

  private void drawComposite() {
    String format = "%+.3f";
    bufferCanvas.drawText(String.format(Locale.ROOT, format, lastLrValue), 0, getHeight() * .25f,
        textPaint);
    bufferCanvas.drawText(String.format(Locale.ROOT, format, lastUdValue), 0, getHeight() * .75f,
        textPaint);

    if (idleFrameCount <= IDLE_FRAME_COUNT) {
      textPaint.setColor(0xff0000ff);
    }
    bufferCanvas.drawText(String.valueOf(idleFrameCount), 0, getHeight() * .5f, textPaint);
    textPaint.setColor(Color.WHITE);

    if (lastDirDebugFrameCount < IDLE_FRAME_COUNT) {
      textPaint.setColor(0xffff0000);
    }
    bufferCanvas.drawText(lastDirDebugStr, 0, getHeight() - 1, textPaint);
    textPaint.setColor(Color.WHITE);
  }
}

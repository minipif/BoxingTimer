package com.minipif.boxingtimer;

import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

  private final static int INTERVAL = 30;
  private final static int START_EXTRA_SECONDS = 3;

  private GraphView graphView;
  private TextView timerTextView;
  private int bellSoundId = -1;

  private int duration = INTERVAL * 3;
  private int timer;
  private boolean running = false;
  private TimerThread timerThread;

  private final SoundPool soundPool;

  {
    soundPool = new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
        new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()).build();
  }

  private final GraphView.DirPressListener dirPressListener = dir -> {
    if (running) {
      stopTimer(false);
      return;
    }

    if (dir.equals(Dir.UP)) {
      duration += INTERVAL;
      invalidate();
    } else if (dir.equals(Dir.DOWN)) {
      if (duration > INTERVAL) {
        duration -= INTERVAL;
        invalidate();
      }
    } else if (dir.equals(Dir.LEFT)) {
      ring();
    } else if (dir.equals(Dir.RIGHT)) {
      startTimer();
    }
  };

  private class TimerThread extends Thread {

    boolean running = true;

    @Override
    public void run() {
      long now = SystemClock.elapsedRealtime();
      for (int time = duration + START_EXTRA_SECONDS; time >= 0; time--) {
        int finalTime = time;
        runOnUiThread(() -> {
          timer = finalTime;
          invalidate();
        });

        long sleepEnd = now += 1000;
        sleepUntil(sleepEnd);
        if (!running) {
          return;
        }
      }

      runOnUiThread(() -> stopTimer(true));
    }

    private void sleepUntil(long sleepEndTimestamp) {
      long sleep = sleepEndTimestamp - SystemClock.elapsedRealtime();
      SystemClock.sleep(sleep);
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    bellSoundId = soundPool.load(this, R.raw.boxing_bell, 1);

    graphView = findViewById(R.id.graphView);
    timerTextView = findViewById(R.id.timer);

    findViewById(R.id.buttonLeft).setOnClickListener(v -> dirPressListener.onDirPressed(Dir.LEFT));
    findViewById(R.id.buttonUp).setOnClickListener(v -> dirPressListener.onDirPressed(Dir.UP));
    findViewById(R.id.buttonDown).setOnClickListener(v -> dirPressListener.onDirPressed(Dir.DOWN));
    findViewById(R.id.buttonRight)
        .setOnClickListener(v -> dirPressListener.onDirPressed(Dir.RIGHT));

    invalidate();
  }

  @Override
  protected void onResume() {
    super.onResume();
    graphView.startGraph();
    graphView.setListener(dirPressListener);
  }

  @Override
  protected void onPause() {
    super.onPause();
    graphView.stopGraph();
    graphView.clearListener();

    if (running) {
      stopTimer(false);
    }
  }

  private void startTimer() {
    if (running || timerThread != null) {
      throw new RuntimeException("Already running");
    }
    running = true;
    timerThread = new TimerThread();
    timerThread.start();
  }

  private void stopTimer(boolean ring) {
    if (!running || timerThread == null) {
      throw new RuntimeException("Not running");
    }

    running = false;
    timerThread.running = false;
    timerThread = null;

    if (ring) {
      ring();
    }
    invalidate();
  }

  private void invalidate() {
    final int d;
    final int textColor;
    if (running) {
      d = timer;
      textColor = Color.GREEN;
    } else {
      d = duration;
      textColor = 0xff997799;
    }
    int min = d / 60;
    int sec = d % 60;
    String text = String.format(Locale.ROOT, "%d:%02d", min, sec);
    timerTextView.setText(text);
    timerTextView.setTextColor(textColor);
  }

  private void ring() {
    soundPool.play(bellSoundId, 1, 1, 0, 0, 1);
  }

}
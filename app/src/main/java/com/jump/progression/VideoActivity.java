package com.jump.progression;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

public final class VideoActivity extends Activity {
    static final String EXTRA_EXERCISE_INDEX = "exercise_index";
    static final String EXTRA_EXERCISE_NAME = "exercise_name";
    private static final int[] VIDEOS = {
            R.raw.exercise_01, R.raw.exercise_02, R.raw.exercise_03, R.raw.exercise_04,
            R.raw.exercise_05, R.raw.exercise_06, R.raw.exercise_07, R.raw.exercise_08,
            R.raw.exercise_09, R.raw.exercise_10
    };

    private VideoView videoView;
    private LinearLayout controls;
    private SeekBar timeline;
    private TextView timeLabel;
    private Button playButton;
    private boolean dragging;
    private int duration;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable updateTimeline = new Runnable() {
        @Override public void run() {
            refreshTimeline();
            handler.postDelayed(this, 250);
        }
    };
    private final Runnable hideControls = () -> {
        if (!dragging && controls != null) controls.setVisibility(View.GONE);
    };

    static int videoForIndex(int index) {
        return index >= 0 && index < VIDEOS.length ? VIDEOS[index] : 0;
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int index = getIntent().getIntExtra(EXTRA_EXERCISE_INDEX, -1);
        int video = videoForIndex(index);
        if (video == 0) {
            finish();
            return;
        }

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.BLACK);

        videoView = new VideoView(this);
        page.addView(videoView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        View tapSurface = new View(this);
        tapSurface.setContentDescription("Показать или скрыть управление видео");
        tapSurface.setOnClickListener(view -> {
            if (controls.getVisibility() == View.VISIBLE) hideControlsNow();
            else showControls();
        });
        page.addView(tapSurface, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        Button back = new Button(this);
        back.setText("← К тренировке");
        back.setTextColor(Color.WHITE);
        back.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x99000000));
        back.setOnClickListener(view -> finish());
        FrameLayout.LayoutParams backLayout = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        page.addView(back, backLayout);

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), dp(8), dp(12), dp(8));
        controls.setBackgroundColor(0xB3000000);
        controls.setVisibility(View.GONE);
        FrameLayout.LayoutParams controlsLayout = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        page.addView(controls, controlsLayout);

        timeline = new SeekBar(this);
        timeline.setContentDescription("Перемотка видео");
        timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) {
                dragging = true;
                handler.removeCallbacks(hideControls);
            }
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) timeLabel.setText(formatTime(progress) + " / " + formatTime(duration));
            }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                videoView.seekTo(bar.getProgress());
                dragging = false;
                refreshTimeline();
                showControls();
            }
        });
        controls.addView(timeline);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        controls.addView(buttons);
        Button rewind = new Button(this);
        rewind.setText("−10 с");
        rewind.setOnClickListener(view -> { seekBy(-10_000); showControls(); });
        buttons.addView(rewind);
        playButton = new Button(this);
        playButton.setText("Пауза");
        playButton.setOnClickListener(view -> {
            if (videoView.isPlaying()) videoView.pause();
            else videoView.start();
            refreshTimeline();
            showControls();
        });
        buttons.addView(playButton);
        Button forward = new Button(this);
        forward.setText("+10 с");
        forward.setOnClickListener(view -> { seekBy(10_000); showControls(); });
        buttons.addView(forward);
        timeLabel = new TextView(this);
        timeLabel.setTextColor(Color.WHITE);
        timeLabel.setText("0:00 / 0:00");
        timeLabel.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        buttons.addView(timeLabel, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        setContentView(page);

        videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + video));
        videoView.setOnPreparedListener(player -> {
            duration = Math.max(0, player.getDuration());
            timeline.setMax(duration);
            videoView.start();
            refreshTimeline();
            handler.post(updateTimeline);
        });
        videoView.setOnCompletionListener(player -> refreshTimeline());
        videoView.setOnErrorListener((player, what, extra) -> {
            Toast.makeText(this, "Не удалось воспроизвести видео", Toast.LENGTH_LONG).show();
            return true;
        });
        videoView.requestFocus();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showControls() {
        controls.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideControls);
        handler.postDelayed(hideControls, 4_000);
    }

    private void hideControlsNow() {
        handler.removeCallbacks(hideControls);
        controls.setVisibility(View.GONE);
    }

    private static String formatTime(int milliseconds) {
        int seconds = Math.max(0, milliseconds) / 1000;
        return (seconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60);
    }

    private void seekBy(int milliseconds) {
        videoView.seekTo(Math.max(0, Math.min(duration, videoView.getCurrentPosition() + milliseconds)));
        refreshTimeline();
    }

    private void refreshTimeline() {
        if (videoView == null || timeline == null) return;
        int position = Math.max(0, videoView.getCurrentPosition());
        if (!dragging) {
            timeline.setProgress(position);
            timeLabel.setText(formatTime(position) + " / " + formatTime(duration));
        }
        playButton.setText(videoView.isPlaying() ? "Пауза" : "Воспроизвести");
    }

    @Override protected void onPause() {
        handler.removeCallbacks(updateTimeline);
        handler.removeCallbacks(hideControls);
        if (videoView != null) videoView.pause();
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (videoView != null && duration > 0) handler.post(updateTimeline);
        if (controls != null && controls.getVisibility() == View.VISIBLE) showControls();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(updateTimeline);
        handler.removeCallbacks(hideControls);
        if (videoView != null) videoView.stopPlayback();
        super.onDestroy();
    }
}

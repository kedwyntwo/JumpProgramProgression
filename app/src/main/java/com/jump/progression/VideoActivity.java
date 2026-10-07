package com.jump.progression;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
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

        Button back = new Button(this);
        back.setText("← К тренировке");
        back.setTextColor(Color.WHITE);
        back.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0x99000000));
        back.setOnClickListener(view -> finish());
        page.addView(back, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START));
        setContentView(page);

        videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + video));
        videoView.setOnPreparedListener(player -> videoView.start());
        videoView.setOnErrorListener((player, what, extra) -> {
            Toast.makeText(this, "Не удалось воспроизвести видео", Toast.LENGTH_LONG).show();
            return true;
        });
        videoView.requestFocus();
    }

    @Override protected void onPause() {
        if (videoView != null) videoView.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (videoView != null) videoView.stopPlayback();
        super.onDestroy();
    }
}

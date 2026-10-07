package com.jump.progression;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.MediaController;
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

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.BLACK);
        page.setPadding(16, 16, 16, 16);

        Button back = new Button(this);
        back.setText("Вернуться к тренировке");
        back.setOnClickListener(view -> finish());
        page.addView(back);

        TextView title = new TextView(this);
        title.setText(getIntent().getStringExtra(EXTRA_EXERCISE_NAME));
        title.setTextColor(Color.WHITE);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        page.addView(title);

        videoView = new VideoView(this);
        page.addView(videoView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(page);

        MediaController controls = new MediaController(this);
        controls.setAnchorView(videoView);
        videoView.setMediaController(controls);
        videoView.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + video));
        videoView.setOnPreparedListener(player -> {
            videoView.start();
            controls.show();
        });
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

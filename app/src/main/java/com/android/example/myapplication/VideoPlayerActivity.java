package com.android.example.myapplication;

import android.net.Uri;
import android.os.Bundle;
import android.widget.MediaController;
import android.widget.VideoView;
import androidx.appcompat.app.AppCompatActivity;

public class VideoPlayerActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        VideoView vv = findViewById(R.id.videoView);
        MediaController mc = new MediaController(this);
        vv.setMediaController(mc);
        mc.setAnchorView(vv);

        String local = getIntent().getStringExtra("localPath");
        String remote = getIntent().getStringExtra("remoteUrl");

        if (local != null && !local.isEmpty()) {
            vv.setVideoURI(Uri.fromFile(new java.io.File(local)));
        } else if (remote != null && !remote.isEmpty()) {
            vv.setVideoURI(Uri.parse(remote));
        }
        vv.requestFocus();
        vv.start();
    }
}

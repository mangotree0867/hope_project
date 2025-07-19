package com.android.example.myapplication;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;

public class GalleryActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private VideoAdapter adapter;
    private ArrayList<VideoItem> videoList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadVideoList();
        adapter = new VideoAdapter(this, videoList);
        recyclerView.setAdapter(adapter);
    }

    private void loadVideoList() {
        File directory = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        Log.d("GalleryActivity", "디렉토리 경로: " + (directory != null ? directory.getAbsolutePath() : "null"));
        if (directory != null && directory.exists()) {
            File[] files = directory.listFiles();
            Log.d("GalleryActivity", "파일 개수: " + (files == null ? "null" : files.length));

            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".mp4")) {
                        Log.d("GalleryActivity", "영상 추가됨: " + file.getName());
                        videoList.add(new VideoItem(file.getName(), file.getAbsolutePath()));
                    }
                }
            } else {
                Log.e("GalleryActivity", "파일 리스트가 null입니다.");
            }
        } else {
            Log.e("GalleryActivity", "영상 디렉토리가 존재하지 않습니다.");
        }
    }
}

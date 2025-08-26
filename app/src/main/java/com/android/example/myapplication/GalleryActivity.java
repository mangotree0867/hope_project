package com.android.example.myapplication;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;

// 내부 저장소 갤러리 화면의 액티비티 정의.
public class GalleryActivity extends AppCompatActivity {
    private RecyclerView recyclerView; // 영상 목록을 보여줄 리스트 뷰.
    private VideoAdapter adapter; // 각 행(View)으로 바인딩하는 어댑터.
    private ArrayList<VideoItem> videoList = new ArrayList<>(); // 영상 아이템들(이름/경로 등)을 담는 리스트.

    // 액티비티 생성 시점. activity_gallery.xml 레이아웃을 UI로 설정.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);
        recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        loadVideoList(); // 파일 시스템에서 영상 목록을 읽어와 videoList에 채움.
        adapter = new VideoAdapter(this, videoList); // 어댑터 생성(컨텍스트 + 데이터 전달) 후 RecyclerView에 연결.
        recyclerView.setAdapter(adapter);
    }

    // 앱 전용 외부 저장소의 Movies 디렉터리를 가져옴.
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

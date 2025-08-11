package com.android.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    // 영상 저장 임시 경로
    private String videoFilePath;
    private Uri videoUri;

    // 카메라 권한 요청 런처
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean allGranted = true;
                        for (Boolean granted : result.values()) {
                            if (granted == null || !granted) {
                                allGranted = false;
                                break;
                            }
                        }
                        if (allGranted) {
                            Toast.makeText(this, "권한 허용됨", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "권한 거부됨", Toast.LENGTH_SHORT).show();
                        }
                    }
            );


    // 영상 촬영 처리 런처
    private final ActivityResultLauncher<Intent> videoCaptureLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            Toast.makeText(this, "영상 촬영 완료", Toast.LENGTH_SHORT).show();

                            // Navigate to ChatActivity with video path
                            Intent chatIntent = new Intent(MainActivity.this, ChatActivity.class);
                            chatIntent.putExtra("videoPath", videoUri.toString());
                            startActivity(chatIntent);
                        }
                    }
            );

    // 갤러리 선택 처리 런처
    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri selectedVideo = result.getData().getData();
                            if (selectedVideo != null) {
                                // Navigate to ChatActivity with selected video
                                Intent chatIntent = new Intent(MainActivity.this, ChatActivity.class);
                                chatIntent.putExtra("videoPath", selectedVideo.toString());
                                startActivity(chatIntent);
                            }
                        }
                    }
            );

    // onCreate: 초기 설정 및 버튼 이벤트 연결
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 카메라 권한 요청
        permissionLauncher.launch(new String[]{
                Manifest.permission.CAMERA
        });

        // 영상 촬영 버튼 클릭 이벤트
        findViewById(R.id.btn_video).setOnClickListener(view -> {
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) != null) {
                try {
                    File videoFile = createVideoFile();
                    if (videoFile != null) {
                        videoUri = FileProvider.getUriForFile(
                                getApplicationContext(),
                                getPackageName() + ".fileprovider",
                                videoFile
                        );
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
                        intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1); // 고화질
                        videoCaptureLauncher.launch(intent);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "영상 파일 생성 실패", Toast.LENGTH_SHORT).show();
                }
            }
        });
        findViewById(R.id.btn_gallery).setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            galleryLauncher.launch(intent);
        });
    }


    // 영상 파일 생성
    private File createVideoFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyMMdd_HHmm").format(new Date());
        String videoFileName = timeStamp;
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        File video = File.createTempFile(
                videoFileName, ".mp4", storageDir
        );
        videoFilePath = video.getAbsolutePath();
        return video;
    }

}
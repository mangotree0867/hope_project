package com.android.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
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

    // 사진, 영상 저장 임시 경로
    private String imageFilePath;
    private Uri photoUri;
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

    // 카메라 앱 실행 결과 처리 런처
    private final ActivityResultLauncher<Intent> startActivityResult =
            registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            handleCapturedImage();
                    }
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

                            VideoView videoView = findViewById(R.id.video_view);
                            videoView.setVideoURI(videoUri); // 방금 촬영한 URI
                            videoView.setVisibility(View.VISIBLE); // 화면에 보이게 하기
                            videoView.start(); // 자동 재생
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

        // 사진 촬영 버튼 클릭 이벤트
        findViewById(R.id.btn_capture).setOnClickListener(view -> openCameraApp());
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
            Intent intent = new Intent(MainActivity.this, GalleryActivity.class);
            startActivity(intent);
        });
    }

    // 카메라 앱 실행 및 임시 파일 생성
    private void openCameraApp() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            try {
                File photoFile = createImageFile();
                if (photoFile != null) {
                    photoUri = FileProvider.getUriForFile(
                            getApplicationContext(),
                            getPackageName() + ".fileprovider",
                            photoFile
                    );
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                    startActivityResult.launch(intent);
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "파일 생성 실패", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 임시 이미지 파일 생성 (파일명: TEST_yyyyMMdd_HHmmss_.jpg)
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "TEST_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName, ".jpg", storageDir
        );
        imageFilePath = image.getAbsolutePath();
        return image;
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

    // 촬영한 이미지 불러와서 회전값 보정 후 ImageView에 표시
    private void handleCapturedImage() {
        Bitmap bitmap = BitmapFactory.decodeFile(imageFilePath);
        ExifInterface exif;
        int exifDegree = 0;

        try {
            exif = new ExifInterface(imageFilePath);
            int exifOrientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
            exifDegree = exifOrientationToDegrees(exifOrientation);
        } catch (IOException e) {
            e.printStackTrace();
        }

        ImageView imageView = findViewById(R.id.iv);
        imageView.setImageBitmap(rotate(bitmap, exifDegree));
    }

    // EXIF 회전값을 실제 각도로 변환
    private int exifOrientationToDegrees(int exifOrientation) {
        switch (exifOrientation) {
            case ExifInterface.ORIENTATION_ROTATE_90: return 90;
            case ExifInterface.ORIENTATION_ROTATE_180: return 180;
            case ExifInterface.ORIENTATION_ROTATE_270: return 270;
            default: return 0;
        }
    }

    // Bitmap을 회전시켜 반환
    private Bitmap rotate(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(
                bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true
        );
    }
}
package com.android.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import android.widget.Button;
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

    // [ADD] 로그인 상태에 따라 메인 버튼 역할을 바꿈
    private void updateUIForAuthState() {
        Button btnLeft = findViewById(R.id.btn_login);  // 왼쪽/첫 번째 버튼 영역 재사용
        Button btnRight = findViewById(R.id.btn_guest); // 오른쪽/두 번째 버튼 영역 재사용
        TextView tvUser = findViewById(R.id.tv_user_name_main);
        String token   = getSharedPreferences("auth", MODE_PRIVATE).getString("token", "");
        String name    = getSharedPreferences("auth", MODE_PRIVATE).getString("user_name", "");
        String loginId = getSharedPreferences("auth", MODE_PRIVATE).getString("login_id", "");

        if (token != null && !token.isEmpty()) {
            // [ADD] 로그인 상태 표시
            String display = (name != null && !name.isEmpty()) ? name
                    : (loginId != null && !loginId.isEmpty()) ? loginId
                    : "알 수 없음";
            if (tvUser != null) tvUser.setText("유저명: " + display);
            // 로그인됨 → "채팅창으로 가기" / "로그아웃"
            btnLeft.setText("채팅 목록");
            btnRight.setText("로그아웃");

            btnLeft.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ChatListActivity.class);
                startActivity(intent);
            });
            btnRight.setOnClickListener(v -> {
                getSharedPreferences("auth", MODE_PRIVATE).edit().remove("token").apply();
                Toast.makeText(this, "로그아웃되었습니다.", Toast.LENGTH_SHORT).show();
                updateUIForAuthState();
            });
        } else {
            // 비로그인 → "로그인" / "게스트로 시작"
            if (tvUser != null) tvUser.setText("게스트 모드");
            btnLeft.setText("로그인");
            btnRight.setText("게스트로 시작");

            btnLeft.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);
            });

            btnRight.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                intent.putExtra("isGuest", true);
                startActivity(intent);
            });
        }
    }

    // onCreate: 초기 설정 및 버튼 이벤트 연결
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        updateUIForAuthState();

        Button btnLogin = findViewById(R.id.btn_login);
        Button btnGuest = findViewById(R.id.btn_guest);

        // 로그인 화면으로 이동
        btnLogin.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
        });

        // 게스트로 시작: 채팅 화면으로 이동 (저장 안 함 플래그 전달)
        btnGuest.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ChatActivity.class);
            intent.putExtra("isGuest", true);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUIForAuthState(); // 로그인/로그아웃 후 돌아왔을 때 버튼 갱신
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
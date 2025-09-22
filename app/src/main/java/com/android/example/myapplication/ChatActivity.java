package com.android.example.myapplication;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import android.graphics.Color;
import android.os.Build;
import android.view.WindowManager;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages;
    private Button btnAttachVideo;
    private ConstraintLayout chatRootLayout;
    private LinearLayout toolbarContainer;
    private Uri videoUri;
    private ExecutorService executorService;
    private static final long MAX_VIDEO_SIZE_BYTES = 100 * 1024 * 1024; // 100MB in bytes
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private boolean isGuest = false;
    private String authToken = "";
    private static final String BASE_URL = AppConfig.BASE_URL;
    // [ADD] 현재 세션 ID (0이면 아직 미지정 → 첫 업로드 시 생성됨)
    private int currentSessionId = 0;
    // [ADD] 메시지 로딩 가드
    private volatile boolean isLoadingMessages = false;
    private int loadingSessionId = 0;
    // [ADD] '생각중…' 말풍선 인덱스 (없으면 -1)
    private int typingIndex = -1;
    private volatile boolean isPolling = false;
    private int pollGeneration = 0;
    // [ADD] 위치 권한 런처/서브타이틀 뷰/지오코딩 스레드풀
    private androidx.activity.result.ActivityResultLauncher<String[]> locationPermLauncher;
    private TextView toolbarSubtitle;
    private final java.util.concurrent.ExecutorService geoExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    // [ADD] 지도 열 때 사용할 마지막 위치/주소(위치 권한 허용 시 좌표까지 세팅)
    private Double lastLat = null;
    private Double lastLng = null;
    private String lastAddress = null; // "서울시 강남구 대치동" 같은 표시 문자열
    private ActivityResultLauncher<String> callPermissionLauncher;
    private String emergencyNumber = "112"; // Default emergency number
    private static final String PREF_SESSION_META = "session_meta";

    // Callback interface for API response
    interface ApiCallback {
        void onResult(String response, String error);
    }

    // Video capture launcher
    private final ActivityResultLauncher<Intent> videoCaptureLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        android.util.Log.d("ChatActivity", "Video capture result: " + result.getResultCode());
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            android.util.Log.d("ChatActivity", "Video captured successfully");
                            handleVideoResult(videoUri.toString());
                        } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                            android.util.Log.d("ChatActivity", "Video capture canceled");
                            // Don't finish the activity, just show a message
                            Toast.makeText(this, "동영상 촬영이 취소되었습니다", Toast.LENGTH_SHORT).show();
                        }
                    }
            );

    // Gallery picker launcher
    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri selectedVideo = result.getData().getData();
                            if (selectedVideo != null) {
                                handleVideoResult(selectedVideo.toString());
                            }
                        }
                    }
            );

    // Camera permission launcher
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            android.util.Log.d("ChatActivity", "Camera permission granted");
                            // Permission granted, proceed with recording
                            proceedWithVideoRecording();
                        } else {
                            android.util.Log.d("ChatActivity", "Camera permission denied");
                            // Permission denied, show alert to guide user
                            showCameraPermissionDeniedAlert();
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window wd = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            wd.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            wd.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            wd.setStatusBarColor(Color.parseColor("#000000")); // ← 원하는 색으로
        }

        setContentView(R.layout.activity_chat);

        // Check if we need to show category selection dialog immediately
        boolean needsCategorySelection = false;
        authToken = getSharedPreferences("auth", MODE_PRIVATE).getString("token", "");
        isGuest = getIntent().getBooleanExtra("isGuest", false);
        currentSessionId = getIntent().getIntExtra("sessionId", 0);

        if (!isGuest && authToken != null && !authToken.isEmpty() && currentSessionId == 0) {
            needsCategorySelection = true;
        } else if (isGuest) {
            needsCategorySelection = true;
        }

        // If we need category selection, hide the entire content to prevent flash
        if (needsCategorySelection) {
            findViewById(android.R.id.content).setVisibility(View.INVISIBLE);
        }

        // Load emergency number from settings
        emergencyNumber = SettingsActivity.getEmergencyNumber(this);

        // 1) 권한 요청 런처 등록
        callPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        // 권한 허용됨 → 바로 통화
                        startEmergencyCall();
                    } else {
                        Toast.makeText(this, "통화 권한이 거부되었습니다.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // 2) 버튼 찾아 클릭 리스너 설정
        ImageButton btnEmergency = findViewById(R.id.btnEmergencyCall);
        if (btnEmergency != null) {
            btnEmergency.setOnClickListener(v -> {
                // Inflate custom dialog layout
                View dialogView = getLayoutInflater().inflate(R.layout.dialog_emergency_call, null);
                TextView tvPhoneNumber = dialogView.findViewById(R.id.tv_phone_number);
                tvPhoneNumber.setText(emergencyNumber);

                // Create dialog
                androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setView(dialogView)
                        .setCancelable(true)
                        .create();

                // Set dialog background to transparent to show custom background
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

                // Handle button clicks
                dialogView.findViewById(R.id.btn_cancel).setOnClickListener(cancelView -> dialog.dismiss());

                dialogView.findViewById(R.id.btn_emergency_call).setOnClickListener(callView -> {
                    // 권한 체크 → 없으면 요청, 있으면 바로 통화
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                            == PackageManager.PERMISSION_GRANTED) {
                        startEmergencyCall();
                    } else {
                        callPermissionLauncher.launch(Manifest.permission.CALL_PHONE);
                    }
                    dialog.dismiss();
                });

                dialog.show();
            });
        }
        // [ADD] 목록에서 전달된 세션 ID 수신 (없으면 0)
        currentSessionId = getIntent().getIntExtra("sessionId", 0);

        // [ADD] 세션별로 화면 초기화 (과거 메시지 섞임 방지)
        if (messages != null) {
            messages.clear();
            if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
        }

        // 로그인 사용자인 경우, 지정된 세션이면 메시지 로딩
        authToken = getSharedPreferences("auth", MODE_PRIVATE).getString("token", "");
        isGuest = getIntent().getBooleanExtra("isGuest", false);

        // Only load messages and show dialog if not already shown
        if (!needsCategorySelection) {
            if (!isGuest && authToken != null && !authToken.isEmpty()) {
                if (currentSessionId > 0) {
                    loadMessagesForSession(currentSessionId);
                } else {
                    loadLatestSessionAndMessages();
                }
            }
        }
        TextView tvUserChat = findViewById(R.id.tv_user_name_chat);
        String token   = getSharedPreferences("auth", MODE_PRIVATE).getString("token", "");
        String name    = getSharedPreferences("auth", MODE_PRIVATE).getString("user_name", "");
        String loginId = getSharedPreferences("auth", MODE_PRIVATE).getString("login_id", "");
        boolean isGuest = getIntent().getBooleanExtra("isGuest", false);

        String display;
        if (token != null && !token.isEmpty() && !isGuest) {
            display = (name != null && !name.isEmpty()) ? name
                    : (loginId != null && !loginId.isEmpty()) ? loginId
                    : "알 수 없음";
        } else {
            display = "게스트";
        }
        if (tvUserChat != null) tvUserChat.setText("유저명: " + display);
        authToken = getSharedPreferences("auth", MODE_PRIVATE).getString("token", "");

        // 로그인 상태면 가장 최신 세션과 메시지 로딩
        if (authToken != null && !authToken.isEmpty()) {
            loadLatestSessionAndMessages();
        }
        isGuest = getIntent().getBooleanExtra("isGuest", false);

        recyclerView = findViewById(R.id.recyclerView);
        btnAttachVideo = findViewById(R.id.btnAttachVideo);
        chatRootLayout = findViewById(R.id.chatRootLayout);
        toolbarContainer = findViewById(R.id.toolbar_container);
        toolbarSubtitle = findViewById(R.id.toolbar_subtitle);
        TextView tvLocation = findViewById(R.id.toolbar_subtitle);

        int FIXED_BG = android.graphics.Color.parseColor("#121212");   // 채팅 배경
        int FIXED_HEADER = android.graphics.Color.parseColor("#FFB03B"); // 헤더(상단바) 배경

        chatRootLayout.setBackgroundColor(FIXED_BG);
        toolbarContainer.setBackgroundColor(FIXED_HEADER);

        // 헤더 안 텍스트 색상 고정(흰색)
        TextView titleText = toolbarContainer.findViewById(R.id.toolbar_title);
        TextView subtitleText = toolbarContainer.findViewById(R.id.toolbar_subtitle);
        if (titleText != null) titleText.setTextColor(Color.BLACK);
        if (subtitleText != null) subtitleText.setTextColor(Color.BLACK);

        if (tvLocation != null) {
            // ... 위치 문자열을 만들어 tvLocation.setText(displayAddress); 한 직후에:
            lastAddress = tvLocation.getText().toString();
            // 좌표도 가지고 있다면 여기서 lastLat/lastLng 같이 세팅
            // lastLat = xxx; lastLng = xxx;
        }

        if (tvLocation != null) {
            tvLocation.setText("위치 불러오는 중...");
            // 주소 눌렀을 때 지도/브라우저로 열기 (주소 문자열 그대로 검색)
            tvLocation.setOnClickListener(v -> {
                String q = tvLocation.getText() != null ? tvLocation.getText().toString() : "";
                if (q.isEmpty() || q.contains("위치 불러오는 중") || q.contains("준비되지 않았")) {
                    Toast.makeText(this, "위치 정보가 아직 준비되지 않았습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                // 브라우저/지도앱으로 검색 열기
                Uri uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(q));
                Intent i = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(i);
            });

            // 채팅창 열릴 때마다 최신 위치 갱신
            updateSubtitleWithLocation(tvLocation);
        }
        
        messages = new ArrayList<>();
        chatAdapter = new ChatAdapter(this, messages);
        executorService = Executors.newSingleThreadExecutor();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(chatAdapter);

        // [ADD] 리스트는 정방향으로 스크롤(위→아래)
        androidx.recyclerview.widget.RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        if (lm instanceof androidx.recyclerview.widget.LinearLayoutManager) {
            androidx.recyclerview.widget.LinearLayoutManager llm = (androidx.recyclerview.widget.LinearLayoutManager) lm;
            llm.setReverseLayout(false);     // 역방향 해제
            llm.setStackFromEnd(false);      // 하단 정렬 해제
        }
        
        // Set up video button
        btnAttachVideo.setOnClickListener(v -> showVideoOptions());

        // Get video path from intent
        String videoPath = getIntent().getStringExtra("videoPath");
        if (videoPath != null) {
            handleVideoResult(videoPath);
        }

        // [ADD] 위치 권한 런처 등록
        locationPermLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fine = result.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarse = result.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false);
                    if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                        updateLocationSubtitle();   // 권한 허용 → 위치 표시
                    } else {
                        if (toolbarSubtitle != null) toolbarSubtitle.setText("위치 권한 거부됨");
                    }
                }
        );
        // [ADD] 진입 시 위치 갱신 시도
        updateLocationSubtitle();

        // 위치 권한 체크 및 가져오기
        FusedLocationProviderClient fusedLocationClient =
                LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            // 권한 없으면 요청
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, 1001);
        } else {
            // 권한 있으면 즉시 위치 요청
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            double lat = location.getLatitude();
                            double lng = location.getLongitude();
                            lastLat = lat;
                            lastLng = lng;

                            // 주소 변환
                            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                            try {
                                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                                if (addresses != null && !addresses.isEmpty()) {
                                    String displayAddress = addresses.get(0).getSubLocality(); // 동까지
                                    if (displayAddress == null || displayAddress.isEmpty()) {
                                        displayAddress = addresses.get(0).getLocality(); // 구
                                    }
                                    if (tvLocation != null) {
                                        tvLocation.setText(displayAddress);
                                        if (currentSessionId > 0 && displayAddress != null && !displayAddress.isEmpty()) {
                                            getSharedPreferences("session_meta", MODE_PRIVATE)
                                                    .edit()
                                                    .putString("addr_" + currentSessionId, displayAddress)
                                                    .apply();
                                        }
                                        lastAddress = displayAddress;
                                        if (currentSessionId > 0) {
                                            getSharedPreferences(PREF_SESSION_META, MODE_PRIVATE)
                                                    .edit()
                                                    .putString("session_" + currentSessionId + "_address", displayAddress)
                                                    .apply();
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });
        }

        // Show category selection dialog if needed
        if (needsCategorySelection) {
            // Post to ensure UI is fully initialized
            findViewById(android.R.id.content).post(() -> showNewChatDialog());
        }
    }

    @Override
    public void onBackPressed() {
        if (isGuest) {
            // 게스트는 채팅목록으로 가지 않고 그냥 종료
            finish();
        } else {
            super.onBackPressed(); // 로그인 사용자는 기존 동작 (채팅목록으로)
        }
    }

    private void showVideoOptions() {
        // Create custom dialog with matching chat design
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_video_options, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        EditText editContext = dialogView.findViewById(R.id.edit_context);

        // Set up button listeners
        dialogView.findViewById(R.id.option_record).setOnClickListener(v -> {
            android.util.Log.d("ChatActivity", "Record video button clicked");
            // Save context before proceeding
            String context = editContext.getText().toString().trim();
            saveContextForSession(context);
            dialog.dismiss();
            recordVideo();
        });

        dialogView.findViewById(R.id.option_gallery).setOnClickListener(v -> {
            // Save context before proceeding
            String context = editContext.getText().toString().trim();
            saveContextForSession(context);
            dialog.dismiss();
            openGallery();
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            dialog.dismiss();
        });

        dialog.show();
    }

    private void saveContextForSession(String context) {
        getSharedPreferences("chat_session", MODE_PRIVATE)
                .edit()
                .putString("context_" + currentSessionId, context)
                .apply();
    }

    private void recordVideo() {
        android.util.Log.d("ChatActivity", "recordVideo() called");

        // Check camera permission first
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            android.util.Log.d("ChatActivity", "Camera permission not granted, requesting...");

            // Check if we should show rationale
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CAMERA)) {
                // User has previously denied permission, show explanation
                showCameraPermissionRationale();
            } else {
                // Request permission
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
            }
        } else {
            // Permission already granted, proceed with video recording
            proceedWithVideoRecording();
        }
    }

    private void proceedWithVideoRecording() {
        android.util.Log.d("ChatActivity", "proceedWithVideoRecording() called");
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            try {
                File videoFile = createVideoFile();
                android.util.Log.d("ChatActivity", "Video file created: " + (videoFile != null ? videoFile.getAbsolutePath() : "null"));
                if (videoFile != null) {
                    videoUri = FileProvider.getUriForFile(
                            getApplicationContext(),
                            getPackageName() + ".fileprovider",
                            videoFile
                    );
                    android.util.Log.d("ChatActivity", "Video URI: " + videoUri.toString());
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
                    intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    android.util.Log.d("ChatActivity", "Launching video capture intent");
                    videoCaptureLauncher.launch(intent);
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "동영상 파일 생성 실패", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "카메라 실행 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "카메라 앱을 찾을 수 없습니다", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private File createVideoFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyMMdd_HHmm").format(new Date());
        String videoFileName = "SIGN_" + timeStamp;
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        return File.createTempFile(videoFileName, ".mp4", storageDir);
    }

    private void showCameraPermissionRationale() {
        // Create custom dialog with matching chat design
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_camera_permission, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        // Set dialog background to transparent to show custom background
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Handle button clicks
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "카메라 권한이 없으면 동영상을 촬영할 수 없습니다", Toast.LENGTH_LONG).show();
        });

        dialogView.findViewById(R.id.btn_request).setOnClickListener(v -> {
            dialog.dismiss();
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA);
        });

        dialog.show();
    }

    private void showCameraPermissionDeniedAlert() {
        // Create custom dialog with matching chat design
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_camera_permission_denied, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)  // Force user to make a choice
                .create();

        // Set dialog background to transparent to show custom background
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Handle button clicks
        dialogView.findViewById(R.id.btn_settings).setOnClickListener(v -> {
            dialog.dismiss();
            // Open app settings
            Intent intent = new Intent();
            intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        });

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "카메라 권한이 없으면 동영상을 촬영할 수 없습니다", Toast.LENGTH_LONG).show();
        });

        dialog.show();
    }

    private void handleVideoResult(String videoPath) {
        // 1) 영상 바이너리 읽기
        byte[] videoData = getVideoBinaryData(videoPath);
        if (videoData == null) {
            Toast.makeText(this, "오류: 동영상 파일 읽기 실패", Toast.LENGTH_SHORT).show();
            // 화면에 따로 메시지를 추가하지 않음(중복/깜빡임 방지)
            return;
        }

        // 2) 용량 제한 체크
        if (videoData.length > MAX_VIDEO_SIZE_BYTES) {
            double videoSizeMB = videoData.length / (1024.0 * 1024.0);
            String errorMsg = String.format("동영상이 너무 큽니다: %.1f MB. 최대 허용 크기는 10 MB입니다.", videoSizeMB);
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
            // 화면에 따로 메시지를 추가하지 않음(중복/깜빡임 방지)
            return;
        }

        double videoSizeMB = videoData.length / (1024.0 * 1024.0);
        Toast.makeText(this, String.format("동영상 업로드 중 (%.1f MB)...", videoSizeMB), Toast.LENGTH_SHORT).show();

        // 3) 업로드 + (화면표시/타이핑/재조회)는 sendVideoToServer가 "단일 책임"으로 처리
        sendVideoToServer(videoData, (response, error) -> {
            // 콜백은 여기서 아무 것도 하지 않음 (화면 갱신/타이핑 제거/재조회는 sendVideoToServer 내부)
        });
    }



    private byte[] getVideoBinaryData(String videoPath) {
        try {
            // Handle both file paths and content URIs
            InputStream inputStream;
            
            if (videoPath.startsWith("content://")) {
                // Handle content URI (from gallery)
                Uri uri = Uri.parse(videoPath);
                inputStream = getContentResolver().openInputStream(uri);
            } else {
                // Handle file path (from camera)
                File file = new File(videoPath);
                inputStream = new FileInputStream(file);
            }
            
            if (inputStream != null) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int bytesRead;
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                }
                
                inputStream.close();
                return byteArrayOutputStream.toByteArray();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "동영상 파일 읽기 실패", Toast.LENGTH_SHORT).show();
        }
        
        return null;
    }

    private void sendVideoToServer(byte[] videoData, ApiCallback callback) {
        // [OPTIONAL] 새채팅(세션 미지정) 상태라면 전송 전에 화면 초기화
        if (currentSessionId == 0 && messages != null) {
            runOnUiThread(() -> {
                messages.clear();
                chatAdapter.notifyDataSetChanged();
            });
        }
        // [ADD] 업로드 직전: 바이트를 임시 mp4 파일로 저장 → 썸네일/재생에 사용
        String localTmpPath = null;
        try {
            java.io.File tmp = java.io.File.createTempFile("upload_", ".mp4", getCacheDir());
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(tmp)) {
                fos.write(videoData);
            }
            localTmpPath = tmp.getAbsolutePath();
        } catch (Exception ignored) {}

        // [REPLACE] 임시파일 생성에 성공했을 때만 '내 영상' 메시지 추가 (중복/빈셀 방지)
        if (localTmpPath != null) {
            final String finalLocalPath = localTmpPath;
            runOnUiThread(() -> {
                messages.add(new ChatMessage(finalLocalPath, ChatMessage.TYPE_USER, true));
                messages.get(messages.size()-1).setCreatedAt(System.currentTimeMillis());
                int last = messages.size() - 1;
                if (last >= 0) {
                    ChatMessage lastMsg = messages.get(last);
                    long ts = lastMsg.getCreatedAt();
                    // 직전 '메시지'의 날짜키와 비교 (헤더/타이핑 제외)
                    String lastKey = null;
                    for (int i = last - 1; i >= 0; i--) {
                        ChatMessage prev = messages.get(i);
                        if (prev.getType() == ChatMessage.TYPE_DATE_HEADER || prev.getType() == ChatMessage.TYPE_TYPING) continue;
                        lastKey = dayKey(prev.getCreatedAt());
                        break;
                    }
                    String curKey = dayKey(ts);
                    if (lastKey == null || !curKey.equals(lastKey)) {
                        // 바로 앞에 날짜헤더 삽입
                        messages.add(last, ChatMessage.dateHeader(dayLabel(ts), ts));
                        chatAdapter.notifyItemInserted(last);
                    }
                }
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerView.scrollToPosition(messages.size() - 1);
            });
        }

        // [REPLACE] 서버 응답 기다리는 동안 '생각중…' 말풍선 표시 (중복 방지)
        runOnUiThread(() -> {
            // 기존 타이핑 버블 제거(있다면)
            if (typingIndex >= 0 && typingIndex < messages.size()
                    && messages.get(typingIndex).getType() == ChatMessage.TYPE_TYPING) {
                messages.remove(typingIndex);
                chatAdapter.notifyItemRemoved(typingIndex);
                typingIndex = -1;
            }

            // 새 타이핑 버블 추가
            typingIndex = messages.size();
            messages.add(new ChatMessage("...", ChatMessage.TYPE_TYPING));
            chatAdapter.notifyItemInserted(typingIndex);
            recyclerView.scrollToPosition(messages.size() - 1);
        });
        executorService.execute(() -> {
            try {
                // [REPLACE] 세션이 있으면 해당 세션으로 업로드, 없으면 새 세션 생성
                String endpoint = BASE_URL + "/predict-video";
                if (currentSessionId > 0) {
                    endpoint += "?session_id=" + currentSessionId;
                }
                URL url = new URL(endpoint);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                // Generate a proper boundary
                String boundary = "----FormBoundary" + System.currentTimeMillis();
                String LINE_FEED = "\r\n";
                
                // Set up the connection
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                // [REPLACE] Authorization 헤더: 로그인 상태면 Bearer 주입, 아니면 게스트 안내 후 중단
                if (!isGuest && authToken != null && !authToken.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + authToken);
                }
                connection.setConnectTimeout(30000); // 30 seconds connect timeout
                connection.setReadTimeout(30000); // 30 seconds read timeout
                
                // Get selected category and context
                SharedPreferences prefs = getSharedPreferences("chat_session", MODE_PRIVATE);
                String selectedCategory = prefs.getString("category_" + currentSessionId, "");
                String userContext = prefs.getString("context_" + currentSessionId, "");

                // Build multipart form data properly
                StringBuilder formData = new StringBuilder();

                // Add category field if available
                if (!selectedCategory.isEmpty()) {
                    formData.append("--").append(boundary).append(LINE_FEED);
                    formData.append("Content-Disposition: form-data; name=\"category\"").append(LINE_FEED);
                    formData.append(LINE_FEED);
                    formData.append(selectedCategory).append(LINE_FEED);
                }

                // Add context field (user input or empty)
                formData.append("--").append(boundary).append(LINE_FEED);
                formData.append("Content-Disposition: form-data; name=\"context\"").append(LINE_FEED);
                formData.append(LINE_FEED);
                formData.append(userContext).append(LINE_FEED);

                // Add file field
                formData.append("--").append(boundary).append(LINE_FEED);
                formData.append("Content-Disposition: form-data; name=\"file\"; filename=\"video.mp4\"").append(LINE_FEED);
                formData.append("Content-Type: application/octet-stream").append(LINE_FEED);
                formData.append(LINE_FEED);

                String formDataEnd = LINE_FEED + "--" + boundary + "--" + LINE_FEED;

                byte[] startBytes = formData.toString().getBytes("UTF-8");
                byte[] endBytes = formDataEnd.getBytes("UTF-8");
                
                connection.setRequestProperty("Content-Length", 
                    String.valueOf(startBytes.length + videoData.length + endBytes.length));
                
                // Write the multipart form data to the request body
                OutputStream outputStream = connection.getOutputStream();
                outputStream.write(startBytes);
                outputStream.write(videoData);
                outputStream.write(endBytes);
                outputStream.flush();
                outputStream.close();
                
                // Get the response
                int responseCode = connection.getResponseCode();
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read the response body
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    final String responseBody = response.toString();

                    try {
                        org.json.JSONObject resp = new org.json.JSONObject(responseBody);
                        final String assistantText = resp.optString("sentence", "");
                        final int sid = resp.optInt("session_id", 0);

                        runOnUiThread(() -> {
                            // 타이핑 제거
                            if (typingIndex >= 0 && typingIndex < messages.size()) {
                                messages.remove(typingIndex);
                                chatAdapter.notifyItemRemoved(typingIndex);
                                typingIndex = -1;
                            }

                            // 로그인 사용자라면 session_id 기억 + 폴링
                            if (!isGuest) {
                                if (currentSessionId == 0 && sid > 0) {
                                    currentSessionId = sid;
                                    // 세션 ID가 새로 설정되면 세션 상세정보로부터 위치 정보 업데이트
                                    updateLocationFromSession(currentSessionId);
                                }
                                // 세션 id 갱신 직후 주소 저장 (채팅창 상단에 이미 주소가 찍혀 있다면)
                                String savedAddrNow = null;
                                TextView tvLocation = findViewById(R.id.toolbar_subtitle);
                                if (tvLocation != null) {
                                    savedAddrNow = tvLocation.getText() != null ? tvLocation.getText().toString() : null;
                                }

                                // lastAddress 변수를 쓰고 있다면 우선순위로 활용
                                if ((savedAddrNow == null || savedAddrNow.isEmpty()) && lastAddress != null) {
                                    savedAddrNow = lastAddress;
                                }

                                if (currentSessionId > 0 && savedAddrNow != null && !savedAddrNow.isEmpty()
                                        && !"위치 불러오는 중...".equals(savedAddrNow)
                                        && !"위치 정보가 아직 준비되지 않았습니다".equals(savedAddrNow)) {
                                    getSharedPreferences("session_meta", MODE_PRIVATE)
                                            .edit()
                                            .putString("addr_" + currentSessionId, savedAddrNow)
                                            .apply();
                                }
                                startPollingAssistantUntilReply(currentSessionId);
                            } else {
                                // 게스트라면 응답 메시지 즉시 표시
                                messages.add(new ChatMessage(assistantText, ChatMessage.TYPE_BOT));
                                chatAdapter.notifyItemInserted(messages.size() - 1);
                                recyclerView.scrollToPosition(messages.size() - 1);
                            }
                        });
                    } catch (Exception ignore) {}
                } else {
                    // Handle error response
                    String errorMessage = "서버 오류 (코드 " + responseCode + ")";
                    try {
                        InputStream errorStream = connection.getErrorStream();
                        if (errorStream != null) {
                            BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream));
                            StringBuilder errorResponse = new StringBuilder();
                            String line;
                            while ((line = errorReader.readLine()) != null) {
                                errorResponse.append(line);
                            }
                            errorReader.close();
                            errorMessage += ": " + errorResponse.toString();
                        }
                    } catch (Exception errorException) {
                        errorMessage += ": 오류 세부사항을 읽을 수 없음";
                    }
                    
                    final String finalErrorMessage = errorMessage;
                    runOnUiThread(() -> {
                        // 타이핑 제거
                        if (typingIndex >= 0 && typingIndex < messages.size()) {
                            messages.remove(typingIndex);
                            chatAdapter.notifyItemRemoved(typingIndex);
                            typingIndex = -1;
                        }
                        // 오류 말풍선
                        messages.add(new ChatMessage("AI 응답 오류: " + finalErrorMessage, ChatMessage.TYPE_BOT));
                        chatAdapter.notifyItemInserted(messages.size() - 1);
                        recyclerView.scrollToPosition(messages.size() - 1);
                    });
                }
                
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                String errorMessage = "네트워크 오류: " + e.getMessage();
                runOnUiThread(() -> {
                    // 타이핑 제거
                    if (typingIndex >= 0 && typingIndex < messages.size()) {
                        messages.remove(typingIndex);
                        chatAdapter.notifyItemRemoved(typingIndex);
                        typingIndex = -1;
                    }
                    // 예외 말풍선
                    messages.add(new ChatMessage("네트워크 오류: " + errorMessage, ChatMessage.TYPE_BOT));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    recyclerView.scrollToPosition(messages.size() - 1);
                });
            }
        });
    }
    // [ADD] 최신 세션 불러오기 → 메시지 목록 호출
    private void loadLatestSessionAndMessages() {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/chat-sessions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Authorization", "Bearer " + authToken);

                int code = conn.getResponseCode();
                if (code == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                        String line; while ((line = br.readLine()) != null) sb.append(line);
                    }
                    org.json.JSONObject res = new org.json.JSONObject(sb.toString());
                    org.json.JSONArray sessions = res.getJSONArray("sessions");
                    if (sessions.length() > 0) {
                        int sessionId = sessions.getJSONObject(0).getInt("id"); // 가장 최근 세션
                        loadMessagesForSession(sessionId);
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    // [ADD] 특정 세션의 메시지 불러와서 RecyclerView에 반영
    private void loadMessagesForSession(int sessionId) {
        // [ADD] 중복 로딩 방지 및 세션 일치 가드
        if (isLoadingMessages) return;
        isLoadingMessages = true;
        loadingSessionId = sessionId;
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + "/chat-sessions/" + sessionId + "/messages");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setRequestProperty("Authorization", "Bearer " + authToken);

                int code = conn.getResponseCode();
                if (code == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                        String line; while ((line = br.readLine()) != null) sb.append(line);
                    }
                    org.json.JSONObject res = new org.json.JSONObject(sb.toString());
                    org.json.JSONArray msgs = res.getJSONArray("messages");

                    runOnUiThread(() -> {
                        try {
                            // [GUARD] 로딩 도중 세션이 바뀌었으면 이 응답은 폐기
                            if (loadingSessionId != currentSessionId) return;

                            // [REPLACE] 덮어쓰기 방식: 먼저 비우고 새로 채움
                            messages.clear();

                            // [CHANGE] 서버가 최신→오래된 순으로 주는 경우를 가정, 역순으로 추가해 시간순 정렬
                            for (int i = msgs.length() - 1; i >= 0; i--) {
                                org.json.JSONObject m = msgs.getJSONObject(i);
                                String role = m.optString("role", "");
                                String text = m.optString("message_text", "");

                                if ("assistant".equalsIgnoreCase(role)) {
                                    messages.add(new ChatMessage(text, ChatMessage.TYPE_BOT));
                                    // [ADD] 서버 created_at 파싱 → ChatMessage.createdAt 반영
                                    long ts = parseCreatedAtMillis(m.optString("created_at", null));
                                    messages.get(messages.size()-1).setCreatedAt(ts);
                                } else {
                                    // [REPLACE] 사용자 메시지: 영상 우선으로 렌더
                                    String mediaUrl = m.optString("media_url", null);  // ← 서버 응답 키명
                                    if (mediaUrl != null && !mediaUrl.isEmpty()) {
                                        // 저장내역(원격) 영상 메시지
                                        messages.add(new ChatMessage(ChatMessage.TYPE_USER, true, mediaUrl));
                                        // [ADD] 서버 created_at 파싱 → ChatMessage.createdAt 반영
                                        long ts = parseCreatedAtMillis(m.optString("created_at", null));
                                        messages.get(messages.size()-1).setCreatedAt(ts);
                                    } else {
                                        // 영상 URL이 없으면 텍스트로만 보이던 과거 레코드 대비
                                        String fallback = (text == null || text.isEmpty()) ? "(업로드된 메시지)" : text;
                                        messages.add(new ChatMessage(fallback, ChatMessage.TYPE_USER));
                                        // [ADD] 서버 created_at 파싱 → ChatMessage.createdAt 반영
                                        long ts = parseCreatedAtMillis(m.optString("created_at", null));
                                        messages.get(messages.size()-1).setCreatedAt(ts);
                                    }
                                }
                            }
                            List<ChatMessage> withHeaders = injectDateHeaders(new java.util.ArrayList<>(messages));
                            messages.clear();
                            messages.addAll(withHeaders);
                            chatAdapter.notifyDataSetChanged();
                            // msgs: 서버 응답의 메시지 배열(JSONArray). 이미 같은 이름을 쓰고 있지?
                            if (msgs.length() > 0 && currentSessionId > 0) {
                                // 서버는 최신 → 오래된 순이므로 0번이 최신
                                String latestCreated = msgs.getJSONObject(0).optString("created_at", "");
                                long latestMillis = parseCreatedAtMillis(latestCreated);
                                getSharedPreferences(PREF_SESSION_META, MODE_PRIVATE)
                                        .edit()
                                        .putLong("session_" + currentSessionId + "_lastAt", latestMillis)
                                        .apply();
                            }
                            if (!messages.isEmpty()) recyclerView.scrollToPosition(messages.size() - 1);
                        } catch (org.json.JSONException e) {
                            Toast.makeText(ChatActivity.this, "대화 불러오기 중 JSON 오류", Toast.LENGTH_SHORT).show();
                        } finally {
                            isLoadingMessages = false; // [ADD] 로딩 종료
                        }
                    });
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    // [ADD] 봇 답장이 DB에 저장될 때까지 /messages 재조회
    private void startPollingAssistantUntilReply(int sessionId) {
        if (sessionId <= 0) return;
        // 이전 폴링 중지(세션 전환/다중 전송 대비)
        pollGeneration++;
        final int myGen = pollGeneration;
        isPolling = true;

        new Thread(() -> {
            try {
                int tries = 0;
                while (isPolling && myGen == pollGeneration && tries < 40) { // 최대 ~60초(1.5s*40)
                    tries++;

                    // GET /chat-sessions/{id}/messages
                    HttpURLConnection c = null;
                    try {
                        URL url = new URL(BASE_URL + "/chat-sessions/" + sessionId + "/messages");
                        c = (HttpURLConnection) url.openConnection();
                        c.setRequestMethod("GET");
                        c.setRequestProperty("Authorization", "Bearer " + authToken);
                        c.setConnectTimeout(5000);
                        c.setReadTimeout(10000);

                        int code = c.getResponseCode();
                        if (code == 200) {
                            StringBuilder sb = new StringBuilder();
                            try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                                String line; while ((line = br.readLine()) != null) sb.append(line);
                            }
                            org.json.JSONObject obj = new org.json.JSONObject(sb.toString());
                            org.json.JSONArray msgs = obj.getJSONArray("messages");

                            // 최신(서버는 message_order desc) 중 'assistant' + message_text 있는지 확인
                            boolean found = false;
                            for (int i = 0; i < msgs.length(); i++) {
                                org.json.JSONObject m = msgs.getJSONObject(i);
                                String role = m.optString("role", "");
                                String t = m.optString("message_text", "");
                                if ("assistant".equalsIgnoreCase(role) && t != null && t.trim().length() > 0) {
                                    found = true;
                                    break;
                                }
                            }

                            if (found) {
                                // UI를 '정방향'으로 갱신(우리가 쓰는 렌더 규칙 그대로)
                                runOnUiThread(() -> {
                                    // 1) 타이핑 제거
                                    if (typingIndex >= 0 && typingIndex < messages.size()) {
                                        messages.remove(typingIndex);
                                        chatAdapter.notifyItemRemoved(typingIndex);
                                        typingIndex = -1;
                                    }

                                    // 2) 전체 목록을 서버 결과로 다시 그림 (정방향: 오래된→최신)
                                    try {
                                        messages.clear();
                                        for (int i = msgs.length() - 1; i >= 0; i--) {
                                            org.json.JSONObject m = msgs.getJSONObject(i);
                                            String role = m.optString("role", "");
                                            String text = m.optString("message_text", "");
                                            String mediaUrl = m.optString("media_url", null);

                                            if ("assistant".equalsIgnoreCase(role)) {
                                                messages.add(new ChatMessage(text, ChatMessage.TYPE_BOT));
                                                long ts = parseCreatedAtMillis(m.optString("created_at", null));
                                                messages.get(messages.size()-1).setCreatedAt(ts);
                                            } else {
                                                if (mediaUrl != null && !mediaUrl.isEmpty()) {
                                                    messages.add(new ChatMessage(ChatMessage.TYPE_USER, true, mediaUrl));
                                                    long ts = parseCreatedAtMillis(m.optString("created_at", null));
                                                    messages.get(messages.size()-1).setCreatedAt(ts);
                                                } else {
                                                    String fallback = (text == null || text.isEmpty()) ? "(업로드된 메시지)" : text;
                                                    messages.add(new ChatMessage(fallback, ChatMessage.TYPE_USER));
                                                    long ts = parseCreatedAtMillis(m.optString("created_at", null));
                                                    messages.get(messages.size()-1).setCreatedAt(ts);
                                                }
                                            }
                                        }
                                        List<ChatMessage> withHeaders = injectDateHeaders(new java.util.ArrayList<>(messages));
                                        messages.clear();
                                        messages.addAll(withHeaders);
                                        chatAdapter.notifyDataSetChanged();
                                        if (msgs.length() > 0 && currentSessionId > 0) {
                                            // 서버는 최신 → 오래된 순이므로 0번이 최신
                                            String latestCreated = msgs.getJSONObject(0).optString("created_at", "");
                                            long latestMillis = parseCreatedAtMillis(latestCreated);
                                            getSharedPreferences(PREF_SESSION_META, MODE_PRIVATE)
                                                    .edit()
                                                    .putLong("session_" + currentSessionId + "_lastAt", latestMillis)
                                                    .apply();
                                        }
                                        if (!messages.isEmpty()) recyclerView.scrollToPosition(messages.size() - 1);
                                    } catch (org.json.JSONException ignore) {}

                                    // 폴링 종료
                                    isPolling = false;
                                });
                                return; // 스레드 종료
                            }
                        }
                    } catch (Exception ignore) {
                        // 네트워크 에러는 다음 루프에서 재시도
                    } finally {
                        if (c != null) c.disconnect();
                    }

                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                }

                // 타임아웃: 타이핑 제거 + 안내
                runOnUiThread(() -> {
                    if (typingIndex >= 0 && typingIndex < messages.size()) {
                        messages.remove(typingIndex);
                        chatAdapter.notifyItemRemoved(typingIndex);
                        typingIndex = -1;
                    }
                    messages.add(new ChatMessage("(처리가 지연되고 있습니다. 잠시 후 자동 갱신됩니다.)", ChatMessage.TYPE_BOT));
                    chatAdapter.notifyItemInserted(messages.size() - 1);
                    recyclerView.scrollToPosition(messages.size() - 1);
                    isPolling = false;
                });
            } catch (Exception ignored) { isPolling = false; }
        }).start();
    }

    // [ADD] ISO/일반 포맷 대응 파서
    private static long parseCreatedAtMillis(String s) {
        if (s == null || s.isEmpty()) return System.currentTimeMillis();
        // 시도 1: java.time (API 26+)
        try {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(s);
                return odt.toInstant().toEpochMilli();
            }
        } catch (Throwable ignore) {}
        // 시도 2: 대표 ISO 패턴들
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",
                "yyyy-MM-dd'T'HH:mm:ssX",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss'Z'"
        };
        for (String p : patterns) {
            try {
                java.text.SimpleDateFormat f = new java.text.SimpleDateFormat(p, java.util.Locale.US);
                f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                return f.parse(s).getTime();
            } catch (Throwable ignore) {}
        }
        return System.currentTimeMillis();
    }

    // 날짜 key 추출(yyyyMMdd)
    private static String dayKey(long millis) {
        return new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.KOREA)
                .format(new java.util.Date(millis));
    }

    // 보기용 라벨(예: 2025년 9월 18일 목요일)
    private static String dayLabel(long millis) {
        return new java.text.SimpleDateFormat("yyyy년 M월 d일 E요일", java.util.Locale.KOREA)
                .format(new java.util.Date(millis));
    }

    /** 메시지 리스트(오래된→최신 순) 앞뒤 사이사이에 날짜 헤더 삽입 */
    private List<ChatMessage> injectDateHeaders(List<ChatMessage> src) {
        List<ChatMessage> out = new java.util.ArrayList<>();
        String lastKey = null;
        for (ChatMessage m : src) {
            if (m.getType() == ChatMessage.TYPE_TYPING) {
                // 타이핑 버블은 헤더 검사 없이 그대로
                out.add(m);
                continue;
            }
            long ts = m.getCreatedAt() == 0 ? System.currentTimeMillis() : m.getCreatedAt();
            String key = dayKey(ts);
            if (!key.equals(lastKey)) {
                out.add(ChatMessage.dateHeader(dayLabel(ts), ts));  // ← 헤더 삽입
                lastKey = key;
            }
            out.add(m);
        }
        return out;
    }

    // [ADD] 위치 권한 체크 → 마지막 위치 얻기 → 역지오코딩 → 서브타이틀 세팅
    private void updateLocationSubtitle() {
        if (toolbarSubtitle == null) return;

        // 권한 체크
        boolean hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED;

        boolean hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED;

        if (!hasFine && !hasCoarse) {
            // 권한 요청
            locationPermLauncher.launch(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
            });
            return;
        }

        // 마지막 위치 시도 (LocationManager 사용: 추가 의존성 불필요)
        try {
            android.location.LocationManager lm =
                    (android.location.LocationManager) getSystemService(android.content.Context.LOCATION_SERVICE);

            android.location.Location loc = null;
            if (hasFine) {
                loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);
            }
            if (loc == null) {
                loc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER);
            }

            if (loc == null) {
                // 즉시 얻지 못하면 간단히 네트워크 프로바이더로 1회 요청(타임아웃 대용)
                lm.requestSingleUpdate(android.location.LocationManager.NETWORK_PROVIDER, new android.location.LocationListener() {
                    @Override public void onLocationChanged(@NonNull android.location.Location location) {
                        resolveAndSetAddress(location.getLatitude(), location.getLongitude());
                    }
                    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                    @Override public void onProviderEnabled(@NonNull String provider) {}
                    @Override public void onProviderDisabled(@NonNull String provider) {}
                }, null);
                if (toolbarSubtitle != null) toolbarSubtitle.setText("위치 탐색 중…");
            } else {
                resolveAndSetAddress(loc.getLatitude(), loc.getLongitude());
            }
        } catch (Throwable t) {
            if (toolbarSubtitle != null) toolbarSubtitle.setText("위치 확인 불가");
        }
    }

    // [ADD] 위/경도 → 주소 문자열(시/구/동)로 변환 후 서브타이틀 세팅
    private void resolveAndSetAddress(double lat, double lon) {
        if (toolbarSubtitle == null) return;

        geoExecutor.execute(() -> {
            String label = "위치 확인 불가";
            try {
                if (android.location.Geocoder.isPresent()) {
                    android.location.Geocoder geocoder = new android.location.Geocoder(this, java.util.Locale.KOREAN);
                    java.util.List<android.location.Address> list = geocoder.getFromLocation(lat, lon, 1);
                    if (list != null && !list.isEmpty()) {
                        android.location.Address a = list.get(0);
                        // 행정 구역 조합 (지역별 반환 필드 차이를 최대한 흡수)
                        String siDo   = safe(a.getAdminArea());        // 서울특별시/경기도...
                        String siGunGu= firstNonEmpty(safe(a.getSubAdminArea()), safe(a.getLocality())); // 강남구/수원시 등
                        String dong   = firstNonEmpty(safe(a.getSubLocality()), safe(a.getThoroughfare()), safe(a.getFeatureName())); // 대치동/XX로/지번 등

                        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
                        if (!siDo.isEmpty()) parts.add(siDo);
                        if (!siGunGu.isEmpty()) parts.add(siGunGu);
                        if (!dong.isEmpty()) parts.add(dong);

                        if (!parts.isEmpty()) {
                            label = android.text.TextUtils.join(" ", parts);
                        }
                    }
                } else {
                    label = "지오코더 미지원 기기";
                }
            } catch (Throwable ignore) {}

            final String finalLabel = label;
            runOnUiThread(() -> {
                if (toolbarSubtitle != null) toolbarSubtitle.setText(finalLabel);
            });
        });
    }

    private String buildShortKoreanAddress(double lat, double lng) {
        try {
            java.util.Locale locale = java.util.Locale.KOREA;
            android.location.Geocoder g = new android.location.Geocoder(this, locale);
            java.util.List<android.location.Address> list = g.getFromLocation(lat, lng, 1);
            if (list == null || list.isEmpty()) return null;
            android.location.Address a = list.get(0);

            // 시/도
            String siDo = safe(a.getAdminArea());         // 예: 서울특별시/경기도
            // 구/군(지역에 따라 subLocality 또는 subAdminArea에 있음)
            String guGun = safe(a.getSubLocality());
            if (guGun.isEmpty()) guGun = safe(a.getSubAdminArea()); // fallback
            // 동/로
            String dongRo = safe(a.getThoroughfare());    // 예: 대치동/테헤란로

            // 일부 단말에서는 thoroughfare가 빈 값인 경우, featureName이 "OO동"으로 오는 케이스도 존재
            if (dongRo.isEmpty()) {
                String feat = safe(a.getFeatureName());
                if (feat.endsWith("동") || feat.endsWith("로") || feat.endsWith("가")) dongRo = feat;
            }

            // 필요한 최소 단위는 "시/도 구/군 동"
            String joined = joinNonEmpty(siDo, guGun, dongRo);
            if (!joined.isEmpty()) return joined;

            // 실패 시 locality(시/군/구)라도
            String loc = safe(a.getLocality());
            if (!loc.isEmpty()) return loc;

            // 최후: 전체주소(길어질 수 있음)
            String line0 = safe(a.getAddressLine(0));
            return line0.isEmpty() ? null : line0;
        } catch (Exception ignore) {
            return null;
        }
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }
    private static String joinNonEmpty(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.trim().isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(p.trim());
            }
        }
        return sb.toString();
    }
    private static String firstNonEmpty(String... arr) {
        for (String s : arr) if (s != null && !s.trim().isEmpty()) return s.trim();
        return "";
    }

    private void updateSubtitleWithLocation(TextView tvLocation) {
        FusedLocationProviderClient fused =
                LocationServices.getFusedLocationProviderClient(this);

        // 권한 체크는 기존에 넣어둔 런타임 퍼미션 로직을 그대로 사용하세요.
        // (여기서는 간결히 생략)
        fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener(loc -> {
                    if (loc == null) {
                        tvLocation.setText("위치 확인 불가");
                        return;
                    }
                    lastLat = loc.getLatitude();
                    lastLng = loc.getLongitude();

                    // Geocoder는 블로킹이므로 워커스레드에서
                    new Thread(() -> {
                        String addr = buildShortKoreanAddress(lastLat, lastLng);
                        runOnUiThread(() -> {
                            if (addr == null || addr.isEmpty()) {
                                tvLocation.setText("위치 확인 불가");
                            } else {
                                lastAddress = addr;
                                tvLocation.setText(addr); // 예: "서울특별시 강남구 대치동"
                            }
                        });
                    }).start();
                })
                .addOnFailureListener(e -> tvLocation.setText("위치 확인 불가"));
    }

    private void showNewChatDialog() {
        // Hide the camera button when showing category selection
        if (btnAttachVideo != null) {
            btnAttachVideo.setVisibility(View.GONE);
        }
        // Also hide the input card layout that contains the button
        View inputLayout = findViewById(R.id.inputLayout);
        if (inputLayout != null) {
            inputLayout.setVisibility(View.GONE);
        }

        // Inflate custom layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_category_selection, null);

        // Create dialog with custom view
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .setCancelable(true)  // Allow back button to dismiss
                .create();

        // Handle dialog dismiss to finish activity without restoring UI
        dialog.setOnCancelListener(dialogInterface -> {
            // Don't restore UI visibility since we're closing the activity
            // Use overridePendingTransition to remove animation
            finish();
            overridePendingTransition(0, 0);
        });

        // Remove dim background completely
        if (dialog.getWindow() != null) {
            dialog.getWindow().setDimAmount(0f);
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Set back button listener
        ImageButton btnBack = dialogView.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            // Don't restore UI visibility since we're closing the activity
            dialog.dismiss();
            finish(); // Go back to chat list
            overridePendingTransition(0, 0); // No animation
        });

        // Set click listeners for each category button
        LinearLayout btnFire = dialogView.findViewById(R.id.btn_fire);
        LinearLayout btnCity = dialogView.findViewById(R.id.btn_city);
        LinearLayout btnExternalInjury = dialogView.findViewById(R.id.btn_external_injury);
        LinearLayout btnInternalInjury = dialogView.findViewById(R.id.btn_internal_injury);

        btnFire.setOnClickListener(v -> {
            // Save selected category
            getSharedPreferences("chat_session", MODE_PRIVATE)
                    .edit()
                    .putString("category_" + currentSessionId, "화재상황")
                    .apply();
            Toast.makeText(this, "화재상황 선택됨", Toast.LENGTH_SHORT).show();
            // Restore button visibility and show content
            findViewById(android.R.id.content).setVisibility(View.VISIBLE);
            if (btnAttachVideo != null) {
                btnAttachVideo.setVisibility(View.VISIBLE);
            }
            if (inputLayout != null) {
                inputLayout.setVisibility(View.VISIBLE);
            }
            dialog.dismiss();
        });

        btnCity.setOnClickListener(v -> {
            // Save selected category
            getSharedPreferences("chat_session", MODE_PRIVATE)
                    .edit()
                    .putString("category_" + currentSessionId, "도심상황")
                    .apply();
            Toast.makeText(this, "도심상황 선택됨", Toast.LENGTH_SHORT).show();
            // Restore button visibility and show content
            findViewById(android.R.id.content).setVisibility(View.VISIBLE);
            if (btnAttachVideo != null) {
                btnAttachVideo.setVisibility(View.VISIBLE);
            }
            if (inputLayout != null) {
                inputLayout.setVisibility(View.VISIBLE);
            }
            dialog.dismiss();
        });

        btnExternalInjury.setOnClickListener(v -> {
            // Save selected category
            getSharedPreferences("chat_session", MODE_PRIVATE)
                    .edit()
                    .putString("category_" + currentSessionId, "외상")
                    .apply();
            Toast.makeText(this, "외상 선택됨", Toast.LENGTH_SHORT).show();
            // Restore button visibility and show content
            findViewById(android.R.id.content).setVisibility(View.VISIBLE);
            if (btnAttachVideo != null) {
                btnAttachVideo.setVisibility(View.VISIBLE);
            }
            if (inputLayout != null) {
                inputLayout.setVisibility(View.VISIBLE);
            }
            dialog.dismiss();
        });

        btnInternalInjury.setOnClickListener(v -> {
            // Save selected category
            getSharedPreferences("chat_session", MODE_PRIVATE)
                    .edit()
                    .putString("category_" + currentSessionId, "내상")
                    .apply();
            Toast.makeText(this, "내상 선택됨", Toast.LENGTH_SHORT).show();
            // Restore button visibility and show content
            findViewById(android.R.id.content).setVisibility(View.VISIBLE);
            if (btnAttachVideo != null) {
                btnAttachVideo.setVisibility(View.VISIBLE);
            }
            if (inputLayout != null) {
                inputLayout.setVisibility(View.VISIBLE);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void startEmergencyCall() {
        try {
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + emergencyNumber));
            startActivity(intent);
        } catch (Exception e) {
            // 혹시라도 ACTION_CALL이 막히면 다이얼러로 fallback
            Intent dial = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + emergencyNumber));
            startActivity(dial);
        }
    }

    private void updateLocationFromSession(int sessionId) {
        if (sessionId <= 0) return;

        executorService.execute(() -> {
            try {
                URL url = new URL(BASE_URL + "/chat-sessions/" + sessionId);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");

                if (!isGuest && authToken != null && !authToken.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + authToken);
                }

                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    org.json.JSONObject sessionData = new org.json.JSONObject(response.toString());
                    String locationInfo = sessionData.optString("location", "");

                    if (!locationInfo.isEmpty()) {
                        runOnUiThread(() -> {
                            lastAddress = locationInfo;
                            TextView tvLocation = findViewById(R.id.toolbar_subtitle);
                            if (tvLocation != null) {
                                tvLocation.setText(locationInfo);
                            }
                        });
                    }
                }
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    @Override
    protected void onResume() {
        super.onResume();
        android.util.Log.d("ChatActivity", "onResume called");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                proceedWithVideoRecording();
            } else {
                // Permission denied
                showCameraPermissionDeniedAlert();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        android.util.Log.d("ChatActivity", "onPause called");
    }

    @Override
    protected void onStop() {
        super.onStop();
        android.util.Log.d("ChatActivity", "onStop called");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        android.util.Log.d("ChatActivity", "onDestroy called");
        if (executorService != null) {
            executorService.shutdown();
        }
    }

}
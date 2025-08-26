package com.android.example.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.media.MediaMetadataRetriever;
import android.graphics.Bitmap;
import android.util.Base64;
import org.json.JSONObject;
import org.json.JSONArray;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView recyclerView;                    // 채팅 메시지 목록 뷰
    private ChatAdapter chatAdapter;                      // 메시지 바인딩 어댑터
    private List<ChatMessage> messages;                   // 메시지 데이터 소스
    private Button btnAttachVideo;                        // "동영상 전송" 버튼(하단)
    private ImageButton btnColorPicker;                   // 헤더 색상 선택 버튼(우상단)
    private ConstraintLayout chatRootLayout;              // 전체 배경(테마 적용 대상)
    private LinearLayout toolbarContainer;                // 커스텀 툴바 컨테이너
    private Uri videoUri;                                 // 촬영 결과가 저장될 URI (FileProvider)
    private ExecutorService executorService;              // 백그라운드 작업 스레드풀
    private static final String PREF_CHAT_BG_COLOR = "chat_bg_color";
    private static final String PREF_HEADER_COLOR = "header_color";

    // 네트워크 응답을 UI 스레드에서 받기 위한 간단한 콜백 타입.
    interface ApiCallback {
        void onResult(String response, String error);
    }

    // 촬영 launcher
    private final ActivityResultLauncher<Intent> videoCaptureLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            handleVideoResult(videoUri.toString());
                        }
                    }
            );

    // 갤러리 launcher
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

    // 초기화/바인딩/테마 로드/인텐트 처리
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);              // 레이아웃 적용
        recyclerView   = findViewById(R.id.recyclerView);    // 목록 참조
        btnAttachVideo = findViewById(R.id.btnAttachVideo);  // 전송 버튼 참조
        btnColorPicker = findViewById(R.id.btnColorPicker);  // 컬러피커 버튼 참조
        chatRootLayout = findViewById(R.id.chatRootLayout);  // 배경 레이아웃
        toolbarContainer = findViewById(R.id.toolbar_container); // 헤더 컨테이너
        messages = new ArrayList<>(); // 빈 메시지 리스트 준비
        chatAdapter = new ChatAdapter(this, messages); // 어댑터 생성
        executorService = Executors.newSingleThreadExecutor(); // 백그라운드 스레드 준비
        recyclerView.setLayoutManager(new LinearLayoutManager(this)); // 수직 리스트
        recyclerView.setAdapter(chatAdapter); // 어댑터 연결
        loadSavedTheme(); // 저장된 테마(배경/헤더) 로드
        btnAttachVideo.setOnClickListener(v -> showVideoOptions()); // 전송 옵션창
        btnColorPicker.setOnClickListener(v -> showThemePicker()); // 테마 선택창
        String videoPath = getIntent().getStringExtra("videoPath"); // 외부에서 전달된 영상 경로 및 처리 로직
        if (videoPath != null) {
            handleVideoResult(videoPath);
        }
    }

    // 동영상 전송 옵션창
    private void showVideoOptions() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_video_options, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        // 각 버튼 동작 연결 후 표시
        dialogView.findViewById(R.id.option_record).setOnClickListener(v -> {
            dialog.dismiss();
            recordVideo();
        });
        dialogView.findViewById(R.id.option_gallery).setOnClickListener(v -> {
            dialog.dismiss();
            openGallery();
        });
        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            dialog.dismiss();
        });
        dialog.show();
    }

    // 영상 촬영 시작
    private void recordVideo() {
        Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE); // 기본 카메라 앱 호출
        if (intent.resolveActivity(getPackageManager()) != null) { // 처리 가능한 앱 존재 확인
            try {
                File videoFile = createVideoFile(); // 저장할 임시 파일 생성
                if (videoFile != null) {
                    videoUri = FileProvider.getUriForFile( // 파일을 content:// URI로 래핑
                            getApplicationContext(),
                            getPackageName() + ".fileprovider",
                            videoFile
                    );
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri); // 촬영 결과를 이 URI로 저장
                    intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                    videoCaptureLauncher.launch(intent); // 촬영 시작
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "동영상 파일 생성 실패", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // 갤러리에서 영상 선택
    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    // 촬영 결과 저장
    private File createVideoFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyMMdd_HHmm").format(new Date()); // 파일명에 쓰일 타임스탬프
        String videoFileName = "SIGN_" + timeStamp;
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
        return File.createTempFile(videoFileName, ".mp4", storageDir);
    }

    // 촬영 및 갤러리 선택 후처리
    private void handleVideoResult(String videoPath) {
        // Add user message with video
        ChatMessage userMsg = new ChatMessage("동영상 전송됨", ChatMessage.TYPE_USER, videoPath);
        userMsg.setTimestampMs(System.currentTimeMillis()); // 전송 시간 기록
        messages.add(userMsg);
        chatAdapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);
        showTypingIndicator();

        try {
            Uri uri = videoPath.startsWith("content://")
                    ? Uri.parse(videoPath) // 갤러리: content URI
                    : Uri.fromFile(new File(videoPath)); // 촬영: 파일 경로 → file URI

            // 프레임 추출(최대 48장, 가로 480px로 리사이즈)
            List<String> frames = extractFramesBase64(uri, /*maxFrames=*/50, /*targetWidth=*/480);
            Toast.makeText(this, "프레임 " + frames.size() + "장 업로드 중...", Toast.LENGTH_SHORT).show();

            // 서버로 JSON 전송 (/predict_sequence)
            sendFramesJsonToServer(frames, (response, error) -> {
                hideTypingIndicator();
                String botMessage = (error != null)
                        ? "동영상 처리 오류: " + error + "\n"
                        : response;
                ChatMessage botMsg = new ChatMessage(botMessage, ChatMessage.TYPE_BOT);
                botMsg.setTimestampMs(System.currentTimeMillis()); // 응답 시간 기록
                messages.add(botMsg);
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerView.scrollToPosition(messages.size() - 1);
            });
        } catch (Exception e) {
            hideTypingIndicator();
            ChatMessage errMsg = new ChatMessage("오류: 프레임 추출 실패 - " + e.getMessage(), ChatMessage.TYPE_BOT);
            errMsg.setTimestampMs(System.currentTimeMillis());
            messages.add(errMsg);
            chatAdapter.notifyItemInserted(messages.size() - 1);
            recyclerView.scrollToPosition(messages.size() - 1);
        }
    }

    // 미리 저장한 테마 적용
    private void loadSavedTheme() {
        int savedBgColor = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
                .getInt(PREF_CHAT_BG_COLOR, getResources().getColor(R.color.chat_bg_default));
        chatRootLayout.setBackgroundColor(savedBgColor); // 배경 컬러 적용
        int savedHeaderRes = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
                .getInt(PREF_HEADER_COLOR, R.drawable.header_gradient_yellow);
        toolbarContainer.setBackgroundResource(savedHeaderRes); // 헤더 배경 drawable 적용
        updateHeaderTextColors(savedHeaderRes);  // 헤더 텍스트/아이콘 색 보정
    }

    // 테마 선택창
    private void showThemePicker() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_theme_picker, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        // 헤더 색상 리스트
        dialogView.findViewById(R.id.header_yellow).setOnClickListener(v -> {changeHeaderColor(R.drawable.header_gradient_yellow);});
        dialogView.findViewById(R.id.header_red).setOnClickListener(v -> {changeHeaderColor(R.drawable.header_gradient_red);});
        dialogView.findViewById(R.id.header_purple).setOnClickListener(v -> {changeHeaderColor(R.drawable.header_gradient_purple);});
        dialogView.findViewById(R.id.header_cyan).setOnClickListener(v -> {changeHeaderColor(R.drawable.header_gradient_cyan);});
        dialogView.findViewById(R.id.header_green).setOnClickListener(v -> {changeHeaderColor(R.drawable.header_gradient_green);});
        dialogView.findViewById(R.id.header_orange).setOnClickListener(v -> {changeHeaderColor(R.drawable.header_gradient_orange);});
        // 채팅 배경 색상 리스트
        dialogView.findViewById(R.id.bg_default).setOnClickListener(v -> {changeBackgroundColor(R.color.chat_bg_default);});
        dialogView.findViewById(R.id.bg_midnight).setOnClickListener(v -> {changeBackgroundColor(R.color.chat_bg_midnight);});
        dialogView.findViewById(R.id.bg_ocean).setOnClickListener(v -> {changeBackgroundColor(R.color.chat_bg_ocean);});
        dialogView.findViewById(R.id.bg_forest).setOnClickListener(v -> {changeBackgroundColor(R.color.chat_bg_forest);});
        dialogView.findViewById(R.id.bg_purple).setOnClickListener(v -> {changeBackgroundColor(R.color.chat_bg_purple);});
        dialogView.findViewById(R.id.bg_charcoal).setOnClickListener(v -> {changeBackgroundColor(R.color.chat_bg_charcoal);});
        dialog.show();
    }

    // 배경 색 적용 및 저장
    private void changeBackgroundColor(int colorResId) {
        int color = getResources().getColor(colorResId);
        chatRootLayout.setBackgroundColor(color);
        getSharedPreferences("ChatPrefs", MODE_PRIVATE)
                .edit()
                .putInt(PREF_CHAT_BG_COLOR, color)
                .apply();
        Toast.makeText(this, "테마 업데이트됨", Toast.LENGTH_SHORT).show();
    }

    // 헤더 색 적용 및 저장
    private void changeHeaderColor(int drawableResId) {
        toolbarContainer.setBackgroundResource(drawableResId);
        getSharedPreferences("ChatPrefs", MODE_PRIVATE)
                .edit()
                .putInt(PREF_HEADER_COLOR, drawableResId)
                .apply();
        updateHeaderTextColors(drawableResId);
        Toast.makeText(this, "테마 업데이트됨", Toast.LENGTH_SHORT).show();
    }

    // 헤더 색상 텍스트 및 아이콘 대비 보정
    private void updateHeaderTextColors(int headerDrawableRes) {
        TextView titleText = toolbarContainer.findViewById(R.id.toolbar_title);
        TextView subtitleText = toolbarContainer.findViewById(R.id.toolbar_subtitle);

        if (headerDrawableRes == R.drawable.header_gradient_yellow || 
            headerDrawableRes == R.drawable.header_gradient_cyan) {
            // Dark text for light backgrounds
            int darkColor = 0xFF3D2914; // 밝은 헤더엔 어두운 텍스트
            if (titleText != null) titleText.setTextColor(darkColor);
            if (subtitleText != null) subtitleText.setTextColor(0xFF6B5D54);
            btnColorPicker.setColorFilter(darkColor);  // 아이콘 틴트
        } else {
            if (titleText != null) titleText.setTextColor(getResources().getColor(R.color.white));
            if (subtitleText != null) subtitleText.setTextColor(getResources().getColor(R.color.text_secondary));
            btnColorPicker.setColorFilter(getResources().getColor(R.color.white));
        }
    }

    // 생각 중.. 문구 추가
    private void showTypingIndicator() {
        runOnUiThread(() -> {
            // Add typing indicator message
            ChatMessage typingMessage = new ChatMessage("생각 중...", ChatMessage.TYPE_TYPING);
            messages.add(typingMessage);
            chatAdapter.notifyItemInserted(messages.size() - 1);
            recyclerView.scrollToPosition(messages.size() - 1);
        });
    }

    // 타이핑 메시지 제거
    private void hideTypingIndicator() {
        runOnUiThread(() -> {
            // Remove typing indicator message
            for (int i = messages.size() - 1; i >= 0; i--) { // 뒤에서부터 검색
                if (messages.get(i).getType() == ChatMessage.TYPE_TYPING) {
                    messages.remove(i);
                    chatAdapter.notifyItemRemoved(i);
                    break; // 가장 최근 것만 제거
                }
            }
        });
    }

    // 영상 프레임 추출, 리사이즈, 인코딩
    private List<String> extractFramesBase64(Uri uri, int maxFrames, int targetWidth) throws Exception {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever(); // 동영상 메타/프레임 접근자
        mmr.setDataSource(this, uri); // 대상 URI 설정
        String durMsStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION); // 영상 길이(밀리초) 읽기
        long durationMs = (durMsStr != null) ? Long.parseLong(durMsStr) : 0L;
        if (durationMs <= 0) throw new IllegalArgumentException("영상 길이를 알 수 없습니다.");
        int frames = Math.min(maxFrames, 50); // 안전상 상한 50
        long stepUs = (durationMs * 1000L) / Math.max(frames, 1); // 균등 간격(마이크로초)

        List<String> list = new ArrayList<>(frames); // 결과 리스트
        for (int i = 0; i < frames; i++) {
            long timeUs = i * stepUs; // i번째 추출 시점
            Bitmap bmp = mmr.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST); // 프레임 추출
            if (bmp == null) continue;
            if (targetWidth > 0 && bmp.getWidth() > targetWidth) { // 폭 기준 다운스케일
                int w = targetWidth;
                int h = Math.max(1, (int) (bmp.getHeight() * (w / (float) bmp.getWidth())));
                bmp = Bitmap.createScaledBitmap(bmp, w, h, true);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream(); // JPEG 압축 → 메모리
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos); // JPEG 품질 80
            String b64 = Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP); // Base64 문자열
            list.add(b64);
            bos.close();
            bmp.recycle(); // 비트맵 메모리 해제
        }
        mmr.release(); // 리소스 해제
        return list; // Base64 프레임 배열
    }

    // JSON POST 정송
    private void sendFramesJsonToServer(List<String> base64Frames, ApiCallback callback) {
        executorService.execute(() -> { // 워커 스레드에서 네트워크
            HttpURLConnection conn = null;
            try {
                URL url = new URL("http://192.168.0.104:8000/predict_sequence"); // 서버 엔드포인트
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST"); // POST
                conn.setDoOutput(true); // 바디 전송
                conn.setConnectTimeout(30000); // 연결 타임아웃
                conn.setReadTimeout(30000);    // 응답 타임아웃
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

                JSONArray arr = new JSONArray(); // 프레임 배열 생성
                for (String b64 : base64Frames) arr.put(b64);
                JSONObject payload = new JSONObject();
                payload.put("image_sequence", arr);
                byte[] out = payload.toString().getBytes("UTF-8"); // JSON 직렬화
                conn.getOutputStream().write(out); // 전송
                conn.getOutputStream().flush();
                conn.getOutputStream().close();

                int code = conn.getResponseCode(); // HTTP 상태코드
                InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(is));
                StringBuilder sb = new StringBuilder(); // 응답 읽기
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                final String body = sb.toString();
                if (code >= 200 && code < 300) {
                    runOnUiThread(() -> callback.onResult(body, null)); // 성공 콜백
                } else {
                    runOnUiThread(() -> callback.onResult(null, "HTTP " + code + ": " + body)); // 실패 콜백
                }
            } catch (Exception e) {
                final String err = "네트워크 오류: " + e.getMessage();
                runOnUiThread(() -> callback.onResult(null, err)); // 예외 콜백
            } finally {
                if (conn != null) conn.disconnect();  // 연결 해제
            }
        });
    }

    // 스레드풀 정리 (액티비티 종료 시 백그라운드 작업 정리.)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
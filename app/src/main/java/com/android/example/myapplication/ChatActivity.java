package com.android.example.myapplication;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
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

public class ChatActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages;
    private Button btnAttachVideo;
    private ImageButton btnColorPicker;
    private ConstraintLayout chatRootLayout;
    private LinearLayout toolbarContainer;
    private TextView titleText, subtitleText;
    private ImageView logoImage;
    private Uri videoUri;
    private ExecutorService executorService;
    private static final String PREF_CHAT_BG_COLOR = "chat_bg_color";
    private static final String PREF_HEADER_COLOR = "header_color";
    private static final long MAX_VIDEO_SIZE_BYTES = 10 * 1024 * 1024; // 10MB in bytes

    // Callback interface for API response
    interface ApiCallback {
        void onResult(String response, String error);
    }

    // Video capture launcher
    private final ActivityResultLauncher<Intent> videoCaptureLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            handleVideoResult(videoUri.toString());
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        recyclerView = findViewById(R.id.recyclerView);
        btnAttachVideo = findViewById(R.id.btnAttachVideo);
        btnColorPicker = findViewById(R.id.btnColorPicker);
        chatRootLayout = findViewById(R.id.chatRootLayout);
        toolbarContainer = findViewById(R.id.toolbar_container);
        
        messages = new ArrayList<>();
        chatAdapter = new ChatAdapter(this, messages);
        executorService = Executors.newSingleThreadExecutor();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(chatAdapter);

        // Load saved theme colors
        loadSavedTheme();
        
        // Set up video button
        btnAttachVideo.setOnClickListener(v -> showVideoOptions());
        
        // Set up theme picker button
        btnColorPicker.setOnClickListener(v -> showThemePicker());

        // Get video path from intent
        String videoPath = getIntent().getStringExtra("videoPath");
        if (videoPath != null) {
            handleVideoResult(videoPath);
        }
    }

    private void showVideoOptions() {
        // Create custom dialog with matching chat design
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_video_options, null);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        
        // Set up button listeners
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

    private void recordVideo() {
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
                    intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
                    videoCaptureLauncher.launch(intent);
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "동영상 파일 생성 실패", Toast.LENGTH_SHORT).show();
            }
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

    private void handleVideoResult(String videoPath) {
        // Add user message with video
        messages.add(new ChatMessage("동영상 전송됨", ChatMessage.TYPE_USER, videoPath));
        chatAdapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);

        // Show typing indicator
        showTypingIndicator();

        // Get video binary data and send to server
        byte[] videoData = getVideoBinaryData(videoPath);
        if (videoData != null) {
            // Check video size limit
            if (videoData.length > MAX_VIDEO_SIZE_BYTES) {
                hideTypingIndicator();
                double videoSizeMB = videoData.length / (1024.0 * 1024.0);
                String errorMsg = String.format("동영상이 너무 큽니다: %.1f MB. 최대 허용 크기는 10 MB입니다.", videoSizeMB);
                messages.add(new ChatMessage("오류: " + errorMsg, ChatMessage.TYPE_BOT));
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerView.scrollToPosition(messages.size() - 1);
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                return;
            }
            
            double videoSizeMB = videoData.length / (1024.0 * 1024.0);
            Toast.makeText(this, String.format("동영상 업로드 중 (%.1f MB)...", videoSizeMB), Toast.LENGTH_SHORT).show();
            sendVideoToServer(videoData, (response, error) -> {
                // This callback runs on the main thread
                hideTypingIndicator();
                
                String botMessage;
                if (error != null) {
                    botMessage = "동영상 처리 오류: " + error;
                } else {
                    botMessage = response;
                }
                
                messages.add(new ChatMessage(botMessage, ChatMessage.TYPE_BOT));
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerView.scrollToPosition(messages.size() - 1);
            });
        } else {
            // If video data is null, show error message
            hideTypingIndicator();
            messages.add(new ChatMessage("오류: 동영상 파일 읽기 실패", ChatMessage.TYPE_BOT));
            chatAdapter.notifyItemInserted(messages.size() - 1);
            recyclerView.scrollToPosition(messages.size() - 1);
        }
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
        executorService.execute(() -> {
            try {
                URL url = new URL("http://192.168.34.63:8000/predict-video");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                // Generate a proper boundary
                String boundary = "----FormBoundary" + System.currentTimeMillis();
                String LINE_FEED = "\r\n";
                
                // Set up the connection
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setRequestProperty("Authorization", "Bearer ACCESS_TOKEN");
                connection.setConnectTimeout(30000); // 30 seconds connect timeout
                connection.setReadTimeout(30000); // 30 seconds read timeout
                
                // Build multipart form data properly
                StringBuilder formData = new StringBuilder();
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
                    
                    // Handle success on the main thread
                    runOnUiThread(() -> {
                        Toast.makeText(this, "동영상 처리 성공", Toast.LENGTH_SHORT).show();
                        callback.onResult(responseBody, null);
                    });
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
                        Toast.makeText(this, "업로드 실패: " + responseCode, Toast.LENGTH_SHORT).show();
                        callback.onResult(null, finalErrorMessage);
                    });
                }
                
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                String errorMessage = "네트워크 오류: " + e.getMessage();
                runOnUiThread(() -> {
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    callback.onResult(null, errorMessage);
                });
            }
        });
    }

    private void loadSavedTheme() {
        // Load background color
        int savedBgColor = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
                .getInt(PREF_CHAT_BG_COLOR, getResources().getColor(R.color.chat_bg_default));
        chatRootLayout.setBackgroundColor(savedBgColor);
        
        // Load header color
        int savedHeaderRes = getSharedPreferences("ChatPrefs", MODE_PRIVATE)
                .getInt(PREF_HEADER_COLOR, R.drawable.header_gradient_yellow);
        toolbarContainer.setBackgroundResource(savedHeaderRes);
        
        // Update text colors based on header
        updateHeaderTextColors(savedHeaderRes);
    }
    
    private void showThemePicker() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_theme_picker, null);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        
        // Header color listeners
        dialogView.findViewById(R.id.header_yellow).setOnClickListener(v -> {
            changeHeaderColor(R.drawable.header_gradient_yellow);
        });
        
        dialogView.findViewById(R.id.header_red).setOnClickListener(v -> {
            changeHeaderColor(R.drawable.header_gradient_red);
        });
        
        dialogView.findViewById(R.id.header_purple).setOnClickListener(v -> {
            changeHeaderColor(R.drawable.header_gradient_purple);
        });
        
        dialogView.findViewById(R.id.header_cyan).setOnClickListener(v -> {
            changeHeaderColor(R.drawable.header_gradient_cyan);
        });
        
        dialogView.findViewById(R.id.header_green).setOnClickListener(v -> {
            changeHeaderColor(R.drawable.header_gradient_green);
        });
        
        dialogView.findViewById(R.id.header_orange).setOnClickListener(v -> {
            changeHeaderColor(R.drawable.header_gradient_orange);
        });
        
        // Background color listeners
        dialogView.findViewById(R.id.bg_default).setOnClickListener(v -> {
            changeBackgroundColor(R.color.chat_bg_default);
        });
        
        dialogView.findViewById(R.id.bg_midnight).setOnClickListener(v -> {
            changeBackgroundColor(R.color.chat_bg_midnight);
        });
        
        dialogView.findViewById(R.id.bg_ocean).setOnClickListener(v -> {
            changeBackgroundColor(R.color.chat_bg_ocean);
        });
        
        dialogView.findViewById(R.id.bg_forest).setOnClickListener(v -> {
            changeBackgroundColor(R.color.chat_bg_forest);
        });
        
        dialogView.findViewById(R.id.bg_purple).setOnClickListener(v -> {
            changeBackgroundColor(R.color.chat_bg_purple);
        });
        
        dialogView.findViewById(R.id.bg_charcoal).setOnClickListener(v -> {
            changeBackgroundColor(R.color.chat_bg_charcoal);
        });
        
        dialog.show();
    }
    
    private void changeBackgroundColor(int colorResId) {
        int color = getResources().getColor(colorResId);
        chatRootLayout.setBackgroundColor(color);
        
        // Save the selected color
        getSharedPreferences("ChatPrefs", MODE_PRIVATE)
                .edit()
                .putInt(PREF_CHAT_BG_COLOR, color)
                .apply();
    }
    
    private void changeHeaderColor(int drawableResId) {
        toolbarContainer.setBackgroundResource(drawableResId);
        
        // Save the selected header
        getSharedPreferences("ChatPrefs", MODE_PRIVATE)
                .edit()
                .putInt(PREF_HEADER_COLOR, drawableResId)
                .apply();
        
        // Update text colors based on header
        updateHeaderTextColors(drawableResId);
        
        Toast.makeText(this, "테마 업데이트됨", Toast.LENGTH_SHORT).show();
    }
    
    private void updateHeaderTextColors(int headerDrawableRes) {
        // Find text views in toolbar
        TextView titleText = toolbarContainer.findViewById(R.id.toolbar_title);
        TextView subtitleText = toolbarContainer.findViewById(R.id.toolbar_subtitle);
        ImageView logoImage = toolbarContainer.findViewById(R.id.toolbar_logo);
        
        // Set colors based on header type
        if (headerDrawableRes == R.drawable.header_gradient_yellow || 
            headerDrawableRes == R.drawable.header_gradient_cyan) {
            // Dark text for light backgrounds
            int darkColor = 0xFF3D2914;
            if (titleText != null) titleText.setTextColor(darkColor);
            if (subtitleText != null) subtitleText.setTextColor(0xFF6B5D54);
            if (logoImage != null) logoImage.setColorFilter(darkColor);
            btnColorPicker.setColorFilter(darkColor);
        } else {
            // White text for dark backgrounds
            if (titleText != null) titleText.setTextColor(getResources().getColor(R.color.white));
            if (subtitleText != null) subtitleText.setTextColor(getResources().getColor(R.color.text_secondary));
            if (logoImage != null) logoImage.setColorFilter(getResources().getColor(R.color.white));
            btnColorPicker.setColorFilter(getResources().getColor(R.color.white));
        }
    }
    
    private void showTypingIndicator() {
        runOnUiThread(() -> {
            // Add typing indicator message
            ChatMessage typingMessage = new ChatMessage("생각 중...", ChatMessage.TYPE_TYPING);
            messages.add(typingMessage);
            chatAdapter.notifyItemInserted(messages.size() - 1);
            recyclerView.scrollToPosition(messages.size() - 1);
        });
    }
    
    private void hideTypingIndicator() {
        runOnUiThread(() -> {
            // Remove typing indicator message
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i).getType() == ChatMessage.TYPE_TYPING) {
                    messages.remove(i);
                    chatAdapter.notifyItemRemoved(i);
                    break;
                }
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

}
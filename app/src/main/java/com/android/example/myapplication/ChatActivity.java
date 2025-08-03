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
import android.widget.Toast;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
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
    private Uri videoUri;
    private ExecutorService executorService;

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
        
        messages = new ArrayList<>();
        chatAdapter = new ChatAdapter(this, messages);
        executorService = Executors.newSingleThreadExecutor();

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(chatAdapter);

        // Set up video button
        btnAttachVideo.setOnClickListener(v -> showVideoOptions());

        // Get video path from intent
        String videoPath = getIntent().getStringExtra("videoPath");
        if (videoPath != null) {
            handleVideoResult(videoPath);
        }
    }

    private void showVideoOptions() {
        // Show dialog to choose between camera and gallery
        String[] options = {"Record Video", "Choose from Gallery"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Video")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        recordVideo();
                    } else {
                        openGallery();
                    }
                })
                .show();
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
                Toast.makeText(this, "Failed to create video file", Toast.LENGTH_SHORT).show();
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
        messages.add(new ChatMessage("Video sent", ChatMessage.TYPE_USER, videoPath));
        chatAdapter.notifyItemInserted(messages.size() - 1);
        recyclerView.scrollToPosition(messages.size() - 1);

        // Get video binary data and send to server
        byte[] videoData = getVideoBinaryData(videoPath);
        if (videoData != null) {
            Toast.makeText(this, "Uploading video (" + videoData.length + " bytes)...", Toast.LENGTH_SHORT).show();
            sendVideoToServer(videoData, (response, error) -> {
                // This callback runs on the main thread
                String botMessage;
                if (error != null) {
                    botMessage = "Error processing video: " + error;
                } else {
                    botMessage = response;
                }
                
                messages.add(new ChatMessage(botMessage, ChatMessage.TYPE_BOT));
                chatAdapter.notifyItemInserted(messages.size() - 1);
                recyclerView.scrollToPosition(messages.size() - 1);
            });
        } else {
            // If video data is null, show error message
            messages.add(new ChatMessage("Error: Failed to read video file", ChatMessage.TYPE_BOT));
            chatAdapter.notifyItemInserted(messages.size() - 1);
            recyclerView.scrollToPosition(messages.size() - 1);
        }
    }

    private String interpretSignLanguage(String videoPath) {
        // This is where you would integrate with your sign language interpretation API/model
        // For now, returning a placeholder response
        return "I detected the following sign language gestures:\n\n" +
               "\"Hello, how are you today?\"\n\n" +
               "The signs were clear and the interpretation confidence is high.";
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
            Toast.makeText(this, "Failed to read video file", Toast.LENGTH_SHORT).show();
        }
        
        return null;
    }

    private void sendVideoToServer(byte[] videoData, ApiCallback callback) {
        executorService.execute(() -> {
            try {
                URL url = new URL("http://192.168.98.63:8000/process_video");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                
                // Generate a proper boundary
                String boundary = "----FormBoundary" + System.currentTimeMillis();
                String LINE_FEED = "\r\n";
                
                // Set up the connection
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                
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
                        Toast.makeText(this, "Video processed successfully", Toast.LENGTH_SHORT).show();
                        callback.onResult(responseBody, null);
                    });
                } else {
                    // Handle error response
                    String errorMessage = "Server error (code " + responseCode + ")";
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
                        errorMessage += ": Unable to read error details";
                    }
                    
                    final String finalErrorMessage = errorMessage;
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Upload failed: " + responseCode, Toast.LENGTH_SHORT).show();
                        callback.onResult(null, finalErrorMessage);
                    });
                }
                
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                String errorMessage = "Network error: " + e.getMessage();
                runOnUiThread(() -> {
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    callback.onResult(null, errorMessage);
                });
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
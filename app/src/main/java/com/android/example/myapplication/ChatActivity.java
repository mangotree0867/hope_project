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
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messages;
    private Button btnAttachVideo;
    private Uri videoUri;

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

        // Simulate processing and add bot response after delay
        new Handler().postDelayed(() -> {
            String interpretation = interpretSignLanguage(videoPath);
            messages.add(new ChatMessage(interpretation, ChatMessage.TYPE_BOT));
            chatAdapter.notifyItemInserted(messages.size() - 1);
            recyclerView.scrollToPosition(messages.size() - 1);
        }, 2000); // 2 second delay to simulate processing
    }

    private String interpretSignLanguage(String videoPath) {
        // This is where you would integrate with your sign language interpretation API/model
        // For now, returning a placeholder response
        return "I detected the following sign language gestures:\n\n" +
               "\"Hello, how are you today?\"\n\n" +
               "The signs were clear and the interpretation confidence is high.";
    }

}
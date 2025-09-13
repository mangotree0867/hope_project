package com.android.example.myapplication;

public class ChatMessage {
    public static final int TYPE_USER = 0;
    public static final int TYPE_BOT = 1;
    public static final int TYPE_TYPING = 2;

    private String message;
    private int type;
    private String videoPath;

    public ChatMessage(String message, int type) {
        this.message = message;
        this.type = type;
    }

    public ChatMessage(String message, int type, String videoPath) {
        this.message = message;
        this.type = type;
        this.videoPath = videoPath;
    }

    public String getMessage() {
        return message;
    }

    public int getType() {
        return type;
    }

    public String getVideoPath() {
        return videoPath;
    }
}
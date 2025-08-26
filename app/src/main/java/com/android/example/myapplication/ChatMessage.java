package com.android.example.myapplication;

public class ChatMessage {
    public static final int TYPE_USER = 0; // 사용자가 보낸 메시지 (오른쪽 말풍선, 썸네일 포함 가능)
    public static final int TYPE_BOT = 1; // 봇이 보낸 메시지 (왼쪽 말풍선)
    public static final int TYPE_TYPING = 2; // “생각 중…” 표시용 타이핑 인디케이터 (실제 텍스트 아님, 로딩 상태)
    private long timestampMs = 0L; // 메시지 발생 시각
    private String message; // 메시지 본문 텍스트
    private int type; // 위의 상수 중 하나를 담는 메시지 타입.
    private String videoPath; // 동영상 메시지일 때 비디오 경로

    // 기본 생성자(동영상 없음)
    public ChatMessage(String message, int type) {
        this.message = message;
        this.type = type;
    }

    // 동영상 포함 생성자
    public ChatMessage(String message, int type, String videoPath) {
        this.message = message;
        this.type = type;
        this.videoPath = videoPath;
    }

    // 본문 get
    public String getMessage() {
        return message;
    }

    // 타입 get
    public int getType() {
        return type;
    }

    // 비디오 경로 get
    public String getVideoPath() {
        return videoPath;
    }

    // 시간 get
    public long getTimestampMs() {
        return timestampMs;
    }
    // 시간 set
    public void setTimestampMs(long timestampMs) {
        this.timestampMs = timestampMs;
    }
}
package com.android.example.myapplication;

import android.graphics.Bitmap;

public class ChatMessage {
    public static final int TYPE_USER = 0;
    public static final int TYPE_BOT = 1;
    public static final int TYPE_TYPING = 2;
    public static final int TYPE_DATE_HEADER = 99;

    private String message;
    private int type;
    private String videoPath;

    // [ADD] 영상 관련
    private boolean isVideo;            // 이 메시지가 영상인지 여부
    private String localVideoPath;      // 업로드 직후 로컬 경로
    private String remoteVideoUrl;      // 서버에서 내려주는 media_url
    private Bitmap videoThumbnail;      // 캐싱된 썸네일
    // [ADD] 보낸 시간(ms)
    private long createdAt;
    private boolean isDateHeader = false;
    public boolean isDateHeader() { return isDateHeader; }

    // --- 텍스트 메시지 ---
    public ChatMessage(String message, int type) {
        this.message = message;
        this.type = type;
        this.isVideo = false;
        this.localVideoPath = null;
        this.remoteVideoUrl = null;
        this.videoPath = null;
        this.videoThumbnail = null;
        this.createdAt = System.currentTimeMillis(); // [ADD]
    }

    // --- 기존 방식: videoPath 포함 (호환성) ---
    public ChatMessage(String message, int type, String videoPath) {
        this.message = message;
        this.type = type;
        this.videoPath = videoPath;
        this.isVideo = true;
        this.localVideoPath = videoPath;
        this.remoteVideoUrl = null;
        this.videoThumbnail = null;
        this.createdAt = System.currentTimeMillis(); // [ADD]
    }

    // --- 새: 로컬 영상 전용 ---
    public ChatMessage(String localVideoPath, int type, boolean isVideo) {
        this.message = null;
        this.type = type;
        this.isVideo = isVideo;
        this.localVideoPath = localVideoPath;
        this.remoteVideoUrl = null;
        this.videoPath = localVideoPath;   // [중요] 기존 코드 호환
        this.videoThumbnail = null;
        this.createdAt = System.currentTimeMillis(); // [ADD]
    }
    // --- 새: 원격 영상 전용 ---
    public ChatMessage(int type, boolean isVideo, String remoteVideoUrl) {
        this.message = null;
        this.type = type;
        this.isVideo = isVideo;
        this.localVideoPath = null;
        this.remoteVideoUrl = remoteVideoUrl;
        this.videoPath = remoteVideoUrl;   // [중요] 기존 코드 호환
        this.videoThumbnail = null;
        this.createdAt = System.currentTimeMillis(); // [ADD]
    }

    public ChatMessage(String dateText) {
        this.message = dateText;
        this.type = TYPE_DATE_HEADER;
        this.isDateHeader = true;
    }

    // [ADD] 날짜 헤더 전용 팩토리
    public static ChatMessage dateHeader(String label, long when) {
        ChatMessage m = new ChatMessage(label, TYPE_DATE_HEADER);
        m.isDateHeader = true;
        m.createdAt = when;   // 헤더도 기준 시간 보유(정렬/비교용)
        return m;
    }

    // --- Getter/Setter ---
    public String getMessage() { return message; }
    public int getType() { return type; }

    // 기존 코드 호환용
    public String getVideoPath() { return videoPath; }

    // 새 API
    public boolean isVideo() { return isVideo; }
    public String getLocalVideoPath() { return localVideoPath; }
    public String getRemoteVideoUrl() { return remoteVideoUrl; }
    public Bitmap getVideoThumbnail() { return videoThumbnail; }
    public void setVideoThumbnail(Bitmap bmp) { this.videoThumbnail = bmp; }
    // [ADD] 타임스탬프 게터/세터
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
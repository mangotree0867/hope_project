package com.android.example.myapplication;

public class ChatSession {
    private int id;
    private String created_at;
    private int message_count;
    private String address;
    private String session_title;
    private String location;

    public ChatSession(int id, String created_at, int message_count) {
        this.id = id;
        this.created_at = created_at;
        this.message_count = message_count;
    }
    public ChatSession(int id, String createdAt, int messageCount, String address, String sessionTitle) {
        this.id = id;
        this.created_at = createdAt;
        this.message_count = messageCount;
        this.address = address;
        this.session_title = sessionTitle;
    }

    public int getId() { return id; }
    public String getCreatedAt() { return created_at; }
    public int getMessageCount() { return message_count; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getTitle() { return session_title; }
    public void setTitle(String title) { this.session_title = title; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
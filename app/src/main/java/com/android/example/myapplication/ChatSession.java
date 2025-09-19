package com.android.example.myapplication;

public class ChatSession {
    private int id;
    private String created_at;
    private int message_count;
    private String address;

    public ChatSession(int id, String created_at, int message_count) {
        this.id = id;
        this.created_at = created_at;
        this.message_count = message_count;
    }
    public ChatSession(int id, String createdAt, int messageCount, String address) {
        this.id = id;
        this.created_at = createdAt;
        this.message_count = messageCount;
        this.address = address;
    }

    public int getId() { return id; }
    public String getCreatedAt() { return created_at; }
    // [ADD]
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}
package com.android.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ChatListActivity extends AppCompatActivity {

    private ChatSessionAdapter adapter;
    private String authToken = "";
    private static final String BASE_URL = AppConfig.BASE_URL;
    private volatile boolean isLoadingSessions = false;
    private LinearLayout emptyState;
    private RecyclerView rvSessions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);

        // 좌측 상단 사용자명 표시
        TextView tvUser = findViewById(R.id.tv_user_name_list);
        if (tvUser != null) {
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
                display = "게스트 모드";
            }
            tvUser.setText("유저명: " + display);
        }

        authToken = getSharedPreferences("auth", MODE_PRIVATE).getString("token", "");

        // Initialize views
        rvSessions = findViewById(R.id.rv_sessions);
        emptyState = findViewById(R.id.empty_state);

        rvSessions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatSessionAdapter(
                /* context = */ this,
                /* onClick  = */ session -> {
            Intent intent = new Intent(ChatListActivity.this, ChatActivity.class);
            intent.putExtra("isGuest", false);
            intent.putExtra("sessionId", session.getId());
            startActivity(intent);
        }
        );
        rvSessions.setAdapter(adapter);

        // Remove divider decoration for cleaner look
        // DividerItemDecoration divider = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        // divider.setDrawable(ContextCompat.getDrawable(this, R.drawable.divider_line));
        // rv.addItemDecoration(divider);

        Button btnNew = findViewById(R.id.btn_new_chat);
        btnNew.setOnClickListener(v -> {
            // 새 세션 시작
            Intent intent = new Intent(ChatListActivity.this, ChatActivity.class);
            intent.putExtra("isGuest", false);
            startActivity(intent);
        });

        ImageButton btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> {
            // Navigate to settings page
            Intent intent = new Intent(ChatListActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        authToken = getSharedPreferences("auth", MODE_PRIVATE).getString("token", "");
        loadSessions();
    }

    private void loadSessions() {
        if (isLoadingSessions) return;
        isLoadingSessions = true;

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BASE_URL + "/chat-sessions");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + authToken);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                        String line; while ((line = br.readLine()) != null) sb.append(line);
                    }
                    org.json.JSONObject res = new org.json.JSONObject(sb.toString());
                    org.json.JSONArray arr = res.getJSONArray("sessions");

                    List<ChatSession> list = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject s = arr.getJSONObject(i);
                        int id = s.getInt("id");
                        String created = s.optString("created_at", "");
                        int count = s.optInt("message_count", 0);
                        ChatSession session = new ChatSession(id, created, count);
                        String savedAddr = getSharedPreferences("session_meta", MODE_PRIVATE)
                                .getString("addr_" + id, "");
                        session.setAddress(savedAddr);
                        list.add(session);
                    }
                    runOnUiThread(() -> {
                        adapter.setSessions(list);
                        updateEmptyState(list.isEmpty());
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "채팅 목록 실패(" + code + ")", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "오류: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                if (conn != null) conn.disconnect();
                isLoadingSessions = false;
            }
        }).start();
    }

    private void updateEmptyState(boolean isEmpty) {
        if (isEmpty) {
            emptyState.setVisibility(View.VISIBLE);
            rvSessions.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvSessions.setVisibility(View.VISIBLE);
        }
    }
}

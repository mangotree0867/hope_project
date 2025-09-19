package com.android.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login); // UI만 구성, 동작은 추후 API 연결 시 구현

        // [ADD] 뷰 참조
        EditText etId = findViewById(R.id.et_id);
        EditText etPw = findViewById(R.id.et_password);
        Button btnLoginConfirm = findViewById(R.id.btn_login_confirm);
        Button btnSignup = findViewById(R.id.btn_signup);
        TextView tvError = findViewById(R.id.tv_error);

        // [ADD] 서버 베이스 URL (AppConfig에서 중앙 관리)
        final String BASE_URL = AppConfig.BASE_URL;

        // [ADD] 로그인 버튼 클릭
        btnLoginConfirm.setOnClickListener(v -> {
            tvError.setVisibility(View.GONE);
            String id = etId.getText().toString().trim();
            String pw = etPw.getText().toString().trim();

            if (id.isEmpty() || pw.isEmpty()) {
                tvError.setText("아이디와 비밀번호를 모두 입력하세요.");
                tvError.setVisibility(View.VISIBLE);
                return;
            }
            // 네트워크는 메인 스레드에서 금지 → 백그라운드 쓰레드 사용
            new Thread(() -> {
                try {
                    URL url = new URL(BASE_URL + "/auth/login");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setDoOutput(true);

                    // Body 작성
                    org.json.JSONObject body = new org.json.JSONObject();
                    body.put("login_id", id);
                    body.put("password", pw);

                    byte[] out = body.toString().getBytes("UTF-8");
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(out);
                    }

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        // 성공 응답 파싱
                        StringBuilder sb = new StringBuilder();
                        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line);
                        }
                        org.json.JSONObject res = new org.json.JSONObject(sb.toString());
                        String token = res.getString("access_token");

                        // [ADD] 로그인 응답의 user 정보 저장 (이름/로그인ID)
                        String userName = "";
                        String loginId = "";
                        org.json.JSONObject userObj = res.optJSONObject("user");
                        if (userObj != null) {
                            userName = userObj.optString("name", "");
                            loginId  = userObj.optString("login_id", "");
                        }

                        getSharedPreferences("auth", MODE_PRIVATE)
                                .edit()
                                .putString("token", token)
                                .putString("user_name", userName)   // ← 메인/채팅 화면에서 사용
                                .putString("login_id", loginId)
                                .apply();

                        // 토큰 저장
                        getSharedPreferences("auth", MODE_PRIVATE)
                                .edit()
                                .putString("token", token)
                                .apply();

                        runOnUiThread(() -> {
                            Toast.makeText(this, "로그인 성공", Toast.LENGTH_SHORT).show();
                            // 메인으로 복귀 (MainActivity.onResume에서 UI가 로그인 상태로 바뀜)
                            finish();
                        });
                    } else {
                        runOnUiThread(() -> {
                            tvError.setText("아이디 또는 비밀번호가 올바르지 않습니다.");
                            tvError.setVisibility(View.VISIBLE);
                        });
                    }
                    conn.disconnect();
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        tvError.setText("로그인 중 오류가 발생했습니다: " + e.getMessage());
                        tvError.setVisibility(View.VISIBLE);
                    });
                }
            }).start();
        });

// [ADD] 회원가입 화면으로 이동 (추후 실제 API 연결 예정)
        btnSignup.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(intent);
        });
    }
}
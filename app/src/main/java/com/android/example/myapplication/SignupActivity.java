package com.android.example.myapplication;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class SignupActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // [ADD] 뷰 참조
        EditText etId   = findViewById(R.id.et_signup_id);
        EditText etPw   = findViewById(R.id.et_signup_pw);
        EditText etName = findViewById(R.id.et_signup_name);
        EditText etEmail= findViewById(R.id.et_signup_email);
        Button btnDoSignup = findViewById(R.id.btn_do_signup);
        TextView tvError = findViewById(R.id.tv_signup_error);

        // [ADD] 서버 베이스 URL (AppConfig에서 중앙 관리)
        final String BASE_URL = AppConfig.BASE_URL;

        btnDoSignup.setOnClickListener(v -> {
            tvError.setVisibility(View.GONE);

            String loginId = etId.getText().toString().trim();
            String password= etPw.getText().toString().trim();
            String name    = etName.getText().toString().trim();
            String email   = etEmail.getText().toString().trim();

            // [검증] 필수값 체크
            if (loginId.isEmpty() || password.isEmpty() || name.isEmpty() || email.isEmpty()) {
                tvError.setText("모든 항목을 입력하세요.");
                tvError.setVisibility(View.VISIBLE);
                return;
            }
            // [검증] 이메일 형식
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tvError.setText("이메일 형식이 올바르지 않습니다.");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            // 네트워크는 백그라운드에서
            new Thread(() -> {
                HttpURLConnection conn = null;
                try {
                    java.net.URL url = new java.net.URL(BASE_URL + "/auth/register");
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setDoOutput(true);

                    // JSON Body
                    org.json.JSONObject body = new org.json.JSONObject();
                    body.put("name", name);
                    body.put("login_id", loginId);
                    body.put("password", password);
                    body.put("email", email);

                    byte[] out = body.toString().getBytes("UTF-8");
                    try (java.io.OutputStream os = conn.getOutputStream()) {
                        os.write(out);
                    }

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        // 성공 → 로그인 화면으로 복귀
                        runOnUiThread(() -> {
                            android.widget.Toast.makeText(this, "회원가입 완료! 로그인해 주세요.", android.widget.Toast.LENGTH_SHORT).show();
                            finish(); // LoginActivity로 돌아감
                        });
                    } else {
                        // 오류 본문 읽기
                        StringBuilder sb = new StringBuilder();
                        java.io.InputStream es = conn.getErrorStream();
                        if (es != null) {
                            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(es, "UTF-8"))) {
                                String line; while ((line = br.readLine()) != null) sb.append(line);
                            }
                        }
                        String msg = "회원가입 실패(" + code + ")";
                        try {
                            if (sb.length() > 0) {
                                org.json.JSONObject err = new org.json.JSONObject(sb.toString());
                                String detail = err.optString("detail", "");
                                if (!detail.isEmpty()) msg += ": " + detail;
                            }
                        } catch (org.json.JSONException ignored) {}
                        String finalMsg = msg;
                        runOnUiThread(() -> {
                            tvError.setText(finalMsg);
                            tvError.setVisibility(View.VISIBLE);
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        tvError.setText("회원가입 중 오류: " + e.getMessage());
                        tvError.setVisibility(View.VISIBLE);
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }).start();
        });
        // TODO: 추후 /auth/register 연결 후 성공 시 finish() 호출하여 LoginActivity로 복귀
    }
}

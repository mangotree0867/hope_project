package com.android.example.myapplication;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private EditText etEmergencyNumber;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        preferences = getSharedPreferences("app_settings", MODE_PRIVATE);

        // Initialize views
        etEmergencyNumber = findViewById(R.id.et_emergency_number);
        ImageButton btnBack = findViewById(R.id.btn_back);
        Button btnSaveEmergency = findViewById(R.id.btn_save_emergency);
        Button btnLogout = findViewById(R.id.btn_logout);

        // Load current settings
        loadSettings();

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Save emergency number
        btnSaveEmergency.setOnClickListener(v -> saveEmergencyNumber());

        // Logout button
        btnLogout.setOnClickListener(v -> logout());
    }

    private void loadSettings() {
        // Load emergency number (default to "119" if not set)
        String emergencyNumber = preferences.getString("emergency_number", "119");
        etEmergencyNumber.setText(emergencyNumber);
    }

    private void saveEmergencyNumber() {
        String emergencyNumber = etEmergencyNumber.getText().toString().trim();

        if (emergencyNumber.isEmpty()) {
            Toast.makeText(this, "긴급 연락처를 입력해주세요", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate phone number format (basic validation)
        if (!emergencyNumber.matches("^[0-9-+()\\s]+$")) {
            Toast.makeText(this, "올바른 전화번호 형식이 아닙니다", Toast.LENGTH_SHORT).show();
            return;
        }

        // Save to SharedPreferences
        preferences.edit()
                .putString("emergency_number", emergencyNumber)
                .apply();

        Toast.makeText(this, "긴급 연락처가 저장되었습니다: " + emergencyNumber, Toast.LENGTH_SHORT).show();
    }

    private void logout() {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setTitle("로그아웃")
                .setMessage("정말 로그아웃하시겠습니까?")
                .setPositiveButton("로그아웃", (dialog, which) -> {
                    // Clear auth token
                    getSharedPreferences("auth", MODE_PRIVATE)
                            .edit()
                            .clear()
                            .apply();

                    Toast.makeText(this, "로그아웃되었습니다", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("취소", (dialog, which) -> dialog.dismiss())
                .show();
    }

    // Static method to get emergency number from any activity
    public static String getEmergencyNumber(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences("app_settings", MODE_PRIVATE);
        return prefs.getString("emergency_number", "119"); // Default to "119"
    }
}
package com.android.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

public class SplashActivity extends AppCompatActivity {

    private volatile boolean isShowing = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // (옵션) 스플래시를 아주 살짝만 보이게 하고 싶으면 300~600ms 정도 유지
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isShowing = false;
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        }, 800); // ← 필요 없으면 0으로 두거나 이 라인을 바로 호출해도 됨.
    }
}
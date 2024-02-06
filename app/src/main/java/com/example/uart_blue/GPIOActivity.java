package com.example.uart_blue;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class GPIOActivity extends AppCompatActivity {
    private TextView textViewGpio138;
    private TextView textViewGpio139;
    private TextView textViewGpio28;
    private GpioControl gpioControl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpio);

        textViewGpio138 = findViewById(R.id.textViewGpio138);
        textViewGpio139 = findViewById(R.id.textViewGpio139);
        textViewGpio28 = findViewById(R.id.textViewGpio28);

        gpioControl = new GpioControl();
        gpioControl.initializeGpioPinsjava(); // GPIO 핀 초기화

        // 정기적으로 GPIO 상태를 확인하고 UI 업데이트
        final Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                updateGpioStatus();
                handler.postDelayed(this, 1000); // 1초마다 반복
            }
        };
        handler.post(runnable);
    }

    private void updateGpioStatus() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String status138 = gpioControl.getPinStatus(138);
                String status139 = gpioControl.getPinStatus(139);
                String status28 = gpioControl.getPinStatus(28);

                textViewGpio138.setText("GPIO 138 상태: " + status138);
                textViewGpio139.setText("GPIO 139 상태: " + status139);
                textViewGpio28.setText("GPIO 28 상태: " + status28);
            }
        });
    }
}

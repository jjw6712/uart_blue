package com.example.uart_blue;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class GPIOActivity extends AppCompatActivity {

    private TextView textViewGpio138;
    private TextView textViewGpio139;
    private TextView textViewGpio28;
    private GpioControl gpioControl;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpio);

        textViewGpio138 = findViewById(R.id.textViewGpio138);
        textViewGpio139 = findViewById(R.id.textViewGpio139);
        textViewGpio28 = findViewById(R.id.textViewGpio28);

        gpioControl = new GpioControl();

        // GPIO 상태를 읽어와서 TextView를 업데이트합니다.
        updateGpioStatus();
    }

    private void updateGpioStatus() {
        String status138 = gpioControl.getPinStatus(138);
        String status139 = gpioControl.getPinStatus(139);
        String status28 = gpioControl.getPinStatus(28);

        textViewGpio138.setText("GPIO 138 상태: " + status138);
        textViewGpio139.setText("GPIO 139 상태: " + status139);
        textViewGpio28.setText("GPIO 28 상태: " + status28);
    }
}


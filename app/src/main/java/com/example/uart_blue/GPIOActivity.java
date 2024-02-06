package com.example.uart_blue;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class GPIOActivity {
    private GpioControl gpioControl;
    private ReadThread readThread;
    private boolean isGpioInputEnabled = false;
    private final Handler handler = new Handler();
    private OptionActivity optionActivity;

    // 생성자에서 OptionActivity 인스턴스를 받습니다.
    public GPIOActivity(OptionActivity optionActivity) {
        this.optionActivity = optionActivity;
        gpioControl = new GpioControl();
        gpioControl.initializeGpioPinsjava();
        startGpioStatusUpdate();
    }

    // GPIO 입력 활성화
    public void enableGpioInput() {
        isGpioInputEnabled = true;
    }

    // GPIO 입력 비활성화
    public void disableGpioInput() {
        isGpioInputEnabled = false;
    }

    private void startGpioStatusUpdate() {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (isGpioInputEnabled) {
                    updateGpioStatus();
                }
                handler.postDelayed(this, 1000); // 1초마다 반복
            }
        };
        handler.post(runnable);
    }

    private void updateGpioStatus() {
        boolean is138Active = gpioControl.isGpioActive(138);
        boolean is139Active = gpioControl.isGpioActive(139);

        if (is138Active) {
            // 시리얼 포트를 통해 '1' 데이터를 보냅니다.
            optionActivity.startReadingData();
            if (readThread != null) {
                readThread.sendDataToSerialPort(new byte[] { '1' });
            }
        }

        if (is139Active) {
            // 시리얼 포트를 통해 '0' 데이터를 보냅니다.
            if (readThread != null) {
                readThread.sendDataToSerialPort(new byte[]{'0'});
                // 시리얼 포트를 그대로 두고, 스레드를 정지합니다.
                readThread.stopThreads();
            }
        }
    }

}

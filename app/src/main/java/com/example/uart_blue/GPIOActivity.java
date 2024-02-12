package com.example.uart_blue;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

public class GPIOActivity extends Thread{
    private GpioControl gpioControl;
    private boolean isGpioInputEnabled = false;
    private final Handler handler = new Handler();
    private OptionActivity optionActivity;
    private Runnable updateGpioStatusRunnable; // Runnable 객체를 멤버 변수로 선언
    Context context;

    // 생성자에서 OptionActivity 인스턴스를 받습니다.
    public GPIOActivity(OptionActivity optionActivity, Context context)  {
        this.context = context;
        this.optionActivity = optionActivity;
        gpioControl = new GpioControl();
        gpioControl.initializeGpioPinsjava();
        // Runnable 객체 초기화
        updateGpioStatusRunnable = new Runnable() {
            @Override
            public void run() {
                if (isGpioInputEnabled) {
                    updateGpioStatus();
                    handler.postDelayed(this, 100); // 1초마다 반복
                }
            }
        };
    }

    // GPIO 입력 활성화
    public void enableGpioInput() {
        if (!isGpioInputEnabled) {
            isGpioInputEnabled = true;
            Log.d(TAG, "스위치 테스트 활성화: ");
            gpioControl.setGpioReadEnabled(isGpioInputEnabled);
            handler.post(updateGpioStatusRunnable); // 반복 작업 시작
        }
    }

    public void disableGpioInput() {
        if (isGpioInputEnabled) {
            isGpioInputEnabled = false;
            Log.d(TAG, "스위치 테스트 비활성화: ");
            handler.removeCallbacks(updateGpioStatusRunnable); // 반복 작업 정지
            gpioControl.setGpioReadDisabled(isGpioInputEnabled);

        }
    }

    private void updateGpioStatus() {
        // GPIO 입력이 활성화되어 있지 않다면 함수를 빠져나갑니다.
        if (!isGpioInputEnabled) {
            return;
        }

        boolean is138Active = gpioControl.isGpioActive(138);
        boolean is139Active = gpioControl.isGpioActive(139);
        boolean is28Active = gpioControl.isGpioActive(28);

        if (is138Active && isGpioInputEnabled) {
            // OptionActivity를 통해 ReadThread 시작
            optionActivity.startReadingDataFromGPIO();
            // 시리얼 포트를 통해 '1' 데이터를 보냅니다.
            optionActivity.sendDataToSerialPort(new byte[] {'1'});
            Intent intent = new Intent("com.example.uart_blue.ACTION_UPDATE_UI");
            intent.putExtra("GPIO_138_ACTIVE", true);
            context.sendBroadcast(intent);
        }

        if (is139Active && isGpioInputEnabled) {
            // 시리얼 포트를 통해 '0' 데이터를 보냅니다.
            optionActivity.sendDataToSerialPort(new byte[]{'0'});
            // ReadThread 정지
            optionActivity.stopReadThread();
            Intent intent = new Intent("com.example.uart_blue.ACTION_UPDATE_UI");
            intent.putExtra("GPIO_139_ACTIVE", true);
            context.sendBroadcast(intent);
        }

        if (is28Active && isGpioInputEnabled) {
            // ReadThread 정지
            //optionActivity.stopReadThread();
            Intent intent = new Intent("com.example.uart_blue.ACTION_UPDATE_UI");
            intent.putExtra("GPIO_28_ACTIVE", true);
            context.sendBroadcast(intent);
        }
    }


}

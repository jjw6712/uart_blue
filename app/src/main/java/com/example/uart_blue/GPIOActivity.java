package com.example.uart_blue;


import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.uart_blue.FileManager.FileManager;
import com.example.uart_blue.FileManager.FileProcessingTask;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GPIOActivity {
    private GpioControl gpioControl;
    private boolean isGpioInputEnabled = false;
    private final Handler handler = new Handler();
    private OptionActivity optionActivity;
    private Runnable updateGpioStatusRunnable; // Runnable 객체를 멤버 변수로 선언
    Context context;
    private boolean is138ActivePrev = false;
    private boolean is139ActivePrev = false;
    private boolean is28ActivePrev = false;
    ReadThread readThread;

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

        // GPIO 138 핀의 상태 변화 감지 및 처리
        if (is138Active && !is138ActivePrev && isGpioInputEnabled) {
            // 스위치가 눌렸다가 떼어진 경우의 로직을 여기에 작성
            //optionActivity.startReadingDataFromGPIO();
            handleGpio138Active();
        }
        is138ActivePrev = is138Active; // 이전 상태 업데이트

        // GPIO 139 핀의 상태 변화 감지 및 처리
        if (is139Active && !is139ActivePrev && isGpioInputEnabled) {
            // 스위치가 눌렸다가 떼어진 경우의 로직을 여기에 작성
            handleGpio139Active();
        }
        is139ActivePrev = is139Active; // 이전 상태 업데이트

        // GPIO 28 핀의 상태 변화 감지 및 처리
        if (is28Active && !is28ActivePrev && isGpioInputEnabled) {
            // 스위치가 눌렸다가 떼어진 경우의 로직을 여기에 작성
            handleGpio28Active();
        }
        is28ActivePrev = is28Active; // 이전 상태 업데이트
    }

    private void handleGpio138Active() {
        // GPIO 138 핀 활성화 시 실행할 로직
        // SerialService를 통해 ReadThread 시작
        Intent serviceIntent = new Intent(context, SerialService.class);
        context.startService(serviceIntent);
    }

    private void handleGpio139Active() {
        // GPIO 139 핀 활성화 시 실행할 로직
        Intent serviceIntent = new Intent(context, SerialService.class);
        context.stopService(serviceIntent); // 서비스 종료
    }

    private void handleGpio28Active() {
        // GPIO 28 핀 활성화 시 실행할 로직
       readThread.ZipFIleSaved();
    }

    }

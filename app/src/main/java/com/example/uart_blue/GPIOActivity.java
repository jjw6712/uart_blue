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
        // OptionActivity를 통해 ReadThread 시작
        optionActivity.startReadingDataFromGPIO();
        if (readThread != null) {
            // 시작 신호: STX = 0x02, CMD = 0x10, ETX = 0x03
            byte[] startSignal = {0x02, 0x10, 0x03};
            // 체크섬 계산
            byte checksum = optionActivity.calculateChecksum(startSignal);
            // 체크섬을 포함하여 전송할 데이터 생성
            byte[] dataToSend = new byte[startSignal.length + 1];
            System.arraycopy(startSignal, 0, dataToSend, 0, startSignal.length);
            dataToSend[dataToSend.length - 1] = checksum;

            // 준비된 데이터를 시리얼 포트를 통해 전송
            optionActivity.sendDataToSerialPort(dataToSend);
        }

        Intent intent = new Intent("com.example.uart_blue.ACTION_UPDATE_UI");
        intent.putExtra("GPIO_138_ACTIVE", true);
        context.sendBroadcast(intent);
    }

    private void handleGpio139Active() {
        // GPIO 139 핀 활성화 시 실행할 로직
        if (readThread != null) {
            // 정지 신호: STX = 0x02, CMD = 0x10, ETX = 0x03
            byte[] startSignal = {0x02, 0x20, 0x03};
            // 체크섬 계산
            byte checksum = optionActivity.calculateChecksum(startSignal);
            // 체크섬을 포함하여 전송할 데이터 생성
            byte[] dataToSend = new byte[startSignal.length + 1];
            System.arraycopy(startSignal, 0, dataToSend, 0, startSignal.length);
            dataToSend[dataToSend.length - 1] = checksum;

            // 준비된 데이터를 시리얼 포트를 통해 전송
            optionActivity.sendDataToSerialPort(dataToSend);
            // ReadThread 정지
            optionActivity.stopReadThread();
        }
        Intent intent = new Intent("com.example.uart_blue.ACTION_UPDATE_UI");
        intent.putExtra("GPIO_139_ACTIVE", true);
        context.sendBroadcast(intent);
    }

    private void handleGpio28Active() {
        // GPIO 28 핀 활성화 시 실행할 로직
        Log.d(TAG, "GPIO 수격신호 수신");
        Intent intent = new Intent("com.example.uart_blue.ACTION_UPDATE_UI");
        intent.putExtra("GPIO_28_ACTIVE", true);
        context.sendBroadcast(intent);
        Date eventTime = new Date(); // 현재 시간을 수격이 발생한 시간으로 가정
        SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        String directoryUriString = sharedPreferences.getString("directoryUri", "");
        long beforeMillis = sharedPreferences.getLong("beforeMillis", 0);
        long afterMillis = sharedPreferences.getLong("afterMillis", 0);

        // 이벤트 발생 후 대기할 시간 계산
        long delay = afterMillis;
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Uri directoryUri = Uri.parse(directoryUriString);
                List<Uri> filesInRange = FileManager.findFilesInRange(context, directoryUri, eventTime, beforeMillis, afterMillis);

                if (!filesInRange.isEmpty()) {
                    // eventTime을 기반으로 파일 이름 생성
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.getDefault());
                    String eventDateTime = sdf.format(eventTime);
                    String combinedFileName = eventDateTime + ".txt";
                    Uri combinedFileUri = FileManager.combineTextFilesInRange(context, filesInRange, combinedFileName, eventTime, beforeMillis, afterMillis);

                    if (combinedFileUri != null) {
                        String zipFileName = eventDateTime + ".zip";
                        Uri outputZipUri = FileManager.createOutputZipUri(context, directoryUri, zipFileName);
                        FileManager.compressFile(context, combinedFileUri, outputZipUri);
                        Log.d(TAG, "파일이 성공적으로 압축되었습니다.");
                    }
                } else {
                    Log.d(TAG, "지정된 시간 범위 내에서 일치하는 파일이 없습니다.");
                }
            }, delay);
    }

    }

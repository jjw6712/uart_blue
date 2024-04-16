package com.example.uart_blue;


import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

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
    private Handler zipFileSavedHandler = new Handler(Looper.getMainLooper());
    private Runnable zipFileSavedRunnable;


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
                    handler.postDelayed(this, 0); // 1초마다 반복
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
        if (!is138Active && is138ActivePrev && isGpioInputEnabled) {
            // 스위치가 눌렸다가 떼어진 경우의 로직을 여기에 작성
            //optionActivity.startReadingDataFromGPIO();
            handleGpio138Active();
        }
        is138ActivePrev = is138Active; // 이전 상태 업데이트

        // GPIO 139 핀의 상태 변화 감지 및 처리
        if (!is139Active && is139ActivePrev && isGpioInputEnabled) {
            // 스위치가 눌렸다가 떼어진 경우의 로직을 여기에 작성
            handleGpio139Active();
        }
        is139ActivePrev = is139Active; // 이전 상태 업데이트

        // GPIO 28 핀의 상태 변화 감지 및 처리
        if (!is28Active && is28ActivePrev && isGpioInputEnabled) {
            // 스위치가 눌렸다가 떼어진 경우의 로직을 여기에 작성
            handleGpio28Active();
        }
        is28ActivePrev = is28Active; // 이전 상태 업데이트
    }

    private void handleGpio138Active() {
        // GPIO 138 핀 활성화 시 실행할 로직
        // SerialService를 통해 ReadThread 시작
        SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        boolean isGpio138Active = sharedPreferences.getBoolean("GPIO_138_ACTIVE", false);
        boolean isGpio139Active = sharedPreferences.getBoolean("GPIO_139_ACTIVE", false);
        if(isGpio138Active && !isGpio139Active){
            Toast.makeText(context, "운전 중에는 운전버튼이 비활성화 됩니다.", Toast.LENGTH_LONG).show();
        }else {
            Intent serviceIntent = new Intent(context, SerialService.class);
            context.startService(serviceIntent); // 서비스 시작
        }

    }

    private void handleGpio139Active() {
        // GPIO 139 핀 활성화 시 실행할 로직
        Intent serviceIntent = new Intent(context, SerialService.class);
        context.stopService(serviceIntent); // 서비스 종료

    }

    private void handleGpio28Active() {
        // GPIO 28 핀 활성화 시 실행할 로직
        SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        boolean isGpio138Active = sharedPreferences.getBoolean("GPIO_138_ACTIVE", false);
        if(!isGpio138Active){
            Toast.makeText(context, "정지 상태에서는 수격 테스트가 불가능합니다.", Toast.LENGTH_LONG).show();
        }else {
            ZipFileSaved();
        }
    }
    public void ZipFileSaved() {
        Log.d(TAG, "수격신호 수신");
        saveWHState(true);
        Date eventTime = new Date(); // 현재 시간을 수격이 발생한 시간으로 가정
        SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        String directoryUriString = sharedPreferences.getString("directoryUri", "");
        String deviceNumber = sharedPreferences.getString("deviceNumber", "");
        int whcount = sharedPreferences.getInt("whcountui", 0);
        String selectedSensorType = sharedPreferences.getString("SelectedSensorType", "");
        String sensor = "";
        if (selectedSensorType.equals("5kg")){
            sensor = "1";
        } else if (selectedSensorType.equals("20kg")) {
            sensor = "2";
        }
        else if (selectedSensorType.equals("30kg")) {
            sensor = "3";
        }else {
            sensor = "4";
        }
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("whcountui", whcount + 1);
        editor.apply();
        long beforeMillis = 1000 * 10;
        long afterMillis = 1000 * 60;
        // 이벤트 발생 후 대기할 시간 계산
        long delay = afterMillis;

        // Define the task to be run
        String finalSensor = sensor;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Uri directoryUri = Uri.parse(directoryUriString);
            List<Uri> filesInRange = FileManager.findFilesInRange(context, directoryUri, eventTime, beforeMillis, afterMillis);

            if (!filesInRange.isEmpty()) {
                // eventTime을 기반으로 파일 이름 생성
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.getDefault());
                String eventDateTime = sdf.format(eventTime);
                String combinedFileName = deviceNumber+"-"+ finalSensor +"-"+eventDateTime + ".txt";
                Uri combinedFileUri = FileManager.combineTextFilesInRange(context, filesInRange, combinedFileName, eventTime, beforeMillis, afterMillis);

                if (combinedFileUri != null) {
                    List<Uri> fileUris = FileManager.findFilesInRange(context, directoryUri, eventTime, beforeMillis, afterMillis);
                    new FileProcessingTask(context, fileUris, combinedFileName, eventTime, beforeMillis, afterMillis, directoryUri).execute();

                }
            } else {
                Log.d(TAG, "지정된 시간 범위 내에서 일치하는 파일이 없습니다.");
            }
            saveWHState(false);
        }, delay);
    }
    public void saveWHState(boolean is28Active) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("GPIO_28_ACTIVE", is28Active);
        editor.apply();
    }
    }

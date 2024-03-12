package com.example.uart_blue;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.uart_blue.FileManager.OldFilesDeletionWorker;
import com.example.uart_blue.FileManager.ZipFilesDeletionWorker;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class SerialService extends Service {
    private ReadThread readThread;
    private static final String CHANNEL_ID = "ForegroundServiceChannel";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundService();
        startSerialCommunication();
        return START_STICKY;
    }

    private void startForegroundService() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // For Android 12 and newer, specify the PendingIntent mutability
            pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            // For older versions, FLAG_UPDATE_CURRENT is sufficient
            pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("UART Logging")
                .setContentText("Logging is running in the background")
                .setSmallIcon(R.drawable.ic_notification) // Make sure to replace with your own drawable resource
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Serial Service";
            String description = "Serial Port Communication Service";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }


    private void startSerialCommunication() {
        // readThread를 시작하는 로직...
        // ReadThread와 FileSaveThread를 다시 시작합니다.
        startReadingData();
        // 데이터를 송신합니다.
        if (readThread != null) {
            // 시작 신호: STX = 0x02, CMD = 0x10, ETX = 0x03
            byte[] startSignal = {0x02, 0x10, 0x03};
            // 체크섬 계산
            byte checksum = calculateChecksum(startSignal);
            // 체크섬을 포함하여 전송할 데이터 생성
            byte[] dataToSend = new byte[startSignal.length + 1];
            System.arraycopy(startSignal, 0, dataToSend, 0, startSignal.length);
            dataToSend[dataToSend.length - 1] = checksum;
            // 데이터 전송
            readThread.sendDataToSerialPort(dataToSend);
        }
        saveGPIOState(true, false);
        scheduleFileDeletionTasks();
    }

    // readThread를 중지하는 메서드
    public void stopSerialCommunication() {
        if (readThread != null) {
            // 시작 신호: STX = 0x02, CMD = 0x10, ETX = 0x03
            byte[] startSignal = {0x02, 0x20, 0x03};
            // 체크섬 계산
            byte checksum = calculateChecksum(startSignal);
            // 체크섬을 포함하여 전송할 데이터 생성
            byte[] dataToSend = new byte[startSignal.length + 1];
            System.arraycopy(startSignal, 0, dataToSend, 0, startSignal.length);
            dataToSend[dataToSend.length - 1] = checksum;
            // 데이터 전송
            readThread.sendDataToSerialPort(dataToSend);
            // 시리얼 포트를 그대로 두고, 스레드를 정지합니다.
            readThread.stopThreads();
        }
        // 앱에서 스케줄된 모든 작업을 취소
        WorkManager.getInstance(getApplicationContext()).cancelAllWork();
        /*if(readThread != null){
            readThread = null;
        }*/
    }
    public void startReadingData() {
        initializeSerialPort();
        String portPath = "/dev/ttyS0"; // 예시 경로
        int baudRate = 115200;
        readThread = new ReadThread(portPath, baudRate, new ReadThread.IDataReceiver() {
            @Override
            public void onReceiveData(String data) {
            }

            @Override
            public void onError(Exception e) {

            }
        }, this);
        readThread.start();
    }

    public void initializeSerialPort() {
        try {
            // Execute superuser command to set SELinux to permissive mode
            Process su = Runtime.getRuntime().exec("su");
            DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());

            // Change permissions of the export file to read and write
            outputStream.writeBytes("chmod 666 /dev/ttyS0\n");

            // Flush and close the output stream
            outputStream.flush();
            outputStream.close();

            // Wait for the command to complete
            su.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new SecurityException("Failed to obtain superuser permissions or change file permissions", e);
        }

    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSerialCommunication(); // 서비스가 종료될 때 시리얼 통신 중지
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    public byte calculateChecksum(byte[] data) {
        byte checksum = 0;
        for (byte b : data) {
            checksum ^= b; // XOR 연산
        }
        return checksum;
    }
    private void saveGPIOState(boolean is138Active, boolean is139Active) {
        SharedPreferences sharedPreferences = this.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("GPIO_138_ACTIVE", is138Active);
        editor.putBoolean("GPIO_139_ACTIVE", is139Active);
        editor.apply();
    }
    public void scheduleFileDeletionTasks() {
        PeriodicWorkRequest oldFilesDeletionRequest = new PeriodicWorkRequest.Builder(OldFilesDeletionWorker.class, 15, TimeUnit.MINUTES)
                .addTag("deleteOldFiles")
                .build();
        WorkManager.getInstance(getApplicationContext()).enqueue(oldFilesDeletionRequest);
        SharedPreferences sharedPreferences = this.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        int days = sharedPreferences.getInt("intervalDays", 0);
        PeriodicWorkRequest zipFilesDeletionRequest = new PeriodicWorkRequest.Builder(ZipFilesDeletionWorker.class, days, TimeUnit.DAYS)
                .addTag("deleteZipFiles")
                .build();
        WorkManager.getInstance(getApplicationContext()).enqueue(zipFilesDeletionRequest);
    }
}

package com.example.uart_blue;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;

import com.example.uart_blue.Network.TcpClient;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class FileSaveThread extends Thread {
    private BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private Context context;
    private Uri directoryUri;
    private String deviceNumber;
    private boolean running = true;
    private static final String TAG = "FileSaveThread";


    public FileSaveThread(Context context) {
        this.context = context;
        this.directoryUri = getSavedDirectoryUri(context);
        this.deviceNumber = getDeviceNumber(context);
    }

    public void run() {
        while (running || (queue != null && !queue.isEmpty())) {
            try {
                String logEntries = queue.take();
                saveDataToFile(logEntries);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void addData(String data) {
        queue.add(data);
    }

    public void stopSaving() {
        running = false;
        if (queue != null) {
            queue.clear();
            queue = null;
        }
    }

        private Uri getSavedDirectoryUri (Context context){
            SharedPreferences prefs = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
            String uriString = prefs.getString("directoryUri", "");
            //Log.d(TAG, "getSavedDirectoryUri: "+uriString);
            return uriString != null ? Uri.parse(uriString) : null;
        }

        private String getDeviceNumber (Context context){
            SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
            deviceNumber = sharedPreferences.getString("deviceNumber", "");
            Log.d(TAG, "getDeviceNumber: " + deviceNumber);
            return deviceNumber;
        }

    private void saveDataToFile(String logEntries) {
        if (directoryUri == null) {
            Log.e(TAG, "No directory selected.");
            return;
        }

        try {
            DocumentFile pickedDir = DocumentFile.fromTreeUri(context, directoryUri);
            DocumentFile dataDirectory = pickedDir.findFile("W.H.Data");
            if (dataDirectory == null || !dataDirectory.exists()) {
                dataDirectory = pickedDir.createDirectory("W.H.Data");
            }

            String fileName = getFileName();
            // 동일한 이름의 파일이 이미 존재하는지 검사
            DocumentFile existingFile = dataDirectory.findFile(fileName);
            if (existingFile != null && existingFile.exists()) {
                // 파일이 이미 존재한다면 해당 파일 삭제
                boolean deleted = existingFile.delete();
                if (!deleted) {
                    Log.e(TAG, "기존파일 삭제 실패: " + fileName);
                    return;
                }
                Log.d(TAG, "기존 파일 삭제 완료: " + fileName);
            }

            DocumentFile newFile = dataDirectory.createFile("text/plain", fileName);
            try (OutputStream out = context.getContentResolver().openOutputStream(newFile.getUri())) {
                out.write(logEntries.getBytes());
                Log.d(TAG, "Saved data to " + fileName);
            } catch (IOException e) {
                Log.e(TAG, "Error writing to file: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error accessing directory: " + e.getMessage());
        }
    }


    private String getFileName () {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
            return deviceNumber + "-" + dateFormat.format(new Date()) + ".txt";
        }
    }


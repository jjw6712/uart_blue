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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public class FileSaveThread extends Thread {
    private BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private ExecutorService executorService = Executors.newFixedThreadPool(5); // 파일 저장 작업을 위한 스레드 풀
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
        while (running || !queue.isEmpty()) {
            try {
                String logEntries = queue.take();
                executorService.submit(() -> saveDataToFile(logEntries)); // 파일 저장 작업을 스레드 풀에 제출
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        executorService.shutdown(); // 스레드 풀 종료
    }

    public void addData(String data, String timestamp) {
        String entry = timestamp + "," + data;
        queue.add(entry);
    }

    public void stopSaving() {
        running = false;
    }

    private Uri getSavedDirectoryUri(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        String uriString = prefs.getString("directoryUri", "");
        return uriString != null ? Uri.parse(uriString) : null;
    }

    private String getDeviceNumber(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        return sharedPreferences.getString("deviceNumber", "");
    }

    private void saveDataToFile(String entry) {
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

            String timestamp = entry.substring(0, 24);
            String logEntries = entry.substring(25);
            String formattedTimestamp = timestamp.replace(", ", "-");
            String fileName = getFileNameWithTimestamp(formattedTimestamp);

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

    private String getFileNameWithTimestamp(String timestamp) {
        return deviceNumber + "-" + timestamp + ".txt";
    }
}


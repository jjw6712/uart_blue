package com.example.uart_blue;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android_serialport_api.SerialPort;

public class Uart extends AppCompatActivity {
    private static final String LOG_TAG = "Uart";
    private static final int REQUEST_DIRECTORY_PICKER = 101;
    private Uri directoryUri;  // 사용자가 선택한 디렉토리의 URI
    private static final int STORAGE_PERMISSION_CODE = 100;
    private static final String DEVICE_ID = "123456";

    protected SerialPort mSerialPort;
    protected OutputStream mOutputStream;
    private InputStream mInputStream;
    private ReadThread mReadThread;

    // Data buffer and counter
    private final byte[] dataBuffer = new byte[1000];
    private int dataCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_option);
        checkStoragePermission();
// 사용자에게 폴더를 선택하게 하여 'data' 폴더에 파일을 저장하도록 요청합니다.
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_DIRECTORY_PICKER);
        try {
            mSerialPort = getSerialPort("/dev/ttyS0", 115200);
            mOutputStream = mSerialPort.getOutputStream();
            mInputStream = mSerialPort.getInputStream();

            mReadThread = new ReadThread();
            mReadThread.start();
        } catch (SecurityException | InvalidParameterException e) {
            Log.e(LOG_TAG, e.getMessage());
        }

        setupButtons();
    }

    @Override
    protected void onDestroy() {
        if (mReadThread != null) {
            mReadThread.interrupt();
            closeSerialPort();
            mSerialPort = null;
        }
        super.onDestroy();
    }

    private void setupButtons() {
        Button sendButton1 = findViewById(R.id.buttonSend1);
        sendButton1.setOnClickListener(v -> sendToComputer('1'));

        Button sendButton0 = findViewById(R.id.buttonSend0);
        sendButton0.setOnClickListener(v -> sendToComputer('0'));
    }

    private void sendToComputer(char data) {
        byte[] sendData = new byte[1];
        sendData[0] = (byte) data;
        try {
            mOutputStream.write(sendData);
            Log.d(LOG_TAG, "Sent '" + data + "' to Computer");
        } catch (IOException ex) {
            Log.e(LOG_TAG, ex.getMessage());
        }
    }

    private class ReadThread extends Thread {
        @Override
        public void run() {
            while (!isInterrupted()) {
                try {
                    if (mInputStream == null) return;
                    int size = mInputStream.read(dataBuffer, dataCounter, dataBuffer.length - dataCounter);
                    if (size > 0) {
                        dataCounter += size;
                        if (dataCounter >= 1000) {
                            saveDataToFile(dataBuffer);
                            dataCounter = 0;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
    }

    private void saveDataToFile(byte[] data) {
        if (directoryUri == null) {
            Log.e(LOG_TAG, "No directory selected.");
            return;
        }

        try {
            // 'data' 폴더를 찾거나 생성합니다.
            DocumentFile pickedDir = DocumentFile.fromTreeUri(this, directoryUri);
            DocumentFile dataDirectory = pickedDir.findFile("data");
            if (dataDirectory == null || !dataDirectory.exists()) {
                // 'data' 폴더가 없으면 새로 생성합니다.
                dataDirectory = pickedDir.createDirectory("data");
            }

            // 파일 이름을 생성합니다.
            String fileName = getFileName();

            // 'data' 폴더 안에 파일을 생성합니다.
            DocumentFile newFile = dataDirectory.createFile("text/plain", fileName);
            try (OutputStream out = getContentResolver().openOutputStream(newFile.getUri())) {
                out.write(data);
                Log.d(LOG_TAG, "Saved data to " + fileName);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error writing to file: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error accessing directory: " + e.getMessage());
        }
    }

    // onActivityResult 메소드를 오버라이드하여 사용자가 폴더를 선택했을 때의 처리를 합니다.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_DIRECTORY_PICKER && resultCode == Activity.RESULT_OK) {
            directoryUri = data.getData();
            // 선택된 디렉토리에 대한 권한을 영구적으로 유지합니다.
            int takeFlags = data.getFlags();
            takeFlags &= (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(directoryUri, takeFlags);
        }
    }
    private String getFileName() {
        String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(new Date());
        return "DeviceID:" + DEVICE_ID + "-" + timeStamp + ".txt";
    }

    private SerialPort getSerialPort(String portNum, int baudRate) {
        try {
            if (mSerialPort == null) {
                mSerialPort = new SerialPort(new File(portNum), baudRate, 0);
            }
        } catch (Exception ex) {
            Log.e(LOG_TAG, ex.getMessage());
        }
        return mSerialPort;
    }

    private void closeSerialPort() {
        if (mSerialPort != null) {
            mSerialPort.close();
            mSerialPort = null;
        }
    }

    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(LOG_TAG, "Storage Permission Granted");
            } else {
                Log.e(LOG_TAG, "Storage Permission Denied");
            }
        }
    }
}

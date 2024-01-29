package com.example.uart_blue;

import static android.content.ContentValues.TAG;
import static androidx.core.content.PackageManagerCompat.LOG_TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import android_serialport_api.SerialPort;

public class OptionActivity extends AppCompatActivity {
    private static final String TAG = "OptionTag";
    private static final int STORAGE_PERMISSION_CODE = 100;
    private static final int REQUEST_DIRECTORY_PICKER = 101;
    private String selectedStorage = ""; //선택한 저장매체
    // PasswordManager 인스턴스 생성
    PasswordManager passwordManager = new PasswordManager(); //패스워드매니저 객체
    String deviceNumber; //디바이스 코드

    protected SerialPort mSerialPort;
    protected OutputStream mOutputStream;
    private InputStream mInputStream;
    public ReadThread mReadThread;
    public Uri directoryUri;  // 사용자가 선택한 디렉토리의 URI

    // Data buffer and counter
    private final byte[] dataBuffer = new byte[1000];
    private int dataCounter = 0;

    // 디바이스 번호 입력 필드 참조 (EditText 추가 필요)
    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_option);
        checkStoragePermission();

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

        @SuppressLint({"MissingInflatedId", "LocalSuppress"}) EditText deviceNumberInput = findViewById(R.id.DeviceEditText); // 레이아웃에 해당 ID를 가진 EditText 추가 필요
        // 컴포넌트 참조 초기화
        //EditText editTextDataInput = findViewById(R.id.editTextDataInput);
        Button buttonSave = findViewById(R.id.btsave);
        Button buttonCancel = findViewById(R.id.btcancel);
        Button buttonSelectDevice = findViewById(R.id.btselectDevice);

        Intent getIntent = getIntent();
        String storageInfo = getIntent.getStringExtra("storageInfo");
        Log.d(TAG, "MainActivity로부터 온 storageInfo: "+storageInfo);
        List<String> storageNames = parseStorageInfo(storageInfo);
        // 예외 처리: MainActivity로부터 전달된 저장매체 정보가 없는 경우
        if (storageInfo == null || storageInfo.isEmpty()) {
            selectedStorage = "";
        }

        // SharedPreferences에서 선택된 저장 매체 복원
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
        deviceNumber = sharedPreferences.getString("deviceNumber", "");
        // EditText에 저장된 디바이스 번호 설정
        deviceNumberInput.setText(deviceNumber);

        // 패스워드 버튼 클릭 리스너 설정
        findViewById(R.id.passwordButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 커스텀 레이아웃을 사용하여 다이얼로그 생성
                LayoutInflater inflater = LayoutInflater.from(OptionActivity.this);
                View dialogView = inflater.inflate(R.layout.password_dialog, null);
                final EditText passwordInput = dialogView.findViewById(R.id.passwordInput);
                AlertDialog dialog = new AlertDialog.Builder(OptionActivity.this)
                        .setTitle("Enter Password")
                        .setView(dialogView)
                        .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                String enteredPassword = passwordInput.getText().toString();
                                if (passwordManager.verifyPassword(enteredPassword)) {
                                    // 패스워드 일치, 디바이스 번호 저장
                                    String deviceNumber = deviceNumberInput.getText().toString();
                                    saveDeviceNumber(deviceNumber); // 디바이스 번호 저장 메소드 호출

                                    // 패스워드 일치, 변경 성공 메시지 다이얼로그 표시
                                    new AlertDialog.Builder(OptionActivity.this)
                                            .setTitle("Success")
                                            .setMessage("디바이스 ID가 "+deviceNumber+"로 변경되었습니다.")
                                            .setPositiveButton("OK", null)
                                            .show();
                                } else {
                                    // 패스워드 불일치, 오류 메시지 다이얼로그 표시
                                    new AlertDialog.Builder(OptionActivity.this)
                                            .setTitle("Error")
                                            .setMessage("올바른 비밀번호가 아닙니다")
                                            .setPositiveButton("OK", null)
                                            .show();
                                }
                            }
                        })
                        .setNegativeButton("취소", null)
                        .show();
            }
        });

        // 저장 버튼 클릭 리스너
        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("selectedStorage", selectedStorage);
                editor.apply();

                Intent intent = new Intent(OptionActivity.this, MainActivity.class);
                intent.putExtra("selectedStorage", extractStorageName(selectedStorage));
                startActivity(intent);
            }

            private String extractStorageName(String storageInfo) {
                if (storageInfo != null && storageInfo.contains(":")) {
                    return storageInfo.split(":")[0].trim();
                }
                return storageInfo;
            }
        });

        // 취소 버튼 클릭 리스너
        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 취소 로직, 입력 필드 초기화 등
                cancelSettings();
                finish();
            }
        });

        buttonSelectDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(intent, REQUEST_DIRECTORY_PICKER);
            }
        });
    }
    private void setupButtons() {
        Button sendButton1 = findViewById(R.id.buttonSend1);
        sendButton1.setOnClickListener(v -> sendToComputer('1'));

        Button sendButton0 = findViewById(R.id.buttonSend0);
        sendButton0.setOnClickListener(v -> sendToComputer('0'));
    }
    @SuppressLint("RestrictedApi")
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
    private void saveData(boolean isUSB1Checked) {
        // 실제 데이터 저장 로직
    }

    private void cancelSettings() {
        // 설정 취소 로직
    }

    // ... TCP/IP 설정 저장 및 테스트를 위한 추가적인 메소드
    private List<String> parseStorageInfo(String storageInfo) {
        List<String> storageDetails = new ArrayList<>();
        if (storageInfo != null && !storageInfo.isEmpty()) {
            String[] storages = storageInfo.split("\n");
            for (String storage : storages) {
                // 저장매체 정보를 ':'를 기준으로 분리
                String[] parts = storage.split(":");
                if (parts.length == 2) { // 디바이스명과 용량 정보가 모두 있을 경우
                    String name = parts[0].trim();
                    String capacity = parts[1].trim();
                    storageDetails.add(name + ": " + capacity); // "디바이스명: 용량GB" 형식으로 추가
                }
            }
        }
        return storageDetails;
    }



    // 디바이스 번호 저장 메소드
    private void saveDeviceNumber(String deviceNumber) {
        // 디바이스 번호 저장 로직 구현
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("deviceNumber", deviceNumber);
        editor.apply();
    }

    private void checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    STORAGE_PERMISSION_CODE);
        }
    }

        @SuppressLint("RestrictedApi")
        public SerialPort getSerialPort(String portNum, int baudRate) {
            try {
                if (mSerialPort == null) {
                    mSerialPort = new SerialPort(new File(portNum), baudRate, 0);
                }
            } catch (Exception ex) {
                Log.e(LOG_TAG, ex.getMessage());
            }
            return mSerialPort;
        }
        private String getFileName() {
            // SharedPreferences에서 선택된 저장 매체 복원
            SharedPreferences sharedPreferences = getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
            deviceNumber = sharedPreferences.getString("deviceNumber", "");
            // 서울 시간대 설정
            TimeZone seoulTimeZone = TimeZone.getTimeZone("Asia/Seoul");
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            dateFormat.setTimeZone(seoulTimeZone);

            // 현재 서울의 시간 얻기
            Date seoulTime = new Date();
            String seoulTimeString = dateFormat.format(seoulTime);
            return deviceNumber + "-" + seoulTimeString + ".txt";
        }
        @SuppressLint("RestrictedApi")
        private void saveDataToFile(byte[] data) {
            if (directoryUri == null) {
                Log.e(LOG_TAG, "No directory selected.");
                return;
            }

            try {
                // 'data' 폴더를 찾거나 생성합니다.
                DocumentFile pickedDir = DocumentFile.fromTreeUri(this, directoryUri);
                DocumentFile dataDirectory = pickedDir.findFile("W.H.Data");
                if (dataDirectory == null || !dataDirectory.exists()) {
                    // 'data' 폴더가 없으면 새로 생성합니다.
                    dataDirectory = pickedDir.createDirectory("W.H.Data");
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

    }



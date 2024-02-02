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
    private ReadThread readThread;
    public Uri directoryUri;  // 사용자가 선택한 디렉토리의 URI

    // 디바이스 번호 입력 필드 참조 (EditText 추가 필요)
    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_option);
        checkStoragePermission();

        setupButtons();

        @SuppressLint({"MissingInflatedId", "LocalSuppress"}) EditText deviceNumberInput = findViewById(R.id.DeviceEditText); // 레이아웃에 해당 ID를 가진 EditText 추가 필요
        Button buttonSave = findViewById(R.id.btsave);
        Button buttonCancel = findViewById(R.id.btcancel);
        Button buttonSelectDevice = findViewById(R.id.btselectDevice);

        Intent getIntent = getIntent();
        String storageInfo = getIntent.getStringExtra("storageInfo");
        Log.d(TAG, "MainActivity로부터 온 storageInfo: " + storageInfo);
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
                                            .setMessage("디바이스 ID가 " + deviceNumber + "로 변경되었습니다.")
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
        sendButton1.setOnClickListener(view -> {
            // ReadThread와 FileSaveThread를 다시 시작합니다.
            startReadingData();
            // 데이터를 송신합니다.
            if (readThread != null) {
                readThread.sendDataToSerialPort(new byte[] { '1' });
            }
        });

        Button sendButton0 = findViewById(R.id.buttonSend0);
        sendButton0.setOnClickListener(v -> {
            if (readThread != null) {
                readThread.sendDataToSerialPort(new byte[] { '0' });
                // 시리얼 포트를 그대로 두고, 스레드를 정지합니다.
                readThread.stopThreads();
            }
        });
    }

    private void startReadingData() {
        String portPath = "/dev/ttyS0"; // 예시 경로
        int baudRate = 115200;
        readThread = new ReadThread(portPath, baudRate, new ReadThread.IDataReceiver() {
            @Override
            public void onReceiveData(String data) {
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // 에러 처리 로직
                        Log.e(TAG, "Error: ", e);
                    }
                });
            }
        }, this);
        readThread.start();
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
            SharedPreferences prefs = getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("directoryUri", String.valueOf(directoryUri));
            editor.apply();
        }
    }

}



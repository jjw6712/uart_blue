package com.example.uart_blue;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class OptionActivity extends AppCompatActivity {
    private static final String TAG = "OptionTag";
    private String selectedStorage = ""; //선택한 저장매체
    // PasswordManager 인스턴스 생성
    PasswordManager passwordManager = new PasswordManager(); //패스워드매니저 객체
    String deviceNumber; //디바이스 코드

    // 디바이스 번호 입력 필드 참조 (EditText 추가 필요)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_option);

        @SuppressLint({"MissingInflatedId", "LocalSuppress"}) EditText deviceNumberInput = findViewById(R.id.DeviceEditText); // 레이아웃에 해당 ID를 가진 EditText 추가 필요
        // 컴포넌트 참조 초기화
        //EditText editTextDataInput = findViewById(R.id.editTextDataInput);
        Button buttonSave = findViewById(R.id.btsave);
        Button buttonCancel = findViewById(R.id.btcancel);

        Intent getIntent = getIntent();
        String storageInfo = getIntent.getStringExtra("storageInfo");
        Log.d(TAG, "MainActivity로부터 온 storageInfo: "+storageInfo);
        List<String> storageNames = parseStorageInfo(storageInfo);
        // 예외 처리: MainActivity로부터 전달된 저장매체 정보가 없는 경우
        if (storageInfo == null || storageInfo.isEmpty()) {
            selectedStorage = "";
        }
        setupRadioButtons(storageNames);

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
    private void setupRadioButtons(List<String> storageNames) {
        RadioGroup radioGroup = findViewById(R.id.radioGroupStorage);
        radioGroup.removeAllViews();

        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
        String savedStorage = sharedPreferences.getString("selectedStorage", "");

        for (String name : storageNames) {
            RadioButton radioButton = new RadioButton(this);
            radioButton.setText(name);
            radioButton.setId(View.generateViewId());

            if (name.equals(savedStorage)) {
                radioButton.setChecked(true);
                selectedStorage = savedStorage;
            }

            radioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedStorage = name;
                }
            });

            radioGroup.addView(radioButton);
        }

        // 예외 처리: 선택된 저장매체가 현재 연결된 저장매체 목록에 없는 경우
        if (!storageNames.contains(savedStorage)) {
            selectedStorage = "";
            radioGroup.clearCheck(); // 모든 라디오 버튼 체크 해제
        }
    }


    // 디바이스 번호 저장 메소드
    private void saveDeviceNumber(String deviceNumber) {
        // 디바이스 번호 저장 로직 구현
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("deviceNumber", deviceNumber);
        editor.apply();
    }
}

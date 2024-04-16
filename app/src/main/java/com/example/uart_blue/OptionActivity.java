package com.example.uart_blue;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.KeyListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.uart_blue.FileManager.FileManager;
import com.example.uart_blue.FileManager.FileProcessingTask;
import com.example.uart_blue.FileManager.OldFilesDeletionWorker;
import com.example.uart_blue.FileManager.ZipFilesDeletionWorker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class OptionActivity extends AppCompatActivity {
    private static final String TAG = "OptionTag";
    private static final String SWITCH_TEST_KEY = "SwitchTest";
    private static final int STORAGE_PERMISSION_CODE = 100;
    private static final int REQUEST_DIRECTORY_PICKER = 101;
    private String selectedStorage = ""; //선택한 저장매체
    // PasswordManager 인스턴스 생성
    PasswordManager passwordManager = new PasswordManager(); //패스워드매니저 객체
    String deviceNumber; //디바이스 코드

    public Uri directoryUri;  // 사용자가 선택한 디렉토리의 URI
    // 멤버 변수로 GPIOActivity 참조를 유지합니다.
    private GPIOActivity gpioActivity;

    private EditText pressEditText, SIPEditText, TPortEditText, ZPortEditText;
    private double maxPressure;
    int days;
    ImageButton btShow;
    Button btstart, btstop, btwh;
    private boolean areButtonsVisible = true;
    SharedPreferences sharedPreferences1;
    SharedPreferences.OnSharedPreferenceChangeListener sharedPreferenceChangeListener;
    double percentage;
    String selectedTime;
    EditText beforeTimesEditText, afterTimesEditText, secondHoldingEditText, WHHoldingEditText, PressColEditText, WLevelEditText;
    Spinner timeSpinner, sensorTypeSpinner, PressSpinner, WLevelSpinner;
    private boolean userSpinnerInteraction = false;

    // 디바이스 번호 입력 필드 참조 (EditText 추가 필요)
    @SuppressLint({"RestrictedApi", "MissingInflatedId", "WrongViewCast"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_option);
        checkStoragePermission();

        PressColEditText = findViewById(R.id.PressColEditText);
        WLevelEditText = findViewById(R.id.WLevelColEditText);
// 스피너에 대한 참조를 얻은 후
        sensorTypeSpinner = findViewById(R.id.SensorTypeSpinner);
        timeSpinner = findViewById(R.id.TimesSpinner);

// 스피너에 터치 리스너를 설정하여 사용자의 상호작용을 감지
        sensorTypeSpinner.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                userSpinnerInteraction = true;
            }
            return false; // 이벤트를 여기서 소비하지 않음
        });

        timeSpinner.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                userSpinnerInteraction = true;
            }
            return false; // 이벤트를 여기서 소비하지 않음
        });
        btShow = findViewById(R.id.btshow);
        btstart = findViewById(R.id.btstart);
        btstop = findViewById(R.id.btstop);
        btwh = findViewById(R.id.btwh);
        // btShow에 OnLongClickListener를 설정합니다.
        btShow.setOnLongClickListener(view -> {
            // areButtonsVisible 플래그를 이용하여 버튼들의 가시성을 전환합니다.
            if (areButtonsVisible) {
                // 버튼들을 숨깁니다.
                btstart.setVisibility(View.INVISIBLE);
                btstop.setVisibility(View.INVISIBLE);
                btwh.setVisibility(View.INVISIBLE);
            } else {
                // 버튼들을 보여줍니다.
                btstart.setVisibility(View.VISIBLE);
                btstop.setVisibility(View.VISIBLE);
                btwh.setVisibility(View.VISIBLE);
            }

            // 가시성 상태를 전환합니다.
            areButtonsVisible = !areButtonsVisible;

            // 이벤트가 처리되었음을 나타내기 위해 true를 반환합니다.
            return true;
        });

        SIPEditText = findViewById(R.id.SIPEditText);
        TPortEditText = findViewById(R.id.TPortEditText);
        ZPortEditText = findViewById(R.id.ZPortEditText);

        sharedPreferences1 = getSharedPreferences("MySharedPrefs", MODE_PRIVATE);

        sharedPreferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                if ("GPIO_138_ACTIVE".equals(key) || "GPIO_139_ACTIVE".equals(key)) {
                    boolean isGpio138Active = sharedPreferences1.getBoolean("GPIO_138_ACTIVE", false);
                    boolean isGpio139Active = sharedPreferences1.getBoolean("GPIO_139_ACTIVE", false);
                    runOnUiThread(() -> {
                        if (isGpio138Active) {
                            SIPEditText.setEnabled(false);
                            SIPEditText.setError("운전중일 때 IP 수정은 불가능 합니다.");
                            pressEditText.setEnabled(false);
                            pressEditText.setError("운전중일 때 압력값 수정은 불가능 합니다.");
                            TPortEditText.setEnabled(false);
                            TPortEditText.setError("운전중일 때 포트 변경은 불가능 합니다.");
                            ZPortEditText.setEnabled(false);
                            ZPortEditText.setError("운전중일 때 포트 변경은 불가능 합니다.");
                            beforeTimesEditText.setEnabled(false);
                            SIPEditText.setError("운전중일 때 수정은 불가능 합니다.");
                            WHHoldingEditText.setEnabled(false);
                            SIPEditText.setError("운전중일 때 수정은 불가능 합니다.");
                            secondHoldingEditText.setEnabled(false);
                            SIPEditText.setError("운전중일 때 수정은 불가능 합니다.");
                            afterTimesEditText.setEnabled(false);
                            SIPEditText.setError("운전중일 때 수정은 불가능 합니다.");
                            timeSpinner.setEnabled(false);
                            SIPEditText.setError("운전중일 때 수정은 불가능 합니다.");
                            sensorTypeSpinner.setEnabled(false);
                            SIPEditText.setError("운전중일 때 수정은 불가능 합니다.");
                            PressColEditText.setEnabled(false);
                            WLevelEditText.setEnabled(false);
                            PressSpinner.setEnabled(false);
                            WLevelSpinner.setEnabled(false);
                        } else if (isGpio139Active) {
                            SIPEditText.setEnabled(true);
                            SIPEditText.setError(null);
                            pressEditText.setEnabled(true);
                            pressEditText.setError(null);
                            TPortEditText.setEnabled(true);
                            TPortEditText.setError(null);
                            ZPortEditText.setEnabled(true);
                            ZPortEditText.setError(null);
                            beforeTimesEditText.setEnabled(true);
                            beforeTimesEditText.setError(null);
                            WHHoldingEditText.setEnabled(true);
                            WHHoldingEditText.setError(null);
                            secondHoldingEditText.setEnabled(true);
                            secondHoldingEditText.setError(null);
                            afterTimesEditText.setEnabled(true);
                            afterTimesEditText.setError(null);
                            timeSpinner.setEnabled(true);
                            sensorTypeSpinner.setEnabled(true);
                            PressColEditText.setEnabled(true);
                            WLevelEditText.setEnabled(true);
                            PressSpinner.setEnabled(true);
                            WLevelSpinner.setEnabled(true);
                        }
                    });
                }
            }
        };

        // SharedPreferences 변경 리스너 등록
        sharedPreferences1.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);

        // Initialize the spinner
        sensorTypeSpinner = findViewById(R.id.SensorTypeSpinner);

        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> sensoradapter = ArrayAdapter.createFromResource(this,
                R.array.sensor_types, android.R.layout.simple_spinner_item);

        // Specify the layout to use when the list of choices appears
        sensoradapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Apply the adapter to the spinner
        sensorTypeSpinner.setAdapter(sensoradapter);

        // Set the spinner click listener
        sensorTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // An item was selected. You can retrieve the selected item using
                if (userSpinnerInteraction) {
                    // 사용자가 스피너를 터치하여 항목을 변경했을 때만 실행
                    pressEditText.setText("");
                    pressEditText.setError("압력값을 다시 입력하세요");
                    setCorrectionRangeHint(); // 보정 범위 업데이트
                    userSpinnerInteraction = false; // 플래그를 초기화
                }

                String selectedItem = parent.getItemAtPosition(position).toString();
                updatePressureLimits(selectedItem);
                // Update the global data manager
                SensorDataManager.getInstance().setMaxPressure(maxPressure);

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Another interface callback
            }
        });
        // Initialize the spinner
        timeSpinner = findViewById(R.id.TimesSpinner);

        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> timeadapter = ArrayAdapter.createFromResource(this,
                R.array.times, android.R.layout.simple_spinner_item);

        // Specify the layout to use when the list of choices appears
        timeadapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Apply the adapter to the spinner
        timeSpinner.setAdapter(timeadapter);

        // Set the spinner click listener
        timeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (userSpinnerInteraction) {
                    // 사용자가 스피너를 터치하여 항목을 변경했을 때만 실행
                    pressEditText.setText("");
                    pressEditText.setError("압력값을 다시 입력하세요");
                    userSpinnerInteraction = false; // 플래그를 초기화
                }
                // An item was selected. You can retrieve the selected item using
                selectedTime = parent.getItemAtPosition(position).toString();
                // Update the global data manager
                SensorDataManager.getInstance().setSelectedTime(Integer.parseInt(selectedTime));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Another interface callback
            }
        });

        pressEditText = findViewById(R.id.PressEditText);
        // GPIOActivity 인스턴스 생성 또는 가져오기
        try {
            gpioActivity = GPIOManager.getInstance(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        beforeTimesEditText = findViewById(R.id.BeforeTimesEditText);
        afterTimesEditText = findViewById(R.id.AfterTimesEditText);
        CheckBox checkboxSwitchTest = findViewById(R.id.checkboxSwitchTest);
        checkboxSwitchTest.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // SharedPreferences에 체크박스 상태를 저장합니다.
            SharedPreferences sharedPreferences = getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean(SWITCH_TEST_KEY, isChecked);
            editor.apply();

            // GPIO 입력 상태를 업데이트합니다.
            if (isChecked) {
                gpioActivity.enableGpioInput();
            } else {
                gpioActivity.disableGpioInput();
            }
        });

        PressSpinner = findViewById(R.id.PressSpinner);

        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> Pressadapter = ArrayAdapter.createFromResource(this,
                R.array.pm, android.R.layout.simple_spinner_item);

        // Specify the layout to use when the list of choices appears
        Pressadapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Apply the adapter to the spinner
        PressSpinner.setAdapter(Pressadapter);

        // Set the spinner click listener
        PressSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Another interface callback
            }
        });

        WLevelSpinner = findViewById(R.id.WLevelSpinner);

        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> WLeveladapter = ArrayAdapter.createFromResource(this,
                R.array.pm, android.R.layout.simple_spinner_item);

        // Specify the layout to use when the list of choices appears
        WLeveladapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // Apply the adapter to the spinner
        WLevelSpinner.setAdapter(WLeveladapter);

        // Set the spinner click listener
        WLevelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Another interface callback
            }
        });

        // 시간 입력받기 위한 EditText 추가
        secondHoldingEditText = findViewById(R.id.SecondHoldingEditText);
        WHHoldingEditText = findViewById(R.id.whHoldingEditText);
        setupButtons(); //터치로 동작을 제어하는 버튼 함수

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
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
        // SharedPreferences에서 선택된 저장 매체 복원
        deviceNumber = sharedPreferences.getString("deviceNumber", "");
        // EditText에 저장된 디바이스 번호 설정
        deviceNumberInput.setText(deviceNumber);
        //수격전, 후 시간설정값 저장
        int beforeSeconds = sharedPreferences.getInt("beforeSeconds", 0);
        int afterMinutes = sharedPreferences.getInt("afterMinutes", 0);
        int intervalHours = sharedPreferences.getInt("intervalHours", 0);
        int intervalDays = sharedPreferences.getInt("intervalDays", 0);
        String selectedSensorType = sharedPreferences.getString("SelectedSensorType", "");
        String selectedTime = sharedPreferences.getString("SelectedTime", "");
        String pressureValue = sharedPreferences.getString("PressureValue", "");
        String serverIP = sharedPreferences.getString("ServerIP", "");
        String TPort = sharedPreferences.getString("TPort", "");
        String ZPort = sharedPreferences.getString("ZPort", "");
        // 보정값 불러오기
        float pressCorrection = sharedPreferences.getFloat("PressCorrection", 0);
        String pressDirection = sharedPreferences.getString("PressDirection", "+");
        float wLevelCorrection = sharedPreferences.getFloat("WLevelCorrection", 0);
        String wLevelDirection = sharedPreferences.getString("WLevelDirection", "+");
        Log.e(TAG, "pressDirection: "+pressDirection );
        setCorrectionRangeHint();

        // EditText에 보정값 설정
        PressColEditText.setText(String.valueOf(pressCorrection));
        WLevelEditText.setText(String.valueOf(wLevelCorrection));
        ArrayAdapter<CharSequence> pressadapter = (ArrayAdapter<CharSequence>) PressSpinner.getAdapter();
        int position = pressadapter.getPosition(pressDirection);
        PressSpinner.setSelection(position, true);

        ArrayAdapter<CharSequence> wleveladapter = (ArrayAdapter<CharSequence>) WLevelSpinner.getAdapter();
        position = wleveladapter.getPosition(wLevelDirection);
        WLevelSpinner.setSelection(position, true);

        SIPEditText.setText(serverIP);
        TPortEditText.setText(TPort);
        ZPortEditText.setText(ZPort);

        pressEditText.setText(pressureValue);

        // Spinner의 어댑터 설정
        ArrayAdapter<CharSequence> sensorTypeAdapter = ArrayAdapter.createFromResource(this, R.array.sensor_types, android.R.layout.simple_spinner_item);
        sensorTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sensorTypeSpinner.setAdapter(sensorTypeAdapter);

        ArrayAdapter<CharSequence> timeAdapter = ArrayAdapter.createFromResource(this, R.array.times, android.R.layout.simple_spinner_item);
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        timeSpinner.setAdapter(timeAdapter);

// 리스너 설정 전에 이전에 선택된 항목을 설정합니다.
        if (!selectedSensorType.isEmpty()) {
            int sensorTypePosition = sensorTypeAdapter.getPosition(selectedSensorType);
            sensorTypeSpinner.setSelection(sensorTypePosition, false); // 리스너를 트리거하지 않음
        }

        if (!selectedTime.isEmpty()) {
            int timePosition = timeAdapter.getPosition(selectedTime);
            timeSpinner.setSelection(timePosition, false); // 리스너를 트리거하지 않음
        }
        beforeTimesEditText.setText(String.valueOf(beforeSeconds));
        afterTimesEditText.setText(String.valueOf(afterMinutes));
        secondHoldingEditText.setText(String.valueOf(intervalHours));
        WHHoldingEditText.setText(String.valueOf(intervalDays));

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
        Button passwordChangeButton = findViewById(R.id.passwordChangeButton);
        passwordChangeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 커스텀 레이아웃을 사용하여 다이얼로그 생성
                LayoutInflater inflater = getLayoutInflater();
                View dialogView = inflater.inflate(R.layout.password_change_dialog, null);
                final EditText oldPasswordInput = dialogView.findViewById(R.id.oldPasswordInput);
                final EditText newPasswordInput = dialogView.findViewById(R.id.newPasswordInput);

                AlertDialog dialog = new AlertDialog.Builder(OptionActivity.this)
                        .setTitle("Change Password")
                        .setView(dialogView)
                        .setPositiveButton("확인", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                String oldPassword = oldPasswordInput.getText().toString();
                                String newPassword = newPasswordInput.getText().toString();
                                if (passwordManager.changePassword(oldPassword, newPassword)) {
                                    // 패스워드 변경 성공
                                    Toast.makeText(OptionActivity.this, "패스워드가 변경되었습니다.", Toast.LENGTH_SHORT).show();
                                } else {
                                    // 패스워드 변경 실패
                                    Toast.makeText(OptionActivity.this, "올바른 현재 패스워드를 입력하세요.", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton("취소", null)
                        .show();
            }
        });
        pressEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not used
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Not used
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().isEmpty()) {
                    double inputValue = Double.parseDouble(s.toString());
                    double maxPressure = SensorDataManager.getInstance().getMaxPressure();
                    if (inputValue < 0.1 || inputValue > maxPressure) {
                        pressEditText.setError("값은 0.1kg과 " + maxPressure + "kg 사이여야 합니다.");
                    } else {
                        // Update the global data manager
                        percentage = (inputValue / maxPressure) * 80;
                        Log.d(TAG, "압력 입력값: "+inputValue + "센서 최대압력값: "+maxPressure+"퍼센트 계산값: "+percentage);
                        SensorDataManager.getInstance().setExpectedPressurePercentage(percentage);
                        pressEditText.setError(null);

                        // SharedPreferences에 percentage 값을 저장
                        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putFloat("percentage", (float) percentage);
                        editor.putFloat("maxPressure", (float) maxPressure);
                        editor.apply(); // 변경 사항을 저장
                    }
                }
            }
        });

        // 저장 버튼 클릭 리스너
        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    String serverIP = String.valueOf(SIPEditText.getText());
                    String TPort = String.valueOf(TPortEditText.getText());
                    String ZPort = String.valueOf(ZPortEditText.getText());

                    // 1초 파일 홀딩 시간을 무조건 1시간을 의미하는 1로 설정
                    int intervalHours = 1;

                    days = Integer.parseInt(WHHoldingEditText.getText().toString());

                    // 일수가 유효한 범위 내에 있는지 확인
                    if (days < 1 || days > 21) {
                        Toast.makeText(OptionActivity.this, "일수는 1일부터 21일 사이로 설정해야 합니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    float pressCorrection;
                    float wLevelCorrection;
                    // 입력된 보정값 파싱
                    try {
                        pressCorrection = Float.parseFloat(PressColEditText.getText().toString());
                        wLevelCorrection = Float.parseFloat(WLevelEditText.getText().toString());
                    } catch (NumberFormatException e) {
                        Toast.makeText(OptionActivity.this, "잘못된 입력 형식입니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // PressColEditText의 보정값 검증
                    double maxRangePress = getSensorMaxPressure(sensorTypeSpinner.getSelectedItem().toString()) * 0.1;
                    if (pressCorrection < 0 || pressCorrection > maxRangePress) {
                        Toast.makeText(OptionActivity.this, "압력 보정값이 허용 범위를 벗어났습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // WLevelEditText의 보정값 검증
                    if (wLevelCorrection < 0 || wLevelCorrection > 10) {
                        Toast.makeText(OptionActivity.this, "수위 보정값이 허용 범위를 벗어났습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString("selectedStorage", selectedStorage);
                    editor.putBoolean(SWITCH_TEST_KEY, checkboxSwitchTest.isChecked()); // 체크박스의 현재 상태를 저장

                    int beforeSeconds = Integer.parseInt(beforeTimesEditText.getText().toString()); // 초 단위
                    int afterMinutes = Integer.parseInt(afterTimesEditText.getText().toString()); // 분 단위
                    // 밀리초 단위로 변환
                    long beforeMillis = beforeSeconds * 1000;
                    long afterMillis = afterMinutes * 60 * 1000;
                    editor.putInt("intervalHours", intervalHours);
                    editor.putInt("intervalDays", days);
                    editor.putInt("beforeSeconds", beforeSeconds);
                    editor.putInt("afterMinutes", afterMinutes);
                    editor.putLong("beforeMillis", beforeMillis);
                    editor.putLong("afterMillis", afterMillis);
                    editor.putString("SelectedSensorType", sensorTypeSpinner.getSelectedItem().toString());
                    editor.putString("SelectedTime", timeSpinner.getSelectedItem().toString());
                    editor.putString("PressureValue", pressEditText.getText().toString());
                    editor.putString("ServerIP", serverIP);
                    editor.putString("TPort", TPort);
                    editor.putString("ZPort", ZPort);

                    // 보정값 저장
                    editor.putFloat("PressCorrection", pressCorrection);
                    editor.putString("PressDirection", PressSpinner.getSelectedItem().toString());
                    editor.putFloat("WLevelCorrection", wLevelCorrection);
                    editor.putString("WLevelDirection", WLevelSpinner.getSelectedItem().toString());
                    editor.apply();

                    // MainActivity로 이동
                    Intent intent = new Intent(OptionActivity.this, MainActivity.class);
                    intent.putExtra("selectedStorage", extractStorageName(selectedStorage));
                    startActivity(intent);
                    finish();

                } catch (NumberFormatException e) {
                    Toast.makeText(OptionActivity.this, "잘못된 시간 형식입니다.", Toast.LENGTH_SHORT).show();
                }

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
                // MainActivity로 이동
                Intent intent = new Intent(OptionActivity.this, MainActivity.class);
                startActivity(intent);
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


    private void updatePressureLimits(String sensorType) {
        int sensorMaxKg = Integer.parseInt(sensorType.replace("kg", ""));
        maxPressure = sensorMaxKg * 0.8; // Set to 80% of the sensor's maximum capacity
        Log.d(TAG, "센서값: "+ maxPressure);
        // Optionally, update EditText hint or other UI elements to show the limit
        pressEditText.setHint("Enter value (0.1 - " + maxPressure + " kg)");
        pressEditText.setTextSize(10);
    }


    private void setupButtons() {
        btstart = findViewById(R.id.btstart);
        btstart.setOnClickListener(view -> {
            Intent serviceIntent = new Intent(this, SerialService.class);
            startService(serviceIntent); // 서비스 시작
        });

        btstop = findViewById(R.id.btstop);
        btstop.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, SerialService.class);
            stopService(serviceIntent); // 서비스 종료
        });

        btwh = findViewById(R.id.btwh);
        btwh.setOnClickListener(v -> {
            // GPIO 28 핀 활성화 시 실행할 로직
            SharedPreferences sharedPreferences = this.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
            boolean isGpio138Active = sharedPreferences.getBoolean("GPIO_138_ACTIVE", false);
            if(!isGpio138Active){
                Toast.makeText(this, "정지 상태에서는 수격 테스트가 불가능합니다.", Toast.LENGTH_LONG).show();
            }else {
                ZipFileSaved();
            }
        });
    }
    private void ZipFileSaved(){
        Log.d(TAG, "수격신호 수신");
        saveWHState(true);
        Date eventTime = new Date(); // 현재 시간을 수격이 발생한 시간으로 가정
        SharedPreferences sharedPreferences = this.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        String directoryUriString = sharedPreferences.getString("directoryUri", "");
        String deviceNumber = sharedPreferences.getString("deviceNumber", "");
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
        int whcount = sharedPreferences.getInt("whcountui", 0);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("whcountui", whcount + 1);
        editor.apply();
        long beforeMillis = 1000 * 10;
        long afterMillis = 1000 * 60;
        // 이벤트 발생 후 대기할 시간 계산
        long delay = afterMillis;
        String finalSensor = sensor;
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Uri directoryUri = Uri.parse(directoryUriString);
            List<Uri> filesInRange = FileManager.findFilesInRange(this, directoryUri, eventTime, beforeMillis, afterMillis);

            if (!filesInRange.isEmpty()) {
                // eventTime을 기반으로 파일 이름 생성
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm", Locale.getDefault());
                String eventDateTime = sdf.format(eventTime);
                String combinedFileName = deviceNumber+"-"+ finalSensor +"-"+eventDateTime + ".txt";
                Uri combinedFileUri = FileManager.combineTextFilesInRange(this, filesInRange, combinedFileName, eventTime, beforeMillis, afterMillis);

                if (combinedFileUri != null) {
                    List<Uri> fileUris = FileManager.findFilesInRange(this, directoryUri, eventTime, beforeMillis, afterMillis);
                    new FileProcessingTask(this, fileUris, combinedFileName, eventTime, beforeMillis, afterMillis, directoryUri).execute();

                }
            } else {
                Log.d(TAG, "지정된 시간 범위 내에서 일치하는 파일이 없습니다.");
            }
            saveWHState(false);
        }, delay);
    }
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


    public void requestWriteSettingsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(getApplicationContext())) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, 200);
            }
        }
    }

    private void setCorrectionRangeHint() {
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
        String selectedSensorType = sensorTypeSpinner.getSelectedItem().toString();

        // 선택된 센서 유형에 따른 최대 값
        double sensorMaxValue = getSensorMaxPressure(selectedSensorType);

        // PressColEditText에 대한 0% ~ 10% 보정 가능 범위 계산 및 표시
        double maxRangePress = sensorMaxValue * 0.1; // 최대 범위는 최대값의 10%
        String hintPress = String.format("보정 범위: 0.00%% ~ %.2f%%", maxRangePress);
        PressColEditText.setError(hintPress);

        // WLevelEditText에 대한 0 ~ 10% 보정 가능 범위 표시
        String hintWLevel = "보정 범위: 0% ~ 10%";
        WLevelEditText.setError(hintWLevel);
    }


    private double getSensorMaxPressure(String selectedSensorType) {
        // 센서 유형에 따른 최대 수압 값 반환
        switch (selectedSensorType) {
            case "5kg":
                return 5.0;
            case "20kg":
                return 20.0;
            case "30kg":
                return 30.0;
            default:
                return 100.0;// 예시, 실제 센서 유형에 맞춰 조정 필요
        }
    }

    protected void onResume() {
        super.onResume();

        // SharedPreferences에서 체크박스 상태를 로드합니다.
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
        boolean isSwitchChecked = sharedPreferences.getBoolean(SWITCH_TEST_KEY, false);

        // 체크박스 상태에 따라 GPIO 입력을 활성화 또는 비활성화합니다.
        if (isSwitchChecked) {
            gpioActivity.enableGpioInput();
        } else {
            gpioActivity.disableGpioInput();
        }
        setCorrectionRangeHint();
        // 체크박스 UI를 저장된 상태와 일치시킵니다.
        CheckBox checkboxSwitchTest = findViewById(R.id.checkboxSwitchTest);
        checkboxSwitchTest.setChecked(isSwitchChecked);

        boolean isGpio138Active = sharedPreferences1.getBoolean("GPIO_138_ACTIVE", false);
        boolean isGpio139Active = sharedPreferences1.getBoolean("GPIO_139_ACTIVE", false);

        if (isGpio138Active) {
            SIPEditText.setEnabled(false);
            SIPEditText.setError("운전중일 때 IP 수정은 불가능 합니다.");
            pressEditText.setEnabled(false);
            pressEditText.setError("운전중일 때 압력값 수정은 불가능 합니다.");
            TPortEditText.setEnabled(false);
            TPortEditText.setError("운전중일 때 포트 변경은 불가능 합니다.");
            ZPortEditText.setEnabled(false);
            ZPortEditText.setError("운전중일 때 포트 변경은 불가능 합니다.");
            beforeTimesEditText.setEnabled(false);
            SIPEditText.setError("운전중일 때 수정은 불가능 합니다.");
            WHHoldingEditText.setEnabled(false);
            SIPEditText.setError("운전중일 때 수정은 불가능 합니다.");
            secondHoldingEditText.setEnabled(false);
            SIPEditText.setError("운전중일 때 수정은 불가능 합니다.");
            afterTimesEditText.setEnabled(false);
            SIPEditText.setError("운전중일 때 수정은 불가능 합니다.");
            timeSpinner.setEnabled(false);
            SIPEditText.setError("운전중일 때 수정은 불가능 합니다.");
            sensorTypeSpinner.setEnabled(false);
            SIPEditText.setError("운전중일 때 수정은 불가능 합니다.");
            PressColEditText.setEnabled(false);
            WLevelEditText.setEnabled(false);
            PressSpinner.setEnabled(false);
            WLevelSpinner.setEnabled(false);
        } else if (isGpio139Active) {
            SIPEditText.setEnabled(true);
            SIPEditText.setError(null);
            pressEditText.setEnabled(true);
            pressEditText.setError(null);
            TPortEditText.setEnabled(true);
            TPortEditText.setError(null);
            ZPortEditText.setEnabled(true);
            ZPortEditText.setError(null);
            beforeTimesEditText.setEnabled(true);
            beforeTimesEditText.setError(null);
            WHHoldingEditText.setEnabled(true);
            WHHoldingEditText.setError(null);
            secondHoldingEditText.setEnabled(true);
            secondHoldingEditText.setError(null);
            afterTimesEditText.setEnabled(true);
            afterTimesEditText.setError(null);
            timeSpinner.setEnabled(true);
            sensorTypeSpinner.setEnabled(true);
            PressColEditText.setEnabled(true);
            WLevelEditText.setEnabled(true);
            PressSpinner.setEnabled(true);
            WLevelSpinner.setEnabled(true);
        }

        }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 리스너 등록 해제
        sharedPreferences1.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    }
    public void saveWHState(boolean is28Active) {
        SharedPreferences sharedPreferences = this.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("GPIO_28_ACTIVE", is28Active);
        editor.apply();
    }
}



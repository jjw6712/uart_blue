package com.example.uart_blue;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.uart_blue.Network.NetworkStateReceiver;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private StringBuilder usbInfo;
    private StringBuilder displayInfo;
    private String SelectDevice = "";
    private TextView textViewUSBStorageInfo;
    private ImageButton buttonOption;
    private final Handler handler = new Handler();
    private TextView tvStop, tvDrive, tvWH;
    private NetworkStateReceiver networkStateReceiver;
    private boolean isReceiverRegistered = false;
    int wh;
    SharedPreferences sharedPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    TextView textViewWHCounterValue, textViewPressValue, textViewWLevelValue;
    private final Runnable runnable = new Runnable() {
        @Override
        public void run() {
            updateUSBStorageInfo();
            handler.postDelayed(this,  60 * 1000); // 60초마다 실행
        }
    };

    // BroadcastReceiver 정의
    private final BroadcastReceiver updateUIReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {


            int battery = intent.getIntExtra("battery", 0);
            int blackout = intent.getIntExtra("blackout", 0);

            TextView textViewInnerBatteryValue = findViewById(R.id.textViewInnerBaterryValue);
            TextView textViewEternalPowerValue = findViewById(R.id.textViewEternalPowerValue);

            textViewInnerBatteryValue.setText(String.format("%d%%", battery));
            textViewEternalPowerValue.setText(blackout == 0 ? "ON" : "OFF");

        }
    };

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
            startActivity(intent);
        }

        // 네트워크 상태 변경을 감지하기 위한 BroadcastReceiver 등록
        if (networkStateReceiver == null) {
            networkStateReceiver = new NetworkStateReceiver();
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(uiUpdateReceiver,
                new IntentFilter("com.example.uart_blue.UPDATE_UI_STATE"));



        tvStop = findViewById(R.id.tvStop);
        tvDrive = findViewById(R.id.tvDrive);
        tvWH = findViewById(R.id.tvWH);

        textViewWHCounterValue = findViewById(R.id.textViewWHCounterValue);
        textViewPressValue = findViewById(R.id.textViewPressValue);
        textViewWLevelValue = findViewById(R.id.textViewW_LevelValue);

        // SharedPreferences에서 값 로드
        sharedPreferences = getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
        String pressDirection = sharedPreferences.getString("PressDirection", "+");
        float pressCorrection = sharedPreferences.getFloat("PressCorrection", 0);
        float wLevelCorrection = sharedPreferences.getFloat("WLevelCorrection", 0);
        String wLevelDirection = sharedPreferences.getString("WLevelDirection", "+");
        // TextView 찾기
        TextView textViewPressColValue = findViewById(R.id.textViewPressColValue);
        TextView textViewWLevelColValue = findViewById(R.id.textViewWLevelColValue);

        // 보정값을 문자열 형태로 포매팅
        String PresscorrectionText = String.format(Locale.US, "%s %.2f", pressDirection, pressCorrection);
        String WLevelcorrectionText = String.format(Locale.US, "%s %.2f", wLevelDirection, wLevelCorrection);

        // TextView에 텍스트 설정
        textViewPressColValue.setText(PresscorrectionText);
        textViewWLevelColValue.setText(WLevelcorrectionText);

        // Press 보정값을 문자열 형태로 포매팅 및 색상 설정
        String pressCorrectionText = String.format(Locale.US, "%s %.2f", pressDirection, pressCorrection);
        textViewPressColValue.setText(pressCorrectionText);
        int pressTextColor = "+".equals(pressDirection) ? ContextCompat.getColor(this, R.color.blue) : ContextCompat.getColor(this, R.color.red);
        textViewPressColValue.setTextColor(pressTextColor);

// WLevel 보정값을 문자열 형태로 포매팅 및 색상 설정
        String wLevelCorrectionText = String.format(Locale.US, "%s %.2f", wLevelDirection, wLevelCorrection);
        textViewWLevelColValue.setText(wLevelCorrectionText);
        int wLevelTextColor = "+".equals(wLevelDirection) ? ContextCompat.getColor(this, R.color.blue) : ContextCompat.getColor(this, R.color.red);
        textViewWLevelColValue.setTextColor(wLevelTextColor);
        // SharedPreferences 초기화
        sharedPreferences = this.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE);

        // SharedPreferences 리스너 설정
        preferenceChangeListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                switch (key) {
                    case "whcountui":
                        int whcountui = sharedPreferences.getInt(key, 0);
                        updateWHUI(whcountui);
                        break;
                    case "GPIO_138_ACTIVE":
                    case "GPIO_139_ACTIVE":
                    case "GPIO_28_ACTIVE":
                        boolean is138Active = sharedPreferences.getBoolean("GPIO_138_ACTIVE", false);
                        boolean is139Active = sharedPreferences.getBoolean("GPIO_139_ACTIVE", false);
                        boolean is28Active = sharedPreferences.getBoolean("GPIO_28_ACTIVE", false);
                        updateGPIOUI(is138Active, is139Active, is28Active);
                        break;
                    case "Press":
                    case "WLevel":
                        loadCorrectionSettings();
                        break;
                }
            }
            private void loadCorrectionSettings() {
                SharedPreferences sharedPreferences = getSharedPreferences("MySharedPrefs", MODE_PRIVATE);
                float pressCorrection = sharedPreferences.getFloat("PressCorrection", 0);
                String pressDirection = sharedPreferences.getString("PressDirection", "+");
                float wLevelCorrection = sharedPreferences.getFloat("WLevelCorrection", 0);
                String wLevelDirection = sharedPreferences.getString("WLevelDirection", "+");
                float Press = sharedPreferences.getFloat("Press", 0);
                float WLevel = sharedPreferences.getFloat("WLevel", 0);
                String selectedSensorType = sharedPreferences.getString("SelectedSensorType", "");
                double sensorMaxPressure = getSensorMaxPressure(selectedSensorType);
                double calculatedPressValue = (sensorMaxPressure * Press) / 100;

                applyCorrectionToDisplayValues(calculatedPressValue, pressCorrection, pressDirection, WLevel, wLevelCorrection, wLevelDirection);
            }

            private void applyCorrectionToDisplayValues(double Press, float pressCorrection, String pressDirection,
                                                        double WLevel, float wLevelCorrection, String wLevelDirection) {
                // 보정 적용
                //Press = pressDirection.equals("+") ? Press + Press * (pressCorrection / 100) : Press - Press * (pressCorrection / 100);
                Press = pressDirection.equals("+") ? Press + pressCorrection : Press - pressCorrection;
                //WLevel = wLevelDirection.equals("+") ? WLevel + WLevel * (wLevelCorrection / 100) : WLevel - WLevel * (wLevelCorrection / 100);
                WLevel = wLevelDirection.equals("+") ? WLevel + wLevelCorrection : WLevel - wLevelCorrection;

                updatePressUI(Press);
                updateWLevelUI(WLevel);
            }
            private double getSensorMaxPressure(String selectedSensorType) {
                switch (selectedSensorType) {
                    case "5kg":
                        return 4.0;
                    case "20kg":
                        return 16.0;
                    case "30kg":
                        return 24.0;
                    default:
                        return 80.0;
                }
            }
        };
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        // 앱 시작 시 초기 값으로 UI 업데이트
        int whcountui = sharedPreferences.getInt("whcountui", 0);
        updateWHUI(whcountui);

        networkStateReceiver = new NetworkStateReceiver();
        buttonOption = findViewById(R.id.buttonOption);
        // BroadcastReceiver 등록
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.example.uart_blue.ACTION_UPDATE_UI");
        registerReceiver(updateUIReceiver, filter);
        buttonOption.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent optionIntent = new Intent(MainActivity.this, OptionActivity.class);
                optionIntent.putExtra("storageInfo", usbInfo.toString());
                startActivity(optionIntent);
                finish();
            }
        });
        SelectDevice =sharedPreferences.getString("selectedStorage", "");

        textViewUSBStorageInfo = findViewById(R.id.textViewDatasizeValue); //저장소 용량tv
        handler.post(runnable); // 첫 실행 및 주기적 업데이트 시작
        updateUSBStorageInfo();

        Button buttonResetWHCounter = findViewById(R.id.buttonResetWHCounter);
        buttonResetWHCounter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("whcountui", 0);
                editor.apply();
                updateWHUI(0); // UI를 즉시 업데이트하여 0을 표시합니다.
            }
        });
    }
    // 데이터 업데이트 메소드

    private void updateUSBStorageInfo() {
        StorageManager storageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
        usbInfo = new StringBuilder();
        displayInfo = new StringBuilder(); // Initialize displayInfo

        List<StorageVolume> storageVolumes = storageManager.getStorageVolumes();
        for (StorageVolume volume : storageVolumes) {
            if (volume.isRemovable()) {
                String description = volume.getDescription(this);
                //Log.d(TAG, "실제 물리 저장매체 정보: " + description);

                File path = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    path = volume.getDirectory();
                }
                if (path != null && path.exists()) {
                    StatFs statFs = new StatFs(path.getPath());
                    long freeBytes = statFs.getAvailableBytes();
                    double freeGB = freeBytes / 1073741824.0; // Convert bytes to GB

                    // Update usbInfo with all storage volumes
                    usbInfo.append(description)
                            .append(": ")
                            .append(String.format(Locale.US, "%.2fGB\n", freeGB));

                    // Update displayInfo based on the selected device
                    if (SelectDevice == null || SelectDevice.isEmpty() || SelectDevice.equals(description)) {
                        displayInfo.append(description)
                                .append(": ")
                                .append(String.format(Locale.US, "%.2fGB\n", freeGB));
                    }
                }
            }
        }
        //Log.d(TAG, "displayInfo: "+displayInfo);
        // Display either the selected device or all devices
        if (SelectDevice == null) {
            textViewUSBStorageInfo.setText("선택된 저장매체가 없습니다.");
        }else if (displayInfo.length() == 0) {
            textViewUSBStorageInfo.setText("연결된 저장매체가 없습니다.");
        } else {
            textViewUSBStorageInfo.setText(displayInfo.toString());
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (networkStateReceiver == null) {
            networkStateReceiver = new NetworkStateReceiver();
        }
        IntentFilter filter = new IntentFilter("com.example.uart_blue.UPDATE_NETWORK_STATE");
        registerReceiver(networkStateReceiver, filter);
        isReceiverRegistered = true; // 리시버가 등록되었음을 표시합니다.
        // 저장된 GPIO 상태를 불러옵니다.
        boolean is138Active = sharedPreferences.getBoolean("GPIO_138_ACTIVE", false);
        boolean is139Active = sharedPreferences.getBoolean("GPIO_139_ACTIVE", false);
        boolean is28Active = sharedPreferences.getBoolean("GPIO_28_ACTIVE", false);

        // UI 업데이트 로직
        updateGPIOUI(is138Active, is139Active, is28Active);
    }
    private void updateWHUI(int count) {
        // UI 스레드에서 TextView 업데이트
        runOnUiThread(() -> textViewWHCounterValue.setText(String.valueOf(count)));
    }
    private void updatePressUI(double Press) {
        // UI 스레드에서 TextView 업데이트
        runOnUiThread(() -> textViewPressValue.setText(String.format("%.2f Kg/cm²", Press)));
    }
    private void updateWLevelUI(double WLevel) {
        // UI 스레드에서 TextView 업데이트
        runOnUiThread(() -> textViewWLevelValue.setText(String.format("%.2f %%", WLevel)));
    }
    private BroadcastReceiver uiUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String state = intent.getStringExtra("networkState");
            updateNetworkStateUI(state);
        }
    };

    private void updateNetworkStateUI(String state) {
        TextView networkStateTextView = findViewById(R.id.textViewNetworkStateValue);
        networkStateTextView.setText(state);
        ImageView ic_notconnected = findViewById(R.id.ic_notconnected);
        ImageView ic_connected = findViewById(R.id.ic_connected);
        if ("연결됨".equals(state)) {
            ic_connected.setVisibility(View.VISIBLE);
            ic_notconnected.setVisibility(View.INVISIBLE);
        } else {
            ic_notconnected.setVisibility(View.VISIBLE);
            ic_connected.setVisibility(View.INVISIBLE);
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
        // 리시버가 등록되어 있으면 해제합니다.
        if (isReceiverRegistered && networkStateReceiver != null) {
            unregisterReceiver(networkStateReceiver);
            isReceiverRegistered = false; // 리시버 등록 해제를 표시합니다.
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(runnable); // 활동이 파괴될 때 업데이트 중지
        // BroadcastReceiver 해제
        unregisterReceiver(updateUIReceiver);
        // 액티비티가 파괴될 때 리스너 등록 해제
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        LocalBroadcastManager.getInstance(this).unregisterReceiver(uiUpdateReceiver);
    }

    private void updateGPIOUI(boolean is138Active, boolean is139Active, boolean is28Active) {
        runOnUiThread(() -> {
            // null 체크 추가
            if(tvStop != null && tvDrive != null && tvWH != null) {
                if (is138Active) {
                    tvDrive.setVisibility(View.VISIBLE);
                    tvStop.setVisibility(View.INVISIBLE);
                } else if (is139Active) {
                    tvDrive.setVisibility(View.INVISIBLE);
                    tvStop.setVisibility(View.VISIBLE);
                }
                tvWH.setVisibility(is28Active ? View.VISIBLE : View.INVISIBLE);
            }
        });
    }
}
